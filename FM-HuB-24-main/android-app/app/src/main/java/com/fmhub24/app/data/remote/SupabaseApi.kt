package com.fmhub24.app.data.remote

import com.fmhub24.app.data.remote.dto.ExtensionDto
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface SupabaseApi {
    @GET("rest/v1/extensions")
    suspend fun getActiveExtensions(
        @Header("apikey") apiKey: String,
        @Header("Authorization") auth: String,
        @Query("status") statusFilter: String = "eq.active",
        @Query("select") select: String = "*",
        @Query("order") order: String = "updated_at.desc"
    ): List<ExtensionDto>
}
