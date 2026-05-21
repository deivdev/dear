package dev.deiv.dear.llm

import android.util.Log
import dev.deiv.dear.data.db.EntryDao
import dev.deiv.dear.data.model.EntryType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class JobStatus { SUCCESS, FAILED, SKIPPED }

data class CatalogJob(
    val entryId: Long,
    val type: EntryType?,
    val startedAt: Long = System.currentTimeMillis(),
)

data class JobRecord(
    val entryId: Long,
    val type: EntryType?,
    val status: JobStatus,
    val message: String,
    val startedAt: Long,
    val durationMs: Long,
)

class CatalogQueue(
    private val scope: CoroutineScope,
    private val dao: EntryDao,
    private val modelManager: ModelManager,
) {
    private val channel = Channel<Long>(capacity = Channel.UNLIMITED)

    private val _current = MutableStateFlow<CatalogJob?>(null)
    val current: StateFlow<CatalogJob?> = _current.asStateFlow()

    private val _pendingCount = MutableStateFlow(0)
    val pendingCount: StateFlow<Int> = _pendingCount.asStateFlow()

    private val _history = MutableStateFlow<List<JobRecord>>(emptyList())
    val history: StateFlow<List<JobRecord>> = _history.asStateFlow()

    fun start() {
        scope.launch(Dispatchers.IO) {
            for (id in channel) {
                processOne(id)
                _pendingCount.value = (_pendingCount.value - 1).coerceAtLeast(0)
            }
        }
        scope.launch(Dispatchers.IO) {
            modelManager.state
                .filter { it == ModelState.READY }
                .collect {
                    val pending = dao.getUncataloged()
                    Log.i(TAG, "Backfill: ${pending.size} pending entries")
                    _pendingCount.value = pending.size
                    pending.forEach { entry -> channel.trySend(entry.id) }
                }
        }
    }

    fun enqueue(entryId: Long) {
        val result = channel.trySend(entryId)
        if (result.isSuccess) {
            _pendingCount.value++
        } else {
            Log.w(TAG, "Failed to enqueue entry $entryId")
        }
    }

    private suspend fun processOne(id: Long) {
        val entry = dao.getById(id) ?: run {
            record(id, null, JobStatus.SKIPPED, "entry missing", System.currentTimeMillis(), 0)
            return
        }
        if (!entry.tags.isNullOrBlank() || !entry.summary.isNullOrBlank()) {
            record(id, entry.type, JobStatus.SKIPPED, "already cataloged", System.currentTimeMillis(), 0)
            return
        }

        modelManager.state.filter { it == ModelState.READY }.first()
        val model = modelManager.getModel() ?: run {
            record(id, entry.type, JobStatus.FAILED, "model null", System.currentTimeMillis(), 0)
            return
        }

        val startedAt = System.currentTimeMillis()
        _current.value = CatalogJob(entryId = id, type = entry.type, startedAt = startedAt)
        Log.i(TAG, "Cataloging entry $id (type=${entry.type})")

        try {
            val cataloger = EntryCataloger(model)
            val result = cataloger.catalog(entry)
            val duration = System.currentTimeMillis() - startedAt
            if (result == null) {
                record(id, entry.type, JobStatus.FAILED, "cataloger returned null", startedAt, duration)
                return
            }
            val tagsCsv = result.tags.joinToString(",").takeIf { it.isNotBlank() }
            val summary = result.summary.takeIf { it.isNotBlank() }
            dao.updateCatalog(id, tagsCsv, summary)
            val msg = "tags=[${tagsCsv ?: "-"}] summary=${summary?.take(60) ?: "-"}"
            record(id, entry.type, JobStatus.SUCCESS, msg, startedAt, duration)
            Log.i(TAG, "Cataloged entry $id: $msg")
        } catch (t: Throwable) {
            val duration = System.currentTimeMillis() - startedAt
            record(id, entry.type, JobStatus.FAILED, t.message ?: t.javaClass.simpleName, startedAt, duration)
            Log.e(TAG, "Catalog failed for $id", t)
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
        private const val TAG = "CatalogQueue"
        private const val MAX_HISTORY = 50
    }
}
