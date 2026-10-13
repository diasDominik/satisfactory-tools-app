package io.github.diasdominik.sft.util

/**
 * Minimal zlib (RFC 1950/1951) support so .sft files stay compatible with the website,
 * which uses pako.deflate / pako.inflate.
 */
object Zlib {
    /** Wraps [data] in zlib framing using stored (uncompressed) deflate blocks. */
    fun deflateStored(data: ByteArray): ByteArray {
        val out = ByteBuilder(data.size + data.size / 65535 * 5 + 16)
        out.add(0x78)
        out.add(0x01)
        var offset = 0
        if (data.isEmpty()) {
            out.add(0x01); out.add(0x00); out.add(0x00); out.add(0xFF); out.add(0xFF)
        }
        while (offset < data.size) {
            val length = minOf(65535, data.size - offset)
            val last = offset + length >= data.size
            out.add(if (last) 1 else 0)
            out.add(length and 0xFF)
            out.add((length shr 8) and 0xFF)
            out.add(length.inv() and 0xFF)
            out.add((length.inv() shr 8) and 0xFF)
            out.addAll(data, offset, length)
            offset += length
        }
        val adler = adler32(data)
        out.add((adler ushr 24) and 0xFF)
        out.add((adler ushr 16) and 0xFF)
        out.add((adler ushr 8) and 0xFF)
        out.add(adler and 0xFF)
        return out.toByteArray()
    }

    fun inflate(data: ByteArray): ByteArray {
        require(data.size >= 2) { "Data too short" }
        val cmf = data[0].toInt() and 0xFF
        val flg = data[1].toInt() and 0xFF
        val hasZlibHeader = (cmf and 0x0F) == 8 && ((cmf shl 8) + flg) % 31 == 0
        val reader = BitReader(data, if (hasZlibHeader) 2 else 0)
        val out = ByteBuilder(data.size * 4)
        do {
            val final = reader.bits(1) == 1
            when (reader.bits(2)) {
                0 -> {
                    reader.alignToByte()
                    val len = reader.byte() or (reader.byte() shl 8)
                    reader.byte(); reader.byte()
                    repeat(len) { out.add(reader.byte()) }
                }
                1 -> inflateBlock(reader, out, FIXED_LITERALS, FIXED_DISTANCES)
                2 -> {
                    val (lit, dist) = readDynamicTables(reader)
                    inflateBlock(reader, out, lit, dist)
                }
                else -> error("Invalid block type")
            }
        } while (!final)
        return out.toByteArray()
    }

    private fun adler32(data: ByteArray): Int {
        var a = 1
        var b = 0
        for (byte in data) {
            a = (a + (byte.toInt() and 0xFF)) % 65521
            b = (b + a) % 65521
        }
        return (b shl 16) or a
    }

    private class Huffman(lengths: IntArray) {
        val counts = IntArray(16)
        val symbols: IntArray

        init {
            for (l in lengths) counts[l]++
            counts[0] = 0
            val offsets = IntArray(16)
            for (i in 1 until 16) offsets[i] = offsets[i - 1] + counts[i - 1]
            symbols = IntArray(lengths.size)
            for ((symbol, l) in lengths.withIndex()) if (l != 0) symbols[offsets[l]++] = symbol
        }

        fun decode(reader: BitReader): Int {
            var code = 0
            var first = 0
            var index = 0
            for (len in 1 until 16) {
                code = code or reader.bits(1)
                val count = counts[len]
                if (code - count < first) return symbols[index + (code - first)]
                index += count
                first += count
                first = first shl 1
                code = code shl 1
            }
            error("Invalid Huffman code")
        }
    }

