package com.chipsandmayo.rtspstream

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

data class StreamEntry(val url: String, val username: String = "", val password: String = "") {

    /** The URL with credentials injected as user-info, which Media3's RTSP client uses for Basic/Digest auth. */
    fun playbackUri(): Uri {
        val uri = Uri.parse(url)
        if (username.isEmpty()) return uri
        val host = uri.host ?: return uri
        val userInfo = buildString {
            append(Uri.encode(username))
            if (password.isNotEmpty()) append(':').append(Uri.encode(password))
        }
        val hostPort = if (uri.port != -1) "$host:${uri.port}" else host
        return uri.buildUpon().encodedAuthority("$userInfo@$hostPort").build()
    }
}

/** Recently played streams, newest first, kept in SharedPreferences as JSON. */
class StreamStore(context: Context) {
    private val prefs = context.getSharedPreferences("streams", Context.MODE_PRIVATE)

    fun all(): List<StreamEntry> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                StreamEntry(o.getString("url"), o.optString("user"), o.optString("pass"))
            }
        }.getOrDefault(emptyList())
    }

    fun add(entry: StreamEntry, keepPassword: Boolean) {
        val stored = if (keepPassword) entry else entry.copy(password = "")
        val list = listOf(stored) + all().filterNot { it.url == entry.url && it.username == entry.username }
        save(list.take(MAX))
    }

    fun remove(entry: StreamEntry) = save(all().filterNot { it == entry })

    fun clear() = prefs.edit().remove(KEY).apply()

    private fun save(list: List<StreamEntry>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("url", it.url).put("user", it.username).put("pass", it.password))
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    private companion object {
        const val KEY = "recent"
        const val MAX = 20
    }
}
