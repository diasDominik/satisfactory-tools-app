package io.github.diasdominik.sft.di

import android.content.Context
import eu.anifantakis.lib.ksafe.KSafe
import eu.anifantakis.lib.ksafe.KSafeConfig
import org.koin.core.annotation.Module
import org.koin.core.annotation.Singleton

@Module
actual class PlatformModule {
    @Singleton
    fun ksafe(context: Context): KSafe = KSafe(context, fileName = STORE_NAME, config = KSafeConfig(appNamespace = APP_NAMESPACE))
}
