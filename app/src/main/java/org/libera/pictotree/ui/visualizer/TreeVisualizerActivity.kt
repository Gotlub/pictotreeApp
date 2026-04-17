package org.libera.pictotree.ui.visualizer

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.libera.pictotree.data.SessionManager
import org.libera.pictotree.data.database.AppDatabase
import org.libera.pictotree.utils.WebViewImageInterceptor

class TreeVisualizerActivity : AppCompatActivity() {

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val webView = WebView(this)
        setContentView(webView)

        val username = SessionManager(this).getUsername() ?: return finish()
        val database = AppDatabase.getDatabase(this, username)
        val treeDao = database.treeDao()
        val imageDao = database.imageDao()

        webView.settings.apply {
            javaScriptEnabled = true
            allowFileAccess = true
            builtInZoomControls = true
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
        }

        val treeId = intent.getIntExtra("TREE_ID", -1)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                if (treeId != -1) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        treeDao.getTreeById(treeId)?.let { treeEntity ->
                            withContext(Dispatchers.Main) {
                                val safeJson = android.util.Base64.encodeToString(treeEntity.jsonPayload.toByteArray(), android.util.Base64.NO_WRAP)
                                webView.evaluateJavascript("javascript:renderTreeBase64('$safeJson', null, true, $treeId);", null)
                            }
                        }
                    }
                }
            }

            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                return WebViewImageInterceptor.intercept(this@TreeVisualizerActivity, username, imageDao, request?.url)
            }
        }

        webView.loadUrl("file:///android_asset/tree_viewer.html")
    }
}
