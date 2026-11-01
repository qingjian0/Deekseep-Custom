package ds.dsh.DeepSeek

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import java.io.File

/** Small foreground audio player used by the Agent music tool for files already on-device. */
class LocalAudioPlaybackService : Service() {

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Local audio player"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null || intent.action != ACTION_CONTROL ||
            intent.getStringExtra(EXTRA_TOKEN) != CONTROL_TOKEN
        ) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        val requestId = safe(intent.getStringExtra(EXTRA_REQUEST_ID))
        val action = safe(intent.getStringExtra(EXTRA_ACTION))
        val path = safe(intent.getStringExtra(EXTRA_PATH))
        if (!requestId.matches(Regex("[A-Za-z0-9_-]{4,80}"))) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        lastRequestId = requestId
        lastDone = false
        lastSuccess = false
        lastDetail = "starting"
        try {
            control(requestId, action, path)
        } catch (error: Throwable) {
            finishRequest(
                requestId,
                false,
                HostRemap.simpleName(error.javaClass) + ": " + (error.message ?: "null")
            )
        }
        return START_NOT_STICKY
    }

    @Throws(Exception::class)
    private fun control(requestId: String, action: String, path: String) {
        synchronized(LOCK) {
            if (action == "play" && path.isNotEmpty()) {
                releaseLocked()
                val file = File(path)
                if (!file.isFile) {
                    finishRequest(requestId, false, "Audio file does not exist")
                    return
                }

                val next = MediaPlayer()
                next.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                next.setDataSource(this, Uri.fromFile(file))
                next.setOnPreparedListener { ready ->
                    try {
                        ready.start()
                        finishRequest(requestId, true, "Local audio playback started")
                    } catch (error: Throwable) {
                        finishRequest(
                            requestId,
                            false,
                            HostRemap.simpleName(error.javaClass) + ": " + (error.message ?: "null")
                        )
                    }
                }
                next.setOnErrorListener { _, what, extra ->
                    finishRequest(
                        requestId,
                        false,
                        "Media decoder error what=$what extra=$extra"
                    )
                    true
                }
                next.setOnCompletionListener {
                    updateNotification("Playback completed")
                }
                player = next
                currentPath = path
                updateNotification(file.name)
                next.prepareAsync()
                return
            }

            val activePlayer = player
            if (activePlayer == null) {
                finishRequest(requestId, false, "No local audio is loaded")
                return
            }
            when (action) {
                "play" -> {
                    activePlayer.start()
                    finishRequest(requestId, true, "Local audio playback resumed")
                }
                "pause" -> {
                    if (activePlayer.isPlaying) activePlayer.pause()
                    finishRequest(requestId, true, "Local audio playback paused")
                }
                "toggle" -> {
                    if (activePlayer.isPlaying) activePlayer.pause() else activePlayer.start()
                    finishRequest(
                        requestId,
                        true,
                        if (activePlayer.isPlaying) {
                            "Local audio playback resumed"
                        } else {
                            "Local audio playback paused"
                        }
                    )
                }
                "stop" -> {
                    releaseLocked()
                    finishRequest(requestId, true, "Local audio playback stopped")
                    stopForeground(true)
                    stopSelf()
                }
                else -> finishRequest(requestId, false, "Unsupported local audio action")
            }
        }
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL)
        } else {
            Notification.Builder(this)
        }
        return builder.setSmallIcon(NotificationIcons.smallIcon(this))
            .setContentTitle("YunSeek Agent")
            .setContentText(text)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        val channel = NotificationChannel(
            CHANNEL,
            "Agent local audio",
            NotificationManager.IMPORTANCE_LOW
        )
        channel.setShowBadge(false)
        manager.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        synchronized(LOCK) {
            releaseLocked()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_CONTROL = "ds.dsh.DeepSeek.action.LOCAL_AUDIO_CONTROL"
        const val EXTRA_TOKEN = "local_audio_control_token"
        const val EXTRA_REQUEST_ID = "request_id"
        const val EXTRA_ACTION = "action"
        const val EXTRA_PATH = "path"
        const val CONTROL_TOKEN = "deekseep-local-audio-v1"

        private const val CHANNEL = "deekseep_agent_local_audio"
        private const val NOTIFICATION_ID = 0xD5A3
        private val LOCK = Any()

        @Volatile
        private var player: MediaPlayer? = null

        @Volatile
        private var currentPath: String = ""

        @Volatile
        private var lastRequestId: String = ""

        @Volatile
        private var lastDone: Boolean = false

        @Volatile
        private var lastSuccess: Boolean = false

        @Volatile
        private var lastDetail: String = ""

        private fun finishRequest(requestId: String, success: Boolean, detail: String?) {
            if (requestId != lastRequestId) return
            lastSuccess = success
            lastDetail = safe(detail)
            lastDone = true
        }

        @JvmStatic
        fun status(requestId: String?): Bundle {
            val matching = requestId != null && requestId == lastRequestId
            return Bundle().apply {
                putBoolean("matching", matching)
                putBoolean("done", matching && lastDone)
                putBoolean("success", matching && lastDone && lastSuccess)
                putString("detail", if (matching) lastDetail else "unknown request")
                putString("path", currentPath)
            }
        }

        private fun releaseLocked() {
            val old = player
            player = null
            currentPath = ""
            if (old == null) return
            try {
                old.stop()
            } catch (_: Throwable) {
            }
            try {
                old.reset()
            } catch (_: Throwable) {
            }
            try {
                old.release()
            } catch (_: Throwable) {
            }
        }

        private fun safe(value: String?): String = value?.trim() ?: ""
    }
}
