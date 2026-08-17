package com.mtd.megawallet.ui.compose

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mtd.common_ui.theme.MegaWalletTheme
import com.mtd.core.manager.ErrorManager
import com.mtd.domain.interfaceRepository.IThemeModeProvider
import com.mtd.domain.model.CreateWalletStep
import com.mtd.domain.model.ImportScreenState
import com.mtd.megawallet.ui.compose.components.AppMessageHost
import com.mtd.megawallet.ui.compose.screens.addexistingwallet.AddExistingWalletScreen
import com.mtd.megawallet.ui.compose.screens.createwallet.CreateWalletScreen
import com.mtd.megawallet.ui.compose.screens.splash.SplashScreen
import com.mtd.megawallet.ui.compose.screens.welcome.WelcomeIntroScreen
import com.mtd.megawallet.ui.compose.theme.resolveIsDark
import com.mtd.megawallet.viewmodel.CreateWalletViewModel
import com.mtd.megawallet.viewmodel.WalletImportViewModel
import com.mtd.megawallet.viewmodel.WelcomeViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * اکتیویتی اصلی برای بخش Welcome/Onboarding اپلیکیشن.
 * شامل Navigation Graph با انیمیشن‌های سفارشی و Parallax Effect برای Modal Transitions.
 */
@AndroidEntryPoint
class WelcomeActivityCompose : ComponentActivity() {

    companion object {
        /** با `true` مستقیم از «شروع» باز می‌شود و اسپلش رد می‌شود. */
        const val EXTRA_SKIP_SPLASH = "skip_splash"
    }

    private val viewModelWelcome: WelcomeViewModel by viewModels()
    private val viewModelWalletImport: WalletImportViewModel by viewModels()
    private val viewModelCreateWallet: CreateWalletViewModel by viewModels()

    // TASK-57 — the app-wide message bus; one AppMessageHost per Activity renders it.
    @Inject lateinit var errorManager: ErrorManager

