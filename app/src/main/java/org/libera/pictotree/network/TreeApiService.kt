package org.libera.pictotree.network

import org.libera.pictotree.network.dto.TreeFullDTO
import org.libera.pictotree.network.dto.TreeMetadataDTO
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path

interface TreeApiService {
    @GET("trees")
    suspend fun getAvailableTrees(
        @Header("Authorization") authHeader: String,
        @retrofit2.http.Query("is_public") isPublic: Boolean = true,
        @retrofit2.http.Query("search") search: String? = null,
        @retrofit2.http.Query("page") page: Int = 1,
        @retrofit2.http.Query("limit") limit: Int = 50
    ): Response<List<TreeMetadataDTO>>

    @GET("trees/{id}")
    suspend fun getTree(
        @Header("Authorization") authHeader: String,
        @Path("id") treeId: Int
    ): Response<TreeFullDTO>

    @GET("pictograms/search")
    suspend fun searchPictograms(
        @Header("Authorization") authHeader: String,
        @retrofit2.http.Query("q") query: String
    ): Response<List<org.libera.pictotree.network.dto.PictoSearchResultDTO>>
}
