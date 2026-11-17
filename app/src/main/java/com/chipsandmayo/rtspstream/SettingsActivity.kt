package com.chipsandmayo.rtspstream

import android.os.Bundle
import android.text.InputType
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
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)

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
    }
}
