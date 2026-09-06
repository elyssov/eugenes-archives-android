package com.elyssov.archives

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.net.Uri
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

/** Recurring voluntary support request. No purchase, unlock, or Play Billing. */
object SupportDialog {
    private const val PREFS = "studio_support"
    private const val KEY_ACKNOWLEDGED = "acknowledged"
    private const val PAYPAL_URL = "https://paypal.me/EVGENIILISOVSKII"
    private const val CLOSE_DELAY_MS = 5_000L

    private data class Strings(
        val title: String,
        val body: String,
        val policy: String,
        val paypal: String,
        val help: String,
        val close: String,
        val acknowledged: String,
    )

    private val L10N = mapOf(
        "en" to Strings(
            "Support the studio",
            "Eugene's Archives is free and works offline. If it has been useful, you can support the studio directly.",
            "This is a voluntary contribution. Nothing is purchased or unlocked; 100% goes to the creator.",
            "OPEN PAYPAL",
            "Other support details",
            "CLOSE",
            "I SUPPORTED — HIDE THIS NOTICE",
        ),
        "ru" to Strings(
            "Поддержать студию",
            "«Архивы Юджина» бесплатны и работают офлайн. Если они вам пригодились, можно напрямую поддержать студию.",
            "Это добровольное пожертвование. Ничего не покупается и не разблокируется; 100% получает автор.",
            "ОТКРЫТЬ PAYPAL",
            "Другие способы поддержки",
            "ЗАКРЫТЬ",
            "Я ПОДДЕРЖАЛ — БОЛЬШЕ НЕ ПОКАЗЫВАТЬ",
        ),
        "vi" to Strings(
            "Hỗ trợ studio",
            "Eugene's Archives miễn phí và hoạt động ngoại tuyến. Nếu ứng dụng hữu ích, bạn có thể hỗ trợ studio trực tiếp.",
            "Đây là khoản đóng góp tự nguyện. Không có nội dung hay tính năng nào được mua hoặc mở khóa; 100% đến tay tác giả.",
            "MỞ PAYPAL",
            "Các cách hỗ trợ khác",
            "ĐÓNG",
            "ĐÃ ỦNG HỘ — ẨN THÔNG BÁO NÀY",
        ),
    )

    fun isAcknowledged(activity: Activity): Boolean =
        activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE)
            .getBoolean(KEY_ACKNOWLEDGED, false)

    fun show(activity: Activity, onHelpRequested: () -> Unit, onDismiss: () -> Unit): Dialog {
        val ctx = activity
        val density = ctx.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        var currentLang = "en"

        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(20), dp(24), dp(20))
            background = GradientDrawable().apply {
                setColor(0xFF1a2235.toInt())
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), 0xFF1e293b.toInt())
            }
        }

        val langRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        val langButtons = mutableMapOf<String, TextView>()
        for (lang in L10N.keys) {
            val flag = when (lang) { "en" -> "🇺🇸"; "ru" -> "🇷🇺"; else -> "🇻🇳" }
            val button = TextView(ctx).apply {
                text = flag
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                setPadding(dp(8), dp(4), dp(8), dp(4))
                alpha = if (lang == currentLang) 1f else 0.4f
                isClickable = true
            }
            langButtons[lang] = button
            langRow.addView(button)
        }
        card.addView(langRow)

        val topRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(ctx).apply {
            setTextColor(0xFF34d399.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
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

        val body = TextView(ctx).apply {
            setTextColor(0xFFe8dcc8.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setLineSpacing(dp(2).toFloat(), 1f)
            setPadding(0, dp(14), 0, dp(8))
        }
        card.addView(body)

        val policy = TextView(ctx).apply {
            setTextColor(0xFF9ca3af.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, 0, 0, dp(16))
        }
        card.addView(policy)

        val paypal = Button(ctx).apply {
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
        card.addView(paypal, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val paypalLink = TextView(ctx).apply {
            setTextColor(0xFF34d399.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, 0)
        }
        card.addView(paypalLink)

        val acknowledged = Button(ctx).apply {
            setTextColor(0xFF111827.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            isAllCaps = false
            visibility = View.INVISIBLE
            isClickable = false
            background = GradientDrawable().apply {
                setColor(0xFF34d399.toInt())
                cornerRadius = dp(8).toFloat()
            }
            setPadding(dp(8), dp(10), dp(8), dp(10))
        }
        card.addView(acknowledged, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val help = TextView(ctx).apply {
            setTextColor(0xFF9ca3af.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, 0)
            movementMethod = LinkMovementMethod.getInstance()
        }
        card.addView(help)

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
                lp.gravity = Gravity.CENTER
                attributes = lp
            }
        }

        fun openPayPal() {
            runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PAYPAL_URL))) }
            dialog.dismiss()
        }
        paypal.setOnClickListener { openPayPal() }
        paypalLink.setOnClickListener { openPayPal() }
        close.setOnClickListener { dialog.dismiss() }
        acknowledged.setOnClickListener {
            ctx.getSharedPreferences(PREFS, Activity.MODE_PRIVATE)
                .edit().putBoolean(KEY_ACKNOWLEDGED, true).apply()
            dialog.dismiss()
        }
        dialog.setOnDismissListener { onDismiss() }

        fun applyLang() {
            val s = L10N[currentLang]!!
            title.text = s.title
            body.text = s.body
            policy.text = s.policy
            paypal.text = "[ ${s.paypal} ]"
            paypalLink.text = PAYPAL_URL
            acknowledged.text = "[ ${s.acknowledged} ]"
            val helpText = SpannableString(s.help)
            helpText.setSpan(object : ClickableSpan() {
                override fun onClick(widget: View) { dialog.dismiss(); onHelpRequested() }
            }, 0, helpText.length, 0)
            helpText.setSpan(ForegroundColorSpan(0xFF34d399.toInt()), 0, helpText.length, 0)
            helpText.setSpan(UnderlineSpan(), 0, helpText.length, 0)
            help.text = helpText
            for ((lang, button) in langButtons) button.alpha = if (lang == currentLang) 1f else 0.4f
        }
        applyLang()
        for ((lang, button) in langButtons) button.setOnClickListener { currentLang = lang; applyLang() }

        Handler(Looper.getMainLooper()).postDelayed({
            if (dialog.isShowing) {
                close.visibility = View.VISIBLE
                close.isClickable = true
                acknowledged.visibility = View.VISIBLE
                acknowledged.isClickable = true
                dialog.setCancelable(true)
            }
        }, CLOSE_DELAY_MS)
        dialog.show()
        return dialog
    }
}
