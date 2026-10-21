package io.github.diasdominik.sft

import io.github.diasdominik.sft.data.DataLoader
import io.github.diasdominik.sft.data.GameVersion
import io.github.diasdominik.sft.save.SaveFile
import io.github.diasdominik.sft.save.SaveFileException
import io.github.diasdominik.sft.save.SaveImport
import io.github.diasdominik.sft.save.SaveUnlocks
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.Deflater
import java.util.zip.Inflater
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Builds small saves in the game's layout and reads the unlocks back. */
class SaveFileTest {
    private val schematics = listOf(
        "/Game/FactoryGame/Schematics/Tutorial/Schematic_Tutorial1.Schematic_Tutorial1_C",
        "/Game/FactoryGame/Schematics/Alternate/Schematic_Alternate_PureIronIngot.Schematic_Alternate_PureIronIngot_C",
    )
    private val recipes = listOf(
        "/Game/FactoryGame/Recipes/Smelter/Recipe_IngotIron.Recipe_IngotIron_C",
        "/Game/FactoryGame/Recipes/Constructor/Recipe_IronPlate.Recipe_IronPlate_C",
        "/Game/FactoryGame/Recipes/Constructor/Recipe_IronRod.Recipe_IronRod_C",
        "/Game/FactoryGame/Recipes/Buildings/Recipe_ConstructorMk1.Recipe_ConstructorMk1_C",
        "/Game/FactoryGame/Recipes/Buildings/Recipe_SmelterMk1.Recipe_SmelterMk1_C",
        "/Game/FactoryGame/Recipes/AlternateRecipes/Recipe_Alternate_Screw.Recipe_Alternate_Screw_C",
    )

    @Test
    fun readsUnlocksWithTheOldPropertyLayout() {
        val save = save(headerType = 13, newTags = false, chunkSize = 131072)
        val unlocks = SaveFile.readUnlocks(save)
        assertEquals("Ficsit Base", unlocks.sessionName)
        assertEquals(setOf("Schematic_Tutorial1_C", "Schematic_Alternate_PureIronIngot_C"), unlocks.schematics)
        assertEquals(recipes.map { it.substringAfterLast('.') }.toSet(), unlocks.recipes)
    }

    @Test
    fun readsUnlocksWithTheNewPropertyLayoutAcrossTinyChunks() {
        // 97-byte chunks force both arrays to straddle several chunks.
        val save = save(headerType = 14, newTags = true, chunkSize = 97, session = "Ŝession ✓")
        val unlocks = SaveFile.readUnlocks(save)
        assertEquals("Ŝession ✓", unlocks.sessionName)
        assertEquals(2, unlocks.schematics.size)
        assertEquals(recipes.size, unlocks.recipes.size)
    }

    @Test
    fun nativeInflaterGivesTheSameResult() {
        val save = save(headerType = 14, newTags = true, chunkSize = 300)
        val native = SaveFile.readUnlocks(save) { data, size ->
            Inflater().run { setInput(data); ByteArray(size).also { inflate(it); end() } }
        }
        assertEquals(SaveFile.readUnlocks(save), native)
    }

    @Test
    fun rejectsOtherFiles() {
        assertFailsWith<SaveFileException> { SaveFile.readUnlocks("# Satisfactory Tools export".encodeToByteArray()) }
        assertFailsWith<SaveFileException> { SaveFile.readUnlocks(ByteArray(3)) }
    }

    @Test
    fun profileTurnsOffWhatIsntUnlockedYet() {
        val data = DataLoader.parse(GameVersion.V1_0, File("src/commonMain/composeResources/files/data1.0.json").readText())
        val profile = SaveImport.profile(
            data,
            SaveUnlocks("Base", setOf("Schematic_Alternate_PureIronIngot_C"), recipes.map { it.substringAfterLast('.') }.toSet()),
        )
        assertEquals(listOf("Recipe_Alternate_PureIronIngot_C", "Recipe_Alternate_Screw_C"), profile.alternates.sorted())
        assertTrue("Recipe_IronRod_C" !in profile.lockedRecipes)
        assertTrue("Recipe_Computer_C" in profile.lockedRecipes)
        assertTrue("Desc_ConstructorMk1_C" !in profile.lockedMachines)
        assertTrue("Desc_ManufacturerMk1_C" in profile.lockedMachines)
    }

    private fun save(headerType: Int, newTags: Boolean, chunkSize: Int, session: String = "Ficsit Base"): ByteArray {
        val header = Bytes().apply {
            int(headerType); int(46); int(365306)
            if (headerType >= 14) string("Autosave 1")
            string("Persistent_Level"); string("?startloc=Grass Fields"); string(session)
            int(3600); long(638000000000000000L); byte(0); int(40)
            string(""); int(0); string("abc"); int(1); repeat(20) { byte(0) }; int(0)
        }
        val body = Bytes().apply {
            int(0)
            string("Persistent_Level"); repeat(300) { byte(it) }
            objectArray("mPurchasedSchematics", schematics, newTags)
            string("mSomethingElse"); repeat(200) { byte(7) }
            // A stray mention of the name that isn't followed by a property.
            string("mAvailableRecipes"); string("NotAnArray")
            objectArray("mAvailableRecipes", recipes, newTags)
            string("None"); repeat(500) { byte(1) }
        }.toByteArray()
        val out = Bytes().apply { bytes(header.toByteArray()) }
        body.toList().chunked(chunkSize).forEach { part ->
            val raw = part.toByteArray()
            val compressed = Deflater().run {
                setInput(raw); finish()
                val buffer = ByteArray(raw.size * 2 + 64)
                buffer.copyOf(deflate(buffer)).also { end() }
            }
            out.apply {
                int(0x9E2A83C1.toInt()); int(0x22222222); long(131072); byte(3)
                long(compressed.size.toLong()); long(raw.size.toLong()); long(compressed.size.toLong()); long(raw.size.toLong())
                bytes(compressed)
            }
        }
        return out.toByteArray()
    }

    private fun Bytes.objectArray(name: String, paths: List<String>, newTags: Boolean) {
        string(name)
        string("ArrayProperty")
        if (newTags) {
            int(1); string("ObjectProperty"); int(0); int(999); byte(0)
        } else {
            int(999); int(0); string("ObjectProperty"); byte(0)
        }
        int(paths.size)
        paths.forEach { string("Persistent_Level"); string(it) }
    }

    private class Bytes : ByteArrayOutputStream() {
        fun byte(v: Int) = write(v)
        fun bytes(v: ByteArray) = write(v)
        fun int(v: Int) = repeat(4) { write(v shr (it * 8)) }
        fun long(v: Long) = repeat(8) { write((v shr (it * 8)).toInt()) }
        fun string(s: String) {
            if (s.isEmpty()) return int(0)
            if (s.all { it.code < 128 }) {
                int(s.length + 1); write(s.toByteArray()); write(0)
            } else {
                int(-(s.length + 1)); s.forEach { c -> write(c.code); write(c.code shr 8) }; write(0); write(0)
            }
        }
    }
}
