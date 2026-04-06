package org.libera.pictotree.data.repository

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path

data class ArasaacPictoDTO(
    @SerializedName("_id") val id: Int,
    @SerializedName("keywords") val keywords: List<ArasaacKeywordDTO>
)

data class ArasaacKeywordDTO(
    @SerializedName("keyword") val keyword: String
)

interface ArasaacApiService {
    @GET("pictograms/{locale}/search/{query}")
    suspend fun searchPictograms(
        @Path("locale") locale: String,
        @Path("query") query: String
    ): List<ArasaacPictoDTO>
}

class ArasaacRepository {
    private val api = Retrofit.Builder()
        .baseUrl("https://api.arasaac.org/api/")
        .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
        .build()
        .create(ArasaacApiService::class.java)

    suspend fun search(query: String, locale: String = "fr"): List<org.libera.pictotree.network.dto.PictoSearchResultDTO> {
        return try {
            val results = api.searchPictograms(locale, query)
            results.map {
                org.libera.pictotree.network.dto.PictoSearchResultDTO(
                    id = it.id,
                    name = it.keywords.firstOrNull()?.keyword ?: "Picto",
                    imageUrl = "https://static.arasaac.org/pictograms/${it.id}/${it.id}_300.png"
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
