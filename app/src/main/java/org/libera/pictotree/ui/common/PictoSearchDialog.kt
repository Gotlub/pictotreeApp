package org.libera.pictotree.ui.common

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayoutMediator
import org.libera.pictotree.R
import org.libera.pictotree.data.SessionManager
import org.libera.pictotree.data.database.AppDatabase
import org.libera.pictotree.data.repository.ArasaacRepository
import org.libera.pictotree.network.RetrofitClient
import org.libera.pictotree.network.dto.PictoSearchResultDTO
import org.libera.pictotree.utils.NetworkConnectivityObserver

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import org.libera.pictotree.utils.ConnectivityObserver

class PictoSearchDialog : DialogFragment() {

    var onPictoSelected: ((PictoSearchResultDTO) -> Unit)? = null
    private lateinit var viewModel: PictoSearchViewModel
    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: com.google.android.material.tabs.TabLayout
    private var isOnline: Boolean = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val root = inflater.inflate(R.layout.dialog_picto_search, container, false)
        
        val sessionManager = SessionManager(requireContext())
        val username = sessionManager.getUsername() ?: "default"
        val database = AppDatabase.getDatabase(requireContext(), username)
        val userConfigRepository = org.libera.pictotree.data.repository.UserConfigRepository(database.userConfigDao())
        
        // Nouvelle instance d'observateur de connectivité
        val connectivityObserver = NetworkConnectivityObserver(requireContext().applicationContext)

        val factory = object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return PictoSearchViewModel(
                    requireActivity().application,
                    database.imageDao(),
                    RetrofitClient.treeApiService,
                    ArasaacRepository(),
                    userConfigRepository,
                    connectivityObserver,
                    username,
                    RetrofitClient.SERVER_URL
                ) as T
            }
        }
        viewModel = ViewModelProvider(this, factory)[PictoSearchViewModel::class.java]

        setupUI(root)
        observeNetworkStatus()
        return root
    }

    private fun setupUI(root: View) {
        val etSearch = root.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etSearch)
        tabLayout = root.findViewById(R.id.tabLayout)
        viewPager = root.findViewById(R.id.viewPager)

        updateAdapter()

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                viewModel.search(position)
            }
        })

        etSearch.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                viewModel.updateQuery(v.text.toString())
                viewModel.search(viewPager.currentItem)
                true
            } else false
        }
    }

    private fun updateAdapter() {
        val adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = if (isOnline) 3 else 1
            override fun createFragment(position: Int): Fragment {
                return when(position) {
                    0 -> SearchTabFragment.newInstance(SearchTabFragment.TYPE_LOCAL)
                    1 -> SearchTabFragment.newInstance(SearchTabFragment.TYPE_BASE)
                    else -> SearchTabFragment.newInstance(SearchTabFragment.TYPE_ARASAAC)
                }
            }
        }
        viewPager.adapter = adapter

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when(position) {
                0 -> "Local"
                1 -> "PictoTree"
                else -> "Arasaac"
            }
        }.attach()
    }

    private fun observeNetworkStatus() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.networkStatus.collect { status ->
                    val wasOnline = isOnline
                    isOnline = status == ConnectivityObserver.Status.Available
                    if (wasOnline != isOnline) {
                        updateAdapter()
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val displayMetrics = resources.displayMetrics
        val width = if (displayMetrics.widthPixels > displayMetrics.heightPixels) {
            (displayMetrics.widthPixels * 0.9).toInt() // 90% de la largeur en paysage
        } else {
            ViewGroup.LayoutParams.MATCH_PARENT
        }
        val height = ViewGroup.LayoutParams.MATCH_PARENT
        dialog?.window?.setLayout(width, height)
    }
}

