package cloud.mindbox.mobile_sdk.inapp.webview

import android.webkit.WebSettings

/**
 * The cache half of the WebView feature toggles (`MobileSdkShouldCacheInAppWebView`, owned by
 * FeatureToggleManager on the SDK side): shared by the prewarm engine and the real show so both
 * WebView instances make the exact same cache decision.
 *
 * Toggle on -> [WebSettings.LOAD_DEFAULT] (persistent HTTP cache). Toggle off ->
 * [WebSettings.LOAD_NO_CACHE], restoring the pre-feature behavior exactly.
 */
internal fun webViewCacheMode(isCacheEnabled: Boolean): Int =
    if (isCacheEnabled) WebSettings.LOAD_DEFAULT else WebSettings.LOAD_NO_CACHE
