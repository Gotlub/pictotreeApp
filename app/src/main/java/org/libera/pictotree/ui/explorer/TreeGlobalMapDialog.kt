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

    // Mémorise le noeud sélectionné pour chaque arbre (affichage visuel)
    private val selectedNodesPerTree = mutableMapOf<Int, String>()

    // CIBLE DE RETOUR : On mémorise l'ID et l'Arbre de la toute dernière sélection EXPLICITE
    private var globalSelectedTreeId: Int = -1
    private var globalSelectedNodeId: String = ""

    // Callbacks. Passes treeId and nodeId (prefixed).
    var onNodeSelectedListener: ((Int, String) -> Unit)? = null
    var onAddToBasketListener: ((Int, String) -> Unit)? = null

    private lateinit var webView: WebView

    companion object {
        private const val TAG = "PictoTreeNav"

        fun newInstance(treeIds: IntArray, currentTreeId: Int, username: String, selectedNodeId: String): TreeGlobalMapDialog {
            val dialog = TreeGlobalMapDialog()
            dialog.treeIds = treeIds
            dialog.currentIndex = treeIds.indexOf(currentTreeId)
            if (dialog.currentIndex == -1 && treeIds.isNotEmpty()) {
                dialog.currentIndex = 0
            }
            dialog.username = username
            dialog.initialSelectedNodeId = selectedNodeId
            
            // Initialisation de la sélection active (celle de la View 4)
            dialog.globalSelectedTreeId = currentTreeId
            dialog.globalSelectedNodeId = selectedNodeId
            if (currentTreeId != -1) {
                dialog.selectedNodesPerTree[currentTreeId] = selectedNodeId
            }
            
            Log.d(TAG, "VIEW_CHANGE: Treant Dialog Created. Target lock on Tree $currentTreeId, Node $selectedNodeId")
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
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.dialog_tree_global_map, container, false)
        webView = root.findViewById(R.id.web_view_map)

        val database = AppDatabase.getDatabase(requireContext(), username)
        val treeDao = database.treeDao()
        val imageDao = database.imageDao()

        val bridge = object {
            @JavascriptInterface
            fun onNodeSelected(prefixedNodeId: String) {
                Log.d(TAG, "TREANT_SELECT: Node clicked in JS: $prefixedNodeId")
                val parts = prefixedNodeId.split("_")
                if (parts.isNotEmpty()) {
                    val treeId = parts[0].toIntOrNull()
                    if (treeId != null) {
                        // On met à jour la sélection visuelle pour cet arbre
                        selectedNodesPerTree[treeId] = prefixedNodeId
                        
                        // ON MET À JOUR LE VERROU DE RETOUR : L'utilisateur a explicitement choisi ce noeud
                        globalSelectedTreeId = treeId
                        globalSelectedNodeId = prefixedNodeId
                        Log.d(TAG, "TREANT_SELECT: Return target updated to Tree $treeId, Node $prefixedNodeId")
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

        fun loadTree(index: Int) {
            if (treeIds.isEmpty() || index < 0 || index >= treeIds.size) return
            val treeId = treeIds[index]
            
            lifecycleScope.launch(Dispatchers.IO) {
                val treeEntity = treeDao.getTreeById(treeId)
                if (treeEntity != null) {
                    withContext(Dispatchers.Main) {
                        // On n'affiche la sélection que si elle appartient à cet arbre
                        val displayNodeId = selectedNodesPerTree[treeId] ?: ""
                        Log.d(TAG, "TREANT_LOAD: Rendering tree $treeId. Highlight: $displayNodeId")
                        val jsonPayload = treeEntity.jsonPayload
                        val safeJson = android.util.Base64.encodeToString(jsonPayload.toByteArray(), android.util.Base64.NO_WRAP)
                        webView.evaluateJavascript("javascript:renderTreeBase64('$safeJson', '$displayNodeId', false, $treeId);", null)
                    }
                }
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (currentIndex != -1) loadTree(currentIndex)
            }

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val urlString = request?.url?.toString() ?: return null
                if (urlString.contains("/api/v1/mobile/pictograms/") || urlString.startsWith("http")) {
                    var response: WebResourceResponse? = null
                    var cleanUrl = urlString.substringBefore("?")
                    cleanUrl = cleanUrl.replace(Regex("(\\.(jpg|jpeg|png|gif))\\d+$", RegexOption.IGNORE_CASE), "$1")
                    val relativePart = cleanUrl.substringAfter("/api/v1/mobile/pictograms/", "").substringAfter("/pictograms/", "")
                    
                    runBlocking {
                        var entity = imageDao.getImageByRemotePath(cleanUrl)
                        if (entity == null && relativePart.isNotEmpty()) {
                            entity = imageDao.getImageByRemotePath("/api/v1/mobile/pictograms/$relativePart")
                                ?: imageDao.getImageByRemotePath(relativePart)
                        }

                        if (entity != null) {
                            val localFile = File(requireContext().filesDir, "$username/${entity.localPath}")
                            if (localFile.exists()) {
                                try {
                                    val stream = FileInputStream(localFile)
                                    val mimeType = if (localFile.name.endsWith(".png", true)) "image/png" else "image/jpeg"
                                    response = WebResourceResponse(mimeType, "UTF-8", stream)
                                } catch (e: Exception) { e.printStackTrace() }
                            }
                        }
                    }
                    if (response != null) return response
                }
                return super.shouldInterceptRequest(view, request)
            }
        }

        webView.loadUrl("file:///android_asset/tree_viewer.html")

        val btnPrevTree = root.findViewById<ImageButton>(R.id.btn_prev_tree)
        val btnNextTree = root.findViewById<ImageButton>(R.id.btn_next_tree)
        
        btnPrevTree.setOnClickListener {
            if (treeIds.isNotEmpty() && currentIndex > 0) {
                currentIndex--
                Log.d(TAG, "TREANT_NAV: Previous Tree (Index $currentIndex)")
                loadTree(currentIndex)
            }
        }
        
        btnNextTree.setOnClickListener {
            if (treeIds.isNotEmpty() && currentIndex < treeIds.size - 1) {
                currentIndex++
                Log.d(TAG, "TREANT_NAV: Next Tree (Index $currentIndex)")
                loadTree(currentIndex)
            }
        }

        val btnAddToBasket = root.findViewById<View>(R.id.btn_add_to_basket)
        btnAddToBasket.setOnClickListener {
            // Pour l'ajout au panier, on privilégie la sélection VISIBLE ou la racine de l'arbre visible
            val currentTreeId = if (treeIds.isNotEmpty() && currentIndex >= 0) treeIds[currentIndex] else -1
            val visibleSelection = selectedNodesPerTree[currentTreeId]
            Log.d(TAG, "ACTION: Basket Click -> visible selection: $visibleSelection")
            
            if (currentTreeId != -1) {
                if (!visibleSelection.isNullOrEmpty()) {
                    onAddToBasketListener?.invoke(currentTreeId, visibleSelection)
                } else {
                    // Fallback sur la racine de l'arbre affiché
                    lifecycleScope.launch(Dispatchers.IO) {
                        val treeEntity = treeDao.getTreeById(currentTreeId)
                        val rawId = treeEntity?.let { extractRootIdFromJson(it.jsonPayload) } ?: ""
                        val fallbackId = "${currentTreeId}_${rawId}_r"
                        withContext(Dispatchers.Main) { onAddToBasketListener?.invoke(currentTreeId, fallbackId) }
                    }
                }
            }
        }

        val btnBackToNav = root.findViewById<View>(R.id.btn_back_to_nav)
        btnBackToNav.setOnClickListener {
            // COMPORTEMENT ATTENDU : On retourne sur le DERNIER NOEUD SÉLECTIONNÉ EXPLICITEMENT
            // même si l'utilisateur est en train de regarder un autre arbre (currentIndex).
            Log.d(TAG, "VIEW_CHANGE: Returning to Nav. Locked on Tree $globalSelectedTreeId, Node $globalSelectedNodeId")
            
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
            if (json.has("root_node")) {
                json.getJSONObject("root_node").optString("node_id", json.getJSONObject("root_node").optString("id"))
            } else if (json.has("roots") && json.getJSONArray("roots").length() > 0) {
                json.getJSONArray("roots").getJSONObject(0).optString("node_id", json.getJSONArray("roots").getJSONObject(0).optString("id"))
            } else ""
        } catch (e: Exception) { "" }
    }
}
