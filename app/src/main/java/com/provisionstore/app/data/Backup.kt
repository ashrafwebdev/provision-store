package com.provisionstore.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A backup is a copy of the whole database file. Restoring replaces all current data. */
object Backup {

    fun exportIntent(context: Context): Intent {
        val db = AppDatabase.get(context)
        // Fold the write-ahead log into the main file so the copy is complete.
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }

        val source = context.getDatabasePath(AppDatabase.FILE_NAME)
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val target = File(dir, "provision-backup-$stamp.db")
        source.copyTo(target, overwrite = true)

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "Save backup")
    }

    /** Replaces the database with the file at [uri]. Call [restartApp] afterwards. */
    fun restore(context: Context, uri: Uri) {
        AppDatabase.close()
        val dbFile = context.getDatabasePath(AppDatabase.FILE_NAME)
        File(dbFile.path + "-wal").delete()
        File(dbFile.path + "-shm").delete()
        val input = context.contentResolver.openInputStream(uri)
            ?: error("Could not open the backup file.")
        input.use { source -> dbFile.outputStream().use { source.copyTo(it) } }
    }

    fun restartApp(context: Context) {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        if (intent != null) context.startActivity(intent)
        Runtime.getRuntime().exit(0)
    }
}
