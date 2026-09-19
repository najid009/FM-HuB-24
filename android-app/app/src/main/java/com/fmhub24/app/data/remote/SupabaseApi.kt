package com.fmhub24.app.data.remote

import com.fmhub24.app.data.remote.dto.ExtensionDto
import com.fmhub24.app.data.remote.dto.AppNoticeDto
import com.fmhub24.app.data.remote.dto.AppReleaseDto
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

    @GET("rest/v1/app_notices")
    suspend fun getActiveNotices(
        @Header("apikey") apiKey: String,
        @Header("Authorization") auth: String,
        @Query("enabled") enabled: String = "eq.true",
        @Query("select") select: String = "*",
        @Query("order") order: String = "priority.desc,published_at.desc",
    ): List<AppNoticeDto>

    @GET("rest/v1/app_releases")
    suspend fun getLatestRelease(
        @Header("apikey") apiKey: String,
        @Header("Authorization") auth: String,
        @Query("enabled") enabled: String = "eq.true",
        @Query("select") select: String = "*",
        @Query("order") order: String = "version_code.desc",
        @Query("limit") limit: Int = 1,
    ): List<AppReleaseDto>
}
