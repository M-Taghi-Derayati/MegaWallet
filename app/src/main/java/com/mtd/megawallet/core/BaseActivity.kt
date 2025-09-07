// In: app/src/main/java/com/mtd/app/base/BaseActivity.kt

package com.mtd.megawallet.core

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.viewbinding.ViewBinding

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ViewBinding توسط delegate در کلاس فرزند handle خواهد شد.
        // setContentView به صورت خودکار توسط delegate فراخوانی می‌شود.

        observeData()
        setupViews()
        setupClickListeners()
    }

    protected open fun observeData() {}
    protected open fun setupViews() {}
    protected open fun setupClickListeners() {}
}