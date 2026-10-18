package dev.deiv.dear.media

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.nio.ByteOrder
import kotlin.math.roundToInt

/** Decodes the audio track of an audio or video file into mono float PCM for the speech model. */
object AudioDecoder {
    const val TARGET_RATE = 16_000
    private const val MAX_SECONDS = 10 * 60
    private const val TIMEOUT_US = 10_000L
    private const val MAX_IDLE_POLLS = 200

    class Decoded(val pcm: FloatArray, val sampleRate: Int, val durationMs: Long)

    /** Returns null when [path] has no audio track. At most [maxSeconds] are decoded. */
    fun decode(path: String, rate: Int = TARGET_RATE, maxSeconds: Int = MAX_SECONDS): Decoded? {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(path)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return null
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val durationMs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) / 1000 else 0L

            val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            codec.configure(format, null, null, 0)
            codec.start()
            try {
                var srcRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                var floatPcm = false
                val mono = FloatList()
                val info = MediaCodec.BufferInfo()
                var inputDone = false
                var idleAfterInput = 0

                while (mono.size < maxSeconds.toLong() * srcRate) {
                    if (!inputDone) {
                        val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                        if (inIndex >= 0) {
                            val size = extractor.readSampleData(codec.getInputBuffer(inIndex)!!, 0)
                            if (size < 0) {
                                codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                    val outIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                    // Some decoders never flag end of stream: give up after ~2 s of silence once input ran out.
                    if (outIndex == MediaCodec.INFO_TRY_AGAIN_LATER && inputDone && ++idleAfterInput > MAX_IDLE_POLLS) break
                    if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        val out = codec.outputFormat
                        srcRate = out.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = out.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        floatPcm = out.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                            out.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                    } else if (outIndex >= 0) {
                        val buffer = codec.getOutputBuffer(outIndex)!!
                        buffer.position(info.offset).limit(info.offset + info.size)
                        buffer.order(ByteOrder.nativeOrder())
                        if (floatPcm) {
                            val samples = buffer.asFloatBuffer()
                            while (samples.remaining() >= channels) {
                                var sum = 0f
                                repeat(channels) { sum += samples.get() }
                                mono.add(sum / channels)
                            }
                        } else {
                            val samples = buffer.asShortBuffer()
                            while (samples.remaining() >= channels) {
                                var sum = 0f
                                repeat(channels) { sum += samples.get() / 32768f }
                                mono.add(sum / channels)
                            }
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    }
                }
                val pcm = resample(mono.toArray(), srcRate, rate)
                return Decoded(pcm, rate, durationMs.takeIf { it > 0 } ?: (pcm.size * 1000L / rate))
            } finally {
                codec.stop()
                codec.release()
            }
        } finally {
            extractor.release()
        }
    }

    /** Low-passes with a box filter as wide as the rate ratio, then interpolates; plenty for speech. */
    private fun resample(src: FloatArray, from: Int, to: Int): FloatArray {
        if (from == to || src.isEmpty()) return src
        val ratio = from.toDouble() / to
        val window = ratio.roundToInt().coerceAtLeast(1)
        val smoothed = if (window > 1) boxFilter(src, window) else src
        val out = FloatArray((src.size / ratio).toInt())
        for (i in out.indices) {
            val pos = i * ratio
            val i0 = pos.toInt().coerceAtMost(smoothed.lastIndex)
            val i1 = (i0 + 1).coerceAtMost(smoothed.lastIndex)
            val frac = (pos - i0).toFloat()
            out[i] = smoothed[i0] * (1 - frac) + smoothed[i1] * frac
        }
        return out
    }

    private fun boxFilter(src: FloatArray, window: Int): FloatArray {
        val out = FloatArray(src.size)
        var sum = 0f
        for (i in src.indices) {
            sum += src[i]
            if (i >= window) sum -= src[i - window]
            out[i] = sum / minOf(i + 1, window)
        }
        return out
    }

    /** Growable float buffer, avoids boxing a few million samples. */
    private class FloatList {
        private var data = FloatArray(1 shl 16)
        var size = 0
            private set

        fun add(value: Float) {
            if (size == data.size) data = data.copyOf(data.size * 2)
            data[size++] = value
        }

        fun toArray(): FloatArray = data.copyOf(size)
    }
}
