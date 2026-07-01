package cloud.mindbox.mobile_sdk.inapp.webview

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import org.json.JSONObject

/**
 * MEASUREMENT / THROWAWAY — WebView cache + prewarm prototype.
 *
 * Android analog of the iOS `WebViewShowProfiler`. Compile-time flags so they can be flipped in code
 * (as requested) and rebuilt. Default = full stack ON (to feel it on device). Set a flag to `false`
 * to restore current behavior. REMOVE this file + call sites before any ship.
 *
 * Levers (mirror iOS):
 *  - [PERSISTENT_CACHE]: `WebSettings.cacheMode` `LOAD_NO_CACHE` (current) -> `LOAD_DEFAULT` (cache on).
 *  - [PREWARM]: warm the WebView renderer process at SDK start (framework-only; the androidx.webkit
 *    1.16.0 `ProcessGlobalConfig.startUpWebView` variant lives in the optional module).
 *  - [PROFILER]: emit `[WVProfile]` show-time marks to logcat (tag `MBWV`).
 */
public object MindboxWebViewLab {
    // Optimal Android config: cache (the win) + preconnect (warms CDN connections for show #1).
    public const val PERSISTENT_CACHE: Boolean = true  // baseline->cache: show1 4584->972, show2 841->232
    public const val PREWARM: Boolean = true           // preconnect variant: show1 972->~532
    public const val PROFILER: Boolean = false         // measurement off for the clean/optimal APK

    private var warmWebView: WebView? = null

    /**
     * MEASUREMENT (throwaway) prewarm — PRECONNECT variant. At SDK start, create a hidden [WebView] that
     * loads a page with ONLY `<link rel=preconnect>`/`<link rel=dns-prefetch>` to the CDN hosts — NO heavy
     * download. This warms both the shared Chromium renderer process AND its connection pool (DNS+TCP+TLS)
     * to the CDNs, which the real in-app WebViews reuse (Chromium's network service is process-wide). It
     * targets show #1's ACTUAL bottleneck — images loading over COLD connections — which process-only
     * warm (`about:blank`) didn't fix and a full cacher made worse (bandwidth contention). Idempotent; main thread.
     */
    public fun prewarm(context: Context) {
        if (!PREWARM) return
        Handler(Looper.getMainLooper()).post {
            if (warmWebView != null) return@post
            runCatching {
                val t0 = System.nanoTime()
                val wv = WebView(context.applicationContext)
                wv.settings.cacheMode =
                    if (PERSISTENT_CACHE) WebSettings.LOAD_DEFAULT else WebSettings.LOAD_NO_CACHE
                wv.loadDataWithBaseURL("https://inapp.local/popup", preconnectHtml(), "text/html", "UTF-8", null)
                warmWebView = wv
                val ms = (System.nanoTime() - t0) / 1_000_000
                Log.i("MBWV", "[WVProfile] prewarm(preconnect): warm WebView created createMs=$ms")
            }.onFailure { Log.i("MBWV", "[WVProfile] prewarm failed: $it") }
        }
    }

    // Preconnect-only page: warms Chromium's connection pool to the CDN hosts without downloading.
    private fun preconnectHtml(): String {
        val hosts = listOf(
            "https://web-static.mindbox.ru",
            "https://api.mindbox.ru",
            "https://mobile-static.mindbox.ru",
            "https://personalization-web.g.mindbox.ru",      // actual image host (from byendpoint)
            "https://personalization-web-stable.mindbox.ru", // actual image host (from byendpoint)
            "https://fonts.googleapis.com",
            "https://fonts.gstatic.com",
        )
        val links = hosts.joinToString("") {
            "<link rel=\"preconnect\" href=\"$it\" crossorigin><link rel=\"dns-prefetch\" href=\"$it\">"
        }
        return "<html><head><meta charset=\"utf-8\">$links</head><body></body></html>"
    }
}

/**
 * MEASUREMENT / THROWAWAY. Records per-step timestamps for ONE WebView in-app show and emits a single
 * `[WVProfile] SUMMARY` logcat line (tag `MBWV`). t0 = first `loadContent`. Native marks are
 * t0-relative ms; JS marks (from the injected probe) are document-relative ms — stitched offline.
 */
internal object MbWvProfiler {
    private const val TAG = "MBWV"
    private var t0: Long = 0L
    private val marks = LinkedHashMap<String, Long>()
    private var finalized = false
    private val lock = Any()

    fun begin() {
        synchronized(lock) {
            t0 = System.nanoTime()
            marks.clear()
            marks["begin"] = 0L
            finalized = false
            Log.i(TAG, "[WVProfile] begin")
        }
    }

    fun mark(label: String) {
        synchronized(lock) {
            if (t0 == 0L) {
                t0 = System.nanoTime()
                marks.clear()
                finalized = false
            }
            val ms = (System.nanoTime() - t0) / 1_000_000
            marks[label] = ms
            Log.i(TAG, "[WVProfile] mark $label +${ms}ms")
        }
    }

