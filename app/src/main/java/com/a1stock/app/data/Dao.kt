package com.a1stock.app.data

import androidx.room.*

@Dao interface EventDao {
    @Query("SELECT * FROM events ORDER BY firstSeenAt DESC LIMIT 100") suspend fun latest(): List<EventEntity>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertAll(rows: List<EventEntity>): List<Long>
}

@Dao interface OwnershipDao {
    @Query("SELECT * FROM ownership ORDER BY importedAt DESC LIMIT 500") suspend fun latest(): List<OwnershipEntity>
    @Query("SELECT * FROM ownership WHERE ticker=:ticker AND holder=:holder ORDER BY importedAt DESC LIMIT 2") suspend fun lastTwo(ticker:String, holder:String): List<OwnershipEntity>
    @Insert suspend fun insertAll(rows: List<OwnershipEntity>)
}

@Dao interface WatchDao {
    @Query("SELECT * FROM watchlist ORDER BY ticker") suspend fun all(): List<WatchEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(row: WatchEntity)
    @Query("DELETE FROM watchlist WHERE ticker=:ticker") suspend fun remove(ticker:String)
}

@Dao
interface IssuerDao {
    @Query("""
        SELECT * FROM issuers
        WHERE active = 1
        AND (ticker LIKE '%' || :query || '%' OR name LIKE '%' || :query || '%')
        ORDER BY ticker
    """)
    suspend fun search(query: String): List<IssuerEntity>

    @Query("SELECT * FROM issuers WHERE active = 1 ORDER BY ticker")
    suspend fun all(): List<IssuerEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<IssuerEntity>)
}

@Dao
interface MarketDao {
    @Query("SELECT * FROM market_data ORDER BY ticker")
    suspend fun all(): List<MarketEntity>

    @Query("SELECT * FROM market_data WHERE ticker = :ticker LIMIT 1")
    suspend fun get(ticker: String): MarketEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: MarketEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<MarketEntity>)
}
