package com.mtd.megawallet


import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import com.mtd.data.service.TransactionMonitorService
import com.mtd.data.socket.NotificationSocketManager
import com.mtd.megawallet.core.BaseActivity
import com.mtd.megawallet.databinding.ActivityMainBinding
import com.mtd.megawallet.viewbinding.viewBinding
import com.mtd.megawallet.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : BaseActivity<ActivityMainBinding, MainViewModel>() {

    override val viewModel: MainViewModel by viewModels()
    override val binding by viewBinding(ActivityMainBinding::inflate)

    @Inject
    lateinit var transactionMonitorService: TransactionMonitorService

    @Inject
    lateinit var socketManager: NotificationSocketManager


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContentView(binding.root)
        askNotificationPermission()
        socketManager.connect()
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
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

    override fun onDestroy() {
        super.onDestroy()
        socketManager.disconnect()
    }

    /**
     * چک می‌کند و در صورت نیاز، اجازه نوتیفیکیشن را از کاربر درخواست می‌کند.
     */
    private fun askNotificationPermission() {
        val requestPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
                if (isGranted) {
                    // اجازه داده شد. حالا می‌توانیم نوتیفیکیشن ارسال کنیم.
                    Timber.i("POST_NOTIFICATIONS permission granted.")
                } else {
                    // اجازه رد شد. باید به کاربر توضیح دهیم که چرا این اجازه مهم است.
                    Timber.w("POST_NOTIFICATIONS permission denied.")
                    // می‌توانید یک SnackBar یا Dialog نمایش دهید.
                }
            }
        // این جریان فقط برای اندروید ۱۳ (TIRAMISU) و بالاتر لازم است
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    // اجازه از قبل داده شده است. کاری لازم نیست.
                    Timber.d("Notification permission is already granted.")
                }

                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    // (اختیاری) اگر کاربر قبلاً یک بار اجازه را رد کرده،
                    // می‌توانید یک دیالوگ برای توضیح بیشتر نمایش دهید.
                    // "برای اطلاع از وضعیت معاملات، به اجازه نوتیفیکیشن نیاز داریم."
                    // سپس لانچر را فراخوانی کنید.
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }

                else -> {
                    // برای اولین بار اجازه را درخواست می‌کنیم
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }
}