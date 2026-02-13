package com.mtd.megawallet

import androidx.multidex.MultiDexApplication
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.util.DebugLogger
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class MegaWalletApplication: MultiDexApplication() , ImageLoaderFactory{
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }

    override fun newImageLoader(): ImageLoader {


        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.20)
                    .strongReferencesEnabled(false)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(filesDir.resolve("image_cache"))
                    .maxSizeBytes(30L * 1024 * 1024) // 30MB
                    .build()
            }
            .components {
                add(SvgDecoder.Factory())
            }
            .networkCachePolicy(CachePolicy.ENABLED)
            .respectCacheHeaders(true)
            .networkObserverEnabled(true)
            .apply {
                if (BuildConfig.BUILD_TYPE == "debug") {
                    logger(DebugLogger())
                }
            }
            .build()
    }
}