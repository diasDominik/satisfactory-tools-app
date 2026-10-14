package io.github.diasdominik.sft.di

import eu.anifantakis.lib.ksafe.KSafe
import io.github.diasdominik.sft.icons.IconDirectory
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import eu.anifantakis.lib.ksafe.KSafeConfig
import org.koin.core.annotation.Module
import org.koin.core.annotation.Singleton

@Module
actual class PlatformModule {
    @Singleton
    fun ksafe(): KSafe = KSafe(fileName = STORE_NAME, config = KSafeConfig(appNamespace = APP_NAMESPACE))

    @Singleton
    fun iconDirectory(): IconDirectory {
        val support = NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true).first() as String
        return IconDirectory("$support/icons")
    }
}
