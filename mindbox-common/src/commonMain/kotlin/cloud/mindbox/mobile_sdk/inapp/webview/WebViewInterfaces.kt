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

/**
 * A ready-to-serve cached response for a WebView subresource request. Android's
 * [shouldInterceptRequest] returns a `WebResourceResponse` built from these fields;
 * other platforms can ignore this type.
 *
 * [encoding] defaults to UTF-8 which is correct for .js/.html/.json; binary resources
 * (images, fonts) should pass `null` or an empty string so the platform doesn't try
 * to decode them.
 */
@InternalMindboxApi
public data class WebViewCachedResource(
    val bytes: ByteArray,
    val mimeType: String,
    val encoding: String? = "UTF-8",
    val statusCode: Int = 200,
    val reasonPhrase: String = "OK",
    val extraHeaders: Map<String, String> = emptyMap(),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WebViewCachedResource) return false
        return mimeType == other.mimeType &&
            encoding == other.encoding &&
            statusCode == other.statusCode &&
            reasonPhrase == other.reasonPhrase &&
            extraHeaders == other.extraHeaders &&
            bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + (encoding?.hashCode() ?: 0)
        result = 31 * result + statusCode
        result = 31 * result + reasonPhrase.hashCode()
        result = 31 * result + extraHeaders.hashCode()
        return result
    }
}

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

    /**
     * Invoked on a background thread for every subresource the WebView is about to load.
     * Return a [WebViewCachedResource] to short-circuit the network request and serve
     * bytes from disk; return `null` to let the WebView load from network normally.
     *
     * Used by the offline webview-asset cache to intercept requests for byendpoint.js,
     * quizzes.js and other dynamically-loaded runtime scripts that bypass the static
     * HTML inlining path.
     */
    public fun onShouldInterceptRequest(url: String?): WebViewCachedResource? = null
}

@InternalMindboxApi
public interface WebViewController {
    public val view: WebViewPlatformView

    public fun loadContent(content: WebViewHtmlContent)

    public fun setVisibility(isVisible: Boolean)

    public fun setUserAgentSuffix(suffix: String)

    public fun setJsBridge(bridge: WebViewJsBridge, bridgeName: String = DEFAULT_WEBVIEW_BRIDGE_NAME)

    public fun setEventListener(listener: WebViewEventListener?)

    public fun executeOnViewThread(action: () -> Unit)

    public fun evaluateJavaScript(js: String, resultCallback: ((String?) -> Unit)?)

    public fun destroy()

    public companion object
}

public const val DEFAULT_WEBVIEW_BRIDGE_NAME: String = "SdkBridge"
