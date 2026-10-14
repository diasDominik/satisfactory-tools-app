package io.github.diasdominik.sft.di

import eu.anifantakis.lib.ksafe.KSafe
import io.github.diasdominik.sft.icons.IconDirectory
import java.io.File
import eu.anifantakis.lib.ksafe.KSafeConfig
import org.koin.core.annotation.Module
import org.koin.core.annotation.Singleton

@Module
actual class PlatformModule {
    @Singleton
    fun ksafe(): KSafe = KSafe(fileName = STORE_NAME, config = KSafeConfig(appNamespace = APP_NAMESPACE))

    @Singleton
    fun iconDirectory(): IconDirectory =
        IconDirectory(File(System.getProperty("user.home"), ".satisfactory-tools/icons").path)
}
