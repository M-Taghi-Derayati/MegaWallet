package com.mtd.megawallet.ui.transaction.deposit

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.R
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.mtd.megawallet.databinding.FragmentReceiveBinding
import com.mtd.megawallet.event.ReceiveUiState
import com.mtd.megawallet.ui.transaction.adapter.ReceiveAdapter
import com.mtd.megawallet.viewmodel.ReceiveViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch


@AndroidEntryPoint
class ReceiveBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentReceiveBinding? = null
    private val binding get() = _binding!!


    private val viewModel: ReceiveViewModel by viewModels()
    private lateinit var receiveAdapter: ReceiveAdapter


    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<FrameLayout>(R.id.design_bottom_sheet)
            if (bottomSheet != null) {
                val behavior = BottomSheetBehavior.from(bottomSheet)
                // --- ۱. حالت اولیه را روی کاملاً باز تنظیم کن ---
                val topPaddingPx = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    60f,
                    resources.displayMetrics
                ).toInt()
               // behavior.expandedOffset=topPaddingPx
                behavior.state = BottomSheetBehavior.STATE_COLLAPSED
                // --- ۲. از حالت نیمه‌باز رد شو (اختیاری ولی مفید) ---
            }
        }
        return dialog
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentReceiveBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)



        setupViews()
        setupClickListeners()
        observeData()
    }

    private fun setupViews() {
        receiveAdapter = ReceiveAdapter(
            onCopyClick = { address -> copyToClipboard(address) },
            onQrClick = { address, networkName -> showQrCodeBottomSheet(address, networkName) }
        )
        binding.recyclerViewAddresses.adapter = receiveAdapter
    }

    private fun setupClickListeners() {
        // Toolbar در BottomSheet معمولاً دکمه بستن (close) دارد نه بازگشت (back)
        binding.toolbar.setNavigationIcon(com.mtd.common_ui.R.drawable.ic_baseline_arrow_back_24)
        binding.toolbar.setNavigationOnClickListener {
            dismiss() // متد برای بستن BottomSheet
        }
    }

    fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> handleUiState(state) }
            }
        }
    }

    private fun handleUiState(state: ReceiveUiState) {
        if (state is ReceiveUiState.Success) {
            receiveAdapter.submitList(state.addressGroups)
        } else if (state is ReceiveUiState.Error) {
            Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
        }
    }

    private fun copyToClipboard(address: String) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Wallet Address", address)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "آدرس کپی شد!", Toast.LENGTH_SHORT).show()
    }

    private fun showQrCodeBottomSheet(address: String, networkName: String) {
        val action = ReceiveBottomSheetFragmentDirections.actionReceiveBottomSheetFragmentToQrCodeBottomSheetFragment(address, networkName)
        findNavController().navigate(action)
    }
}