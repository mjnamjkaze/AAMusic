package com.gsvn.aamusic.widget

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.widget.Button
import android.widget.LinearLayout

/**
 * A minimal in-app QWERTY keyboard used when the app is projected onto an
 * Android Auto (secondary) display, where the system IME renders at an unusable
 * tiny / mis-aligned size. Because this keyboard is part of the app's OWN view
 * hierarchy, it renders at the correct size on the car screen and taps register
 * normally.
 *
 * Latin letters + digits + space/backspace/search only (no Vietnamese
 * diacritics); it feeds the app's search box, which matches case-insensitively.
 */
class CarKeyboard @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    /** Called with the character to insert at the cursor. */
    var onKey: (Char) -> Unit = {}
    var onBackspace: () -> Unit = {}
    var onSearch: () -> Unit = {}

    private val charRows = listOf(
        "1234567890",
        "qwertyuiop",
        "asdfghjkl",
        "zxcvbnm"
    )

    init {
        orientation = VERTICAL
        val p = dp(4)
        setPadding(p, p, p, p)
        for (row in charRows) addView(buildCharRow(row))
        addView(buildBottomRow())
    }

    private fun buildCharRow(chars: String): LinearLayout = newRow().apply {
        for (c in chars) addView(key(c.toString()) { onKey(c) }, keyParams(1f))
    }

    private fun buildBottomRow(): LinearLayout = newRow().apply {
        addView(key("⌫") { onBackspace() }, keyParams(1.6f))
        addView(key(",") { onKey(',') }, keyParams(1f))
        addView(key("space") { onKey(' ') }, keyParams(4f))
        addView(key(".") { onKey('.') }, keyParams(1f))
        addView(key("Tìm") { onSearch() }, keyParams(2f))
    }

    private fun newRow() = LinearLayout(context).apply {
        orientation = HORIZONTAL
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
    }

    private fun key(label: String, action: () -> Unit) = Button(context).apply {
        text = label
        isAllCaps = false
        // Never steal focus from the search field, otherwise the keyboard would
        // hide itself the moment a key is tapped.
        isFocusable = false
        isFocusableInTouchMode = false
        minWidth = 0
        minimumWidth = 0
        minHeight = 0
        minimumHeight = 0
        setPadding(0, 0, 0, 0)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        setOnClickListener { action() }
    }

    private fun keyParams(weight: Float) =
        LayoutParams(0, dp(48), weight).apply {
            val m = dp(2)
            setMargins(m, m, m, m)
        }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
