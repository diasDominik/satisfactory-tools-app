package io.github.diasdominik.sft.save

import io.github.diasdominik.sft.util.Zlib

/** What a Satisfactory save says the player has unlocked. Class names, like `Recipe_IronRod_C`. */
data class SaveUnlocks(
    val sessionName: String,
    val schematics: Set<String>,
    val recipes: Set<String>,
)

class SaveFileException(message: String) : Exception(message)

/**
 * Reads just the unlocks out of a Satisfactory (1.0 and later) .sav file.
 *
 * A save is a small uncompressed header followed by zlib-compressed chunks of at most 128 KiB.
 * Rather than parsing every object in the world, the decompressed body is scanned for the two
 * properties that hold the unlocks: the schematic manager's `mPurchasedSchematics` and the recipe
 * manager's `mAvailableRecipes`. Both are arrays of object references.
 *
 * The body is decompressed one chunk at a time and only a small window is kept, so a large late-game
 * save doesn't have to fit in memory uncompressed.
 */
object SaveFile {
    private const val CHUNK_TAG = 0x9E2A83C1.toInt()
    private const val CHUNK_HEADER_V2 = 0x22222222
    private const val SCHEMATICS = "mPurchasedSchematics"
    private const val RECIPES = "mAvailableRecipes"

    /** [inflate] gets one compressed chunk and its uncompressed size; platforms pass their native zlib. */
    fun readUnlocks(
        bytes: ByteArray,
        inflate: (ByteArray, Int) -> ByteArray = { data, _ -> Zlib.inflate(data) },
    ): SaveUnlocks {
        val reader = Reader(bytes)
        val sessionName = try {
            readSessionName(reader)
        } catch (e: RuntimeException) {
            throw SaveFileException("This isn't a Satisfactory save file.")
        }
        var position = findChunk(bytes, reader.position)
            ?: throw SaveFileException("This isn't a Satisfactory save file, or it's from a game version before 1.0.")
        val scanner = Scanner()
        while (position + 49 <= bytes.size && bytes.int(position) == CHUNK_TAG) {
            val v2 = bytes.int(position + 4) == CHUNK_HEADER_V2
            val compressed = bytes.long(position + if (v2) 17 else 16).toInt()
            val uncompressed = bytes.long(position + if (v2) 25 else 24).toInt()
            val start = position + if (v2) 49 else 48
            if (compressed <= 0 || start + compressed > bytes.size) throw SaveFileException("The save file is cut off.")
            scanner.append(inflate(bytes.copyOfRange(start, start + compressed), uncompressed))
            position = start + compressed
        }
        scanner.finish()
        if (scanner.schematics.isEmpty() && scanner.recipes.isEmpty()) {
            throw SaveFileException("No unlocks found in this save.")
        }
        return SaveUnlocks(sessionName, scanner.schematics, scanner.recipes)
    }

    private fun readSessionName(reader: Reader): String {
        val headerType = reader.int()
        reader.int() // save version
        reader.int() // build
        if (headerType < 0 || headerType > 64) throw SaveFileException("This isn't a Satisfactory save file.")
        if (headerType >= 14) reader.string() // save name
        reader.string() // map name
        reader.string() // map options
        return reader.string()
    }

    /** The first chunk header at or after [from]; the rest of the header varies between game versions. */
    private fun findChunk(bytes: ByteArray, from: Int): Int? {
        for (i in from..bytes.size - 8) {
            if (bytes.int(i) == CHUNK_TAG && bytes.int(i + 4).let { it == CHUNK_HEADER_V2 || it == 0 }) return i
        }
        return null
    }

    /** Finds the two unlock properties in the body as it streams past, keeping only what's still needed. */
    private class Scanner {
        val schematics = mutableSetOf<String>()
        val recipes = mutableSetOf<String>()
        private val needles = listOf(SCHEMATICS, RECIPES).map { it to fString(it) }
        private val longest = needles.maxOf { it.second.size }
        private var window = ByteArray(0)
        private var searchFrom = 0
        private val pending = mutableListOf<Pair<String, Int>>()

        fun append(chunk: ByteArray) {
            val keepFrom = minOf(pending.minOfOrNull { it.second } ?: Int.MAX_VALUE, searchFrom)
            window = window.copyOfRange(keepFrom, window.size) + chunk
            searchFrom -= keepFrom
            pending.replaceAll { it.first to it.second - keepFrom }
            for (i in searchFrom..window.size - longest) {
                for ((name, needle) in needles) if (window.matches(i, needle)) pending += name to i
            }
            searchFrom = maxOf(searchFrom, window.size - longest + 1)
            resolve(final = false)
        }

