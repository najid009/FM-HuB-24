package com.fmhub24.app.data.repository

import android.util.Log
import com.fmhub24.app.data.local.dao.CachedExtensionDao
import com.fmhub24.app.data.local.entity.CachedExtension
import com.fmhub24.app.data.remote.SupabaseClient
import com.fmhub24.app.data.remote.dto.ExtensionDto
import com.fmhub24.app.plugins.ExtensionDownloader
import com.fmhub24.app.plugins.PluginManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Syncs the Supabase `extensions` rows into local files, then hands them to [PluginManager].
 *
 * Kept deliberately strict about *why* something is (re)downloaded, because the two failure
 * modes people hit — "nothing loaded" and "old version still loaded" — are both decided here.
 */
@Singleton
class ExtensionRepository @Inject constructor(
    private val supabaseClient: SupabaseClient,
    private val cachedExtensionDao: CachedExtensionDao,
    private val extensionDownloader: ExtensionDownloader,
    private val pluginManager: PluginManager,
) {

    fun getActiveExtensions(): Flow<Result<List<ExtensionDto>>> = flow {
        emit(supabaseClient.fetchActiveExtensions())
    }.flowOn(Dispatchers.IO)

    /**
     * @param onProgress (extension name, percent)
     * @return the rows that should be loaded now (enabled + status active only)
     */
    suspend fun syncFromRemote(
        remoteExtensions: List<ExtensionDto>,
        onProgress: (String, Int) -> Unit = { _, _ -> {} },
    ): List<CachedExtension> = withContext(Dispatchers.IO) {
        // Heal files left writable by an older build (Android 14 refuses those outright).
        extensionDownloader.hardenCachedFiles()

        val cachedList = cachedExtensionDao.getAll()
        val cachedMap = cachedList.associateBy { it.id }
        val touched = mutableSetOf<String>()

        for (remote in remoteExtensions) {
            touched += remote.id
            val cached = cachedMap[remote.id]
            val fileName = remote.fileName
                ?: "${remote.id.take(8)}-v${remote.version}.cs3"
            val needsDownload = when {
                cached == null -> true
                !File(cached.localFilePath).exists() -> true
                cached.version != remote.version -> true
                // Same version number but different bytes => the admin re-uploaded in place.
                ExtensionDownloader.normalizeHash(remote.fileHash) != null &&
                    ExtensionDownloader.normalizeHash(cached.fileHash) != null &&
                    ExtensionDownloader.normalizeHash(remote.fileHash) !=
                        ExtensionDownloader.normalizeHash(cached.fileHash) -> true
                else -> false
            }

            if (!needsDownload) continue

            try {
                onProgress(remote.name, 0)
                val downloaded = extensionDownloader.downloadExtension(
                    url = remote.fileUrl,
                    fileName = fileName,
                    expectedSha256 = remote.fileHash,   // prefix-tolerant; see ExtensionDownloader.normalizeHash
                    onProgress = { progress -> onProgress(remote.name, progress) },
                )

                cachedExtensionDao.insert(
                    CachedExtension(
                        id = remote.id,
                        name = remote.name,
                        version = remote.version,
                        localFilePath = downloaded.file.absolutePath,
                        fileUrl = remote.fileUrl,
                        status = remote.status,
                        // New rows start enabled; an existing user choice is preserved below.
                        enabled = cached?.enabled ?: true,
                        pluginClassName = remote.pluginClassName,
                        apiVersion = remote.apiVersion,
                        fileHash = downloaded.sha256 ?: ExtensionDownloader.normalizeHash(remote.fileHash),
                        sizeBytes = downloaded.bytes,
                        sourceRepoUrl = remote.sourceRepoUrl,
                    )
                )

                if (cached != null && cached.localFilePath != downloaded.file.absolutePath) {
                    extensionDownloader.deleteExtension(cached.localFilePath)
                }
                onProgress(remote.name, 100)
            } catch (e: Exception) {
                Log.w(TAG, "Download failed for ${remote.name}", e)
                // Keep the previous version usable rather than dropping to "no extensions".
                cachedExtensionDao.setLastError(remote.id, e.message)
            }
        }

        // Anything the admin removed: drop the file and the row.
        for (cached in cachedList) {
            if (cached.id !in touched && remoteExtensions.none { it.id == cached.id }) {
                extensionDownloader.deleteExtension(cached.localFilePath)
                cachedExtensionDao.delete(cached.id)
            }
        }

        loadEnabled()
    }

    /** Load everything the user has enabled; results land in [PluginManager]. */
    suspend fun loadEnabled(): List<CachedExtension> = withContext(Dispatchers.IO) {
        val toLoad = cachedExtensionDao.getEnabled()
        pluginManager.loadExtensions(toLoad)
        toLoad.forEach { cached ->
            val failure = pluginManager.loadingStateValue()
            val errorForFile = (failure as? PluginManager.LoadingState.Error)
                ?.errors
                ?.firstOrNull { cached.localFilePath.endsWith(it.file) }
                ?.let { "${it.reason}${it.missingClass?.let { m -> " (missing $m)" } ?: ""}" }
            cachedExtensionDao.setLastError(cached.id, errorForFile)
        }
        toLoad
    }

    suspend fun getCachedExtensions(): List<CachedExtension> = cachedExtensionDao.getAll()

    fun observeCachedExtensions(): Flow<List<CachedExtension>> = cachedExtensionDao.observeAll()

    /**
     * The user's per-provider switch. Disabling does not delete anything: the file stays
     * downloaded and only the load step filters it out, so re-enabling is instant and offline.
     */
    suspend fun setExtensionEnabled(id: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        cachedExtensionDao.setEnabled(id, enabled)
        loadEnabled()
    }

    suspend fun reloadFromCache(): List<CachedExtension> = withContext(Dispatchers.IO) {
        extensionDownloader.hardenCachedFiles()
        loadEnabled()
    }

    /** Forget everything: files, rows and loaded providers (Settings -> "Reset"). */
    suspend fun clearAll() = withContext(Dispatchers.IO) {
        pluginManager.clear()
        cachedExtensionDao.getAll().forEach { extensionDownloader.deleteExtension(it.localFilePath) }
        cachedExtensionDao.clearAll()
    }

    private companion object {
        const val TAG = "FMHubExtRepo"
    }
}
