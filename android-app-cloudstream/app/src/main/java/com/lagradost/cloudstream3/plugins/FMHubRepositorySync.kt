package com.lagradost.cloudstream3.plugins

import android.util.Log
import com.lagradost.cloudstream3.BuildConfig
import com.lagradost.cloudstream3.app
import org.json.JSONArray
import org.json.JSONObject

/**
 * Synchronizes repositories approved by the FMHuB24 admin panel into CloudStream's native
 * RepositoryManager. The endpoint is intentionally a small JSON contract so it can be served by
 * Supabase Storage, a CDN, or the admin backend without adding another networking dependency.
 *
 * Expected payload:
 * {
 *   "repositories": [
 *     {"name":"FMHuB24 Sources","url":"https://example/repo.json","iconUrl":"..."}
 *   ]
 * }
 *
 * Only HTTPS URLs are accepted. The sync never removes user-added repositories and never downloads
 * plugin code itself; CloudStream's existing repository/plugin manager performs that step with its
 * normal hash and version checks.
 */
object FMHubRepositorySync {
    private const val TAG = "FMHubRepositorySync"

    suspend fun sync() {
        val configUrl = BuildConfig.FMHUB_REPOSITORY_CONFIG_URL.trim()
        if (configUrl.isBlank()) return
        if (!configUrl.startsWith("https://", ignoreCase = true)) {
            Log.w(TAG, "Ignoring non-HTTPS repository config URL")
            return
        }

        runCatching {
            val payload = app.get(configUrl, timeout = 15).text
            val repositories = if (payload.trimStart().startsWith("[")) {
                JSONArray(payload)
            } else {
                JSONObject(payload).optJSONArray("repositories") ?: JSONArray()
            }
            for (index in 0 until repositories.length()) {
                val item = repositories.optJSONObject(index) ?: continue
                val url = item.optString("url").trim()
                if (!url.startsWith("https://", ignoreCase = true)) continue
                val name = item.optString("name", "FMHuB24 Repository").trim()
                val iconUrl = item.optString("iconUrl", item.optString("icon_url")).trim().ifBlank { null }
                RepositoryManager.addRepository(
                    com.lagradost.cloudstream3.ui.settings.extensions.RepositoryData(
                        iconUrl = iconUrl,
                        name = name.ifBlank { "FMHuB24 Repository" },
                        url = url,
                    )
                )
            }
        }.onFailure { error ->
            // Remote admin configuration must never prevent the base app from opening.
            Log.w(TAG, "Could not sync admin repositories", error)
        }
    }
}
