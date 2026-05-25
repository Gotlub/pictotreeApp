package org.libera.pictotree.ui.explorer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.RadioGroup
import android.widget.Toast
import android.graphics.Color
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
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
import com.google.android.material.slider.Slider
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch
import org.libera.pictotree.R
import org.libera.pictotree.data.SessionManager
import org.libera.pictotree.data.database.AppDatabase
import org.libera.pictotree.data.model.CardTimeConfig
import org.libera.pictotree.data.model.TimeMode
import org.libera.pictotree.network.RetrofitClient
import org.libera.pictotree.utils.TTSManager
import androidx.navigation.fragment.findNavController
import android.content.pm.ActivityInfo

/**
 * Fragment pour l'affichage plein écran du bandeau de phrase.
 * Utilisé pour la configuration et la visualisation du Time Timer.
 * Forcé en mode Paysage et 100% opaque pour éviter les stimuli visuels.
 */
class PhraseFullscreenFragment : Fragment() {

    private lateinit var viewModel: TreeExplorerViewModel
    private lateinit var ttsManager: TTSManager
    private lateinit var adapter: PhraseAdapter
    private lateinit var rv: RecyclerView
    private var isDraggingPhrase = false
    private var itemTouchHelper: ItemTouchHelper? = null

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var cardClockMode: MaterialCardView
    private lateinit var cardPlayStopTimer: MaterialCardView
    private lateinit var ivPlayStopTimer: ImageView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val root = inflater.inflate(R.layout.fragment_phrase_fullscreen, container, false)

        val username = SessionManager(requireContext()).getUsername() ?: "dummy"
        val database = AppDatabase.getDatabase(requireContext(), username)
        
