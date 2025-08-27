package com.example.firetvapp

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private lateinit var cursor: ImageView
    private lateinit var rootLayout: FrameLayout

    private var cursorX = 500f
    private var cursorY = 500f
    private val step = 50 // Bewegungsschritt in Pixeln

    companion object {
        private const val START_URL = "https://xprime.tv/"
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Root FrameLayout erstellen
        rootLayout = FrameLayout(this)

        // WebView
        webView = WebView(this).apply {
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

        // Cursor
        cursor = ImageView(this).apply {
            setImageResource(R.drawable.cursor)   // <-- hier dein Bildname ohne .png/.jpg
            layoutParams = FrameLayout.LayoutParams(50, 50)
            x = cursorX
            y = cursorY
        }


        // Views hinzufügen
        rootLayout.addView(webView)
        rootLayout.addView(cursor)

        setContentView(rootLayout)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                cursorY -= step
                cursor.y = cursorY
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                cursorY += step
                cursor.y = cursorY
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                cursorX -= step
                cursor.x = cursorX
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                cursorX += step
                cursor.x = cursorX
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                // Klick-Ereignis ins WebView senden
                val downTime = System.currentTimeMillis()
                val motionDown = MotionEvent.obtain(
                    downTime, downTime,
                    MotionEvent.ACTION_DOWN, cursorX, cursorY, 0
                )
                val motionUp = MotionEvent.obtain(
                    downTime, downTime + 100,
                    MotionEvent.ACTION_UP, cursorX, cursorY, 0
                )
                webView.dispatchTouchEvent(motionDown)
                webView.dispatchTouchEvent(motionUp)
                motionDown.recycle()
                motionUp.recycle()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }
}
