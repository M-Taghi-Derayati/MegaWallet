package com.mtd.megawallet.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mtd.data.datasource.DefaultBlockchainConnectionModeProvider
import com.mtd.domain.interfaceRepository.INotificationPreferenceProvider
import com.mtd.domain.interfaceRepository.IFiatCurrencyProvider
import com.mtd.domain.interfaceRepository.IThemeModeProvider
import com.mtd.domain.model.BlockchainConnectionMode
import com.mtd.domain.model.FiatCurrency
import com.mtd.domain.model.ThemeMode
import com.mtd.megawallet.notification.FcmTokenRegistrar
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    // نوعِ concrete است نه اینترفیسِ دامنه، چون `setMode` روی خودِ پیاده‌سازی است: اینترفیس فقط
    // `currentMode` دارد و نوشتن از راهِ دیگری، کشِ حافظه‌اش را کهنه می‌گذاشت و
    // `ChainDataSourceFactory` تا استارتِ بعدی از مسیرِ قبلی می‌خواند.
    private val connectionModeProvider: DefaultBlockchainConnectionModeProvider,
    private val fiatCurrencyProvider: IFiatCurrencyProvider,
    private val themeModeProvider: IThemeModeProvider,
    private val notificationPreferenceProvider: INotificationPreferenceProvider,
    // ثبت/لغوِ توکن روی رله. بدونِ این، خاموش‌کردنِ کلید فقط یک ترجیحِ محلی بود و اعلان‌ها
    // همچنان می‌رسیدند.
    private val fcmTokenRegistrar: FcmTokenRegistrar
) : ViewModel() {

    private val _connectionMode = MutableStateFlow(connectionModeProvider.currentMode())
    val connectionMode: StateFlow<BlockchainConnectionMode> = _connectionMode.asStateFlow()

    /** مستقیم از خودِ provider خوانده می‌شود تا تعویضِ واحد پول در هر صفحهٔ دیگری هم دیده شود. */
    val fiatCurrency: StateFlow<FiatCurrency> = fiatCurrencyProvider.currency

    /** همان جریانی که هر دو Activity برای انتخابِ رنگ‌ها collect می‌کنند. */
    val themeMode: StateFlow<ThemeMode> = themeModeProvider.themeMode

    val pushEnabled: StateFlow<Boolean> = notificationPreferenceProvider.pushEnabled

    init {
        // تا این خواندن‌ها تمام نشوند مقدارهای پیش‌فرض گزارش می‌شوند؛ خودشان را اصلاح می‌کنند.
        // همه idempotent‌اند، پس هم‌پوشانی با prime استارتِ برنامه بی‌ضرر است.
        viewModelScope.launch { fiatCurrencyProvider.ensurePrimed() }
        viewModelScope.launch { themeModeProvider.ensurePrimed() }
        viewModelScope.launch { notificationPreferenceProvider.ensurePrimed() }
    }

    fun setConnectionMode(mode: BlockchainConnectionMode) {
        if (_connectionMode.value == mode) return
        _connectionMode.value = mode
        viewModelScope.launch { connectionModeProvider.setMode(mode) }
    }

    fun setFiatCurrency(currency: FiatCurrency) {
        if (fiatCurrencyProvider.currency.value == currency) return
        viewModelScope.launch { fiatCurrencyProvider.set(currency) }
    }

    fun setThemeMode(mode: ThemeMode) {
        if (themeModeProvider.themeMode.value == mode) return
        viewModelScope.launch { themeModeProvider.set(mode) }
    }

    /**
     * ذخیره‌کردنِ ترجیح **و** اثرگذارکردنش: ثبتِ توکن روی رله بعد از نشستنِ ترجیح انجام می‌شود،
     * چون خودِ ثبت‌کننده همین مقدار را می‌خواند و ترتیبِ برعکس، حالتِ قبلی را می‌دید.
     */
    fun setPushEnabled(enabled: Boolean) {
        if (notificationPreferenceProvider.pushEnabled.value == enabled) return
        viewModelScope.launch {
            notificationPreferenceProvider.set(enabled)
            fcmTokenRegistrar.onPushPreferenceChanged(enabled)
        }
    }
}
