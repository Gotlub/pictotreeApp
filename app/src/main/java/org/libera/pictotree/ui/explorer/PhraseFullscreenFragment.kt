package org.libera.pictotree.ui.explorer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch
import org.libera.pictotree.R
import org.libera.pictotree.data.SessionManager
import org.libera.pictotree.data.database.AppDatabase
import org.libera.pictotree.data.database.entity.UserConfig
import org.libera.pictotree.network.RetrofitClient
import org.libera.pictotree.utils.TTSManager

import androidx.fragment.app.DialogFragment

class PhraseFullscreenFragment : DialogFragment() {

    private lateinit var viewModel: TreeExplorerViewModel
    private lateinit var ttsManager: TTSManager
    private lateinit var adapter: PhraseAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, android.R.style.Theme_DeviceDefault_Light_NoActionBar_Fullscreen)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        // Forcer le paysage pour le bandeau de phrase
        requireActivity().requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        
        val root = inflater.inflate(R.layout.fragment_phrase_fullscreen, container, false)

        val username = SessionManager(requireContext()).getUsername() ?: "dummy"
        val database = AppDatabase.getDatabase(requireContext(), username)
        val treeDao = database.treeDao()
        val userConfigRepository = org.libera.pictotree.data.repository.UserConfigRepository(database.userConfigDao())

        val factory = object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return TreeExplorerViewModel(
                    requireActivity().application, 
                    treeDao, 
                    userConfigRepository,
                    RetrofitClient.SERVER_URL, 
                    username
                ) as T
            }
        }
        // Share ViewModel at Activity level
        viewModel = ViewModelProvider(requireActivity(), factory)[TreeExplorerViewModel::class.java]
        
        ttsManager = TTSManager(requireContext())
        
        // Observer la langue depuis le ViewModel (Cache réactif)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.userConfig.collect { config ->
                    config?.let { ttsManager.setLanguage(it.locale) }
                }
            }
        }
        
        setupUI(root)
        observeViewModel()
        
        return root
    }

    private fun setupUI(root: View) {
        val rv = root.findViewById<RecyclerView>(R.id.rv_phrase_fullscreen)
        val btnClose = root.findViewById<ImageButton>(R.id.btn_close_fullscreen)
        val fabSpeak = root.findViewById<FloatingActionButton>(R.id.fab_speak_fullscreen)

        adapter = PhraseAdapter(R.layout.item_phrase_picto_fullscreen)
        rv.adapter = adapter
        rv.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)

        btnClose.setOnClickListener {
            dismiss()
        }

        ttsManager.setListeners(
            onStart = { utteranceId ->
                val index = utteranceId.toIntOrNull() ?: -1
                requireActivity().runOnUiThread {
                    if (index != -1) {
                        adapter.highlightPosition(index)
                        rv.smoothScrollToPosition(index)
                    }
                }
            },
            onDone = { utteranceId ->
                val index = utteranceId.toIntOrNull() ?: -1
                val totalItems = adapter.itemCount
                if (index == totalItems - 1) {
                    requireActivity().runOnUiThread {
                        adapter.highlightPosition(-1)
                    }
                }
            }
        )

        fabSpeak.setOnClickListener {
            val phrase = viewModel.phraseList.value
            if (phrase.isEmpty()) return@setOnClickListener
            ttsManager.stop()
            phrase.forEachIndexed { index, node ->
                ttsManager.speak(node.label, index.toString())
            }
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.phraseList.collect { phrase ->
                    adapter.submitList(phrase)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Rétablir l'orientation automatique en sortant
        requireActivity().requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        ttsManager.shutdown()
    }
}
