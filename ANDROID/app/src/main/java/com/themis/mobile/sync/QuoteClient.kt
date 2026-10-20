package com.themis.mobile.sync

import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class Quote(val text: String, val author: String)

object QuoteClient {
    private val client = OkHttpClient.Builder().callTimeout(6, TimeUnit.SECONDS).build()

    /** Null when offline, timed out, or rate-limited, so the UI simply hides the quote. */
    suspend fun fetch(): Quote? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url("https://zenquotes.io/api/random").header("User-Agent", "themis-mobile").build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val o = JsonParser.parseString(resp.body?.string()).asJsonArray[0].asJsonObject
                val q = o["q"].asString.trim()
                val a = o["a"].asString.trim()
                if (q.isEmpty() || a.equals("zenquotes.io", ignoreCase = true)) null else Quote(q, a)
            }
        } catch (_: Exception) { null }
    }
}
