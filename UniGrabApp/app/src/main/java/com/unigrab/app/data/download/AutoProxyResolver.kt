package com.unigrab.app.data.download

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import java.util.Collections

object AutoProxyResolver {
    private var workingProxy: String? = null
    private val failedProxies = Collections.synchronizedSet(mutableSetOf<String>())

    // Pre-verified proxy APIs that return recently-checked, live proxies
    private data class ProxySource(
        val url: String,
        val type: SourceType,
        val defaultProtocol: String = "http"
    )

    private enum class SourceType { PLAIN_TEXT, GEONODE_JSON, PROXYSCRAPE_JSON }

    private val PROXY_SOURCES = listOf(
        // HTTP/HTTPS proxies (prevents SOCKS5 2-bytes-missing truncation errors on mobile)
        ProxySource(
            "https://proxylist.geonode.com/api/proxy-list?limit=100&page=1&sort_by=lastChecked&sort_type=desc&protocols=http%2Chttps&speed=fast&country=US&country=CA&country=GB&country=DE&country=FR&country=NL&country=SG&country=JP",
            SourceType.GEONODE_JSON, "http"
        ),
        ProxySource(
            "https://api.proxyscrape.com/v2/?request=displayproxies&protocol=http&timeout=5000&country=us,ca,gb,de,fr,nl,sg,jp",
            SourceType.PLAIN_TEXT, "http"
        ),
        ProxySource(
            "https://api.proxyscrape.com/v4/free-proxy-list/get?request=display_proxies&proxy_format=protocolipport&format=json&limit=100&timeout=5000&country=us,ca,gb,de,fr,nl,sg,jp",
            SourceType.PROXYSCRAPE_JSON
        )
    )

    /**
     * Invalidate the cached working proxy so next call finds a fresh one.
     * Call this when a download fails through the cached proxy.
     */
    fun invalidateCurrent() {
        val old = workingProxy
        if (old != null) {
            failedProxies.add(old)
            workingProxy = null
            Log.d("AutoProxy", "Invalidated cached proxy: $old")
        }
    }

    suspend fun getProxy(targetUrl: String? = null, forceRefresh: Boolean = false): String? = withContext(Dispatchers.IO) {
        // Return cached proxy only if not force-refreshing
        if (workingProxy != null && !forceRefresh) {
            return@withContext workingProxy
        }

        // If force-refreshing, invalidate the old one
        if (forceRefresh) {
            invalidateCurrent()
        }

        Log.d("AutoProxy", "Searching for a working proxy...")
        val candidates = mutableListOf<Pair<String, String>>() // protocol to host:port

        for (source in PROXY_SOURCES) {
            try {
                val fetched = when (source.type) {
                    SourceType.PLAIN_TEXT -> fetchPlainTextList(source.url, source.defaultProtocol)
                    SourceType.GEONODE_JSON -> fetchGeonodeJson(source.url, source.defaultProtocol)
                    SourceType.PROXYSCRAPE_JSON -> fetchProxyScrapeJson(source.url)
                }
                candidates.addAll(fetched)
                Log.d("AutoProxy", "Fetched ${fetched.size} proxies from ${source.type}")
            } catch (e: Exception) {
                Log.e("AutoProxy", "Failed to fetch from ${source.url}: ${e.message}")
            }
            if (candidates.size >= 500) break
        }

        // Remove already-failed proxies
        candidates.removeAll { (protocol, addr) -> failedProxies.contains("$protocol://$addr") }

        if (candidates.isEmpty()) {
            Log.e("AutoProxy", "No proxy candidates available after filtering.")
            return@withContext null
        }

        candidates.shuffle()

        // Test in waves — more waves and larger batches for better odds
        val waveSize = 25
        val maxWaves = 6 // test up to 150 proxies

        for (wave in 0 until maxWaves) {
            val start = wave * waveSize
            val end = minOf(start + waveSize, candidates.size)
            if (start >= candidates.size) break

            val batch = candidates.subList(start, end)
            Log.d("AutoProxy", "Wave ${wave + 1}/$maxWaves: testing ${batch.size} proxies...")

            val deferreds = batch.map { (protocol, addr) ->
                async {
                    if (testProxy(protocol, addr, targetUrl)) "$protocol://$addr"
                    else {
                        failedProxies.add("$protocol://$addr")
                        null
                    }
                }
            }

            val results = deferreds.awaitAll()
            val winner = results.firstOrNull { it != null }
            if (winner != null) {
                Log.d("AutoProxy", "✓ Verified working proxy: $winner")
                workingProxy = winner
                return@withContext winner
            }
        }

        Log.e("AutoProxy", "✗ Could not find a working proxy after testing ${minOf(maxWaves * waveSize, candidates.size)} candidates.")
        return@withContext null
    }

