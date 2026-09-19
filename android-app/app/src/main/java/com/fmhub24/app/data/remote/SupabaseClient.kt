package com.fmhub24.app.data.remote

import com.fmhub24.app.BuildConfig
import com.fmhub24.app.data.remote.dto.ExtensionDto
import com.fmhub24.app.data.remote.dto.AppNoticeDto
import com.fmhub24.app.data.remote.dto.AppReleaseDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseClient @Inject constructor() {

    private val baseUrl = BuildConfig.SUPABASE_URL.let { url ->
        if (url.isBlank()) "https://placeholder.supabase.co/"
        else if (url.endsWith("/")) url else "$url/"
    }
    private val anonKey = BuildConfig.SUPABASE_ANON_KEY

    private val okHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private val api by lazy {
        retrofit.create(SupabaseApi::class.java)
    }

    /**
     * Returns false when the build was made without real Supabase credentials
     * (missing local.properties) or with placeholder/example values.
     */
    private fun isConfigured(): Boolean {
        val url = BuildConfig.SUPABASE_URL.trim()
        val key = BuildConfig.SUPABASE_ANON_KEY.trim()
        if (url.isBlank() || key.isBlank()) return false
        if (url.contains("placeholder.supabase.co", ignoreCase = true)) return false
        if (url.contains("your-project.supabase.co", ignoreCase = true)) return false
        if (key.contains("your-anon-key-here", ignoreCase = true)) return false
        if (key == "placeholder-key") return false
        return true
    }

    suspend fun fetchActiveExtensions(): Result<List<ExtensionDto>> = withContext(Dispatchers.IO) {
        // Check config
        if (!isConfigured()) {
            return@withContext Result.failure(
                Exception(
                    "This build has no content service configured. Add SUPABASE_URL and SUPABASE_ANON_KEY " +
                            "(the same project the Admin Panel uses) to android-app/local.properties " +
                            "and rebuild the app."
                )
            )
        }
        try {
            val result = api.getActiveExtensions(
                apiKey = anonKey,
                auth = "Bearer $anonKey"
            )
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchActiveNotices(): Result<List<AppNoticeDto>> = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext Result.success(emptyList())
        runCatching {
            api.getActiveNotices(apiKey = anonKey, auth = "Bearer $anonKey")
        }
    }

    suspend fun fetchLatestRelease(): Result<AppReleaseDto?> = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext Result.success(null)
        runCatching {
            api.getLatestRelease(apiKey = anonKey, auth = "Bearer $anonKey").firstOrNull()
        }
    }

    fun provideOkHttpClient(): OkHttpClient = okHttpClient
}
