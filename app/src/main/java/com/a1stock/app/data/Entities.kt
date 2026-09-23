package com.a1stock.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "events", indices = [])
data class EventEntity(
    @PrimaryKey val fingerprint: String,
    val ticker: String?, val title: String, val publishedAt: String?,
    val category: String, val source: String, val sourceUrl: String,
    val documentUrl: String?, val summary: String?, val firstSeenAt: Long
)

@Entity(tableName = "ownership")
data class OwnershipEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ticker: String, val holder: String, val percentage: Double?,
    val shares: Long?, val asOf: String, val threshold: String, val sourceUrl: String,
    val importedAt: Long
)

@Entity(tableName = "watchlist", primaryKeys = ["ticker"])
data class WatchEntity(val ticker: String, val notifyEvents: Boolean = true, val notifyOwnership: Boolean = true)
