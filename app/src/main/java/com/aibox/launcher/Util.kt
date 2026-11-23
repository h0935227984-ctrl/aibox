package com.aibox.launcher

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.WindowManager
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import org.lsposed.hiddenapibypass.HiddenApiBypass

data class Combo(val top: String, val bottom: String)
class AppItem(val label: String, val pkg: String, val icon: Drawable)

/** 自適應單位:1u = 螢幕短邊的 1%。所有新介面都以 u 為單位,換任何尺寸/解析度的螢幕都會等比例縮放 */
object Ui {
    fun unit(c: Context): Float {
        val dm = c.resources.displayMetrics
        return minOf(dm.widthPixels, dm.heightPixels) / 100f
    }
}

object Store {
    private fun sp(c: Context) = c.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    fun combos(c: Context): MutableList<Combo> {
        val a = JSONArray(sp(c).getString("combos", "[]"))
        return MutableList(a.length()) { i -> a.getJSONObject(i).let { Combo(it.getString("t"), it.getString("b")) } }
    }

    fun saveCombos(c: Context, list: List<Combo>) {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("t", it.top).put("b", it.bottom)) }
        sp(c).edit().putString("combos", a.toString()).apply()
    }

    fun blurOn(c: Context) = sp(c).getBoolean("blurOn", false)
    fun setBlurOn(c: Context, v: Boolean) = sp(c).edit().putBoolean("blurOn", v).apply()
    fun blurStrength(c: Context) = sp(c).getInt("blurS", 50)
    fun setBlurStrength(c: Context, v: Int) = sp(c).edit().putInt("blurS", v).apply()
    fun dimOn(c: Context) = sp(c).getBoolean("dimOn", true)
    fun setDimOn(c: Context, v: Boolean) = sp(c).edit().putBoolean("dimOn", v).apply()
    fun dimStrength(c: Context) = sp(c).getInt("dimS", 40)
    fun setDimStrength(c: Context, v: Int) = sp(c).edit().putInt("dimS", v).apply()

    fun navBarOn(c: Context) = sp(c).getBoolean("navBar", true)
    fun setNavBarOn(c: Context, v: Boolean) = sp(c).edit().putBoolean("navBar", v).apply()

    fun car3DOn(c: Context) = sp(c).getBoolean("car3DOn", true)
    fun setCar3DOn(c: Context, v: Boolean) = sp(c).edit().putBoolean("car3DOn", v).apply()

    fun rainbowRoadOn(c: Context) = sp(c).getBoolean("rainbowOn", true)
    fun setRainbowRoadOn(c: Context, v: Boolean) = sp(c).edit().putBoolean("rainbowOn", v).apply()

    fun autoPlaySpotifyOn(c: Context) = sp(c).getBoolean("autoSpotify", true)
    fun setAutoPlaySpotifyOn(c: Context, v: Boolean) = sp(c).edit().putBoolean("autoSpotify", v).apply()
    fun lastSpotifyUri(c: Context): String? = sp(c).getString("sp_last_uri", null)
    fun lastSpotifyPos(c: Context): Long = sp(c).getLong("sp_last_pos", 0L)
    fun setLastSpotifyTrack(c: Context, uri: String?, posMs: Long) {
        sp(c).edit().putString("sp_last_uri", uri).putLong("sp_last_pos", posMs.coerceAtLeast(0L)).apply()
    }

    fun wallpaperPaths(c: Context): MutableList<String> {
        val a = JSONArray(sp(c).getString("wp_paths", "[]"))
        return MutableList(a.length()) { i -> a.getString(i) }
    }
    fun setWallpaperPaths(c: Context, list: List<String>) {
        val a = JSONArray()
        list.forEach { a.put(it) }
        sp(c).edit().putString("wp_paths", a.toString()).apply()
    }
    fun wallpaperRandomOn(c: Context) = sp(c).getBoolean("wp_random", false)
    fun setWallpaperRandomOn(c: Context, v: Boolean) = sp(c).edit().putBoolean("wp_random", v).apply()

    /** 時鐘位置(螢幕比例)與縮放倍率 */
    fun clockX(c: Context) = sp(c).getFloat("clkX", 0.05f)
    fun clockY(c: Context) = sp(c).getFloat("clkY", 0.04f)
    fun clockScale(c: Context) = sp(c).getFloat("clkS", 1f)
    fun saveClock(c: Context, x: Float, y: Float, s: Float) =
        sp(c).edit().putFloat("clkX", x).putFloat("clkY", y).putFloat("clkS", s).apply()
    fun resetClock(c: Context) = sp(c).edit().remove("clkX").remove("clkY").remove("clkS").apply()

    fun wallpaper(c: Context): String? = sp(c).getString("wp", null)
    fun wallpaperIsVideo(c: Context) = sp(c).getBoolean("wpv", false)
    fun setWallpaper(c: Context, path: String?, video: Boolean) =
        sp(c).edit().putString("wp", path).putBoolean("wpv", video).apply()
}

