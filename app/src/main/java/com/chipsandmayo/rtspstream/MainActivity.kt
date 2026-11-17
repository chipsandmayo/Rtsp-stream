package com.chipsandmayo.rtspstream

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chipsandmayo.rtspstream.databinding.ActivityMainBinding
import com.chipsandmayo.rtspstream.databinding.ItemRecentBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var store: StreamStore
    private val adapter = RecentAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        store = StreamStore(this)

        binding.recentList.layoutManager = LinearLayoutManager(this)
        binding.recentList.adapter = adapter

        binding.playButton.setOnClickListener { play() }
        binding.urlInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) { play(); true } else false
        }

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
        R.id.action_clear_recent -> {
            store.clear(); refreshRecent(); true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun play() {
        val url = binding.urlInput.text?.toString()?.trim().orEmpty()
        if (!url.startsWith("rtsp://", true) && !url.startsWith("rtsps://", true)) {
            binding.urlLayout.error = getString(R.string.invalid_url)
            return
        }
        binding.urlLayout.error = null

        val entry = StreamEntry(
            url = url,
            username = binding.usernameInput.text?.toString()?.trim().orEmpty(),
            password = binding.passwordInput.text?.toString().orEmpty(),
        )
        store.add(entry, keepPassword = PlayerSettings.load(this).rememberCredentials)
        startActivity(PlayerActivity.intent(this, entry))
    }

    private fun refreshRecent() {
        val items = store.all()
        adapter.submit(items)
        binding.emptyRecent.isVisible = items.isEmpty()
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
            holder.b.recentUrl.text = e.url
            holder.b.recentUser.text = if (e.username.isNotEmpty()) "user: ${e.username}" else ""
            holder.b.recentUser.isVisible = e.username.isNotEmpty()
            holder.b.root.setOnClickListener {
                binding.urlInput.setText(e.url)
                binding.usernameInput.setText(e.username)
                binding.passwordInput.setText(e.password)
                play()
            }
            holder.b.root.setOnLongClickListener {
                // Long-press fills the form without playing, so you can edit first
                binding.urlInput.setText(e.url)
                binding.usernameInput.setText(e.username)
                binding.passwordInput.setText(e.password)
                true
            }
            holder.b.recentDelete.setOnClickListener {
                store.remove(e); refreshRecent()
            }
        }
    }
}
