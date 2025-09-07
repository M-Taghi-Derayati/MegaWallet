package com.mtd.megawallet


import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.mtd.data.service.TransactionMonitorService
import com.mtd.megawallet.core.BaseActivity
import com.mtd.megawallet.databinding.ActivityMainBinding
import com.mtd.megawallet.viewbinding.viewBinding
import com.mtd.megawallet.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : BaseActivity<ActivityMainBinding, MainViewModel>() {

    override val viewModel: MainViewModel by viewModels()
    override val binding by viewBinding(ActivityMainBinding::inflate)

    @Inject
    lateinit var transactionMonitorService: TransactionMonitorService


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContentView(R.layout.activity_main)
    }

    override fun onStart() {
        super.onStart()
        // ۲. شروع مانیتورینگ وقتی اپلیکیشن قابل مشاهده می‌شود (به foreground می‌آید)
        // ما از lifecycleScope خود اکتیویتی استفاده می‌کنیم تا مطمئن شویم
        // Coroutineها با از بین رفتن اکتیویتی به درستی کنسل می‌شوند.
        transactionMonitorService.startMonitoring()
    }

    override fun onStop() {
        super.onStop()
        // ۳. توقف مانیتورینگ وقتی اپلیکیشن دیگر قابل مشاهده نیست (به background می‌رود)
        // این کار از مصرف بیهوده باتری و دیتا جلوگیری می‌کند.
        transactionMonitorService.stopMonitoring()
    }
}