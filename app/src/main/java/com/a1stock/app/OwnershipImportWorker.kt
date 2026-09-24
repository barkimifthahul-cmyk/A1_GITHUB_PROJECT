package com.a1stock.app

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.a1stock.app.data.A1Database
import com.a1stock.app.data.EventEntity
import com.a1stock.app.data.OwnershipChangeDetector
import com.a1stock.app.data.OwnershipCollector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OwnershipImportWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {

        val uriString = inputData.getString("uri")
            ?: return Result.failure()

        val uri = Uri.parse(uriString)

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

        val newOwnership = withContext(Dispatchers.IO) {
            OwnershipCollector().loadFromUri(
                applicationContext,
                uri
            )
        }

        if (newOwnership.isEmpty()) {
            db.close()
            return Result.failure()
        }

        val oldOwnership = withContext(Dispatchers.IO) {
            db.ownershipDao().all()
        }

        val changes = OwnershipChangeDetector().detect(
            oldRows = oldOwnership,
            newRows = newOwnership
        )

        val events = changes.map { change ->

            val fingerprint =
                "OWNERSHIP|${change.ticker}|${change.holder}|${change.threshold}|${change.asOf}|${change.status}"

            val oldText = String.format(
                "%.2f%%",
                change.oldPercentage
            )

            val newText = String.format(
                "%.2f%%",
                change.newPercentage
            )

            val changeText = String.format(
                "%+.2f%%",
                change.changePercentage
            )

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

            EventEntity(
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

        val inserted = withContext(Dispatchers.IO) {
            val count =
                db.eventDao().insertAll(events)
                    .count { it != -1L }

            db.ownershipDao().replaceSnapshot(newOwnership)

            count
        }

        db.close()

        notifyUser(
            newOwnership.size,
            inserted
        )

        Result.success()

    } catch (e: Exception) {
        Result.failure()
    }

    }
    private fun notifyUser(rows: Int, changes: Int) {

        val notificationManager =
            applicationContext.getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as android.app.NotificationManager

        notificationManager.createNotificationChannel(
            android.app.NotificationChannel(
                "a1",
                "A1 Ownership",
                android.app.NotificationManager.IMPORTANCE_DEFAULT
            )
        )

        val notification =
            androidx.core.app.NotificationCompat.Builder(
                applicationContext,
                "a1"
            )
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("A1: Data kepemilikan diperbarui")
                .setContentText(
                    "$rows data pemegang saham, $changes perubahan"
                )
                .setAutoCancel(true)
                .build()

        notificationManager.notify(
            2001,
            notification
        )
    }
}
