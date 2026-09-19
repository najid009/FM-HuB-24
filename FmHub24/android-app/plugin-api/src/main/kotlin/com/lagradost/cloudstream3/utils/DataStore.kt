@file:OptIn(com.lagradost.cloudstream3.InternalAPI::class)

package com.lagradost.cloudstream3.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

/**
 * Host-side implementation of `com.lagradost.cloudstream3.utils.DataStore`.
 *
 * WHY THIS LIVES HERE AND NOT IN THE CLOUDSTREAM LIBRARY
 * ------------------------------------------------------
 * CloudStream keeps `DataStore` in its **app** module, yet plenty of community providers call
 * `DataStore.getKey<MyState>(...)` / `setKey(...)` to persist site state (page cursors,
 * cookie blobs, "last used" flags). With only the library on the classpath that reference is
 * unresolved and the *whole provider class* fails to link — which is what surfaced as
 * "1 file(s) found but none produced a MainAPI provider".
 *
 * `AppUtils.parseJson(String, KClass)` and `AppUtils.toJsonLiteral` are marked `@InternalAPI`
 * upstream (opt-in, level ERROR), which is why this file opts in at the top: mirroring CloudStream's
 * own `DataStore` implementation is the point of the shim, and any other choice would change what
 * ends up in SharedPreferences.
 *
 * The host therefore provides the same object, package, name and signatures, and extensions
 * compile against it through `:plugin-api` (`compileOnly`). One class, both sides.
 *
 * Values are stored as JSON strings through the shared Jackson/kotlinx mappers, exactly like
 * CloudStream does, so anything one plugin writes another plugin (or the app) can read.
 */
object DataStore {

    /** SharedPreferences file used for plugin + app state. */
    const val PREFERENCES_NAME = "rebuild_preference"

    /**
     * Present only so that extension bytecode compiled against CloudStream's own `DataStore.mapper`
     * links (the field is referenced by name at runtime). New code must use
     * `com.lagradost.cloudstream3.mapper` or `AppUtils` directly — same instance, stable name.
     */
    @Deprecated(
        "Do not use the mapper version from DataStore. Use methods from AppUtils to parse JSON, " +
            "or com.lagradost.cloudstream3.mapper for the mapper itself.",
        level = DeprecationLevel.ERROR,
        replaceWith = ReplaceWith("com.lagradost.cloudstream3.mapper"),
    )
    val mapper = com.lagradost.cloudstream3.mapper

    fun getFolderName(folder: String, path: String): String = "$folder/$path"

    fun Context.getSharedPrefs(): SharedPreferences =
        getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun Context.getDefaultSharedPrefs(): SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(this)

    /** Batched writes: `apply()` is expensive, so callers writing many keys share one editor. */
    data class Editor(val editor: SharedPreferences.Editor) {
        fun <T> setKeyRaw(path: String, value: T) {
            @Suppress("UNCHECKED_CAST")
            when {
                value == null -> editor.remove(path)
                value is Set<*> && value.all { it is String } ->
                    editor.putStringSet(path, value as Set<String>)
                value is Boolean -> editor.putBoolean(path, value)
                value is Int -> editor.putInt(path, value)
                value is Long -> editor.putLong(path, value)
                value is Float -> editor.putFloat(path, value)
                else -> editor.putString(path, encode(value))
            }
        }

        fun apply() = editor.apply()
    }

    fun editor(context: Context, isEditingAppSettings: Boolean = false): Editor {
        val prefs = if (isEditingAppSettings) {
            with(context) { getDefaultSharedPrefs() }
        } else {
            with(context) { getSharedPrefs() }
        }
        return Editor(prefs.edit())
    }

    fun Context.getKeys(folder: String): List<String> {
        // Trailing '/' so "home" does not also match "homepage/…".
        val fixedFolder = folder.trimEnd('/') + "/"
        return getSharedPrefs().all.keys.filter { it.startsWith(fixedFolder) }
    }

    fun Context.containsKey(path: String): Boolean = getSharedPrefs().contains(path)

    fun Context.containsKey(folder: String, path: String): Boolean =
        containsKey(getFolderName(folder, path))

