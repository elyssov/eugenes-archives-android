package com.elyssov.archives

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.UnderlineSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Tip-jar pop-up shown at app start until the user has tipped at least once.
 *
 * Layout (all built in code; no XML to keep the embedded asset bundle lean):
 *
 *   ┌─────────────────────────────────────┐
 *   │  Поддержать автора              [×] │  ← close button hidden first 3s
 *   │                                     │
 *   │  Архивы бесплатны. Если они вам    │
 *   │  нравятся — вы можете               │
 *   │  поблагодарить автора. Все средства │
 *   │  идут на продолжение исследований.  │
 *   │                                     │
 *   │  [$1] [$5] [$10] [$15] [$20]       │
 *   │                                     │
 *   │  Подробности — раздел «Помощь».    │
 *   └─────────────────────────────────────┘
 *
 * The close (×) button is disabled and invisible for the first 3 seconds
 * to make the prompt deliberate; it never blocks the underlying WebView.
 */
object TipDialog {

    fun show(
        activity: Activity,
        billing: BillingManager,
        onHelpRequested: () -> Unit,
    ): Dialog {
        val ctx = activity
        val density = ctx.resources.displayMetrics.density

        fun dp(value: Int): Int = (value * density).toInt()
        fun sp(value: Float): Float = value

        // ─── card (the rounded container) ───────────────────────────────
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(20), dp(24), dp(20))
            background = GradientDrawable().apply {
                setColor(0xFF1a2235.toInt())
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), 0xFF1e293b.toInt())
            }
        }

        // ─── top row: title + close (×) ────────────────────────────────
        val topRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(ctx).apply {
            text = "Support the author"
            setTextColor(0xFF34d399.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            )
        }
        val close = TextView(ctx).apply {
            text = "×"
            setTextColor(0xFF9ca3af.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
            setPadding(dp(12), 0, dp(4), 0)
            // Hidden + non-clickable for the first 3 seconds
            visibility = View.INVISIBLE
            isClickable = false
        }
        topRow.addView(title)
        topRow.addView(close)
        card.addView(topRow)

        // ─── body text ─────────────────────────────────────────────────
        val body = TextView(ctx).apply {
            text =
                "Eugene's Archives is free.\n" +
                "If it has been useful — consider thanking the author. " +
                "Every contribution goes directly to continued research, " +
                "writing, and software."
            setTextColor(0xFFe8dcc8.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setLineSpacing(dp(2).toFloat(), 1f)
            setPadding(0, dp(14), 0, dp(18))
        }
        card.addView(body)

        // ─── price buttons row ─────────────────────────────────────────
        val buttonRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val amounts = listOf(
            1 to BillingManager.SKU_1,
            5 to BillingManager.SKU_5,
            10 to BillingManager.SKU_10,
            15 to BillingManager.SKU_15,
            20 to BillingManager.SKU_20,
        )
        val priceButtons = mutableListOf<Button>()
        for ((amount, sku) in amounts) {
            val btn = Button(ctx).apply {
                text = "$$amount"
                setTextColor(0xFF34d399.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                isAllCaps = false
                background = GradientDrawable().apply {
                    setColor(0xFF111827.toInt())
                    cornerRadius = dp(8).toFloat()
                    setStroke(dp(1), 0xFF34d399.toInt())
                }
                setPadding(dp(8), dp(10), dp(8), dp(10))
            }
            val lp = LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            ).apply {
                marginStart = dp(4)
                marginEnd = dp(4)
            }
            btn.layoutParams = lp
            priceButtons.add(btn)
            buttonRow.addView(btn)
        }
        card.addView(buttonRow)

        // ─── footer link to Help ───────────────────────────────────────
        val footer = TextView(ctx).apply {
            val full = "Want to support on an ongoing basis? See the Help section for PayPal and card details."
            val linkStart = full.indexOf("Help")
            val linkEnd = linkStart + "Help".length
            val sp = SpannableString(full)
            sp.setSpan(object : ClickableSpan() {
                override fun onClick(widget: View) {
                    onHelpRequested()
                }
            }, linkStart, linkEnd, 0)
            sp.setSpan(ForegroundColorSpan(0xFF34d399.toInt()), linkStart, linkEnd, 0)
            sp.setSpan(UnderlineSpan(), linkStart, linkEnd, 0)
            text = sp
            movementMethod = LinkMovementMethod.getInstance()
            setTextColor(0xFF9ca3af.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, dp(16), 0, 0)
        }
        card.addView(footer)

        // ─── dialog wrapper ────────────────────────────────────────────
        val dialog = Dialog(activity).apply {
            requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
            setContentView(card)
            setCancelable(false)         // unlocked manually after 3s
            setCanceledOnTouchOutside(false)
            window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                val lp = attributes
                lp.width = (ctx.resources.displayMetrics.widthPixels * 0.88f).toInt()
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                attributes = lp
            }
        }

        // Wire button → billing
        for (i in priceButtons.indices) {
            val (_, sku) = amounts[i]
            priceButtons[i].setOnClickListener {
                billing.launchBilling(activity, sku)
                // Dismiss eagerly; if the flow succeeds BillingManager flips
                // hasEverTipped so we won't show again next launch. If the
                // user cancels Google's sheet, they'll see us next time —
                // intentional, no different from a real tip jar.
                dialog.dismiss()
            }
        }
        close.setOnClickListener { dialog.dismiss() }

        // Reveal close (×) after 3 seconds
        Handler(Looper.getMainLooper()).postDelayed({
            close.visibility = View.VISIBLE
            close.isClickable = true
            dialog.setCancelable(true)
        }, 3_000L)

        dialog.show()
        return dialog
    }
}
