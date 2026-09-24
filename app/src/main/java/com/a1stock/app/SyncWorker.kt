package com.a1stock.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.room.Room
import androidx.work.*
import com.a1stock.app.data.A1Database
import com.a1stock.app.data.IssuerSeeder
import com.a1stock.app.data.SourceCollector
import com.a1stock.app.data.KseiCollector
import com.a1stock.app.data.OwnershipCollector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = try {

        val db = Room.databaseBuilder(
            applicationContext,
            A1Database::class.java,
            "a1.db"
        )
            .addMigrations(A1Database.MIGRATION_1_2, A1Database.MIGRATION_2_3)
            .build()

        db.issuerDao().insertAll(IssuerSeeder.initialData(applicationContext))

        val idxEvents = SourceCollector().collectIdxAnnouncements()
        val kseiEvents = KseiCollector().collectCorporateActions()
        val events = idxEvents + kseiEvents

        val ownershipRows = OwnershipCollector()
            .loadFromAsset(applicationContext)

        withContext(Dispatchers.IO) {
            if (ownershipRows.isNotEmpty()) {
                db.ownershipDao().replaceSnapshot(ownershipRows)
            }
        }

        val inserted = withContext(Dispatchers.IO) {
            db.eventDao().insertAll(events)
        }.count { it != -1L }

        if (inserted > 0) {
            notifyUser(inserted)
        }

        db.close()
        Result.success()

    } catch (e: Exception) {
        Result.retry()
    }

    private fun notifyUser(n: Int) {
        val nm = applicationContext.getSystemService(
            Context.NOTIFICATION_SERVICE
        ) as NotificationManager

        nm.createNotificationChannel(
            NotificationChannel(
                "a1",
                "A1 Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )

        val notification = NotificationCompat.Builder(
            applicationContext,
            "a1"
        )
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("A1: informasi baru")
            .setContentText("$n event baru dari IDX/BEI dan KSEI")
            .setAutoCancel(true)
            .build()

        nm.notify(1001, notification)
    }
}
