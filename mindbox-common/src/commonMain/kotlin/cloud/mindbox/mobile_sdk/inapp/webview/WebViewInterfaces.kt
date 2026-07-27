package cloud.mindbox.mobile_sdk.inapp.webview

import cloud.mindbox.mobile_sdk.annotations.InternalMindboxApi

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
@InternalMindboxApi
public expect class WebViewPlatformView

@InternalMindboxApi
public data class WebViewHtmlContent(
    val baseUrl: String,
    val html: String
)

@InternalMindboxApi
public data class WebViewError(
    val code: Int?,
    val description: String?,
    val url: String?,
    val isForMainFrame: Boolean?
)

@InternalMindboxApi
public fun interface WebViewJsBridge {
    public fun onAction(message: String)
}

@InternalMindboxApi
public interface WebViewEventListener {
    public fun onPageFinished(url: String?)

    public fun onShouldOverrideUrlLoading(url: String?, isForMainFrame: Boolean?): Boolean = false

    public fun onError(error: WebViewError) {
    }

    public fun onHttpError(url: String?, statusCode: Int?, isForMainFrame: Boolean?) {
    }
}

@InternalMindboxApi
public fun isScriptResourceUrl(url: String?): Boolean {
    val raw = url?.trim().orEmpty()
    if (raw.isEmpty()) return false
    val path = raw
        .substringBefore('#')
        .substringBefore('?')
    return path.endsWith(".js", ignoreCase = true)
}

/** Lower bound of HTTP statuses treated as errors for the no-cache recovery path. */
@InternalMindboxApi
public const val HTTP_ERROR_MIN_STATUS: Int = 400

/**
 * True when an HTTP error on a page subresource is the kind the no-cache reload can recover:
 * an error status ([HTTP_ERROR_MIN_STATUS]+) on a script resource ([isScriptResourceUrl]).
 * A cached error on a bootstrap script poisons the page; a broken image or stats beacon can't
 * stop the runtime from booting, so reloading over it would be a regression.
 *
 * The single source of truth shared by both recovery paths — the prewarm engine
 * (`InAppWebViewPrewarmEngine`) and the show-path policy (`WebViewNoCacheRetryPolicy`) — which
 * layer their own path-specific state (init guard / telemetry vs. generation / abort) on top.
 */
@InternalMindboxApi
public fun isRecoverableScriptHttpError(url: String?, statusCode: Int?): Boolean =
    statusCode != null && statusCode >= HTTP_ERROR_MIN_STATUS && isScriptResourceUrl(url)

@InternalMindboxApi
public interface WebViewController {
    public val view: WebViewPlatformView

    public fun loadContent(content: WebViewHtmlContent)

    public fun setVisibility(isVisible: Boolean)

    public fun setUserAgentSuffix(suffix: String)

    public fun setJsBridge(bridge: WebViewJsBridge, bridgeName: String = DEFAULT_WEBVIEW_BRIDGE_NAME)

    public fun setEventListener(listener: WebViewEventListener?)

    public fun setCacheBypass(isBypassEnabled: Boolean) {
    }

    public fun executeOnViewThread(action: () -> Unit)

    /**
     * [resultCallback], if non-null, always fires exactly once — with the raw evaluate
     * result, or `null` when the call can't reach a live WebView (e.g. destroy already
     * requested). Callers waiting on this callback (deferred results, readiness polling)
     * must not be left hanging just because the view is mid-teardown.
     */
    public fun evaluateJavaScript(js: String, resultCallback: ((String?) -> Unit)?)

    public fun destroy()

    public companion object
}

public const val DEFAULT_WEBVIEW_BRIDGE_NAME: String = "SdkBridge"
