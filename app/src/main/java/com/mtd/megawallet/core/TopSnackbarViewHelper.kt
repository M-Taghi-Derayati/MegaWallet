package com.mtd.megawallet.core

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Activity
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.google.android.material.card.MaterialCardView
import com.mtd.domain.model.ui.UiEvent

/**
 * Helper class for displaying custom top snackbar in View-based UI (Activity/Fragment)
 * Similar to CustomTopSnackbar in Compose
 *
 * NOTE (TASK-57): the app is 100% Compose and nothing currently calls this — the live path is
 * `AppMessageHost` + `CustomTopSnackbar`. It is kept for View-based hosts and mirrors the same
 * error/success styling policy so the two cannot drift.
 */
class TopSnackbarViewHelper private constructor() {

    private var currentSnackbarView: View? = null
    private var currentState: ErrorSnackbarState? = null
    private var activity: Activity? = null
    private var fragment: Fragment? = null

    companion object {
        @Volatile
        private var INSTANCE: TopSnackbarViewHelper? = null

        fun getInstance(): TopSnackbarViewHelper {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TopSnackbarViewHelper().also { INSTANCE = it }
            }
        }
    }


    fun init(activity: Activity) {
        this.activity = activity
        this.fragment = null
    }


    fun init(fragment: Fragment) {
        this.fragment = fragment
        this.activity = fragment.activity
    }


    fun showErrorSnackbar(event: UiEvent.ShowErrorSnackbar) {
        show(
            shortMessage = event.shortMessage,
            detailedMessage = event.detailedMessage,
            title = event.errorTitle,
            style = Style.ERROR
        )
    }

    /** TASK-57 — success-styled confirmation; no "جزئیات" affordance, tap to dismiss. */
    fun showSuccessSnackbar(event: UiEvent.ShowSuccessSnackbar) {
        show(
            shortMessage = event.message,
            detailedMessage = "",
            title = "",
            style = Style.SUCCESS
        )
    }

    private fun show(
        shortMessage: String,
        detailedMessage: String,
        title: String,
        style: Style
    ) {
        val context = activity ?: fragment?.requireContext() ?: return
        val rootView = getRootView() ?: return

        currentState = ErrorSnackbarState(
            shortMessage = shortMessage,
            detailedMessage = detailedMessage,
            errorTitle = title
        )

        dismissCurrentSnackbar()

        val snackbarView = createSnackbarView(context, shortMessage, detailedMessage, title, style)

        val layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP
            topMargin = getStatusBarHeight(rootView)
            setMargins(32, topMargin + 16, 32, 0)
        }

        rootView.addView(snackbarView, layoutParams)
        currentSnackbarView = snackbarView

        animateIn(snackbarView)
    }


    fun dismissCurrentSnackbar() {
        currentSnackbarView?.let { view ->
            animateOut(view) {
                (view.parent as? ViewGroup)?.removeView(view)
                currentSnackbarView = null
            }
        }
    }


    private fun getRootView(): ViewGroup? {
        return activity?.findViewById(android.R.id.content) as? ViewGroup
            ?: fragment?.view?.rootView as? ViewGroup
    }


    private fun createSnackbarView(
        context: android.content.Context,
        shortMessage: String,
        detailedMessage: String,
        title: String,
        style: Style
    ): View {
        val cardView = MaterialCardView(context).apply {
            radius = 48f
            cardElevation = 8f
            setCardBackgroundColor(backgroundColorFor(context, style))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(48, 32, 48, 32)
        }

        val icon = ImageView(context).apply {
            setImageResource(
                when (style) {
                    Style.ERROR -> android.R.drawable.ic_dialog_alert
                    Style.SUCCESS -> android.R.drawable.checkbox_on_background
                }
            )
            setColorFilter(Color.WHITE)
            contentDescription = if (style == Style.ERROR) "خطا" else "انجام شد"
            layoutParams = LinearLayout.LayoutParams(48, 48).apply {
                marginEnd = 24
            }
        }

        val messageText = TextView(context).apply {
            text = shortMessage
            setTextColor(Color.WHITE)
            textSize = 14f
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        if (detailedMessage.isNotEmpty()) {
            val detailsText = TextView(context).apply {
                text = "جزئیات"
                setTextColor(Color.WHITE)
                textSize = 12f
                alpha = 0.7f
            }

            container.addView(icon)
            container.addView(messageText)
            container.addView(detailsText)

            cardView.setOnClickListener {
                showErrorDialog(context, title, detailedMessage)
            }
        } else {
            container.addView(icon)
            container.addView(messageText)

            cardView.setOnClickListener {
                dismissCurrentSnackbar()
            }
        }

        cardView.addView(container)
        return cardView
    }


    /** Background colour per style — red for errors, brand green for confirmations. */
    private fun backgroundColorFor(context: android.content.Context, style: Style): Int {
        val attr = when (style) {
            Style.ERROR -> android.R.attr.colorError
            Style.SUCCESS -> android.R.attr.colorPrimary
        }
        val typedValue = android.util.TypedValue()
        return if (context.theme.resolveAttribute(attr, typedValue, true)) {
            typedValue.data
        } else {
            val fallback = when (style) {
                Style.ERROR -> android.R.color.holo_red_dark
                Style.SUCCESS -> android.R.color.holo_green_dark
            }
            ContextCompat.getColor(context, fallback)
        }
    }

    private fun showErrorDialog(
        context: android.content.Context,
        title: String,
        detailedMessage: String
    ) {
        if (detailedMessage.isEmpty()) return

        androidx.appcompat.app.AlertDialog.Builder(context)
            .setTitle(title.ifBlank { "خطا" })
            .setMessage(detailedMessage)
            .setPositiveButton("بستن") { dialog, _ ->
                dialog.dismiss()
                dismissCurrentSnackbar()
            }
            .setOnDismissListener {
                dismissCurrentSnackbar()
            }
            .show()
    }

    private fun animateIn(view: View) {
        view.translationY = -view.height.toFloat()
        view.alpha = 0f

        val animator = ValueAnimator.ofFloat(-view.height.toFloat(), 0f).apply {
            duration = 300
            interpolator = DecelerateInterpolator()
            addUpdateListener { animation ->
                val value = animation.animatedValue as Float
                view.translationY = value
                view.alpha = 1f - (value / -view.height.toFloat()).coerceIn(0f, 1f)
            }
        }
        animator.start()
    }



    private fun animateOut(view: View, onComplete: () -> Unit) {
        val animator = ValueAnimator.ofFloat(0f, -view.height.toFloat()).apply {
            duration = 250
            interpolator = DecelerateInterpolator()
            addUpdateListener { animation ->
                val value = animation.animatedValue as Float
                view.translationY = value
                view.alpha = 1f - (value / -view.height.toFloat()).coerceIn(0f, 1f)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    onComplete()
                }
            })
        }
        animator.start()
    }


    private fun getStatusBarHeight(rootView: View): Int {
        val insets = ViewCompat.getRootWindowInsets(rootView)
            ?: return 0
        val systemWindowInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        return systemWindowInsets.top
    }

    fun clear() {
        dismissCurrentSnackbar()
        currentState = null
        activity = null
        fragment = null
    }


    private data class ErrorSnackbarState(
        val shortMessage: String,
        val detailedMessage: String,
        val errorTitle: String
    )

    /** Mirrors `TopSnackbarStyle` on the Compose side. */
    private enum class Style { ERROR, SUCCESS }
}

