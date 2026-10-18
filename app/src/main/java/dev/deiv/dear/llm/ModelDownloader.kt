package dev.deiv.dear.llm

import android.app.DownloadManager
import android.content.Context
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
    data class Running(val bytesDownloaded: Long, val totalBytes: Long) : DownloadProgress()
    data class Done(val model: CatalogModel) : DownloadProgress()
    data class Failed(val reason: String) : DownloadProgress()
}

class ModelDownloader(private val context: Context, private val modelManager: ModelManager) {

    private val dm by lazy {
        context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    }

    private val downloadDir: File
        get() = File(context.externalCacheDir ?: context.cacheDir, "model-downloads").apply { mkdirs() }

    /** Enqueues the files of [model] that are not installed yet; returns their DownloadManager ids. */
    fun start(model: CatalogModel): List<Long> =
        model.files
            .filterNot { File(modelManager.modelDir, it.fileName).exists() }
            .map { file -> enqueue(file, model.displayName) }

    fun cancel(ids: List<Long>) {
        if (ids.isNotEmpty()) dm.remove(*ids.toLongArray())
    }

    /**
     * Polls the downloads of [model]. Files are moved into the model directory only once all of
     * them finished, so a model never becomes visible without its projector.
     */
    fun observe(model: CatalogModel, ids: List<Long>, pollMs: Long = 500): Flow<DownloadProgress> = flow {
        emit(DownloadProgress.Idle)
        val expectedBytes = model.files.sumOf { it.sizeMB.toLong() * 1_000_000 }
        while (true) {
            var downloaded = 0L
            val finished = mutableListOf<File>()
            for (id in ids) {
                val row = query(id) ?: run {
                    emit(DownloadProgress.Failed("download $id not found"))
                    return@flow
                }
                when (row.status) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        val file = row.localFile ?: run {
                            emit(DownloadProgress.Failed("downloaded file missing"))
                            return@flow
                        }
                        downloaded += file.length()
                        finished += file
                    }
                    DownloadManager.STATUS_FAILED -> {
                        emit(DownloadProgress.Failed("failed (reason=${row.reason})"))
                        return@flow
                    }
                    else -> downloaded += row.bytesSoFar
                }
            }
            if (finished.size == ids.size) {
                finished.forEach(::moveIntoModelDir)
                modelManager.refresh()
                emit(DownloadProgress.Done(model))
                return@flow
            }
            emit(DownloadProgress.Running(downloaded, expectedBytes))
            delay(pollMs)
        }
    }.flowOn(Dispatchers.IO)

    private fun enqueue(file: ModelFile, title: String): Long {
        val dest = File(downloadDir, file.fileName)
        if (dest.exists()) dest.delete()
        val req = DownloadManager.Request(Uri.parse(file.url)).apply {
            setTitle("$title · ${file.fileName}")
            setDescription("Downloading model for Dear")
            setAllowedOverMetered(true)
            setAllowedOverRoaming(false)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            setDestinationUri(Uri.fromFile(dest))
            addRequestHeader("User-Agent", "Mozilla/5.0 (Linux; Android) Dear/1.0")
        }
        return dm.enqueue(req)
    }

    private class Row(val status: Int, val reason: Int, val bytesSoFar: Long, val localFile: File?)

    private fun query(id: Long): Row? =
        dm.query(DownloadManager.Query().setFilterById(id))?.use { c ->
            if (!c.moveToFirst()) return null
            val localUri = c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
            Row(
                status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                reason = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)),
                bytesSoFar = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                localFile = localUri?.let { Uri.parse(it).path }?.let { File(it) }?.takeIf { it.exists() },
            )
        }

    private fun moveIntoModelDir(src: File) {
        val dest = File(modelManager.modelDir, src.name)
        if (dest.exists()) dest.delete()
        if (!src.renameTo(dest)) {
            // cross-volume fallback: copy + delete
            src.inputStream().use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            src.delete()
        }
        // The newest model file is the active one.
        dest.setLastModified(System.currentTimeMillis())
        Log.i(TAG, "Model file moved to ${dest.absolutePath}")
    }

    companion object {
        private const val TAG = "ModelDownloader"
    }
}
