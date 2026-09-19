package com.fmhub24.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "watch_progress")
data class WatchProgress(
    @PrimaryKey val url: String,
    val name: String,
    val posterUrl: String?,
    val apiName: String,
    val position: Long, // in ms
    val duration: Long, // in ms
    val episodeData: String? = null, // for series, the episode's data url
    val episodeName: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)
