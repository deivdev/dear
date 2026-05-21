package dev.deiv.dear.llm

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

sealed class DownloadProgress {
    object Idle : DownloadProgress()
    data class Running(val bytesDownloaded: Long, val totalBytes: Long, val status: Int) : DownloadProgress()
    data class Done(val file: File) : DownloadProgress()
    data class Failed(val reason: String) : DownloadProgress()
}

class ModelDownloader(private val context: Context, private val modelManager: ModelManager) {

    private val dm by lazy {
        context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    }

    fun start(model: CatalogModel): Long {
        // Use external cache as DownloadManager target, then move to internal model dir on completion.
        val cacheDir = File(context.externalCacheDir ?: context.cacheDir, "model-downloads").apply { mkdirs() }
        val dest = File(cacheDir, model.fileName)
        if (dest.exists()) dest.delete()

        val req = DownloadManager.Request(Uri.parse(model.url)).apply {
            setTitle(model.displayName)
            setDescription("Downloading model for Dear")
            setAllowedOverMetered(true)
            setAllowedOverRoaming(false)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            setDestinationUri(Uri.fromFile(dest))
            addRequestHeader("User-Agent", "Mozilla/5.0 (Linux; Android) Dear/1.0")
        }
        return dm.enqueue(req)
    }

    fun cancel(id: Long) {
        dm.remove(id)
    }

    fun observe(id: Long, pollMs: Long = 500): Flow<DownloadProgress> = flow {
        emit(DownloadProgress.Idle)
        while (true) {
            val q = DownloadManager.Query().setFilterById(id)
            val cursor: Cursor = dm.query(q) ?: run {
                emit(DownloadProgress.Failed("query returned null"))
                return@flow
            }
            cursor.use {
                if (!it.moveToFirst()) {
                    emit(DownloadProgress.Failed("download not found"))
                    return@flow
                }
                val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val downloaded = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val total = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        val localUri = it.getString(it.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
                        val src = Uri.parse(localUri).path?.let { p -> File(p) }
                        if (src == null || !src.exists()) {
                            emit(DownloadProgress.Failed("downloaded file missing"))
                            return@flow
                        }
                        val finalFile = moveIntoModelDir(src)
                        emit(DownloadProgress.Done(finalFile))
                        return@flow
                    }
                    DownloadManager.STATUS_FAILED -> {
                        val reason = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                        emit(DownloadProgress.Failed("failed (reason=$reason)"))
                        return@flow
                    }
                    else -> emit(DownloadProgress.Running(downloaded, total, status))
                }
            }
            delay(pollMs)
        }
    }.flowOn(Dispatchers.IO)

    private fun moveIntoModelDir(src: File): File {
        val dest = File(modelManager.modelDir, src.name)
        if (dest.exists()) dest.delete()
        if (!src.renameTo(dest)) {
            // cross-volume fallback: copy + delete
            src.inputStream().use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            src.delete()
        }
        Log.i(TAG, "Model moved to ${dest.absolutePath}")
        return dest
    }

    companion object {
        private const val TAG = "ModelDownloader"
    }
}
