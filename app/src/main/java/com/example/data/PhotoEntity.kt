package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "photos")
data class PhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val filePath: String,
    val zoom: Float,
    val isNightMode: Boolean,
    val isHyperClarity: Boolean,
    val isAiUpscaled: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
