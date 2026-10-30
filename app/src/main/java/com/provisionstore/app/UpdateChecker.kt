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

    /** Returns the newer release, or null if already up to date or the check fails. */
    fun checkForUpdate(): UpdateInfo? {
        return try {
            val json = JSONObject(get(latestUrl))
            val remoteCode = json.getString("tag_name").removePrefix("v").toInt()
            if (remoteCode <= BuildConfig.VERSION_CODE) {
                null
            } else {
                val assets = json.getJSONArray("assets")
                (0 until assets.length())
                    .map { assets.getJSONObject(it) }
                    .firstOrNull { it.getString("name").endsWith(".apk") }
                    ?.let { UpdateInfo(remoteCode, it.getString("browser_download_url")) }
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Downloads the APK into the app cache and returns the file. Call off the main thread. */
    fun download(context: Context, update: UpdateInfo): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val target = File(dir, "update-${update.versionCode}.apk")
        openConnection(update.apkUrl).inputStream.use { input ->
            target.outputStream().use { input.copyTo(it) }
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
