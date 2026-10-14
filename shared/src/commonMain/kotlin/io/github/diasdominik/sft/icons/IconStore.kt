package io.github.diasdominik.sft.icons

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import io.github.diasdominik.sft.data.GameData
import io.github.diasdominik.sft.state.SettingsStore
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.UserAgent
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.koin.core.annotation.Singleton

/** Where downloaded icons are kept on this device. */
class IconDirectory(val path: String)

data class IconSyncProgress(val done: Int, val total: Int)

/** The icon store the UI draws from; null (as in tests and previews) shows the app's own icons. */
val LocalIconStore = staticCompositionLocalOf<IconStore?> { null }

/**
 * The game's item and building icons, downloaded once from the Satisfactory wiki and kept on the
 * device, so they also show offline. The app ships none of them; anything the wiki doesn't have
 * keeps the app's own icon.
 */
@Singleton
class IconStore(directory: IconDirectory, private val settings: SettingsStore) {
    private val dir = Path(directory.path)
    private val bitmaps = mutableStateMapOf<String, ImageBitmap>()
    private val absent = HashSet<String>()
    private val mutex = Mutex()

    /** Download progress while icons are being fetched, else null. */
    var progress by mutableStateOf<IconSyncProgress?>(null)
        private set

    /** Bumped when new icons arrive, so icons shown without one can look again. */
    var generation by mutableIntStateOf(0)
        private set

    fun bitmap(className: String): ImageBitmap? = bitmaps[className]

    /** Loads [className]'s icon from disk if it was downloaded; cheap to call again. */
    @OptIn(ExperimentalResourceApi::class)
    suspend fun load(className: String) {
        if (className in bitmaps || mutex.withLock { className in absent }) return
        val bitmap = withContext(Dispatchers.Default) {
            runCatching {
                val file = file(className)
                if (!SystemFileSystem.exists(file)) null
                else SystemFileSystem.source(file).buffered().use { it.readByteArray() }.decodeToImageBitmap()
            }.getOrNull()
        }
        if (bitmap != null) bitmaps[className] = bitmap else mutex.withLock { absent += className }
    }

    /** Downloads every icon not on the device yet, once per game data version unless [force]d. */
    suspend fun sync(data: GameData, force: Boolean = false) {
        val key = KEY_SYNCED + data.version.id
        if (progress != null || (!force && settings.getString(key) == SYNC_VERSION)) return
        val names = LinkedHashMap<String, String>()
        data.items.values.forEach { names[it.className] = it.name }
        data.buildings.values.forEach { names[it.className] = it.name }
        progress = IconSyncProgress(0, names.size)
        val client = HttpClient { install(UserAgent) { agent = USER_AGENT } }
        try {
            withContext(Dispatchers.Default) { SystemFileSystem.createDirectories(dir) }
            val todo = names.filterKeys { force || !SystemFileSystem.exists(file(it)) }
            val urls = resolve(client, todo)
            var done = names.size - todo.size
            progress = IconSyncProgress(done, names.size)
            var failed = false
            val limit = Semaphore(PARALLEL_DOWNLOADS)
            coroutineScope {
                urls.map { (className, url) ->
                    async {
                        limit.withPermit {
                            val bytes = download(client, url)
                            if (bytes == null) failed = true
                            else withContext(Dispatchers.Default) {
                                SystemFileSystem.sink(file(className)).buffered().use { it.write(bytes) }
                            }
                            mutex.withLock { absent -= className }
                            done++
                            progress = IconSyncProgress(done, names.size)
                            if (done % 50 == 0) generation++
                        }
                    }
                }.awaitAll()
            }
            // Icons the wiki doesn't have count as done; only failed downloads are retried next start.
            if (!failed) settings.putString(key, SYNC_VERSION)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            // Offline or the wiki is unreachable: keep what we have and try again on the next start.
        } finally {
            client.close()
            mutex.withLock { absent.clear() }
            progress = null
            generation++
        }
    }

    /** Finds each icon's file on the wiki, 50 names per request, trying simpler names for variants. */
    private suspend fun resolve(client: HttpClient, names: Map<String, String>): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        var pending = names.mapValues { (_, name) -> WikiIcons.candidates(name) }
        while (pending.isNotEmpty()) {
            val titles = pending.mapValues { it.value.first() }
            val found = HashMap<String, String>()
            for (batch in titles.values.distinct().chunked(50)) {
                val json = fetch(client, WikiIcons.queryUrl(batch))?.decodeToString() ?: throw IllegalStateException("Wiki unreachable")
                found += WikiIcons.parseQuery(json, batch)
            }
            for ((className, title) in titles) found[title]?.let { result[className] = it }
            pending = pending.filterKeys { it !in result }.mapValues { it.value.drop(1) }.filterValues { it.isNotEmpty() }
        }
        return result
    }

    private suspend fun download(client: HttpClient, url: String): ByteArray? = fetch(client, url)

    /** GET with backoff when the wiki asks us to slow down. */
    private suspend fun fetch(client: HttpClient, url: String): ByteArray? {
        var wait = 1000L
        repeat(6) {
            val response = runCatching { client.get(url) }.getOrNull() ?: return null
            if (response.status == HttpStatusCode.TooManyRequests || response.status.value >= 500) {
                delay(wait)
                wait *= 2
            } else {
                return if (response.status.isSuccess()) response.body<ByteArray>() else null
            }
        }
        return null
    }

    private fun file(className: String) = Path(dir, "$className.png")

    private companion object {
        const val KEY_SYNCED = "icons-synced-"
        /** Raise to download every icon again after a change to how they are fetched. */
        const val SYNC_VERSION = "1"
        const val PARALLEL_DOWNLOADS = 4
        const val USER_AGENT = "SatisfactoryToolsApp (+https://github.com/diasDominik/satisfactory-tools-app)"
    }
}
