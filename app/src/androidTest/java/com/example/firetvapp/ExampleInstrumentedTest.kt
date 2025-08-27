package com.example.firetvapp

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.KeyEvent
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.viewinterop.AndroidView

class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView

    companion object {
        private const val START_URL = "https://xprime.tv/"
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            WebViewScreen()
        }
    }

    @Composable
    fun WebViewScreen() {
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    webView = this
                    isFocusable = true
                    isFocusableInTouchMode = true
                    requestFocus()

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        setSupportZoom(true)
                        builtInZoomControls = false
                        displayZoomControls = false
                        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        userAgentString = "$userAgentString FireTVApp"
                    }

                    webViewClient = WebViewClient()
                    webChromeClient = WebChromeClient()
                    loadUrl(START_URL)
                }
            }
        )
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // Navigation mit FireTV Fernbedienung
        if (::webView.isInitialized) {
            when (keyCode) {
                KeyEvent.KEYCODE_BACK -> {
                    if (webView.canGoBack()) {
                        webView.goBack()
                        return true
                    }
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    webView.pageDown(false); return true
                }
                KeyEvent.KEYCODE_DPAD_UP -> {
                    webView.pageUp(false); return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    webView.scrollBy(-200, 0); return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    webView.scrollBy(200, 0); return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }
}
