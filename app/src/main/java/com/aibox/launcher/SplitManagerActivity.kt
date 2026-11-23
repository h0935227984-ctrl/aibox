package com.aibox.launcher

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.GridLayout
import android.widget.Spinner
import android.widget.Toast

class SplitManagerActivity : BaseActivity() {
    private lateinit var apps: List<AppItem>
    private lateinit var grid: GridLayout

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContentView(R.layout.activity_split)
        reserveBottom(findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0))
        apps = Apps.listWithCar3D(this)
        grid = findViewById(R.id.list)

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, apps.map { it.label })
        val spTop = findViewById<Spinner>(R.id.spTop).also { it.adapter = adapter }
        val spBottom = findViewById<Spinner>(R.id.spBottom).also { it.adapter = adapter }
        if (apps.size > 1) spTop.setSelection(1)
        if (apps.size > 0) spBottom.setSelection(0)

        findViewById<View>(R.id.back).setOnClickListener { finish() }
        findViewById<View>(R.id.save).setOnClickListener {
            if (apps.isEmpty()) return@setOnClickListener
            val top = apps[spTop.selectedItemPosition]
            val bottom = apps[spBottom.selectedItemPosition]
            if (top.pkg == bottom.pkg) {
                Toast.makeText(this, "上下兩個 App 不能相同", Toast.LENGTH_SHORT).show(); return@setOnClickListener
            }
            val list = Store.combos(this)
            if (list.any { it.top == top.pkg && it.bottom == bottom.pkg }) {
                Toast.makeText(this, "這個組合已經存在", Toast.LENGTH_SHORT).show(); return@setOnClickListener
            }
            list.add(Combo(top.pkg, bottom.pkg))
            Store.saveCombos(this, list)
            refresh()
        }
        refresh()
    }

    private fun refresh() {
        val list = Store.combos(this)
        Folders.fill(this, grid, list, true) { idx ->
            list.removeAt(idx); Store.saveCombos(this, list); refresh()
        }
    }
}
