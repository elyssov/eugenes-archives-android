package com.elyssov.archives

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat

class MainActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private lateinit var billing: BillingManager

    /** Guard so the tip dialog never re-appears within a single launch
     *  (e.g. on rotation, or a second BillingManager onReady callback). */
    private var dialogShownThisLaunch = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = 0xFF0a0e1a.toInt()
        window.navigationBarColor = 0xFF0a0e1a.toInt()

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
        webView.loadUrl("file:///android_asset/index.html")

        // Tip jar. Defensive: always schedule the dialog via the main-thread
        // Handler 800 ms after onCreate. `webView.postDelayed` turned out to
        // be unreliable on some devices (it requires the view to be attached
        // and animation frames to start firing — if anything blocks layout,
        // the callback can be deferred indefinitely).
        //
        // The `hasEverTipped` check is currently DISABLED so the dialog
        // always shows, regardless of any cached preference state. Once
        // we confirm the dialog is reliably appearing on the test device,
        // we'll restore the check in a later release.
        fun maybeShowTipDialog(source: String) {
            Log.d("TipDialog", "maybeShowTipDialog from=$source dialogShown=$dialogShownThisLaunch finishing=$isFinishing")
            if (dialogShownThisLaunch || isFinishing) return
            dialogShownThisLaunch = true
            try {
                TipDialog.show(this, billing) {
                    webView.loadUrl("file:///android_asset/help.html")
                }
            } catch (t: Throwable) {
                Log.e("TipDialog", "show() failed", t)
                dialogShownThisLaunch = false // allow retry
            }
        }
        billing = BillingManager(this) { maybeShowTipDialog("billing-onReady") }
        billing.start()
        Handler(Looper.getMainLooper()).postDelayed(
            { maybeShowTipDialog("timer-800ms") },
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
        } else {
            super.onBackPressed()
        }
    }
}
