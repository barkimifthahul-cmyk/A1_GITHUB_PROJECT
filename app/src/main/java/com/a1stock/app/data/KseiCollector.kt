package com.a1stock.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.MessageDigest

class KseiCollector {

    private val client = OkHttpClient.Builder().build()

    private val api =
        "https://www.ksei.co.id/api/announcements"

    private val corporateActionId =
        "0f76d708-4ea3-4af1-bec3-d54cb17e403c"

    suspend fun collectCorporateActions(
        limit: Int = 20
    ): List<EventEntity> = withContext(Dispatchers.IO) {

        val url = api +
                "?locale=en" +
                "&filter%5Bannouncement_type_id%5D=$corporateActionId" +
                "&filter%5Bpublished%5D=true" +
                "&sort=-published_at" +
                "&page%5Bsize%5D=$limit" +
                "&page%5Bnumber%5D=1" +
                "&append=pdfEn"

        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("User-Agent", "A1 Stock Intelligence/1.3")
            .build()

        client.newCall(request).execute().use { response ->

            if (!response.isSuccessful) {
                error("KSEI HTTP ${response.code}")
            }

            val json = JSONObject(
                response.body?.string().orEmpty()
            )

            val data = json.optJSONArray("data")
                ?: return@withContext emptyList()

            val result = mutableListOf<EventEntity>()

            for (i in 0 until data.length()) {

                val item = data.optJSONObject(i)
                    ?: continue

                val title = item.optString("title")
                    .trim()

                if (title.isBlank()) continue

                val publishedAt =
                    item.optString("published_at")
                        .takeIf { it.isNotBlank() }

                val reference =
                    item.optString("ac_no_reff")
                        .takeIf { it.isNotBlank() }

                val caTypeId =
                    item.optString("ca_type_id")
                        .takeIf { it.isNotBlank() }

                val pdfUrl = extractPdfUrl(item)

                val ticker = extractTicker(
                    title,
                    pdfUrl
                )

                val category = classify(
                    title,
                    caTypeId
                )

                val sourceUrl =
                    pdfUrl ?: "https://www.ksei.co.id/en/publication/announcement"

                val fingerprint = sha256(
                    "KSEI|${item.optString("id")}|$title|$sourceUrl"
                )

                result += EventEntity(
                    fingerprint = fingerprint,
                    ticker = ticker,
                    title = title,
                    publishedAt = publishedAt,
                    category = category,
                    source = "KSEI",
                    sourceUrl = sourceUrl,
                    documentUrl = pdfUrl,
                    summary = reference,
                    firstSeenAt = System.currentTimeMillis()
                )
            }

            result.distinctBy { it.fingerprint }
        }
    }

    private fun extractPdfUrl(item: JSONObject): String? {

        val pdfArray = item.optJSONArray("pdfEn")
            ?: return null

        if (pdfArray.length() == 0) return null

        val pdf = pdfArray.optJSONObject(0)
            ?: return null

        return pdf.optString("url")
            .takeIf { it.isNotBlank() }
    }

    private fun extractTicker(
        title: String,
        pdfUrl: String?
    ): String? {

        if (pdfUrl != null) {
            val fileName =
                pdfUrl.substringAfterLast("/")

            val match = Regex(
                "^([A-Z0-9]{4,5})_"
            ).find(fileName)

            if (match != null) {
                return match.groupValues[1]
            }
        }

        val titleMatch = Regex(
            "\\(([A-Z0-9]{4,5})\\)"
        ).find(title)

        return titleMatch?.groupValues?.get(1)
    }

    private fun classify(
        title: String,
        caTypeId: String?
    ): String {

        val s = title.lowercase()

        return when {
            "right" in s ||
            "hmetd" in s ->
                "RIGHT_ISSUE"

            "buy back" in s ||
            "buyback" in s ->
                "BUYBACK"

            "tender offer" in s ->
                "TENDER_OFFER"

            "rups" in s ||
            "rupon" in s ||
            "rapat umum" in s ->
                "RUPS"

            "merger" in s ->
                "MERGER"

            "bonus" in s ->
                "BONUS_SHARE"

            "ipo" in s ->
                "IPO"

            "deviden" in s ||
            "dividen" in s ->
                "DIVIDEN"

            "interest" in s ||
            "redemption" in s ||
            "bunga" in s ->
                "INTEREST"

            else ->
                "CORPORATE_ACTION"
        }
    }

    private fun sha256(s: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(s.toByteArray())
            .joinToString("") {
                "%02x".format(it)
            }
}
