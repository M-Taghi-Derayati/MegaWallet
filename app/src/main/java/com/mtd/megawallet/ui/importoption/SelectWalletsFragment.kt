package com.mtd.megawallet.ui.importoption

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.hilt.navigation.fragment.hiltNavGraphViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.mtd.megawallet.R
import com.mtd.megawallet.core.BaseFragment
import com.mtd.megawallet.databinding.FragmentSelectWalletsBinding
import com.mtd.megawallet.event.OnboardingNavigationEvent
import com.mtd.megawallet.event.OnboardingUiState
import com.mtd.megawallet.ui.importoption.adapter.SelectWalletsAdapter
import com.mtd.megawallet.viewbinding.viewBinding
import com.mtd.megawallet.viewmodel.OnboardingViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch


@AndroidEntryPoint
class SelectWalletsFragment : BaseFragment<FragmentSelectWalletsBinding, OnboardingViewModel>(R.layout.fragment_select_wallets) {
    override val viewModel: OnboardingViewModel by hiltNavGraphViewModels(R.id.onboarding_graph)
    override val binding by viewBinding(FragmentSelectWalletsBinding::bind)
    private val args: SelectWalletsFragmentArgs  by navArgs()
    private lateinit var walletsAdapter: SelectWalletsAdapter



    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.discoverAccountsFromMnemonic(args.mnemonic,args.privateKey)
    }

    override fun setupViews() {
        walletsAdapter = SelectWalletsAdapter { accountId, isSelected ->
            viewModel.onAccountSelectionChanged(accountId, isSelected)
        }
        binding.recyclerViewWallets.adapter = walletsAdapter
    }



    override fun setupClickListeners() {
        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        // حالا mnemonic را به متد import پاس می‌دهیم
        binding.buttonImport.setOnClickListener {
            viewModel.importSelectedAccounts(args.mnemonic,args.privateKey)
        }
    }

    override fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        handleUiState(state)
                    }
                }
                launch {
                    viewModel.navigationEvent.collect { event ->
                        if (event is OnboardingNavigationEvent.NavigateToHome) {
                            // به صفحه اصلی برو و تمام صفحات قبلی (onboarding) را از backstack پاک کن
                            findNavController().navigate(
                                R.id.action_global_homeFragment,
                                null,
                                NavOptions.Builder().setPopUpTo(R.id.onboarding_graph, true).build()
                            )
                        }
                    }
                }

            }
        }
    }

    private fun handleUiState(state: OnboardingUiState) {
        when(state){
            is OnboardingUiState.WalletsToImport ->{
                binding.progressBar.isVisible = state.isLoading
                walletsAdapter.submitList(state.accounts)

                val selectedCount = state.selectedAccountIds.size
                binding.buttonImport.isEnabled = selectedCount > 0 && !state.isLoading
                if (selectedCount > 0) {
                    binding.buttonImport.text = "وارد کردن $selectedCount کیف پول"
                } else {
                    binding.buttonImport.text = "یک کیف پول انتخاب کنید"
                }
            }
            else->{}
        }




        // ... update description text
    }

}