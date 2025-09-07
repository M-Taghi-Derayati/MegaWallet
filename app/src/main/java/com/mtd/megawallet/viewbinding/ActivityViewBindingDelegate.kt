
package com.mtd.megawallet.viewbinding

import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import androidx.viewbinding.ViewBinding
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 * یک Delegate برای ساخت و مدیریت ViewBinding در Activity ها.
 * این نسخه با BaseActivity و وراثت سازگار است.
 */
class ActivityViewBindingDelegate<T : ViewBinding>(
    private val bindingInflater: (LayoutInflater) -> T
) : ReadOnlyProperty<AppCompatActivity, T> {

    private var binding: T? = null

    override fun getValue(thisRef: AppCompatActivity, property: KProperty<*>): T {
        // اگر binding از قبل ساخته شده، آن را برگردان
        binding?.let { return it }

        // ساخت binding با استفاده از inflater ای که به ما داده شده
        val newBinding = bindingInflater.invoke(thisRef.layoutInflater)

        // تنظیم ContentView اکتیویتی
        thisRef.setContentView(newBinding.root)

        // ذخیره کردن binding برای استفاده‌های بعدی و برگرداندن آن
        return newBinding.also { this.binding = it }
    }
}

/**
 * اکستنشن فانکشن برای استفاده راحت‌تر از Delegate در Activity ها.
 */
fun <T : ViewBinding> AppCompatActivity.viewBinding(
    bindingInflater: (LayoutInflater) -> T
): ActivityViewBindingDelegate<T> = ActivityViewBindingDelegate(bindingInflater)