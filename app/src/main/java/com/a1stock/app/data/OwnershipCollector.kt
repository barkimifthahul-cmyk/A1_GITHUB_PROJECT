package com.a1stock.app.data

import android.content.Context
import android.net.Uri
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

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

    fun loadFromUri(
        context: Context,
        uri: Uri,
        threshold: String = ">=5%",
        sourceUrl: String = "IDX/KSEI"
    ): List<OwnershipEntity> {

        val name = getFileName(context, uri)?.lowercase() ?: ""

        return if (name.endsWith(".xlsx")) {
            loadFromXlsx(context, uri, threshold, sourceUrl)
        } else {
            loadFromCsv(context, uri, threshold, sourceUrl)
        }
    }

    private fun loadFromCsv(
        context: Context,
        uri: Uri,
        threshold: String,
        sourceUrl: String
    ): List<OwnershipEntity> {

        val rows = mutableListOf<OwnershipRow>()
        val minimum = minimumPercentage(threshold)

        context.contentResolver.openInputStream(uri)
            ?.bufferedReader()
            ?.useLines { lines ->

                lines.drop(1).forEach { line ->

                    val columns = line
                        .split(",")
                        .map { it.trim().removeSurrounding("\"") }

                    if (columns.size < 12) return@forEach

                    val date = columns[0].trim()
                    val ticker = columns[1].trim()
                    val holder = columns[3].trim()

                    val shares = columns[10]
                        .replace(",", "")
                        .trim()
                        .toLongOrNull()

                    val percentage = columns[11]
                        .replace(",", ".")
                        .trim()
                        .toDoubleOrNull()

                    if (
                        ticker.isNotBlank() &&
                        holder.isNotBlank() &&
                        percentage != null &&
                        percentage >= minimum
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
            ?: throw IllegalStateException("Tidak dapat membuka file")

        return normalize(rows)
    }

    private fun loadFromXlsx(
        context: Context,
        uri: Uri,
        threshold: String,
        sourceUrl: String
    ): List<OwnershipEntity> {

        val minimum = minimumPercentage(threshold)

        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Tidak dapat membuka file Excel")

        input.use { stream ->

            val entries = unzipXlsx(stream)

            val sharedStrings = parseSharedStrings(
                entries["xl/sharedStrings.xml"]
            )

            val sheetXml = entries["xl/worksheets/sheet1.xml"]
                ?: throw IllegalStateException("Sheet Excel tidak ditemukan")

            val document = DocumentBuilderFactory
                .newInstance()
                .newDocumentBuilder()
                .parse(sheetXml.inputStream())

            val cells = document.getElementsByTagName("c")
            val rows = mutableListOf<OwnershipRow>()

            var asOf = ""

            val firstStrings = sharedStrings.take(1)
            if (firstStrings.isNotEmpty()) {
                asOf = extractDate(firstStrings[0])
            }

            for (i in 0 until cells.length) {

                val cell = cells.item(i)
                val ref = cell.attributes
                    ?.getNamedItem("r")
                    ?.nodeValue
                    ?: continue

                val rowNumber = ref
                    .filter { it.isDigit() }
                    .toIntOrNull()
                    ?: continue

                if (rowNumber < 5) continue

                val column = ref
                    .filter { it.isLetter() }
                    .uppercase()

                val value = readCellValue(cell, sharedStrings)

                when (column) {
                    "B" -> {
                        currentTicker = value.trim()
                    }

                    "E" -> {
                        currentHolder = value.trim()
                    }

                    "P" -> {
                        currentShares = parseLong(value)
                    }

                    "Q" -> {
                        val percentage = parseDouble(value)

                        if (
                            currentTicker.isNotBlank() &&
                            currentHolder.isNotBlank() &&
                            percentage != null &&
                            percentage >= minimum
                        ) {
                            rows.add(
                                OwnershipRow(
                                    ticker = currentTicker,
                                    holder = currentHolder,
                                    percentage = percentage,
                                    shares = currentShares,
                                    asOf = asOf,
                                    threshold = threshold,
                                    sourceUrl = sourceUrl
                                )
                            )
                        }

                        currentShares = null
                    }
                }

                if (column == "A") {
                    // Tidak digunakan; nomor baris hanya informasi tampilan Excel.
                }
            }

            return normalize(rows)
        }
    }

    private var currentTicker = ""
    private var currentHolder = ""
    private var currentShares: Long? = null

    private fun unzipXlsx(input: InputStream): Map<String, ByteArray> {

        val result = mutableMapOf<String, ByteArray>()

        ZipInputStream(input).use { zip ->

            while (true) {

                val entry = zip.nextEntry ?: break

                if (!entry.isDirectory) {
                    result[entry.name] = zip.readBytes()
                }

                zip.closeEntry()
            }
        }

        return result
    }

    private fun parseSharedStrings(xmlBytes: ByteArray?): List<String> {

        if (xmlBytes == null) return emptyList()

        val document = DocumentBuilderFactory
            .newInstance()
            .newDocumentBuilder()
            .parse(xmlBytes.inputStream())

        val strings = mutableListOf<String>()
        val items = document.getElementsByTagName("si")

        for (i in 0 until items.length) {

            val item = items.item(i)
            val textNodes = item.getElementsByTagName("t")

            val builder = StringBuilder()

            for (j in 0 until textNodes.length) {
                builder.append(textNodes.item(j).textContent)
            }

            strings.add(builder.toString())
        }

        return strings
    }

    private fun readCellValue(
        cell: org.w3c.dom.Node,
        sharedStrings: List<String>
    ): String {

        val type = cell.attributes
            ?.getNamedItem("t")
            ?.nodeValue

        val values = (cell as org.w3c.dom.Element)
            .getElementsByTagName("v")

        if (values.length > 0) {

            val raw = values.item(0).textContent

            if (type == "s") {
                val index = raw.toIntOrNull()
                return if (
                    index != null &&
                    index >= 0 &&
                    index < sharedStrings.size
                ) {
                    sharedStrings[index]
                } else {
                    raw
                }
            }

            return raw
        }

        val inline = (cell as org.w3c.dom.Element)
            .getElementsByTagName("t")

        if (inline.length > 0) {
            return inline.item(0).textContent
        }

        return ""
    }

    private fun parseLong(value: String): Long? {
        return value
            .replace(",", "")
            .replace(".", "")
            .trim()
            .toLongOrNull()
    }

    private fun parseDouble(value: String): Double? {
        return value
            .replace(",", ".")
            .trim()
            .toDoubleOrNull()
    }

    private fun minimumPercentage(threshold: String): Double {
        return Regex("""([0-9]+(?:\.[0-9]+)?)""")
            .find(threshold)
            ?.groupValues
            ?.get(1)
            ?.toDoubleOrNull()
            ?: 5.0
    }

    private fun extractDate(title: String): String {
        val match = Regex(
            """per tanggal\s+(.+)$""",
            RegexOption.IGNORE_CASE
        ).find(title)

        return match?.groupValues?.get(1)?.trim()
            ?: title
    }

    private fun getFileName(
        context: Context,
        uri: Uri
    ): String? {

        var result: String? = null

        context.contentResolver
            .query(
                uri,
                arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )
            ?.use { cursor ->

                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(
                        android.provider.OpenableColumns.DISPLAY_NAME
                    )

                    if (index >= 0) {
                        result = cursor.getString(index)
                    }
                }
            }

        return result
    }

    private fun ByteArray.inputStream() =
        java.io.ByteArrayInputStream(this)
}
