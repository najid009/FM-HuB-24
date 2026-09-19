package com.fmhub24.app.data.remote.dto

data class AppNoticeDto(
    val id: String,
    val title: String,
    val message: String,
    val enabled: Boolean = true,
    val priority: Int = 0,
    val published_at: String? = null,
)

data class AppReleaseDto(
    val id: String,
    val version_code: Int,
    val version_name: String,
    val download_url: String,
    val release_notes: String? = null,
    val is_required: Boolean = false,
    val enabled: Boolean = true,
)
