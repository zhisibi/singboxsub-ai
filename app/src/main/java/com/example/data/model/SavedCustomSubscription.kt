package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_custom_subscriptions")
data class SavedCustomSubscription(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val format: String, // "singbox", "mihomo", "base64"
    val token: String = "",
    val nodeIds: String = "", // comma-separated node IDs e.g. "1,2,5"
    val createdAt: Long = System.currentTimeMillis()
)
