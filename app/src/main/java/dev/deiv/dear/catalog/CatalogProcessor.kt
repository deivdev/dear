package dev.deiv.dear.catalog

import android.content.Context
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import dev.deiv.dear.data.db.EntryDao
import dev.deiv.dear.data.model.CatalogStatus
import dev.deiv.dear.data.model.EntryType
import dev.deiv.dear.llm.CatalogStep
import dev.deiv.dear.llm.EntryCataloger
import dev.deiv.dear.llm.ModelManager
import dev.deiv.dear.llm.ModelUnavailableException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

enum class JobStatus { SUCCESS, FAILED, SKIPPED }

data class CatalogJob(
    val entryId: Long,
    val type: EntryType?,
    val startedAt: Long = System.currentTimeMillis(),
    /** For audio and video: which frame or audio chunk is being looked at. */
    val step: CatalogStep? = null,
)

data class JobRecord(
    val entryId: Long,
    val type: EntryType?,
    val status: JobStatus,
    val message: String,
    val startedAt: Long,
    val durationMs: Long,
)

/**
 * Catalogs pending entries. The work itself runs inside [CatalogWorker], so Android keeps the
 * process alive (and unfrozen) while it runs, even after the user left the app.
 */
class CatalogProcessor(
    private val context: Context,
    private val dao: EntryDao,
    private val modelManager: ModelManager,
) {
    private val _current = MutableStateFlow<CatalogJob?>(null)
    val current: StateFlow<CatalogJob?> = _current.asStateFlow()

    private val _history = MutableStateFlow<List<JobRecord>>(emptyList())
    val history: StateFlow<List<JobRecord>> = _history.asStateFlow()

    /** Entries waiting for (or in) cataloging, straight from the database. */
    val pendingCount: Flow<Int> = dao.observeCatalogableCount(MAX_ATTEMPTS)

    /**
     * Requests a catalog run. Cheap and safe to call often: a run already in progress keeps going
     * and this one is appended behind it, so nothing inserted meanwhile is missed.
     */
    fun schedule() {
        val request = OneTimeWorkRequestBuilder<CatalogWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    /** Catalogs until nothing is left. Only [CatalogWorker] calls this, one run at a time. */
    suspend fun runPending() {
        if (modelManager.installed() == null) {
            Log.i(TAG, "No model installed, nothing to do")
            return
        }
        // Runs never overlap, so anything still PROCESSING was cut short by a killed process.
        val interrupted = dao.resetInterrupted()
        if (interrupted > 0) Log.i(TAG, "Re-queued $interrupted interrupted entries")

        // Re-query every time to pick up entries shared while this run is going; each entry is
        // tried at most once per run so a failing one cannot spin.
        val tried = mutableSetOf<Long>()
        while (true) {
            val id = dao.getCatalogable(MAX_ATTEMPTS).firstOrNull { it !in tried } ?: break
            tried += id
            if (!processOne(id)) break
        }
        Log.i(TAG, "Run finished: ${tried.size} entries")
    }

    /** Returns false when the model cannot run, which ends the current run. */
    private suspend fun processOne(id: Long): Boolean {
        val entry = dao.getById(id) ?: return true
        if (entry.status == CatalogStatus.DONE) return true

        val startedAt = System.currentTimeMillis()
        _current.value = CatalogJob(entryId = id, type = entry.type, startedAt = startedAt)
        dao.markProcessing(id)
        Log.i(TAG, "Cataloging entry $id (type=${entry.type}, attempt=${entry.attempts + 1})")

        try {
            val cataloger = EntryCataloger(
                modelManager = modelManager,
                saveState = { dao.updateCatalogState(id, it) },
                report = { step -> _current.value = _current.value?.copy(step = step) },
            )
            val result = cataloger.catalog(entry)
            result.durationMs?.let { dao.updateDuration(id, it) }
            dao.saveCatalog(
                id = id,
                title = result.title,
                summary = result.summary.takeIf { it.isNotBlank() },
                extractedText = result.extractedText,
                tags = result.tags,
                model = modelManager.installed()?.name,
            )
            val duration = System.currentTimeMillis() - startedAt
            val msg = "tags=[${result.tags.joinToString(",").ifBlank { "-" }}] " +
                "summary=${result.summary.take(60).ifBlank { "-" }}"
            record(id, entry.type, JobStatus.SUCCESS, msg, startedAt, duration)
            Log.i(TAG, "Cataloged entry $id in ${duration}ms")
            return true
        } catch (e: CancellationException) {
            // Stopped by the system: not the entry's fault, WorkManager resumes the run later.
            withContext(NonCancellable) { dao.requeue(id) }
            throw e
        } catch (e: ModelUnavailableException) {
            dao.requeue(id)
            record(id, entry.type, JobStatus.SKIPPED, e.message ?: "model unavailable", startedAt, 0)
            Log.w(TAG, "Model unavailable, entry $id re-queued: ${e.message}")
            return false
        } catch (t: Throwable) {
            val duration = System.currentTimeMillis() - startedAt
            val reason = t.message ?: t.javaClass.simpleName
            dao.markFailed(id, reason)
            record(id, entry.type, JobStatus.FAILED, reason, startedAt, duration)
            Log.e(TAG, "Catalog failed for $id", t)
            return true
        } finally {
            _current.value = null
        }
    }

    private fun record(
        entryId: Long,
        type: EntryType?,
        status: JobStatus,
        message: String,
        startedAt: Long,
        durationMs: Long,
    ) {
        val rec = JobRecord(entryId, type, status, message, startedAt, durationMs)
        _history.value = (listOf(rec) + _history.value).take(MAX_HISTORY)
    }

    companion object {
        private const val TAG = "CatalogProcessor"
        private const val WORK_NAME = "catalog"
        private const val MAX_HISTORY = 50
        const val MAX_ATTEMPTS = 3
    }
}