        fun finish() = resolve(final = true)

        private fun resolve(final: Boolean) {
            pending.removeAll { (name, at) ->
                val refs = try {
                    readObjectArray(Reader(window, at))
                } catch (e: IndexOutOfBoundsException) {
                    // The array runs into the next chunk; try again once it's here.
                    if (!final) return@removeAll false
                    null
                } catch (e: IllegalStateException) {
                    null
                }
                refs?.forEach { path ->
                    val className = path.substringAfterLast('.')
                    if (className.isNotEmpty()) (if (name == SCHEMATICS) schematics else recipes) += className
                }
                true
            }
        }
    }

    /**
     * Reads an array-of-object-references property at the reader's position, in either the
     * pre-UE 5.4 tag layout (type name, size, index, inner type) or the newer one that writes the
     * type as a tree. Returns the referenced path names, or null when this isn't such a property.
     */
    private fun readObjectArray(reader: Reader): List<String>? {
        reader.string() // property name
        if (reader.string() != "ArrayProperty") return null
        val afterType = reader.position
        // Newer layout: the type node's child count, then the inner type node.
        if (reader.int() == 1 && reader.string() == "ObjectProperty" && reader.int() == 0) {
            reader.int() // size
            val flags = reader.byte()
            if (flags and 1 != 0) reader.int()
            if (flags and 2 != 0) reader.skip(16)
            return readReferences(reader)
        }
        reader.position = afterType
        reader.int() // size
        reader.int() // index
        if (reader.string() != "ObjectProperty") return null
        if (reader.byte() != 0) reader.skip(16)
        return readReferences(reader)
    }

    private fun readReferences(reader: Reader): List<String>? {
        val count = reader.int()
        if (count < 0 || count > 100_000) return null
        return List(count) {
            reader.string() // level name
            reader.string()
        }
    }

    private fun fString(text: String): ByteArray {
        val length = text.length + 1
        return byteArrayOf(length.toByte(), (length shr 8).toByte(), (length shr 16).toByte(), (length shr 24).toByte()) +
            text.encodeToByteArray() + 0
    }

    private fun ByteArray.matches(at: Int, needle: ByteArray): Boolean {
        for (j in needle.indices) if (this[at + j] != needle[j]) return false
        return true
    }

    private fun ByteArray.int(at: Int): Int =
        (this[at].toInt() and 0xFF) or ((this[at + 1].toInt() and 0xFF) shl 8) or
            ((this[at + 2].toInt() and 0xFF) shl 16) or ((this[at + 3].toInt() and 0xFF) shl 24)

    private fun ByteArray.long(at: Int): Long = (int(at).toLong() and 0xFFFFFFFFL) or (int(at + 4).toLong() shl 32)

    /** Little-endian reads that throw [IndexOutOfBoundsException] past the end. */
    private class Reader(private val bytes: ByteArray, var position: Int = 0) {
        fun byte(): Int = bytes[position++].toInt() and 0xFF

        fun int(): Int {
            if (position + 4 > bytes.size) throw IndexOutOfBoundsException()
            return bytes.int(position).also { position += 4 }
        }

        fun skip(count: Int) {
            if (position + count > bytes.size) throw IndexOutOfBoundsException()
            position += count
        }

        /** An FString: positive length for Latin-1, negative for UTF-16; both include the terminator. */
        fun string(): String {
            val length = int()
            if (length == 0) return ""
            if (length > 0) {
                if (length > 4096 || position + length > bytes.size) throw lengthError(length)
                return CharArray(length - 1) { (bytes[position + it].toInt() and 0xFF).toChar() }
                    .concatToString().also { position += length }
            }
            val chars = -length
            if (chars > 4096 || position + chars * 2 > bytes.size) throw lengthError(length)
            return CharArray(chars - 1) {
                ((bytes[position + it * 2].toInt() and 0xFF) or ((bytes[position + it * 2 + 1].toInt() and 0xFF) shl 8)).toChar()
            }.concatToString().also { position += chars * 2 }
        }

        private fun lengthError(length: Int): RuntimeException =
            if (position + (if (length > 0) length else -length * 2) > bytes.size && length in -4096..4096) IndexOutOfBoundsException()
            else IllegalStateException("Not a string")
    }
}
