package com.fmhub24.app.plugins

import com.fmhub24.app.util.CrashLog
import android.content.Context
import android.content.res.AssetManager
import android.content.res.Resources
import android.util.Log
import com.fmhub.plugin.api.FMHubApi
import com.google.gson.Gson
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.extractorApis
import dalvik.system.PathClassLoader
import java.io.File
import java.io.InputStreamReader
import java.lang.reflect.Modifier
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads ONE `.cs3` (a zip holding `classes.dex` + `manifest.json`) into the host.
 *
 * Flow — intentionally the same as CloudStream's `PluginManager.loadPlugin`, because that is
 * the contract every community extension is built against:
 *
 *  1. make the file **read-only** (Android 14 refuses to load a writable dex:
 *     `SecurityException: Writable dex file ... is not allowed`);
 *  2. `PathClassLoader(file, context.classLoader)` — one loader per file, parent = host, so
 *     the dex resolves `MainAPI`/`ExtractorLink`/`app` from the host's CloudStream library;
 *  3. read `manifest.json` through that loader (`getResourceAsStream`), so a repacked
 *     zip layout still works;
 *  4. instantiate `pluginClassName` as [BasePlugin], set its `filename`, inject resources if
 *     `requiresResources` is set;
 *  5. call `plugin.load()` — providers are registered *inside* that call, via
 *     `registerMainAPI` into [APIHolder.allProviders];
 *  6. harvest exactly the providers/extractors this file added.
 *
 * Every failure produces a human-readable [Failure] instead of being swallowed: "no provider
 * found" used to be all the UI could show, which made this undiagnosable.
 */
@Singleton
class PluginLoader @Inject constructor() {

    private val gson = Gson()

    data class LoadedPlugin(
        val filePath: String,
        val fileName: String,
        val manifest: PluginManifest,
        val plugin: BasePlugin?,
        val classLoader: PathClassLoader,
        val providers: List<MainAPI>,
        val extractors: List<ExtractorApi>,
        val warnings: List<String> = emptyList(),
    )

    data class Failure(
        val file: String,
        val reason: String,
        val missingClass: String? = null,
        val cause: String? = null,
    )

    sealed class Outcome {
        data class Success(val loaded: LoadedPlugin) : Outcome()
        data class Failed(val failure: Failure) : Outcome()
    }

    fun load(context: Context, file: File): Outcome {
        val name = file.name
        return try {
            loadUnsafe(context, file, name)
        } catch (t: Throwable) {
            Log.e(TAG, "Unexpected failure loading $name", t)
            Outcome.Failed(
                Failure(
                    file = name,
                    reason = describe(t) ?: "Unhandled ${t.javaClass.simpleName}",
                    missingClass = missingClassOf(t),
                    cause = t.toString(),
                )
            )
        }
    }

