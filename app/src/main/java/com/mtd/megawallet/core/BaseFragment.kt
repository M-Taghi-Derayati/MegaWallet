package com.mtd.megawallet.core

import android.os.Bundle
import android.view.View
import androidx.annotation.LayoutRes
import androidx.fragment.app.Fragment
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
 * کلاس پایه برای تمام فرگمنت‌ها
 * @param T نوع ViewBinding مربوط به فرگمنت
 * @param VM نوع ViewModel مربوط به فرگمنت  
 * @property layoutId آیدی لایه مربوط به فرگمنت
 */
abstract class BaseFragment<T : ViewBinding, VM : ViewModel>(
    @LayoutRes private val layoutId: Int
) : Fragment(layoutId) {

    // هر فرگمنت فرزند باید ViewModel خودش را پیاده‌سازی کند
    protected abstract val viewModel: VM

    // هر فرگمنت فرزند باید روش ساخت ViewBinding خودش را مشخص کند
    protected abstract val binding: T

    /**
     * Helper برای نمایش Custom Top Snackbar
     */
    private val snackbarHelper = TopSnackbarViewHelper.getInstance()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Initialize snackbar helper
        snackbarHelper.init(this)
        
        // تنظیم خودکار error handling (اگر ViewModel از BaseViewModel باشد)
        setupErrorHandling()
        
        // فراخوانی متدهای abstract برای پیاده‌سازی در فرزندان
        observeData()
        setupViews()
        setupClickListeners()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        snackbarHelper.clear()
    }

    /**
     * تنظیم خودکار error handling
     */
    private fun setupErrorHandling() {
        if (viewModel is BaseViewModel) {
            val baseViewModel = viewModel as BaseViewModel
            viewLifecycleOwner.lifecycleScope.launch {
                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    baseViewModel.uiEvents.collect { event ->
                        handleUiEvent(event)
                    }
                }
            }
        }
    }

    /**
     * هندل کردن UI events
     * می‌توان در کلاس فرزند override کرد برای سفارشی‌سازی
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
                // منطق navigation - می‌توان در فرزندان override کرد
            }
            is UiEvent.DismissLoading -> {
                // منطق dismiss loading
            }
        }
    }

    /**
     * نمایش Dialog
     */
    protected open fun showDialog(event: UiEvent.ShowDialog) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
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

    /**
     * برای مشاهده LiveData ها از ViewModel
     */
    protected open fun observeData() {
        // پیاده‌سازی در کلاس‌های فرزند
    }

    /**
     * برای تنظیمات اولیه View ها
     */
    protected open fun setupViews() {
        // پیاده‌سازی در کلاس‌های فرزند
    }

    /**
     * برای تنظیم کلیک لیسنرها
     */
    protected open fun setupClickListeners() {
        // پیاده‌سازی در کلاس‌های فرزند
    }

    val operator: AnimationViewOperator by lazy {
        AnimationViewOperator(Interpolators(Easings.CIRC_OUT))
    }
}