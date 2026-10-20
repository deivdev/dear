package dev.deiv.dear

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.video.VideoFrameDecoder
import dev.deiv.dear.catalog.CatalogProcessor
import dev.deiv.dear.catalog.CatalogWorker
import dev.deiv.dear.data.backup.Archive
import dev.deiv.dear.data.db.DearDatabase
import dev.deiv.dear.data.repository.ChatRepository
import dev.deiv.dear.data.repository.EntryRepository
import dev.deiv.dear.llm.ModelDownloader
import dev.deiv.dear.llm.ModelManager
import dev.deiv.dear.llm.ModelState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class DearApp : Application(), SingletonImageLoader.Factory {

    lateinit var modelManager: ModelManager
        private set

    lateinit var entryRepository: EntryRepository
        private set

    lateinit var catalogProcessor: CatalogProcessor
        private set

    lateinit var chatRepository: ChatRepository
        private set

    lateinit var modelDownloader: ModelDownloader
        private set

    lateinit var archive: Archive
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        val db = DearDatabase.getInstance(this)
        val dao = db.entryDao()
        modelManager = ModelManager(this, appScope)
        modelDownloader = ModelDownloader(this, modelManager)
        catalogProcessor = CatalogProcessor(this, dao, modelManager)
        entryRepository = EntryRepository(this, dao, requestCatalog = catalogProcessor::schedule)
        chatRepository = ChatRepository(db.chatDao())
        archive = Archive(this, db)
        CatalogWorker.createNotificationChannel(this)
        catalogProcessor.scheduleWatchdog()

        // The model is loaded on demand by whoever needs it, not at every process start. A run is
        // requested at startup (leftovers) and whenever a model gets installed.
        appScope.launch {
            modelManager.state
                .map { it != ModelState.NOT_INSTALLED }
                .distinctUntilChanged()
                .filter { it }
                .collect { catalogProcessor.ensureScheduled() }
        }
    }

    /** Coil with video support, so video entries get a frame as thumbnail. */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(VideoFrameDecoder.Factory()) }
            .build()
}
