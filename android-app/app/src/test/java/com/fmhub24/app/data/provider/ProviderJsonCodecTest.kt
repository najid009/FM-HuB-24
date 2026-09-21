package com.fmhub24.app.data.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderJsonCodecTest {
    @Test
    fun parsesHomeCatalogAndMapsMediaKind() {
        val result = ProviderJsonCodec.home(
            """{"ok":true,"data":[{"id":"m1","title":"Example","items":[{"id":"m1","title":"Example","kind":"movie","year":2026,"poster_url":"https://cdn.example/poster.jpg"}]}]}""",
        )

        assertTrue(result is ProviderResult.Success)
        val section = (result as ProviderResult.Success).value.single()
        assertEquals("Example", section.items.single().title)
        assertEquals(ProviderMediaKind.MOVIE, section.items.single().kind)
        assertEquals(2026, section.items.single().year)
    }

    @Test
    fun preservesProviderErrorWithoutInventingData() {
        val result = ProviderJsonCodec.search(
            """{"ok":false,"error":{"kind":"unavailable","message":"source is down"}}""",
        )

        assertTrue(result is ProviderResult.Failure)
        assertEquals("source is down", (result as ProviderResult.Failure).message)
    }

    @Test
    fun rejectsMalformedPayloadAsNonRetryableFailure() {
        val result = ProviderJsonCodec.details("not-json")

        assertTrue(result is ProviderResult.Failure)
        assertTrue(!(result as ProviderResult.Failure).retryable)
    }
}
