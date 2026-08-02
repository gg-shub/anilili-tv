package com.shubh.anililitv.data.remote

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.shubh.anililitv.data.settings.SettingsStore
import com.shubh.anililitv.diagnostics.DiagnosticsLog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

data class RawPipeResponse(val ok: Boolean, val status: Int, val obf: String?, val body: String?, val error: String?)

@SuppressLint("StaticFieldLeak")
object PipeBridge {
    private val ORIGINS = listOf(
        "https://www.miruro.to",
        "https://www.miruro.tv",
        "https://www.miruro.bz",
    )

    private val main = Handler(Looper.getMainLooper())

    private const val IDLE_AFTER_MS = 2_000L
    private val idleRunnable = Runnable {
        webView?.onPause()
        DiagnosticsLog.event("PipeBridge webview idled")
    }

    private fun scheduleIdle() {
        main.removeCallbacks(idleRunnable)
        main.postDelayed(idleRunnable, IDLE_AFTER_MS)
    }

    private fun scheduleIdleIfUnused() {
        main.post {
            if (pending.isEmpty()) scheduleIdle()
        }
    }

    private const val MIRROR_TIMEOUT_MS = 7_000L

    @Volatile private var webView: WebView? = null
    @Volatile private var ready = CompletableDeferred<Boolean>()
    @Volatile private var originIndex = 0
    private val pending = ConcurrentHashMap<String, CompletableDeferred<String>>()
    private val counter = AtomicLong(0)

    private val activeOrigin: String get() = ORIGINS[originIndex]

    private val mirrorWatchdog = Runnable {
        DiagnosticsLog.event("PipeBridge mirror timed out after ${MIRROR_TIMEOUT_MS}ms origin=$activeOrigin")
        advanceMirror()
    }

    private fun armMirrorWatchdog() {
        main.removeCallbacks(mirrorWatchdog)
        main.postDelayed(mirrorWatchdog, MIRROR_TIMEOUT_MS)
    }

    private fun advanceMirror() {
        main.removeCallbacks(mirrorWatchdog)
        if (ready.isCompleted) return
        if (originIndex < ORIGINS.lastIndex) {
            originIndex++
            DiagnosticsLog.event("PipeBridge trying mirror $activeOrigin")
            armMirrorWatchdog()
            webView?.loadUrl("$activeOrigin/")
        } else {
            DiagnosticsLog.event("PipeBridge all mirrors failed")
            ready.complete(false)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun attach(wv: WebView) {
        DiagnosticsLog.event("PipeBridge.attach")
        webView = wv
        if (ready.isCompleted) ready = CompletableDeferred()

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)
        with(wv.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = false
            allowContentAccess = false
        }
        wv.addJavascriptInterface(Bridge, "AndroidPipe")
        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url ?: return true
                val host = url.host.orEmpty().lowercase()
                val allowed = url.scheme == "https" && ORIGINS.any { origin ->
                    val originHost = origin.removePrefix("https://www.")
                    host == originHost || host.endsWith(".$originHost")
                }
                if (!allowed) {
                    DiagnosticsLog.event("PipeBridge blocked nav: $url")
                    Log.d(TAG, "blocked nav: $url")
                }
                return !allowed
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                DiagnosticsLog.event("PipeBridge page started: ${url ?: "unknown"}")
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                DiagnosticsLog.event("PipeBridge page finished: ${url ?: "unknown"} title=${view?.title ?: "none"}")
                Log.d(TAG, "onPageFinished: $url  title=${view?.title}")
                if (url != null && url.startsWith(activeOrigin)) {
                    main.removeCallbacks(mirrorWatchdog)
                    SettingsStore.setLastWorkingPipeOrigin(activeOrigin)
                    main.postDelayed(
                        {
                            if (!ready.isCompleted) ready.complete(true)
                            scheduleIdle()
                        },
                        2000,
                    )
                }
            }

            @android.annotation.TargetApi(android.os.Build.VERSION_CODES.M)
            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: android.webkit.WebResourceError?,
            ) {
                if (request?.isForMainFrame != true) return
                DiagnosticsLog.event(
                    "PipeBridge main-frame error code=${error?.errorCode} " +
                        "description=${error?.description} origin=$activeOrigin",
                )
                main.post { advanceMirror() }
            }

            @android.annotation.TargetApi(android.os.Build.VERSION_CODES.O)
            override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                DiagnosticsLog.event(
                    "PipeBridge render process gone didCrash=${detail?.didCrash()} " +
                        "priority=${detail?.rendererPriorityAtExit()}",
                )
                view?.let(::detach)
                return true
            }
        }
        originIndex = ORIGINS.indexOf(SettingsStore.lastWorkingPipeOrigin.value).coerceAtLeast(0)
        DiagnosticsLog.event("PipeBridge load origin=$activeOrigin")
        armMirrorWatchdog()
        wv.loadUrl("$activeOrigin/")
    }

    fun detach(wv: WebView) {
        if (webView !== wv) return
        DiagnosticsLog.event("PipeBridge.detach")
        main.removeCallbacks(idleRunnable)
        webView = null
        ready = CompletableDeferred()
        pending.entries.toList().forEach { (id, request) ->
            if (pending.remove(id, request)) {
                request.complete("""{"ok":false,"status":-1,"error":"webview released"}""")
            }
        }
        wv.removeJavascriptInterface("AndroidPipe")
    }

    object Bridge {
        @JavascriptInterface
        fun onResult(id: String, json: String) {
            pending.remove(id)?.complete(json)
        }
    }

    suspend fun fetch(e: String, timeoutMs: Long = 30_000): String {
        withTimeoutOrNull(25_000) { ready.await() }
        val id = counter.incrementAndGet().toString()
        val deferred = CompletableDeferred<String>()
        pending[id] = deferred

        val js = """
            (function(){
              try {
                fetch('/api/secure/pipe?e=$e', { headers: { 'Accept': '*/*' }, credentials: 'include' })
                  .then(function(r){
                    return r.text().then(function(b){
                      AndroidPipe.onResult('$id', JSON.stringify({
                        ok: r.ok, status: r.status,
                        obf: (r.headers.get('x-obfuscated') || ''), body: b
                      }));
                    });
                  })
                  .catch(function(err){
                    AndroidPipe.onResult('$id', JSON.stringify({ ok:false, status:-1, error:String(err) }));
                  });
              } catch (err) {
                AndroidPipe.onResult('$id', JSON.stringify({ ok:false, status:-1, error:String(err) }));
              }
            })();
        """.trimIndent()

        main.post {
            val wv = webView
            if (wv == null) {
                pending.remove(id)?.complete("""{"ok":false,"status":-1,"error":"webview not ready"}""")
            } else {
                main.removeCallbacks(idleRunnable)
                wv.onResume()
                wv.evaluateJavascript(js, null)
            }
        }

        val result = withTimeoutOrNull(timeoutMs) { deferred.await() }
            ?: run {
                pending.remove(id)
                DiagnosticsLog.event("PipeBridge fetch timeout e.len=${e.length}")
                """{"ok":false,"status":-1,"error":"timeout"}"""
            }
        scheduleIdleIfUnused()
        Log.d(TAG, "fetch(e.len=${e.length}) -> ${result.take(180)}")
        return result
    }

    private const val TAG = "PipeBridge"
}
