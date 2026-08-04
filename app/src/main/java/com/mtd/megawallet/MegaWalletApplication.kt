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
import com.mtd.data.config.ConfigCatalogBootstrapper
import com.mtd.data.datasource.DefaultBlockchainConnectionModeProvider
import com.mtd.data.datasource.DefaultTestnetVisibilityProvider
import com.mtd.data.di.ForImageLoading
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class MegaWalletApplication: Application() , ImageLoaderFactory{

    // TASK-53 — ConfigManager دیگر مستقیم مصرف نمی‌شود؛ bootstrapper آن را صدا می‌زند و
    // نتیجه را روی رجیستری‌ها می‌نشاند.
    @Inject lateinit var configCatalogBootstrapper: ConfigCatalogBootstrapper


    @Inject lateinit var connectionModeProvider: DefaultBlockchainConnectionModeProvider

    // TASK-53 — ترجیحِ نمایشِ شبکه‌های تست؛ مثل حالتِ اتصال، خارج از ترد اصلی hydrate می‌شود.
    @Inject lateinit var testnetVisibilityProvider: DefaultTestnetVisibilityProvider

    // Lazy است چون newImageLoader ممکن است پیش از آماده‌شدنِ کاملِ گراف صدا زده شود و ساختنِ
    // کلاینت زنجیرهٔ tokenStore/authenticator را هم می‌کشد؛ با Lazy تا اولین درخواستِ تصویر عقب می‌افتد.
    @Inject @ForImageLoading lateinit var imageOkHttpClient: dagger.Lazy<OkHttpClient>


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

            runCatching { configCatalogBootstrapper.bootstrap() }
                .onSuccess { Timber.i("Catalog bootstrap finished (appliedFromBundle=$it)") }
                .onFailure { Timber.w(it, "Catalog bootstrap failed; using the local seed catalog") }
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
            .okHttpClient { imageOkHttpClient.get() }
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
