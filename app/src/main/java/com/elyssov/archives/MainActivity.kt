package com.elyssov.archives

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private lateinit var billing: BillingManager

    /** Guard so the tip dialog never re-appears within a single launch
     *  (e.g. on rotation, or a second BillingManager onReady callback). */
    private var dialogShownThisLaunch = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        // Compliance с targetSdk 35: enableEdgeToEdge() обязателен (старые
        // window.statusBarColor / navigationBarColor под Android 15 deprecated
        // и помечаются Google Play как «неподдерживаемые API»). Цвета баров
        // больше не задаём в коде — они прозрачные, под ними просвечивает
        // windowBackground (#0a0e1a в themes.xml) — визуально то же самое.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Cutout policy НЕ deprecated. По прежнему держим WebView подальше
        // от выреза камеры (тестер на OnePlus 10 Pro показал, что текст
        // лез под фронт-камеру при свободном cutout).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
        }

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.textZoom = 100
            settings.setSupportZoom(false)
            settings.builtInZoomControls = false
            settings.displayZoomControls = false
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true

            setBackgroundColor(0xFF0a0e1a.toInt())

            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
        }

        setContentView(webView)

        // Под edge-to-edge система рисует прозрачные status / navigation bars
        // поверх контента. Применяем insets как padding к WebView, чтобы
        // содержимое не лезло под бары (и под cutout — мы его всё равно
        // избегаем через LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER, но на всякий
        // случай в insets подмешиваем displayCutout — на устройствах, где
        // системa всё-таки уберёт его в safe-area, мы это учитываем).
        ViewCompat.setOnApplyWindowInsetsListener(webView) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
                    or WindowInsetsCompat.Type.displayCutout()
            )
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        webView.loadUrl("file:///android_asset/index.html")

        // Tip jar. Scheduled via the main-thread Handler 800 ms after
        // onCreate (webView.postDelayed is unreliable — depends on view
        // attachment + animation frames).
        //
        // `hasEverTipped` is respected: once a user has tipped any amount,
        // the prompt never appears again on this Google account, even
        // after reinstall (BillingManager.queryPurchases on startup
        // restores the flag from Play).
        fun maybeShowTipDialog() {
            if (dialogShownThisLaunch || isFinishing) return
            if (::billing.isInitialized && billing.hasEverTipped) return
            dialogShownThisLaunch = true
            try {
                TipDialog.show(this, billing) {
                    webView.loadUrl("file:///android_asset/help.html")
                }
            } catch (t: Throwable) {
                Log.e("TipDialog", "show() failed", t)
                dialogShownThisLaunch = false
            }
        }
        billing = BillingManager(this) { maybeShowTipDialog() }
        billing.start()
        Handler(Looper.getMainLooper()).postDelayed(
            { maybeShowTipDialog() },
            800L
        )
    }

    override fun onDestroy() {
        if (::billing.isInitialized) billing.release()
        super.onDestroy()
    }

    @Deprecated("Use OnBackPressedDispatcher")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
            return
        }
        // Confirm exit at the root of WebView history — previously the app
        // just closed silently, which felt abrupt (tester feedback).
        AlertDialog.Builder(this)
            .setTitle("Eugene's Archives")
            .setMessage("Exit the app?")
            .setPositiveButton("Exit") { _, _ -> super.onBackPressed() }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
