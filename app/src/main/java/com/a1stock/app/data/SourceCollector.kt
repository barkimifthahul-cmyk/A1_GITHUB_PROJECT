package com.a1stock.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.security.MessageDigest

class SourceCollector {
    private val client = OkHttpClient.Builder().build()
    private val announcements = "https://www.idx.co.id/id/berita/pengumuman"

    suspend fun collectIdxAnnouncements(): List<EventEntity> = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(announcements)
            .header("User-Agent", "A1 Stock Intelligence/1.3")
            .build()
        client.newCall(req).execute().use { response ->
            if (!response.isSuccessful) error("IDX HTTP ${response.code}")
            val html = response.body?.string().orEmpty()
            parseAnnouncements(html)
        }
    }

    private fun parseAnnouncements(html: String): List<EventEntity> {
        val doc = Jsoup.parse(html)
        val out = mutableListOf<EventEntity>()
        for (a in doc.select("a[href]")) {
            val title = a.text().trim()
            val href = a.absUrl("href").ifBlank { a.attr("href") }
            if (title.length < 12 || href.isBlank()) continue
            val ticker = Regex("\\[([A-Z0-9]{4,5})\\]").find(title)?.groupValues?.get(1)
            val category = classify(title)
            val fp = sha256("$ticker|$title|$href")
            out += EventEntity(fp,ticker,title,null,category,"IDX/BEI",href,null,null,System.currentTimeMillis())
        }
        return out.distinctBy { it.fingerprint }
    }

    private fun classify(t: String): String {
        val s=t.lowercase()
        return when {
            "right issue" in s || "rights issue" in s -> "RIGHT_ISSUE"
            "private placement" in s -> "PRIVATE_PLACEMENT"
            "akuisisi" in s -> "AKUISISI"
            "dividen" in s -> "DIVIDEN"
            "buyback" in s -> "BUYBACK"
            "rups" in s || "rapat umum" in s -> "RUPS"
            "direksi" in s || "komisaris" in s -> "MANAJEMEN"
            "afiliasi" in s -> "AFILIASI"
            "transaksi material" in s -> "TRANSAKSI_MATERIAL"
            "kepemilikan" in s || "pemegang saham" in s -> "KEPEMILIKAN"
            "suspensi" in s -> "SUSPENSI"
            else -> "LAINNYA"
        }
    }
    private fun sha256(s:String):String = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString(""){ "%02x".format(it) }
}
