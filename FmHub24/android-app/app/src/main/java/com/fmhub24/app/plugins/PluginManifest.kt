package com.fmhub24.app.plugins

import com.google.gson.annotations.SerializedName

/**
 * `manifest.json` as found inside a `.cs3`.
 *
 * CloudStream's own shape (`BasePlugin.Manifest`) is `name`, `pluginClassName`,
 * `requiresResources`, `version`. FMHub's extension tooling adds `fmhubApiVersion` so the
 * host can reject a plugin compiled against an incompatible contract *before* trying to link
 * it — the difference between "this file is for another app version" and a silent zero-provider.
 */
data class PluginManifest(
    @SerializedName("pluginClassName") val pluginClassName: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("version") val version: Int? = null,
    @SerializedName("requiresResources") val requiresResources: Boolean = false,
    @SerializedName("fmhubApiVersion") val fmhubApiVersion: Int? = null,
    @SerializedName("language") val language: String? = null,
) {
    /** Only class names CloudStream's loader would accept as a plugin entry point. */
    val hasPluginClass: Boolean get() = !pluginClassName.isNullOrBlank()
}