    private fun loadUnsafe(context: Context, file: File, name: String): Outcome {
        // ---- 0. file sanity ------------------------------------------------------------
        if (!file.exists() || file.length() == 0L) {
            return fail(name, "File is missing or empty (${file.absolutePath})")
        }
        if (!isZip(file)) {
            return fail(
                name,
                "Not a zip container — a .cs3 must be a zip with classes.dex + manifest.json " +
                    "(got ${file.length()} bytes, first bytes ${headOf(file)})"
            )
        }
        if (!file.containsDex()) {
            return fail(name, "No classes.dex inside the container")
        }

        // ---- 1. Android 14: dex file must not be writable -----------------------------
        val readOnly = try {
            file.setReadOnly()
        } catch (t: Throwable) {
            Log.w(TAG, "Could not set $name read-only", t)
            false
        }
        if (!readOnly) {
            Log.w(TAG, "$name is still writable; ART may refuse to load it (Android 14+)")
        }

        // ---- 2. one classloader per file, parent = host -------------------------------
        val loader = try {
            PathClassLoader(file.absolutePath, context.classLoader)
        } catch (t: Throwable) {
            return fail(name, "PathClassLoader could not open the file: ${describe(t)}", t)
        }

        // ---- 3. manifest (optional but required for the pluginClassName path) ---------
        val manifest = readManifest(loader, file) ?: PluginManifest()

        // ---- 4. FMHub contract gate ----------------------------------------------------
        val declaredApi = manifest.fmhubApiVersion
        if (declaredApi != null && declaredApi != FMHubApi.API_VERSION) {
            return fail(
                name,
                "Built for FMHub plugin API v$declaredApi but this host is v${FMHubApi.API_VERSION} " +
                    "(CloudStream ${com.fmhub24.app.BuildConfig.CLOUDSTREAM_VERSION}). " +
                    "Rebuild the extension against this host's :plugin-api."
            )
        }

        val warnings = mutableListOf<String>()
        if (manifest.requiresResources) {
            warnings += "manifest asks for resources; host injects them via AssetManager, " +
                "but the plugin must not depend on CloudStream's app-module Plugin class"
        }

        // ---- 5/6. load + harvest -------------------------------------------------------
        val providersBefore = APIHolder.allProviders.toList()
        val extractorsBefore = extractorApis.toList()

        var plugin: BasePlugin? = null
        // The instantiated manifest class, before *any* cast. `BasePlugin` and `MainAPI` are
        // unrelated types, and reusing one typed local for both - which is what the old
        // `plugin is MainAPI` check below did - makes kotlinc hand ART a register whose type
        // contradicts the declared one. The verifier then rejects the *whole class*:
        //   java.lang.VerifyError: Verifier rejected class com.fmhub24.app.plugins.PluginLoader:
        //     loadUnsafe(...) register v12 has type MainAPI but expected BasePlugin
        // and a VerifyError is not a load failure: it kills the process the moment Hilt builds the
        // graph, i.e. an app that closes on open with no UI to explain itself. Seen on Android 14.
        var entryPoint: Any? = null
        var usedManifestClass = manifest.hasPluginClass

        if (usedManifestClass) {
            // `manifest.pluginClassName` comes from a file a human can edit, so it is normalised:
            // whitespace inside the name ("com.foo. BarPlugin") is a typo, not a different class.
            val className = manifest.pluginClassName!!.trim().replace(" ", "")
            if (className != manifest.pluginClassName) {
                warnings += "normalised manifest pluginClassName to \"$className\""
            }
            val instance: Any? = try {
                val clazz = loader.loadClass(className)
                val isAbstract = Modifier.isAbstract(clazz.modifiers)
                val isPlugin = BasePlugin::class.java.isAssignableFrom(clazz)
                val isProvider = MainAPI::class.java.isAssignableFrom(clazz)
                when {
                    isAbstract -> {
                        warnings += "$className is abstract; cannot be instantiated"
                        null
                    }

                    !isPlugin && !isProvider -> {
                        warnings += "$className is neither a BasePlugin nor a MainAPI, so it cannot " +
                            "register anything as an entry point"
                        null
                    }

                    else -> try {
                        clazz.getDeclaredConstructor().newInstance()
                    } catch (t: NoSuchMethodException) {
                        return fail(
                            name,
                            "$className has no public no-arg constructor " +
                                "(CloudStream instantiates plugin entry points that way): ${describe(t)}",
                            t
                        )
                    } catch (t: Throwable) {
                        return fail(
                            name,
                            "$className could not be instantiated: ${describe(t) ?: t.javaClass.simpleName}",
                            t
                        )
                    }
                }
            } catch (t: Throwable) {
                val onlyMissing = generateSequence(t) { it.cause }
                    .take(6)
                    .all { it is ClassNotFoundException || it is NoClassDefFoundError }
                if (!onlyMissing) {
                    return fail(
                        name,
                        "Could not load plugin class $className: ${describe(t) ?: t.javaClass.simpleName}",
                        t
                    )
                }
                // The name in manifest.json is not in classes.dex — usually a renamed class or a
                // manifest written by hand. That is a *warning*, not a refusal: fall through to the
                // dex scan, which is exactly what CloudStream's own loader relies on.
                warnings += "manifest declares $className, which classes.dex does not contain; " +
                    "falling back to scanning the dex for providers"
                usedManifestClass = false
            }

            entryPoint = instance

            // Each cast gets its own local with its own declared type. Never narrow one typed local
            // into an unrelated type and back - that is exactly the register conflict above.
            val basePlugin = instance as? BasePlugin
            plugin = basePlugin
            if (basePlugin != null) {
                basePlugin.filename = file.absolutePath
                if (manifest.requiresResources) {
                    injectResources(context, file, basePlugin, warnings)
                }
                try {
                    basePlugin.load()
                } catch (t: Throwable) {
                    return fail(
                        name,
                        "BasePlugin.load() threw: ${describe(t) ?: t.javaClass.simpleName}",
                        t
                    )
                }
            } else if (instance != null) {
                warnings += "$className does not extend BasePlugin; used as a provider directly " +
                    "(older .cs3 layout, so there is no load() to call)"
            }
        }

        var providers = APIHolder.allProviders.filterNot { providersBefore.contains(it) }
        var extractors = extractorApis.filterNot { extractorsBefore.contains(it) }

        // Fallback A: the manifest class *is* the provider - the layout where the entry point
        // implemented MainAPI instead of extending BasePlugin. The previous gate rejected every
        // non-BasePlugin class *before* this line, so a package built like that was reported as
        // "loaded, but produced no MainAPI provider": that is the original bug this branch exists to
        // fix, and it is why the register type had to be widened here rather than cast away.
        // Registering mirrors BasePlugin.registerMainAPI so unloading stays symmetric.
        if (providers.isEmpty()) {
            val bareProvider = entryPoint as? MainAPI
            if (bareProvider != null) {
                providers = listOf(bareProvider)
                try {
                    bareProvider.sourcePlugin = file.absolutePath
                    APIHolder.allProviders.add(bareProvider)
                    APIHolder.addPluginMapping(bareProvider)
                } catch (t: Throwable) {
                    Log.w(TAG, "Could not register $name as a direct provider", t)
                }
            }
        }

        // Fallback B: no usable manifest entry point — scan the dex for MainAPI subclasses.
        if (providers.isEmpty() && !usedManifestClass) {
            var firstLinkError: Throwable? = null
            val discovered = mutableListOf<MainAPI>()
            for (className in DexClassScanner.classNames(file)) {
                if (!DexClassScanner.looksLikeCandidate(className)) continue
                try {
                    val clazz = loader.loadClass(className)
                    if (Modifier.isAbstract(clazz.modifiers) || Modifier.isInterface(clazz.modifiers)) continue
                    if (!MainAPI::class.java.isAssignableFrom(clazz)) continue
                    val instance = instantiateProvider(clazz, loader) ?: continue
                    instance.sourcePlugin = file.absolutePath
                    discovered += instance
                } catch (t: Throwable) {
                    if (firstLinkError == null) firstLinkError = t
                }
            }
            providers = discovered
            if (discovered.isEmpty() && firstLinkError != null) {
                return fail(
                    name,
                    "No pluginClassName in manifest and no MainAPI could be linked: " +
                        (describe(firstLinkError) ?: firstLinkError.javaClass.simpleName),
                    firstLinkError
                )
            }
        }

        // Nothing registered? Then the plugin loaded but contributed no provider.
        if (providers.isEmpty()) {
            val dexClasses = runCatching {
                DexClassScanner.classNames(file).filterNot { it.startsWith("kotlin") || it.startsWith("androidx") }
            }.getOrDefault(emptyList())
            return fail(
                name,
                if (manifest.hasPluginClass) {
                    "Plugin class loaded and load() ran, but it registered no MainAPI " +
                        "(declared ${manifest.pluginClassName}, dex contains " +
                        "${dexClasses.size} class(es): ${dexClasses.take(6).joinToString(", ")})"
                } else {
                    "No MainAPI implementation found in the dex " +
                        "(dex contains ${dexClasses.size} class(es): ${dexClasses.take(6).joinToString(", ")})"
                }
            )
        }

        // Mirror CloudStream: apply per-provider overrides after registration.
        try {
            APIHolder.initAll()
        } catch (t: Throwable) {
            Log.w(TAG, "APIHolder.initAll() failed", t)
        }

        val webviewOnly = providers.filter { runCatching { it.usesWebView }.getOrDefault(false) }
        if (webviewOnly.isNotEmpty()) {
            warnings += "Provider(s) need a WebView and may return nothing until one is wired up: " +
                webviewOnly.joinToString { it.name }
        }

        return Outcome.Success(
            LoadedPlugin(
                filePath = file.absolutePath,
                fileName = name,
                manifest = manifest,
                plugin = plugin,
                classLoader = loader,
                providers = providers,
                extractors = extractors,
                warnings = warnings,
            )
        )
    }

