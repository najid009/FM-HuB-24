package com.fmhub24.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloaded_content")
data class DownloadedContent(
    @PrimaryKey val id: String,
    val name: String,
    val posterUrl: String?,
    val apiName: String,
    val episodeName: String?,
    val localPath: String,
    val sourceUrl: String,
    val status: String = "completed",
    val createdAt: Long = System.currentTimeMillis()
)
