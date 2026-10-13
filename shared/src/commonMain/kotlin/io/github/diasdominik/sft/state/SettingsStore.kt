package io.github.diasdominik.sft.state

import eu.anifantakis.lib.ksafe.KSafe
import eu.anifantakis.lib.ksafe.plain
import org.koin.core.annotation.Singleton

/** App preferences and saved production lines. Nothing here is secret, so values are stored unencrypted. */
@Singleton
class SettingsStore(ksafe: KSafe) {
    private val prefs = ksafe.plain

    fun getString(key: String): String? = prefs.getDirect(key, "").ifEmpty { null }

    fun putString(key: String, value: String) = prefs.putDirect(key, value)
}
