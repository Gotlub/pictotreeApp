package org.libera.pictotree.network

import okhttp3.*
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import org.libera.pictotree.BuildConfig
import org.libera.pictotree.utils.AuthEvents
import org.json.JSONObject

object RetrofitClient {
    const val SERVER_URL = "http://10.0.2.2:5000"
    private const val BASE_URL = "$SERVER_URL/api/v1/mobile/"

    private var tokenProvider: (() -> String?)? = null
    private var refreshTokenProvider: (() -> String?)? = null
    private var onTokenRefreshed: ((String) -> Unit)? = null

    /**
     * Initialisation avec les accès au SessionManager
     */
    fun init(
        tokenProvider: () -> String?,
        refreshTokenProvider: () -> String?,
        onTokenRefreshed: (String) -> Unit
    ) {
        this.tokenProvider = tokenProvider
        this.refreshTokenProvider = refreshTokenProvider
        this.onTokenRefreshed = onTokenRefreshed
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
    }

    // Intercepteur pour injecter le token
    private val authInterceptor = Interceptor { chain ->
        val requestBuilder = chain.request().newBuilder()
        tokenProvider?.invoke()?.let { token ->
            requestBuilder.addHeader("Authorization", "Bearer $token")
        }
        chain.proceed(requestBuilder.build())
    }

    // Authenticator pour gérer le 401 (Refresh Token)
    private val authenticator = object : Authenticator {
        override fun authenticate(route: Route?, response: Response): Request? {
            // Si on a déjà essayé de s'authentifier pour cette requête, on arrête
            if (response.countPriorResponse() >= 2) {
                return null
            }

            android.util.Log.d("PictoTreeAuth", "401 detected, attempting to refresh token...")
            
            val refreshToken = refreshTokenProvider?.invoke()
            if (refreshToken == null) {
                AuthEvents.triggerLogout()
                return null
            }

            // Faire l'appel de refresh de manière synchrone
            val refreshRequest = Request.Builder()
                .url("${BASE_URL}refresh")
                .post(RequestBody.create(null, ByteArray(0)))
                .addHeader("Authorization", "Bearer $refreshToken")
                .build()

            val refreshClient = OkHttpClient.Builder().addInterceptor(loggingInterceptor).build()
            
            try {
                val refreshResponse = refreshClient.newCall(refreshRequest).execute()
                if (refreshResponse.isSuccessful) {
                    val bodyString = refreshResponse.body?.string() ?: ""
                    val json = JSONObject(bodyString)
                    val newAccessToken = json.getString("access_token")
                    
                    android.util.Log.d("PictoTreeAuth", "Token refreshed successfully.")
                    
                    // Sauvegarder le nouveau token
                    onTokenRefreshed?.invoke(newAccessToken)

                    // Re-tenter la requête initiale avec le nouveau token
                    return response.request.newBuilder()
                        .header("Authorization", "Bearer $newAccessToken")
                        .build()
                } else {
                    android.util.Log.e("PictoTreeAuth", "Refresh failed: ${refreshResponse.code}")
                    AuthEvents.triggerLogout()
                }
            } catch (e: Exception) {
                android.util.Log.e("PictoTreeAuth", "Error during refresh", e)
                AuthEvents.triggerLogout()
            }

            return null
        }
    }

    private fun Response.countPriorResponse(): Int {
        var result = 1
        var priorResponse = priorResponse
        while (priorResponse != null) {
            result++
            priorResponse = priorResponse.priorResponse
        }
        return result
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        .authenticator(authenticator)
        .build()

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val apiService: AuthApiService by lazy {
        retrofit.create(AuthApiService::class.java)
    }

    val treeApiService: TreeApiService by lazy {
        retrofit.create(TreeApiService::class.java)
    }
}
