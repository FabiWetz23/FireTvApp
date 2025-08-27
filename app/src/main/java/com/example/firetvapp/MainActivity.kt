package com.example.firetvapp

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
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
import androidx.annotation.RequiresApi
import okhttp3.Cache
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private lateinit var root: FrameLayout
    private lateinit var progress: ProgressBar
    private lateinit var cursor: ImageView

    companion object {
        private const val START_URL = "https://xprime.tv/"
        private const val CACHE_SIZE_BYTES = 20L * 1024L * 1024L // 20 MB
        private val NO_OP_CHROME_CLIENT = object : WebChromeClient() {}
        private val MOBILE_UA: String =
            "Mozilla/5.0 (Linux; Android 10; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Chrome/114.0.0.0 Mobile Safari/537.36"
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        root = FrameLayout(this)

        progress = ProgressBar(this).apply {
            isIndeterminate = true
            visibility = View.VISIBLE
        }
        val progressLp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.CENTER }

        webView = buildWebView().also { it.loadUrl(START_URL) }

        // (Optional) Cursor-Overlay deaktiviert, um RAM/Overdraw zu sparen
        cursor = ImageView(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            setImageDrawable(null)
            layoutParams = FrameLayout.LayoutParams(50, 50)
            visibility = View.GONE
        }

        root.addView(
            webView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        root.addView(progress, progressLp)
        root.addView(cursor)

        setContentView(root)

        // Back immer abfangen: nie schließen, sondern zur Startseite
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleTvBack()
            }
        })
    }

    /** Einheitliche Back-Logik für TV-Remotes */
    /** Smarte Back-Logik: zuerst In-Page schließen, dann Verlauf, zuletzt Startseite */
    private fun handleTvBack(): Boolean {
        if (!::webView.isInitialized) return true

        // 1) Versuche zuerst, in der Seite Overlays/Suche zu schließen (JS)
        val js = """
        (function(){
          // Wenn ein Eingabefeld fokussiert ist: blur() statt Seitenwechsel
          var ae = document.activeElement;
          if (ae && (ae.tagName === 'INPUT' || ae.tagName === 'TEXTAREA' || ae.isContentEditable)) {
            ae.blur();
            return 'blurred';
          }
          // Häufige Close-Buttons/Overlays
          var close = document.querySelector('[data-close],[data-dismiss],.modal [data-action="close"],.modal .close,[aria-label="Close"],[aria-label="Schließen"],.search-overlay .close');
          if (close) { close.click(); return 'closed'; }

          // Falls eine Such-UI offen ist (Beispiele): versuche sie zu schließen
          var so = document.querySelector('.search-overlay.open, .search--open, body.search-open');
          if (so) { 
            // Sende ESC in-Page
            var evt = new KeyboardEvent('keydown',{key:'Escape',keyCode:27,which:27,bubbles:true});
            document.dispatchEvent(evt);
            return 'closed';
          }
          return 'none';
        })();
    """.trimIndent()

        webView.evaluateJavascript(js) { result ->
            // Wenn wir 'blurred' oder 'closed' zurückbekommen: Event ist verbraucht
            if (result?.contains("blurred") == true || result?.contains("closed") == true) {
                return@evaluateJavascript
            } else {
                // 2) Danach normal im Verlauf zurück
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    // 3) Letzte Stufe: zur Startseite (App nie schließen)
                    val current = webView.url ?: ""
                    if (!current.equals(START_URL, ignoreCase = true)) {
                        webView.loadUrl(START_URL)
                    }
                }
            }
        }
        return true
    }

    @RequiresApi(Build.VERSION_CODES.M)
    @SuppressLint("SetJavaScriptEnabled")
    private fun buildWebView(): WebView {
        return WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            overScrollMode = View.OVER_SCROLL_NEVER

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false

                // Mobile Rendering
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportZoom(false)

                // RAM-schonend
                offscreenPreRaster = false
                cacheMode = WebSettings.LOAD_DEFAULT
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW

                // First Paint schneller: Bilder erst nach Sichtbarkeit
                loadsImagesAutomatically = false
                blockNetworkImage = true

                defaultTextEncodingName = "utf-8"
                setNeedInitialFocus(false)

                // Smartphone-Ansicht erzwingen
                userAgentString = MOBILE_UA
            }

            // Cookies korrekt setzen
            val cm = CookieManager.getInstance()
            cm.setAcceptCookie(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                cm.setAcceptThirdPartyCookies(this, false)
            }

            val okHttp = okHttpClientWithCache()

            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    request ?: return null
                    if (request.method.uppercase() != "GET") return null

                    return runCatching {
                        val hb = Headers.Builder()
                        for ((k, v) in request.requestHeaders) if (k.isNotBlank()) hb.add(k, v)
                        hb.set("User-Agent", MOBILE_UA)

                        val req = Request.Builder()
                            .url(request.url.toString())
                            .headers(hb.build())
                            .get()
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
                    // erster Frame sichtbar -> Progress weg, Bilder freigeben
                    this@MainActivity.runOnUiThread {
                        this@MainActivity.progress.visibility = View.GONE
                        view?.settings?.apply {
                            blockNetworkImage = false
                            loadsImagesAutomatically = true
                        }
                    }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    // Wenn die Startseite geladen wurde und wir von "Back" kamen:
                    if (url != null && url.equals(START_URL, ignoreCase = true)) {
                        // History leeren, damit ein weiterer „Zurück“ nicht schließt
                        view?.clearHistory()
                    }
                }
            }

            webChromeClient = NO_OP_CHROME_CLIENT
        }
    }

    private fun okHttpClientWithCache(): OkHttpClient {
        val dir = File(cacheDir, "http_webview_cache").apply { mkdirs() }
        val cache = Cache(dir, CACHE_SIZE_BYTES)
        return OkHttpClient.Builder()
            .cache(cache)
            .addNetworkInterceptor { chain ->
                val req = chain.request()
                val res = chain.proceed(req)
                val url = req.url.toString().lowercase()
                val isStatic = url.endsWith(".js") || url.endsWith(".css") ||
                        url.endsWith(".png") || url.endsWith(".jpg") || url.endsWith(".jpeg") ||
                        url.endsWith(".webp") || url.endsWith(".svg") || url.endsWith(".gif") ||
                        url.endsWith(".woff") || url.endsWith(".woff2")
                if (isStatic) {
                    res.newBuilder()
                        .header("Cache-Control", "public, max-age=31536000, immutable")
                        .build()
                } else res
            }
            .build()
    }

    // Physischer Zurück-Button der TV-Remote abfangen (inkl. ESC als Fallback)
    @SuppressLint("GestureBackNavigation")
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {

            // Nur echte Zurück-Taste; ESC NICHT als Back, damit Suche/Keyboard nicht abgewürgt werden
            KeyEvent.KEYCODE_BACK -> {
                handleTvBack()
                true
            }

            // Menü-Button → Klick auf das Such-Icon ausführen
            KeyEvent.KEYCODE_MENU -> {
                if (::webView.isInitialized) {
                    val js = """
                    (function () {
                      function doClick(el) {
                        if (!el) return false;
                        try { el.click(); return true; } catch(e){}
                        try {
                          el.dispatchEvent(new MouseEvent('click', {bubbles:true, cancelable:true}));
                          return true;
                        } catch(e){}
                        return false;
                      }
                      var icon = document.querySelector('svg.lucide-search, svg.lucide-icon.lucide.lucide-search');
                      var clickable = icon ? icon.closest('button, a, [role="button"], [tabindex], .clickable') : null;
                      if (clickable && doClick(clickable)) return true;
                      if (icon && doClick(icon)) return true;
                      return false;
                    })();
                """.trimIndent()
                    webView.evaluateJavascript(js, null)
                }
                true
            }

            else -> super.onKeyDown(keyCode, event)
        }
    }
}
