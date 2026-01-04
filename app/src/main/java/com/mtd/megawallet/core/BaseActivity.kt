// In: app/src/main/java/com/mtd/app/base/BaseActivity.kt

package com.mtd.megawallet.core

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewbinding.ViewBinding
import com.mtd.common_ui.AnimationViewOperator
import com.mtd.core.ui.UiEvent
import com.mtd.core.utils.Easings
import com.mtd.core.utils.Interpolators
import kotlinx.coroutines.launch

/**
 * کلاس پایه برای تمام اکتیویتی‌ها
 * @param T نوع ViewBinding مربوط به اکتیویتی
 * @param VM نوع ViewModel مربوط به اکتیویتی
 */
abstract class BaseActivity<T : ViewBinding, VM : ViewModel> : AppCompatActivity() {

    // هر اکتیویتی فرزند باید ViewModel خودش را پیاده‌سازی کند
    protected abstract val viewModel: VM

    // هر اکتیویتی فرزند باید روش ساخت ViewBinding خودش را مشخص کند
    protected abstract val binding: T

    /**
     * Helper برای نمایش Custom Top Snackbar
     */
    private val snackbarHelper = TopSnackbarViewHelper.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize snackbar helper
        snackbarHelper.init(this)
        
        // تنظیم خودکار error handling
        setupErrorHandling()

        observeData()
        setupViews()
        setupClickListeners()
    }

    override fun onDestroy() {
        super.onDestroy()
        snackbarHelper.clear()
    }

    /**
     * تنظیم خودکار error handling
     */
    private fun setupErrorHandling() {
        if (viewModel is BaseViewModel) {
            val baseViewModel = viewModel as BaseViewModel
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    baseViewModel.uiEvents.collect { event ->
                        handleUiEvent(event)
                    }
                }
            }
        }
    }

    /**
     * هندل کردن UI events
     */
    protected open fun handleUiEvent(event: UiEvent) {
        when (event) {
            is UiEvent.ShowErrorSnackbar -> {
                snackbarHelper.showErrorSnackbar(event)
            }
            is UiEvent.ShowDialog -> {
                showDialog(event)
            }
            is UiEvent.Navigate -> {
                // منطق navigation
            }
            is UiEvent.DismissLoading -> {
                // منطق dismiss loading
            }
        }
    }

    internal fun showSnackbar(message: String){
        snackbarHelper.showErrorSnackbar(UiEvent.ShowErrorSnackbar(message))
    }


    /**
     * نمایش Dialog
     */
    protected open fun showDialog(event: UiEvent.ShowDialog) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(event.title)
            .setMessage(event.message)
            .setPositiveButton(event.positiveButton) { _, _ -> event.onPositive() }
            .apply {
                event.negativeButton?.let { negText ->
                    setNegativeButton(negText) { _, _ -> event.onNegative() }
                }
            }
            .show()
    }

    protected open fun observeData() {}
    protected open fun setupViews() {}
    protected open fun setupClickListeners() {}

    val operator: AnimationViewOperator by lazy {
        AnimationViewOperator(Interpolators(Easings.CIRC_OUT))
    }
}