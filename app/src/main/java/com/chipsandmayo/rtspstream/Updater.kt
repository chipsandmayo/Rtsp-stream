package com.chipsandmayo.rtspstream

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Checks the repo's "latest" GitHub release and installs the APK attached to it. */
object Updater {
    private const val BASE = "https://github.com/chipsandmayo/Rtsp-stream/releases/download/latest"
    private const val VERSION_URL = "$BASE/version.json"
    private const val APK_URL = "$BASE/RtspStream.apk"

    data class Release(val versionCode: Int, val versionName: String, val notes: String)

    val installedVersionCode get() = BuildConfig.VERSION_CODE
    val installedVersionName get() = BuildConfig.VERSION_NAME

    suspend fun fetchLatest(): Release = withContext(Dispatchers.IO) {
        val conn = open(VERSION_URL)
        try {
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            Release(
                versionCode = json.getInt("versionCode"),
                versionName = json.optString("versionName", json.getInt("versionCode").toString()),
                notes = json.optString("notes"),
            )
        } finally {
            conn.disconnect()
        }
    }

    /** Downloads the APK into the app cache, reporting progress 0–100 (or -1 if size unknown). */
    suspend fun download(context: Context, onProgress: suspend (Int) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val out = File(dir, "RtspStream.apk")
        val conn = open(APK_URL)
        try {
            val total = conn.contentLengthLong
            var done = 0L
            var lastPct = -2
            conn.inputStream.use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        done += n
                        val pct = if (total > 0) (done * 100 / total).toInt() else -1
                        if (pct != lastPct) {
                            lastPct = pct
                            withContext(Dispatchers.Main) { onProgress(pct) }
                        }
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        out
    }

    /** Hands the downloaded APK to Android's package installer. */
    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun open(url: String): HttpURLConnection {
        // Cache-buster so we never see a stale version.json
        val conn = URL("$url?t=${System.currentTimeMillis()}").openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = true
        conn.useCaches = false
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.setRequestProperty("Cache-Control", "no-cache")
        conn.setRequestProperty("User-Agent", "RtspStream/${BuildConfig.VERSION_NAME}")
        if (conn.responseCode !in 200..299) {
            val code = conn.responseCode
            conn.disconnect()
            throw IllegalStateException(if (code == 404) "no release published yet" else "HTTP $code")
        }
        return conn
    }
}
