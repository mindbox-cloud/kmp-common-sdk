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

    public fun onError(error: WebViewError) {
    }
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
