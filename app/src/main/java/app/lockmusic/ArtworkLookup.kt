package app.lockmusic

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Static artwork from the public iTunes Search API, used when the music app does not supply
 * usable artwork. Track text is cleaned first, then searched in three steps:
 * artist+title+album, then without the track, then without the album.
 */
object ArtworkLookup {
    private val junk = listOf(
        Regex("""\s*-\s*(EP|Single|Remastered.*|Deluxe.*)$""", RegexOption.IGNORE_CASE),
        Regex("""\s*[(\[]\s*(feat|ft|with)\.?[^)\]]*[)\]]""", RegexOption.IGNORE_CASE),
    )

    fun clean(s: String): String = junk.fold(s) { acc, r -> acc.replace(r, "") }.trim()

    suspend fun fetch(t: Track): Bitmap? = withContext(Dispatchers.IO) {
        val artist = clean(t.artist)
        val title = clean(t.title)
        val album = clean(t.album)
        val queries = listOf(
            "$artist $title $album",
            "$artist $album",
            "$artist $title",
        ).map { it.trim() }.filter { it.isNotBlank() }.distinct()
        for (q in queries) {
            val url = search(q, artist) ?: continue
            download(url)?.let { return@withContext it }
        }
        null
    }

    private fun search(query: String, artist: String): String? = runCatching {
        val term = URLEncoder.encode(query, "UTF-8")
        val body = get("https://itunes.apple.com/search?term=$term&entity=song&limit=5") ?: return null
        val results = JSONObject(body).optJSONArray("results") ?: return null
        val first = artist.lowercase().split(" ").firstOrNull { it.length > 2 }
        for (i in 0 until results.length()) {
            val r = results.getJSONObject(i)
            val who = r.optString("artistName").lowercase()
            if (first == null || who.contains(first)) {
                val art = r.optString("artworkUrl100")
                if (art.isNotBlank()) return art.replace("100x100", "1200x1200")
            }
        }
        null
    }.getOrNull()

    private fun get(url: String): String? {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        return try { if (c.responseCode == 200) c.inputStream.bufferedReader().readText() else null } finally { c.disconnect() }
    }

    private fun download(url: String): Bitmap? = runCatching {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 12000
        try { c.inputStream.use { BitmapFactory.decodeStream(it) } } finally { c.disconnect() }
    }.getOrNull()
}