    /**
     * Providers are normally `class X : MainAPI()` but some repos use `object X : MainAPI()`
     * (Kotlin singletons), whose constructor is private — read the `INSTANCE` field instead of
     * failing.
     */
    private fun instantiateProvider(clazz: Class<*>, loader: PathClassLoader): MainAPI? {
        return try {
            clazz.getDeclaredConstructor().apply { isAccessible = true }.newInstance() as MainAPI
        } catch (t: Throwable) {
            try {
                val field = clazz.getDeclaredField("INSTANCE").apply { isAccessible = true }
                field.get(null) as? MainAPI
            } catch (_: Throwable) {
                throw t
            }
        }
    }

    /**
     * `requiresResources` support, same trick as CloudStream: build an [AssetManager] that also
     * knows about the plugin file and hand it to the plugin *if* it exposes a setter. Plugins
     * that need CloudStream's app-module `Plugin` superclass cannot be served (that class is
     * not part of the published library), and we say so instead of failing silently.
     */
    private fun injectResources(
        context: Context,
        file: File,
        plugin: BasePlugin,
        warnings: MutableList<String>,
    ) {
        try {
            val assets = AssetManager::class.java.getDeclaredConstructor().newInstance()
            val addAssetPath = AssetManager::class.java.getMethod("addAssetPath", String::class.java)
            addAssetPath.invoke(assets, file.absolutePath)
            val resources = Resources(
                assets,
                context.resources.displayMetrics,
                context.resources.configuration
            )
            val setter = plugin.javaClass.methods.firstOrNull { it.name == "setResources" && it.parameterCount == 1 }
            if (setter != null) {
                setter.invoke(plugin, resources)
            } else {
                warnings += "requiresResources is set but the plugin exposes no setResources(); " +
                    "resource lookups will return defaults"
            }
        } catch (t: Throwable) {
            warnings += "Could not inject plugin resources: ${describe(t) ?: t.javaClass.simpleName}"
        }
    }

