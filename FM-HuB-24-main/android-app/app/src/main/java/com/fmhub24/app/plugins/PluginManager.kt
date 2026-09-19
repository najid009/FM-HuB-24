package com.fmhub24.app.plugins

import android.content.Context
import android.util.Log
import com.fmhub24.app.data.local.entity.CachedExtension
import com.fmhub24.app.util.CrashLog
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.extractorApis
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the set of loaded plugins: which files were tried, what they registered, and — the
 * part that used to be missing — *why* a file failed.
 *
 * Registration is global ([APIHolder.allProviders], because that is the contract
 * extensions write to), so loading must always unload first; otherwise toggling a provider
 * in Settings would duplicate every entry.
 */
@Singleton
class PluginManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pluginLoader: PluginLoader,
) {

    data class ProviderEntry(
        val provider: MainAPI,
        val pluginName: String,
        val filePath: String,
        val providerName: String,
    )

    data class LoadError(
        val file: String,
        val reason: String,
        val missingClass: String? = null,
        val cause: String? = null,
    )

    sealed class LoadingState {
        object Idle : LoadingState()
        data class Loading(val total: Int) : LoadingState()
        data class Progress(val name: String, val index: Int, val total: Int) : LoadingState()
        data class Success(
            val providerCount: Int,
            val filesLoaded: Int,
            val errors: List<LoadError> = emptyList(),
            val warnings: List<String> = emptyList(),
        ) : LoadingState()

        data class Error(val message: String, val errors: List<LoadError> = emptyList()) : LoadingState()
    }

    private val _loadedProviders = MutableStateFlow<List<MainAPI>>(emptyList())
    val loadedProviders: StateFlow<List<MainAPI>> = _loadedProviders

    private val _loadingState = MutableStateFlow<LoadingState>(LoadingState.Idle)
    val loadingState: StateFlow<LoadingState> = _loadingState

    private val _entries = MutableStateFlow<List<ProviderEntry>>(emptyList())
    /** Everything the UI shows for "Extensions" (name + which file it came from). */
    val providerEntries: StateFlow<List<ProviderEntry>> = _entries

    private val loadedPlugins = LinkedHashMap<String, PluginLoader.LoadedPlugin>()

    suspend fun loadExtensions(cachedExtensions: List<CachedExtension>) = withContext(Dispatchers.IO) {
        // A device that has crashed twice in a row is not going to be helped by loading the same
        // dex again — and a crash during load usually takes the app down before anything can be
        // read. Skip the plugin files, stay open, and let the operator turn it off from the crash
        // screen ("Clear & retry") or by toggling an extension in Settings.
        if (CrashLog.recentCrashCount(context) >= 2) {
            val reason = "Skipped because the app crashed repeatedly on this device (safe mode). " +
                "Clear the crash log from the crash screen, or Settings -> Extensions -> reload, to try again."
            _loadingState.value = LoadingState.Error(reason, emptyList())
            _loadedProviders.value = emptyList()
            _entries.value = emptyList()
            Log.w(TAG, reason)
            return@withContext
        }

        val total = cachedExtensions.size
        _loadingState.value = LoadingState.Loading(total)
        unloadAllInternal()

        // Installed here rather than in Application.onCreate: see HostBootstrap for why the library
        // must not be touched while the process is still starting. Without this, every provider
        // fails its first request in a way that looks like the site being down.
        if (!HostBootstrap.ensure(context)) {
            val reason = HostBootstrap.failureReason ?: "Host runtime could not be initialized"
            _loadingState.value = LoadingState.Error(
                "The CloudStream host runtime did not start, so no provider can make a request. " +
                    "No file was skipped for being broken.\n\n$reason",
                emptyList()
            )
            Log.e(TAG, reason)
            return@withContext
        }

        val providers = mutableListOf<MainAPI>()
        val entries = mutableListOf<ProviderEntry>()
        val errors = mutableListOf<LoadError>()
        val warnings = mutableListOf<String>()
        val seenProviderClasses = mutableSetOf<String>()

        cachedExtensions.forEachIndexed { index, cached ->
            _loadingState.value = LoadingState.Progress(cached.name, index + 1, total)
            val file = File(cached.localFilePath)
            when (val outcome = pluginLoader.load(context, file)) {
                is PluginLoader.Outcome.Success -> {
                    val loaded = outcome.loaded
                    loadedPlugins[cached.id] = loaded
                    warnings += loaded.warnings.map { "${loaded.fileName}: $it" }
                    loaded.providers.forEach { api ->
                        if (seenProviderClasses.add(api.javaClass.name)) {
                            providers += api
                            entries += ProviderEntry(
                                provider = api,
                                pluginName = loaded.manifest.name ?: cached.name,
                                filePath = loaded.filePath,
                                providerName = api.name,
                            )
                        }
                    }
                    Log.i(
                        TAG,
                        "Loaded ${loaded.fileName}: ${loaded.providers.size} provider(s)" +
                            if (loaded.extractors.isEmpty()) "" else ", ${loaded.extractors.size} extractor(s)"
                    )
                }

                is PluginLoader.Outcome.Failed -> {
                    errors += LoadError(
                        file = outcome.failure.file,
                        reason = outcome.failure.reason,
                        missingClass = outcome.failure.missingClass,
                        cause = outcome.failure.cause,
                    )
                }
            }
        }

        _loadedProviders.value = providers
        _entries.value = entries
        _loadingState.value = when {
            providers.isNotEmpty() -> LoadingState.Success(
                providerCount = providers.size,
                filesLoaded = loadedPlugins.size,
                errors = errors,
                warnings = warnings.distinct(),
            )

            cachedExtensions.isNotEmpty() -> LoadingState.Error(
                message = buildString {
                    append("Failed to load any extension. ")
                    append(cachedExtensions.size)
                    append(" file(s) found but none produced a MainAPI provider.")
                    if (errors.isNotEmpty()) {
                        append("\n\n")
                        errors.forEachIndexed { i, e ->
                            append("${i + 1}. ${e.file}: ${e.reason}")
                            if (e.missingClass != null) append("\n   missing class: ${e.missingClass}")
                            append("\n")
                        }
                    }
                },
                errors = errors,
            )

            else -> LoadingState.Error("No extension files are cached yet.", errors)
        }
    }

    fun getLoadedProviders(): List<MainAPI> = _loadedProviders.value

    /** Providers belong to a plugin by exact class name; [APIHolder] already indexes them. */
    fun getProviderByName(name: String): MainAPI? =
        _loadedProviders.value.find { it.name == name } ?: APIHolder.getApiFromNameNull(name)

    fun getPlugins(): List<PluginLoader.LoadedPlugin> = loadedPlugins.values.toList()

    fun loadingStateValue(): LoadingState = _loadingState.value

    /** Remove everything this app instance registered (plugins stay in their own classloaders). */
    fun unloadAll() = unloadAllInternal()

    private fun unloadAllInternal() {
        loadedPlugins.values.forEach { loaded ->
            try {
                (loaded.plugin as? BasePlugin)?.beforeUnload()
            } catch (t: Throwable) {
                Log.w(TAG, "beforeUnload failed for ${loaded.fileName}", t)
            }
        }
        val registered = loadedPlugins.values.flatMap { it.providers }
        val registeredExtractors = loadedPlugins.values.flatMap { it.extractors }
        loadedPlugins.clear()

        if (registered.isNotEmpty()) {
            safe { APIHolder.allProviders.removeAll(registered) }
            registered.forEach { safe { APIHolder.removePluginMapping(it) } }
        }
        if (registeredExtractors.isNotEmpty()) {
            safe { extractorApis.removeAll(registeredExtractors) }
        }
        _loadedProviders.value = emptyList()
        _entries.value = emptyList()
    }

    fun clear() {
        unloadAllInternal()
        _loadingState.value = LoadingState.Idle
    }

    /** `ExtractorApi`s contributed by plugins — needed to resolve non-direct links. */
    fun getExtractors(): List<ExtractorApi> = extractorApis.toList()

    private inline fun safe(block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            Log.w(TAG, "ignored while unloading", t)
        }
    }

    private companion object {
        const val TAG = "FMHubPluginManager"
    }
}