    fun ingestJs(json: String) {
        synchronized(lock) {
            if (finalized || t0 == 0L) return
            finalized = true
            val native = marks.entries.joinToString(" ") { "${it.key}=${it.value}" }
            val js = runCatching {
                val o = JSONObject(json)
                val order = listOf(
                    "firstPaint", "fcp", "lcp", "domContentLoaded", "domComplete",
                    "loadEventEnd", "lastImageEnd", "lastResourceEnd",
                    "resourceCount", "imageCount", "transferBytes",
                    "byendpointDur", "byendpointEnd", "finalizedByCap",
                )
                order.filter { o.has(it) }.joinToString(" ") { "$it=${o.get(it)}" }
            }.getOrElse { "jsParseError=$it" }
            Log.i(
                TAG,
                "[WVProfile] SUMMARY cache=${MindboxWebViewLab.PERSISTENT_CACHE} " +
                    "prewarm=${MindboxWebViewLab.PREWARM} | NATIVE $native | JS(doc-rel) $js",
            )
        }
    }

    val probeJs: String
        get() = PROBE
}

/** MEASUREMENT-ONLY JS interface receiving the probe's consolidated payload. */
internal class MbProfilerBridge {
    @JavascriptInterface
    fun postMessage(message: String) {
        MbWvProfiler.ingestJs(message)
    }
}

// Same probe as iOS (paint/LCP/resource PerformanceObservers, network-idle finalize at 1200ms,
// 9s cap), posting the consolidated payload to the native `MBProfiler` interface as a JSON string.
private const val PROBE = """
(function () {
  var marks = {};
  var lastResAt = 0, loaded = false, done = false;
  function rec(k, v) { if (typeof v === 'number' && isFinite(v)) marks[k] = Math.round(v); }
  try {
    new PerformanceObserver(function (l) {
      l.getEntries().forEach(function (e) {
        if (e.name === 'first-paint') rec('firstPaint', e.startTime);
        if (e.name === 'first-contentful-paint') rec('fcp', e.startTime);
      });
    }).observe({ type: 'paint', buffered: true });
  } catch (e) {}
  try {
    new PerformanceObserver(function (l) {
      var es = l.getEntries(); var last = es[es.length - 1];
      rec('lcp', last.startTime);
    }).observe({ type: 'largest-contentful-paint', buffered: true });
  } catch (e) {}
  try {
    new PerformanceObserver(function (l) {
      l.getEntries().forEach(function (r) { if (r.responseEnd > lastResAt) lastResAt = r.responseEnd; });
    }).observe({ type: 'resource', buffered: true });
  } catch (e) {}
  function finalize(byCap) {
    if (done) return; done = true;
    try {
      var nav = performance.getEntriesByType('navigation')[0];
      if (nav) { rec('domContentLoaded', nav.domContentLoadedEventEnd); rec('domComplete', nav.domComplete); rec('loadEventEnd', nav.loadEventEnd); }
      var res = performance.getEntriesByType('resource');
      rec('resourceCount', res.length);
      var imgs = res.filter(function (r) { return r.initiatorType === 'img' || r.initiatorType === 'css' || /\.(png|jpe?g|webp|gif|svg)(\?|${'$'})/i.test(r.name); });
      rec('imageCount', imgs.length);
      var lastResEnd = 0, lastImgEnd = 0, bytes = 0;
      res.forEach(function (r) { if (r.responseEnd > lastResEnd) lastResEnd = r.responseEnd; bytes += (r.transferSize || r.encodedBodySize || 0); });
      imgs.forEach(function (r) { if (r.responseEnd > lastImgEnd) lastImgEnd = r.responseEnd; });
      rec('lastResourceEnd', lastResEnd);
      if (imgs.length) rec('lastImageEnd', lastImgEnd);
      rec('transferBytes', bytes);
      var be = res.filter(function (r) { return /byendpoint/i.test(r.name); }).pop();
      if (be) { rec('byendpointDur', be.duration); rec('byendpointEnd', be.responseEnd); }
      rec('finalizedByCap', byCap ? 1 : 0);
      window.MBProfiler.postMessage(JSON.stringify(marks));
    } catch (e) {
      try { window.MBProfiler.postMessage(JSON.stringify({ jsError: String(e) })); } catch (_) {}
    }
  }
  var startedAt = performance.now();
  var iv = setInterval(function () {
    var now = performance.now();
    if (done) { clearInterval(iv); return; }
    if (loaded && (now - lastResAt) > 1200) { clearInterval(iv); finalize(false); }
    else if (now - startedAt > 9000) { clearInterval(iv); finalize(true); }
  }, 250);
  function onLoad() { loaded = true; lastResAt = Math.max(lastResAt, performance.now()); }
  if (document.readyState === 'complete') { onLoad(); } else { window.addEventListener('load', onLoad); }
})();
"""
