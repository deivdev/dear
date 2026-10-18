#include <jni.h>
#include <android/log.h>
#include <unistd.h>

#include <algorithm>
#include <memory>
#include <mutex>
#include <stdexcept>
#include <string>
#include <vector>

#include "chat.h"
#include "common.h"
#include "json-schema-to-grammar.h"
#include "llama.h"
#include "mtmd.h"
#include "mtmd-helper.h"
#include "sampling.h"

#define TAG "DearLLM"
#define LOGi(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGe(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

namespace {

constexpr int N_CTX   = 8192;
// Also the micro-batch: models with non-causal image attention need a whole image in one ubatch.
constexpr int N_BATCH = 1024;

struct dear_model {
    llama_model   * model = nullptr;
    llama_context * ctx   = nullptr;
    mtmd_context  * mctx  = nullptr;
    common_chat_templates_ptr tmpls;
    common_batch batch;
};

bool g_verbose_log = false;

void log_callback(ggml_log_level level, const char * text, void *) {
    // Keep llama.cpp quiet except for problems, and backend selection while loading.
    if (level == GGML_LOG_LEVEL_ERROR) {
        __android_log_print(ANDROID_LOG_ERROR, "llama.cpp", "%s", text);
    } else if (level == GGML_LOG_LEVEL_WARN || (g_verbose_log && level == GGML_LOG_LEVEL_INFO)) {
        __android_log_print(ANDROID_LOG_WARN, "llama.cpp", "%s", text);
    }
}

int n_threads() {
    // Leave the little cores alone: performance cores only, 2..4.
    return std::clamp((int) sysconf(_SC_NPROCESSORS_ONLN) - 2, 2, 4);
}

std::string to_std(JNIEnv * env, jstring s) {
    if (!s) return {};
    const char * chars = env->GetStringUTFChars(s, nullptr);
    std::string out(chars);
    env->ReleaseStringUTFChars(s, chars);
    return out;
}

void throw_java(JNIEnv * env, const char * msg) {
    if (env->ExceptionCheck()) return;
    env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), msg);
}

// Length of the longest prefix of `s` that does not stop in the middle of a UTF-8 sequence.
size_t utf8_complete_prefix(const std::string & s) {
    const size_t n = s.size();
    for (size_t back = 1; back <= std::min<size_t>(4, n); ++back) {
        const auto c = (unsigned char) s[n - back];
        if ((c & 0xC0) == 0x80) continue; // continuation byte, keep looking for the lead byte
        const size_t need = (c & 0x80) == 0x00 ? 1
                          : (c & 0xE0) == 0xC0 ? 2
                          : (c & 0xF0) == 0xE0 ? 3
                          : (c & 0xF8) == 0xF0 ? 4 : 1;
        return back >= need ? n : n - back;
    }
    return n;
}

void free_model(dear_model * dm) {
    if (!dm) return;
    if (dm->mctx) mtmd_free(dm->mctx);
    dm->batch = common_batch(); // release the batch before its context
    if (dm->ctx) llama_free(dm->ctx);
    if (dm->model) llama_model_free(dm->model);
    delete dm;
}

std::vector<mtmd::bitmap> read_media(JNIEnv * env, jobjectArray jmedia, jintArray jdims) {
    std::vector<mtmd::bitmap> out;
    if (!jmedia) return out;
    const jsize n = env->GetArrayLength(jmedia);
    std::vector<jint> dims(2 * n);
    if (n > 0) env->GetIntArrayRegion(jdims, 0, 2 * n, dims.data());
    jclass byte_array = env->FindClass("[B");
    for (jsize i = 0; i < n; ++i) {
        jobject item = env->GetObjectArrayElement(jmedia, i);
        if (env->IsInstanceOf(item, byte_array)) {
            // Packed RGB, width x height x 3
            const auto w = (uint32_t) dims[2 * i];
            const auto h = (uint32_t) dims[2 * i + 1];
            auto arr = (jbyteArray) item;
            if ((size_t) env->GetArrayLength(arr) != (size_t) w * h * 3) {
                throw std::runtime_error("image buffer size does not match its dimensions");
            }
            std::vector<unsigned char> rgb((size_t) w * h * 3);
            env->GetByteArrayRegion(arr, 0, (jsize) rgb.size(), (jbyte *) rgb.data());
            out.emplace_back(w, h, rgb.data());
        } else {
            // Mono PCM float samples at the projector's sample rate
            auto arr = (jfloatArray) item;
            std::vector<float> pcm(env->GetArrayLength(arr));
            env->GetFloatArrayRegion(arr, 0, (jsize) pcm.size(), pcm.data());
            out.emplace_back(mtmd_bitmap_init_from_audio(pcm.size(), pcm.data()));
        }
        env->DeleteLocalRef(item);
    }
    return out;
}

