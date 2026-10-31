package com.danielportelabyrne.cinema.data.letterboxd

import com.danielportelabyrne.cinema.data.exchange
import com.danielportelabyrne.cinema.ui.Perf
import android.annotation.SuppressLint
import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

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
        return locked(url) { if (cancelled()) null else plain(url) ?: viaBrowser(url, cancelled) }
    }

    /**
     * A small JSON answer (a poster's address, say). The browser check guards it as it guards pages, so it falls back to
     * the browser, which shows the JSON as text inside the page: the caller picks the value out of it.
     */
    fun json(path: String): String? {
        val url = if (path.startsWith("http")) path else BASE + path
        plain(url, acceptJson = true)?.let { return it }
        if (!allowBrowser) return null
        return locked(url) { inPage(path) ?: viaBrowser(url) }
    }

    /** True once the hidden browser has loaded a Letterboxd page, so a request from its script is same-origin and carries its clearance. */
    @Volatile private var onLetterboxd = false

    /**
     * Asks the page the browser is already on for [path], from inside it: no navigation, so a small JSON answer costs one
     * request rather than a page load. Null when the page is not Letterboxd's, or the request fails, and then the caller loads
     * the address in the browser as before.
     */
    private fun inPage(path: String): String? {
        val view = webView ?: return null
        if (!onLetterboxd) return null
        val token = System.nanoTime().toString()
        val body = AtomicReference<String?>()
        val done = CountDownLatch(1)
        pageSink = { t, text -> if (t == token) { body.set(text); done.countDown() } }
        main.post {
            view.evaluateJavascript("fetch(${JSONObject.quote(path)},{credentials:'same-origin',headers:{'Accept':'application/json'}})" +
                ".then(function(r){return r.ok?r.text():''}).then(function(t){cinemaPage.receive('$token',t)})" +
                ".catch(function(){cinemaPage.receive('$token','')})", null)
        }
        done.await(20, TimeUnit.SECONDS)
        Perf.mark("lb in-page ${path} ${if (body.get().isNullOrEmpty()) "failed" else "ok"}")
        return body.get()?.takeIf { it.isNotEmpty() }
    }

    /** Runs [block] with the browser to itself. The wait for it is logged (debug builds), apart from the browser's own time. */
    private fun <T> locked(url: String, block: () -> T): T {
        val waitStart = Perf.now()
        return synchronized(browserLock) {
            Perf.took("lb browser wait ${url.substringAfter(BASE)}", Perf.now() - waitStart)
            block()
        }
    }

    private fun plain(url: String, acceptJson: Boolean = false): String? {
        val path = url.substringAfter(BASE)
        if (!gate.allow()) { Perf.mark("lb plain $path skipped refusals=${gate.refusalsInARow()}"); return null }
        val started = Perf.now()
        var outcome = "error"
        return try {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 10_000; c.readTimeout = 15_000; c.instanceFollowRedirects = true
            c.setRequestProperty("User-Agent", userAgent)
            c.setRequestProperty("Accept", if (acceptJson) "application/json" else "text/html")
            c.setRequestProperty("Accept-Language", "en-GB,en;q=0.9")
            CookieManager.getInstance().getCookie(BASE)?.let { c.setRequestProperty("Cookie", it) }
            c.exchange {
                outcome = c.responseCode.toString()
                when (c.responseCode) {
                    200 -> c.inputStream.bufferedReader().use { it.readText() }.let { body ->
                        if (isChallenge(body)) { gate.refusal(); outcome = "challenge"; null } else { gate.success(); body }
                    }
                    403, 503 -> { gate.refusal(); null }
                    else -> null
                }
            }
        } catch (e: Exception) { outcome = e.javaClass.simpleName; Log.w(TAG, "plain $path: ${e.javaClass.simpleName}"); null }
        finally { Perf.took("lb plain $path $outcome refusals=${gate.refusalsInARow()}", Perf.now() - started) }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun viaBrowser(url: String, cancelled: () -> Boolean = { false }): String? {
        val done = CountDownLatch(1)
        // The page's HTML, as the browser's script hands it over through the bridge. The token ties it to this load, so a page
        // that arrives late from an earlier load (one that timed out) is not taken for this one.
        val token = System.nanoTime().toString()
        val page = AtomicReference<String?>()
        pageSink = { t, html -> if (t == token) { page.set(html); done.countDown() } }
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
                addJavascriptInterface(PageBridge(), "cinemaPage")
                (activity.window.decorView as ViewGroup).addView(this, ViewGroup.LayoutParams(1, 1))
            }.also { webView = it }
            fun poll(attempt: Int) {
                // Ready once the browser check has gone: no "Just a moment" title, none of its widgets, and something on the
                // page (a whole page, or a small fragment such as the ratings chart).
                view.evaluateJavascript("(function(){var t=document.title||'';var c=document.querySelector('#challenge-running,#challenge-form,.cf-turnstile,#cf-wrapper,#challenge-stage');" +
                    "return (t.indexOf('Just a moment')<0 && !c && document.body && document.body.innerHTML.length>40)?'1':'0'})()") { raw ->
                    val ready = raw?.trim('"') == "1"
                    if (ready) {
                        view.evaluateJavascript(readPageJs(token), null)
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
                // Also refused: anything from another host (ads, analytics, widgets) and the stylesheets, fonts and pictures a page
                // draws with. The HTML and the browser check's scripts (from Letterboxd and Cloudflare) are still loaded.
                override fun shouldInterceptRequest(v: WebView, request: WebResourceRequest): WebResourceResponse? {
                    if (request.isForMainFrame || !(refusedHost(request.url.host) || LetterboxdPages.notNeededByBrowser(request.url.host, request.url.path.orEmpty()))) return null
                    return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                }
            }
            watchCancel()
            view.loadUrl(url)
        }
        done.await(25, TimeUnit.SECONDS)
        done.countDown()   // ends the cancel watch, if the wait ran out first
        main.post { CookieManager.getInstance().flush(); webView?.stopLoading() }
        val html = page.get()
        // The browser now sits on this page, or on nothing usable: in-page requests are only made from a loaded Letterboxd page.
        onLetterboxd = html != null
        Log.i(TAG, "browser ${url.substringAfter(BASE)}: ${if (html != null) "${html.length} chars" else "failed"} in ${System.currentTimeMillis() - started} ms")
        return html?.takeUnless { isChallenge(it) }
    }

    /** Receives a loaded page's HTML from the script (see [readPageJs]). Called on a WebView thread, not the main one. */
    private var pageSink: ((String, String) -> Unit)? = null

    private inner class PageBridge {
        @JavascriptInterface
        fun receive(token: String, html: String) { pageSink?.invoke(token, html) }
    }

    fun close() { main.post { webView?.let { (it.parent as? ViewGroup)?.removeView(it); it.destroy() }; webView = null } }

    companion object {
        /**
         * Shared by every instance: the browser check is the same for all of them. Once closed, a probe is let through
         * every minute (see [PlainGate]); a browser load does not reopen it, so plain requests are not spent on every page.
         */
        private val gate = PlainGate()

        const val BASE = "https://letterboxd.com"
        fun isChallenge(html: String) = html.length < 20_000 && (html.contains("Just a moment") || html.contains("challenges.cloudflare.com"))

        /** Hosts whose requests a page is refused: the consent dialog's vendor list, which alone adds a megabyte to a page. */
        private val REFUSED_HOSTS = listOf("fundingchoicesmessages.google.com")
        fun refusedHost(host: String?) = host != null && REFUSED_HOSTS.any { host == it || host.endsWith(".$it") }


        /**
         * The page's HTML with the consent dialog taken out, handed to [PageBridge] under [token]: the page is read here, never
         * shown. Passing the HTML through the bridge avoids the JSON string literal that evaluateJavascript would return.
         */
        fun readPageJs(token: String) = "(function(){var c=document.getElementById('fc-consent-root');if(c&&c.parentNode)c.parentNode.removeChild(c);" +
            "cinemaPage.receive('$token',document.documentElement.outerHTML);})()"
    }
}
