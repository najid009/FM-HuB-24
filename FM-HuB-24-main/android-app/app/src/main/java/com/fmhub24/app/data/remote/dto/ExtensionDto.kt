package com.fmhub24.app.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * One row of the Supabase `extensions` table.
 *
 * The extra columns beyond name/url/version are what makes failures diagnosable and repo sync
 * possible: `plugin_class_name` skips guesswork, `api_version` (FMHub contract) rejects a
 * mismatched build with a real message, and `file_hash` lets us verify a download.
 */
data class ExtensionDto(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("file_url") val fileUrl: String,
    @SerializedName("version") val version: Int = 1,
    @SerializedName("language") val language: String?,
    @SerializedName("tv_types") val tvTypes: List<String>?,
    @SerializedName("status") val status: String = "active",
    @SerializedName("icon_url") val iconUrl: String?,
    @SerializedName("description") val description: String?,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("updated_at") val updatedAt: String?,

    // ---- repo / plugin metadata (nullable: older rows do not have them yet) ----
    @SerializedName("internal_name") val internalName: String? = null,
    @SerializedName("plugin_class_name") val pluginClassName: String? = null,
    @SerializedName("api_version") val apiVersion: Int? = null,
    @SerializedName("cs_api_version") val csApiVersion: Int? = null,
    @SerializedName("file_hash") val fileHash: String? = null,
    @SerializedName("file_name") val fileName: String? = null,
    @SerializedName("size_bytes") val sizeBytes: Long? = null,
    @SerializedName("source_repo_url") val sourceRepoUrl: String? = null,
    @SerializedName("requires_resources") val requiresResources: Boolean? = null,
)
