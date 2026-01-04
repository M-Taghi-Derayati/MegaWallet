package com.mtd.megawallet.ui.swap

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import com.mtd.megawallet.databinding.DialogLoadingBinding

class LoadingDialogFragment : DialogFragment() {

    private var _binding: DialogLoadingBinding? = null
    private val binding get() = _binding!!

    // کلید برای ارسال پیام به دیالوگ
    companion object {
        const val TAG = "LoadingDialog"
        private const val ARG_MESSAGE = "arg_message"

        fun newInstance(message: String? = null): LoadingDialogFragment {
            return LoadingDialogFragment().apply {
                arguments = bundleOf(ARG_MESSAGE to message)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogLoadingBinding.inflate(inflater, container, false)
        // تنظیمات ظاهری دیالوگ
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val message = arguments?.getString(ARG_MESSAGE)
        if (!message.isNullOrEmpty()) {
            binding.tvLoadingMessage.text = message
            binding.tvLoadingMessage.visibility = View.VISIBLE
        } else {
            binding.tvLoadingMessage.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /**
     * یک متد برای آپدیت کردن پیام در حالی که دیالوگ باز است.
     */
    fun updateMessage(newMessage: String) {
        if (_binding != null) {
            binding.tvLoadingMessage.text = newMessage
            binding.tvLoadingMessage.visibility = View.VISIBLE
        }
        // آرگومان‌ها را هم آپدیت می‌کنیم تا در صورت چرخش صفحه، پیام حفظ شود
        arguments?.putString(ARG_MESSAGE, newMessage)
    }
}