package org.libera.pictotree.network

import org.libera.pictotree.network.dto.TreeFullDTO
import org.libera.pictotree.network.dto.TreeMetadataDTO
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface TreeApiService {
    @GET("trees")
    suspend fun getAvailableTrees(
        @Query("is_public") isPublic: Boolean = true,
        @Query("search") search: String? = null,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 50
    ): Response<List<TreeMetadataDTO>>

    @GET("trees/{id}")
    suspend fun getTree(
        @Path("id") treeId: Int
    ): Response<TreeFullDTO>

    @GET("pictograms/search")
    suspend fun searchPictograms(
        @Query("q") query: String
    ): Response<List<org.libera.pictotree.network.dto.PictoSearchResultDTO>>
}
