package org.libera.pictotree.ui.editprofile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import androidx.appcompat.widget.SwitchCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.libera.pictotree.R
import org.libera.pictotree.network.dto.TreeMetadataDTO

class TreeSelectionDialogFragment(
    private val remoteTreesFlow: StateFlow<List<TreeMetadataDTO>>,
    private val onSearchRequested: (String, Boolean) -> Unit,
    private val onLoadMoreRequested: () -> Unit,
    private val onTreeSelected: (TreeMetadataDTO) -> Unit
) : DialogFragment() {

    private var searchJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_tree_selection, container, false)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (resources.displayMetrics.heightPixels * 0.85).toInt()
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val recyclerView: RecyclerView = view.findViewById(R.id.recyclerViewRemoteTrees)
        val progressBar: ProgressBar = view.findViewById(R.id.progressBarRemoteTrees)
        val etSearch: TextInputEditText = view.findViewById(R.id.etSearchRemoteTrees)
        val switchPublic: SwitchCompat = view.findViewById(R.id.switchIsPublic)

        val adapter = RemoteTreeAdapter { tree ->
            onTreeSelected(tree)
            dismiss()
        }
        
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        // Handle Infinite Scroll
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val visibleItemCount = layoutManager.childCount
                val totalItemCount = layoutManager.itemCount
                val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount
                    && firstVisibleItemPosition >= 0) {
                    onLoadMoreRequested()
                }
            }
        })

        // Observer la liste depuis le ViewModel dynamiquement
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                remoteTreesFlow.collect { trees ->
                    adapter.submitList(trees)
                }
            }
        }

        // Ecouter les changements de texte avec Debounce manuel
        etSearch.doAfterTextChanged { editable ->
            searchJob?.cancel()
            val query = editable?.toString() ?: ""
            searchJob = viewLifecycleOwner.lifecycleScope.launch {
                delay(300) // Debounce 300ms
                onSearchRequested(query, switchPublic.isChecked)
            }
        }

        // Ecouter le switch
        switchPublic.setOnCheckedChangeListener { _, isChecked ->
            val text = if (isChecked) "Arbres Publics" else "Mes Arbres Privés"
            switchPublic.text = text
            
            val query = etSearch.text?.toString() ?: ""
            onSearchRequested(query, isChecked)
        }
    }
}
