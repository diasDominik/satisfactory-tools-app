package io.github.diasdominik.sft.di

import org.koin.core.KoinApplication
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.KoinApplication as KoinApp
import org.koin.core.annotation.Module
import org.koin.mp.KoinPlatform
import org.koin.plugin.module.dsl.startKoin

/** Everything annotated in the app's packages. */
@Module
@ComponentScan("io.github.diasdominik.sft")
class AppModule

/** Platform bindings, such as the KSafe store (Android needs a Context to build it). */
@Module
expect class PlatformModule

@KoinApp(modules = [AppModule::class, PlatformModule::class])
object SatisfactoryToolsApp

internal const val STORE_NAME = "sft"
internal const val APP_NAMESPACE = "io.github.diasdominik.sft"

/** Starts Koin once; later calls are ignored so every entry point can call it safely. */
fun initKoin(config: KoinApplication.() -> Unit = {}) {
    if (KoinPlatform.getKoinOrNull() != null) return
    startKoin<SatisfactoryToolsApp>(config)
}
