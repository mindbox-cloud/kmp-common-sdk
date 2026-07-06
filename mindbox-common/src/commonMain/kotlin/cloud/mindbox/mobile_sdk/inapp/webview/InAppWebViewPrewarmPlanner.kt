package cloud.mindbox.mobile_sdk.inapp.webview

import cloud.mindbox.mobile_sdk.annotations.InternalMindboxApi

/**
 * Webview layer URLs extracted from the mobile config (one entry per webview layer).
 */
@InternalMindboxApi
public data class InAppWebViewPrewarmLayer(
    val baseUrl: String?,
    val contentUrl: String?
)

/**
 * What the prewarm should load: the content page of the first valid webview layer
 * (loaded under [baseUrl] so it lands in the same cache partition as a real show)
 * plus a preconnect page covering every https origin the config knows about.
 */
@InternalMindboxApi
public data class InAppWebViewPrewarmPlan(
    val baseUrl: String,
    val contentUrl: String,
    val preconnectOrigins: List<String>,
    val preconnectHtml: String
)

/**
 * Pure planning logic for the in-app WebView prewarm: derives everything from the
 * mobile config (plus extra origins such as the API domain and hosts learned from
 * previous shows) — no hardcoded hosts.
 */
@InternalMindboxApi
public object InAppWebViewPrewarmPlanner {

    /**
     * Builds a prewarm plan from the config's webview [layers] and [extraOrigins]
     * (API domain, learned hosts — bare hosts or https URLs). Returns null when the
     * config has no layer with a resolvable https baseUrl + contentUrl.
     */
    public fun buildPlan(
        layers: List<InAppWebViewPrewarmLayer>,
        extraOrigins: List<String> = emptyList()
    ): InAppWebViewPrewarmPlan? {
        val page = layers.firstOrNull { layer ->
            httpsOrigin(layer.baseUrl) != null && httpsOrigin(layer.contentUrl) != null
        } ?: return null
        val baseUrl = page.baseUrl ?: return null
        val contentUrl = page.contentUrl ?: return null

        // contentUrl hosts only: baseUrl is just the cache partition and is typically a
        // synthetic host nothing ever connects to — preconnecting it wastes a DNS lookup.
        val origins = (layers.map { it.contentUrl } + extraOrigins)
            .mapNotNull { httpsOrigin(it) }
            .distinct()
            .sorted()

        return InAppWebViewPrewarmPlan(
            baseUrl = baseUrl,
            contentUrl = contentUrl,
            preconnectOrigins = origins,
            preconnectHtml = preconnectHtml(origins)
        )
    }

    /**
     * Normalizes [url] to an "https://host[:port]" origin. Accepts full https URLs and
     * bare hosts ("api.mindbox.ru"); any other scheme yields null.
     */
    public fun httpsOrigin(url: String?): String? {
        val raw = url?.trim().orEmpty()
        if (raw.isEmpty()) return null
        val rest = when {
            raw.startsWith("https://", ignoreCase = true) -> raw.drop("https://".length)
            raw.contains("://") || raw.startsWith("//") -> return null
            else -> raw
        }
        val authority = rest.takeWhile { char -> char != '/' && char != '?' && char != '#' }
        if (authority.isEmpty()) return null
        // Origins get interpolated into preconnect HTML attributes — accept only legal
        // host[:port] characters so a corrupt config value can never become markup.
        if (authority.any { char -> !char.isLetterOrDigit() && char != '.' && char != '-' && char != ':' }) return null
        val host = authority.substringBefore(':')
        if (host.isEmpty() || host.none { char -> char.isLetterOrDigit() }) return null
        return "https://" + authority.lowercase()
    }

    /**
     * The official prewarm contract with the web runtime: the prewarm content page is
     * loaded with these parameters on its document URL (`loadDataWithBaseURL` baseUrl →
     * `location.search`), and a runtime that knows the contract boots tracker-only — no
     * `ready` handshake, no form, byendpoint straight into the HTTP cache. Runtimes that
     * predate the contract ignore the parameters, degrading the prewarm to a plain page
     * warm. Real shows never get these parameters.
     */
    public fun prewarmContentBaseUrl(baseUrl: String, endpointId: String, deviceUuid: String): String {
        // The params must land in the QUERY: appended after a '#' they would live in the
        // fragment, location.search would stay empty and the contract silently degrades.
        val fragmentStart = baseUrl.indexOf('#')
        val withoutFragment = if (fragmentStart >= 0) baseUrl.substring(0, fragmentStart) else baseUrl
        val fragment = if (fragmentStart >= 0) baseUrl.substring(fragmentStart) else ""
        val separator = if ('?' in withoutFragment) '&' else '?'
        return withoutFragment + separator +
            "prewarm=1" +
            "&endpointId=" + encodeQueryValue(endpointId) +
            "&deviceUuid=" + encodeQueryValue(deviceUuid) +
            fragment
    }

    /** RFC 3986 percent-encoding for a query value (unreserved characters pass through). */
    private fun encodeQueryValue(value: String): String = buildString {
        value.encodeToByteArray().forEach { byte ->
            // Only ASCII bytes can be unreserved; non-ASCII UTF-8 bytes are negative here.
            val char = if (byte >= 0) byte.toInt().toChar() else null
            if (char != null && (char.isLetterOrDigit() || char in "-._~")) {
                append(char)
            } else {
                append('%')
                append(((byte.toInt() and 0xFF) or 0x100).toString(16).substring(1).uppercase())
            }
        }
    }

    /**
     * A page with only `<link rel=preconnect>`/`<link rel=dns-prefetch>` hints: warms
     * DNS+TCP+TLS to [origins] without downloading anything.
     */
    public fun preconnectHtml(origins: List<String>): String {
        val links = origins.joinToString(separator = "") { origin ->
            "<link rel=\"preconnect\" href=\"$origin\" crossorigin>" +
                "<link rel=\"dns-prefetch\" href=\"$origin\">"
        }
        return "<!doctype html><html><head><meta charset=\"utf-8\">$links</head><body></body></html>"
    }

    /**
     * Returns a JSON array (as a string) of unique https hosts the current page has
     * actually fetched resources from — feeds the learned-hosts store so the next
     * launch can preconnect to hosts the config alone cannot reveal (image CDNs).
     */
    public val observedResourceHostsScript: String =
        "(function(){try{var h={};performance.getEntriesByType('resource').forEach(function(r){" +
            "try{var u=new URL(r.name);if(u.protocol==='https:'){h[u.host]=1}}catch(e){}});" +
            "return JSON.stringify(Object.keys(h))}catch(e){return'[]'}})()"
}
