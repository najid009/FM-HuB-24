package com.fmhub24.app.media

import android.util.Base64
import com.fmhub24.app.plugins.cloudstream.ExtractorLink
import java.util.UUID

/** Standard metadata contract for extensions that expose DRM-protected streams. */
data class DrmConfig(
    val scheme: UUID,
    val licenseUrl: String,
    val licenseHeaders: Map<String, String>,
    val offlineKeySetId: ByteArray? = null,
) {
    companion object {
        private val widevine = UUID.fromString("edef8ba9-79d6-4ace-a3c8-27dcd51d21ed")
        private val playReady = UUID.fromString("9a04f079-9840-4286-ab92-e65be0885f95")
        private val clearKey = UUID.fromString("e2719d58-a985-b3c9-781a-b030af78d30e")

        fun from(link: ExtractorLink): DrmConfig? {
            val headers = link.headers
            fun value(vararg keys: String): String? = headers.entries.firstOrNull { entry ->
                keys.any { it.equals(entry.key, ignoreCase = true) }
            }?.value?.takeIf { it.isNotBlank() }
            val licenseUrl = value("DRM-License-Url", "DRM-License", "license_url", "license") ?: return null
            val scheme = when (value("DRM-Scheme", "drm_scheme")?.lowercase()) {
                "playready" -> playReady
                "clearkey", "clear_key" -> clearKey
                else -> widevine
            }
            val requestHeaders = headers.filterKeys { key ->
                key.startsWith("DRM-Header-", ignoreCase = true) ||
                    key.equals("Authorization", ignoreCase = true) ||
                    key.equals("X-Auth-Token", ignoreCase = true)
            }.mapKeys { (key, _) -> key.substringAfter("DRM-Header-", key) }
            val keySetId = value("DRM-KeySet-Id", "drm_key_set_id")?.let {
                runCatching { Base64.decode(it, Base64.DEFAULT) }.getOrNull()
            }
            return DrmConfig(scheme, licenseUrl, requestHeaders, keySetId)
        }
    }
}
