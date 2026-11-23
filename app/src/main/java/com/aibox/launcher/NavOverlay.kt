package com.aibox.launcher

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RectShape
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout

/** 導覽列目前是否固定顯示。桌面、分屏與單一 App 視窗用它讓出底部區域 */
object NavState {
    @Volatile var visible = false
        private set
    var listener: ((Boolean) -> Unit)? = null
    fun set(v: Boolean) {
        if (visible == v) return
        visible = v
        listener?.invoke(v)
    }
}

/**
 * 全系統自適應寬度高質感導覽列(返回 / 首頁 / 最近工作 / 全部 App)。
 * - 與分割畫面 / 單一 App 共享底部安全高度 (`barHeightPx`)，避免遮擋下方按鈕
 * - 支援向下滑動暫時收合為右下角迷你呼出鈕，點擊即可展開，避免任何全螢幕 App 下緣被擋住
 */
class NavOverlay(private val svc: AccessibilityService) {
    private val wm = svc.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val u = Ui.unit(svc)
    private var bar: View? = null
    private var barParams: WindowManager.LayoutParams? = null
    private var miniHandle: View? = null
    private var isCollapsed = false

    private fun pu(v: Float) = (v * u).toInt()
    private fun params(w: Int, hgt: Int, flags: Int) = WindowManager.LayoutParams(
        w, hgt, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, flags, PixelFormat.TRANSLUCENT
    )

    fun start() { refresh() }

    fun touchDown() {}
    fun touchUp() {}

    /** 設定開關改變時呼叫 */
    fun refresh() {
        if (Store.navBarOn(svc)) {
            if (isCollapsed) showMiniHandle() else showFixedBar()
        } else {
            isCollapsed = false
            removeBarNow()
            removeMiniNow()
        }
    }

    /** 回到桌面時自動展開完整導覽列 */
    fun expandIfNeeded() {
        if (!Store.navBarOn(svc)) return
        if (isCollapsed) {
            isCollapsed = false
            removeMiniNow()
            showFixedBar()
        }
    }

    fun stop() {
        isCollapsed = false
        removeBarNow()
        removeMiniNow()
    }

    /** 建立並固定顯示於螢幕最底部的自適應高質感導覽列 */
    private fun showFixedBar() {
        removeMiniNow()
        if (bar == null) {
            val v = buildBar()
            val p = params(
                WindowManager.LayoutParams.MATCH_PARENT,
                pu(BAR_H_U),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            ).apply {
                gravity = Gravity.BOTTOM
                y = 0
            }
            try {
                wm.addView(v, p)
                bar = v
                barParams = p
            } catch (_: Exception) {
                return
            }
        }
        bar?.translationY = 0f
        bar?.alpha = 1.0f
        NavState.set(true)
    }

    private fun collapseToMini() {
        if (!Store.navBarOn(svc)) return
        isCollapsed = true
        removeBarNow()
        showMiniHandle()
    }

