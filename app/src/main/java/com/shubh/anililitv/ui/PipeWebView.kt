package com.shubh.anililitv.ui

import android.webkit.WebView
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.layout.size
import com.shubh.anililitv.data.remote.PipeBridge
import com.shubh.anililitv.diagnostics.CrashReporter
import com.shubh.anililitv.diagnostics.DiagnosticsLog

@Composable
fun PipeWebView() {
    AndroidView(
        factory = { ctx ->
            try {
                DiagnosticsLog.event("PipeWebView factory create WebView start")
                DiagnosticsLog.webViewPackage("PipeWebView factory")
                WebView(ctx).also {
                    it.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                    it.isFocusable = false
                    it.isClickable = false
                    it.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                    PipeBridge.attach(it)
                    DiagnosticsLog.event("PipeWebView factory create WebView complete")
                }
            } catch (e: Throwable) {
                CrashReporter.logNonFatal("System WebView unavailable; pipe providers disabled", e)
                View(ctx)
            }
        },
        onRelease = { view ->
            val web = view as? WebView ?: return@AndroidView
            DiagnosticsLog.event("PipeWebView release url=${web.url ?: "none"} size=${web.width}x${web.height}")
            PipeBridge.detach(web)
            web.stopLoading()
            web.webChromeClient = null
            web.webViewClient = android.webkit.WebViewClient()
            web.destroy()
        },
        modifier = Modifier.size(1.dp),
    )
}
