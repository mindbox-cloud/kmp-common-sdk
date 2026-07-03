package cloud.mindbox.mobile_sdk.inapp.webview

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebSettings
import android.webkit.WebView
import cloud.mindbox.mobile_sdk.annotations.InternalMindboxApi

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
    private val log: (String) -> Unit = {}
) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var webView: WebView? = null

    // Set synchronously in [abort] — BEFORE the release is posted — so a load block that was
    // already posted from a background thread can never resurrect the WebView after a real
    // show has taken the network over.
    @Volatile
    private var isAborted = false

    /** Loads the preconnect page under [baseUrl] (the show's cache partition). */
    public fun loadPreconnectPage(html: String, baseUrl: String, userAgentSuffix: String?) {
        mainHandler.post {
            runCatching {
                val view = ensureWebView(userAgentSuffix) ?: return@post
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
     */
    public fun loadContentPage(html: String, baseUrl: String, userAgentSuffix: String?) {
        mainHandler.post {
            runCatching {
                val view = ensureWebView(userAgentSuffix) ?: return@post
                view.loadDataWithBaseURL(baseUrl, html, "text/html", "UTF-8", null)
                log("content page loaded under $baseUrl")
            }.onFailure { error -> log("content page failed: $error") }
        }
    }

    /** Stops and destroys the prewarm WebView; a later prewarm may create a fresh one. */
    public fun release() {
        mainHandler.post {
            val view = webView ?: return@post
            webView = null
            runCatching {
                view.stopLoading()
                view.loadUrl("about:blank")
                view.clearHistory()
                view.removeAllViews()
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
    private fun ensureWebView(userAgentSuffix: String?): WebView? {
        if (isAborted) return null
        webView?.let { return it }
        return runCatching {
            WebView(appContext).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                if (!userAgentSuffix.isNullOrBlank()) {
                    val currentUserAgent: String = settings.userAgentString ?: ""
                    if (!currentUserAgent.contains(userAgentSuffix)) {
                        settings.userAgentString = "$currentUserAgent $userAgentSuffix".trim()
                    }
                }
            }
        }.onFailure { error -> log("WebView creation failed: $error") }
            .getOrNull()
            ?.also { created -> webView = created }
    }

}
