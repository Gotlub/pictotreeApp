package org.libera.pictotree.ui.common

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
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
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvEmpty: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        type = arguments?.getInt(ARG_TYPE) ?: TYPE_LOCAL
        viewModel = ViewModelProvider(requireParentFragment())[PictoSearchViewModel::class.java]
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val root = inflater.inflate(R.layout.fragment_search_tab, container, false)
        
        recyclerView = root.findViewById(R.id.rvResults)
        progressBar = root.findViewById(R.id.progressBar)
        tvEmpty = root.findViewById(R.id.tvEmpty)
        
        adapter = SearchResultAdapter { selected ->
            (parentFragment as? PictoSearchDialog)?.onPictoSelected?.invoke(selected)
            (parentFragment as? PictoSearchDialog)?.dismiss()
        }
        
        val displayMetrics = resources.displayMetrics
        val screenWidthDp = displayMetrics.widthPixels / displayMetrics.density
        val spanCount = (screenWidthDp / 120).toInt().coerceAtLeast(3)
        
        recyclerView.layoutManager = GridLayoutManager(requireContext(), spanCount)
        recyclerView.adapter = adapter

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
                    is SearchUiState.Loading -> {
                        progressBar.visibility = View.VISIBLE
                        tvEmpty.visibility = View.GONE
                        adapter.submitList(emptyList())
                    }
                    is SearchUiState.Success -> {
                        progressBar.visibility = View.GONE
                        adapter.submitList(state.results)
                        tvEmpty.visibility = if (state.results.isEmpty()) View.VISIBLE else View.GONE
                    }
                    is SearchUiState.Error -> {
                        progressBar.visibility = View.GONE
                        tvEmpty.text = state.message.asString(requireContext())
                        tvEmpty.visibility = View.VISIBLE
                        adapter.submitList(emptyList())
                    }
                    is SearchUiState.Idle -> {
                        progressBar.visibility = View.GONE
                        tvEmpty.visibility = View.GONE
                        adapter.submitList(emptyList())
                    }
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
            tv.text = item.name ?: "Picto"
            
            // Sécurisation contre les URLs nulles et centralisation de la résolution de source d'image
            val displayUrl = item.thumbnailUrl ?: item.imageUrl ?: ""
            val hostUrl = org.libera.pictotree.network.RetrofitClient.SERVER_URL
            val sessionManager = org.libera.pictotree.data.SessionManager(iv.context)
            val username = sessionManager.getUsername() ?: "default"
            
            val imageSource = org.libera.pictotree.utils.FileUtils.getFinalImageSource(displayUrl, iv.context, username, hostUrl)
            val hasImage = when (imageSource) {
                is java.io.File -> true
                is String -> imageSource.isNotBlank()
                else -> false
            }
            
            if (hasImage) {
                val finalSource = if (imageSource is String) {
                    org.libera.pictotree.utils.FileUtils.normalizeServerAddress(imageSource)
                } else {
                    imageSource
                }
                val imageLoader = org.libera.pictotree.network.RetrofitClient.getImageLoader(iv.context)
                iv.load(finalSource, imageLoader) {
                    crossfade(true)
                    placeholder(R.drawable.ic_launcher_foreground)
                    error(R.drawable.ic_launcher_foreground)
                }
            } else {
                iv.setImageResource(R.drawable.ic_launcher_foreground)
            }
            
            itemView.setOnClickListener { onClick(item) }
        }
    }
}
