package com.a1stock.app.data

import android.content.Context
import android.net.Uri

data class OwnershipRow(
    val ticker: String,
    val holder: String,
    val percentage: Double?,
    val shares: Long?,
    val asOf: String,
    val threshold: String,
    val sourceUrl: String
)

class OwnershipCollector {

    fun normalize(rows: List<OwnershipRow>): List<OwnershipEntity> {
        val now = System.currentTimeMillis()

        return rows.map {
            OwnershipEntity(
                ticker = it.ticker.uppercase(),
                holder = it.holder.trim(),
                percentage = it.percentage,
                shares = it.shares,
                asOf = it.asOf,
                threshold = it.threshold,
                sourceUrl = it.sourceUrl,
                importedAt = now
            )
        }
    }

    fun loadFromUri(context: Context, uri: Uri, threshold: String = ">=1%", sourceUrl: String = "IDX/KSEI"): List<OwnershipEntity> {
        val rows = mutableListOf<OwnershipRow>()

        context.contentResolver.openInputStream(uri)?.bufferedReader()?.useLines { lines ->
            lines.drop(1).forEach { line ->
                val columns = line.split(",").map { it.trim().removeSurrounding("\\\"") }
                if (columns.size < 12) return@forEach

                val date = columns[0].trim()
                val ticker = columns[1].trim()
                val holder = columns[3].trim()
                val shares = columns[10].trim().toLongOrNull()
                val percentage = columns[11].trim().toDoubleOrNull()

                if (ticker.isNotBlank() &&
                    holder.isNotBlank() &&
                    percentage != null &&
                    percentage >= 1.0
                ) {
                    rows.add(
                        OwnershipRow(
                            ticker = ticker,
                            holder = holder,
                            percentage = percentage,
                            shares = shares,
                            asOf = date,
                            threshold = threshold,
                            sourceUrl = sourceUrl
                        )
                    )
                }
            }
        } ?: throw IllegalStateException("Tidak dapat membuka file")

        return normalize(rows)
    }

    fun loadFromAsset(context: Context, assetName: String, threshold: String = ">=1%", sourceUrl: String = "IDX/KSEI"): List<OwnershipEntity> {
        val rows = mutableListOf<OwnershipRow>()

        context.assets.open(assetName)
            .bufferedReader()
            .useLines { lines ->

                lines.drop(1).forEach { line ->

                    val columns = line.split(",").map { it.trim().removeSurrounding("\"") }

                    if (columns.size < 12) return@forEach

                    val date = columns[0].trim()
                    val ticker = columns[1].trim()
                    val holder = columns[3].trim()

                    val shares =
                        columns[10].trim().toLongOrNull()

                    val percentage =
                        columns[11].trim().toDoubleOrNull()

                    if (ticker.isNotBlank() &&
                        holder.isNotBlank() &&
                        percentage != null &&
                        percentage >= 1.0
                    ) {
                        rows.add(
                            OwnershipRow(
                                ticker = ticker,
                                holder = holder,
                                percentage = percentage,
                                shares = shares,
                                asOf = date,
                                threshold = threshold,
                                sourceUrl = sourceUrl
                            )
                        )
                    }
                }
            }

        return normalize(rows)
    }
}
