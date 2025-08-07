package com.mtd.megawallet

import androidx.multidex.MultiDexApplication
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class MegaWalletApplication: MultiDexApplication() {
    override fun onCreate() {
        super.onCreate()
        Timber.Forest.plant(Timber.DebugTree())
    }
}