// Evaluates the rendered chat prompt, media included. Returns the number of prompt tokens.
int32_t eval_prompt(dear_model * dm, const common_chat_params & chat,
                    std::vector<mtmd::bitmap> & bitmaps, int max_tokens) {
    const int n_ctx = (int) llama_n_ctx(dm->ctx);
    llama_memory_clear(llama_get_memory(dm->ctx), true);

    if (!dm->mctx) {
        if (!bitmaps.empty()) throw std::runtime_error("model has no multimodal projector");
        std::vector<llama_token> tokens;
        for (size_t i = 0; i < chat.prompt.parts.size(); ++i) {
            const auto & part = chat.prompt.parts[i];
            // User input must never be parsed into control tokens.
            auto t = common_tokenize(dm->ctx, part.text, i == 0, !part.is_input);
            tokens.insert(tokens.end(), t.begin(), t.end());
        }
        const auto n_prompt = (int32_t) tokens.size();
        if (n_prompt + max_tokens > n_ctx) throw std::runtime_error("prompt too long for the context");
        for (int32_t i = 0; i < n_prompt; i += N_BATCH) {
            const int32_t n = std::min(N_BATCH, n_prompt - i);
            dm->batch.clear();
            for (int32_t j = 0; j < n; ++j) {
                dm->batch.add(tokens[i + j], i + j, 0, i + j == n_prompt - 1);
            }
            if (llama_process(dm->ctx, LLAMA_PROCESS_TYPE_DECODE, dm->batch.get()) != 0) {
                throw std::runtime_error("prompt decode failed");
            }
        }
        return n_prompt;
    }

    // Split every template part on the media marker so that text and media interleave in order.
    struct segment { std::string text; bool parse_special; };
    const std::string marker = mtmd_default_marker();
    std::vector<segment> segments;
    std::vector<int> order; // >= 0: segment index, < 0: -(media index + 1)
    int n_markers = 0;
    for (const auto & part : chat.prompt.parts) {
        size_t start = 0;
        size_t pos;
        while ((pos = part.text.find(marker, start)) != std::string::npos) {
            segments.push_back({part.text.substr(start, pos - start), !part.is_input});
            order.push_back((int) segments.size() - 1);
            order.push_back(-(++n_markers));
            start = pos + marker.size();
        }
        segments.push_back({part.text.substr(start), !part.is_input});
        order.push_back((int) segments.size() - 1);
    }
    if ((size_t) n_markers != bitmaps.size()) {
        throw std::runtime_error("media markers do not match the media passed in");
    }

    std::vector<mtmd_input_text> texts(segments.size());
    for (size_t i = 0; i < segments.size(); ++i) {
        texts[i] = {segments[i].text.data(), segments[i].text.size(), false, segments[i].parse_special};
    }
    std::vector<mtmd_input_part> parts;
    parts.reserve(order.size());
    for (int o : order) {
        if (o >= 0) parts.push_back({&texts[o], nullptr});
        else parts.push_back({nullptr, bitmaps[-o - 1].ptr.get()});
    }
    std::vector<const mtmd_input_part *> part_ptrs;
    part_ptrs.reserve(parts.size());
    for (const auto & p : parts) part_ptrs.push_back(&p);

    mtmd::input_chunks chunks(mtmd_input_chunks_init());
    if (mtmd_tokenize_from_parts(dm->mctx, chunks.ptr.get(), part_ptrs.data(), part_ptrs.size(), true) != 0) {
        throw std::runtime_error("prompt tokenization failed");
    }
    const auto n_prompt = (int32_t) mtmd_helper_get_n_tokens(chunks.ptr.get());
    if (n_prompt + max_tokens > n_ctx) throw std::runtime_error("prompt too long for the context");

    llama_pos n_past = 0;
    if (mtmd_helper_eval_chunks(dm->mctx, dm->ctx, chunks.ptr.get(), 0, 0, N_BATCH, true, &n_past) != 0) {
        throw std::runtime_error("prompt evaluation failed");
    }
    return n_past;
}

} // namespace

