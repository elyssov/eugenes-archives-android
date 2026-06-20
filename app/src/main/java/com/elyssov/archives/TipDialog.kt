package com.elyssov.archives

import android.app.Activity
import android.app.Dialog
import android.content.Context
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
 * Trilingual (EN default, switchable to RU / VI via flag row at the top).
 *
 * Default language is always English regardless of the user's last language
 * choice in the WebView — the tip-jar lives in native UI, separate from
 * the reader's locale preference, and English is the broadest default for
 * a global audience.
 */
object TipDialog {

    // Shared with BillingManager so we don't multiply preference files.
    private const val PREFS = "tip_jar"
    private const val KEY_LANG = "dialog_lang"

    private data class Strings(
        val title: String,
        val body: String,
        val footerFull: String,
        val footerLinkText: String,
    )

    private val L10N = mapOf(
        "en" to Strings(
            title = "Support the author",
            body = "Eugene's Archives is free.\n" +
                "If it has been useful — consider thanking the author. " +
                "Every contribution goes directly to continued research, " +
                "writing, and software.",
            footerFull = "Want to support on an ongoing basis? See the Help section for PayPal and card details.",
            footerLinkText = "Help",
        ),
        "ru" to Strings(
            title = "Поддержать автора",
            body = "Eugene's Archives — бесплатное приложение.\n" +
                "Если оно вам пригодилось — можно поблагодарить автора. " +
                "Все средства идут на дальнейшие исследования, " +
                "тексты и софт.",
            footerFull = "Поддержать на регулярной основе? PayPal и реквизиты карты — в разделе «Помощь».",
            footerLinkText = "Помощь",
        ),
        "vi" to Strings(
            title = "Hỗ trợ tác giả",
            body = "Eugene's Archives là miễn phí.\n" +
                "Nếu nó hữu ích — hãy cân nhắc cảm ơn tác giả. " +
                "Mọi đóng góp đều dành cho nghiên cứu, viết lách và phần mềm.",
            footerFull = "Muốn hỗ trợ định kỳ? Xem mục Trợ giúp để biết PayPal và thông tin thẻ.",
            footerLinkText = "Trợ giúp",
        ),
        "zh" to Strings(
            title = "支持作者",
            body = "尤金的档案馆是免费的。\n" +
                "如果它对您有所帮助 — 请考虑感谢作者。" +
                "所有贡献都直接用于继续研究、写作和软件开发。",
            footerFull = "想要长期支持？请在「帮助」中查看 PayPal 与银行卡信息。",
            footerLinkText = "帮助",
        ),
    )

    fun show(
        activity: Activity,
        billing: BillingManager,
        onHelpRequested: () -> Unit,
    ): Dialog {
        val ctx = activity
        val density = ctx.resources.displayMetrics.density

        fun dp(value: Int): Int = (value * density).toInt()

        // Default language: ALWAYS English. WITHOUT EXCEPTIONS.
        // Per Eugene 20.06: tip-jar greeting должно всегда стартовать на en,
        // независимо от прошлых выборов или системной локали. Пользователь
        // может переключить флаг внутри одного показа диалога — но это НЕ
        // персистится и НЕ влияет на следующий запуск.
        var currentLang = "en"

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

        // ─── language row: 🇺🇸 🇷🇺 🇻🇳 ─────────────────────────────────
        val langRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        val langButtons = mutableMapOf<String, TextView>()
        for (lang in listOf("en", "ru", "vi", "zh")) {
            val flag = when (lang) {
                "en" -> "🇺🇸"
                "ru" -> "🇷🇺"
                "vi" -> "🇻🇳"
                "zh" -> "🇨🇳"
                else -> "?"
            }
            val btn = TextView(ctx).apply {
                text = flag
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                setPadding(dp(8), dp(4), dp(8), dp(4))
                alpha = if (lang == currentLang) 1f else 0.4f
                isClickable = true
            }
            langButtons[lang] = btn
            langRow.addView(btn)
        }
        card.addView(langRow)

        // ─── top row: title + close (×) ────────────────────────────────
        val topRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(ctx).apply {
            text = L10N[currentLang]!!.title
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
            visibility = View.INVISIBLE
            isClickable = false
        }
        topRow.addView(title)
        topRow.addView(close)
        card.addView(topRow)

        // ─── body text ─────────────────────────────────────────────────
        val body = TextView(ctx).apply {
            text = L10N[currentLang]!!.body
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
        for ((amount, _) in amounts) {
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
            setTextColor(0xFF9ca3af.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, dp(16), 0, 0)
            movementMethod = LinkMovementMethod.getInstance()
        }
        card.addView(footer)

        // ─── update everything when language changes ───────────────────
        fun applyLang() {
            val s = L10N[currentLang]!!
            title.text = s.title
            body.text = s.body
            val sp = SpannableString(s.footerFull)
            val linkStart = s.footerFull.indexOf(s.footerLinkText)
            if (linkStart >= 0) {
                val linkEnd = linkStart + s.footerLinkText.length
                sp.setSpan(object : ClickableSpan() {
                    override fun onClick(widget: View) { onHelpRequested() }
                }, linkStart, linkEnd, 0)
                sp.setSpan(ForegroundColorSpan(0xFF34d399.toInt()), linkStart, linkEnd, 0)
                sp.setSpan(UnderlineSpan(), linkStart, linkEnd, 0)
            }
            footer.text = sp
            for ((lang, btn) in langButtons) {
                btn.alpha = if (lang == currentLang) 1f else 0.4f
            }
        }
        applyLang()

        // Hook flag clicks — only changes текущий показ; не персистится,
        // чтобы tip-jar при следующем запуске снова стартовал на en.
        for ((lang, btn) in langButtons) {
            btn.setOnClickListener {
                currentLang = lang
                applyLang()
            }
        }

        // ─── dialog wrapper ────────────────────────────────────────────
        val dialog = Dialog(activity).apply {
            requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
            setContentView(card)
            setCancelable(false)
            setCanceledOnTouchOutside(false)
            window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                val lp = attributes
                lp.width = (ctx.resources.displayMetrics.widthPixels * 0.88f).toInt()
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                // Опустить диалог на ~5% высоты экрана от центра, чтобы он
                // не упирался в status-bar / camera notch — флаги становятся
                // нажимаемыми на устройствах с глубоким cutout.
                lp.gravity = Gravity.CENTER
                lp.y = (ctx.resources.displayMetrics.heightPixels * 0.05f).toInt()
                attributes = lp
            }
        }

        // Wire button → billing
        for (i in priceButtons.indices) {
            val (_, sku) = amounts[i]
            priceButtons[i].setOnClickListener {
                billing.launchBilling(activity, sku)
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
