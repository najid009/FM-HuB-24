package com.fmhub24.app.plugins

import java.io.File
import java.util.zip.ZipFile

/**
 * Best-effort DEX class enumeration.
 *
 * Used only as a *fallback* for `.cs3` files whose `manifest.json` carries no
 * `pluginClassName` (hand-made plugins, some legacy packages). The normal path is
 * `manifest.json -> pluginClassName`, exactly like CloudStream.
 *
 * `DexFile.loadClassIterator()` is a system-only API, so names are read straight out of the
 * dex container: header -> class_defs -> type_ids -> string_ids -> utf8 data. Malformed input
 * yields an empty list instead of throwing.
 */
internal object DexClassScanner {

    /** DEX header offsets (little-endian). */
    private const val STRING_IDS_SIZE = 0x3C
    private const val STRING_IDS_OFF = 0x40
    private const val TYPE_IDS_SIZE = 0x44
    private const val TYPE_IDS_OFF = 0x48
    private const val CLASS_DEFS_SIZE = 0x64
    private const val CLASS_DEFS_OFF = 0x68
    private const val CLASS_DEF_ITEM_SIZE = 32
    private const val HEADER_SIZE = 0x70

    fun classNames(file: File): List<String> {
        val names = mutableListOf<String>()
        try {
            ZipFile(file).use { zip ->
                zip.entries().asSequence()
                    .filter { it.name.endsWith(".dex") }
                    .forEach { entry ->
                        try {
                            zip.getInputStream(entry).use { names += parse(it.readBytes()) }
                        } catch (_: Exception) {
                            // unreadable dex inside the container — ignore this entry
                        }
                    }
            }
        } catch (_: Exception) {
            // Not a zip / unreadable — the caller falls back to other discovery paths.
        }
        return names.distinct()
    }

    /**
     * Cheap pre-filter before any reflection: providers live in the plugin's own package,
     * not in stdlib/support noise. Deliberately conservative — correctness comes from the
     * `isAssignableFrom` check in the loader, this only keeps the loop short.
     */
    fun looksLikeCandidate(className: String): Boolean {
        val simple = className.substringAfterLast('.')
        if (simple.isEmpty()) return false
        if (simple.endsWith("Companion")) return false
        if (simple.contains('$')) return false
        val root = className.substringBefore('.')
        if (root in NOISE_ROOTS) return false
        return !isDependencyNoise(className)
    }

    // Only runtime/dependency roots that can never hold a provider. `com.*`/`org.*` stay in,
    // because most community providers live there.
    private val NOISE_ROOTS = setOf(
        "kotlin", "kotlinx", "android", "androidx", "java", "javax", "dalvik", "libcore",
        "okhttp3", "okio", "retrofit2",
    )

    private val NOISE_PREFIXES = listOf(
        "com.fasterxml.", "com.google.gson.", "com.google.common.", "com.squareup.",
        "com.lagradost.nicehttp.", "org.jsoup.", "org.mozilla.", "org.intellij.",
        "org.jetbrains.", "io.ktor.", "retrofit2.",
    )

    /** Second-stage filter: `com.*` roots that are dependency noise rather than plugin code. */
    fun isDependencyNoise(className: String): Boolean = NOISE_PREFIXES.any { className.startsWith(it) }

    private fun parse(dex: ByteArray): List<String> {
        if (dex.size < HEADER_SIZE + CLASS_DEF_ITEM_SIZE) return emptyList()
        return try {
            fun u32(offset: Int): Int {
                if (offset < 0 || offset + 4 > dex.size) throw ArrayIndexOutOfBoundsException()
                return (dex[offset].toInt() and 0xFF) or
                    ((dex[offset + 1].toInt() and 0xFF) shl 8) or
                    ((dex[offset + 2].toInt() and 0xFF) shl 16) or
                    ((dex[offset + 3].toInt() and 0xFF) shl 24)
            }

            val stringIdsSize = u32(STRING_IDS_SIZE)
            val stringIdsOff = u32(STRING_IDS_OFF)
            val typeIdsSize = u32(TYPE_IDS_SIZE)
            val typeIdsOff = u32(TYPE_IDS_OFF)
            val classDefsSize = u32(CLASS_DEFS_SIZE)
            val classDefsOff = u32(CLASS_DEFS_OFF)
            if (classDefsSize <= 0 || classDefsSize > 200_000) return emptyList()
            if (stringIdsSize <= 0 || typeIdsSize <= 0) return emptyList()

            fun stringAt(index: Int): String? {
                if (index < 0 || index >= stringIdsSize) return null
                var pos = u32(stringIdsOff + 4 * index) // uleb128 utf16 size, then MUTF-8 bytes
                if (pos < 0 || pos >= dex.size) return null
                while (pos < dex.size && dex[pos].toInt() and 0x80 != 0) pos++
                pos++ // skip the final byte of the uleb128 length
                val start = pos
                while (pos < dex.size && dex[pos].toInt() != 0) pos++
                if (pos <= start || pos > dex.size) return null
                return String(dex, start, pos - start, Charsets.UTF_8)
            }

            val result = ArrayList<String>(classDefsSize)
            for (i in 0 until classDefsSize) {
                val typeIdx = u32(classDefsOff + CLASS_DEF_ITEM_SIZE * i)
                if (typeIdx < 0 || typeIdx >= typeIdsSize) continue
                val descriptor = stringAt(u32(typeIdsOff + 4 * typeIdx)) ?: continue
                if (descriptor.length > 3 &&
                    descriptor.startsWith("L") &&
                    descriptor.endsWith(";")
                ) {
                    result += descriptor.substring(1, descriptor.length - 1).replace('/', '.')
                }
            }
            result
        } catch (_: Throwable) {
            emptyList()
        }
    }
}