    // همان provider که MainActivity می‌خواند؛ Singleton است تا پوسته بین این دو صفحه نپرد.
    @Inject lateinit var themeModeProvider: IThemeModeProvider

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        enableEdgeToEdge()
        // پس از حذفِ آخرین کیف‌پول، برنامه از همین‌جا از نو بالا می‌آید. اسپلش کارش تصمیم‌گیری
        // بین «خانه» و «شروع» است و اینجا جواب از قبل معلوم است، پس فقط یک لوگوی تکراری بود.
        val startAtOnboarding = intent.getBooleanExtra(EXTRA_SKIP_SPLASH, false)
        setContent {
            val themeMode by themeModeProvider.themeMode.collectAsStateWithLifecycle()
            MegaWalletTheme(darkTheme = themeMode.resolveIsDark()) {
                // TASK-02/TD-36 — all onboarding screens (recovery-phrase display, import, passcode
                // setup) are sensitive, so protect the whole flow from screen capture.
                com.mtd.megawallet.ui.compose.components.SecureFlagEffect()
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        WelcomeNavGraph(
                            viewModelWelcome = viewModelWelcome,
                            viewModelWalletImport = viewModelWalletImport,
                            viewModelCreateWallet=viewModelCreateWallet,
                            startAtOnboarding = startAtOnboarding,
                            onNavigateToHome = {
                                startActivity(Intent(this@WelcomeActivityCompose,MainActivityCompose::class.java))
                                finish()
                            }
                        )

                        // TASK-57 — single message host for the whole onboarding flow.
                        AppMessageHost(uiMessages = errorManager.uiMessages)
                    }
                }
            }
        }


    }


    @Composable
    fun WelcomeNavGraph(
        viewModelWelcome: WelcomeViewModel,
        viewModelWalletImport: WalletImportViewModel,
        viewModelCreateWallet:CreateWalletViewModel,
        startAtOnboarding: Boolean = false,
        onNavigateToHome: () -> Unit
    ) {
        val navController = rememberNavController()

        // ردیابی وضعیت Modal بر اساس مقصد فعلی
        LaunchedEffect(navController) {
            navController.currentBackStackEntryFlow.collect { backStackEntry ->
                val currentRoute = backStackEntry.destination.route
                // فعال کردن Modal State برای صفحات Modal
                viewModelWelcome.setModalActive(
                    currentRoute == "add_existing_wallet"
                )
            }
        }

        NavHost(
            navController = navController,
            startDestination = if (startAtOnboarding) "onboarding" else "splash",
            enterTransition = {
                fadeIn(animationSpec = tween(500)) + slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start, tween(500)
                )
            },
            exitTransition = {
                fadeOut(animationSpec = tween(500)) + slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start, tween(500)
                )
            },
            popEnterTransition = {
                fadeIn(animationSpec = tween(500)) + slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.End, tween(500)
                )
            },
            popExitTransition = {
                fadeOut(animationSpec = tween(500)) + slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.End, tween(500)
                )
            }
        ) {
            composable("splash") {
                // ⚠️ این پرسش **هم‌زمان** با انیمیشن شروع می‌شود، نه پس از آن. قبلاً اسپلش مکثِ
                // ثابتِ خودش را می‌کرد و تازه بعدش از دیسک می‌پرسید، پس دو انتظار پشتِ سرِ هم
                // جمع می‌شد. `null` یعنی «هنوز جواب نیامده».
                var hasWallet by remember { mutableStateOf<Boolean?>(null) }
                LaunchedEffect(Unit) { hasWallet = viewModelWelcome.hasWallet() }

                SplashScreen(ready = hasWallet != null) {
                    if (hasWallet == true) {
                        onNavigateToHome()
                    } else {
                        navController.navigate("onboarding") {
                            popUpTo("splash") { inclusive = true }
                        }
                    }
                }
            }

            // صفحه Onboarding - بدون انیمیشن هنگام برگشت از Modal
            composable(
                route = "onboarding",
                popEnterTransition = {
                    // غیرفعال کردن انیمیشن Slide هنگام برگشت از Modal
                    // فقط Parallax Effect (Scale + Dim) اعمال می‌شود
                    fadeIn(animationSpec = tween(0))
                }
            ) {
               /* OnboardingScreen(
                    onConnectWallet = { navController.navigate("add_existing_wallet") },
                    onCreateWallet = { navController.navigate("create_wallet") }
                )*/
                WelcomeIntroScreen(
                    onConnectWallet = { navController.navigate("add_existing_wallet") },
                    onCreateWallet = { navController.navigate("create_wallet") }
                )
            }

            // Modal Transition با Slide Up از پایین (مانند iOS)
            composable(
                route = "add_existing_wallet",
                enterTransition = {
                    slideInVertically(
                        initialOffsetY = { it }, // شروع از پایین صفحه
                        animationSpec = tween(
                            durationMillis = 400,
                            easing = FastOutSlowInEasing
                        )
                    )
                },
                exitTransition = {
                    slideOutVertically(
                        targetOffsetY = { it }, // خروج به پایین صفحه
                        animationSpec = tween(
                            durationMillis = 400,
                            easing = FastOutSlowInEasing
                        )
                    )
                },
                popExitTransition = {
                    // همان انیمیشن exit برای دکمه Back سیستم
                    slideOutVertically(
                        targetOffsetY = { it },
                        animationSpec = tween(
                            durationMillis = 400,
                            easing = FastOutSlowInEasing
                        )
                    )
                }
            ) {
                AddExistingWalletScreen(
                    onBack = {
                        // اگر در حالت STACKED هستیم، به onboarding برگرد
                        if (viewModelWalletImport.screenState == ImportScreenState.STACKED) {
                            navController.popBackStack("onboarding", false)
                        } else {
                            // در غیر این صورت از اپ خارج شو
                            (navController.context as ComponentActivity).finish()
                        }
                    },
                    onImportSuccess = { importData ->
                        viewModelWelcome.setImportData(importData)
                        navController.navigate("create_wallet")
                    },
                    onRestoreFromCloud = { walletItem ->
                        // شروع restore در CreateWalletViewModel
                        viewModelCreateWallet.startRestoreFromCloud(walletItem)
                        navController.navigate("create_wallet")
                    }
                )
            }

            composable("create_wallet") {
                CreateWalletScreen(
                    viewModel = viewModelCreateWallet,
                    onBack = { currentStep ->
                        // اگر importData وجود دارد، یعنی از AddExistingWalletScreen آمده‌ایم
                        // پس باید به آن برگردیم
                        when(currentStep){
                            CreateWalletStep.SEED_PHRASE_GENERATION -> {
                                (navController.context as ComponentActivity).finish()
                            }
                            CreateWalletStep.CLOUD_BACKUP_PASSWORD -> (navController.context as ComponentActivity).finish()
                            else-> {
                                viewModelCreateWallet.resetToInitialState()
                                navController.popBackStack()
                            }
                        }
                    },
                    onNavigateToHome = onNavigateToHome,
                    importData = viewModelWelcome.pendingImportData
                )
            }
        }
    }
}


