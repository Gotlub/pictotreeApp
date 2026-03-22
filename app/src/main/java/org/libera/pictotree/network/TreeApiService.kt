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
        @Header("Authorization") authHeader: String
    ): Response<List<TreeMetadataDTO>>

    @GET("trees/{id}")
    suspend fun getTree(
        @Header("Authorization") authHeader: String,
        @Path("id") treeId: Int
    ): Response<TreeFullDTO>
}
