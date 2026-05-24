package com.elyssov.archives

import android.annotation.SuppressLint
import android.os.Bundle
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

        // Tip jar. Billing connects asynchronously; on devices without
        // Play Services (emulator, China ROM) `onReady` may never fire.
        // We want the prompt to appear regardless of billing state, so
        // it ALSO surfaces after a short delay — whichever happens
        // first. If billing later confirms the user has already tipped,
        // the dialog was a no-op for this launch anyway because
        // `dialogShownThisLaunch` is set.
        fun maybeShowTipDialog() {
            if (dialogShownThisLaunch || isFinishing) return
            if (::billing.isInitialized && billing.hasEverTipped) return
            dialogShownThisLaunch = true
            TipDialog.show(this, billing) {
                webView.loadUrl("file:///android_asset/help.html")
            }
        }
        billing = BillingManager(this) { maybeShowTipDialog() }
        billing.start()
        // Fallback so the prompt always appears on a fresh install,
        // even if Play Billing never connects.
        webView.postDelayed({ maybeShowTipDialog() }, 1_500L)
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
