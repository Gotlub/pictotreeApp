package org.libera.pictotree.ui.common

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.launch
import org.libera.pictotree.R
import org.libera.pictotree.data.SessionManager
import org.libera.pictotree.data.database.AppDatabase
import org.libera.pictotree.data.repository.ArasaacRepository
import org.libera.pictotree.network.RetrofitClient
import org.libera.pictotree.network.dto.PictoSearchResultDTO

class PictoSearchDialog : DialogFragment() {

    var onPictoSelected: ((PictoSearchResultDTO) -> Unit)? = null
    
    private lateinit var viewModel: PictoSearchViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val root = inflater.inflate(R.layout.dialog_picto_search, container, false)
        
        val sessionManager = SessionManager(requireContext())
        val username = sessionManager.getUsername() ?: "default"
        val database = AppDatabase.getDatabase(requireContext(), username)
        
        val factory = object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return PictoSearchViewModel(
                    requireActivity().application,
                    database.imageDao(),
                    RetrofitClient.treeApiService,
                    ArasaacRepository(),
                    sessionManager.getToken(),
                    username,
                    "http://10.0.2.2:5000"
                ) as T
            }
        }
        viewModel = ViewModelProvider(this, factory)[PictoSearchViewModel::class.java]

        setupUI(root)
        
        return root
    }

    private fun setupUI(root: View) {
        val etSearch = root.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etSearch)
        val tabLayout = root.findViewById<com.google.android.material.tabs.TabLayout>(R.id.tabLayout)
        val viewPager = root.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.viewPager)

        viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = 3
            override fun createFragment(position: Int): Fragment {
                return when(position) {
                    0 -> SearchTabFragment.newInstance(SearchTabFragment.TYPE_LOCAL)
                    1 -> SearchTabFragment.newInstance(SearchTabFragment.TYPE_BASE)
                    else -> SearchTabFragment.newInstance(SearchTabFragment.TYPE_ARASAAC)
                }
            }
        }

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when(position) {
                0 -> "Local"
                1 -> "PictoTree"
                else -> "Arasaac"
            }
        }.attach()

        etSearch.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                viewModel.search(v.text.toString())
                true
            } else false
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }
}
