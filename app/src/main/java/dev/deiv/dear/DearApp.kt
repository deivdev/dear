package dev.deiv.dear

import android.app.Application
import dev.deiv.dear.llm.ModelManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DearApp : Application() {

    lateinit var modelManager: ModelManager
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        modelManager = ModelManager(this)
        appScope.launch {
            modelManager.loadModel()
        }
    }
}
