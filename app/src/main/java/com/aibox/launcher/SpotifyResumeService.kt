package com.aibox.launcher

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder

class SpotifyResumeService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        SpotifyAutoPlay.fallbackMediaPlay(this)
        return START_NOT_STICKY
    }

    companion object {
        fun startAutoResume(context: Context) {
            SpotifyAutoPlay.fallbackMediaPlay(context)
        }

        fun authorizeInteractive(context: Context, callback: (Boolean, String) -> Unit) {
            SpotifyAutoPlay.fallbackMediaPlay(context)
            callback(true, "已透過媒體按鈕發送背景播放廣播至 Spotify")
        }
    }
}

object SpotifyRemoteStatus {
    var statusText: String = "媒體按鈕續播模式（不需要 SDK）"
    private val listeners = mutableListOf<() -> Unit>()

    fun hasValidClientId(): Boolean = false

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    fun notifyChanged() {
        listeners.forEach { it.invoke() }
    }
}
