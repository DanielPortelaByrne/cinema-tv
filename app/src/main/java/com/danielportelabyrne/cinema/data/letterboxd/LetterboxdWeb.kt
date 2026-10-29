package com.danielportelabyrne.cinema.data.letterboxd

import com.danielportelabyrne.cinema.data.exchange
import android.annotation.SuppressLint
import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private const val TAG = "Letterboxd"

/**
 * Reads public Letterboxd pages. Letterboxd has no open API, and some of its pages (profiles, likes, diaries, reviews)
 * sit behind a browser check that a plain request never passes. So: a plain request first, with the same identity and
 * cookies as the app's own WebView; when that meets the check, the page is loaded in a hidden WebView, which passes it
 * like any browser, and its HTML is read back. The check's cookie then lets plain requests through for a while.
 *
 * Blocking: call off the main thread. Null when the page cannot be had.
 */
class LetterboxdWeb(private val activity: Activity, private val allowBrowser: Boolean = true) {
    private val main = Handler(Looper.getMainLooper())
    private val userAgent: String by lazy { WebSettings.getDefaultUserAgent(activity) }
    private val browserLock = Any()
    @Volatile private var webView: WebView? = null

    /**
     * [path] is a site path ("/someone/likes/films/") or a full Letterboxd address. [cancelled] says the page is no longer
     * wanted (a search typed past): it is then not started, or a browser load of it is stopped, so the next page gets the browser.
     */
    fun page(path: String, cancelled: () -> Boolean = { false }): String? {
        val url = if (path.startsWith("http")) path else BASE + path
        plain(url)?.let { return it }
        if (!allowBrowser) return null
        return synchronized(browserLock) { if (cancelled()) null else plain(url) ?: viaBrowser(url, cancelled) }
    }

    /**
     * A small JSON answer (a poster's address, say). The browser check guards it as it guards pages, so it falls back to
     * the browser, which shows the JSON as text inside the page: the caller picks the value out of it.
     */
    fun json(path: String): String? {
        val url = if (path.startsWith("http")) path else BASE + path
        plain(url, acceptJson = true)?.let { return it }
        if (!allowBrowser) return null
        return synchronized(browserLock) { viaBrowser(url) }
    }

    /** Refusals in a row by the browser check. Once they pile up (every page on the TV, say), plain requests stop being tried. */
    @Volatile private var refusals = 0

    private fun plain(url: String, acceptJson: Boolean = false): String? {
        if (refusals >= PLAIN_REFUSALS) return null
        return try {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 10_000; c.readTimeout = 15_000; c.instanceFollowRedirects = true
            c.setRequestProperty("User-Agent", userAgent)
            c.setRequestProperty("Accept", if (acceptJson) "application/json" else "text/html")
            c.setRequestProperty("Accept-Language", "en-GB,en;q=0.9")
            CookieManager.getInstance().getCookie(BASE)?.let { c.setRequestProperty("Cookie", it) }
            c.exchange {
                when (c.responseCode) {
                    200 -> c.inputStream.bufferedReader().use { it.readText() }.let { body ->
                        if (isChallenge(body)) { refusals++; null } else { refusals = 0; body }
                    }
                    403, 503 -> { refusals++; null }
                    else -> null
                }
            }
        } catch (e: Exception) { Log.w(TAG, "plain ${url.substringAfter(BASE)}: ${e.javaClass.simpleName}"); null }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun viaBrowser(url: String, cancelled: () -> Boolean = { false }): String? {
        val done = CountDownLatch(1)
        var html: String? = null
        val started = System.currentTimeMillis()
        main.post {
            if (activity.isDestroyed) { done.countDown(); return@post }
            val view = webView ?: WebView(activity).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadsImagesAutomatically = false
                settings.blockNetworkImage = true
                settings.userAgentString = userAgent
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                isFocusable = false; isFocusableInTouchMode = false; alpha = 0.01f
                // A WebView only runs its scripts properly while attached; a 1-pixel one in a corner is never seen.
                (activity.window.decorView as ViewGroup).addView(this, ViewGroup.LayoutParams(1, 1))
            }.also { webView = it }
            fun poll(attempt: Int) {
                // Ready once the browser check has gone: no "Just a moment" title, none of its widgets, and something on the
                // page (a whole page, or a small fragment such as the ratings chart).
                view.evaluateJavascript("(function(){var t=document.title||'';var c=document.querySelector('#challenge-running,#challenge-form,.cf-turnstile,#cf-wrapper,#challenge-stage');" +
                    "return (t.indexOf('Just a moment')<0 && !c && document.body && document.body.innerHTML.length>40)?'1':'0'})()") { raw ->
                    val ready = raw?.trim('"') == "1"
                    if (ready) {
                        view.evaluateJavascript(READ_PAGE_JS) { out ->
                            html = decodeJsString(out); done.countDown()
                        }
                    } else if (attempt < 80) main.postDelayed({ poll(attempt + 1) }, 250)
                    else done.countDown()
                }
            }
            // A page nobody wants any more stops loading at once, so the browser is free for the next one.
            fun watchCancel() {
                if (done.count == 0L) return
                if (cancelled()) { view.stopLoading(); done.countDown() } else main.postDelayed({ watchCancel() }, 250)
            }
            view.webViewClient = object : WebViewClient() {
                override fun onPageFinished(v: WebView, u: String) { poll(0) }
                // The consent dialog's vendor list is refused: it adds a megabyte to each page, and the page still renders without it.
                override fun shouldInterceptRequest(v: WebView, request: WebResourceRequest): WebResourceResponse? {
                    if (request.isForMainFrame || !refusedHost(request.url.host)) return null
                    return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                }
            }
            watchCancel()
            view.loadUrl(url)
        }
        done.await(25, TimeUnit.SECONDS)
        done.countDown()   // ends the cancel watch, if the wait ran out first
        main.post { CookieManager.getInstance().flush(); webView?.stopLoading() }
        Log.i(TAG, "browser ${url.substringAfter(BASE)}: ${if (html != null) "${html!!.length} chars" else "failed"} in ${System.currentTimeMillis() - started} ms")
        return html?.takeUnless { isChallenge(it) }
    }

    fun close() { main.post { webView?.let { (it.parent as? ViewGroup)?.removeView(it); it.destroy() }; webView = null } }

    companion object {
        const val BASE = "https://letterboxd.com"
        /** Browser-check refusals in a row after which plain requests stop being tried. */
        const val PLAIN_REFUSALS = 3

        fun isChallenge(html: String) = html.length < 20_000 && (html.contains("Just a moment") || html.contains("challenges.cloudflare.com"))

        /** Hosts whose requests a page is refused: the consent dialog's vendor list, which alone adds a megabyte to a page. */
        private val REFUSED_HOSTS = listOf("fundingchoicesmessages.google.com")
        fun refusedHost(host: String?) = host != null && REFUSED_HOSTS.any { host == it || host.endsWith(".$it") }

        /** The page's HTML with the consent dialog taken out: the page is read here, never shown. */
        private const val READ_PAGE_JS = "(function(){var c=document.getElementById('fc-consent-root');if(c&&c.parentNode)c.parentNode.removeChild(c);return document.documentElement.outerHTML;})()"

        /** evaluateJavascript hands back a JSON string literal; this turns it into the text it holds. */
        fun decodeJsString(raw: String?): String? {
            if (raw == null || raw == "null" || raw.length < 2) return null
            return try { org.json.JSONArray("[$raw]").getString(0) } catch (_: Exception) { null }
        }
    }
}