    private val LENGTH_BASE = intArrayOf(3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 15, 17, 19, 23, 27, 31, 35, 43, 51, 59, 67, 83, 99, 115, 131, 163, 195, 227, 258)
    private val LENGTH_EXTRA = intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 0)
    private val DIST_BASE = intArrayOf(1, 2, 3, 4, 5, 7, 9, 13, 17, 25, 33, 49, 65, 97, 129, 193, 257, 385, 513, 769, 1025, 1537, 2049, 3073, 4097, 6145, 8193, 12289, 16385, 24577)
    private val DIST_EXTRA = intArrayOf(0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13)
    private val CODE_LENGTH_ORDER = intArrayOf(16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15)

    private val FIXED_LITERALS = Huffman(IntArray(288) { i -> when { i < 144 -> 8; i < 256 -> 9; i < 280 -> 7; else -> 8 } })
    private val FIXED_DISTANCES = Huffman(IntArray(30) { 5 })

    private fun readDynamicTables(reader: BitReader): Pair<Huffman, Huffman> {
        val hlit = reader.bits(5) + 257
        val hdist = reader.bits(5) + 1
        val hclen = reader.bits(4) + 4
        val codeLengths = IntArray(19)
        for (i in 0 until hclen) codeLengths[CODE_LENGTH_ORDER[i]] = reader.bits(3)
        val codeLengthHuffman = Huffman(codeLengths)
        val lengths = IntArray(hlit + hdist)
        var i = 0
        while (i < hlit + hdist) {
            when (val symbol = codeLengthHuffman.decode(reader)) {
                in 0..15 -> lengths[i++] = symbol
                16 -> {
                    val previous = lengths[i - 1]
                    repeat(3 + reader.bits(2)) { lengths[i++] = previous }
                }
                17 -> repeat(3 + reader.bits(3)) { lengths[i++] = 0 }
                else -> repeat(11 + reader.bits(7)) { lengths[i++] = 0 }
            }
        }
        return Huffman(lengths.copyOfRange(0, hlit)) to Huffman(lengths.copyOfRange(hlit, hlit + hdist))
    }

    private fun inflateBlock(reader: BitReader, out: ByteBuilder, literals: Huffman, distances: Huffman) {
        while (true) {
            val symbol = literals.decode(reader)
            when {
                symbol < 256 -> out.add(symbol)
                symbol == 256 -> return
                else -> {
                    val li = symbol - 257
                    val length = LENGTH_BASE[li] + reader.bits(LENGTH_EXTRA[li])
                    val di = distances.decode(reader)
                    val distance = DIST_BASE[di] + reader.bits(DIST_EXTRA[di])
                    out.copyBack(distance, length)
                }
            }
        }
    }

    private class BitReader(private val data: ByteArray, private var position: Int) {
        private var bitBuffer = 0
        private var bitCount = 0

        fun bits(count: Int): Int {
            while (bitCount < count) {
                require(position < data.size) { "Unexpected end of data" }
                bitBuffer = bitBuffer or ((data[position++].toInt() and 0xFF) shl bitCount)
                bitCount += 8
            }
            val value = bitBuffer and ((1 shl count) - 1)
            bitBuffer = bitBuffer ushr count
            bitCount -= count
            return value
        }

        fun alignToByte() {
            bitBuffer = 0
            bitCount = 0
        }

        fun byte(): Int {
            require(position < data.size) { "Unexpected end of data" }
            return data[position++].toInt() and 0xFF
        }
    }

    private class ByteBuilder(initial: Int) {
        private var buffer = ByteArray(maxOf(16, initial))
        private var size = 0

        private fun ensure(extra: Int) {
            if (size + extra > buffer.size) buffer = buffer.copyOf(maxOf(buffer.size * 2, size + extra))
        }

        fun add(value: Int) {
            ensure(1)
            buffer[size++] = value.toByte()
        }

        fun addAll(source: ByteArray, offset: Int, length: Int) {
            ensure(length)
            source.copyInto(buffer, size, offset, offset + length)
            size += length
        }

        fun copyBack(distance: Int, length: Int) {
            require(distance <= size) { "Invalid distance" }
            ensure(length)
            var from = size - distance
            repeat(length) { buffer[size++] = buffer[from++] }
        }

        fun toByteArray() = buffer.copyOf(size)
    }
}
