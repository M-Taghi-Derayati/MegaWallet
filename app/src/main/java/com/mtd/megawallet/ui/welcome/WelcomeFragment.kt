package com.mtd.megawallet.ui.welcome

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import androidx.hilt.navigation.fragment.hiltNavGraphViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import com.mtd.megawallet.R
import com.mtd.megawallet.core.BaseFragment
import com.mtd.megawallet.databinding.FragmentHomeBinding
import com.mtd.megawallet.databinding.FragmentHomeBinding.bind
import com.mtd.megawallet.databinding.FragmentWelcomeBinding
import com.mtd.megawallet.event.OnboardingNavigationEvent
import com.mtd.megawallet.event.OnboardingUiState
import com.mtd.megawallet.viewbinding.viewBinding
import com.mtd.megawallet.viewmodel.MainViewModel
import com.mtd.megawallet.viewmodel.OnboardingViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlin.getValue


@AndroidEntryPoint
class WelcomeFragment : BaseFragment<FragmentWelcomeBinding, OnboardingViewModel>(R.layout.fragment_welcome) {
    override val viewModel: OnboardingViewModel by hiltNavGraphViewModels(R.id.onboarding_graph)
    override val binding by viewBinding(FragmentWelcomeBinding::bind)


    override fun setupClickListeners() {
        binding.btnCreateWallet.setOnClickListener {
            viewModel.createNewWallet()
        }

        binding.btnImportWallet.setOnClickListener {
            findNavController().navigate(R.id.action_welcome_to_import_options)
        }
    }

    override fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state -> handleUiState(state) }
                }
                launch {
                    viewModel.navigationEvent.collect { event -> handleNavigationEvent(event) }
                }
            }
        }
    }

    private fun handleUiState(state: OnboardingUiState) {
        // TODO: Add a ProgressBar to fragment_welcome.xml with id "progressBar"
        // binding.progressBar.isVisible = state is OnboardingUiState.Loading
        binding.btnCreateWallet.isEnabled = state !is OnboardingUiState.Loading
        binding.btnImportWallet.isEnabled = state !is OnboardingUiState.Loading

        if (state is OnboardingUiState.Error) {
            Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
            viewModel.errorShown() // اطلاع به ViewModel که خطا نمایش داده شد
        }
    }

    private fun handleNavigationEvent(event: OnboardingNavigationEvent) {
        when (event) {
            OnboardingNavigationEvent.NavigateToHome -> {}
            OnboardingNavigationEvent.NavigateToImportOptions ->  findNavController().navigate(R.id.action_welcome_to_import_options)
            is OnboardingNavigationEvent.NavigateToShowMnemonic -> {}
            else -> {}
        }
    }
}