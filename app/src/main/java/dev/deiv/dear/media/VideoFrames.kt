package dev.deiv.dear.media

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build

/** Grabs a few evenly spaced frames from a video, for the vision model to look at. */
object VideoFrames {

    class Frames(val bitmaps: List<Bitmap>, val durationMs: Long)

    /** Short clips need fewer frames than long ones to be understood. */
    fun frameCountFor(durationMs: Long): Int = when {
        durationMs <= 30_000 -> 3
        durationMs <= 120_000 -> 4
        else -> 6
    }

    fun durationMs(path: String): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } finally {
            retriever.release()
        }
    }

    /** Frames at the middle of [count] equal slices of the video, scaled to fit [maxSide]. */
    fun extract(path: String, count: Int, maxSide: Int): Frames {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(path)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val n = if (duration > 0) count else 1
            val frames = (0 until n).mapNotNull { i ->
                val timeUs = if (duration > 0) duration * 1000 * (2 * i + 1) / (2 * n) else 0L
                frameAt(retriever, timeUs, maxSide)
            }
            return Frames(frames, duration)
        } finally {
            retriever.release()
        }
    }

    private fun frameAt(retriever: MediaMetadataRetriever, timeUs: Long, maxSide: Int): Bitmap? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            retriever.getScaledFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, maxSide, maxSide)
        } else {
            retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        }
}
