package org.libera.pictotree.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import org.libera.pictotree.BuildConfig

object RetrofitClient {
    // Emulator loopback IP to access host machine localhost
    private const val BASE_URL = "http://10.0.2.2:5000/api/v1/mobile/"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
    }

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
        .addInterceptor(loggingInterceptor)
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