    private fun readManifest(loader: PathClassLoader, file: File): PluginManifest? {
        loader.getResourceAsStream("manifest.json")?.use { stream ->
            return parseManifest(stream.reader().readText(), file.name)
        }
        return try {
            ZipFile(file).use { zip ->
                val entry = zip.getEntry("manifest.json") ?: return null
                zip.getInputStream(entry).bufferedReader().use { parseManifest(it.readText(), file.name) }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "manifest.json unreadable in ${file.name}", t)
            null
        }
    }

    private fun parseManifest(json: String, file: String): PluginManifest? = try {
        gson.fromJson(json, PluginManifest::class.java)
    } catch (t: Throwable) {
        Log.w(TAG, "manifest.json malformed in $file", t)
        null
    }

    private fun isZip(file: File): Boolean = file.inputStream().use { input ->
        val b = ByteArray(4)
        if (input.read(b) < 4) return false
        b[0] == 'P'.code.toByte() && b[1] == 'K'.code.toByte() &&
            (b[2] == 3.toByte() || b[2] == 5.toByte() || b[2] == 7.toByte())
    }

    private fun File.containsDex(): Boolean = try {
        ZipFile(this).use { zip -> zip.entries().asSequence().any { it.name.endsWith(".dex") } }
    } catch (t: Throwable) {
        false
    }

    private fun headOf(file: File): String = try {
        file.inputStream().use { input ->
            ByteArray(8).let { bytes ->
                val read = input.read(bytes)
                if (read <= 0) "empty"
                else bytes.take(read).joinToString(" ") { b -> "%02X".format(b) }
            }
        }
    } catch (t: Throwable) {
        "?"
    }

