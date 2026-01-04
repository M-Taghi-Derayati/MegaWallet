package com.mtd.megawallet.ui.welcome.home


import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.mtd.common_ui.StartActivity
import com.mtd.common_ui.mClick
import com.mtd.common_ui.setTextColorAnim
import com.mtd.core.utils.Easings
import com.mtd.core.utils.Interpolators
import com.mtd.megawallet.R
import com.mtd.megawallet.core.BaseFragment
import com.mtd.megawallet.databinding.FragmentHomeBinding
import com.mtd.megawallet.event.HomeUiState
import com.mtd.megawallet.event.HomeUiState.DisplayCurrency
import com.mtd.megawallet.ui.swap.TradeActivity
import com.mtd.megawallet.ui.welcome.home.adapter.ActivityAdapter
import com.mtd.megawallet.ui.welcome.home.adapter.AssetsAdapter
import com.mtd.megawallet.viewbinding.viewBinding
import com.mtd.megawallet.viewmodel.HomeViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch


@AndroidEntryPoint
class HomeFragment : BaseFragment<FragmentHomeBinding, HomeViewModel>(R.layout.fragment_home) {

    override val viewModel: HomeViewModel by activityViewModels()
    override val binding by viewBinding(FragmentHomeBinding::bind)


    // ViewBinding های تو در تو برای دسترسی راحت

    private val mainContentBinding by lazy { binding.mainContent }
    private val bottomSheetContentBinding by lazy { binding.bottomSheetContent }
    private lateinit var isViewLastSelectMenuBasketStatus: AppCompatTextView

