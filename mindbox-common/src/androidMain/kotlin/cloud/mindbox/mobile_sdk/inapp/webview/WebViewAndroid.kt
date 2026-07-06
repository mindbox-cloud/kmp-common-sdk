package cloud.mindbox.mobile_sdk.inapp.webview

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.*
import cloud.mindbox.mobile_sdk.annotations.InternalMindboxApi

@InternalMindboxApi
public actual typealias WebViewPlatformView = View

@InternalMindboxApi
public fun WebViewController.Companion.create(
    context: android.content.Context,
    isDebugEnabled: Boolean
): WebViewController {
    return AndroidWebViewController(context, isDebugEnabled)
}

@OptIn(InternalMindboxApi::class)
private class AndroidWebViewController(
    context: android.content.Context,
    isDebugEnabled: Boolean
) : WebViewController {

    private val webView: WebView = WebView(context)
    private var eventListener: WebViewEventListener? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    // Set on the main looper inside [destroy]; @Volatile only so a post from a background
    // thread that races the destroy still sees the latch when its block runs.
    @Volatile
    private var isDestroyed = false

    init {
        WebView.setWebContentsDebuggingEnabled(isDebugEnabled)
        configureWebView()
        webView.webViewClient = createWebViewClient()
    }

    override val view: WebViewPlatformView
        get() = webView

    override fun loadContent(content: WebViewHtmlContent) {
        if (MindboxWebViewLab.PROFILER) MbWvProfiler.begin() // MEASUREMENT (throwaway): t0
        webView.loadDataWithBaseURL(
            content.baseUrl,
            content.html,
            "text/html",
            "UTF-8",
            null
        )
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

    // NOT View.post: on a detached view (onClose removes the view from its parent before
    // calling destroy) View.post lands in the view's HandlerActionQueue, which drains only
    // on the next attach — queued work, including destroy() itself, would never run and
    // every closed in-app would leak a live WebView with its page JS still executing.
    override fun executeOnViewThread(action: () -> Unit) {
        mainHandler.post {
            if (isDestroyed) return@post
            action()
        }
    }

    override fun evaluateJavaScript(js: String, resultCallback: ((String?) -> Unit)?) {
        executeOnViewThread {
            webView.evaluateJavascript(js, resultCallback)
        }
    }

    override fun destroy() {
        mainHandler.post {
            if (isDestroyed) return@post
            isDestroyed = true
            runCatching {
                webView.stopLoading()
                webView.loadUrl("about:blank")
                webView.clearHistory()
                webView.removeAllViews()
            }
            // Destroy separately: a failure in the cosmetic cleanup above must never leak
            // the renderer by skipping the one call that actually frees it.
            runCatching { webView.destroy() }
        }
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
            cacheMode = WebSettings.LOAD_DEFAULT
            allowContentAccess = true
        }
        webView.setBackgroundColor(Color.TRANSPARENT)
        if (MindboxWebViewLab.PROFILER) {
            webView.addJavascriptInterface(MbProfilerBridge(), "MBProfiler") // MEASUREMENT (throwaway)
        }
    }

    private fun createWebViewClient(): WebViewClient {
        return object : WebViewClient() {

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                return eventListener?.onShouldOverrideUrlLoading(
                    url = request?.url?.toString(),
                    isForMainFrame = request?.isForMainFrame,
                ) ?: false
            }

            @Deprecated("Deprecated in Java")
            @Suppress("DEPRECATION")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                // The String overload can't tell frames apart (comparing against originalUrl
                // is false for any NEW url — exactly the navigations the lock must catch).
                // Treat everything as main-frame so the lock actually holds on API < 24.
                return eventListener?.onShouldOverrideUrlLoading(
                    url = url,
                    isForMainFrame = true,
                ) ?: false
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
                eventListener?.onError(webViewError)
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
                eventListener?.onError(webViewError)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                if (MindboxWebViewLab.PROFILER) {
                    MbWvProfiler.mark("navFinish") // MEASUREMENT (throwaway)
                    view?.evaluateJavascript(MbWvProfiler.probeJs, null)
                }
                eventListener?.onPageFinished(url)
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

