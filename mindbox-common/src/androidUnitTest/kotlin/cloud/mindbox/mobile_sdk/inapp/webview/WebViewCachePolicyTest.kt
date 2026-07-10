package cloud.mindbox.mobile_sdk.inapp.webview

import android.webkit.WebSettings
import org.junit.Assert.assertEquals
import org.junit.Test

internal class WebViewCachePolicyTest {

    @Test
    fun `toggle on selects the persistent HTTP cache`() {
        assertEquals(WebSettings.LOAD_DEFAULT, webViewCacheMode(isCacheEnabled = true))
    }

    @Test
    fun `toggle off restores the pre-feature cache-less mode`() {
        assertEquals(WebSettings.LOAD_NO_CACHE, webViewCacheMode(isCacheEnabled = false))
    }
}
