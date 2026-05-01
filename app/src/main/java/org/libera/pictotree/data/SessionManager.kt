package org.libera.pictotree.data

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("pictotree_session", Context.MODE_PRIVATE)

    fun saveSession(username: String, token: String?, refreshToken: String? = null) {
        val editor = prefs.edit()
        if (token != null) {
            editor.putString("USER_TOKEN", token)
            editor.putBoolean("IS_ONLINE", true)
        } else {
            editor.putBoolean("IS_ONLINE", false)
        }
        if (refreshToken != null) {
            editor.putString("REFRESH_TOKEN", refreshToken)
        }
        editor.putString("USERNAME", username)
        
        // Add to history of known users
        val knownUsers = getKnownUsers().toMutableSet()
        knownUsers.add(username)
        editor.putStringSet("KNOWN_USERS", knownUsers)
        
        editor.apply()
    }

    fun isOnline(): Boolean {
        return prefs.getBoolean("IS_ONLINE", false)
    }

    fun getToken(): String? {
        return prefs.getString("USER_TOKEN", null)
    }

    fun getRefreshToken(): String? {
        return prefs.getString("REFRESH_TOKEN", null)
    }

    fun updateAccessToken(newToken: String) {
        prefs.edit().putString("USER_TOKEN", newToken).apply()
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
            .remove("REFRESH_TOKEN")
            .remove("USERNAME")
            .remove("IS_ONLINE")
            .apply()
    }

    fun switchToOfflineMode() {
        prefs.edit()
            .remove("USER_TOKEN")
            .remove("REFRESH_TOKEN")
            .putBoolean("IS_ONLINE", false)
            .apply()
    }
}
