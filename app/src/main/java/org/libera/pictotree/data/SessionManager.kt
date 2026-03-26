package org.libera.pictotree.data

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("pictotree_session", Context.MODE_PRIVATE)

    fun saveSession(username: String, token: String?) {
        val editor = prefs.edit()
        if (token != null) {
            editor.putString("USER_TOKEN", token)
        }
        editor.putString("USERNAME", username)
        
        // Add to history of known users
        val knownUsers = getKnownUsers().toMutableSet()
        knownUsers.add(username)
        editor.putStringSet("KNOWN_USERS", knownUsers)
        
        editor.apply()
    }

    fun getToken(): String? {
        return prefs.getString("USER_TOKEN", null)
    }

    fun getUsername(): String? {
        return prefs.getString("USERNAME", null)
    }

    fun getKnownUsers(): Set<String> {
        return prefs.getStringSet("KNOWN_USERS", emptySet()) ?: emptySet()
    }

    fun clearSession() {
        prefs.edit()
            .remove("USER_TOKEN")
            .remove("USERNAME")
            // On conserve KNOWN_USERS pour l'historique AutoCompleteTextView
            .apply()
    }
}
