package com.chipsandmayo.rtspstream

import android.content.Context
import androidx.preference.PreferenceManager

/** Snapshot of everything in the Settings screen, read once when a stream starts. */
data class PlayerSettings(
    val forceTcp: Boolean,
    val timeoutMs: Long,
    val userAgent: String,
    val autoReconnect: Boolean,
    val reconnectDelaySec: Int,
    val maxReconnects: Int,
    val buffer: BufferConfig,
    val resizeMode: String,
    val orientation: String,
    val preferSoftwareDecoder: Boolean,
    val showStats: Boolean,
    val startMuted: Boolean,
    val keepScreenOn: Boolean,
    val pipOnLeave: Boolean,
    val rememberCredentials: Boolean,
) {
    data class BufferConfig(
        val minMs: Int,
        val maxMs: Int,
        val startMs: Int,
        val rebufferMs: Int,
    )

    companion object {
        fun load(context: Context): PlayerSettings {
            val p = PreferenceManager.getDefaultSharedPreferences(context)
            fun int(key: String, def: Int) =
                p.getString(key, null)?.trim()?.toIntOrNull()?.coerceAtLeast(0) ?: def

            val buffer = when (p.getString("latency_preset", "low")) {
                "ultra" -> BufferConfig(250, 1000, 100, 250)
                "balanced" -> BufferConfig(1000, 3000, 500, 1000)
                "smooth" -> BufferConfig(2500, 8000, 1000, 2500)
                "custom" -> {
                    // DefaultLoadControl requires start <= min <= max, so sanitise.
                    val min = int("min_buffer_ms", 500)
                    val max = int("max_buffer_ms", 2000).coerceAtLeast(min)
                    val start = int("start_buffer_ms", 250).coerceAtMost(min)
                    BufferConfig(min, max, start, start.coerceAtLeast(min / 2).coerceAtMost(min))
                }
                else -> BufferConfig(500, 2000, 250, 500) // "low"
            }

            return PlayerSettings(
                forceTcp = p.getString("transport", "auto") == "tcp",
                timeoutMs = int("timeout_ms", 8000).coerceAtLeast(1000).toLong(),
                userAgent = p.getString("user_agent", null)?.takeIf { it.isNotBlank() } ?: "RtspStream/1.0",
                autoReconnect = p.getBoolean("auto_reconnect", true),
                reconnectDelaySec = int("reconnect_delay_s", 3).coerceAtLeast(1),
                maxReconnects = int("max_reconnects", 0),
                buffer = buffer,
                resizeMode = p.getString("resize_mode", "fit") ?: "fit",
                orientation = p.getString("orientation", "auto") ?: "auto",
                preferSoftwareDecoder = p.getString("decoder", "hardware") == "software",
                showStats = p.getBoolean("show_stats", false),
                startMuted = p.getBoolean("start_muted", false),
                keepScreenOn = p.getBoolean("keep_screen_on", true),
                pipOnLeave = p.getBoolean("pip_on_leave", true),
                rememberCredentials = p.getBoolean("remember_credentials", true),
            )
        }
    }
}
