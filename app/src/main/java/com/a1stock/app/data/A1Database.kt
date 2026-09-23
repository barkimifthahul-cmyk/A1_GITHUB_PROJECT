package com.a1stock.app.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS issuers (
                        ticker TEXT NOT NULL,
                        name TEXT NOT NULL,
                        sector TEXT,
                        active INTEGER NOT NULL,
                        PRIMARY KEY(ticker)
                    )
                """)
            }
        }
    }
}
