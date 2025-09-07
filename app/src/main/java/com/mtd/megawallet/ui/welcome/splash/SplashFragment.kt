package com.mtd.megawallet.ui.welcome.splash

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.mtd.megawallet.R
import com.mtd.megawallet.event.MainNavigationEvent
import com.mtd.megawallet.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SplashFragment : Fragment(R.layout.fragment_splash) {

    // ما از activityViewModels استفاده می‌کنیم تا به همان نمونه ViewModel که در MainActivity است دسترسی پیدا کنیم.
    private val viewModel: MainViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeNavigation()
    }

    private fun observeNavigation() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Collect کردن StateFlow
                viewModel.navigationState
                    .filter { it !is MainNavigationEvent.Loading } // فقط به ایونت‌های واقعی ناوبری گوش بده
                    .collect { state ->
                        when (state) {
                            MainNavigationEvent.NavigateToHome -> {
                                findNavController().navigate(R.id.action_splash_to_home)
                            }
                            MainNavigationEvent.NavigateToOnboarding -> {
                                findNavController().navigate(R.id.action_splash_to_onboarding)
                            }
                            MainNavigationEvent.Loading -> {
                                // کاری انجام نمی‌دهیم، در صفحه اسپلش باقی می‌مانیم
                            }
                        }
                    }
            }
        }
        }
}