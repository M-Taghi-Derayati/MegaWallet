package com.mtd.megawallet.ui.importoption

import android.os.Bundle
import android.text.SpannableStringBuilder
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.mtd.megawallet.R
import com.mtd.megawallet.core.BaseFragment
import com.mtd.megawallet.databinding.FragmentImportOptionsBinding
import com.mtd.megawallet.event.OnboardingUiState
import com.mtd.megawallet.viewbinding.viewBinding
import com.mtd.megawallet.viewmodel.MainViewModel
import com.mtd.megawallet.viewmodel.OnboardingViewModel
import dagger.hilt.android.AndroidEntryPoint


@AndroidEntryPoint
class ImportOptionsFragment : BaseFragment<FragmentImportOptionsBinding, MainViewModel>(R.layout.fragment_import_options) {

    override val viewModel: MainViewModel by activityViewModels()
    val onboardingViewModel: OnboardingViewModel by activityViewModels()
    override val binding by viewBinding(FragmentImportOptionsBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        onboardingViewModel.resetStateForNewScreen(OnboardingUiState.EnteringSeed())
    }

    override fun setupClickListeners() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().popBackStack()
        }

        binding.cardImportSeed.setOnClickListener {
            findNavController().navigate(R.id.action_import_options_to_import_seed)
        }

        binding.cardImportBackup.setOnClickListener {
            findNavController().navigate(R.id.action_import_options_to_import_backup)
        }
    }


}