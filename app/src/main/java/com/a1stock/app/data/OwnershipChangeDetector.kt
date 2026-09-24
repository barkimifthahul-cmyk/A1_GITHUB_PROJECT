package com.a1stock.app.data

data class OwnershipChange(
    val ticker: String,
    val holder: String,
    val threshold: String,
    val oldPercentage: Double,
    val newPercentage: Double,
    val changePercentage: Double,
    val oldShares: Long?,
    val newShares: Long?,
    val status: String,
    val asOf: String
)

class OwnershipChangeDetector {

    fun detect(
        oldRows: List<OwnershipEntity>,
        newRows: List<OwnershipEntity>
    ): List<OwnershipChange> {

        if (oldRows.isEmpty()) {
            return emptyList()
        }

        val oldMap = oldRows.associateBy {
            "${it.ticker}|${it.holder}|${it.threshold}"
        }

        val newMap = newRows.associateBy {
            "${it.ticker}|${it.holder}|${it.threshold}"
        }

        val keys = oldMap.keys + newMap.keys

        return keys.mapNotNull { key ->

            val old = oldMap[key]
            val current = newMap[key]

            val oldPercentage = old?.percentage ?: 0.0
            val newPercentage = current?.percentage ?: 0.0

            val oldShares = old?.shares
            val newShares = current?.shares

            val percentageChanged =
                oldPercentage != newPercentage

            val sharesChanged =
                oldShares != newShares

            if (!percentageChanged && !sharesChanged) {
                return@mapNotNull null
            }

            val status = when {
                old == null && current != null -> "NEW"
                old != null && current == null -> "OUT"
                newPercentage > oldPercentage -> "INCREASE"
                newPercentage < oldPercentage -> "DECREASE"
                else -> "CHANGED"
            }

            OwnershipChange(
                ticker = current?.ticker ?: old!!.ticker,
                holder = current?.holder ?: old!!.holder,
                threshold = current?.threshold ?: old!!.threshold,
                oldPercentage = oldPercentage,
                newPercentage = newPercentage,
                changePercentage = newPercentage - oldPercentage,
                oldShares = oldShares,
                newShares = newShares,
                status = status,
                asOf = current?.asOf ?: old!!.asOf
            )
        }
    }
}
