package com.a1stock.app.data

import android.content.Context

object IssuerSeeder {

    fun initialData(context: Context): List<IssuerEntity> {
        return try {
            context.assets.open("issuers.csv").bufferedReader().useLines { lines ->
                lines
                    .drop(1)
                    .mapNotNull { line ->
                        val parts = line.split(",")
                        if (parts.size >= 3) {
                            IssuerEntity(
                                ticker = parts[0].trim(),
                                name = parts[1].trim(),
                                sector = parts[2].trim()
                            )
                        } else {
                            null
                        }
                    }
                    .toList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
