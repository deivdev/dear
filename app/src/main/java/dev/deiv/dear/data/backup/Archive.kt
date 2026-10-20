package dev.deiv.dear.data.backup

import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.work.WorkManager
import dev.deiv.dear.data.db.DearDatabase
import dev.deiv.dear.data.repository.EntryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * The whole collection as one zip: a snapshot of the database (entries, tags, chats) and the files
 * of the entries. Models are left out: they are gigabytes and can be downloaded or imported again.
 * Importing replaces the current collection and restarts the app, since Room cannot reopen in place.
 */
class Archive(private val context: Context, private val db: DearDatabase) {

    private val dbFile: File get() = context.getDatabasePath(DB_NAME)

    /** Writes the archive to [uri] and returns how many entries it holds. */
    suspend fun export(uri: Uri): Int = withContext(Dispatchers.IO) {
        val snapshot = File(context.cacheDir, "export-$DB_NAME").apply { delete() }
        try {
            snapshot(snapshot)
            val count = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM entries").use {
                if (it.moveToFirst()) it.getInt(0) else 0
            }
            val manifest = JSONObject()
                .put("format", FORMAT)
                .put("dbVersion", db.openHelper.readableDatabase.version)
                .put("filesDir", context.filesDir.path)
                .put("exportedAt", System.currentTimeMillis())
            val out = context.contentResolver.openOutputStream(uri) ?: error("Cannot open $uri")
            // Media is already compressed: the fastest level is as good as any.
            ZipOutputStream(out.buffered()).apply { setLevel(Deflater.BEST_SPEED) }.use { zip ->
                zip.putNextEntry(ZipEntry(MANIFEST))
                zip.write(manifest.toString().toByteArray())
                zip.closeEntry()
                zip.putNextEntry(ZipEntry(DB_NAME))
                snapshot.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
                EntryRepository.entriesDir(context).listFiles()?.filter { it.isFile }?.forEach { file ->
                    zip.putNextEntry(ZipEntry("$ENTRIES/${file.name}"))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            Log.i(TAG, "Exported $count entries")
            count
        } finally {
            snapshot.delete()
        }
    }

    /**
     * Replaces the collection with the archive at [uri], then restarts the app. Everything is unpacked
     * and checked before the current data is touched; on any failure the collection stays as it was.
     */
    suspend fun import(uri: Uri): Unit = withContext(Dispatchers.IO) {
        val staging = File(context.filesDir, "import-staging").apply { deleteRecursively() }
        try {
            val stagedDb = File(staging, DB_NAME)
            val manifest = unpack(uri, staging)
            if (manifest.optInt("format") != FORMAT) throw IOException("Not a Dear archive")
            if (!stagedDb.exists()) throw IOException("The archive has no database")
            prepare(stagedDb, manifest.optString("filesDir"))

            // Point of no return: Room is closed from here on, so the app restarts whatever happens.
            WorkManager.getInstance(context).cancelAllWork()
            db.close()
            try {
                swapIn(stagedDb, File(staging, ENTRIES))
                Log.i(TAG, "Archive imported, restarting")
            } catch (t: Throwable) {
                Log.e(TAG, "Import failed while swapping, the collection was kept", t)
            } finally {
                staging.deleteRecursively()
                restart()
            }
        } finally {
            staging.deleteRecursively()
        }
    }

    /** Files first, then the database, which a rename replaces atomically; a failed step rolls the files back. */
    private fun swapIn(stagedDb: File, stagedEntries: File) {
        val entriesDir = EntryRepository.entriesDir(context)
        val previous = File(context.filesDir, "entries-previous").apply { deleteRecursively() }
        if (!entriesDir.renameTo(previous)) throw IOException("Cannot move the current files aside")
        if (!stagedEntries.renameTo(entriesDir)) {
            previous.renameTo(entriesDir)
            throw IOException("Cannot move the files in place")
        }
        // A cleanly closed database has nothing left in its WAL.
        listOf("-wal", "-shm", "-journal").forEach { File(dbFile.path + it).delete() }
        if (!stagedDb.renameTo(dbFile)) {
            entriesDir.deleteRecursively()
            previous.renameTo(entriesDir)
            throw IOException("Cannot move the database in place")
        }
        previous.deleteRecursively()
    }

    private fun snapshot(dest: File) {
        val sql = db.openHelper.writableDatabase
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            sql.execSQL("VACUUM INTO '${dest.path.replace("'", "''")}'")
        } else {
            // VACUUM INTO needs SQLite 3.27 (Android 11): fold the WAL into the main file and copy that.
            sql.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
            dbFile.copyTo(dest, overwrite = true)
        }
    }

    /** Extracts the known parts of the archive into [dir] and returns its manifest. */
    private fun unpack(uri: Uri, dir: File): JSONObject {
        var manifest: JSONObject? = null
        val entriesDir = File(dir, ENTRIES).apply { mkdirs() }
        val input = context.contentResolver.openInputStream(uri) ?: error("Cannot open $uri")
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name
                when {
                    entry.isDirectory -> Unit
                    name == MANIFEST -> manifest = JSONObject(zip.readBytes().decodeToString())
                    name == DB_NAME -> File(dir, DB_NAME).outputStream().use { zip.copyTo(it) }
                    name.startsWith("$ENTRIES/") -> {
                        // Only plain file names: nothing in the archive can write outside the folder.
                        val fileName = name.removePrefix("$ENTRIES/")
                        if (fileName.isEmpty() || fileName.contains('/') || fileName.startsWith(".")) {
                            throw IOException("Unexpected file in the archive: $name")
                        }
                        File(entriesDir, fileName).outputStream().use { zip.copyTo(it) }
                    }
                }
            }
        }
        return manifest ?: throw IOException("Not a Dear archive")
    }

    /** Checks the staged database can be opened by this version and points its files to this install. */
    private fun prepare(stagedDb: File, exportedFilesDir: String) {
        val currentVersion = db.openHelper.readableDatabase.version
        val flags = SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.NO_LOCALIZED_COLLATORS
        SQLiteDatabase.openDatabase(stagedDb.path, null, flags).use { sql ->
            if (sql.version > currentVersion) throw IOException("The archive comes from a newer version of Dear")
            val filesDir = context.filesDir.path
            if (exportedFilesDir.isNotEmpty() && exportedFilesDir != filesDir) {
                sql.execSQL(
                    "UPDATE entries SET filePath = ? || substr(filePath, ?) WHERE substr(filePath, 1, ?) = ?",
                    arrayOf(filesDir, exportedFilesDir.length + 1, exportedFilesDir.length, exportedFilesDir),
                )
            }
        }
    }

    private fun restart() {
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            context.startActivity(Intent.makeRestartActivityTask(it.component))
        }
        Runtime.getRuntime().exit(0)
    }

    companion object {
        private const val TAG = "Archive"
        private const val FORMAT = 1
        private const val DB_NAME = "dear.db"
        private const val MANIFEST = "manifest.json"
        private const val ENTRIES = "entries"
    }
}
