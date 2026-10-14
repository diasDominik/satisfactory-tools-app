package io.github.diasdominik.sft.icons

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** How the Satisfactory wiki names its icon files, and how to ask its API where they are. */
object WikiIcons {
    private const val API = "https://satisfactory.wiki.gg/api.php"
    const val ICON_SIZE = 128

    /**
     * File titles to try for a name, best first. Construction pieces such as
     * "Ramp (2 m) (Concrete)" fall back to "Ramp (2 m)" and then "Ramp".
     */
    fun candidates(name: String): List<String> {
        val clean = name.replace(' ', ' ').trim()
        val result = mutableListOf(clean)
        var current = clean
        while (current.endsWith(")") && current.contains(" (")) {
            current = current.substring(0, current.lastIndexOf(" (")).trim()
            result += current
        }
        return result.distinct().map { "File:$it.png" }
    }

    fun queryUrl(titles: List<String>): String = API + "?action=query&format=json&formatversion=2&redirects=1&prop=imageinfo" +
        "&iiprop=url&iiurlwidth=$ICON_SIZE&titles=" + encode(titles.joinToString("|"))

    /** Thumbnail URL per requested title, following the API's normalisation and redirects. */
    fun parseQuery(json: String, titles: List<String>): Map<String, String> {
        val query = Json.parseToJsonElement(json).jsonObject["query"]?.jsonObject ?: return emptyMap()
        fun mapping(key: String) = (query[key] as? JsonArray).orEmpty().associate {
            it.jsonObject["from"]!!.jsonPrimitive.content to it.jsonObject["to"]!!.jsonPrimitive.content
        }
        val normalized = mapping("normalized")
        val redirects = mapping("redirects")
        val pages = (query["pages"] as? JsonArray).orEmpty().map { it.jsonObject }.associateBy { it["title"]!!.jsonPrimitive.content }
        return titles.mapNotNull { title ->
            val resolved = normalized[title] ?: title
            val page = pages[redirects[resolved] ?: resolved] ?: return@mapNotNull null
            val info = (page["imageinfo"] as? JsonArray)?.firstOrNull() as? JsonObject ?: return@mapNotNull null
            val url = (info["thumburl"] ?: info["url"])?.jsonPrimitive?.content ?: return@mapNotNull null
            title to url
        }.toMap()
    }

    private fun encode(value: String): String = buildString {
        for (byte in value.encodeToByteArray()) {
            val c = byte.toInt().toChar()
            if (c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c in "-_.~") append(c)
            else append('%').append((byte.toInt() and 0xff).toString(16).uppercase().padStart(2, '0'))
        }
    }
}
