package com.aibox.launcher

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.MotionEvent
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import java.io.File

/**
 * 載入 3D 車輛與痛車塗裝的高效能 3D 互動旋轉檢視器 (WebView + Three.js / WebGL)
 * 支援 360 度手動與巡航旋轉、特斯拉彩虹大道、GPS 移動/轉彎角度連動與視角平滑過渡。
 * 針對高通 Snapdragon 6350 (Adreno 619L) 實現切背景即時暫停 WebGL 與分屏降載優化。
 */
class GlbCarView(context: Context) : FrameLayout(context) {
    val webView = WebView(context)
    private var isPageLoaded = false
    private var latestSpeedKmh = 0f
    private var latestBearingDeg = -1f
    private var latestRainbow = Store.rainbowRoadOn(context)
    private var isLiteMode = false

    inner class AndroidBridge {
        @JavascriptInterface
        fun pickDecalImage() {
            (context as? HomeActivity)?.launchDecalImagePicker()
        }

        @JavascriptInterface
        fun openApps() {
            (context as? HomeActivity)?.runOnUiThread {
                context.startActivity(Intent(context, AppsActivity::class.java))
            }
        }

        @JavascriptInterface
        fun setRainbowRoad(enabled: Boolean) {
            latestRainbow = enabled
            Store.setRainbowRoadOn(context, enabled)
        }
    }

    init {
        setBackgroundColor(Color.TRANSPARENT)
        webView.setBackgroundColor(Color.TRANSPARENT)
        webView.setLayerType(LAYER_TYPE_HARDWARE, null)
        webView.webChromeClient = WebChromeClient()
        webView.addJavascriptInterface(AndroidBridge(), "AndroidBridge")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val uri = request.url
                if (uri.host == "appassets.androidplatform.net") {
                    val path = uri.path ?: return null
                    if (path.startsWith("/assets/")) {
                        val assetName = path.removePrefix("/assets/")
                        val corsHeaders = mapOf(
                            "Access-Control-Allow-Origin" to "*",
                            "Cache-Control" to "no-cache"
                        )
                        if (assetName == "custom_car.glb") {
                            val customFile = File(context.filesDir, "custom_car.glb")
                            if (customFile.exists()) {
                                return WebResourceResponse("model/gltf-binary", null, 200, "OK", corsHeaders, customFile.inputStream())
                            }
                        }
                        val mime = when {
                            assetName.endsWith(".html", true) -> "text/html"
                            assetName.endsWith(".js", true) -> "application/javascript"
                            assetName.endsWith(".glb", true) -> "model/gltf-binary"
                            assetName.endsWith(".png", true) -> "image/png"
                            assetName.endsWith(".jpg", true) || assetName.endsWith(".jpeg", true) -> "image/jpeg"
                            else -> "application/octet-stream"
                        }
                        val encoding = if (mime.startsWith("text/") || mime == "application/javascript") "UTF-8" else null
                        return try {
                            WebResourceResponse(mime, encoding, 200, "OK", corsHeaders, context.assets.open(assetName))
                        } catch (_: Exception) {
                            null
                        }
                    }
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                isPageLoaded = true
                setRainbowRoad(latestRainbow)
                if (isLiteMode) setLiteMode(true)
                setGpsData(lastSpeedKmhSafe(), latestBearingDeg)
            }
        }

        @Suppress("DEPRECATION")
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            useWideViewPort = false
            loadWithOverviewMode = false
            cacheMode = WebSettings.LOAD_NO_CACHE
        }

        addView(webView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        val rainbowOn = latestRainbow
        val customGlb = File(context.filesDir, "custom_car.glb")
        val modelParam = if (customGlb.exists()) "custom_car.glb" else "carnew.glb"
        webView.loadUrl("https://appassets.androidplatform.net/assets/car_viewer.html?model=$modelParam&rainbow=$rainbowOn")
    }

    private fun lastSpeedKmhSafe() = latestSpeedKmh

    /** 離開桌面切換至地圖或 Spotify 時，完全暫停 WebGL requestAnimationFrame 釋放 GPU/CPU 給前景 App */
    fun pauseRendering() {
        webView.onPause()
        webView.pauseTimers()
        if (isPageLoaded) {
            webView.evaluateJavascript("if(window.pauseAnimation)window.pauseAnimation()", null)
        }
    }

    /** 回到桌面時恢復 WebGL 渲染 */
    fun resumeRendering() {
        webView.onResume()
        webView.resumeTimers()
        if (isPageLoaded) {
            webView.evaluateJavascript("if(window.resumeAnimation)window.resumeAnimation()", null)
        }
    }

    /** 分屏模式降載：限制渲染解析度 pixelRatio 與調低幀率，讓地圖與音樂執行更流暢 */
    fun setLiteMode(enabled: Boolean) {
        isLiteMode = enabled
        if (!isPageLoaded) return
        webView.post {
            webView.evaluateJavascript("if(window.setLiteMode)window.setLiteMode($enabled)", null)
        }
    }

    /** 關閉視窗時完全銷毀 WebGL Context 與 WebView，還原記憶體給系統 */
    fun destroyView() {
        try {
            if (isPageLoaded) {
                webView.evaluateJavascript("if(window.disposeWebGL)window.disposeWebGL()", null)
            }
            webView.stopLoading()
            webView.clearHistory()
            removeAllViews()
            webView.destroy()
        } catch (_: Exception) {}
    }

    fun setGpsData(speedKmh: Float, bearingDeg: Float) {
        latestSpeedKmh = speedKmh
        latestBearingDeg = bearingDeg
        if (!isPageLoaded) return
        webView.post {
            webView.evaluateJavascript("if(window.setGpsData)setGpsData($speedKmh, $bearingDeg)", null)
        }
    }

    fun setGpsSpeed(speedKmh: Float) {
        setGpsData(speedKmh, latestBearingDeg)
    }

    fun setRainbowRoad(enabled: Boolean) {
        latestRainbow = enabled
        if (!isPageLoaded) return
        webView.post {
            webView.evaluateJavascript("if(window.setRainbowRoad)setRainbowRoad($enabled)", null)
        }
    }

    fun loadDecalDataUrl(dataUrl: String) {
        webView.post {
            webView.evaluateJavascript("if(window.setDecalDataUrl)setDecalDataUrl('$dataUrl')", null)
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)
        return super.onInterceptTouchEvent(ev)
    }
}
