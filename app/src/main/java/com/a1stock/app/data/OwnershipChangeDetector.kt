package com.a1stock.app.data

data class OwnershipChange(
    val ticker: String,
    val holder: String,
    val threshold: String,
    val oldPercentage: Double?,
    val newPercentage: Double?,
    val oldShares: Long?,
    val newShares: Long?,
    val asOf: String
)

class OwnershipChangeDetector {

    fun detect(
        oldRows: List<OwnershipEntity>,
        newRows: List<OwnershipEntity>
    ): List<OwnershipChange> {

        val oldMap = oldRows.associateBy {
            "${it.ticker}|${it.holder}|${it.threshold}"
        }

        return newRows.mapNotNull { current ->

            val key =
                "${current.ticker}|${current.holder}|${current.threshold}"

            val old = oldMap[key] ?: return@mapNotNull null

            val percentageChanged =
                old.percentage != current.percentage

            val sharesChanged =
                old.shares != current.shares

            if (!percentageChanged && !sharesChanged) {
                return@mapNotNull null
            }

            OwnershipChange(
                ticker = current.ticker,
                holder = current.holder,
                threshold = current.threshold,
                oldPercentage = old.percentage,
                newPercentage = current.percentage,
                oldShares = old.shares,
                newShares = current.shares,
                asOf = current.asOf
            )
        }
    }
}