// --- JNI: load ---

extern "C"
JNIEXPORT jlong JNICALL
Java_dev_deiv_dear_llm_LlamaEngine_nativeLoad(JNIEnv * env, jclass, jstring jmodel, jstring jmmproj,
                                              jstring jlib_dir) {
    static std::once_flag backends_once;
    const std::string lib_dir = to_std(env, jlib_dir);
    std::call_once(backends_once, [&] {
        g_verbose_log = true;
        llama_log_set(log_callback, nullptr);
        mtmd_helper_log_set(log_callback, nullptr);
        ggml_backend_load_all_from_path(lib_dir.c_str());
        llama_backend_init();
        g_verbose_log = false;
    });

    const std::string model_path = to_std(env, jmodel);
    LOGi("Loading model %s", model_path.c_str());
    llama_model * model = llama_model_load_from_file(model_path.c_str(), llama_model_default_params());
    if (!model) {
        throw_java(env, "failed to load model");
        return 0;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx           = std::min<uint32_t>(N_CTX, llama_model_n_ctx_train(model));
    cparams.n_batch         = N_BATCH;
    cparams.n_ubatch        = N_BATCH;
    cparams.n_threads       = n_threads();
    cparams.n_threads_batch = n_threads();
    llama_context * ctx = llama_init_from_model(model, cparams);
    if (!ctx) {
        llama_model_free(model);
        throw_java(env, "failed to create context");
        return 0;
    }

    auto * dm = new dear_model();
    dm->model = model;
    dm->ctx   = ctx;
    dm->batch = common_batch(ctx);
    try {
        dm->tmpls = common_chat_templates_init(model, "");
    } catch (const std::exception & e) {
        free_model(dm);
        throw_java(env, e.what());
        return 0;
    }

    if (jmmproj) {
        const std::string mmproj_path = to_std(env, jmmproj);
        mtmd_context_params mparams = mtmd_context_params_default();
        mparams.use_gpu       = false;
        mparams.n_threads     = n_threads();
        mparams.print_timings = false;
        mparams.warmup        = false;
        dm->mctx = mtmd_init_from_file(mmproj_path.c_str(), model, mparams);
        if (!dm->mctx) {
            free_model(dm);
            throw_java(env, "failed to load multimodal projector");
            return 0;
        }
    }

    LOGi("Model ready: ctx=%u threads=%d vision=%d audio=%d", llama_n_ctx(ctx), n_threads(),
         dm->mctx && mtmd_support_vision(dm->mctx), dm->mctx && mtmd_support_audio(dm->mctx));
    return reinterpret_cast<jlong>(dm);
}

// --- JNI: capabilities ---

extern "C"
JNIEXPORT jboolean JNICALL
Java_dev_deiv_dear_llm_LlamaEngine_nativeSupportsVision(JNIEnv *, jclass, jlong handle) {
    auto * dm = reinterpret_cast<dear_model *>(handle);
    return dm && dm->mctx && mtmd_support_vision(dm->mctx);
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_dev_deiv_dear_llm_LlamaEngine_nativeSupportsAudio(JNIEnv *, jclass, jlong handle) {
    auto * dm = reinterpret_cast<dear_model *>(handle);
    return dm && dm->mctx && mtmd_support_audio(dm->mctx);
}

extern "C"
JNIEXPORT jint JNICALL
Java_dev_deiv_dear_llm_LlamaEngine_nativeAudioSampleRate(JNIEnv *, jclass, jlong handle) {
    auto * dm = reinterpret_cast<dear_model *>(handle);
    return dm && dm->mctx ? mtmd_get_audio_sample_rate(dm->mctx) : -1;
}

// --- JNI: generate ---
// Renders the chat with the model's own template, evaluates text + media, then samples token by
// token. Each complete UTF-8 chunk goes to listener.onToken(byte[]); returning false stops early.
// Returns {promptTokens, generatedTokens}.

extern "C"
JNIEXPORT jintArray JNICALL
Java_dev_deiv_dear_llm_LlamaEngine_nativeGenerate(JNIEnv * env, jclass, jlong handle,
                                                  jobjectArray jroles, jobjectArray jcontents,
                                                  jobjectArray jmedia, jintArray jmedia_dims,
                                                  jint max_tokens, jfloat temperature,
                                                  jstring jjson_schema, jobject listener) {
    auto * dm = reinterpret_cast<dear_model *>(handle);
    if (!dm) {
        throw_java(env, "model not loaded");
        return nullptr;
    }

    try {
        common_chat_templates_inputs inputs;
        const jsize n_msgs = env->GetArrayLength(jroles);
        for (jsize i = 0; i < n_msgs; ++i) {
            auto role    = (jstring) env->GetObjectArrayElement(jroles, i);
            auto content = (jstring) env->GetObjectArrayElement(jcontents, i);
            common_chat_msg msg;
            msg.role    = to_std(env, role);
            msg.content = to_std(env, content);
            inputs.messages.push_back(std::move(msg));
            env->DeleteLocalRef(role);
            env->DeleteLocalRef(content);
        }
        inputs.add_generation_prompt = true;
        inputs.use_jinja             = true;
        inputs.enable_thinking       = false;
        const common_chat_params chat = common_chat_templates_apply(dm->tmpls.get(), inputs);

        auto bitmaps = read_media(env, jmedia, jmedia_dims);
        const int32_t n_prompt = eval_prompt(dm, chat, bitmaps, max_tokens);

        common_params_sampling sparams;
        sparams.temp = temperature;
        const std::string schema = to_std(env, jjson_schema);
        if (!schema.empty()) {
            sparams.grammar = common_grammar(COMMON_GRAMMAR_TYPE_OUTPUT_FORMAT,
                                             json_schema_to_grammar(common_json::parse(schema)));
        }
        std::unique_ptr<common_sampler, decltype(&common_sampler_free)> smpl(
                common_sampler_init(dm->model, sparams), common_sampler_free);
        if (!smpl) throw std::runtime_error("failed to create sampler");

        jmethodID on_token = env->GetMethodID(env->GetObjectClass(listener), "onToken", "([B)Z");
        std::string pending;
        auto emit = [&](size_t len) -> bool {
            jbyteArray bytes = env->NewByteArray((jsize) len);
            env->SetByteArrayRegion(bytes, 0, (jsize) len, (const jbyte *) pending.data());
            const jboolean go_on = env->CallBooleanMethod(listener, on_token, bytes);
            env->DeleteLocalRef(bytes);
            pending.erase(0, len);
            return !env->ExceptionCheck() && go_on == JNI_TRUE;
        };

        const llama_vocab * vocab = llama_model_get_vocab(dm->model);
        const int n_ctx = (int) llama_n_ctx(dm->ctx);
        llama_pos n_past = n_prompt;
        int n_gen = 0;
        bool stopped = false;
        while (n_gen < max_tokens && n_past < n_ctx) {
            const llama_token tok = common_sampler_sample(smpl.get(), dm->ctx, -1);
            common_sampler_accept(smpl.get(), tok, true);
            if (llama_vocab_is_eog(vocab, tok)) break;
            ++n_gen;

            pending += common_token_to_piece(dm->ctx, tok);
            const size_t ready = utf8_complete_prefix(pending);
            if (ready > 0 && !emit(ready)) {
                stopped = true;
                break;
            }

            dm->batch.clear();
            dm->batch.add(tok, n_past++, 0, true);
            if (llama_process(dm->ctx, LLAMA_PROCESS_TYPE_DECODE, dm->batch.get()) != 0) {
                throw std::runtime_error("token decode failed");
            }
        }
        if (!stopped && !pending.empty()) emit(pending.size());
        if (env->ExceptionCheck()) return nullptr;

        jintArray stats = env->NewIntArray(2);
        const jint values[2] = {n_prompt, n_gen};
        env->SetIntArrayRegion(stats, 0, 2, values);
        return stats;
    } catch (const std::exception & e) {
        LOGe("generate failed: %s", e.what());
        throw_java(env, e.what());
        return nullptr;
    }
}

// --- JNI: free ---

extern "C"
JNIEXPORT void JNICALL
Java_dev_deiv_dear_llm_LlamaEngine_nativeFree(JNIEnv *, jclass, jlong handle) {
    LOGi("Freeing model resources");
    free_model(reinterpret_cast<dear_model *>(handle));
}
