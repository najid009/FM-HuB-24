package com.fmhub24.app.domain.model

import com.fmhub24.app.plugins.cloudstream.HomePageList
import com.fmhub24.app.plugins.cloudstream.SearchResponse

data class HomeSection(
    val providerName: String,
    val list: HomePageList
)

data class ContentItem(
    val searchResponse: SearchResponse,
    val providerName: String
)
