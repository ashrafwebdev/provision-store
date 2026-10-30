package com.provisionstore.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(val versionCode: Int, val apkUrl: String)

/**
 * Checks the latest GitHub release and installs it.
 * Releases are tagged "v<versionCode>" (for example "v42") and carry the APK as an asset.
 */
object UpdateChecker {

    private val latestUrl = "https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest"

    /**
     * Returns the newer release, or null if already up to date.
     * Throws if the check fails, for example when there is no internet.
     */
    fun checkForUpdate(): UpdateInfo? {
        val json = JSONObject(get(latestUrl))
        val remoteCode = json.getString("tag_name").removePrefix("v").toInt()
        if (remoteCode <= BuildConfig.VERSION_CODE) return null

        val assets = json.getJSONArray("assets")
        return (0 until assets.length())
            .map { assets.getJSONObject(it) }
            .firstOrNull { it.getString("name").endsWith(".apk") }
            ?.let { UpdateInfo(remoteCode, it.getString("browser_download_url")) }
            ?: throw IllegalStateException("The new release has no APK attached.")
    }

    /**
     * Downloads the APK into the app cache and returns the file. Call off the main thread.
     * [onProgress] gets a fraction from 0 to 1, or -1 when the file size is unknown.
     */
    fun download(context: Context, update: UpdateInfo, onProgress: (Float) -> Unit): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val target = File(dir, "update-${update.versionCode}.apk")
        val connection = openConnection(update.apkUrl)
        val total = connection.contentLengthLong
        connection.inputStream.use { input ->
            target.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                var downloaded = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    downloaded += read
                    onProgress(if (total > 0) downloaded.toFloat() / total else -1f)
                }
            }
        }
        return target
    }

    /** Opens the system installer. Sends the user to the "install unknown apps" setting first if needed. */
    fun install(context: Context, apk: File) {
        if (!context.packageManager.canRequestPackageInstalls()) {
            val settings = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(settings)
            throw IllegalStateException("Allow installs from this app, then tap Update again.")
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun get(url: String): String =
        openConnection(url).inputStream.bufferedReader().use { it.readText() }

    private fun openConnection(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            setRequestProperty("Accept", "application/vnd.github+json")
            instanceFollowRedirects = true
            connectTimeout = 15_000
            readTimeout = 60_000
        }
}