    private fun showMiniHandle() {
        if (miniHandle == null) {
            val v = buildMiniHandle()
            val d = svc.resources.displayMetrics.density
            val p = params(
                (44 * d).toInt(),
                (26 * d).toInt(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                x = (12 * d).toInt()
                y = (8 * d).toInt()
            }
            try {
                wm.addView(v, p)
                miniHandle = v
            } catch (_: Exception) {
                return
            }
        }
        NavState.set(false)
    }

    private fun removeBarNow() {
        bar?.let { safeRemove(it) }
        bar = null
        barParams = null
        if (!isCollapsed) NavState.set(false)
    }

    private fun removeMiniNow() {
        miniHandle?.let { safeRemove(it) }
        miniHandle = null
    }

    private fun safeRemove(v: View) { try { wm.removeView(v) } catch (_: Exception) {} }

    /** 建構漸層過渡潔白導覽列條 (返回、首頁、最近工作、全部 App)，支援向下滑動收合 */
    @SuppressLint("ClickableViewAccessibility")
    private fun buildBar(): View {
        val touchSlop = ViewConfiguration.get(svc).scaledTouchSlop
        val collapseThreshold = pu(3.5f).toFloat()

        val row = object : LinearLayout(svc) {
            private var downY = 0f
            private var dragging = false

            override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downY = ev.rawY
                        dragging = false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (ev.rawY - downY > touchSlop * 1.4f) {
                            dragging = true
                            return true
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        dragging = false
                    }
                }
                return super.onInterceptTouchEvent(ev)
            }

            override fun onTouchEvent(ev: MotionEvent): Boolean {
                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downY = ev.rawY
                        dragging = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val rawDy = ev.rawY - downY
                        if (!dragging && rawDy > touchSlop * 1.4f) dragging = true
                        if (dragging) {
                            val dy = rawDy.coerceAtLeast(0f)
                            translationY = dy
                            alpha = (1f - (dy / pu(BAR_H_U).toFloat()) * 0.6f).coerceIn(0.3f, 1f)
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        val dy = ev.rawY - downY
                        if (dragging && dy > collapseThreshold) {
                            collapseToMini()
                        } else {
                            animate().translationY(0f).alpha(1f).setDuration(150).start()
                        }
                        dragging = false
                        return true
                    }
                }
                return super.onTouchEvent(ev)
            }
        }.apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
            setPadding(pu(ROW_HPAD_U), pu(ROW_TOP_PAD_U), pu(ROW_HPAD_U), pu(ROW_BOT_PAD_U))
            background = ShapeDrawable(RectShape()).apply {
                shaderFactory = object : ShapeDrawable.ShaderFactory() {
                    override fun resize(width: Int, height: Int): Shader {
                        return LinearGradient(
                            0f, 0f, 0f, height.toFloat().coerceAtLeast(1f),
                            intArrayOf(
                                0x00FFFFFF,         // 頂端 100% 透明，消除水平切線
                                0x66FFFFFF,         // 上中段柔和白霧過渡
                                0xD4FFFFFF.toInt(), // 中下段漸濃白底
                                0xFCFFFFFF.toInt()  // 最底部純白承托圖示
                            ),
                            floatArrayOf(0f, 0.28f, 0.65f, 1.0f),
                            Shader.TileMode.CLAMP
                        )
                    }
                }
            }
            elevation = 0f
        }

        fun btn(res: Int, onClick: () -> Unit) = ImageView(svc).apply {
            setImageResource(res)
            setColorFilter(0xFF0F172A.toInt()) // 深色高對比圖示
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(pu(BTN_HPAD_U), pu(BTN_VPAD_U), pu(BTN_HPAD_U), pu(BTN_VPAD_U))
            foreground = android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x1F0F172A),
                null,
                GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(0xFFFFFFFF.toInt())
                    cornerRadius = pu(8f).toFloat()
                }
            )
            layoutParams = LinearLayout.LayoutParams(pu(ICON_U + 2 * BTN_HPAD_U), pu(ICON_U + 2 * BTN_VPAD_U)).apply {
                setMargins(pu(GAP_U), 0, pu(GAP_U), 0)
            }
            setOnClickListener { onClick() }
        }

        // 返回按鈕
        row.addView(btn(R.drawable.ic_nav_back) { svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK) })
        // 首頁按鈕
        row.addView(btn(R.drawable.ic_nav_home) { svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME) })
        // 最近工作按鈕
        row.addView(btn(R.drawable.ic_nav_recents) { svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS) })
        // 全部 App 按鈕 (最右側)
        row.addView(btn(R.drawable.ic_nav_apps) {
            try {
                val intent = Intent(svc, AppsActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                svc.startActivity(intent)
            } catch (_: Exception) {}
        })

        return row
    }

    /** 暫時收合時顯示於右下角的迷你展開膠囊鈕 */
    private fun buildMiniHandle(): View {
        val d = svc.resources.displayMetrics.density
        return object : View(svc) {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF0F172A.toInt()
                style = Paint.Style.STROKE
                strokeWidth = 2.2f * d
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            private val path = Path()

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val cx = width / 2f
                val cy = height / 2f
                val dx = 6f * d
                val dy = 3f * d
                path.reset()
                path.moveTo(cx - dx, cy + dy)
                path.lineTo(cx, cy - dy)
                path.lineTo(cx + dx, cy + dy)
                canvas.drawPath(path, paint)
            }
        }.apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(0xCCFFFFFF.toInt())
                setStroke(maxOf(1, (1 * d).toInt()), 0x260F172A)
                cornerRadius = 13f * d
            }
            elevation = 3f * d
            setOnClickListener {
                isCollapsed = false
                removeMiniNow()
                showFixedBar()
            }
        }
    }

    companion object {
        private const val ICON_U = 5.4f
        private const val BTN_HPAD_U = 3.5f
        private const val BTN_VPAD_U = 1.4f
        private const val ROW_HPAD_U = 1.0f
        private const val ROW_TOP_PAD_U = 2.2f
        private const val ROW_BOT_PAD_U = 0.8f
        private const val GAP_U = 5.0f
        private const val BAR_H_U = ICON_U + 2 * BTN_VPAD_U + ROW_TOP_PAD_U + ROW_BOT_PAD_U

        /** 導覽列實際覆蓋在螢幕底部的視窗高度(px)，分屏與全螢幕 App 需扣除這段高度 */
        fun barHeightPx(c: Context): Int = (Ui.unit(c) * BAR_H_U).toInt()

        /** 導覽列固定顯示時桌面預留的底部高度(px) */
        fun reservePx(c: Context): Int = (Ui.unit(c) * (BAR_H_U + 1.0f)).toInt()
    }
}
