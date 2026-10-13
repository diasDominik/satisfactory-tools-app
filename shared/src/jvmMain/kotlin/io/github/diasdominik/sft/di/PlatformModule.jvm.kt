package io.github.diasdominik.sft.di

import eu.anifantakis.lib.ksafe.KSafe
import eu.anifantakis.lib.ksafe.KSafeConfig
import org.koin.core.annotation.Module
import org.koin.core.annotation.Singleton

@Module
actual class PlatformModule {
    @Singleton
    fun ksafe(): KSafe = KSafe(fileName = STORE_NAME, config = KSafeConfig(appNamespace = APP_NAMESPACE))
}
