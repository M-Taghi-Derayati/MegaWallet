package com.mtd.megawallet

import android.app.Application
import android.os.StrictMode
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.util.DebugLogger
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mtd.data.config.ConfigManager
import com.mtd.data.datasource.DefaultBlockchainConnectionModeProvider
import com.mtd.data.datasource.DefaultTestnetVisibilityProvider
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class MegaWalletApplication: Application() , ImageLoaderFactory{

    @Inject lateinit var configManager: ConfigManager


    @Inject lateinit var connectionModeProvider: DefaultBlockchainConnectionModeProvider

    // TASK-53 — ترجیحِ نمایشِ شبکه‌های تست؛ مثل حالتِ اتصال، خارج از ترد اصلی hydrate می‌شود.
    @Inject lateinit var testnetVisibilityProvider: DefaultTestnetVisibilityProvider


    private val crashHandler = CoroutineExceptionHandler { _, throwable ->
        Timber.e(throwable, "Uncaught coroutine exception in applicationScope")
        // Non-fatal (SupervisorJob isolates it, the app keeps running) → record so it isn't silent.
        FirebaseCrashlytics.getInstance().recordException(throwable)
    }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + crashHandler)

    override fun onCreate() {
        super.onCreate()
        installStrictModeIfDebug()
        FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = BuildConfig.BUILD_TYPE != "debug"



        // TASK-25 — process-wide safety net so uncaught exceptions are logged before the crash.
        installGlobalCrashLogger()
        if (BuildConfig.BUILD_TYPE == "debug") {
            Timber.plant(Timber.DebugTree())
        } else {
            Timber.plant(
                object : Timber.Tree() {
                    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                        if (priority >= Log.INFO) {
                            Log.println(priority, tag ?: "MegaWallet", message)
                            t?.let { Log.println(priority, tag ?: "MegaWallet", Log.getStackTraceString(it)) }
                        }
                    }
                }
            )
        }

        warmUpDynamicConfig()
    }


    private fun warmUpDynamicConfig() {
        applicationScope.launch {
            runCatching { connectionModeProvider.prime() }
                .onFailure { Timber.w(it, "Connection-mode prime failed; defaulting to DIRECT") }
            runCatching { testnetVisibilityProvider.prime() }
                .onFailure { Timber.w(it, "Testnet-visibility prime failed; using the build default") }
            runCatching { configManager.getValidatedConfig() }
                .onSuccess { Timber.i("Dynamic config ready (version=${it.version})") }
                .onFailure { Timber.w(it, "Dynamic config warm-up failed; using local fallback") }
        }
    }

    private fun installStrictModeIfDebug() {
        if (BuildConfig.BUILD_TYPE != "debug") return
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .detectCustomSlowCalls()
                .penaltyLog()
                .build()
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedClosableObjects()
                .detectActivityLeaks()
                .detectLeakedRegistrationObjects()
                .penaltyLog()
                .build()
        )
    }


    private fun installGlobalCrashLogger() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Timber.e(throwable, "FATAL uncaught exception on thread '${thread.name}'")
            previous?.uncaughtException(thread, throwable)
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
