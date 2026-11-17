package com.chipsandmayo.rtspstream

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chipsandmayo.rtspstream.databinding.ActivityMainBinding
import com.chipsandmayo.rtspstream.databinding.ItemRecentBinding
import com.google.android.material.snackbar.Snackbar

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var store: StreamStore
    private val adapter = RecentAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.padForSystemBars()
        setSupportActionBar(binding.toolbar)
        store = StreamStore(this)

        binding.recentList.layoutManager = LinearLayoutManager(this)
        binding.recentList.adapter = adapter

        binding.playButton.setOnClickListener { play() }
        binding.urlInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) { play(); true } else false
        }
        binding.urlInput.doAfterTextChanged { binding.urlLayout.error = null }
        binding.usernameInput.doAfterTextChanged { updateAuthLabel() }

        binding.authToggle.setOnClickListener { setAuthExpanded(!binding.authFields.isVisible) }
        binding.clearRecent.setOnClickListener { clearRecent() }

        // Opened from an rtsp:// link in another app
        intent?.data?.toString()?.let { url ->
            binding.urlInput.setText(url)
            if (savedInstanceState == null) play()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshRecent()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_settings -> {
            startActivity(Intent(this, SettingsActivity::class.java)); true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun setAuthExpanded(expanded: Boolean) {
        binding.authFields.isVisible = expanded
        binding.authToggle.setIconResource(if (expanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more)
    }

    private fun updateAuthLabel() {
        val user = binding.usernameInput.text?.toString()?.trim().orEmpty()
        binding.authToggle.text =
            if (user.isEmpty()) getString(R.string.authentication) else getString(R.string.authentication_set, user)
    }

    private fun fill(e: StreamEntry) {
        binding.urlInput.setText(e.url)
        binding.urlInput.setSelection(e.url.length)
        binding.usernameInput.setText(e.username)
        binding.passwordInput.setText(e.password)
        updateAuthLabel()
        if (e.username.isNotEmpty()) setAuthExpanded(true)
    }

    private fun play() {
        val url = binding.urlInput.text?.toString()?.trim().orEmpty()
        if (!url.startsWith("rtsp://", true) && !url.startsWith("rtsps://", true)) {
            binding.urlLayout.error = getString(R.string.invalid_url)
            binding.urlInput.requestFocus()
            return
        }
        binding.urlLayout.error = null
        hideKeyboard()

        val entry = StreamEntry(
            url = url,
            username = binding.usernameInput.text?.toString()?.trim().orEmpty(),
            password = binding.passwordInput.text?.toString().orEmpty(),
        )
        store.add(entry, keepPassword = PlayerSettings.load(this).rememberCredentials)
        startActivity(PlayerActivity.intent(this, entry))
    }

    private fun hideKeyboard() {
        currentFocus?.let {
            getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(it.windowToken, 0)
            it.clearFocus()
        }
    }

    private fun refreshRecent() {
        val items = store.all()
        adapter.submit(items)
        binding.emptyRecent.isVisible = items.isEmpty()
        binding.recentList.isVisible = items.isNotEmpty()
        binding.clearRecent.isVisible = items.isNotEmpty()
    }

    private fun clearRecent() {
        val backup = store.all()
        store.clear()
        refreshRecent()
        Snackbar.make(binding.root, R.string.cleared, Snackbar.LENGTH_LONG)
            .setAction(R.string.undo) { store.replaceAll(backup); refreshRecent() }
            .show()
    }

    private fun removeRecent(entry: StreamEntry) {
        val backup = store.all()
        store.remove(entry)
        refreshRecent()
        Snackbar.make(binding.root, R.string.removed, Snackbar.LENGTH_LONG)
            .setAction(R.string.undo) { store.replaceAll(backup); refreshRecent() }
            .show()
    }

    private inner class RecentAdapter : RecyclerView.Adapter<RecentAdapter.VH>() {
        private var items: List<StreamEntry> = emptyList()

        fun submit(list: List<StreamEntry>) {
            items = list
            notifyDataSetChanged()
        }

        inner class VH(val b: ItemRecentBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(ItemRecentBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val e = items[position]
            holder.b.recentTitle.text = e.displayHost
            holder.b.recentSubtitle.text =
                if (e.username.isNotEmpty()) "${e.displayPath}  ·  ${e.username}" else e.displayPath
            holder.b.root.setOnClickListener {
                fill(e)
                play()
            }
            holder.b.root.setOnLongClickListener {
                // Long-press fills the form without playing, so you can edit first
                fill(e)
                binding.urlInput.requestFocus()
                true
            }
            holder.b.recentDelete.setOnClickListener { removeRecent(e) }
        }
    }
}
