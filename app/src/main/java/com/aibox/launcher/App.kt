package com.aibox.launcher

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.appcompat.app.AppCompatDelegate

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // 固定淺色(白色)。想改回深色,把 MODE_NIGHT_NO 改成 MODE_NIGHT_YES
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        // 開機啟動時於背景執行緒預載 App 標籤與圖示快取，免加載卡頓
        Apps.preload(this)

        // 監聽系統 App 安裝與移除，自動刷新快取
        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addDataScheme("package")
            }
            registerReceiver(object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent?) {
                    Apps.invalidateCache(context)
                }
            }, filter)
        } catch (_: Exception) {}
    }
}
