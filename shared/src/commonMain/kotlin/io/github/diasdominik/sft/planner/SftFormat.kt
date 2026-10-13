package io.github.diasdominik.sft.planner

import io.github.diasdominik.sft.util.Zlib
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Reads and writes the website's ".sft" export format:
 * comment lines starting with '#', then "0" + base64(zlib(JSON {type: "tabs", tabs: [...]})).
 */
object SftFormat {
    private const val VERSION = "0"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = true }

    @Serializable
    private data class Export(val type: String, val tabs: List<ProductionData>)

    @OptIn(ExperimentalEncodingApi::class)
    fun export(tabs: List<ProductionData>, names: List<String>, timestamp: String? = null): String {
        val payload = json.encodeToString(Export.serializer(), Export("tabs", tabs)).encodeToByteArray()
        val encoded = Base64.encode(Zlib.deflateStored(payload))
        return buildString {
            append("# Satisfactory Tools export (version 1)\n\n# Contains these production lines:\n")
            names.forEach { append("# - ").append(it).append('\n') }
            append('\n').append(VERSION).append(encoded).append('\n')
            if (timestamp != null) append("\n# Exported on ").append(timestamp).append('\n')
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun import(text: String): List<ProductionData> {
        val body = text.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.joinToString("")
        require(body.isNotEmpty()) { "No data found" }
        require(body.startsWith(VERSION)) { "Invalid version specified: ${body.first()}" }
        val bytes = Base64.decode(body.substring(1))
        val parsed = json.decodeFromString(Export.serializer(), Zlib.inflate(bytes).decodeToString())
        require(parsed.type == "tabs") { "Invalid file type" }
        return parsed.tabs
    }
}
