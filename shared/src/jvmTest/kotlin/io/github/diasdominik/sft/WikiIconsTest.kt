package io.github.diasdominik.sft

import io.github.diasdominik.sft.icons.WikiIcons
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WikiIconsTest {
    @Test
    fun variantsFallBackToTheirBasePiece() {
        assertEquals(
            listOf("File:Ramp (2 m) (Concrete).png", "File:Ramp (2 m).png", "File:Ramp.png"),
            WikiIcons.candidates("Ramp (2 m) (Concrete)"),
        )
        assertEquals(listOf("File:Iron Plate.png"), WikiIcons.candidates("Iron Plate"))
    }

    @Test
    fun queryEncodesTitles() {
        val url = WikiIcons.queryUrl(listOf("File:Iron Plate.png", "File:AI Limiter.png"))
        assertTrue(url.endsWith("titles=File%3AIron%20Plate.png%7CFile%3AAI%20Limiter.png"), url)
        assertTrue("iiurlwidth=128" in url)
    }

    @Test
    fun parsesThumbnailsNormalisationAndMissingFiles() {
        val json = """
            {"batchcomplete":true,"query":{
              "normalized":[{"fromencoded":false,"from":"File:Iron_Plate.png","to":"File:Iron Plate.png"}],
              "pages":[
                {"ns":6,"title":"File:Iron Plate.png","imageinfo":[{"thumburl":"https://satisfactory.wiki.gg/images/thumb/Iron_Plate.png/128px-Iron_Plate.png","url":"https://satisfactory.wiki.gg/images/Iron_Plate.png"}]},
                {"ns":6,"title":"File:Nope.png","missing":true}
              ]}}
        """.trimIndent()
        val result = WikiIcons.parseQuery(json, listOf("File:Iron_Plate.png", "File:Nope.png"))
        assertEquals(mapOf("File:Iron_Plate.png" to "https://satisfactory.wiki.gg/images/thumb/Iron_Plate.png/128px-Iron_Plate.png"), result)
    }
}
