package cloud.mindbox.mobile_sdk.inapp.webview

import cloud.mindbox.mobile_sdk.annotations.InternalMindboxApi

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
    public fun getParam(key: String): String?

    public fun onAction(action: String, data: String) {
    }
}

@InternalMindboxApi
public fun interface WebViewEventListener {
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

    public fun destroy()

    public companion object
}

public const val DEFAULT_WEBVIEW_BRIDGE_NAME: String = "SdkBridge"

