package com.aibox.launcher

import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.IBinder
import android.view.Display

class ClusterMirrorService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        fun start(context: Context, resultCode: Int, data: Intent) {
            MirrorState.isRunning = true
            MirrorState.notifyChanged()
        }

        fun stop(context: Context) {
            MirrorState.isRunning = false
            MirrorState.notifyChanged()
        }
    }
}

data class ExternalDisplayInfo(val name: String, val id: Int)

object MirrorState {
    var isRunning: Boolean = false
    var externalDisplayName: String? = null
    var sourceWidth: Int = 1920
    var sourceHeight: Int = 1080

    private val listeners = mutableListOf<() -> Unit>()

    fun externalDisplays(context: Context): List<ExternalDisplayInfo> {
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager ?: return emptyList()
        return dm.displays
            .filter { it.displayId != Display.DEFAULT_DISPLAY }
            .map { ExternalDisplayInfo(it.name, it.displayId) }
    }

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
