// Vendored from https://github.com/mahmoodnizamani94-png/cloudstream-extensions-phisher (mirror of https://github.com/phisher98/cloudstream-extensions-phisher), GPL-3.0.
// Kept in sync manually: re-copy this file when upstream changes, then bump `version`
// in the module build script so devices see an update.
package com.phisher98

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class AllMovieLandProviderPlugin: BasePlugin() {
    override fun load() {
        NetworkOptimizer.initialize()
        // All providers should be added in this manner. Please don't edit the providers list directly.
        registerMainAPI(AllMovieLandProvider())
    }
}
