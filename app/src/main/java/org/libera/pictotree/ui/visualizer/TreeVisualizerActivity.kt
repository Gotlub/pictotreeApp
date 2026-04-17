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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.libera.pictotree.data.SessionManager
import org.libera.pictotree.data.database.AppDatabase
import java.io.File
import java.io.FileInputStream

class TreeVisualizerActivity : AppCompatActivity() {

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val webView = WebView(this)
        setContentView(webView)

        val username = SessionManager(this).getUsername()
        if (username == null) {
            finish()
            return
        }

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
                super.onPageFinished(view, url)
                // Quand Treant.js est chargé, on injecte l'arbre JSON
                if (treeId != -1) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        val treeEntity = treeDao.getTreeById(treeId)
                        if (treeEntity != null) {
                            withContext(Dispatchers.Main) {
                                // Envoi robuste via Base64 pour éviter de casser la syntaxe JS avec les quotes du JSON
                                val safeJson = android.util.Base64.encodeToString(treeEntity.jsonPayload.toByteArray(), android.util.Base64.NO_WRAP)
                                // Paramètre 3: readOnly = true pour l'édition de profil
                                // Paramètre 4: treeId pour le préfixage des IDs
                                webView.evaluateJavascript("javascript:renderTreeBase64('$safeJson', null, true, $treeId);", null)
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
                // Interception Magique: Dès que WebView demande une image distante (ex: arasaac),
                // on lui donne le fichier local SQLite s'il a été téléchargé pendant l'import!
                if (urlString.startsWith("http://") || urlString.startsWith("https://")) {
                    var response: WebResourceResponse? = null
                    
                    // Treant rajoute parfois dynamiquement des timestamps (?177893...) pour bypass le cache
                    // On les coupe pour que le chemin direct match à 100% avec notre SQLite !
                    val cleanUrl = urlString.substringBefore("?")
                    
                    runBlocking {
                        val entity = imageDao.getImageByRemotePath(cleanUrl)
                        if (entity != null) {
                            val localFile = File(filesDir, "$username/${entity.localPath}")
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
    }
}
