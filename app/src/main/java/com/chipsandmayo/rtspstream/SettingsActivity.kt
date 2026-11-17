package com.chipsandmayo.rtspstream

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import kotlinx.coroutines.launch
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.chipsandmayo.rtspstream.databinding.ActivitySettingsBinding
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.padForSystemBars()
        binding.toolbar.setNavigationOnClickListener { finish() }
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settingsContainer, SettingsFragment())
                .commit()
        }
    }

    class SettingsFragment : PreferenceFragmentCompat() {

        private var latest: Updater.Release? = null
        private var busy = false
        private lateinit var checkPref: Preference
        private lateinit var updatePref: Preference

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)

            checkPref = findPreference("check_updates")!!
            updatePref = findPreference("install_update")!!
            checkPref.summary = "Installed version: ${Updater.installedVersionName}"
            checkPref.setOnPreferenceClickListener { checkForUpdates(); true }
            updatePref.setOnPreferenceClickListener { downloadAndInstall(); true }

            val numericKeys = listOf(
                "timeout_ms", "reconnect_delay_s", "max_reconnects",
                "min_buffer_ms", "max_buffer_ms", "start_buffer_ms",
            )
            numericKeys.forEach { key ->
                findPreference<EditTextPreference>(key)?.setOnBindEditTextListener {
                    it.inputType = InputType.TYPE_CLASS_NUMBER
                }
            }

            // Custom buffer fields only matter when the "Custom" preset is chosen
            val customKeys = listOf("min_buffer_ms", "max_buffer_ms", "start_buffer_ms")
            fun updateCustom(preset: String?) =
                customKeys.forEach { findPreference<EditTextPreference>(it)?.isEnabled = preset == "custom" }
            val presetPref = findPreference<androidx.preference.ListPreference>("latency_preset")
            updateCustom(presetPref?.value)
            presetPref?.setOnPreferenceChangeListener { _, newValue ->
                updateCustom(newValue as? String); true
            }
        }

        override fun onResume() {
            super.onResume()
            // Returning from "Install unknown apps" permission screen: carry on with the update
            if (latest != null && !busy && requireContext().packageManager.canRequestPackageInstalls()) {
                updatePref.isEnabled = true
            }
        }

        private fun checkForUpdates() {
            if (busy) return
            busy = true
            checkPref.summary = "Checking…"
            lifecycleScope.launch {
                runCatching { Updater.fetchLatest() }
                    .onSuccess { r ->
                        if (r.versionCode > Updater.installedVersionCode) {
                            latest = r
                            checkPref.summary = "Update available (installed: ${Updater.installedVersionName})"
                            updatePref.isEnabled = true
                            updatePref.summary = "Install ${r.versionName}" +
                                (if (r.notes.isNotBlank()) " — ${r.notes}" else "")
                        } else {
                            latest = null
                            checkPref.summary = "You're up to date (${Updater.installedVersionName})"
                            updatePref.isEnabled = false
                            updatePref.summary = "No update available"
                        }
                    }
                    .onFailure { e ->
                        checkPref.summary = "Couldn't check for updates: ${e.message ?: e.javaClass.simpleName}"
                    }
                busy = false
            }
        }

        private fun downloadAndInstall() {
            val release = latest ?: return
            if (busy) return
            val ctx = requireContext()
            if (!ctx.packageManager.canRequestPackageInstalls()) {
                Toast.makeText(ctx, "Allow RTSP Stream to install apps, then come back and tap Update", Toast.LENGTH_LONG).show()
                startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                )
                return
            }
            busy = true
            updatePref.isEnabled = false
            updatePref.summary = "Downloading ${release.versionName}…"
            lifecycleScope.launch {
                runCatching {
                    Updater.download(ctx) { pct ->
                        updatePref.summary = if (pct >= 0) "Downloading ${release.versionName}… $pct%"
                        else "Downloading ${release.versionName}…"
                    }
                }.onSuccess { apk ->
                    updatePref.summary = "Install ${release.versionName}"
                    updatePref.isEnabled = true
                    Updater.install(ctx, apk)
                }.onFailure { e ->
                    updatePref.summary = "Download failed: ${e.message ?: e.javaClass.simpleName} — tap to retry"
                    updatePref.isEnabled = true
                }
                busy = false
            }
        }
    }
}
