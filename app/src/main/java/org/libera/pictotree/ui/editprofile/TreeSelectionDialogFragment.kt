package org.libera.pictotree.ui.editprofile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.libera.pictotree.R
import org.libera.pictotree.network.dto.TreeMetadataDTO

class TreeSelectionDialogFragment(
    private val availableTrees: List<TreeMetadataDTO>,
    private val onTreeSelected: (TreeMetadataDTO) -> Unit
) : DialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_tree_selection, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val recyclerView: RecyclerView = view.findViewById(R.id.recyclerViewRemoteTrees)
        val progressBar: ProgressBar = view.findViewById(R.id.progressBarRemoteTrees)

        val adapter = RemoteTreeAdapter { tree ->
            onTreeSelected(tree)
            dismiss()
        }
        
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
        
        adapter.submitList(availableTrees)
    }
}