    private fun fail(file: String, reason: String, cause: Throwable? = null): Outcome.Failed =
        Outcome.Failed(
            Failure(
                file = file,
                reason = reason,
                missingClass = cause?.let { missingClassOf(it) },
                cause = cause?.toString(),
            )
        ).also {
            Log.e(TAG, "$file: $reason", cause)
            // The screen shows nothing technical, so the reason has to live in the log file:
            // this line is what a developer reads when a source "does not start" on a device.
            CrashLog.note(
                "Source not started: $file",
                reason + (cause?.let { c -> "\n" + CrashLog.stackOf(c) } ?: "")
            )
        }

    companion object {
        private const val TAG = "FMHubPluginLoader"

        /**
         * Turn a link failure into the one sentence that matters. ART reports unresolved types
         * as `NoClassDefFoundError: Failed resolution of: Lcom/foo/Bar;` — that class name is
         * the actual diagnosis, and it used to be thrown away.
         */
        fun describe(t: Throwable?): String? {
            var current: Throwable? = t
            var best: String? = null
            while (current != null) {
                val message = current.message
                if (!message.isNullOrBlank()) {
                    best = when {
                        message.contains("Failed resolution of:") -> {
                            val missing = message.substringAfter("Failed resolution of:")
                                .substringBefore(' ').trim('{', '}')
                                .removePrefix("L").removeSuffix(";").replace('/', '.')
                            "host is missing $missing${appOnlyHint(missing) ?: ""}"
                        }
                        current is NoClassDefFoundError -> "missing class: $message"
                        current is ClassNotFoundException -> "missing class: $message"
                        current is AbstractMethodError || current is NoSuchMethodError ->
                            "incompatible API: $message"
                        current is SecurityException -> "blocked by the OS: $message"
                        else -> "${current.javaClass.simpleName}: $message"
                    }
                }
                current = current.cause?.takeIf { it !== current }
            }
            return best ?: t?.javaClass?.simpleName
        }

        /**
         * Classes that exist only in CloudStream's *app* module. An extension that references one
         * was built against the app instead of the plugin API, and no host but CloudStream itself
         * can satisfy it — so say that instead of leaving "host is missing X" as a puzzle.
         */
        private fun appOnlyHint(className: String): String? {
            val hit = APP_ONLY_PREFIXES.any { className.startsWith(it) } ||
                className in APP_ONLY_CLASSES ||
                className.contains(".R$") || className.endsWith(".R")
            if (!hit) return null
            return " — that type lives in CloudStream's app module, which no other host ships. " +
                "Rebuild the extension against com.fmhub24.pluginapi:plugin-api and use the plugin " +
                "API only (MainAPI, ExtractorApi, DataStore/context keys, app.get/post)."
        }

        /** Exact class names: a prefix here would also match library types under the same package. */
        private val APP_ONLY_CLASSES = setOf(
            // Base class for *bundled* CloudStream extensions; `.cs3` files must extend BasePlugin.
            "com.lagradost.cloudstream3.plugins.Plugin",
        )

        private val APP_ONLY_PREFIXES = listOf(
            "com.lagradost.cloudstream3.utils.DataStoreHelper",
            "com.lagradost.cloudstream3.utils.DataStoreFileHelper",
            "com.lagradost.cloudstream3.utils.UIHelper",
            "com.lagradost.cloudstream3.CloudStreamApp",
            "com.lagradost.cloudstream3.CommonActivity",
            "com.lagradost.cloudstream3.MainActivity",
            "com.lagradost.cloudstream3.ui.",
            "com.lagradost.cloudstream3.syncproviders.AccountManager",
            "com.lagradost.cloudstream3.database.",
            // VideoClickAction & friends: useful, but only for a host that has CloudStream's UI.
            "com.lagradost.cloudstream3.actions.",
        )

        fun missingClassOf(t: Throwable?): String? {
            var current: Throwable? = t
            while (current != null) {
                val message = current.message
                if (message != null && message.contains("Failed resolution of:")) {
                    return message.substringAfter("Failed resolution of:")
                        .substringBefore(' ')
                        .trim('{', '}')
                        .removePrefix("L").removeSuffix(";").replace('/', '.')
                }
                if (current is ClassNotFoundException) return current.message
                current = current.cause?.takeIf { it !== current }
            }
            return null
        }
    }
}