object Apps {
    const val PKG_CAR_3D = "__aibox_car_3d__"

    @Volatile private var cachedList: List<AppItem>? = null
    @Volatile private var cachedMap: Map<String, AppItem>? = null
    @Volatile private var isHiddenApiExempted = false

    fun preload(c: Context) {
        val appCtx = c.applicationContext
        Thread {
            queryApps(appCtx)
        }.start()
    }

    fun invalidateCache(c: Context? = null) {
        cachedList = null
        cachedMap = null
        c?.let { preload(it) }
    }

    private fun queryApps(c: Context): List<AppItem> {
        val pm = c.packageManager
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val list = pm.queryIntentActivities(i, 0)
            .filter { it.activityInfo.packageName != c.packageName }
            .map { AppItem(it.loadLabel(pm).toString(), it.activityInfo.packageName, it.loadIcon(pm)) }
            .distinctBy { it.pkg }
            .sortedBy { it.label.lowercase() }
        cachedList = list
        cachedMap = list.associateBy { it.pkg }
        return list
    }

    fun list(c: Context): List<AppItem> {
        return cachedList ?: synchronized(this) {
            cachedList ?: queryApps(c.applicationContext)
        }
    }

    fun listWithCar3D(c: Context): List<AppItem> {
        val carIcon = androidx.core.content.ContextCompat.getDrawable(c, R.drawable.ic_car_3d_split)
            ?: icon(c, c.packageName)!!
        return listOf(AppItem("3D 車輛模擬大道", PKG_CAR_3D, carIcon)) + list(c)
    }

    fun isMapApp(pkg: String, label: String): Boolean {
        val p = pkg.lowercase()
        val l = label.lowercase()
        return p == "com.google.android.apps.maps" ||
            p == "com.waze" ||
            p.contains("papago") ||
            p.contains("locnavi") ||
            p.contains("kingwaytek") ||
            p.contains("autonavi") ||
            p.contains("baidumap") ||
            p.contains("tomtom") ||
            p.contains("sygic") ||
            l.contains("地圖") ||
            l.contains("maps") ||
            l.contains("導航")
    }

