package cloud.mindbox.mobile_sdk.inapp.webview

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import cloud.mindbox.mobile_sdk.annotations.InternalMindboxApi
import java.util.concurrent.atomic.AtomicInteger

/**
 * Platform actuator for the in-app WebView prewarm: owns one hidden [WebView] that
 * loads the preconnect page and then the config's content page, so the shared
 * Chromium network stack warms its connections and HTTP cache for the real show.
 *
 * The instance is never shown or reused for a show (Android shares the renderer
 * process, so instance reuse buys nothing) — [release] destroys it as soon as a
 * real show starts, the config proves there is nothing to warm, or the prewarm
 * has settled. All WebView work is posted to the main looper.
 */
@InternalMindboxApi
public class InAppWebViewPrewarmEngine(
    private val appContext: Context,
    private val log: (String) -> Unit = {},
    private val isCacheEnabled: () -> Boolean
) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var webView: WebView? = null

    // Main-thread confined (written in posted blocks / client callbacks on the main looper).
    // The last content page is retained so a poisoned-cache HTTP error can be answered by
    // reloading the exact same page with the cache bypassed; one retry per content load.
    // Cleared in [release] — the engine is process-lived, so a settled prewarm must not
    // keep the page HTML reachable for the rest of the process.
    private var lastContentPage: ContentPage? = null
    private var hasRetriedWithoutCache = false

    /**
     * Invoked (on the main looper) when the no-cache recovery reload actually starts —
     * lets the owner restart its settle budget so the healing load is not cut short by
     * a budget the original load already spent.
     */
    public var onNoCacheRetryStarted: (() -> Unit)? = null

    private data class ContentPage(
        val html: String,
        val baseUrl: String,
        val generation: Int
    )

    // Set synchronously in [abort] — BEFORE the release is posted — so a load block that was
    // already posted from a background thread can never resurrect the WebView after a real
    // show has taken the network over.
    @Volatile
    private var isAborted = false

    // Bumped by every [release] (including via [abort]). release() alone doesn't set
    // isAborted, so it isn't a terminal gate on its own — a loadPreconnectPage/loadContentPage
    // call already posted (or racing concurrently) before a release could otherwise still run
    // ensureWebView() afterwards and resurrect a WebView nothing will ever clean up again.
    // Each load captures the generation at call time and ensureWebView() rejects it if a
    // release has bumped the counter since, however this task got interleaved with that release.
    private val generation = AtomicInteger(0)

    /** Loads the preconnect page under [baseUrl] (the show's cache partition). */
    public fun loadPreconnectPage(html: String, baseUrl: String, userAgentSuffix: String?) {
        val requestedGeneration = generation.get()
        mainHandler.post {
            runCatching {
                val view = ensureWebView(userAgentSuffix, requestedGeneration) ?: return@post
                view.loadDataWithBaseURL(baseUrl, html, "text/html", "UTF-8", null)
                log("preconnect page loaded under $baseUrl")
            }.onFailure { error -> log("preconnect page failed: $error") }
        }
    }

    /**
     * Loads the real content page under [baseUrl] (which carries the official prewarm
     * params): a runtime that knows the contract boots tracker-only and pulls byendpoint
     * into the shared HTTP cache; an older runtime ignores the params and the load
     * degrades to a plain page warm. Nothing reaches the SDK either way.
     *
     * When the cache feature is on ([isCacheEnabled] — the same latched decision this
     * WebView's cache mode was created with), a 4xx/5xx on a script subresource (a cached
     * error response poisoning the page) triggers ONE reload of the same page with the
     * cache bypassed — the fresh responses overwrite the poisoned entries, healing the
     * cache before any real show needs it. With the cache off nothing can be poisoned and
     * a reload would repeat the exact failed request, so the page is not retained at all.
     */
    public fun loadContentPage(
        html: String,
        baseUrl: String,
        userAgentSuffix: String?
    ) {
        val requestedGeneration = generation.get()
        mainHandler.post {
            runCatching {
                val view = ensureWebView(userAgentSuffix, requestedGeneration) ?: return@post
                lastContentPage = if (isCacheEnabled()) ContentPage(html, baseUrl, requestedGeneration) else null
                hasRetriedWithoutCache = false
                view.loadDataWithBaseURL(baseUrl, html, "text/html", "UTF-8", null)
                log("content page loaded under $baseUrl")
            }.onFailure { error -> log("content page failed: $error") }
        }
    }

    /**
     * One-shot recovery from a poisoned HTTP cache: reload the retained content page with
     * the cache bypassed. Runs on the main looper (WebViewClient callbacks land there).
     */
    private fun retryContentPageWithoutCache(failedUrl: String?, statusCode: Int) {
        if (hasRetriedWithoutCache || isAborted) return
        val view = webView ?: return
        val page = lastContentPage ?: return
        if (page.generation != generation.get()) return
        hasRetriedWithoutCache = true
        runCatching {
            view.settings.cacheMode = WebSettings.LOAD_NO_CACHE
            view.loadDataWithBaseURL(page.baseUrl, page.html, "text/html", "UTF-8", null)
            log("HTTP $statusCode for $failedUrl — reloading content page without cache")
            onNoCacheRetryStarted?.invoke()
        }.onFailure { error -> log("no-cache retry failed: $error") }
    }

    /**
     * Evaluates [js] on the prewarm WebView (main looper). Calls back with the raw
     * `evaluateJavascript` result, or null when there is no WebView (never created,
     * released, or aborted) — lets the owner poll page state, e.g. network idle.
     */
    public fun evaluateJavaScript(js: String, resultCallback: (String?) -> Unit) {
        mainHandler.post {
            val view = webView
            if (view == null || isAborted) {
                resultCallback(null)
                return@post
            }
            runCatching { view.evaluateJavascript(js) { result -> resultCallback(result) } }
                .onFailure { error ->
                    log("evaluateJavaScript failed: $error")
                    resultCallback(null)
                }
        }
    }

    /** Stops and destroys the prewarm WebView; a later prewarm may create a fresh one. */
    public fun release() {
        // Bumped synchronously, BEFORE the cleanup is posted — same reasoning as isAborted
        // in abort(): a load already posted (or racing this call) must see the new generation
        // by the time it runs, however the two tasks end up interleaved on the main looper.
        generation.incrementAndGet()
        mainHandler.post {
            lastContentPage = null
            hasRetriedWithoutCache = false
            val view = webView ?: return@post
            webView = null
            runCatching {
                view.stopLoading()
                view.loadUrl("about:blank")
                view.clearHistory()
                view.removeAllViews()
            }.onFailure { error -> log("release cleanup failed: $error") }
            // Destroy separately: a failure in the cosmetic cleanup above must never leak
            // the renderer by skipping the one call that actually frees it.
            runCatching {
                view.destroy()
                log("prewarm WebView released")
            }.onFailure { error -> log("release failed: $error") }
        }
    }

    /**
     * Terminal [release]: additionally refuses every future page load. Call when a real
     * show starts — from that point the prewarm must never touch the network again.
     */
    public fun abort() {
        isAborted = true
        release()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun ensureWebView(userAgentSuffix: String?, requestedGeneration: Int): WebView? {
        if (isAborted || requestedGeneration != generation.get()) return null
        webView?.let { return it }
        return runCatching {
            WebView(appContext).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                val cacheEnabled = isCacheEnabled()
                settings.cacheMode = webViewCacheMode(cacheEnabled)
                log("cache ${if (cacheEnabled) "ON" else "OFF"} (cacheMode=${settings.cacheMode})")
                if (!userAgentSuffix.isNullOrBlank()) {
                    val currentUserAgent: String = settings.userAgentString ?: ""
                    if (!currentUserAgent.contains(userAgentSuffix)) {
                        settings.userAgentString = "$currentUserAgent $userAgentSuffix".trim()
                    }
                }
                webViewClient = pinnedNavigationClient
            }
        }.onFailure { error -> log("WebView creation failed: $error") }
            .getOrNull()
            ?.also { created -> webView = created }
    }

    /**
     * Pins the hidden WebView to the documents the SDK loads itself: any page-initiated
     * top-frame navigation (JS redirect, meta refresh, a legacy runtime navigating away)
     * is refused. Without a client, chromium routes such navigations to an external
     * browsing intent — a hidden prewarm must never be able to pop the user's browser.
     */
    private val pinnedNavigationClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            log("blocked prewarm navigation to ${request?.url}")
            return true
        }

        @Deprecated("Deprecated in Java")
        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
            log("blocked prewarm navigation to $url")
            return true
        }

        override fun onReceivedHttpError(
            view: WebView?,
            request: WebResourceRequest?,
            errorResponse: WebResourceResponse?
        ) {
            val statusCode = errorResponse?.statusCode ?: return
            val url = request?.url?.toString()
            if (!isRecoverableScriptHttpError(url, statusCode)) return
            log("HTTP $statusCode for script $url during prewarm")
            retryContentPageWithoutCache(failedUrl = url, statusCode = statusCode)
        }
    }
}
