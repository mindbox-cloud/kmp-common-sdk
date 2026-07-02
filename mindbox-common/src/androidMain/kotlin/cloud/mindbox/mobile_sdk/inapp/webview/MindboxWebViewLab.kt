package cloud.mindbox.mobile_sdk.inapp.webview

import android.util.Log
import android.webkit.JavascriptInterface
import org.json.JSONObject

/**
 * MEASUREMENT / THROWAWAY — WebView show-time measurement switches.
 *
 * The production levers (persistent cache + config-driven prewarm) live in
 * `AndroidWebViewController` / `InAppWebViewPrewarmEngine` and are always on; this
 * object only carries compile-time switches for A/B measurement runs and the
 * `[WVProfile]` logcat profiler. REMOVE this file + call sites before any ship.
 */
public object MindboxWebViewLab {
    /** Emit `[WVProfile]` show-time marks to logcat (tag `MBWV`). */
    public const val PROFILER: Boolean = false

    /** Gates the PRODUCTION prewarm for A/B runs: false -> cache-only baseline. */
    public const val PREWARM_ENABLED: Boolean = true

    /** Skip the content page: preconnect-only prewarm (the previous measured optimum). */
    public const val PREWARM_PRECONNECT_ONLY: Boolean = false
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
                "[WVProfile] SUMMARY prewarm=${MindboxWebViewLab.PREWARM_ENABLED} " +
                    "preconnectOnly=${MindboxWebViewLab.PREWARM_PRECONNECT_ONLY} | NATIVE $native | JS(doc-rel) $js",
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
