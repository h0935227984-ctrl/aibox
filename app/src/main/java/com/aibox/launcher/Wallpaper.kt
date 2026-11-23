package com.aibox.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.FrameLayout
import java.io.File

object Wallpaper {
    fun file(c: Context): File? {
        val paths = Store.wallpaperPaths(c)
        if (paths.isNotEmpty()) {
            val path = if (Store.wallpaperRandomOn(c)) paths.random() else paths[0]
            val f = File(path)
            if (f.exists()) return f
        }
        return Store.wallpaper(c)?.let { File(it) }?.takeIf { it.exists() }
    }

    fun clear(c: Context) {
        Store.wallpaperPaths(c).forEach { File(it).delete() }
        Store.setWallpaperPaths(c, emptyList())
        Store.wallpaper(c)?.let { File(it).delete() }
        Store.setWallpaper(c, null, false)
    }

    /** 把使用者選的多張圖片/影片複製進 App 私有空間 */
    fun importUris(c: Context, uris: List<Uri>, done: (Boolean) -> Unit) {
        val app = c.applicationContext
        Thread {
            val paths = mutableListOf<String>()
            var firstVideo = false
            for ((index, uri) in uris.withIndex()) {
                try {
                    val video = (app.contentResolver.getType(uri) ?: "").startsWith("video/")
                    val out = File(app.filesDir, "wallpaper_${System.currentTimeMillis()}_$index." + if (video) "mp4" else "img")
                    app.contentResolver.openInputStream(uri)!!.use { i -> out.outputStream().use { o -> i.copyTo(o) } }
                    paths.add(out.path)
                    if (index == 0) firstVideo = video
                } catch (_: Exception) {}
            }
            val ok = paths.isNotEmpty()
            if (ok) {
                clear(app)
                Store.setWallpaperPaths(app, paths)
                Store.setWallpaper(app, paths[0], firstVideo)
            }
            Handler(Looper.getMainLooper()).post { done(ok) }
        }.start()
    }

    /** 讀取整個資料夾內的所有圖片與影片 */
    fun importFolderFromAppFolder(c: Context): Boolean {
        val dir = c.getExternalFilesDir(null) ?: return false
        val files = dir.listFiles() ?: return false
        val paths = mutableListOf<String>()
        var firstVideo = false
        for (f in files) {
            val name = f.name.lowercase()
            if (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".mp4")) {
                val ext = name.substringAfterLast('.')
                val out = File(c.filesDir, "wallpaper_${System.currentTimeMillis()}_${paths.size}.$ext")
                f.copyTo(out, true)
                paths.add(out.path)
                if (paths.size == 1) firstVideo = (ext == "mp4")
            }
        }
        if (paths.isNotEmpty()) {
            clear(c)
            Store.setWallpaperPaths(c, paths)
            Store.setWallpaper(c, paths[0], firstVideo)
            return true
        }
        return false
    }

    /** 依螢幕大小縮小解碼,避免大圖爆記憶體 */
    fun decode(path: String, w: Int, h: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var s = 1
        while (bounds.outWidth / (s * 2) >= w && bounds.outHeight / (s * 2) >= h) s *= 2
        return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = s })
    }
}

/** 影片桌布:靜音、循環播放、置中裁切填滿 */
class VideoBg(private val tv: TextureView, private val onError: () -> Unit) : TextureView.SurfaceTextureListener {
    private var mp: MediaPlayer? = null
    private var path: String? = null
    private var surface: Surface? = null

    init { tv.surfaceTextureListener = this }

    fun play(p: String) { if (p == path && mp != null) return; path = p; start() }
    fun release() { mp?.release(); mp = null }

    private fun start() {
        val p = path ?: return
        val s = surface ?: return
        release()
        try {
            mp = MediaPlayer().apply {
                setSurface(s)
                setDataSource(p)
                isLooping = true
                setVolume(0f, 0f)
                setOnVideoSizeChangedListener { _, w, h -> fit(w, h) }
                setOnPreparedListener { it.start() }
                setOnErrorListener { _, _, _ -> onError(); true }
                prepareAsync()
            }
        } catch (e: Exception) { onError() }
    }

    private fun fit(vw: Int, vh: Int) {
        val parent = tv.parent as? View ?: return
        if (vw == 0 || vh == 0 || parent.width == 0) return
        val sc = maxOf(parent.width.toFloat() / vw, parent.height.toFloat() / vh)
        tv.layoutParams = FrameLayout.LayoutParams((vw * sc).toInt(), (vh * sc).toInt(), Gravity.CENTER)
    }

    override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) { surface = Surface(st); start() }
    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
        release(); surface?.release(); surface = null; return true
    }
    override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
}

/** 圖片虛化:先縮成小圖再做方框模糊,放大顯示時會更柔和;速度快,車機也跑得動 */
object Blur {
    fun small(path: String): Bitmap? {
        val b = Wallpaper.decode(path, 400, 400) ?: return null
        val sc = 400f / maxOf(b.width, b.height)
        if (sc >= 1f) return b
        return Bitmap.createScaledBitmap(
            b, (b.width * sc).toInt().coerceAtLeast(1), (b.height * sc).toInt().coerceAtLeast(1), true
        )
    }

    /** strength 0..100 */
    fun apply(src: Bitmap, strength: Int): Bitmap {
        val out = src.copy(Bitmap.Config.ARGB_8888, true) ?: return src
        val r = (1 + strength / 100f * 24f).toInt()
        val w = out.width
        val h = out.height
        val px = IntArray(w * h)
        out.getPixels(px, 0, w, 0, 0, w, h)
        repeat(3) { box(px, w, h, r, true); box(px, w, h, r, false) }
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }

    private fun box(px: IntArray, w: Int, h: Int, r: Int, horiz: Boolean) {
        val len = if (horiz) w else h
        val lines = if (horiz) h else w
        val step = if (horiz) 1 else w
        val stride = if (horiz) w else 1
        val tmp = IntArray(len)
        val div = 2 * r + 1
        for (l in 0 until lines) {
            val base = l * stride
            var ra = 0; var ga = 0; var ba = 0
            for (i in -r..r) {
                val p = px[base + i.coerceIn(0, len - 1) * step]
                ra += (p shr 16) and 0xFF; ga += (p shr 8) and 0xFF; ba += p and 0xFF
            }
            for (i in 0 until len) {
                tmp[i] = (0xFF shl 24) or ((ra / div) shl 16) or ((ga / div) shl 8) or (ba / div)
                val add = px[base + (i + r + 1).coerceAtMost(len - 1) * step]
                val sub = px[base + (i - r).coerceAtLeast(0) * step]
                ra += ((add shr 16) and 0xFF) - ((sub shr 16) and 0xFF)
                ga += ((add shr 8) and 0xFF) - ((sub shr 8) and 0xFF)
                ba += (add and 0xFF) - (sub and 0xFF)
            }
            for (i in 0 until len) px[base + i * step] = tmp[i]
        }
    }
}
