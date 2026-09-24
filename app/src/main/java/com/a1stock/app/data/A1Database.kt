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
        IssuerEntity::class,
        MarketEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class A1Database : RoomDatabase() {

    abstract fun eventDao(): EventDao
    abstract fun ownershipDao(): OwnershipDao
    abstract fun watchDao(): WatchDao
    abstract fun issuerDao(): IssuerDao
    abstract fun marketDao(): MarketDao

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

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS market_data (
                        ticker TEXT NOT NULL,
                        price REAL,
                        change REAL,
                        changePercent REAL,
                        volume INTEGER,
                        marketCap INTEGER,
                        updatedAt INTEGER NOT NULL,
                        PRIMARY KEY(ticker)
                    )
                """)
            }
        }
    }
}
