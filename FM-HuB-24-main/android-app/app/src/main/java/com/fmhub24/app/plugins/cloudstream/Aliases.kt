package com.fmhub24.app.plugins.cloudstream

/**
 * The app's own code keeps importing from this package, but every alias points at the REAL
 * CloudStream class from the `com.github.recloudstream.cloudstream:library` artifact.
 *
 * This is deliberate: plugin dex files reference `com.lagradost.cloudstream3.*` by name, so
 * the host must expose *those exact classes*. Anything here that re-declared a type instead
 * of aliasing it would create a second, incompatible `MainAPI` — which is precisely how the
 * old build ended up with "1 file(s) found but none produced a MainAPI provider".
 *
 * Prefer importing `com.lagradost.cloudstream3.*` directly in new code; this file only exists
 * to keep the existing view models compiling while the migration settles.
 */

typealias MainAPI = com.lagradost.cloudstream3.MainAPI
typealias BasePlugin = com.lagradost.cloudstream3.plugins.BasePlugin
typealias CloudstreamPlugin = com.lagradost.cloudstream3.plugins.CloudstreamPlugin

typealias LoadResponse = com.lagradost.cloudstream3.LoadResponse
typealias MovieLoadResponse = com.lagradost.cloudstream3.MovieLoadResponse
typealias TvSeriesLoadResponse = com.lagradost.cloudstream3.TvSeriesLoadResponse
typealias AnimeLoadResponse = com.lagradost.cloudstream3.AnimeLoadResponse
typealias LiveStreamLoadResponse = com.lagradost.cloudstream3.LiveStreamLoadResponse
typealias TorrentLoadResponse = com.lagradost.cloudstream3.TorrentLoadResponse
typealias Episode = com.lagradost.cloudstream3.Episode

typealias SearchResponse = com.lagradost.cloudstream3.SearchResponse
typealias MovieSearchResponse = com.lagradost.cloudstream3.MovieSearchResponse
typealias TvSeriesSearchResponse = com.lagradost.cloudstream3.TvSeriesSearchResponse
typealias AnimeSearchResponse = com.lagradost.cloudstream3.AnimeSearchResponse
typealias LiveSearchResponse = com.lagradost.cloudstream3.LiveSearchResponse
typealias SearchQuality = com.lagradost.cloudstream3.SearchQuality

typealias HomePageResponse = com.lagradost.cloudstream3.HomePageResponse
typealias HomePageList = com.lagradost.cloudstream3.HomePageList
typealias MainPageRequest = com.lagradost.cloudstream3.MainPageRequest
typealias MainPageData = com.lagradost.cloudstream3.MainPageData

typealias TvType = com.lagradost.cloudstream3.TvType
typealias DubStatus = com.lagradost.cloudstream3.DubStatus
typealias ShowStatus = com.lagradost.cloudstream3.ShowStatus
typealias SubtitleFile = com.lagradost.cloudstream3.SubtitleFile

// These two live in the `utils` package upstream — that is the name plugin bytecode uses,
// so the aliases must not "simplify" it.
typealias ExtractorLink = com.lagradost.cloudstream3.utils.ExtractorLink
typealias ExtractorApi = com.lagradost.cloudstream3.utils.ExtractorApi
typealias ExtractorLinkType = com.lagradost.cloudstream3.utils.ExtractorLinkType
