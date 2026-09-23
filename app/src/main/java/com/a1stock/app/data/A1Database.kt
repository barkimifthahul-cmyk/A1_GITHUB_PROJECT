package com.a1stock.app.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        EventEntity::class,
        OwnershipEntity::class,
        WatchEntity::class,
        IssuerEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class A1Database : RoomDatabase() {
    abstract fun eventDao(): EventDao
    abstract fun ownershipDao(): OwnershipDao
    abstract fun watchDao(): WatchDao
    abstract fun issuerDao(): IssuerDao
}
