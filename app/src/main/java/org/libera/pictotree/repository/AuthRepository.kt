package org.libera.pictotree.repository

import org.libera.pictotree.network.AuthApiService
import org.libera.pictotree.network.LoginRequest
import org.libera.pictotree.network.LoginResponse

class AuthRepository(private val apiService: AuthApiService) {

    suspend fun login(username: String, password: String): Result<LoginResponse> {
        return try {
            val response = apiService.login(LoginRequest(username = username, password = password))
            if (response.isSuccessful) {
                response.body()?.let {
                    Result.success(it)
                } ?: Result.failure(Exception("Corps de la réponse vide"))
            } else {
                // Erreur HTTP ex: 401 Unauthorized
                Result.failure(Exception("Erreur de connexion : \${response.code()}"))
            }
        } catch (e: Exception) {
            // Erreur réseau ex: pas de connexion internet
            Result.failure(Exception("Erreur réseau : \${e.message}"))
        }
    }
}