    fun label(c: Context, pkg: String): String {
        if (pkg == PKG_CAR_3D) return "3D 模擬大道"
        cachedMap?.get(pkg)?.let { return it.label }
        return try {
            c.packageManager.getApplicationLabel(c.packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (_: Exception) { pkg }
    }

    fun icon(c: Context, pkg: String): Drawable? {
        if (pkg == PKG_CAR_3D) return androidx.core.content.ContextCompat.getDrawable(c, R.drawable.ic_car_3d_split)
        cachedMap?.get(pkg)?.let { return it.icon }
        return try {
            c.packageManager.getApplicationIcon(pkg)
        } catch (_: Exception) { null }
    }

    fun open(c: Context, pkg: String) {
        if (pkg == PKG_CAR_3D) {
            val intent = Intent(c, CarHalfActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            c.startActivity(intent)
            return
        }
        if (NavState.visible && Split.freeformAvailable(c)) {
            try {
                val (w, h) = Split.screenSize(c)
                val usableH = (h - NavOverlay.barHeightPx(c)).coerceAtLeast(h / 2)
                openBounds(c, pkg, Rect(0, 0, w, usableH))
                return
            } catch (_: Throwable) {}
        }
        val i = c.packageManager.getLaunchIntentForPackage(pkg) ?: return
        c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /**
     * 以指定範圍開啟(自由視窗)。只給範圍不夠:系統會當成全螢幕、直接忽略範圍,
     * 必須同時明確要求「自由視窗模式」(WINDOWING_MODE_FREEFORM = 5)。
     * 這個方法被系統隱藏,需要反射呼叫,必要時用 HiddenApiBypass 解除限制。
     */
    fun openBounds(c: Context, pkg: String, r: Rect) {
        val i = if (pkg == PKG_CAR_3D) {
            Intent(c, CarHalfActivity::class.java)
        } else {
            c.packageManager.getLaunchIntentForPackage(pkg) ?: return
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val o = ActivityOptions.makeBasic().setLaunchBounds(r)
        requestFreeform(o)
        c.startActivity(i, o.toBundle())
    }

    private fun requestFreeform(o: ActivityOptions) {
        fun call() {
            ActivityOptions::class.java
                .getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType)
                .invoke(o, 5)
        }
        try { call() } catch (_: Throwable) {
            try {
                if (!isHiddenApiExempted && Build.VERSION.SDK_INT >= 28) {
                    HiddenApiBypass.addHiddenApiExemptions("Landroid/app/ActivityOptions;")
                    isHiddenApiExempted = true
                }
                call()
            } catch (_: Throwable) { /* 系統不允許:只能退回全螢幕開啟 */ }
        }
    }
}

object Split {
    fun freeformAvailable(c: Context) =
        c.packageManager.hasSystemFeature("android.software.freeform_window_management") ||
            try { Settings.Global.getString(c.contentResolver, "enable_freeform_support") == "1" } catch (_: Exception) { false }

    fun screenSize(c: Context): Pair<Int, Int> {
        val d = DisplayMetrics()
        @Suppress("DEPRECATION")
        (c.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(d)
        return d.widthPixels to d.heightPixels
    }

    /** 兩個 App 各佔一半(自動扣除導覽列高度):直式螢幕 = 上下,橫式螢幕 = 左右 */
    fun launch(ctx: Context, combo: Combo) {
        val c = ctx.applicationContext
        if (!freeformAvailable(c)) {
            Toast.makeText(c, "此系統尚未啟用自由視窗(開發人員選項),畫面可能不會分割", Toast.LENGTH_LONG).show()
        }
        val (w, h) = screenSize(c)
        val navH = if (NavState.visible) NavOverlay.barHeightPx(c) else 0
        val usableH = (h - navH).coerceAtLeast(h / 2)
        val first: Rect
        val second: Rect
        if (h >= w) {
            first = Rect(0, 0, w, usableH / 2)
            second = Rect(0, usableH / 2, w, usableH)
        } else {
            first = Rect(0, 0, w / 2, usableH)
            second = Rect(w / 2, 0, w, usableH)
        }
        Apps.openBounds(c, combo.top, first)
        Handler(Looper.getMainLooper()).postDelayed({ Apps.openBounds(c, combo.bottom, second) }, 350)
    }

    /** 強制上下分割啟動（例如：上半地圖 + 下半 3D 模擬大道，或上半 3D 模擬大道 + 下半 Spotify） */
    fun launchVertical(ctx: Context, topPkg: String, bottomPkg: String) {
        val c = ctx.applicationContext
        if (!freeformAvailable(c)) {
            Toast.makeText(c, "此系統尚未啟用自由視窗(開發人員選項),畫面可能不會分割", Toast.LENGTH_LONG).show()
        }
        val (w, h) = screenSize(c)
        val navH = if (NavState.visible) NavOverlay.barHeightPx(c) else 0
        val usableH = (h - navH).coerceAtLeast(h / 2)
        val topBounds = Rect(0, 0, w, usableH / 2)
        val bottomBounds = Rect(0, usableH / 2, w, usableH)
        Apps.openBounds(c, topPkg, topBounds)
        Handler(Looper.getMainLooper()).postDelayed({ Apps.openBounds(c, bottomPkg, bottomBounds) }, 350)
    }
}