    // --- Fetch methods ---

    private fun fetchPlainTextList(urlStr: String, protocol: String): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")

        BufferedReader(InputStreamReader(conn.inputStream)).use { reader ->
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val trimmed = line?.trim() ?: continue
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
                if (trimmed.contains(":")) {
                    val parts = trimmed.split(":")
                    if (parts.size == 2 && parts[1].all { it.isDigit() }) {
                        result.add(protocol to trimmed)
                    }
                }
            }
        }
        return result
    }

    private fun fetchGeonodeJson(urlStr: String, defaultProtocol: String): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        conn.setRequestProperty("User-Agent", "Mozilla/5.0")

        val responseText = conn.inputStream.bufferedReader().use { it.readText() }
        val json = JSONObject(responseText)
        val data = json.optJSONArray("data") ?: return result

        for (i in 0 until data.length()) {
            val entry = data.getJSONObject(i)
            val ip = entry.optString("ip", "")
            val port = entry.optString("port", "")
            val country = entry.optString("country", "").lowercase()
            val protocols = entry.optJSONArray("protocols")
            if (ip.isBlank() || port.isBlank() || country == "in") continue

            val protocol = if (protocols != null && protocols.length() > 0) {
                protocols.getString(0).lowercase()
            } else defaultProtocol

            val normalizedProtocol = when {
                protocol.contains("socks5") -> "socks5"
                protocol.contains("socks4") -> "socks4"
                else -> "http"
            }
            result.add(normalizedProtocol to "$ip:$port")
        }
        return result
    }

    private fun fetchProxyScrapeJson(urlStr: String): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        conn.setRequestProperty("User-Agent", "Mozilla/5.0")

        val responseText = conn.inputStream.bufferedReader().use { it.readText() }
        val json = JSONObject(responseText)
        val proxies = json.optJSONArray("proxies") ?: return result

        for (i in 0 until proxies.length()) {
            val entry = proxies.getJSONObject(i)
            val ip = entry.optString("ip", "")
            val port = entry.optInt("port", 0)
            val code = entry.optString("code", "").lowercase()
            val protocol = entry.optString("protocol", "http").lowercase()
            if (ip.isBlank() || port == 0 || code == "in") continue

            val normalizedProtocol = when {
                protocol.contains("socks5") -> "socks5"
                protocol.contains("socks4") -> "socks4"
                else -> "http"
            }
            result.add(normalizedProtocol to "$ip:$port")
        }
        return result
    }

    // --- Proxy validation ---

    private fun testProxy(protocol: String, addr: String, targetUrl: String?): Boolean {
        try {
            val parts = addr.split(":")
            val host = parts[0]
            val port = parts[1].toInt()

            val proxyType = when (protocol) {
                "socks5", "socks4" -> Proxy.Type.SOCKS
                else -> Proxy.Type.HTTP
            }

            val proxy = Proxy(proxyType, InetSocketAddress(host, port))

            // Test against Cloudflare trace to see if we can reach Cloudflare-protected sites like PornHub
            val testUrl = "https://www.cloudflare.com/cdn-cgi/trace"

            val conn = URL(testUrl).openConnection(proxy) as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
            conn.connect()
            val code = conn.responseCode

            // Only accept success (2xx) and redirects (3xx)
            // Reject 403/429/5xx — those mean the proxy is blocked too
            return code in 200..399
        } catch (e: Exception) {
            return false
        }
    }
}
