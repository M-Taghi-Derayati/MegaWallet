package com.mtd.megawallet.ui.swap

import android.R.attr.text
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.mtd.common_ui.textChange
import com.mtd.megawallet.databinding.FragmentAssetSelectionBinding
import com.mtd.megawallet.event.SwapUiState
import com.mtd.megawallet.ui.swap.adapter.AssetSelectionAdapter
import com.mtd.megawallet.viewmodel.SwapViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AssetSelectionBottomSheet : BottomSheetDialogFragment() {

    // ما از sharedViewModel استفاده می‌کنیم تا به همان نمونه ViewModel از SwapFragment دسترسی داشته باشیم
    private val viewModel: SwapViewModel by activityViewModels()
    private lateinit var binding: FragmentAssetSelectionBinding
    private lateinit var assetAdapter: AssetSelectionAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentAssetSelectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        observeViewModel()
        setupSearch()
    }

    private fun setupRecyclerView() {
        assetAdapter = AssetSelectionAdapter { selectedAsset ->
            val currentState = viewModel.uiState.value // گرفتن state فعلی
            // تشخیص اینکه آیا مبدا انتخاب شده یا مقصد
            if (currentState is SwapUiState.Ready) {
                if (currentState.bottomSheetTitle.contains("مبدا")) {
                    viewModel.onFromAssetSelected(selectedAsset.assetId)
                } else {
                    viewModel.onToAssetSelected(selectedAsset.assetId)
                }
                dismiss()
            }

            // BottomSheet به صورت خودکار بسته می‌شود چون ViewModel state را تغییر می‌دهد
        }
        binding.rvAssets.adapter = assetAdapter
        binding.rvAssets.layoutManager = LinearLayoutManager(context)
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                if (state is SwapUiState.Ready) {
                    binding.tvBottomSheetTitle.text = state.bottomSheetTitle
                    assetAdapter.submitList(state.assetsForSelection)
                }
            }
        }
    }

    private fun setupSearch() {
        binding.editTextSearch.textChange { it ->
            if (it.toString().isNotEmpty())
                viewModel.onSearchQueryChanged(text.toString())
        }
    }
}