
package com.mtd.megawallet.core

import android.os.Bundle
import android.view.View
import androidx.annotation.LayoutRes
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.viewbinding.ViewBinding
import com.mtd.common_ui.AnimationViewOperator
import com.mtd.core.utils.Easings
import com.mtd.core.utils.Interpolators

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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // فراخوانی متدهای abstract برای پیاده‌سازی در فرزندان
        observeData()
        setupViews()
        setupClickListeners()
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