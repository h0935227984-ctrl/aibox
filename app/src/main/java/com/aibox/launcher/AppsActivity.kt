package com.aibox.launcher

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.ImageView
import android.widget.TextView

class AppsActivity : BaseActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContentView(R.layout.activity_apps)
        reserveBottom(findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0))
        findViewById<View>(R.id.back).setOnClickListener { finish() }
        val apps = Apps.list(this)
        val grid = findViewById<GridView>(R.id.grid)
        grid.adapter = object : BaseAdapter() {
            override fun getCount() = apps.size
            override fun getItem(p: Int) = apps[p]
            override fun getItemId(p: Int) = p.toLong()
            override fun getView(p: Int, cv: View?, parent: ViewGroup): View {
                val v = cv ?: layoutInflater.inflate(R.layout.item_app, parent, false)
                v.findViewById<ImageView>(R.id.icon).setImageDrawable(apps[p].icon)
                v.findViewById<TextView>(R.id.label).text = apps[p].label
                return v
            }
        }
        grid.setOnItemClickListener { _, _, p, _ ->
            val item = apps[p]
            if (Apps.isMapApp(item.pkg, item.label)) {
                showLaunchModeChooser(item)
            } else {
                Apps.open(this, item.pkg)
                finish()
            }
        }
        grid.setOnItemLongClickListener { _, _, p, _ ->
            showLaunchModeChooser(apps[p])
            true
        }
    }

    private fun showLaunchModeChooser(item: AppItem) {
        val options = arrayOf(
            "全螢幕開啟「${item.label}」",
            "上半螢幕「${item.label}」＋ 下半「3D 模擬大道」",
            "上半「3D 模擬大道」＋ 下半螢幕「${item.label}」"
        )
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("選擇開啟方式")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> Apps.open(this, item.pkg)
                    1 -> Split.launchVertical(this, item.pkg, Apps.PKG_CAR_3D)
                    2 -> Split.launchVertical(this, Apps.PKG_CAR_3D, item.pkg)
                }
                finish()
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
