package io.github.diasdominik.sft

import android.app.Application
import io.github.diasdominik.sft.di.initKoin
import org.koin.android.ext.koin.androidContext

class SatisfactoryToolsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin { androidContext(this@SatisfactoryToolsApplication) }
    }
}
