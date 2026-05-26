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
        } else {
            super.onBackPressed()
        }
    }
}
