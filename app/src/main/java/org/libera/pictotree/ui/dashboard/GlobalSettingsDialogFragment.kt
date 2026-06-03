package org.libera.pictotree.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import kotlinx.coroutines.launch
import org.libera.pictotree.R

class GlobalSettingsDialogFragment : DialogFragment() {

    private lateinit var viewModel: DashboardViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, com.google.android.material.R.style.Theme_Material3_Light_Dialog)
    }

    override fun onStart() {
        super.onStart()
        val width = (resources.displayMetrics.widthPixels * 0.90).toInt()
        dialog?.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.dialog_global_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        viewModel = ViewModelProvider(requireParentFragment())[DashboardViewModel::class.java]

        val spinnerLanguage = view.findViewById<Spinner>(R.id.spinnerLanguage)
        val switchOfflineAccess = view.findViewById<MaterialSwitch>(R.id.switchOfflineAccess)
        val switchGlobalSearch = view.findViewById<MaterialSwitch>(R.id.switchGlobalSearch)
        val switchEnableRotation = view.findViewById<MaterialSwitch>(R.id.switchEnableRotation)
        val switchEnableTTS = view.findViewById<MaterialSwitch>(R.id.switchEnableTTS)
        val switchEnableViewChange = view.findViewById<MaterialSwitch>(R.id.switchEnableViewChange)
        
        val spinnerStartupView = view.findViewById<Spinner>(R.id.spinnerStartupView)
        val spinnerOrientation = view.findViewById<Spinner>(R.id.spinnerOrientation)
        val spinnerTimerColor = view.findViewById<Spinner>(R.id.spinnerTimerColor)
        val btnClose = view.findViewById<MaterialButton>(R.id.btnCloseSettings)

        // Setup Languages
        val languages = resources.getStringArray(R.array.settings_languages)
        val codes = resources.getStringArray(R.array.settings_language_codes)
        spinnerLanguage.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, languages)

        // Setup Display preferences
        val startupViewOptions = resources.getStringArray(R.array.settings_startup_view_options)
        val startupViewValues = resources.getStringArray(R.array.settings_startup_view_values)
        spinnerStartupView.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, startupViewOptions)

        val orientationOptions = resources.getStringArray(R.array.settings_orientation_options)
        val orientationValues = resources.getStringArray(R.array.settings_orientation_values)
        spinnerOrientation.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, orientationOptions)

        // Setup Timer color preference
        val timerColorOptions = resources.getStringArray(R.array.settings_timer_color_options)
        val timerColorValues = resources.getStringArray(R.array.settings_timer_color_values)
        spinnerTimerColor.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, timerColorOptions)

        val sessionManager = org.libera.pictotree.data.SessionManager(requireContext())
        val currentTimerColor = sessionManager.getTimerColor()
        val timerColorIdx = timerColorValues.indexOf(currentTimerColor)
        if (timerColorIdx != -1) spinnerTimerColor.setSelection(timerColorIdx)

        // Sync with ViewModel (One-way binding to UI)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.userConfig.collect { config ->
                    config?.let {
                        val langIdx = codes.indexOf(it.locale)
                        if (langIdx != -1 && spinnerLanguage.selectedItemPosition != langIdx) spinnerLanguage.setSelection(langIdx)

                        val startupIdx = startupViewValues.indexOf(it.startupView)
                        if (startupIdx != -1 && spinnerStartupView.selectedItemPosition != startupIdx) spinnerStartupView.setSelection(startupIdx)

                        val orientIdx = orientationValues.indexOf(it.defaultOrientation)
                        if (orientIdx != -1 && spinnerOrientation.selectedItemPosition != orientIdx) spinnerOrientation.setSelection(orientIdx)

                        if (switchOfflineAccess.isChecked != it.isOfflineAccessAllowed) switchOfflineAccess.isChecked = it.isOfflineAccessAllowed
                        if (switchGlobalSearch.isChecked != it.enableSearch) switchGlobalSearch.isChecked = it.enableSearch
                        if (switchEnableRotation.isChecked != it.enableRotationButton) switchEnableRotation.isChecked = it.enableRotationButton
                        if (switchEnableTTS.isChecked != it.enableTTSButton) switchEnableTTS.isChecked = it.enableTTSButton
                        if (switchEnableViewChange.isChecked != it.enableViewChangeButton) switchEnableViewChange.isChecked = it.enableViewChangeButton
                    }
                }
            }
        }

        // Listeners with change detection to avoid loops
        spinnerLanguage.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (viewModel.userConfig.value?.locale != codes[position]) {
                    viewModel.setLanguage(codes[position]) {
                        // When language is changed, recreate the activity to apply changes across all strings
                        if (isAdded) {
                            activity?.recreate()
                        }
                    }
                }
            }
        }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        spinnerStartupView.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val current = viewModel.userConfig.value ?: return
                if (current.startupView != startupViewValues[position]) {
                    viewModel.setGlobalDisplaySettings(startupViewValues[position], current.defaultOrientation)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        spinnerOrientation.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val current = viewModel.userConfig.value ?: return
                if (current.defaultOrientation != orientationValues[position]) {
                    viewModel.setGlobalDisplaySettings(current.startupView, orientationValues[position])
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        switchOfflineAccess.setOnClickListener {
            viewModel.setOfflineAccessAllowed(switchOfflineAccess.isChecked)
        }

        switchGlobalSearch.setOnClickListener {
            viewModel.setEnableSearch(switchGlobalSearch.isChecked)
        }

        switchEnableRotation.setOnClickListener {
            viewModel.updateUIControls(switchEnableRotation.isChecked, switchEnableTTS.isChecked, switchEnableViewChange.isChecked)
        }
        switchEnableTTS.setOnClickListener {
            viewModel.updateUIControls(switchEnableRotation.isChecked, switchEnableTTS.isChecked, switchEnableViewChange.isChecked)
        }
        switchEnableViewChange.setOnClickListener {
            viewModel.updateUIControls(switchEnableRotation.isChecked, switchEnableTTS.isChecked, switchEnableViewChange.isChecked)
        }

        spinnerTimerColor.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                sessionManager.setTimerColor(timerColorValues[position])
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        btnClose.setOnClickListener { dismiss() }
    }
}
