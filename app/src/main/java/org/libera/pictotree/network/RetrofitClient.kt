package org.libera.pictotree.network

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    // Emulator loopback IP to access host machine localhost
    private const val BASE_URL = "http://10.0.2.2:5000/api/v1/mobile/"

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val requestBuilder = chain.request().newBuilder()
            
            // TODO: Injecter le Header 'Authorization: Bearer mon_token_jwt' ici plus tard
            // val token = getAuthToken() // Méthode pour récupérer le token sauvegardé
            // if (token != null) {
            //     requestBuilder.addHeader("Authorization", "Bearer \$token")
            // }

            val request = requestBuilder.build()
            chain.proceed(request)
        }
        .build()

    val apiService: AuthApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AuthApiService::class.java)
    }
}
