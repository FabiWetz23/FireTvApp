package com.example.firetvapp

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.Gravity
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Cache
import okhttp3.Request
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private lateinit var cursor: ImageView
    private lateinit var rootLayout: FrameLayout
    private lateinit var progress: ProgressBar

    companion object {
        private const val START_URL = "https://xprime.tv/"
        private const val CACHE_SIZE_BYTES = 100L * 1024L * 1024L // 100 MB
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Root
        rootLayout = FrameLayout(this)

        // Progress-Overlay (indeterminate)
        progress = ProgressBar(this).apply {
            isIndeterminate = true
            visibility = View.VISIBLE
        }
        val progressLp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.CENTER }

        // WebView
        webView = buildWebView()
        webView.loadUrl(START_URL)

        // Cursor-Overlay (optional)
        cursor = ImageView(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            setImageDrawable(null) // Falls du ein Icon hast: setImageResource(R.drawable.cursor)
            layoutParams = FrameLayout.LayoutParams(50, 50)
            x = 500f; y = 500f
            visibility = View.GONE
        }

        // Hierarchie
        rootLayout.addView(
            webView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        rootLayout.addView(progress, progressLp)
        rootLayout.addView(cursor)

        setContentView(rootLayout)

        // Back-Handling: zuerst WebView-Verlauf, dann App schließen
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (::webView.isInitialized && webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun buildWebView(): WebView {
        return WebView(this).apply {
            setBackgroundColor(Color.BLACK)

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false

                // TV-optimiert
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportZoom(false)

                // Besserer First Paint; mehr RAM, aber auf Fire TV ok
                offscreenPreRaster = true

                // HTTP Caching (wirkt mit OkHttp + shouldInterceptRequest)
                cacheMode = WebSettings.LOAD_DEFAULT

                // Mixed Content – falls alles HTTPS: NEVER_ALLOW
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

                // Bilder zunächst zurückhalten -> schnellere erste Darstellung
                loadsImagesAutomatically = false

                // UA leicht anpassen
                val ua = userAgentString ?: ""
                userAgentString = ua.replace("TV", "") + " FireTVApp/1.0"
            }

            // OkHttp-Client mit Disk-Cache
            val okHttp = okHttpClientWithCache()

            webViewClient = object : WebViewClient() {

                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    request ?: return null
                    return runCatching {
                        // ---- FIX 1: KEIN deprecated Headers.of(map) ----
                        val headersBuilder = Headers.Builder()
                        for ((k, v) in request.requestHeaders) {
                            if (k.isNotBlank()) headersBuilder.add(k, v)
                        }
                        val req = Request.Builder()
                            .url(request.url.toString())
                            .headers(headersBuilder.build())
                            .method(request.method, null)
                            .build()

                        val resp = okHttp.newCall(req).execute()
                        val body = resp.body ?: return null

                        val contentType = resp.header("Content-Type") ?: "text/plain; charset=utf-8"
                        val mime = contentType.substringBefore(";").trim()
                        val encoding = contentType.substringAfter("charset=", "utf-8").trim()

                        WebResourceResponse(
                            mime,
                            encoding,
                            resp.code,
                            resp.message,
                            resp.headers.toMultimap().mapValues { it.value.joinToString(",") },
                            body.byteStream()
                        )
                    }.getOrNull()
                }

                override fun onPageCommitVisible(view: WebView?, url: String?) {
                    // Erster Frame sichtbar -> Progress aus
                    // ---- FIX 2: explizit auf progress zugreifen ----
                    this@MainActivity.runOnUiThread {
                        this@MainActivity.progress.visibility = View.GONE
                    }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    // Jetzt Bilder freigeben
                    view?.settings?.loadsImagesAutomatically = true
                }
            }

            webChromeClient = object : WebChromeClient() {}
        }
    }

    private fun okHttpClientWithCache(): OkHttpClient {
        val cacheDir = File(cacheDir, "http_webview_cache").apply { mkdirs() }
        val cache = Cache(cacheDir, CACHE_SIZE_BYTES)
        return OkHttpClient.Builder()
            .cache(cache)
            // Optional: aggressiver für statische Assets (falls Server-Header schwach sind)
            .addNetworkInterceptor { chain ->
                val request = chain.request()
                val response = chain.proceed(request)
                val url = request.url.toString().lowercase()
                val isStatic = url.endsWith(".js") || url.endsWith(".css")
                        || url.endsWith(".png") || url.endsWith(".jpg")
                        || url.endsWith(".jpeg") || url.endsWith(".webp")
                        || url.endsWith(".svg") || url.endsWith(".gif")
                        || url.endsWith(".woff") || url.endsWith(".woff2")
                if (isStatic) {
                    response.newBuilder()
                        .header("Cache-Control", "public, max-age=31536000, immutable")
                        .build()
                } else {
                    response
                }
            }
            .build()
    }

    // Back-Taste zusätzlich via KeyEvent (falls nötig)
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && ::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
