package dev.deiv.dear.catalog

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.deiv.dear.DearApp

/**
 * Restarts cataloging that Android dropped. A run whose process is killed half-way (frozen, or
 * too much CPU in the background) is not always resumed, and the queue would otherwise wait until
 * the app is opened again.
 */
class CatalogWatchdogWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val processor = (applicationContext as DearApp).catalogProcessor
        if (processor.hasPending()) processor.ensureScheduled()
        return Result.success()
    }
}
