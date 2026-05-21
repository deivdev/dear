package dev.deiv.dear.llm

import android.app.ActivityManager
import android.content.Context
import android.os.Debug

data class ModelStats(
    val modelName: String,
    val modelSizeMB: Long,
    val processRamMB: Long,
    val deviceTotalRamMB: Long,
    val deviceAvailRamMB: Long,
    val lowMemory: Boolean,
    val tokensPerSec: Double,
    val lastTokenCount: Int,
    val lastDurationMs: Long,
)

object ModelStatsCollector {
    fun collect(context: Context, modelManager: ModelManager): ModelStats? {
        val file = modelManager.currentModelFile ?: return null
        val model = modelManager.getModel() ?: return null

        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val procMem = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }
        val procKb = procMem.totalPss.toLong() // PSS in KB

        return ModelStats(
            modelName = file.name,
            modelSizeMB = file.length() / 1_000_000,
            processRamMB = procKb / 1024,
            deviceTotalRamMB = mi.totalMem / 1_000_000,
            deviceAvailRamMB = mi.availMem / 1_000_000,
            lowMemory = mi.lowMemory,
            tokensPerSec = model.lastTokensPerSec,
            lastTokenCount = model.lastTokenCount,
            lastDurationMs = model.lastDurationMs,
        )
    }
}
