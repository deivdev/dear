package dev.deiv.dear.catalog

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import dev.deiv.dear.DearApp
import dev.deiv.dear.R
import dev.deiv.dear.ui.MainActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CatalogWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as DearApp
        return try {
            coroutineScope {
                val processor = app.catalogProcessor
                val progress = if (processor.hasPending() && startForeground()) {
                    launch { processor.pendingCount.collect { updateNotification(it) } }
                } else {
                    null
                }
                processor.runPending()
                progress?.cancel()
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Log.e(TAG, "Catalog run failed", t)
            Result.retry()
        } finally {
            // Once the run ends the process can be cached and frozen: don't leave GBs of weights behind.
            withContext(NonCancellable) { app.modelManager.release() }
        }
    }

    /**
     * As a foreground service the run gets every core (in the background only the slow ones) and is
     * not stopped after a few minutes. Android allows that only while the user is around, e.g. right
     * after sharing; otherwise the run carries on in the background.
     */
    private suspend fun startForeground(): Boolean = try {
        setForeground(getForegroundInfo())
        true
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        Log.i(TAG, "Cataloging in the background: ${t.message}")
        false
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = notification(pending = null)
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM ->
                ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            else -> ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    /** Updates the foreground notification with what is left in the queue. */
    private fun updateNotification(pending: Int) {
        applicationContext.getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notification(pending))
    }

    private fun notification(pending: Int?): Notification {
        val context = applicationContext
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(
                pending?.takeIf { it > 0 }
                    ?.let { context.resources.getQuantityString(R.plurals.catalog_pending, it, it) }
                    ?: context.getString(R.string.catalog_notification_text)
            )
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    companion object {
        private const val TAG = "CatalogWorker"
        private const val CHANNEL_ID = "catalog"
        private const val NOTIFICATION_ID = 1001

        fun createNotificationChannel(context: Context) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.catalog_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            )
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
