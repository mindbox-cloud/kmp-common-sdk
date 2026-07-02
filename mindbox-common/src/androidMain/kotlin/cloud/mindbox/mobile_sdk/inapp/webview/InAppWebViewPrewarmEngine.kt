package cloud.mindbox.mobile_sdk.inapp.webview

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
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
     * Loads the real content page under [baseUrl] with a legacy-contract stub bridge:
     * the page detects `SdkBridge.receiveParam`, answers `ready` locally with
     * [endpointId]/[deviceUuid] and an empty popUpId, boots the runtime (tracker →
     * byendpoint into the HTTP cache) and renders no form. Nothing reaches the SDK.
     */
    public fun loadContentPage(
        html: String,
        baseUrl: String,
        endpointId: String,
        deviceUuid: String,
        userAgentSuffix: String?
    ) {
        mainHandler.post {
            runCatching {
                val view = ensureWebView(userAgentSuffix) ?: return@post
                view.addJavascriptInterface(
                    PrewarmLegacyParamBridge(endpointId, deviceUuid),
                    DEFAULT_WEBVIEW_BRIDGE_NAME
                )
                view.loadDataWithBaseURL(baseUrl, html, "text/html", "UTF-8", null)
                log("content page loaded under $baseUrl for endpoint $endpointId")
            }.onFailure { error -> log("content page failed: $error") }
        }
    }

    /** Stops and destroys the prewarm WebView (started downloads are abandoned). */
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

    @SuppressLint("SetJavaScriptEnabled")
    private fun ensureWebView(userAgentSuffix: String?): WebView? {
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

    /**
     * Legacy SDK bridge contract (`window.SdkBridge.receiveParam`): the page treats its
     * presence as "legacy SDK" and never awaits a native `ready` response. An empty
     * popUpId means the runtime initializes without rendering any form.
     */
    private class PrewarmLegacyParamBridge(
        private val endpointId: String,
        private val deviceUuid: String
    ) {
        @JavascriptInterface
        fun receiveParam(param: String?): String = when (param) {
            "endpointId" -> endpointId
            "deviceUuid" -> deviceUuid
            else -> ""
        }

        @JavascriptInterface
        @Suppress("UNUSED_PARAMETER")
        fun postMessage(message: String?) {
            // Prewarm sink: a prewarm page must never reach the real SDK bridge.
        }
    }
}
