package org.libera.pictotree.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.textfield.MaterialAutoCompleteTextView
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

        val actvUsers: MaterialAutoCompleteTextView = view.findViewById(R.id.actv_users)
        val switchOnlineMode: SwitchCompat = view.findViewById(R.id.switch_online_mode)
        val tilPassword: TextInputLayout = view.findViewById(R.id.til_password)
        val btnLogin: Button = view.findViewById(R.id.btn_login)

        // Setup AutoCompleteTextView Adapter with an empty list initially
        val adapter = ArrayAdapter<String>(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            mutableListOf()
        )
        actvUsers.setAdapter(adapter)

        // View Interactions
        
        // Listen to typed text and selection
        actvUsers.doAfterTextChanged { editable ->
            val username = editable?.toString() ?: ""
            viewModel.onUsernameChanged(username)
        }

        switchOnlineMode.setOnCheckedChangeListener { _, isChecked ->
            viewModel.onOnlineModeToggled(isChecked)
        }

        btnLogin.setOnClickListener {
            val password = tilPassword.editText?.text?.toString() ?: ""
            viewModel.login(password)
        }

        // Observing ViewModel State
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    // 1. Update AutoCompleteTextView adapter list
                    if (adapter.count != state.availableUsers.size) {
                        adapter.clear()
                        adapter.addAll(state.availableUsers)
                        adapter.notifyDataSetChanged()
                    }

                    // Select correct item securely avoiding infinite loop
                    state.selectedUser?.let { selected ->
                        if (actvUsers.text.toString() != selected) {
                            actvUsers.setText(selected, false) // false avoids triggering dropdown/filtering
                        }
                    }

                    // 2. Update Switch
                    if (switchOnlineMode.isChecked != state.isOnlineMode) {
                        switchOnlineMode.isChecked = state.isOnlineMode
                    }

                    // 3. Update Password Visibility
                    tilPassword.visibility = if (state.isPasswordVisible) View.VISIBLE else View.GONE

                    // 4. Update Loading State on Button
                    if (state.isLoading) {
                        btnLogin.isEnabled = false
                        btnLogin.text = "Connexion..."
                    } else {
                        btnLogin.isEnabled = true
                        btnLogin.text = "Login"
                    }

                    // 5. Handle Error
                    state.errorMessage?.let { error ->
                        Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
                    }

                    // 6. Handle Success
                    if (state.isLoginSuccessful) {
                        Toast.makeText(requireContext(), "Connexion réussie ! Token reçu", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }
}
