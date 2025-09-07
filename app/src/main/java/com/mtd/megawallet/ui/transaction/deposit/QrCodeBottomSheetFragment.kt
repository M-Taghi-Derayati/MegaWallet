package com.mtd.megawallet.ui.transaction.deposit


import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.navArgs
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.mtd.common_ui.toStyledAddress
import com.mtd.megawallet.databinding.BottomSheetQrCodeBinding
import net.glxn.qrgen.android.QRCode
import com.google.android.material.R as material

class QrCodeBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: BottomSheetQrCodeBinding? = null
    private val binding get() = _binding!!

    // دریافت آرگومان‌ها با استفاده از Safe Args
    private val args: QrCodeBottomSheetFragmentArgs by navArgs()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<FrameLayout>(material.id.design_bottom_sheet)
            if (bottomSheet != null) {
                val behavior = BottomSheetBehavior.from(bottomSheet)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                // --- ۲. از حالت نیمه‌باز رد شو (اختیاری ولی مفید) ---
            }
        }
        return dialog
    }


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetQrCodeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViews()
        setupClickListeners()
    }

    private fun setupViews() {
        val address = args.address
        val networkName = args.networkName


        val highlightColor = ContextCompat.getColor(requireContext(), com.mtd.common_ui.R.color.text_primary)
        binding.textFullAddress.text = args.address.toStyledAddress(
            context = requireContext(),
            startChars = 6,
            endChars = 6,
            highlightColor = highlightColor
        )


        binding.textNetworkName.text = networkName

        // ساخت و نمایش QR Code
        try {
            val qrBitmap: Bitmap = QRCode.from(address).withSize(1024, 1024).bitmap()
            binding.imageQrCode.setImageBitmap(qrBitmap)
        } catch (e: Exception) {
            // در صورت بروز خطا در ساخت QR Code
            Toast.makeText(context, "خطا در ساخت QR Code", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    private fun setupClickListeners() {
        binding.buttonCopy.setOnClickListener {
            copyToClipboard(args.address)
        }
        binding.buttonShare.setOnClickListener {
            shareAddress(args.address)
        }
    }

    private fun copyToClipboard(address: String) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Wallet Address", address)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "آدرس کپی شد!", Toast.LENGTH_SHORT).show()
    }

    private fun shareAddress(address: String) {
        val sendIntent: Intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, address)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "اشتراک‌گذاری آدرس از طریق")
        startActivity(shareIntent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}