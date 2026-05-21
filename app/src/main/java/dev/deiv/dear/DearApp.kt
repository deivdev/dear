package dev.deiv.dear

import android.app.Application
import dev.deiv.dear.data.db.DearDatabase
import dev.deiv.dear.data.repository.EntryRepository
import dev.deiv.dear.llm.CatalogQueue
import dev.deiv.dear.llm.ModelDownloader
import dev.deiv.dear.llm.ModelManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DearApp : Application() {

    lateinit var modelManager: ModelManager
        private set

    lateinit var entryRepository: EntryRepository
        private set

    lateinit var catalogQueue: CatalogQueue
        private set

    lateinit var modelDownloader: ModelDownloader
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        val db = DearDatabase.getInstance(this)
        val dao = db.entryDao()
        modelManager = ModelManager(this)
        modelDownloader = ModelDownloader(this, modelManager)
        catalogQueue = CatalogQueue(appScope, dao, modelManager)
        entryRepository = EntryRepository(this, dao) { id -> catalogQueue.enqueue(id) }
        catalogQueue.start()
        appScope.launch {
            modelManager.loadModel()
        }
    }
}
