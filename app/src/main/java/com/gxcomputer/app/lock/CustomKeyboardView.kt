package com.gxcomputer.app.lock

import android.view.Gravity
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import com.gxcomputer.app.R

/**
 * Kilit ekranının kendi özel klavyesi - sistemin varsayılan klavyesi yerine.
 * Basit ve sağlam olması için satırlar programatik LinearLayout ile üretilir.
 */
class CustomKeyboardView(
    private val container: LinearLayout,
    private val onKey: (String) -> Unit,
    private val onBackspace: () -> Unit,
    private val onDone: () -> Unit
) {
    private val rows = listOf(
        "1234567890",
        "qwertyuıop",
        "asdfgğhjklş",
        "zxcvbnmöç"
    )

    fun build() {
        container.removeAllViews()
        val ctx = container.context

        rows.forEach { rowChars ->
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            rowChars.forEach { c ->
                row.addView(makeKey(c.toString()))
            }
            container.addView(row)
        }

        // Alt satır: boşluk + sil + tamam
        val bottomRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val backspaceBtn = ImageButton(ctx).apply {
            setImageResource(R.drawable.ic_backspace)
            background = androidx.core.content.ContextCompat.getDrawable(ctx, R.drawable.bg_keyboard_key)
            layoutParams = LinearLayout.LayoutParams(0, 44.dp(ctx), 1f).apply { setMargins(2.dp(ctx), 2.dp(ctx), 2.dp(ctx), 2.dp(ctx)) }
            setOnClickListener { onBackspace() }
        }

        val spaceBtn = Button(ctx, null, 0, R.style.GXKeyboardKey).apply {
            text = ""
            layoutParams = LinearLayout.LayoutParams(0, 44.dp(ctx), 3f).apply { setMargins(2.dp(ctx), 2.dp(ctx), 2.dp(ctx), 2.dp(ctx)) }
            setOnClickListener { onKey(" ") }
        }

        val doneBtn = Button(ctx, null, 0, R.style.GXKeyboardKey).apply {
            text = "OK"
            setTextColor(ctx.getColor(R.color.gx_accent))
            layoutParams = LinearLayout.LayoutParams(0, 44.dp(ctx), 1f).apply { setMargins(2.dp(ctx), 2.dp(ctx), 2.dp(ctx), 2.dp(ctx)) }
            setOnClickListener { onDone() }
        }

        bottomRow.addView(backspaceBtn)
        bottomRow.addView(spaceBtn)
        bottomRow.addView(doneBtn)
        container.addView(bottomRow)
    }

    private fun makeKey(char: String): Button {
        val ctx = container.context
        return Button(ctx, null, 0, R.style.GXKeyboardKey).apply {
            text = char
            gravity = Gravity.CENTER
            setOnClickListener { onKey(char) }
        }
    }

    private fun Int.dp(ctx: android.content.Context): Int =
        (this * ctx.resources.displayMetrics.density).toInt()
}
