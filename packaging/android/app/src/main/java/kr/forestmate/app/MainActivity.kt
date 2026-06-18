package kr.forestmate.app

import android.app.Activity
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import kr.forestmate.core.api.ApiConfig

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        root.addView(TextView(this).apply {
            text = "ForestMate native"
            textSize = 24f
        })
        root.addView(TextView(this).apply {
            text = ApiConfig.DEFAULT_BASE_URL
            textSize = 13f
        })
        setContentView(root)
    }
}
