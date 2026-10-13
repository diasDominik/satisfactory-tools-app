package io.github.diasdominik.sft

import io.github.diasdominik.sft.planner.ProductionData
import io.github.diasdominik.sft.planner.ProductionItem
import io.github.diasdominik.sft.planner.ProductionMetadata
import io.github.diasdominik.sft.planner.ProductionRequest
import io.github.diasdominik.sft.planner.SftFormat
import io.github.diasdominik.sft.util.Zlib
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.Deflater
import java.util.zip.Inflater
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class SftFormatTest {
    @Test
    fun roundTrip() {
        val tab = ProductionData(ProductionMetadata(name = "Plates"), ProductionRequest(production = listOf(ProductionItem("Desc_IronPlate_C", amount = 60.0))))
        val text = SftFormat.export(listOf(tab), listOf("Plates"))
        assertEquals(listOf(tab), SftFormat.import(text))
    }

    @Test
    fun storedDeflateIsValidZlib() {
        val payload = ByteArray(200_000) { (it % 251).toByte() }
        val inflater = Inflater()
        inflater.setInput(Zlib.deflateStored(payload))
        val out = ByteArray(payload.size)
        assertEquals(payload.size, inflater.inflate(out))
        assertContentEquals(payload, out)
    }

    @Test
    fun inflatesCompressedData() {
        val payload = buildString { repeat(2000) { append("{\"item\":\"Desc_IronPlate_C\",\"amount\":$it}") } }.encodeToByteArray()
        for (level in listOf(1, 6, 9)) {
            val deflater = Deflater(level)
            deflater.setInput(payload)
            deflater.finish()
            val buffer = ByteArrayOutputStream()
            val chunk = ByteArray(8192)
            while (!deflater.finished()) buffer.write(chunk, 0, deflater.deflate(chunk))
            assertContentEquals(payload, Zlib.inflate(buffer.toByteArray()))
        }
    }

    @Test
    fun importsWebsiteStyleExport() {
        val json = """{"type":"tabs","tabs":[{"metadata":{"name":null,"icon":null,"schemaVersion":1,"gameVersion":"0"},"request":{"allowedAlternateRecipes":[],"blockedRecipes":[],"blockedMachines":[],"blockedResources":[],"sinkableResources":[],"production":[{"item":"Desc_Rotor_C","type":"perMinute","amount":10,"ratio":100}],"input":[],"resourceMax":{"Desc_OreIron_C":92100,"Desc_Water_C":9007199254740991},"resourceWeight":{"Desc_OreIron_C":1}}}]}"""
        val deflater = Deflater(9)
        deflater.setInput(json.encodeToByteArray())
        deflater.finish()
        val buf = ByteArray(4096)
        val len = deflater.deflate(buf)
        val text = "# Satisfactory Tools export (version 1)\n\n0" + Base64.getEncoder().encodeToString(buf.copyOf(len)) + "\n\n# Exported on now\n"
        val tabs = SftFormat.import(text)
        assertEquals("Desc_Rotor_C", tabs.single().request.production.single().item)
    }
}