        val factory = object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return TreeExplorerViewModel(
                    requireActivity().application, 
                    database.treeDao(), 
                    database.profileDao(), 
                    database.imageDao(), 
                    org.libera.pictotree.data.repository.UserConfigRepository(database.userConfigDao()), 
                    RetrofitClient.SERVER_URL, 
                    username
                ) as T
            }
        }
        viewModel = ViewModelProvider(requireActivity(), factory)[TreeExplorerViewModel::class.java]
        ttsManager = TTSManager(requireContext())
        
        setupUI(root)
        setupTimerConfigPanel(root)
        observeViewModel()
        
        return root
    }

    private fun setupUI(root: View) {
        drawerLayout = root.findViewById(R.id.drawer_layout_fullscreen)
        drawerLayout.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerClosed(drawerView: View) {
                viewModel.selectedIndexForConfig.value = null
            }
        })
        rv = root.findViewById(R.id.rv_phrase_fullscreen)
        val btnClose = root.findViewById<ImageButton>(R.id.btn_close_fullscreen)
        val fabSpeak = root.findViewById<FloatingActionButton>(R.id.fab_speak_fullscreen)
        val toggleSize = root.findViewById<MaterialButtonToggleGroup>(R.id.toggle_phrase_size)
        cardClockMode = root.findViewById(R.id.card_clock_mode_fullscreen)

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
            if (isChecked) {
                val size = when(checkedId) {
                    R.id.btn_size_s -> 0
                    R.id.btn_size_l -> 2
                    else -> 1
                }
                viewModel.updatePhraseSize(size)
            }
        }

        // Utiliser popBackStack pour quitter le mode plein écran
        btnClose.setOnClickListener { findNavController().popBackStack() }

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
            val phrase = viewModel.phraseList.value
            if (phrase.isEmpty()) return@setOnClickListener
            ttsManager.stop()
            phrase.forEachIndexed { index, card -> ttsManager.speak(card.node.label, index.toString()) }
        }

        cardClockMode.setOnClickListener {
            val newState = !viewModel.isClockModeActive.value
            viewModel.isClockModeActive.value = newState
            if (!newState) drawerLayout.closeDrawer(GravityCompat.END)
        }

        cardPlayStopTimer = root.findViewById(R.id.card_play_stop_timer_fullscreen)
        ivPlayStopTimer = root.findViewById(R.id.iv_play_stop_timer)
        cardPlayStopTimer.setOnClickListener {
            val isTimerRunning = viewModel.isTimerActivated.value
            if (isTimerRunning) {
                viewModel.stopAllTimers()
            } else {
                viewModel.isClockModeActive.value = false
                viewModel.isTimerActivated.value = true
                viewModel.startTimerForFirstCard()
            }
        }
    }

    private fun setupTimerConfigPanel(root: View) {
        val configPanel = root.findViewById<View>(R.id.include_timer_config_fullscreen)
        val rgMode = configPanel.findViewById<RadioGroup>(R.id.rg_time_mode)
        
        val etDuration = configPanel.findViewById<android.widget.EditText>(R.id.et_duration)
        val btnMinus = configPanel.findViewById<View>(R.id.btn_duration_minus)
        val btnPlus = configPanel.findViewById<View>(R.id.btn_duration_plus)
        val tvLabelDuration = configPanel.findViewById<View>(R.id.tv_label_duration)
        val layoutDurationContainer = configPanel.findViewById<View>(R.id.layout_duration_container)
        
        val tvSelectedPictoName = configPanel.findViewById<android.widget.TextView>(R.id.tv_selected_picto_name)
        val swSound = configPanel.findViewById<MaterialSwitch>(R.id.switch_play_sound)
        val swAutoRemove = configPanel.findViewById<MaterialSwitch>(R.id.switch_auto_remove)
        val swVisualPulse = configPanel.findViewById<MaterialSwitch>(R.id.switch_visual_pulse)
        val btnApply = configPanel.findViewById<Button>(R.id.btn_apply_time_config)

        // Masquer l'option 'None' qui est désormais retirée
        configPanel.findViewById<View>(R.id.rb_mode_none)?.visibility = View.GONE

        fun updateConfigPanelUi(mode: TimeMode) {
            if (mode == TimeMode.TIMER) {
                val timerColorStr = org.libera.pictotree.data.SessionManager(requireContext()).getTimerColor()
                val colorHex = when (timerColorStr) {
                    "green" -> "#E8F5E9" // Vert doux
                    "blue" -> "#E3F2FD"  // Bleu doux
                    else -> "#FFEBEE"    // Rouge doux (par défaut)
                }
                configPanel.setBackgroundColor(Color.parseColor(colorHex))
                swSound.isEnabled = true
                swAutoRemove.isEnabled = true
                swVisualPulse.isEnabled = true
                tvLabelDuration.visibility = View.VISIBLE
                layoutDurationContainer.visibility = View.VISIBLE
            } else {
                configPanel.setBackgroundColor(Color.parseColor("#E3F2FD")) // Bleu doux (Jalon par défaut)
                swSound.isEnabled = false
                swAutoRemove.isEnabled = false
                swVisualPulse.isEnabled = false
                swVisualPulse.isChecked = false
                tvLabelDuration.visibility = View.GONE
                layoutDurationContainer.visibility = View.GONE
            }
        }

        btnMinus.setOnClickListener {
            val current = etDuration.text.toString().toIntOrNull() ?: 1
            if (current > 1) {
                etDuration.setText((current - 1).toString())
            }
        }

        btnPlus.setOnClickListener {
            val current = etDuration.text.toString().toIntOrNull() ?: 1
            if (current < 60) {
                etDuration.setText((current + 1).toString())
            }
        }

        rgMode.setOnCheckedChangeListener { _, checkedId ->
            val mode = if (checkedId == R.id.rb_mode_timer) TimeMode.TIMER else TimeMode.JALON
            updateConfigPanelUi(mode)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.selectedIndexForConfig.collect { index ->
                    adapter.selectedConfigIndex = index
                    if (index != null && index in viewModel.phraseList.value.indices) {
                        val card = viewModel.phraseList.value[index]
                        tvSelectedPictoName?.text = "Pictogramme : ${card.node.label}"
                        val config = card.timeConfig
                        val targetMode = if (config.mode == TimeMode.NONE) TimeMode.JALON else config.mode
                        when(targetMode) {
                            TimeMode.TIMER -> rgMode.check(R.id.rb_mode_timer)
                            else -> rgMode.check(R.id.rb_mode_jalon)
                        }
                        etDuration.setText(config.durationMinutes.coerceIn(1, 60).toString())
                        swSound.isChecked = config.playSoundAtEnd
                        swAutoRemove.isChecked = config.autoRemove
                        swVisualPulse.isChecked = config.visualPulse
                        updateConfigPanelUi(targetMode)
                    } else {
                        tvSelectedPictoName?.text = "Pictogramme : Aucun"
                    }
                }
            }
        }

        btnApply.setOnClickListener {
            val index = viewModel.selectedIndexForConfig.value ?: return@setOnClickListener
            val mode = when(rgMode.checkedRadioButtonId) {
                R.id.rb_mode_timer -> TimeMode.TIMER
                else -> TimeMode.JALON
            }
            val inputMinutes = etDuration.text.toString().toIntOrNull() ?: 1
            val coercedMinutes = inputMinutes.coerceIn(1, 60)
            
            val newConfig = CardTimeConfig(
                mode = mode,
                durationMinutes = coercedMinutes,
                playSoundAtEnd = if (mode == TimeMode.TIMER) swSound.isChecked else false,
                repeatSound = false,
                autoRemove = if (mode == TimeMode.TIMER) swAutoRemove.isChecked else false,
                visualPulse = if (mode == TimeMode.TIMER) swVisualPulse.isChecked else false
            )
            viewModel.updateCardTimeConfig(index, newConfig)
            drawerLayout.closeDrawer(GravityCompat.END)
            Toast.makeText(requireContext(), "Configuration appliquée", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateAdapterForSize(size: Int, username: String) {
        val layoutRes = when(size) {
            0 -> R.layout.item_phrase_picto_small
            1 -> R.layout.item_phrase_picto_medium
            else -> R.layout.item_phrase_picto_large
        }
        adapter = PhraseAdapter(username, layoutRes, onItemClick = { position -> 
            if (viewModel.isClockModeActive.value) {
                viewModel.selectedIndexForConfig.value = position
                drawerLayout.openDrawer(GravityCompat.END)
                
                val layoutManager = rv.layoutManager as? LinearLayoutManager
                if (layoutManager != null) {
                    val view = layoutManager.findViewByPosition(position)
                    if (view != null) {
                        val cardWidthPx = view.width
                        val currentLeft = view.left
                        val density = rv.resources.displayMetrics.density
                        val drawerWidthPx = 300 * density
                        val marginPx = 10 * density
                        val targetLeftScreenCoordinate = rv.width - drawerWidthPx - marginPx - cardWidthPx
                        val dx = (currentLeft - targetLeftScreenCoordinate).toInt()
                        rv.smoothScrollBy(dx, 0)
                    }
                }
            } else {
                val card = adapter.getCurrentList()[position]
                ttsManager.speak(card.node.label)
            }
        }).apply {
            timerColor = org.libera.pictotree.data.SessionManager(requireContext()).getTimerColor()
        }
        adapter.isClockModeActive = viewModel.isClockModeActive.value
        adapter.isTimerActivated = viewModel.isTimerActivated.value
        rv.adapter = adapter
        (rv.itemAnimator as? androidx.recyclerview.widget.SimpleItemAnimator)?.supportsChangeAnimations = false
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
                    viewModel.isClockModeActive.collect { active ->
                        adapter.isClockModeActive = active
                        adapter.notifyDataSetChanged()
                        cardClockMode.setCardBackgroundColor(
                            android.content.res.ColorStateList.valueOf(
                                if (active) Color.parseColor("#BBDEFB")
                                else Color.parseColor("#F5F5F5")
                            )
                        )
                        val density = rv.resources.displayMetrics.density
                        if (active) {
                            val leftPadding = (16 * density).toInt()
                            val rightPadding = (320 * density).toInt()
                            rv.setPadding(leftPadding, rv.paddingTop, rightPadding, rv.paddingBottom)
                        } else {
                            val defaultPadding = (120 * density).toInt()
                            rv.setPadding(defaultPadding, rv.paddingTop, defaultPadding, rv.paddingBottom)
                        }
                        if (!active) {
                            drawerLayout.closeDrawer(GravityCompat.END)
                        }
                    }
                }

                launch {
                    viewModel.isTimerActivated.collect { active ->
                        adapter.isTimerActivated = active
                        adapter.notifyDataSetChanged()
                        ivPlayStopTimer.setImageResource(
                            if (active) R.drawable.ic_stop
                            else R.drawable.ic_play
                        )
                        cardPlayStopTimer.setCardBackgroundColor(
                            android.content.res.ColorStateList.valueOf(
                                if (active) Color.parseColor("#FFCDD2") // Rouge doux pour Stop
                                else Color.parseColor("#F5F5F5")       // Gris standard
                            )
                        )
                    }
                }
                
                launch {
                    viewModel.currentTimeFlow.collect { elapsed ->
                        val firstCard = viewModel.phraseList.value.firstOrNull()
                        if (firstCard?.timeConfig?.mode == org.libera.pictotree.data.model.TimeMode.TIMER && firstCard.timeConfig.endTimeMillis > 0) {
                            adapter.notifyItemChanged(0)
                        }
                    }
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
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val mainActivity = requireActivity() as? org.libera.pictotree.MainActivity
        mainActivity?.disableOrientationLock()
        requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    }

    override fun onStop() {
        super.onStop()
        val mainActivity = requireActivity() as? org.libera.pictotree.MainActivity
        mainActivity?.enableOrientationLock()
        mainActivity?.applyUserOrientation()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        ttsManager.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        ttsManager.shutdown()
    }
}
