package com.miruronative.ui.watch

import android.annotation.SuppressLint
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient.CustomViewCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.miruronative.diagnostics.CrashReporter
import com.miruronative.ui.adaptive.LocalAppDeviceProfile

/**
 * Renders an embed/iframe provider (or the live site) in a WebView. This is both the player for
 * `type:"embed"` sources and the durable fallback when the native path is Cloudflare-blocked.
 *
 * Main-frame navigation is pinned to the embed's own host: ad-funded embed pages fire same-frame
 * redirects that would otherwise replace the video with an ad page. Resource and iframe loads are
 * unaffected, so the players themselves keep working.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun EmbedWebView(
    url: String,
    referer: String?,
    modifier: Modifier = Modifier,
    onFullscreenChanged: (Boolean) -> Unit = {},
    onProgress: ((positionMs: Long, durationMs: Long) -> Unit)? = null,
) {
    val device = LocalAppDeviceProfile.current
    val lifecycleOwner = LocalContext.current.findLifecycleOwner()
    var customView by remember { mutableStateOf<View?>(null) }
    var customViewCallback by remember { mutableStateOf<CustomViewCallback?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    val currentOnFullscreenChanged by rememberUpdatedState(onFullscreenChanged)
    val currentOnProgress by rememberUpdatedState(onProgress)

    // Registrable-ish host the embed is allowed to navigate within (its own host + subdomains).
    val allowedHost = remember(url) {
        runCatching { Uri.parse(url).host }.getOrNull()?.lowercase()?.removePrefix("www.")
    }

    // Called from the WebView's JS bridge thread every few seconds while the page's <video> plays.
    val progressBridge = remember {
        object {
            @JavascriptInterface
            fun onTick(positionSec: Double, durationSec: Double) {
                if (positionSec <= 0 || durationSec <= 0) return
                currentOnProgress?.invoke((positionSec * 1000).toLong(), (durationSec * 1000).toLong())
            }
        }
    }

    val chromeClient = remember {
        object : WebChromeClient() {
            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (view == null) {
                    callback?.onCustomViewHidden()
                    return
                }
                if (customView != null) {
                    callback?.onCustomViewHidden()
                    return
                }

                customView = view
                customViewCallback = callback
                currentOnFullscreenChanged(true)
            }

            override fun onHideCustomView() {
                val fullscreenView = customView ?: return
                (fullscreenView.parent as? ViewGroup)?.removeView(fullscreenView)
                customView = null
                customViewCallback?.onCustomViewHidden()
                customViewCallback = null
                currentOnFullscreenChanged(false)
            }
        }
    }

    val webClient = remember(allowedHost) {
        object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val target = request?.url ?: return true
                if (!request.isForMainFrame) return false
                val host = target.host.orEmpty().lowercase().removePrefix("www.")
                val allowed = allowedHost != null &&
                    (host == allowedHost || host.endsWith(".$allowedHost"))
                return !allowed // block ad redirects that navigate away from the embed
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                loadError = null
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                view?.evaluateJavascript(PROGRESS_POLL_JS, null)
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                if (request?.isForMainFrame == true) {
                    loadError = error?.description?.toString() ?: "The server did not respond"
                }
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?,
            ) {
                if (request?.isForMainFrame == true) {
                    loadError = "HTTP ${errorResponse?.statusCode ?: "error"} from the video server"
                }
            }
        }
    }

    BackHandler(enabled = customView != null) { chromeClient.onHideCustomView() }

    DisposableEffect(chromeClient) {
        onDispose {
            val fullscreenView = customView
            if (fullscreenView != null) {
                (fullscreenView.parent as? ViewGroup)?.removeView(fullscreenView)
                customView = null
                customViewCallback?.onCustomViewHidden()
                customViewCallback = null
            }
        }
    }

    DisposableEffect(lifecycleOwner, webView) {
        val owner = lifecycleOwner
        val web = webView
        if (owner == null || web == null) {
            onDispose {}
        } else {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_PAUSE,
                    Lifecycle.Event.ON_STOP -> {
                        runCatching { web.evaluateJavascript(PAUSE_VIDEO_JS, null) }
                        web.onPause()
                        web.pauseTimers()
                    }
                    Lifecycle.Event.ON_RESUME,
                    Lifecycle.Event.ON_START -> {
                        web.resumeTimers()
                        web.onResume()
                    }
                    else -> Unit
                }
            }
            owner.lifecycle.addObserver(observer)
            onDispose { owner.lifecycle.removeObserver(observer) }
        }
    }

    Box(modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                try {
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        setBackgroundColor(android.graphics.Color.BLACK)
                        isFocusable = true
                        isFocusableInTouchMode = true
                        with(settings) {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            mediaPlaybackRequiresUserGesture = false
                            loadWithOverviewMode = true
                            useWideViewPort = true
                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            allowFileAccess = false
                            allowContentAccess = false
                            userAgentString = userAgentString.replace("; wv", "") // look less like a webview
                        }
                        addJavascriptInterface(progressBridge, "AniliProgress")
                        webViewClient = webClient
                        webChromeClient = chromeClient
                        if (device.isTv) post { requestFocus() }
                        webView = this
                    }
                } catch (e: Throwable) {
                    CrashReporter.logNonFatal("System WebView unavailable; embed player disabled", e)
                    View(ctx).apply { setBackgroundColor(android.graphics.Color.BLACK) }
                }
            },
            update = { view ->
                val web = view as? WebView ?: return@AndroidView
                web.webViewClient = webClient
                val headers = referer?.let { mapOf("Referer" to it) } ?: emptyMap()
                if (web.url != url) web.loadUrl(url, headers)
            },
            onRelease = { view ->
                val web = view as? WebView ?: return@AndroidView
                if (webView === web) webView = null
                runCatching { web.evaluateJavascript(PAUSE_VIDEO_JS, null) }
                web.onPause()
                web.pauseTimers()
                web.stopLoading()
                web.removeJavascriptInterface("AniliProgress")
                web.loadUrl("about:blank")
                web.clearHistory()
                web.removeAllViews()
                web.webChromeClient = null
                web.webViewClient = WebViewClient()
                web.destroy()
            },
        )

        loadError?.let { message ->
            Column(
                Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "This server's page failed to load",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                Text(
                    "$message — try another server below.",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        customView?.let { fullscreenView ->
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    FrameLayout(ctx).apply {
                        setBackgroundColor(android.graphics.Color.BLACK)
                        isFocusable = true
                        descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
                    }
                },
                update = { container ->
                    if (fullscreenView.parent !== container) {
                        (fullscreenView.parent as? ViewGroup)?.removeView(fullscreenView)
                        container.removeAllViews()
                        container.addView(
                            fullscreenView,
                            FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            ),
                        )
                    }
                },
                onRelease = { container -> container.removeAllViews() },
            )
        }
    }
}

/**
 * Every web player ultimately drives an HTML5 <video>. Poll it (and any same-origin iframe's)
 * every 5 s while playing and report position/duration to the Kotlin bridge. Cross-origin
 * iframes are unreachable by design — those hosts simply won't report progress.
 */
