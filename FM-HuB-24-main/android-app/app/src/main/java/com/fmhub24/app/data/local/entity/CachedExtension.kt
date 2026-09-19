package com.fmhub24.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A downloaded extension package plus everything the loader needs to make a good decision.
 *
 * [enabled] is the user's own switch (the admin panel decides what is offered; the user decides
 * what runs — same split as CloudStream, where a repo can be curated yet providers are
 * individually toggleable).
 */
@Entity(tableName = "cached_extensions")
data class CachedExtension(
    @PrimaryKey val id: String,
    val name: String,
    val version: Int,
    val localFilePath: String,
    val loadedAt: Long = System.currentTimeMillis(),
    val fileUrl: String,
    val status: String = "active",
    val enabled: Boolean = true,
    val pluginClassName: String? = null,
    val apiVersion: Int? = null,
    val fileHash: String? = null,
    val sizeBytes: Long? = null,
    val sourceRepoUrl: String? = null,
    /** Populated after a load attempt so Settings can show the reason without re-loading. */
    val lastError: String? = null,
)
