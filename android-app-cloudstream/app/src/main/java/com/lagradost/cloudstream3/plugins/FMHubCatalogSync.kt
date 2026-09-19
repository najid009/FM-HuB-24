package com.lagradost.cloudstream3.plugins

import android.util.Log
import com.lagradost.cloudstream3.BuildConfig
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.DataStore.getKey
import com.lagradost.cloudstream3.utils.DataStore.setKey
import org.json.JSONObject

/**
 * Fetches the FMHuB24 catalogue contract before native providers load. The raw JSON is cached so a
 * future category-aware home renderer can keep the last known rules when the network is unavailable.
 * This is deliberately fail-open: a control-plane outage must not blank the app.
 */
object FMHubCatalogSync {
    private const val TAG = "FMHubCatalogSync"
    private const val PREFS = "fmhub24_catalog"
    private const val CATALOG_JSON = "catalog_json"
    private const val TRENDING_JSON = "trending_json"

    suspend fun sync() {
        syncEndpoint(BuildConfig.FMHUB_CATALOG_CONFIG_URL, CATALOG_JSON)
        syncEndpoint(BuildConfig.FMHUB_TMDB_TRENDING_URL, TRENDING_JSON)
    }

    fun cachedCatalog(): JSONObject? = readJson(CATALOG_JSON)
    fun cachedTrending(): JSONObject? = readJson(TRENDING_JSON)

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
                app.setKey(PREFS, key, payload)
            }
        }.onFailure { error -> Log.w(TAG, "Could not sync $key", error) }
    }

    private fun readJson(key: String): JSONObject? = runCatching {
        app.getKey<String>(PREFS, key)?.let(::JSONObject)
    }.getOrNull()
}
