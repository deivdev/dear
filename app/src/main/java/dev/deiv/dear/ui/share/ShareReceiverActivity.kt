package dev.deiv.dear.ui.share

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import dev.deiv.dear.DearApp
import dev.deiv.dear.data.repository.EntryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ShareReceiverActivity : ComponentActivity() {

    private val repo: EntryRepository
        get() = (application as DearApp).entryRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return finishWithToast("Nothing to capture")
        val sourceApp = intent.getStringExtra(Intent.EXTRA_REFERRER_NAME)
            ?: referrer?.host

        lifecycleScope.launch {
            val count = withContext(Dispatchers.IO) {
                when (intent.action) {
                    Intent.ACTION_SEND -> handleSend(intent, sourceApp)
                    Intent.ACTION_SEND_MULTIPLE -> handleSendMultiple(intent, sourceApp)
                    else -> 0
                }
            }
            finishWithToast(if (count > 0) "Captured $count item(s)" else "Capture failed")
        }
    }

    private suspend fun handleSend(intent: Intent, sourceApp: String?): Int {
        val mime = intent.type
        val uri = intent.getParcelableExtraCompat<Uri>(Intent.EXTRA_STREAM)
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
        return when {
            uri != null -> {
                runCatching { repo.addFromUri(uri, mime, sourceApp) }
                    .map { 1 }.getOrDefault(0)
            }
            !text.isNullOrBlank() -> {
                runCatching { repo.addText(text, sourceApp) }
                    .map { 1 }.getOrDefault(0)
            }
            else -> 0
        }
    }

    private suspend fun handleSendMultiple(intent: Intent, sourceApp: String?): Int {
        val uris = intent.getParcelableArrayListExtraCompat<Uri>(Intent.EXTRA_STREAM)
            ?: return 0
        val mime = intent.type
        var ok = 0
        for (u in uris) {
            runCatching { repo.addFromUri(u, mime, sourceApp) }
                .onSuccess { ok++ }
        }
        return ok
    }

    private fun finishWithToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        finish()
    }

    @Suppress("DEPRECATION")
    private inline fun <reified T : android.os.Parcelable> Intent.getParcelableExtraCompat(name: String): T? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(name, T::class.java)
        } else {
            getParcelableExtra(name) as? T
        }

    @Suppress("DEPRECATION")
    private inline fun <reified T : android.os.Parcelable> Intent.getParcelableArrayListExtraCompat(name: String): ArrayList<T>? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableArrayListExtra(name, T::class.java)
        } else {
            getParcelableArrayListExtra(name)
        }
}
