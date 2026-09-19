package com.fmhub.plugin.api

/**
 * FMHub24 plugin contract version.
 *
 * The CloudStream types themselves (`MainAPI`, `LoadResponse`, `ExtractorLink`, …) are
 * versioned by the CloudStream revision pinned in `android-app/gradle.properties`
 * (`CLOUDSTREAM_VERSION`) — see [BuildConfig.CLOUDSTREAM_VERSION].
 *
 * This number versions *our* additions on top of it: [FMHubBasePlugin], [FMHubProvider]
 * and the host-side `com.lagradost.cloudstream3.utils.DataStore` shim.
 *
 * Rules:
 *  - Bump [API_VERSION] whenever an extension compiled against the old contract would stop
 *    working with the host (a renamed helper, a changed signature, a removed default).
 *  - Never reuse a number for an incompatible change.
 *
 * The host refuses extensions whose `manifest.json` declares a different `fmhubApiVersion`
 * and prints a readable error instead of the old "none produced a MainAPI provider".
 * Community extensions (built without our plugin-api) simply omit that field and are
 * treated as plain CloudStream plugins.
 */
object FMHubApi {
    const val API_VERSION: Int = 1

    /** Extra manifest.json key written by the FMHub extension build tooling. */
    const val MANIFEST_API_VERSION_FIELD = "fmhubApiVersion"

    /** Minimum CloudStream "API version" this host can serve (informational, printed in logs). */
    const val MIN_EXTENSION_FILE_API = 0
}
