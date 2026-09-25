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

            val sharedStrings =
                parseSharedStrings(entries["xl/sharedStrings.xml"])

            val sheetXml =
                entries["xl/worksheets/sheet1.xml"]
                    ?: throw IllegalStateException("Sheet Excel tidak ditemukan")

            val document = DocumentBuilderFactory
                .newInstance()
                .newDocumentBuilder()
                .parse(sheetXml.inputStream())

            val rowNodes = document.getElementsByTagName("row")
            val rows = mutableListOf<OwnershipRow>()

            var asOf = ""

            /*
             * Baca seluruh sheet per BARIS.
             * Ini penting supaya ticker, holder, saham dan persentase
             * berasal dari baris Excel yang sama.
             */
            for (i in 0 until rowNodes.length) {
                val rowNode = rowNodes.item(i)
                val cells = rowNode.childNodes

                val values = mutableMapOf<String, String>()

                for (j in 0 until cells.length) {
                    val cell = cells.item(j)

                    if (cell.nodeName != "c") continue

                    val ref = cell.attributes
                        ?.getNamedItem("r")
                        ?.nodeValue
                        ?: continue

                    val column = ref
                        .filter { it.isLetter() }
                        .uppercase()

                    values[column] =
                        readCellValue(cell, sharedStrings).trim()
                }

                /*
                 * Ambil tanggal dari judul jika tersedia.
                 * Biasanya ada pada bagian awal Excel.
                 */
                if (asOf.isBlank()) {
                    values.values.firstOrNull {
                        it.contains("per tanggal", ignoreCase = true)
                    }?.let {
                        asOf = extractDate(it)
                    }
                }

                /*
                 * Fallback apabila tanggal tidak ditemukan
                 * dari shared string/judul.
                 */
                if (asOf.isBlank()) {
                    values.values.firstOrNull {
                        Regex("""\d{1,2}[-/]\d{1,2}[-/]\d{2,4}""")
                            .containsMatchIn(it)
                    }?.let {
                        asOf = it
                    }
                }

                if (values.isEmpty()) continue

                val ticker = values["B"]
                    ?.trim()
                    ?.uppercase()
                    ?: ""

                val holder = values["E"]
                    ?.trim()
                    ?: ""

                val shares = parseLong(values["P"])
                val percentage = parseDouble(values["Q"])

                /*
                 * Abaikan header dan baris kosong.
                 */
                if (ticker.isBlank() || holder.isBlank()) continue

                /*
                 * Hanya masukkan data yang memenuhi threshold.
                 */
                if (percentage == null || percentage < minimum) continue

                rows.add(
                    OwnershipRow(
                        ticker = ticker,
                        holder = holder,
                        percentage = percentage,
                        shares = shares,
                        asOf = asOf,
                        threshold = threshold,
                        sourceUrl = sourceUrl
                    )
                )
            }

            android.util.Log.d(
                "A1_OWNERSHIP",
                "XLSX selesai dibaca: rows=${rows.size}, asOf=$asOf, minimum=$minimum"
            )

            rows.take(20).forEachIndexed { index, row ->
                android.util.Log.d(
                    "A1_OWNERSHIP",
                    "ROW[$index] ticker=${row.ticker} holder=${row.holder} shares=${row.shares} percentage=${row.percentage}"
                )
            }

            if (rows.isEmpty()) {
                android.util.Log.e(
                    "A1_OWNERSHIP",
                    "TIDAK ADA DATA OWNERSHIP YANG LOLOS FILTER >= $minimum%"
                )
            }

            return normalize(rows)
        }
    }

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

            val item = items.item(i) as org.w3c.dom.Element
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

    private fun parseLong(value: String?): Long? {
        if (value == null) return null

        val v = value
            .replace("%", "")
            .replace(" ", "")
            .trim()

        if (v.isBlank()) return null

        return v
            .replace(",", "")
            .replace(".", "")
            .toLongOrNull()
    }

    private fun parseDouble(value: String?): Double? {
        if (value == null) return null

        var v = value
            .replace("%", "")
            .replace(" ", "")
            .trim()

        if (v.isBlank()) return null

        v = when {
            v.contains(",") && v.contains(".") -> {
                if (v.lastIndexOf(",") > v.lastIndexOf(".")) {
                    v.replace(".", "").replace(",", ".")
                } else {
                    v.replace(",", "")
                }
            }
            v.contains(",") -> v.replace(",", ".")
            else -> v
        }

        return v.toDoubleOrNull()
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
