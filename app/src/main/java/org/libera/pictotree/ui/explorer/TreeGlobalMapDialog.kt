package org.libera.pictotree.ui.explorer

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageButton
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.libera.pictotree.R
import org.libera.pictotree.data.database.AppDatabase
import org.libera.pictotree.utils.WebViewImageInterceptor
import org.json.JSONObject

class TreeGlobalMapDialog : DialogFragment() {

    private var treeIds: IntArray = intArrayOf()
    private var currentIndex: Int = -1
    private var username: String = ""
    private var initialSelectedNodeId: String = ""

    private val selectedNodesPerTree = mutableMapOf<Int, String>()
    private var globalSelectedTreeId: Int = -1
    private var globalSelectedNodeId: String = ""

    var onNodeSelectedListener: ((Int, String) -> Unit)? = null
    var onAddToBasketListener: ((Int, String) -> Unit)? = null

    private lateinit var webView: WebView

    companion object {
        private const val TAG = "PictoTreeNav"

        fun newInstance(treeIds: IntArray, currentTreeId: Int, username: String, selectedNodeId: String): TreeGlobalMapDialog {
            val dialog = TreeGlobalMapDialog()
            dialog.treeIds = treeIds
            dialog.currentIndex = treeIds.indexOf(currentTreeId)
            if (dialog.currentIndex == -1 && treeIds.isNotEmpty()) dialog.currentIndex = 0
            dialog.username = username
            dialog.initialSelectedNodeId = selectedNodeId
            dialog.globalSelectedTreeId = currentTreeId
            dialog.globalSelectedNodeId = selectedNodeId
            if (currentTreeId != -1) dialog.selectedNodesPerTree[currentTreeId] = selectedNodeId
            return dialog
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, android.R.style.Theme_DeviceDefault_Light_NoActionBar_Fullscreen)
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        Log.d(TAG, "VIEW_CHANGE: Treant Dialog Dismissed")
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val root = inflater.inflate(R.layout.dialog_tree_global_map, container, false)
        webView = root.findViewById(R.id.web_view_map)

        val database = AppDatabase.getDatabase(requireContext(), username)
        val treeDao = database.treeDao()
        val imageDao = database.imageDao()

        val bridge = object {
            @JavascriptInterface
            fun onNodeSelected(prefixedNodeId: String) {
                val treeId = prefixedNodeId.split("_").firstOrNull()?.toIntOrNull() ?: return
                
                // UNICITÉ GLOBALE : On vide les sélections des autres arbres
                selectedNodesPerTree.clear()
                
                selectedNodesPerTree[treeId] = prefixedNodeId
                globalSelectedTreeId = treeId
                globalSelectedNodeId = prefixedNodeId
                Log.d(TAG, "TREANT_SELECT: Global target updated to Tree $treeId, Node $prefixedNodeId")
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

        fun loadTree(index: Int) {
            if (treeIds.isEmpty() || index !in treeIds.indices) return
            val treeId = treeIds[index]
            lifecycleScope.launch(Dispatchers.IO) {
                treeDao.getTreeById(treeId)?.let { entity ->
                    withContext(Dispatchers.Main) {
                        val selectedNodeId = selectedNodesPerTree[treeId] ?: ""
                        val safeJson = android.util.Base64.encodeToString(entity.jsonPayload.toByteArray(), android.util.Base64.NO_WRAP)
                        webView.evaluateJavascript("javascript:renderTreeBase64('$safeJson', '$selectedNodeId', false, $treeId);", null)
                    }
                }
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                if (currentIndex != -1) loadTree(currentIndex)
            }
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                return WebViewImageInterceptor.intercept(requireContext(), username, imageDao, request?.url)
            }
        }

        webView.loadUrl("file:///android_asset/tree_viewer.html")

        root.findViewById<ImageButton>(R.id.btn_prev_tree).setOnClickListener {
            if (currentIndex > 0) { currentIndex--; loadTree(currentIndex) }
        }
        root.findViewById<ImageButton>(R.id.btn_next_tree).setOnClickListener {
            if (currentIndex < treeIds.size - 1) { currentIndex++; loadTree(currentIndex) }
        }

        root.findViewById<View>(R.id.btn_add_to_basket).setOnClickListener {
            val currentTreeId = if (currentIndex in treeIds.indices) treeIds[currentIndex] else -1
            val visibleSelection = selectedNodesPerTree[currentTreeId]
            if (currentTreeId != -1) {
                if (!visibleSelection.isNullOrEmpty()) {
                    onAddToBasketListener?.invoke(currentTreeId, visibleSelection)
                } else {
                    lifecycleScope.launch(Dispatchers.IO) {
                        val treeEntity = treeDao.getTreeById(currentTreeId)
                        val rawId = treeEntity?.let { extractRootIdFromJson(it.jsonPayload) } ?: ""
                        val fallbackId = "${currentTreeId}_${rawId}_r"
                        withContext(Dispatchers.Main) { onAddToBasketListener?.invoke(currentTreeId, fallbackId) }
                    }
                }
            }
        }

        root.findViewById<View>(R.id.btn_back_to_nav).setOnClickListener {
            Log.d(TAG, "VIEW_CHANGE: Returning to Nav. Tree $globalSelectedTreeId, Node $globalSelectedNodeId")
            if (globalSelectedTreeId != -1 && globalSelectedNodeId.isNotEmpty()) {
                onNodeSelectedListener?.invoke(globalSelectedTreeId, globalSelectedNodeId)
            }
            dismiss()
        }

        return root
    }

    private fun extractRootIdFromJson(jsonPayload: String): String {
        return try {
            val json = JSONObject(jsonPayload)
            val root = if (json.has("root_node")) json.getJSONObject("root_node")
            else if (json.has("roots") && json.getJSONArray("roots").length() > 0) json.getJSONArray("roots").getJSONObject(0)
            else null
            root?.optString("node_id", root.optString("id")) ?: ""
        } catch (e: Exception) { "" }
    }
}
