package org.libera.pictotree.ui.explorer

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.libera.pictotree.data.SessionManager
import org.libera.pictotree.data.database.AppDatabase
import java.io.File
import java.io.FileInputStream

class TreeGlobalMapDialog : DialogFragment() {

    private var treeId: Int = -1
    private var username: String = ""
    var onNodeSelectedListener: ((String) -> Unit)? = null

    companion object {
        fun newInstance(treeId: Int, username: String): TreeGlobalMapDialog {
            val dialog = TreeGlobalMapDialog()
            dialog.treeId = treeId
            dialog.username = username
            return dialog
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, android.R.style.Theme_DeviceDefault_Light_NoActionBar_Fullscreen)
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val webView = WebView(requireContext())
        webView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        val database = AppDatabase.getDatabase(requireContext(), username)
        val treeDao = database.treeDao()
        val imageDao = database.imageDao()

        // Setup Bridge interface
        val bridge = object {
            @JavascriptInterface
            fun jumpToNode(nodeId: String) {
                // Call UI thread from bridge thread
                lifecycleScope.launch(Dispatchers.Main) {
                    onNodeSelectedListener?.invoke(nodeId)
                    dismiss()
                }
            }
        }

        webView.settings.apply {
            javaScriptEnabled = true
            allowFileAccess = true
            builtInZoomControls = true
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
        }

        webView.addJavascriptInterface(bridge, "AndroidBridge")

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (treeId != -1) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        val treeEntity = treeDao.getTreeById(treeId)
                        if (treeEntity != null) {
                            withContext(Dispatchers.Main) {
                                val jsonPayload = treeEntity.jsonPayload
                                val safeJson = android.util.Base64.encodeToString(jsonPayload.toByteArray(), android.util.Base64.NO_WRAP)
                                webView.evaluateJavascript("javascript:renderTreeBase64('$safeJson');", null)
                            }
                        }
                    }
                }
            }

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val urlString = request?.url?.toString() ?: return null
                if (urlString.startsWith("http://") || urlString.startsWith("https://")) {
                    var response: WebResourceResponse? = null
                    val cleanUrl = urlString.substringBefore("?")
                    
                    runBlocking {
                        val entity = imageDao.getImageByRemotePath(cleanUrl)
                        if (entity != null) {
                            val localFile = File(requireContext().filesDir, "$username/${entity.localPath}")
                            if (localFile.exists()) {
                                try {
                                    val stream = FileInputStream(localFile)
                                    val mimeType = if (localFile.name.endsWith(".png", true)) "image/png" else "image/jpeg"
                                    response = WebResourceResponse(mimeType, "UTF-8", stream)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                    }
                    if (response != null) return response
                }
                return super.shouldInterceptRequest(view, request)
            }
        }

        webView.loadUrl("file:///android_asset/tree_viewer.html")
        return webView
    }
}
