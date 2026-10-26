package cloud.mindbox.mobile_sdk.inapp.webview

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import cloud.mindbox.mobile_sdk.annotations.InternalMindboxApi

@InternalMindboxApi
public actual typealias WebViewPlatformView = View

@InternalMindboxApi
public fun WebViewController.Companion.create(
    context: android.content.Context,
    isDebugEnabled: Boolean,
    isCacheEnabled: Boolean,
    log: (String) -> Unit = {}
): WebViewController = AndroidWebViewController(context, isDebugEnabled, isCacheEnabled, log)

// Upper bound on waiting for a renderer that never answers an in-flight
// evaluateJavascript; the drained case destroys as soon as the last callback lands.
private const val DESTROY_DRAIN_TIMEOUT_MS = 1_000L

@OptIn(InternalMindboxApi::class)
private class AndroidWebViewController(
    context: android.content.Context,
    isDebugEnabled: Boolean,
    private val isCacheEnabled: Boolean,
    private val log: (String) -> Unit
) : WebViewController {

    private val webView: WebView = WebView(context)
    private var eventListener: WebViewEventListener? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    // All three main-thread confined: mutated only inside blocks running on [mainHandler]
    // (posts from background threads run their check on the main looper too). Callbacks,
    // not a count: a hung renderer never invokes its ValueCallback, so completeDestroy()
    // needs the actual callbacks to force-complete with null, not just how many are pending.
    private val pendingEvaluateCallbacks = mutableListOf<(String?) -> Unit>()
    private var isDestroyRequested = false
    private var isDestroyed = false

    init {
        WebView.setWebContentsDebuggingEnabled(isDebugEnabled)
        configureWebView()
        webView.webViewClient = createWebViewClient()
    }

    override val view: WebViewPlatformView
        get() = webView

    override fun loadContent(content: WebViewHtmlContent) {
        executeOnViewThread {
            webView.loadDataWithBaseURL(
                content.baseUrl,
                content.html,
                "text/html",
                "UTF-8",
                null
            )
        }
    }

    override fun setVisibility(isVisible: Boolean) {
        executeOnViewThread {
            webView.visibility = if (isVisible) View.VISIBLE else View.INVISIBLE
        }
    }

    override fun setUserAgentSuffix(suffix: String) {
        executeOnViewThread {
            val currentUserAgent: String = webView.settings.userAgentString ?: ""
            if (currentUserAgent.contains(suffix)) {
                return@executeOnViewThread
            }
            webView.settings.userAgentString = "$currentUserAgent $suffix".trim()
        }
    }

    override fun setJsBridge(bridge: WebViewJsBridge, bridgeName: String) {
        executeOnViewThread {
            webView.removeJavascriptInterface(bridgeName)
            webView.addJavascriptInterface(AndroidWebViewJsBridge(bridge), bridgeName)
        }
    }

    override fun setEventListener(listener: WebViewEventListener?) {
        eventListener = listener
    }

    override fun setCacheBypass(isBypassEnabled: Boolean) {
        executeOnViewThread {
            webView.settings.cacheMode =
                if (isBypassEnabled) WebSettings.LOAD_NO_CACHE else webViewCacheMode(isCacheEnabled)
            log("cache bypass ${if (isBypassEnabled) "ON" else "OFF"} (cacheMode=${webView.settings.cacheMode})")
        }
    }

    // NOT View.post: on a detached view (onClose removes the view from its parent before
    // calling destroy) View.post lands in the view's HandlerActionQueue, which drains only
    // on the next attach — queued work, including destroy() itself, would never run and
    // every closed in-app would leak a live WebView with its page JS still executing.
    override fun executeOnViewThread(action: () -> Unit) {
        mainHandler.post {
            if (isDestroyRequested) return@post
            action()
        }
    }

    override fun evaluateJavaScript(js: String, resultCallback: ((String?) -> Unit)?) {
        mainHandler.post {
            if (isDestroyRequested) {
                resultCallback?.invoke(null)
                return@post
            }
            if (resultCallback == null) {
                webView.evaluateJavascript(js, null)
            } else {
                pendingEvaluateCallbacks.add(resultCallback)
                webView.evaluateJavascript(js) { result ->
                    // remove() returns false if completeDestroy() already force-completed
                    // this one with null on the drain timeout — a late real answer from a
                    // renderer that was merely slow, not hung, must not fire it twice.
                    if (pendingEvaluateCallbacks.remove(resultCallback)) {
                        resultCallback(result)
                    }
                    completeDestroyIfDrained()
                }
            }
        }
    }

    /**
     * Destroy waits for in-flight [evaluateJavaScript] result callbacks by design: the
     * evaluate call executes before the destroy request (FIFO on the main looper), but its
     * result comes back from the renderer asynchronously — destroying at once would drop
     * it (concretely: the learned-hosts capture queued right before close). New work is
     * refused from here; the timeout only bounds a renderer that never answers.
     */
    override fun destroy() {
        mainHandler.post {
            if (isDestroyRequested) return@post
            isDestroyRequested = true
            runCatching { webView.stopLoading() }
            mainHandler.postDelayed(::completeDestroy, DESTROY_DRAIN_TIMEOUT_MS)
            completeDestroyIfDrained()
        }
    }

    private fun completeDestroyIfDrained() {
        if (isDestroyRequested && pendingEvaluateCallbacks.isEmpty()) completeDestroy()
    }

    private fun completeDestroy() {
        if (isDestroyed) return
        isDestroyed = true
        // The drain timeout can win with callbacks still outstanding — a hung renderer
        // never calls its ValueCallback. Force them to null rather than abandon them: the
        // evaluateJavaScript contract is "always fires", not "usually fires."
        pendingEvaluateCallbacks.toList().forEach { callback -> callback(null) }
        pendingEvaluateCallbacks.clear()
        runCatching {
            webView.loadUrl("about:blank")
            webView.clearHistory()
            webView.removeAllViews()
        }
        // Destroy separately: a failure in the cosmetic cleanup above must never leak
        // the renderer by skipping the one call that actually frees it.
        runCatching { webView.destroy() }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            builtInZoomControls = true
            displayZoomControls = false
            defaultTextEncodingName = "utf-8"
            // Persistent HTTP cache: in-app resources are revalidated/served per the CDN's
            // cache headers instead of being re-downloaded on every show.
            cacheMode = webViewCacheMode(isCacheEnabled)
            log("cache ${if (isCacheEnabled) "ON" else "OFF"} (cacheMode=$cacheMode)")
            allowContentAccess = true
        }
        webView.setBackgroundColor(Color.TRANSPARENT)
    }

    // Chromium invokes the client callbacks directly: a crash inside the owner's listener
    // would otherwise travel into the WebView framework and take the host app down. Failures
    // are logged and contained; the navigation dispatch defaults to true — "handled, do not
    // navigate" is the safe answer for both the overlay lock and the embedded block.
    private fun dispatchListener(block: () -> Unit) {
        runCatching(block).onFailure { error -> log("WebView event listener crashed: $error") }
    }

    private fun dispatchNavigationLock(block: () -> Boolean?): Boolean =
        runCatching { block() ?: false }.getOrElse { error ->
            log("WebView navigation listener crashed, blocking the navigation: $error")
            true
        }

    private fun createWebViewClient(): WebViewClient {
        return object : WebViewClient() {

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean = dispatchNavigationLock {
                eventListener?.onShouldOverrideUrlLoading(
                    url = request?.url?.toString(),
                    isForMainFrame = request?.isForMainFrame,
                )
            }

            @Deprecated("Deprecated in Java")
            @Suppress("DEPRECATION")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                // The String overload can't tell frames apart (comparing against originalUrl
                // is false for any NEW url — exactly the navigations the lock must catch).
                // Treat everything as main-frame so the lock actually holds on API < 24.
                return dispatchNavigationLock {
                    eventListener?.onShouldOverrideUrlLoading(
                        url = url,
                        isForMainFrame = true,
                    )
                }
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                val webViewError: WebViewError = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    WebViewError(
                        code = error?.errorCode,
                        description = error?.description?.toString(),
                        url = request?.url?.toString(),
                        isForMainFrame = request?.isForMainFrame
                    )
                } else {
                    WebViewError(
                        code = null,
                        description = null,
                        url = request?.url?.toString(),
                        isForMainFrame = request?.isForMainFrame
                    )
                }
                dispatchListener { eventListener?.onError(webViewError) }
            }

            @Deprecated("Deprecated in Java")
            @Suppress("DEPRECATION")
            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                val webViewError: WebViewError = WebViewError(
                    code = errorCode,
                    description = description,
                    url = failingUrl,
                    isForMainFrame = failingUrl == view?.originalUrl
                )
                dispatchListener { eventListener?.onError(webViewError) }
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?
            ) {
                dispatchListener {
                    eventListener?.onHttpError(
                        url = request?.url?.toString(),
                        statusCode = errorResponse?.statusCode,
                        isForMainFrame = request?.isForMainFrame
                    )
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                dispatchListener { eventListener?.onPageFinished(url) }
            }

            // The callback (and RenderProcessGoneDetail) exists since API 26 only — the
            // framework never invokes it below O, where the renderer shares the app process
            // and its death is the app crash itself. TargetApi is truthful, not a suppression.
            @TargetApi(Build.VERSION_CODES.O)
            override fun onRenderProcessGone(
                view: WebView?,
                detail: RenderProcessGoneDetail?
            ): Boolean {
                // Returning false here lets the framework kill the HOST app's process — a real
                // hazard for long-lived embedded blocks whose backgrounded renderer the system
                // is free to reclaim. Consume the event, drop the dead view and let the owner
                // react through the ordinary main-frame error path (overlay closes, block fails).
                log("WebView render process gone (didCrash=${detail?.didCrash()})")
                dispatchListener {
                    eventListener?.onError(
                        WebViewError(
                            code = null,
                            description = "render_process_gone",
                            url = null,
                            isForMainFrame = true,
                        )
                    )
                }
                runCatching {
                    (view?.parent as? ViewGroup)?.removeView(view)
                    view?.destroy()
                }
                return true
            }
        }
    }

    private class AndroidWebViewJsBridge(
        private val bridge: WebViewJsBridge
    ) {
        @JavascriptInterface
        fun postMessage(message: String) {
            bridge.onAction(message)
        }
    }
}
