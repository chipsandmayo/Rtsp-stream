package com.chipsandmayo.rtspstream

import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Rational
import android.view.TextureView
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import com.chipsandmayo.rtspstream.databinding.ActivityPlayerBinding
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(UnstableApi::class)
class PlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerBinding
    private lateinit var settings: PlayerSettings
    private lateinit var entry: StreamEntry

    private var player: ExoPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var reconnectAttempts = 0
    private var muted = false
    private var videoAspect = Rational(16, 9)

    private val statsTicker = object : Runnable {
        override fun run() {
            updateStats()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settings = PlayerSettings.load(this)
        entry = StreamEntry(
            url = intent.getStringExtra(EXTRA_URL).orEmpty(),
            username = intent.getStringExtra(EXTRA_USER).orEmpty(),
            password = intent.getStringExtra(EXTRA_PASS).orEmpty(),
        )
        muted = savedInstanceState?.getBoolean(STATE_MUTED) ?: settings.startMuted

        requestedOrientation = when (settings.orientation) {
            "landscape" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            "portrait" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            else -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        }
        if (settings.keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding.playerView.resizeMode = when (settings.resizeMode) {
            "fill" -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            "zoom" -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            "fixed_width" -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
            "fixed_height" -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT
            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
        // Live stream: seeking / progress bar make no sense
        binding.playerView.setShowFastForwardButton(false)
        binding.playerView.setShowRewindButton(false)
        binding.playerView.setShowNextButton(false)
        binding.playerView.setShowPreviousButton(false)
        binding.playerView.setControllerVisibilityListener(
            androidx.media3.ui.PlayerView.ControllerVisibilityListener { visibility ->
                binding.topActions.visibility = visibility
            }
        )

        binding.statusText.isVisible = settings.showStats
        binding.muteButton.setOnClickListener { setMuted(!muted) }
        binding.snapshotButton.setOnClickListener { takeSnapshot() }
        binding.retryButton.setOnClickListener {
            reconnectAttempts = 0
            restartPlayer()
        }

        hideSystemUi()
    }

    override fun onStart() {
        super.onStart()
        initializePlayer()
        if (settings.showStats) handler.post(statsTicker)
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacksAndMessages(null)
        releasePlayer()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_MUTED, muted)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUi()
    }

    // ---- Player lifecycle -------------------------------------------------

    private fun initializePlayer() {
        if (player != null) return
        binding.errorPanel.isVisible = false

        val b = settings.buffer
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(b.minMs, b.maxMs, b.startMs, b.rebufferMs)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val renderers = DefaultRenderersFactory(this).setEnableDecoderFallback(true)
        if (settings.preferSoftwareDecoder) {
            renderers.setMediaCodecSelector { mimeType, secure, tunneling ->
                // false (software) sorts before true (hardware)
                MediaCodecUtil.getDecoderInfos(mimeType, secure, tunneling)
                    .sortedBy { it.hardwareAccelerated }
            }
        }

        val exo = ExoPlayer.Builder(this, renderers)
            .setLoadControl(loadControl)
            .build()

        val source = RtspMediaSource.Factory()
            .setForceUseRtpTcp(settings.forceTcp)
            .setTimeoutMs(settings.timeoutMs)
            .setUserAgent(settings.userAgent)
            .createMediaSource(MediaItem.fromUri(entry.playbackUri()))

        exo.addListener(playerListener)
        exo.setMediaSource(source)
        exo.volume = if (muted) 0f else 1f
        exo.playWhenReady = true
        exo.prepare()

        binding.playerView.player = exo
        player = exo
        updateMuteIcon()
    }

    private fun releasePlayer() {
        player?.let {
            it.removeListener(playerListener)
            it.release()
        }
        player = null
        binding.playerView.player = null
    }

    private fun restartPlayer() {
        releasePlayer()
        initializePlayer()
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_READY) {
                reconnectAttempts = 0
                binding.errorPanel.isVisible = false
            }
            updateStats()
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            if (videoSize.width > 0 && videoSize.height > 0) {
                val w = (videoSize.width * videoSize.pixelWidthHeightRatio).toInt()
                // PiP only accepts aspect ratios between ~1:2.39 and 2.39:1
                videoAspect = Rational(w, videoSize.height).let {
                    val r = it.toFloat()
                    when {
                        r > 2.39f -> Rational(239, 100)
                        r < 1 / 2.39f -> Rational(100, 239)
                        else -> it
                    }
                }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            val canRetry = settings.autoReconnect &&
                (settings.maxReconnects == 0 || reconnectAttempts < settings.maxReconnects)
            if (canRetry) {
                reconnectAttempts++
                showMessage(getString(R.string.reconnecting, settings.reconnectDelaySec, reconnectAttempts), showRetry = false)
                handler.postDelayed({ restartPlayer() }, settings.reconnectDelaySec * 1000L)
            } else {
                showMessage(getString(R.string.playback_error, describe(error)), showRetry = true)
            }
        }
    }

    private fun describe(error: PlaybackException): String {
        val cause = error.cause?.message
        return when (error.errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ->
                "can't reach the camera/server. Check the address and that you're on the same network."
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                "connection timed out. Try TCP transport in Settings."
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
                "server rejected the request (wrong path or credentials?)"
            else -> listOfNotNull(error.errorCodeName, cause).joinToString(" — ")
        }
    }

    private fun showMessage(text: String, showRetry: Boolean) {
        binding.errorText.text = text
        binding.retryButton.isVisible = showRetry
        binding.errorPanel.isVisible = true
    }

    // ---- Controls --------------------------------------------------------

    private fun setMuted(value: Boolean) {
        muted = value
        player?.volume = if (muted) 0f else 1f
        updateMuteIcon()
    }

    private fun updateMuteIcon() {
        binding.muteButton.setImageResource(
            if (muted) android.R.drawable.ic_lock_silent_mode else android.R.drawable.ic_lock_silent_mode_off
        )
    }

    @SuppressLint("SetTextI18n")
    private fun updateStats() {
        if (!settings.showStats) return
        val p = player ?: return
        val f = p.videoFormat
        val state = when (p.playbackState) {
            Player.STATE_IDLE -> "idle"
            Player.STATE_BUFFERING -> "buffering"
            Player.STATE_READY -> if (p.isPlaying) "playing" else "ready"
            Player.STATE_ENDED -> "ended"
            else -> "?"
        }
        val dropped = p.videoDecoderCounters?.let { it.ensureUpdated(); it.droppedBufferCount } ?: 0
        val buffered = (p.bufferedPosition - p.currentPosition).coerceAtLeast(0)
        val transport = if (settings.forceTcp) "TCP" else "auto"
        binding.statusText.text = buildString {
            append("state: $state  transport: $transport\n")
            if (f != null) {
                append("video: ${f.width}x${f.height}")
                if (f.frameRate > 0) append(" @ %.1f fps".format(f.frameRate))
                append("\ncodec: ${f.codecs ?: f.sampleMimeType}")
            } else {
                append("video: —")
            }
            p.audioFormat?.let { append("\naudio: ${it.sampleMimeType} ${it.sampleRate}Hz") }
            append("\nbuffer: ${buffered} ms  dropped: $dropped")
        }
    }

    private fun takeSnapshot() {
        val tv = binding.playerView.videoSurfaceView as? TextureView
        val bmp = tv?.bitmap
        if (bmp == null) {
            Toast.makeText(this, R.string.snapshot_failed, Toast.LENGTH_SHORT).show()
            return
        }
        val name = "rtsp_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".jpg"
        val ok = runCatching { saveBitmap(bmp, name) }.getOrDefault(false)
        Toast.makeText(this, if (ok) R.string.snapshot_saved else R.string.snapshot_failed, Toast.LENGTH_SHORT).show()
    }

    private fun saveBitmap(bmp: Bitmap, name: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/RtspStream")
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
            contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) } ?: return false
            return true
        }
        // Android 8/9: app-specific pictures folder (no storage permission needed)
        val dir = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "RtspStream").apply { mkdirs() }
        FileOutputStream(File(dir, name)).use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        return true
    }

    // ---- Fullscreen & picture-in-picture --------------------------------

    private fun hideSystemUi() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (settings.pipOnLeave && player?.isPlaying == true &&
            packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)
        ) {
            runCatching {
                enterPictureInPictureMode(
                    PictureInPictureParams.Builder().setAspectRatio(videoAspect).build()
                )
            }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        binding.playerView.useController = !isInPictureInPictureMode
        binding.topActions.visibility = if (isInPictureInPictureMode) View.GONE else View.VISIBLE
        binding.statusText.isVisible = settings.showStats && !isInPictureInPictureMode
    }

    companion object {
        private const val EXTRA_URL = "url"
        private const val EXTRA_USER = "user"
        private const val EXTRA_PASS = "pass"
        private const val STATE_MUTED = "muted"

        fun intent(context: Context, entry: StreamEntry): Intent =
            Intent(context, PlayerActivity::class.java)
                .putExtra(EXTRA_URL, entry.url)
                .putExtra(EXTRA_USER, entry.username)
                .putExtra(EXTRA_PASS, entry.password)
    }
}
