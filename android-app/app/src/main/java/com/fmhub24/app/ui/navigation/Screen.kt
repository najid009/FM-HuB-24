package com.fmhub24.app.ui.navigation

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Home : Screen("home")
    object Search : Screen("search")
    object Favorites : Screen("favorites")
    object Settings : Screen("settings")
    object Category : Screen("category/{providerName}/{categoryName}") {
        fun createRoute(providerName: String, categoryName: String): String =
            "category/${encodeNavSegment(providerName)}/${encodeNavSegment(categoryName)}"
    }
    object Downloads : Screen("downloads")
    object OfflinePlayer : Screen("offline-player/{path}/{title}") {
        fun createRoute(path: String, title: String): String =
            "offline-player/${encodeNavSegment(path)}/${encodeNavSegment(title)}"
    }
    object Details : Screen("details/{url}/{apiName}") {
        fun createRoute(url: String, apiName: String): String {
            // Encode url to be safe for navigation.
            // URLEncoder turns space into '+', but the navigation framework decodes
            // path segments with URL-decoding (no '+' -> space), so normalize '+'
            // to '%20' to survive the round-trip exactly once.
            val encodedUrl = encodeNavSegment(url)
            return "details/$encodedUrl/$apiName"
        }
    }
    object Player : Screen("player/{url}/{apiName}/{name}/{posterUrl}") {
        fun createRoute(url: String, apiName: String, name: String, posterUrl: String?, episodeData: String? = null, episodeName: String? = null): String {
            val encodedUrl = encodeNavSegment(url)
            val encodedName = encodeNavSegment(name)
            val encodedPoster = encodeNavSegment(posterUrl ?: "")
            var base = "player/$encodedUrl/$apiName/$encodedName/$encodedPoster"
            if (episodeData != null) {
                val encEpData = encodeNavSegment(episodeData)
                val encEpName = encodeNavSegment(episodeName ?: "")
                base += "?episodeData=$encEpData&episodeName=$encEpName"
            }
            return base
        }
    }
}

/** URLEncoder + '+' normalization so values survive exactly one framework decode. */
private fun encodeNavSegment(value: String): String =
    java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")
