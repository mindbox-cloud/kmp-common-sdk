package cloud.mindbox.mobile_sdk.inapp.webview

import cloud.mindbox.mobile_sdk.annotations.InternalMindboxApi
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(InternalMindboxApi::class)
class IsScriptResourceUrlTest {

    @Test
    fun script_urls_match() {
        assertTrue(isScriptResourceUrl("https://api.example.com/scripts/v1/tracker.js"))
        assertTrue(isScriptResourceUrl("https://api.example.com/scripts/v1/tracker.js?v=1.0.31"))
        assertTrue(isScriptResourceUrl("https://web-static.mindbox.ru/js/byendpoint/x.webview.js?_=5949609"))
        assertTrue(isScriptResourceUrl("https://cdn.test/main.js#fragment"))
        assertTrue(isScriptResourceUrl("https://cdn.test/MAIN.JS"))
        assertTrue(isScriptResourceUrl("  https://cdn.test/padded.js  "))
    }

    @Test
    fun non_script_urls_do_not_match() {
        assertFalse(isScriptResourceUrl(null))
        assertFalse(isScriptResourceUrl(""))
        assertFalse(isScriptResourceUrl("   "))
        assertFalse(isScriptResourceUrl("https://cdn.test/banner.png"))
        assertFalse(isScriptResourceUrl("https://fonts.googleapis.com/css2?family=Inter"))
        assertFalse(isScriptResourceUrl("https://stats.test/client-stats?pg=1"))
        // ".js" only in the query/fragment, not in the path
        assertFalse(isScriptResourceUrl("https://cdn.test/page?file=tracker.js"))
        assertFalse(isScriptResourceUrl("https://cdn.test/page#tracker.js"))
        // path merely containing ".js" without ending on it
        assertFalse(isScriptResourceUrl("https://cdn.test/tracker.json"))
    }

    @Test
    fun recoverable_script_http_error_requires_error_status_and_script() {
        // Error status on a script → recoverable.
        assertTrue(isRecoverableScriptHttpError("https://api.example.com/scripts/v1/tracker.js", 404))
        assertTrue(isRecoverableScriptHttpError("https://cdn.test/main.js", 500))
        // Boundary: exactly HTTP_ERROR_MIN_STATUS.
        assertTrue(isRecoverableScriptHttpError("https://cdn.test/main.js", HTTP_ERROR_MIN_STATUS))
    }

    @Test
    fun non_recoverable_script_http_error_cases() {
        // Success / redirect / missing status are not errors.
        assertFalse(isRecoverableScriptHttpError("https://cdn.test/main.js", 200))
        assertFalse(isRecoverableScriptHttpError("https://cdn.test/main.js", 302))
        assertFalse(isRecoverableScriptHttpError("https://cdn.test/main.js", null))
        // Error status but not a script resource.
        assertFalse(isRecoverableScriptHttpError("https://cdn.test/banner.png", 404))
    }
}
