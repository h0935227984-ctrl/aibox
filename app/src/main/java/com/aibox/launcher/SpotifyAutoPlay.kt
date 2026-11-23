package com.aibox.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent

/**
 * 啟動器啟動時於背景自動續播 Spotify 上次最後播放的歌曲（不開啟 Spotify 頁面）。
 * 透過查詢 Spotify 的 MediaButtonReceiver 發送 KEYCODE_MEDIA_PLAY 媒體鍵廣播喚醒背景播放，
 * 若尚未進入播放狀態再補送系統媒體鍵，並於音樂已播放時自動跳過。
 */
object SpotifyAutoPlay {
    private const val SPOTIFY_PKG = "com.spotify.music"
    private const val FALLBACK_RECEIVER = "com.spotify.mediasession.mediasession.receiver.MediaButtonReceiver"

    private val handler = Handler(Looper.getMainLooper())

    fun triggerOnLauncherStart(context: Context) {
        SpotifyResumeService.startAutoResume(context)
    }

    fun fallbackMediaPlay(context: Context) {
        if (!Store.autoPlaySpotifyOn(context)) return
        val appCtx = context.applicationContext
        if (!isSpotifyInstalled(appCtx)) return

        // 延遲 0.4 秒待車機開機音訊就緒後再發送背景媒體鍵，不切換 Spotify 至前景
        handler.postDelayed({
            tryResumeIfIdle(appCtx, allowFallbackDispatch = false)
            handler.postDelayed({
                tryResumeIfIdle(appCtx, allowFallbackDispatch = true)
            }, 1400L)
        }, 400L)
    }

    private fun tryResumeIfIdle(context: Context, allowFallbackDispatch: Boolean) {
        if (!Store.autoPlaySpotifyOn(context)) return
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        if (am.isMusicActive) return

        val receivers = querySpotifyMediaReceivers(context)
        if (receivers.isNotEmpty()) {
            receivers.forEach { cn -> sendMediaPlayToReceiver(context, cn) }
        } else {
            sendMediaPlayToReceiver(context, ComponentName(SPOTIFY_PKG, FALLBACK_RECEIVER))
        }

        if (allowFallbackDispatch && !am.isMusicActive) {
            val now = SystemClock.uptimeMillis()
            am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY, 0))
            am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY, 0))
        }
    }

    private fun sendMediaPlayToReceiver(context: Context, component: ComponentName) {
        val now = SystemClock.uptimeMillis()
        for (action in intArrayOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            val keyEvent = KeyEvent(now, now, action, KeyEvent.KEYCODE_MEDIA_PLAY, 0)
            val intent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                setPackage(SPOTIFY_PKG)
                setComponent(component)
                addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES or Intent.FLAG_RECEIVER_FOREGROUND)
                putExtra(Intent.EXTRA_KEY_EVENT, keyEvent)
            }
            try {
                context.sendBroadcast(intent)
            } catch (_: Exception) {}
        }
    }

    private fun querySpotifyMediaReceivers(context: Context): List<ComponentName> {
        return try {
            val probe = Intent(Intent.ACTION_MEDIA_BUTTON).setPackage(SPOTIFY_PKG)
            val list = if (Build.VERSION.SDK_INT >= 33) {
                context.packageManager.queryBroadcastReceivers(
                    probe,
                    PackageManager.ResolveInfoFlags.of(0L)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.queryBroadcastReceivers(probe, 0)
            }
            list.mapNotNull { info ->
                info.activityInfo?.let { ComponentName(it.packageName, it.name) }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun isSpotifyInstalled(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= 33) {
                context.packageManager.getPackageInfo(
                    SPOTIFY_PKG,
                    PackageManager.PackageInfoFlags.of(0L)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(SPOTIFY_PKG, 0)
            }
            true
        } catch (_: Exception) {
            false
        }
    }
}
