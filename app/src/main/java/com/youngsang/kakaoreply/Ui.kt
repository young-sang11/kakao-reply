package com.youngsang.kakaoreply

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

object Ui {
    fun dp(c: Context, v: Int): Int = (v * c.resources.displayMetrics.density).toInt()

    fun text(c: Context, s: String, size: Float = 15f, bold: Boolean = false): TextView =
        TextView(c).apply {
            text = s
            textSize = size
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(c, 4), 0, dp(c, 4))
        }

    fun button(c: Context, s: String, onClick: () -> Unit): Button =
        Button(c).apply {
            text = s
            isAllCaps = false
            textSize = 15f
            setOnClickListener { onClick() }
        }

    fun card(c: Context, color: Int = 0x22888888): LinearLayout =
        LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(c, 12).toFloat()
                setColor(color)
            }
            val p = dp(c, 14)
            setPadding(p, p, p, p)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(c, 6), 0, dp(c, 6)) }
        }

    fun column(c: Context): LinearLayout =
        LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            val p = dp(c, 16)
            setPadding(p, p, p, dp(c, 40))
        }
}
