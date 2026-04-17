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
import android.widget.ImageButton
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.libera.pictotree.R
import org.libera.pictotree.data.database.AppDatabase
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream

class TreeGlobalMapDialog : DialogFragment() {

    private var treeIds: IntArray = intArrayOf()
    private var currentIndex: Int = -1
    private var username: String = ""
    private var initialSelectedNodeId: String = ""

    // Mémorise le noeud sélectionné (préfixé) pour chaque arbre pour éviter de "viser à côté"
    private val selectedNodesPerTree = mutableMapOf<Int, String>()

    // Callbacks. Passes treeId and nodeId (prefixed).
    var onNodeSelectedListener: ((Int, String) -> Unit)? = null
    var onAddToBasketListener: ((Int, String) -> Unit)? = null

    private lateinit var webView: WebView

    companion object {
        fun newInstance(treeIds: IntArray, currentTreeId: Int, username: String, selectedNodeId: String): TreeGlobalMapDialog {
            val dialog = TreeGlobalMapDialog()
            dialog.treeIds = treeIds
            dialog.currentIndex = treeIds.indexOf(currentTreeId)
            if (dialog.currentIndex == -1 && treeIds.isNotEmpty()) {
                dialog.currentIndex = 0
            }
            dialog.username = username
            dialog.initialSelectedNodeId = selectedNodeId
            
            // Initialiser la sélection pour l'arbre actuel (Format préfixé attendu)
            if (currentTreeId != -1) {
                dialog.selectedNodesPerTree[currentTreeId] = selectedNodeId
            }
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
    ): View? {
        val root = inflater.inflate(R.layout.dialog_tree_global_map, container, false)
        webView = root.findViewById(R.id.web_view_map)

        val database = AppDatabase.getDatabase(requireContext(), username)
        val treeDao = database.treeDao()
        val imageDao = database.imageDao()

        // Javascript Bridge
        val bridge = object {
            @JavascriptInterface
            fun onNodeSelected(prefixedNodeId: String) {
                // On extrait le treeId du préfixe pour être sûr de mémoriser au bon endroit
                val parts = prefixedNodeId.split("_", limit = 2)
                if (parts.size == 2) {
                    val treeId = parts[0].toIntOrNull()
                    if (treeId != null) {
                        selectedNodesPerTree[treeId] = prefixedNodeId
                    }
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

        // Load specific tree function
        fun loadTree(index: Int) {
            if (treeIds.isEmpty() || index < 0 || index >= treeIds.size) return
            val treeId = treeIds[index]
            
            lifecycleScope.launch(Dispatchers.IO) {
                val treeEntity = treeDao.getTreeById(treeId)
                if (treeEntity != null) {
                    withContext(Dispatchers.Main) {
                        // On ne force plus la racine ici, mais on l'utilisera comme fallback au retour
                        val selectedNodeId = selectedNodesPerTree[treeId] ?: ""
                        val jsonPayload = treeEntity.jsonPayload
                        val safeJson = android.util.Base64.encodeToString(jsonPayload.toByteArray(), android.util.Base64.NO_WRAP)
                        // On passe désormais le treeId au JS pour qu'il préfixe les IDs
                        webView.evaluateJavascript("javascript:renderTreeBase64('$safeJson', '$selectedNodeId', false, $treeId);", null)
                    }
                }
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (currentIndex != -1) {
                    loadTree(currentIndex)
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

        // Actions UI locales
        val btnPrevTree = root.findViewById<ImageButton>(R.id.btn_prev_tree)
        val btnNextTree = root.findViewById<ImageButton>(R.id.btn_next_tree)
        
        btnPrevTree.setOnClickListener {
            if (treeIds.isNotEmpty() && currentIndex > 0) {
                currentIndex--
                loadTree(currentIndex)
            }
        }
        
        btnNextTree.setOnClickListener {
            if (treeIds.isNotEmpty() && currentIndex < treeIds.size - 1) {
                currentIndex++
                loadTree(currentIndex)
            }
        }

        val btnAddToBasket = root.findViewById<View>(R.id.btn_add_to_basket)
        btnAddToBasket.setOnClickListener {
            val currentTreeId = if (treeIds.isNotEmpty() && currentIndex >= 0) treeIds[currentIndex] else -1
            val selectedNodeId = selectedNodesPerTree[currentTreeId]
            if (currentTreeId != -1 && !selectedNodeId.isNullOrEmpty()) {
                onAddToBasketListener?.invoke(currentTreeId, selectedNodeId)
            }
        }

        val btnBackToNav = root.findViewById<View>(R.id.btn_back_to_nav)
        btnBackToNav.setOnClickListener {
            val currentTreeId = if (treeIds.isNotEmpty() && currentIndex >= 0) treeIds[currentIndex] else -1
            var selectedNodeId = selectedNodesPerTree[currentTreeId]
            
            // Si l'utilisateur n'a rien sélectionné dans cet arbre, on vise la racine par défaut au retour
            if (selectedNodeId.isNullOrEmpty() && currentTreeId != -1) {
                lifecycleScope.launch(Dispatchers.IO) {
                    val treeEntity = treeDao.getTreeById(currentTreeId)
                    if (treeEntity != null) {
                        try {
                            val json = JSONObject(treeEntity.jsonPayload)
                            val rawId = if (json.has("root_node")) {
                                json.getJSONObject("root_node").optString("node_id", json.getJSONObject("root_node").optString("id"))
                            } else if (json.has("roots") && json.getJSONArray("roots").length() > 0) {
                                json.getJSONArray("roots").getJSONObject(0).optString("node_id", json.getJSONArray("roots").getJSONObject(0).optString("id"))
                            } else ""
                            
                            val fallbackId = "${currentTreeId}_$rawId"
                            
                            withContext(Dispatchers.Main) {
                                onNodeSelectedListener?.invoke(currentTreeId, fallbackId)
                                dismiss()
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) { dismiss() }
                        }
                    } else {
                        withContext(Dispatchers.Main) { dismiss() }
                    }
                }
            } else if (currentTreeId != -1 && !selectedNodeId.isNullOrEmpty()) {
                onNodeSelectedListener?.invoke(currentTreeId, selectedNodeId!!)
                dismiss()
            } else {
                dismiss()
            }
        }

        return root
    }
}
