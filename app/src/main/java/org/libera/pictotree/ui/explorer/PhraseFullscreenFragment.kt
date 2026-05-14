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
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.button.MaterialButtonToggleGroup
import kotlinx.coroutines.launch
import org.libera.pictotree.R
import org.libera.pictotree.data.SessionManager
import org.libera.pictotree.data.database.AppDatabase
import org.libera.pictotree.network.RetrofitClient
import org.libera.pictotree.utils.TTSManager
import androidx.fragment.app.DialogFragment

class PhraseFullscreenFragment : DialogFragment() {

    private lateinit var viewModel: TreeExplorerViewModel
    private lateinit var ttsManager: TTSManager
    private lateinit var adapter: PhraseAdapter
    private lateinit var rv: RecyclerView
    private var isDraggingPhrase = false
    private var lastClickTime = 0L // Debounce
    private var itemTouchHelper: ItemTouchHelper? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, android.R.style.Theme_DeviceDefault_Light_NoActionBar_Fullscreen)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        (requireActivity() as? org.libera.pictotree.MainActivity)?.disableOrientationLock()
        requireActivity().requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        
        val root = inflater.inflate(R.layout.fragment_phrase_fullscreen, container, false)

        val username = SessionManager(requireContext()).getUsername() ?: "dummy"
        val database = AppDatabase.getDatabase(requireContext(), username)
        val treeDao = database.treeDao()
        val profileDao = database.profileDao()
        val userConfigRepository = org.libera.pictotree.data.repository.UserConfigRepository(database.userConfigDao())

        val factory = object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return TreeExplorerViewModel(requireActivity().application, treeDao, profileDao, database.imageDao(), userConfigRepository, RetrofitClient.SERVER_URL, username) as T
            }
        }
        viewModel = ViewModelProvider(requireActivity(), factory)[TreeExplorerViewModel::class.java]
        ttsManager = TTSManager(requireContext())
        
        setupUI(root)
        observeViewModel()
        
        return root
    }

    private fun setupUI(root: View) {
        rv = root.findViewById(R.id.rv_phrase_fullscreen)
        val btnClose = root.findViewById<ImageButton>(R.id.btn_close_fullscreen)
        val fabSpeak = root.findViewById<FloatingActionButton>(R.id.fab_speak_fullscreen)
        val toggleSize = root.findViewById<MaterialButtonToggleGroup>(R.id.toggle_phrase_size)

        val username = SessionManager(requireContext()).getUsername() ?: "default"
        
        updateAdapterForSize(viewModel.uiState.value.phraseSize, username)

        val currentSize = viewModel.uiState.value.phraseSize
        val checkedBtnId = when(currentSize) {
            0 -> R.id.btn_size_s
            2 -> R.id.btn_size_l
            else -> R.id.btn_size_m
        }
        toggleSize.check(checkedBtnId)

        toggleSize.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked && canClick()) {
                val size = when(checkedId) {
                    R.id.btn_size_s -> 0
                    R.id.btn_size_l -> 2
                    else -> 1
                }
                viewModel.updatePhraseSize(size)
            }
        }

        btnClose.setOnClickListener { if (canClick()) dismiss() }

        ttsManager.setListeners(
            onStart = { utteranceId ->
                val index = utteranceId.toIntOrNull() ?: -1
                requireActivity().runOnUiThread { if (index != -1) { adapter.highlightPosition(index); rv.smoothScrollToPosition(index) } }
            },
            onDone = { utteranceId ->
                val index = utteranceId.toIntOrNull() ?: -1
                if (index == adapter.itemCount - 1) requireActivity().runOnUiThread { adapter.highlightPosition(-1) }
            }
        )

        fabSpeak.setOnClickListener {
            if (canClick()) {
                val phrase = viewModel.phraseList.value
                if (phrase.isEmpty()) return@setOnClickListener
                ttsManager.stop()
                phrase.forEachIndexed { index, node -> ttsManager.speak(node.label, index.toString()) }
            }
        }
    }

    private fun canClick(): Boolean {
        val now = System.currentTimeMillis()
        val debounce = viewModel.settings.value.clickDebounceMs
        if (now - lastClickTime < debounce) return false
        lastClickTime = now
        return true
    }

    private fun updateAdapterForSize(size: Int, username: String) {
        val layoutRes = when(size) {
            0 -> R.layout.item_phrase_picto_small
            1 -> R.layout.item_phrase_picto_medium
            else -> R.layout.item_phrase_picto_large
        }
        
        adapter = PhraseAdapter(username, layoutRes, onItemClick = { node -> 
            if (canClick()) ttsManager.speak(node.label) 
        })
        rv.adapter = adapter
        rv.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        adapter.submitList(viewModel.phraseList.value)

        itemTouchHelper?.attachToRecyclerView(null)
        itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT, ItemTouchHelper.UP
        ) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                adapter.moveItem(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
                return true
            }
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                if (direction == ItemTouchHelper.UP) viewModel.removeItemFromPhrase(viewHolder.bindingAdapterPosition)
            }
            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    isDraggingPhrase = true
                    val elevationPx = 50 * viewHolder!!.itemView.resources.displayMetrics.density
                    viewHolder.itemView?.apply { alpha = 0.8f; scaleX = 1.15f; scaleY = 1.15f; elevation = elevationPx }
                }
            }
            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                isDraggingPhrase = false
                viewHolder.itemView.apply { alpha = 1.0f; scaleX = 1.0f; scaleY = 1.0f; elevation = 0f }
                viewModel.updatePhraseListSilently(adapter.getCurrentList().toList())
            }
        })
        itemTouchHelper?.attachToRecyclerView(rv)
    }

    private fun observeViewModel() {
        val username = SessionManager(requireContext()).getUsername() ?: "default"
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.phraseList.collect { phrase -> if (!isDraggingPhrase) adapter.submitList(phrase) }
                }
                launch {
                    viewModel.uiState.collect { state ->
                        val targetLayout = when(state.phraseSize) {
                            0 -> R.layout.item_phrase_picto_small
                            1 -> R.layout.item_phrase_picto_medium
                            else -> R.layout.item_phrase_picto_large
                        }
                        if (adapter.layoutId != targetLayout) updateAdapterForSize(state.phraseSize, username)
                    }
                }
                launch {
                    viewModel.userConfig.collect { config -> config?.let { ttsManager.setLanguage(it.locale) } }
                }
                launch {
                    viewModel.settings.collect { settings -> ttsManager.setSpeed(settings.ttsSpeed) }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        val mainActivity = requireActivity() as? org.libera.pictotree.MainActivity
        mainActivity?.enableOrientationLock()
        mainActivity?.applyUserOrientation()
        ttsManager.shutdown()
    }
}
