package dev.deiv.dear.llm

import android.app.ActivityManager
import android.content.Context
import android.os.Debug

data class ModelStats(
    val modelName: String,
    val modelSizeMB: Long,
    val multimodal: Boolean,
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
        val model = modelManager.installed() ?: return null
        val last = modelManager.lastStats.value

        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val procMem = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }
        val procKb = procMem.totalPss.toLong() // PSS in KB

        return ModelStats(
            modelName = model.name,
            modelSizeMB = (model.file.length() + (model.mmproj?.length() ?: 0)) / 1_000_000,
            multimodal = model.mmproj != null,
            processRamMB = procKb / 1024,
            deviceTotalRamMB = mi.totalMem / 1_000_000,
            deviceAvailRamMB = mi.availMem / 1_000_000,
            lowMemory = mi.lowMemory,
            tokensPerSec = last?.tokensPerSec ?: 0.0,
            lastTokenCount = last?.generatedTokens ?: 0,
            lastDurationMs = last?.durationMs ?: 0,
        )
    }
}
