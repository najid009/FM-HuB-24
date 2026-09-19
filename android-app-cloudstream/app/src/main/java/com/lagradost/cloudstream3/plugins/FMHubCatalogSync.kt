package com.lagradost.cloudstream3.plugins

import android.util.Log
import com.lagradost.cloudstream3.BuildConfig
import com.lagradost.cloudstream3.app
import org.json.JSONObject

/**
 * Fetches the FMHuB24 catalogue contract before native providers load. The raw JSON is cached so a
 * future category-aware home renderer can keep the last known rules when the network is unavailable.
 * This is deliberately fail-open: a control-plane outage must not blank the app.
 */
object FMHubCatalogSync {
    private const val TAG = "FMHubCatalogSync"
    @Volatile private var catalogPayload: String? = null
    @Volatile private var trendingPayload: String? = null

    suspend fun sync() {
        syncEndpoint(BuildConfig.FMHUB_CATALOG_CONFIG_URL, "catalog")
        syncEndpoint(BuildConfig.FMHUB_TMDB_TRENDING_URL, "trending")
    }

    fun cachedCatalog(): JSONObject? = catalogPayload?.let(::JSONObject)
    fun cachedTrending(): JSONObject? = trendingPayload?.let(::JSONObject)

    private suspend fun syncEndpoint(url: String, key: String) {
        val endpoint = url.trim()
        if (endpoint.isBlank()) return
        if (!endpoint.startsWith("https://", ignoreCase = true)) {
            Log.w(TAG, "Ignoring non-HTTPS endpoint: $key")
            return
        }
        runCatching {
            val payload = app.get(endpoint, timeout = 15).text
            val json = JSONObject(payload)
            if (json.optBoolean("success", true) || json.has("categories") || json.has("items")) {
                if (key == "catalog") catalogPayload = payload else trendingPayload = payload
            }
        }.onFailure { error -> Log.w(TAG, "Could not sync $key", error) }
    }
}
