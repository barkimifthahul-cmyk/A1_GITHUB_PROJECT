package com.a1stock.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.room.Room
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.a1stock.app.data.A1Database
import com.a1stock.app.data.IssuerSeeder
import com.a1stock.app.data.KseiCollector
import com.a1stock.app.data.OwnershipCollector
import com.a1stock.app.data.OwnershipChangeDetector
import com.a1stock.app.data.SourceCollector
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
            .addMigrations(
                A1Database.MIGRATION_1_2,
                A1Database.MIGRATION_2_3
            )
            .build()

        db.issuerDao().insertAll(
            IssuerSeeder.initialData(applicationContext)
        )

        val idxEvents = SourceCollector().collectIdxAnnouncements()
        val kseiEvents = KseiCollector().collectCorporateActions()
        val events = idxEvents + kseiEvents

        val ownershipRows = OwnershipCollector()
            .loadFromAsset(applicationContext)

        val ownershipValid =
            ownershipRows.isNotEmpty() &&
            ownershipRows.all {
                it.ticker.isNotBlank() &&
                it.holder.isNotBlank() &&
                (it.percentage ?: 0.0) >= 1.0
            }

        val ownershipChanges = if (ownershipValid) {
            val oldOwnership = withContext(Dispatchers.IO) {
                db.ownershipDao().all()
            }

            OwnershipChangeDetector().detect(
                oldRows = oldOwnership,
                newRows = ownershipRows
            )
        } else {
            emptyList()
        }

        val ownershipEvents = ownershipChanges.map { change ->
            val fingerprint = "OWNERSHIP|${change.ticker}|${change.holder}|${change.threshold}|${change.asOf}|${change.status}"

            val oldText = String.format("%.2f%%", change.oldPercentage)
            val newText = String.format("%.2f%%", change.newPercentage)
            val changeText = String.format("%+.2f%%", change.changePercentage)

            val summary = when (change.status) {
                "NEW" ->
                    "${change.holder}: kepemilikan baru $newText (sebelumnya 0.00%)"
                "OUT" ->
                    "${change.holder}: keluar, sebelumnya $oldText, sekarang 0.00%"
                "INCREASE" ->
                    "${change.holder}: naik dari $oldText menjadi $newText (perubahan $changeText)"
                "DECREASE" ->
                    "${change.holder}: turun dari $oldText menjadi $newText (perubahan $changeText)"
                else ->
                    "${change.holder}: berubah dari $oldText menjadi $newText (perubahan $changeText)"
            }

            com.a1stock.app.data.EventEntity(
                fingerprint = fingerprint,
                ticker = change.ticker,
                title = "Perubahan kepemilikan ${change.threshold}: ${change.status}",
                publishedAt = change.asOf,
                category = "OWNERSHIP",
                source = "IDX/KSEI",
                sourceUrl = "IDX/KSEI",
                documentUrl = null,
                summary = summary,
                firstSeenAt = System.currentTimeMillis()
            )
        }

        val allEvents = events + ownershipEvents

        val inserted = withContext(Dispatchers.IO) {
            val count = db.eventDao().insertAll(allEvents).count { it != -1L }

            if (ownershipValid) {
                db.ownershipDao().replaceSnapshot(ownershipRows)
            }

            count
        }

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
