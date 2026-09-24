package com.a1stock.app.data

import android.content.Context

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

    fun loadFromAsset(context: Context): List<OwnershipEntity> {
        val rows = mutableListOf<OwnershipRow>()

        context.assets.open("kepemilikan_saham_20260227.csv")
            .bufferedReader()
            .useLines { lines ->

                lines.drop(1).forEach { line ->

                    val columns = line.split(",")

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
                                threshold = ">=1%",
                                sourceUrl = "KSEI/BEI dataset 27-Feb-2026"
                            )
                        )
                    }
                }
            }

        return normalize(rows)
    }
}
