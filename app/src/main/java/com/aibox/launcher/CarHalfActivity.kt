package com.aibox.launcher

import android.os.Bundle
import android.widget.FrameLayout

/**
 * 半屏 3D 模擬大道視窗：
 * 自動啟用 Lite Mode 降載（渲染解析度倍率上限 1.25、幀率節流在約 30fps/12.5fps），
 * 並於離開前景時完全暫停 WebGL 渲染，關閉時即時釋放 Context 與歸還記憶體。
 */
class CarHalfActivity : BaseActivity() {
    private var glbCarView: GlbCarView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val carView = GlbCarView(this).apply {
            setLiteMode(true)
        }
        glbCarView = carView
        val container = FrameLayout(this).apply {
            id = android.R.id.content
            addView(carView)
        }
        setContentView(container)
    }

    override fun onResume() {
        super.onResume()
        glbCarView?.resumeRendering()
    }

    override fun onPause() {
        super.onPause()
        glbCarView?.pauseRendering()
    }

    override fun onDestroy() {
        super.onDestroy()
        glbCarView?.destroyView()
        glbCarView = null
        System.gc()
    }
}
