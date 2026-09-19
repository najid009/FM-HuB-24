package com.fmhub24.app.di

import com.fmhub24.app.data.remote.SupabaseClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideSupabaseClient(): SupabaseClient {
        return SupabaseClient()
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(supabaseClient: SupabaseClient): OkHttpClient {
        return supabaseClient.provideOkHttpClient()
    }
}
