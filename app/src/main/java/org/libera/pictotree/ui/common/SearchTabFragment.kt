package org.libera.pictotree.ui.common

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import kotlinx.coroutines.launch
import org.libera.pictotree.R
import org.libera.pictotree.network.dto.PictoSearchResultDTO

class SearchTabFragment : Fragment() {

    companion object {
        const val TYPE_LOCAL = 0
        const val TYPE_BASE = 1
        const val TYPE_ARASAAC = 2
        private const val ARG_TYPE = "type"

        fun newInstance(type: Int) = SearchTabFragment().apply {
            arguments = Bundle().apply { putInt(ARG_TYPE, type) }
        }
    }

    private var type: Int = TYPE_LOCAL
    private lateinit var viewModel: PictoSearchViewModel
    private lateinit var adapter: SearchResultAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        type = arguments?.getInt(ARG_TYPE) ?: TYPE_LOCAL
        // Shared ViewModel with parent PictoSearchDialog
        viewModel = ViewModelProvider(requireParentFragment())[PictoSearchViewModel::class.java]
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val root = RecyclerView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setPadding(16, 16, 16, 16)
            clipToPadding = false
        }
        
        adapter = SearchResultAdapter { selected ->
            (parentFragment as? PictoSearchDialog)?.onPictoSelected?.invoke(selected)
            (parentFragment as? PictoSearchDialog)?.dismiss()
        }
        root.layoutManager = GridLayoutManager(requireContext(), 3)
        root.adapter = adapter

        observeViewModel()
        
        return root
    }

    private fun observeViewModel() {
        val resultsFlow = when(type) {
            TYPE_LOCAL -> viewModel.localResults
            TYPE_BASE -> viewModel.baseResults
            else -> viewModel.arasaacResults
        }

        viewLifecycleOwner.lifecycleScope.launch {
            resultsFlow.collect { state ->
                when(state) {
                    is SearchUiState.Success -> adapter.submitList(state.results)
                    else -> adapter.submitList(emptyList())
                }
            }
        }
    }
}

class SearchResultAdapter(private val onClick: (PictoSearchResultDTO) -> Unit) : RecyclerView.Adapter<SearchResultAdapter.ViewHolder>() {
    private var items = emptyList<PictoSearchResultDTO>()

    fun submitList(newItems: List<PictoSearchResultDTO>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_search_picto, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(items[position])
    override fun getItemCount() = items.size

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val iv = view.findViewById<ImageView>(R.id.iv_picto)
        private val tv = view.findViewById<TextView>(R.id.tv_label)

        fun bind(item: PictoSearchResultDTO) {
            tv.text = item.name
            iv.load(item.imageUrl) {
                crossfade(true)
                placeholder(R.drawable.ic_launcher_foreground)
            }
            itemView.setOnClickListener { onClick(item) }
        }
    }
}
