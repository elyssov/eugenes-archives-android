package com.elyssov.archives

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
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
    private var supportDialogOpen = false
    private val mainHandler = Handler(Looper.getMainLooper())

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        // Compliance с targetSdk 35: enableEdgeToEdge() обязателен (старые
        // window.statusBarColor / navigationBarColor под Android 15 deprecated
        // и помечаются Google Play как «неподдерживаемые API»). Цвета баров
        // больше не задаём в коде — они прозрачные, под ними просвечивает
        // windowBackground (#0a0e1a в themes.xml) — визуально то же самое.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Cutout policy: на Android 15+ Play Console помечает
        // LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER как "unsupported edge-to-edge
        // API". Безопасное поведение достигается через onApplyWindowInsets
        // ниже — мы примешиваем WindowInsetsCompat.Type.displayCutout() в
        // padding WebView, поэтому контент по-прежнему не лезет под вырез
        // камеры (был баг на OnePlus 10 Pro в v2.8).

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

        // Voluntary studio support. Delayed long enough that the reader has
        // had time to enter the archive before the one-time request appears.
        lateinit var maybeShowSupportDialog: () -> Unit
        fun scheduleSupport(delayMs: Long) {
            mainHandler.postDelayed({ maybeShowSupportDialog() }, delayMs)
        }
        maybeShowSupportDialog = show@{
            if (supportDialogOpen || isFinishing || isDestroyed || SupportDialog.isAcknowledged(this)) return@show
            supportDialogOpen = true
            try {
                SupportDialog.show(
                    activity = this,
                    onHelpRequested = { webView.loadUrl("file:///android_asset/help.html") },
                    onDismiss = {
                    supportDialogOpen = false
                    if (!isFinishing && !isDestroyed && !SupportDialog.isAcknowledged(this)) {
                        scheduleSupport(SUPPORT_REPEAT_MS)
                    }
                    },
                )
            } catch (t: Throwable) {
                Log.e("SupportDialog", "show() failed", t)
                supportDialogOpen = false
                scheduleSupport(SUPPORT_REPEAT_MS)
            }
        }
        scheduleSupport(SUPPORT_FIRST_DELAY_MS)
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
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

    companion object {
        private const val SUPPORT_FIRST_DELAY_MS = 5 * 60 * 1000L
        private const val SUPPORT_REPEAT_MS = 10 * 60 * 1000L
    }
}
