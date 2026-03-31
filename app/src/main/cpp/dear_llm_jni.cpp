#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include <unistd.h>

#include "common.h"
#include "sampling.h"
#include "llama.h"

#define TAG "DearLLM"
#define LOGi(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGe(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// Per-model context holder
struct dear_context {
    llama_model   *model   = nullptr;
    llama_context *ctx     = nullptr;
    llama_batch    batch;
    common_sampler *sampler = nullptr;
};

static constexpr int N_CTX       = 4096;
static constexpr int BATCH_SIZE  = 512;

static int get_n_threads() {
    int n = (int)sysconf(_SC_NPROCESSORS_ONLN) - 2;
    if (n < 2) n = 2;
    if (n > 4) n = 4;
    return n;
}

// --- JNI: Load model ---

extern "C"
JNIEXPORT jlong JNICALL
Java_dev_deiv_dear_llm_LlamaModel_nativeLoadModel(JNIEnv *env, jclass, jstring jpath) {
    const char *path = env->GetStringUTFChars(jpath, nullptr);
    LOGi("Loading model from: %s", path);

    // Load backends
    ggml_backend_load_all();
    llama_backend_init();

    // Load model
    llama_model_params model_params = llama_model_default_params();
    llama_model *model = llama_model_load_from_file(path, model_params);
    env->ReleaseStringUTFChars(jpath, path);

    if (!model) {
        LOGe("Failed to load model");
        return 0;
    }

    // Create context
    int n_threads = get_n_threads();
    LOGi("Using %d threads", n_threads);

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx           = N_CTX;
    ctx_params.n_batch         = BATCH_SIZE;
    ctx_params.n_ubatch        = BATCH_SIZE;
    ctx_params.n_threads       = n_threads;
    ctx_params.n_threads_batch = n_threads;

    llama_context *ctx = llama_init_from_model(model, ctx_params);
    if (!ctx) {
        LOGe("Failed to create context");
        llama_model_free(model);
        return 0;
    }

    // Create sampler
    common_params_sampling sparams;
    sparams.temp = 0.7f;
    common_sampler *sampler = common_sampler_init(model, sparams);

    // Pack into context holder
    auto *dc = new dear_context();
    dc->model   = model;
    dc->ctx     = ctx;
    dc->batch   = llama_batch_init(BATCH_SIZE, 0, 1);
    dc->sampler = sampler;

    LOGi("Model loaded successfully");
    return reinterpret_cast<jlong>(dc);
}

// --- JNI: Generate tokens ---

extern "C"
JNIEXPORT jobjectArray JNICALL
Java_dev_deiv_dear_llm_LlamaModel_nativeGenerate(JNIEnv *env, jobject, jlong ctxPtr, jstring jprompt, jint maxTokens) {
    auto *dc = reinterpret_cast<dear_context *>(ctxPtr);
    if (!dc || !dc->ctx) {
        jclass strClass = env->FindClass("java/lang/String");
        jobjectArray result = env->NewObjectArray(1, strClass, nullptr);
        env->SetObjectArrayElement(result, 0, env->NewStringUTF("[Error: invalid context]"));
        return result;
    }

    const char *prompt_cstr = env->GetStringUTFChars(jprompt, nullptr);
    std::string prompt(prompt_cstr);
    env->ReleaseStringUTFChars(jprompt, prompt_cstr);

    // Clear KV cache
    llama_memory_clear(llama_get_memory(dc->ctx), false);
    common_sampler_reset(dc->sampler);

    // Tokenize prompt
    auto tokens = common_tokenize(dc->ctx, prompt, true, true);
    LOGi("Prompt tokens: %d", (int)tokens.size());

    if ((int)tokens.size() > N_CTX - 4) {
        tokens.resize(N_CTX - 4);
        LOGi("Prompt truncated to %d tokens", (int)tokens.size());
    }

    // Decode prompt in batches
    for (int i = 0; i < (int)tokens.size(); i += BATCH_SIZE) {
        int batch_size = std::min((int)tokens.size() - i, BATCH_SIZE);
        common_batch_clear(dc->batch);
        for (int j = 0; j < batch_size; j++) {
            bool last = (i + j == (int)tokens.size() - 1);
            common_batch_add(dc->batch, tokens[i + j], i + j, {0}, last);
        }
        if (llama_decode(dc->ctx, dc->batch) != 0) {
            LOGe("llama_decode failed during prompt processing");
            break;
        }
    }

    // Generate tokens
    std::vector<std::string> output_tokens;
    std::string cached_chars;
    int pos = (int)tokens.size();
    const llama_vocab *vocab = llama_model_get_vocab(dc->model);

    for (int i = 0; i < maxTokens; i++) {
        llama_token new_token = common_sampler_sample(dc->sampler, dc->ctx, -1);
        common_sampler_accept(dc->sampler, new_token, true);

        // Check for end of generation
        if (llama_vocab_is_eog(vocab, new_token)) {
            if (!cached_chars.empty()) {
                output_tokens.push_back(cached_chars);
            }
            break;
        }

        // Convert token to text
        std::string piece = common_token_to_piece(dc->ctx, new_token);
        cached_chars += piece;

        // Only emit valid UTF-8
        // Simple check: if last byte is a continuation starter, keep caching
        unsigned char last_byte = (unsigned char)cached_chars.back();
        bool might_be_incomplete = (last_byte & 0x80) != 0;
        if (!might_be_incomplete || cached_chars.size() > 4) {
            output_tokens.push_back(cached_chars);
            cached_chars.clear();
        }

        // Decode the new token for next iteration
        common_batch_clear(dc->batch);
        common_batch_add(dc->batch, new_token, pos, {0}, true);
        if (llama_decode(dc->ctx, dc->batch) != 0) {
            LOGe("llama_decode failed during generation at pos %d", pos);
            break;
        }
        pos++;
    }

    // Flush any remaining cached chars
    if (!cached_chars.empty()) {
        output_tokens.push_back(cached_chars);
    }

    LOGi("Generated %d token chunks", (int)output_tokens.size());

    // Convert to Java String array
    jclass strClass = env->FindClass("java/lang/String");
    jobjectArray result = env->NewObjectArray((int)output_tokens.size(), strClass, nullptr);
    for (int i = 0; i < (int)output_tokens.size(); i++) {
        env->SetObjectArrayElement(result, i, env->NewStringUTF(output_tokens[i].c_str()));
    }
    return result;
}

// --- JNI: Free ---

extern "C"
JNIEXPORT void JNICALL
Java_dev_deiv_dear_llm_LlamaModel_nativeFree(JNIEnv *, jobject, jlong ctxPtr) {
    auto *dc = reinterpret_cast<dear_context *>(ctxPtr);
    if (!dc) return;

    LOGi("Freeing model resources");
    if (dc->sampler) common_sampler_free(dc->sampler);
    llama_batch_free(dc->batch);
    if (dc->ctx)   llama_free(dc->ctx);
    if (dc->model) llama_model_free(dc->model);
    llama_backend_free();
    delete dc;
}
