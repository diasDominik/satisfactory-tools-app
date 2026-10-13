package io.github.diasdominik.sft.data

import io.github.diasdominik.sft.resources.Res
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.ExperimentalResourceApi

object DataLoader {
    private val json = Json { ignoreUnknownKeys = true }
    private val cache = mutableMapOf<GameVersion, GameData>()

    fun parse(version: GameVersion, text: String): GameData =
        GameData(version, json.decodeFromString(GameDataSchema.serializer(), text))

    @OptIn(ExperimentalResourceApi::class)
    suspend fun load(version: GameVersion): GameData {
        cache[version]?.let { return it }
        val text = Res.readBytes(version.file).decodeToString()
        return parse(version, text).also { cache[version] = it }
    }
}
