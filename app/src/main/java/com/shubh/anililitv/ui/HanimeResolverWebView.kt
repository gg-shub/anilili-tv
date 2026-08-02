package com.shubh.anililitv.ui

import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.shubh.anililitv.data.remote.HanimeBridge
import com.shubh.anililitv.diagnostics.CrashReporter
import com.shubh.anililitv.diagnostics.DiagnosticsLog

@Composable
fun HanimeResolverWebView() {
    AndroidView(
        factory = { ctx ->
            try {
                DiagnosticsLog.event("HanimeResolverWebView factory create WebView start")
                WebView(ctx).also {
                    it.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                    it.isFocusable = false
                    it.isClickable = false
                    it.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                    HanimeBridge.attach(it)
                    DiagnosticsLog.event("HanimeResolverWebView factory create WebView complete")
                }
            } catch (e: Throwable) {
                CrashReporter.logNonFatal("System WebView unavailable; hanime resolver disabled", e)
                View(ctx)
            }
        },
        onRelease = { view ->
            val web = view as? WebView ?: return@AndroidView
            DiagnosticsLog.event("HanimeResolverWebView release")
            HanimeBridge.detach(web)
            web.stopLoading()
            web.webChromeClient = null
            web.webViewClient = WebViewClient()
            web.loadUrl("about:blank")
            web.destroy()
        },
        modifier = Modifier.size(1.dp),
    )
}
