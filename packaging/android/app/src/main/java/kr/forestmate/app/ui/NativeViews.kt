package kr.forestmate.app.ui

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import kr.forestmate.app.state.PhoneTab

object NativeViews {
    fun screen(context: Context, title: String, body: String): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 28, 28, 28)
            setBackgroundColor(Color.rgb(244, 247, 242))
            addView(TextView(context).apply {
                text = title
                textSize = 24f
                setTextColor(Color.rgb(27, 67, 50))
            })
            addView(TextView(context).apply {
                text = body
                textSize = 15f
                setTextColor(Color.rgb(36, 52, 43))
            })
        }

    fun tabButton(context: Context, tab: PhoneTab, onClick: () -> Unit): Button =
        Button(context).apply {
            text = tab.label
            isAllCaps = false
            gravity = Gravity.CENTER
            setOnClickListener { onClick() }
        }

    fun actionButton(context: Context, label: String, onClick: () -> Unit): Button =
        Button(context).apply {
            text = label
            isAllCaps = false
            setOnClickListener { onClick() }
        }

    fun statusText(context: Context, textValue: String): TextView =
        TextView(context).apply {
            text = textValue
            textSize = 14f
            setTextColor(Color.rgb(36, 52, 43))
        }
}
