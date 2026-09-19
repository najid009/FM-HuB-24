package com.fmhub24.app.domain.model

import com.fmhub24.app.data.remote.dto.ExtensionDto

data class Extension(
    val id: String,
    val name: String,
    val fileUrl: String,
    val version: Int,
    val language: String?,
    val tvTypes: List<String>?,
    val status: String,
    val iconUrl: String?,
    val description: String?
)

fun ExtensionDto.toDomain(): Extension {
    return Extension(
        id = id,
        name = name,
        fileUrl = fileUrl,
        version = version,
        language = language,
        tvTypes = tvTypes,
        status = status,
        iconUrl = iconUrl,
        description = description
    )
}
