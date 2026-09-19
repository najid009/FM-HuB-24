package com.fmhub.plugin.api

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.utils.ExtractorApi

/**
 * Entry point of an FMHub `.cs3` plugin.
 *
 * CloudStream loads a plugin by reading `manifest.json -> pluginClassName`, instantiating
 * that class (no-arg constructor) and calling [BasePlugin.load]. Providers only become
 * visible to the host *inside* `load()`, through `registerMainAPI`, so this is the one place
 * where a plugin must never throw.
 *
 * Extensions written for FMHub extend this class instead of [BasePlugin] directly: it adds
 *  - `@CloudstreamPlugin`-compatible shape (the gradle plugin writes the manifest for us),
 *  - a safe registration path that skips providers which failed to construct (a broken
 *    provider no longer takes the whole plugin down with it),
 *  - [onProvidersRegistered] for late wiring (e.g. per-provider settings).
 *
 * Example:
 * ```kotlin
 * @CloudstreamPlugin
 * class FMHubArchivePlugin : FMHubBasePlugin() {
 *     override fun providers() = listOf(InternetArchiveProvider())
 * }
 * ```
 */
abstract class FMHubBasePlugin : BasePlugin() {

    /** Providers this plugin contributes. Called exactly once, from [load]. */
    open fun providers(): List<MainAPI> = emptyList()

    /** Embed/unmizer extractors (`ExtractorApi`) contributed by this plugin. */
    open fun extractors(): List<ExtractorApi> = emptyList()

    final override fun load() {
        providers().forEach { provider ->
            try {
                registerMainAPI(provider)
            } catch (t: Throwable) {
                android.util.Log.e(TAG, "Failed to register ${provider.javaClass.name}", t)
            }
        }
        extractors().forEach { extractor ->
            try {
                registerExtractorAPI(extractor)
            } catch (t: Throwable) {
                android.util.Log.e(TAG, "Failed to register ${extractor.javaClass.name}", t)
            }
        }
        try {
            onProvidersRegistered()
        } catch (t: Throwable) {
            android.util.Log.e(TAG, "onProvidersRegistered failed", t)
        }
    }

    /** Hook for anything that must run after every provider of this plugin is registered. */
    open fun onProvidersRegistered() {}

    companion object {
        const val TAG = "FMHubPlugin"
    }
}
