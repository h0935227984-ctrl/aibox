package com.aibox.launcher

import android.os.Bundle
import android.widget.FrameLayout

class ClusterDisplayActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = FrameLayout(this).apply {
            id = android.R.id.content
        }
        setContentView(container)
    }
}
