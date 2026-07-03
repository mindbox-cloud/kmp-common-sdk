package cloud.mindbox.mobile_sdk.inapp.webview

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InAppWebViewPrewarmPlannerTest {

    private val layer = InAppWebViewPrewarmLayer(
        baseUrl = "https://inapp.local/popup",
        contentUrl = "https://mobile-static.mindbox.ru/stable/inapps/webview/content/index.html"
    )

    @Test
    fun testHttpsOrigin_fullUrl() {
        assertEquals(
            "https://mobile-static.mindbox.ru",
            InAppWebViewPrewarmPlanner.httpsOrigin("https://mobile-static.mindbox.ru/stable/index.html?x=1#f")
        )
    }

    @Test
    fun testHttpsOrigin_bareHostLowercased() {
        assertEquals("https://api.mindbox.ru", InAppWebViewPrewarmPlanner.httpsOrigin("API.Mindbox.ru"))
    }

    @Test
    fun testHttpsOrigin_keepsPort() {
        assertEquals("https://host.ru:8443", InAppWebViewPrewarmPlanner.httpsOrigin("https://host.ru:8443/path"))
    }

    @Test
    fun testHttpsOrigin_rejectsNonHttpsAndGarbage() {
        assertNull(InAppWebViewPrewarmPlanner.httpsOrigin("http://insecure.ru/x"))
        assertNull(InAppWebViewPrewarmPlanner.httpsOrigin("ftp://files.ru"))
        assertNull(InAppWebViewPrewarmPlanner.httpsOrigin("//protocol-relative.ru"))
        assertNull(InAppWebViewPrewarmPlanner.httpsOrigin("https://"))
        assertNull(InAppWebViewPrewarmPlanner.httpsOrigin("https://user@host.ru"))
        assertNull(InAppWebViewPrewarmPlanner.httpsOrigin("   "))
        assertNull(InAppWebViewPrewarmPlanner.httpsOrigin(null))
        assertNull(InAppWebViewPrewarmPlanner.httpsOrigin("https://:443"))
    }

    @Test
    fun testBuildPlan_firstValidLayerAndUniqueSortedOrigins() {
        val plan = InAppWebViewPrewarmPlanner.buildPlan(
            layers = listOf(
                InAppWebViewPrewarmLayer(baseUrl = null, contentUrl = "https://mobile-static.mindbox.ru/a.html"),
                layer,
                layer.copy(contentUrl = "https://other-static.mindbox.ru/b.html")
            ),
            extraOrigins = listOf("api.mindbox.ru", "https://web-static.mindbox.ru", "api.mindbox.ru")
        )

        assertNotNull(plan)
        assertEquals("https://inapp.local/popup", plan.baseUrl)
        assertEquals("https://mobile-static.mindbox.ru/stable/inapps/webview/content/index.html", plan.contentUrl)
        assertEquals(
            listOf(
                "https://api.mindbox.ru",
                "https://mobile-static.mindbox.ru",
                "https://other-static.mindbox.ru",
                "https://web-static.mindbox.ru"
            ),
            plan.preconnectOrigins
        )
    }

    @Test
    fun testBuildPlan_nullWhenNoValidLayer() {
        val plan = InAppWebViewPrewarmPlanner.buildPlan(
            layers = listOf(
                InAppWebViewPrewarmLayer(baseUrl = "https://inapp.local/popup", contentUrl = null),
                InAppWebViewPrewarmLayer(baseUrl = null, contentUrl = null),
                InAppWebViewPrewarmLayer(baseUrl = "http://insecure.ru", contentUrl = "https://ok.ru/x")
            ),
            extraOrigins = listOf("api.mindbox.ru")
        )

        assertNull(plan)
        assertNull(InAppWebViewPrewarmPlanner.buildPlan(emptyList()))
    }

    @Test
    fun testBuildPlan_ignoresInvalidExtraOrigins() {
        val plan = InAppWebViewPrewarmPlanner.buildPlan(
            layers = listOf(layer),
            extraOrigins = listOf("", "http://nope.ru", "fonts.gstatic.com")
        )

        assertNotNull(plan)
        assertEquals(
            listOf("https://fonts.gstatic.com", "https://mobile-static.mindbox.ru"),
            plan.preconnectOrigins
        )
    }

    @Test
    fun testBuildPlan_baseUrlHostIsNotPreconnected() {
        val plan = InAppWebViewPrewarmPlanner.buildPlan(layers = listOf(layer))

        assertNotNull(plan)
        assertEquals("https://inapp.local/popup", plan.baseUrl)
        assertEquals(listOf("https://mobile-static.mindbox.ru"), plan.preconnectOrigins)
    }

    @Test
    fun testPrewarmContentBaseUrl_appendsContractParams() {
        assertEquals(
            "https://inapp.local/popup?prewarm=1&endpointId=Mpush-test.WebView&deviceUuid=abc-123",
            InAppWebViewPrewarmPlanner.prewarmContentBaseUrl(
                baseUrl = "https://inapp.local/popup",
                endpointId = "Mpush-test.WebView",
                deviceUuid = "abc-123"
            )
        )
    }

    @Test
    fun testPrewarmContentBaseUrl_keepsExistingQueryAndEncodes() {
        assertEquals(
            "https://inapp.local/popup?keep=me&prewarm=1&endpointId=End%20point%26x&deviceUuid=%D1%8E%D0%B8%D0%B4",
            InAppWebViewPrewarmPlanner.prewarmContentBaseUrl(
                baseUrl = "https://inapp.local/popup?keep=me",
                endpointId = "End point&x",
                deviceUuid = "юид"
            )
        )
    }

    @Test
    fun testPreconnectHtml_linksPerOriginNoScripts() {
        val html = InAppWebViewPrewarmPlanner.preconnectHtml(
            listOf("https://a.ru", "https://b.ru")
        )

        assertTrue(html.startsWith("<!doctype html>"))
        assertTrue(html.contains("<link rel=\"preconnect\" href=\"https://a.ru\" crossorigin>"))
        assertTrue(html.contains("<link rel=\"dns-prefetch\" href=\"https://b.ru\">"))
        assertFalse(html.contains("<script"))
    }

    @Test
    fun testObservedResourceHostsScript_selfInvokingJsonExpression() {
        val script = InAppWebViewPrewarmPlanner.observedResourceHostsScript

        assertTrue(script.startsWith("(function(){"))
        assertTrue(script.endsWith("})()"))
        assertTrue(script.contains("getEntriesByType('resource')"))
        assertTrue(script.contains("JSON.stringify"))
    }
}
