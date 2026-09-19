package com.fmhub24.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorites")
data class Favorite(
    @PrimaryKey val url: String,
    val name: String,
    val posterUrl: String?,
    val apiName: String,
    val type: String?,
    val addedAt: Long = System.currentTimeMillis()
)