private val PROGRESS_POLL_JS = """
    (function() {
      if (window.__aniliProgressHooked) return;
      window.__aniliProgressHooked = true;
      function findVideo() {
        var v = document.querySelector('video');
        if (v) return v;
        var frames = document.querySelectorAll('iframe');
        for (var i = 0; i < frames.length; i++) {
          try {
            var d = frames[i].contentDocument;
            if (d) {
              var fv = d.querySelector('video');
              if (fv) return fv;
            }
          } catch (e) { /* cross-origin */ }
        }
        return null;
      }
      setInterval(function() {
        try {
          var v = findVideo();
          if (v && !v.paused && isFinite(v.duration) && v.duration > 0 && v.currentTime > 0) {
            AniliProgress.onTick(v.currentTime, v.duration);
          }
        } catch (e) { /* bridge detached */ }
      }, 5000);
    })();
""".trimIndent()

private val PAUSE_VIDEO_JS = """
    (function() {
      function pauseVideos(root) {
        var videos = root.querySelectorAll('video');
        for (var i = 0; i < videos.length; i++) {
          videos[i].pause();
        }
      }
      try {
        pauseVideos(document);
        var frames = document.querySelectorAll('iframe');
        for (var i = 0; i < frames.length; i++) {
          try {
            var d = frames[i].contentDocument;
            if (d) pauseVideos(d);
          } catch (e) { /* cross-origin */ }
        }
      } catch (e) { /* ignored */ }
    })();
""".trimIndent()

private tailrec fun Context.findLifecycleOwner(): LifecycleOwner? = when (this) {
    is LifecycleOwner -> this
    is ContextWrapper -> baseContext.findLifecycleOwner()
    else -> null
}
