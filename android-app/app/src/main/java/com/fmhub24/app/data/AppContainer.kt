package com.fmhub24.app.data

import android.content.Context
import com.fmhub24.app.data.local.DataStoreSettingsRepository
import com.fmhub24.app.data.local.InMemoryCollectionRepository
import com.fmhub24.app.data.provider.NativeContentRepository
import com.fmhub24.app.domain.repository.CollectionRepository
import com.fmhub24.app.domain.repository.ContentRepository
import com.fmhub24.app.domain.repository.SettingsRepository

class AppContainer(context: Context) {
    val nativeContentRepository = NativeContentRepository()
    val contentRepository: ContentRepository = nativeContentRepository
    val collectionRepository: CollectionRepository = InMemoryCollectionRepository()
    val settingsRepository: SettingsRepository = DataStoreSettingsRepository(context)
}