    private val assetsAdapter = AssetsAdapter()
    private val activityAdapter = ActivityAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return view ?: inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun setupViews() {

        isViewLastSelectMenuBasketStatus = mainContentBinding.txtMenuApps
        bottomSheetContentBinding.recyclerViewAssets.adapter = assetsAdapter
        mainContentBinding.recyclerViewHistory.adapter = activityAdapter

        // تنظیم رفتار اولیه BottomSheet
        val bottomSheetBehavior = BottomSheetBehavior.from(binding.bottomSheet)
        // محاسبه ارتفاع‌ها
        val screenHeight = resources.displayMetrics.heightPixels
        val collapsedHeight = (screenHeight * 0.1).toInt()


        // حالت اولیه
        bottomSheetBehavior.peekHeight = collapsedHeight
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HALF_EXPANDED

        bottomSheetBehavior.isFitToContents = false
        bottomSheetBehavior.halfExpandedRatio = 0.61f
        val topPaddingPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            80f,
            resources.displayMetrics
        ).toInt()
        bottomSheetBehavior.expandedOffset = topPaddingPx
        bottomSheetBehavior.addBottomSheetCallback(object :
            BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                // اینجا میشه روی تغییر حالت‌ها واکنش داد
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                // انیمیشن یا تغییر UI هنگام کشیدن
            }
        })

        // لمس و کشیدن بین سه حالت
        binding.bottomSheet.setOnTouchListener(object : View.OnTouchListener {
            private var startY = 0f
            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                when (event?.action) {
                    MotionEvent.ACTION_DOWN -> startY = event.rawY
                    MotionEvent.ACTION_UP -> {
                        val endY = event.rawY
                        val delta = startY - endY

                        if (delta > 100) { // کشیدن به بالا
                            when (bottomSheetBehavior.state) {
                                BottomSheetBehavior.STATE_COLLAPSED -> {
                                    bottomSheetBehavior.state =
                                        BottomSheetBehavior.STATE_HALF_EXPANDED
                                }

                                BottomSheetBehavior.STATE_HALF_EXPANDED -> {
                                    bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
                                }
                            }
                        } else if (delta < -100) { // کشیدن به پایین
                            when (bottomSheetBehavior.state) {
                                BottomSheetBehavior.STATE_EXPANDED -> {
                                    bottomSheetBehavior.state =
                                        BottomSheetBehavior.STATE_HALF_EXPANDED
                                }

                                BottomSheetBehavior.STATE_HALF_EXPANDED -> {
                                    bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                                }
                            }
                        }
                    }
                }
                return false
            }
        })


    }

    override fun setupClickListeners() {
        mainContentBinding.buttonSend.mClick {
            findNavController().navigate(R.id.action_home_to_send_graph)
        }
        mainContentBinding.buttonReceive.mClick {
            findNavController().navigate(R.id.action_homeFragment_to_receiveBottomSheet)
        }

        mainContentBinding.buttonSwap.mClick {
            StartActivity(TradeActivity::class.java)

          //  findNavController().navigate(R.id.action_homeFragment_to_swapFragment)
        }

        mainContentBinding.txtMenuApps.mClick {
            mAnimBasketVisualSelect(mainContentBinding.txtMenuApps)
        }
        mainContentBinding.txtMenuHistory.mClick {
            mAnimBasketVisualSelect(mainContentBinding.txtMenuHistory)
        }
        mainContentBinding.iconSettings.mClick {
            viewModel.toggleDisplayCurrency()
        }
        // ... بقیه کلیک‌ها
    }

    override fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        handleUiState(state)
                    }
                }
            }
        }
    }

    private fun handleUiState(state: HomeUiState) {
        // مدیریت کلی ProgressBar اصلی
        // این ProgressBar زمانی نمایش داده می‌شود که هیچ داده‌ای (حتی اسکلت) وجود ندارد

        // بقیه UI فقط زمانی نمایش داده می‌شود که در حالت Loading نباشیم
        mainContentBinding.root.isVisible = state !is HomeUiState.Loading
        binding.bottomSheet.isVisible = state !is HomeUiState.Loading

        when (state) {
            is HomeUiState.Success -> {
                val balanceText = when (state.displayCurrency) {
                    DisplayCurrency.IRR -> {
                        mainContentBinding.textTotalUsdt.text = "تومان"
                        state.totalBalanceIrr
                    }
                    DisplayCurrency.USDT -> {
                        mainContentBinding.textTotalUsdt.text = "تتر"
                        state.totalBalanceUsdt
                    }
                }
                mainContentBinding.textTotalBalance.text = balanceText

                // آپدیت کردن بخش بالایی (موجودی کل)

                // ارسال لیست به آداپترها
                assetsAdapter.submitList(state.assets)
                activityAdapter.submitList(state.recentActivity)
            }

            is HomeUiState.Error -> {
                // مخفی کردن UI اصلی و نمایش صفحه خطا
                mainContentBinding.root.isVisible = false
                binding.bottomSheet.isVisible = false
                // TODO: Show a dedicated error view with a retry button
                Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
            }

            is HomeUiState.Loading -> {
                // کاری لازم نیست انجام شود، چون وضعیت کلی قبلاً مدیریت شده
            }
        }
    }

    private fun mAnimBasketVisualSelect(view: AppCompatTextView) {
        operator.translateLeftRight(mainContentBinding.vSelectMenu, view.x, 500)
            .doOnSubscribe {
                mAnimMenuBasketStatus(view)
            }
            .subscribe()

    }

    private fun mAnimMenuBasketStatus(view: AppCompatTextView) {
        if (isViewLastSelectMenuBasketStatus != view) {
            isViewLastSelectMenuBasketStatus.setTextColorAnim(
                com.mtd.common_ui.R.color.text_primary,
                com.mtd.common_ui.R.color.text_secondary,
                Interpolators(Easings.CUBIC_IN_OUT)
            )
            view.setTextColorAnim(
                com.mtd.common_ui.R.color.text_secondary,
                com.mtd.common_ui.R.color.text_primary,
                Interpolators(Easings.CUBIC_IN_OUT)
            )
            isViewLastSelectMenuBasketStatus = view
        }
    }

}