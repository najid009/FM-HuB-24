package com.fmhub24.extensions.archive

import com.fmhub.plugin.api.FMHubBasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

/**
 * Plugin entry point.
 *
 * The CloudStream gradle plugin finds this class through [CloudstreamPlugin] and writes
 * `manifest.json` with `pluginClassName = com.fmhub24.extensions.archive.FMHubArchivePlugin`.
 * The host then instantiates it (no-arg), calls `load()`, and every provider registered inside
 * that call becomes visible in the app. Two consequences worth remembering:
 *
 *  - the class must be `public` with a no-arg constructor (no constructor params, no `inner`),
 *  - `load()` must never throw — a throw here is what the old build reported as
 *    "none produced a MainAPI provider".
 */
@CloudstreamPlugin
class FMHubArchivePlugin : FMHubBasePlugin() {

    override fun providers() = listOf(InternetArchiveProvider())
}