    fun Context.removeKey(path: String) {
        try {
            val prefs = getSharedPrefs()
            if (prefs.contains(path)) {
                prefs.edit { remove(path) }
            }
        } catch (e: Exception) {
            logError(e)
        }
    }

    fun Context.removeKey(folder: String, path: String) = removeKey(getFolderName(folder, path))

    fun Context.removeKeys(folder: String): Int {
        val keys = getKeys("$folder/")
        return try {
            getSharedPrefs().edit {
                keys.forEach { remove(it) }
            }
            keys.size
        } catch (e: Exception) {
            logError(e)
            0
        }
    }

    fun <T> Context.setKey(path: String, value: T) {
        try {
            getSharedPrefs().edit {
                putString(path, encode(value))
            }
        } catch (e: Exception) {
            logError(e)
        }
    }

    fun <T> Context.setKey(folder: String, path: String, value: T) =
        setKey(getFolderName(folder, path), value)

    fun <T : Any> Context.getKey(path: String, valueType: Class<T>): T? {
        return try {
            val json: String = getSharedPrefs().getString(path, null) ?: return null
            parseJson(json, valueType.kotlin)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Reads [path] into T. The inline bodies reference only public members so that plugins
     * compiling against `plugin-api` (where these are `compileOnly`) can inline them safely.
     */
    inline fun <reified T : Any> Context.getKey(path: String): T? = getKey(path, null as T?)

    inline fun <reified T : Any> Context.getKey(path: String, defVal: T?): T? {
        return try {
            val json: String = getSharedPrefs().getString(path, null) ?: return defVal
            parseJson(json)
        } catch (e: Exception) {
            defVal
        }
    }

    inline fun <reified T : Any> Context.getKey(folder: String, path: String): T? =
        getKey(getFolderName(folder, path), null)

    inline fun <reified T : Any> Context.getKey(folder: String, path: String, defVal: T?): T? =
        getKey(getFolderName(folder, path), defVal) ?: defVal

    inline fun <reified T : Any> String.toKotlinObject(): T = parseJson(this)

    fun <T : Any> String.toKotlinObject(valueType: Class<T>): T = parseJson(this, valueType.kotlin)

    // ------------------------------------------------------------------ delegates

    /**
     * `var myState by PreferenceDelegate("key", default)` — the property-delegation helper from
     * CloudStream's app module. Extensions use it for persisted site settings, so the type has to
     * exist here with the same name and constructor shape or the whole class fails to link.
     *
     * It reads the application Context through `com.lagradost.api.getContext()`, which the host
     * installs in `Application.attachBaseContext`.
     */
    class PreferenceDelegate<T : Any>(val key: String, val default: T) {
        private val klass: KClass<out T> = default::class
        private var cache: T? = null

        private fun context(): Context? = com.lagradost.api.getContext() as? Context

        operator fun getValue(self: Any?, property: KProperty<*>): T =
            cache ?: context()?.let { ctx ->
                with(DataStore) { ctx.getKey(key, klass.java) }
            } ?: default

        operator fun setValue(self: Any?, property: KProperty<*>, value: T?) {
            cache = value
            val ctx = context() ?: return
            with(DataStore) {
                if (value == null) ctx.removeKey(key) else ctx.setKey(key, value)
            }
        }
    }

    // ------------------------------------------------------------------ encoding

    /**
     * JSON-encodes everything, strings included: `getKey<String>` goes through the JSON parsers, so
     * a bare `hello` would not round-trip while `"hello"` does.
     *
     * Delegates to `AppUtils.toJsonLiteral()` — i.e. kotlinx-serialization first, Jackson second —
     * because that is exactly what CloudStream's own `setKey` does. Matching the *encoding* matters
     * as much as matching the signature: a plugin must be able to read state it wrote on another
     * host, and a value written by the FMHub host must be readable by the same provider elsewhere.
     */
    @PublishedApi
    internal fun <T> encode(value: T): String? = try {
        if (value == null) null else with(AppUtils) { (value as Any).toJsonLiteral() }
    } catch (e: Exception) {
        try {
            com.lagradost.cloudstream3.mapper.writeValueAsString(value)
        } catch (e2: Exception) {
            logError(e2)
            null
        }
    }
}
