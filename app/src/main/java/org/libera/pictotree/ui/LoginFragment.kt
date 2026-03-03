package org.libera.pictotree.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch
import org.libera.pictotree.R
import org.libera.pictotree.viewmodel.LoginViewModel

class LoginFragment : Fragment() {

    private val viewModel: LoginViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_login, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val spinnerUsers: Spinner = view.findViewById(R.id.spinner_users)
        val switchOnlineMode: SwitchCompat = view.findViewById(R.id.switch_online_mode)
        val tilPassword: TextInputLayout = view.findViewById(R.id.til_password)
        val btnLogin: Button = view.findViewById(R.id.btn_login)

        // Setup Spinners Adapter with an empty list initially
        val adapter = ArrayAdapter<String>(
            requireContext(),
            android.R.layout.simple_spinner_item,
            mutableListOf()
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerUsers.adapter = adapter

        // View Interactions
        spinnerUsers.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                 val user = adapter.getItem(position)
                 user?.let { viewModel.onUserSelected(it) }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        switchOnlineMode.setOnCheckedChangeListener { _, isChecked ->
            viewModel.onOnlineModeToggled(isChecked)
        }

        btnLogin.setOnClickListener {
            // Handle Login action. No business logic here.
            // Example: viewModel.login(password)
        }

        // Observing ViewModel State
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    // 1. Update Spinner
                    if (adapter.count != state.availableUsers.size) {
                        adapter.clear()
                        adapter.addAll(state.availableUsers)
                        adapter.notifyDataSetChanged()
                    }

                    // Select correct item in spinner securely avoiding infinite loop
                    state.selectedUser?.let { selected ->
                         val position = adapter.getPosition(selected)
                         if (position >= 0 && spinnerUsers.selectedItemPosition != position) {
                             spinnerUsers.setSelection(position)
                         }
                    }

                    // 2. Update Switch
                    if (switchOnlineMode.isChecked != state.isOnlineMode) {
                        switchOnlineMode.isChecked = state.isOnlineMode
                    }

                    // 3. Update Password Visibility
                    tilPassword.visibility = if (state.isPasswordVisible) View.VISIBLE else View.GONE
                }
            }
        }
    }
}
