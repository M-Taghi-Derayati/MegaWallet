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
import com.mtd.core.ui.UiEvent

/**
 * Helper class for displaying custom top snackbar in View-based UI (Activity/Fragment)
 * Similar to CustomTopSnackbar in Compose
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

    /**
     * Initialize with Activity
     */
    fun init(activity: Activity) {
        this.activity = activity
        this.fragment = null
    }

    /**
     * Initialize with Fragment
     */
    fun init(fragment: Fragment) {
        this.fragment = fragment
        this.activity = fragment.activity
    }

    /**
     * Show error snackbar
     */
    fun showErrorSnackbar(event: UiEvent.ShowErrorSnackbar) {
        val context = activity ?: fragment?.requireContext() ?: return
        val rootView = getRootView() ?: return

        // Save state
        currentState = ErrorSnackbarState(
            shortMessage = event.shortMessage,
            detailedMessage = event.detailedMessage,
            errorTitle = event.errorTitle
        )

        // Remove existing snackbar if any
        dismissCurrentSnackbar()

        // Create snackbar view
        val snackbarView = createSnackbarView(context, event)
        
        // Add to root view
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

        // Animate in
        animateIn(snackbarView)
    }

    /**
     * Dismiss current snackbar
     */
    fun dismissCurrentSnackbar() {
        currentSnackbarView?.let { view ->
            animateOut(view) {
                (view.parent as? ViewGroup)?.removeView(view)
                currentSnackbarView = null
            }
        }
    }

    /**
     * Get root view
     */
    private fun getRootView(): ViewGroup? {
        return activity?.findViewById(android.R.id.content) as? ViewGroup
            ?: fragment?.view?.rootView as? ViewGroup
    }

    /**
     * Create snackbar view
     */
    private fun createSnackbarView(context: android.content.Context, event: UiEvent.ShowErrorSnackbar): View {
        val cardView = MaterialCardView(context).apply {
            radius = 48f
            cardElevation = 8f
            // استفاده از رنگ error از Material Design
            val errorColor = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                val typedValue = android.util.TypedValue()
                context.theme.resolveAttribute(android.R.attr.colorError, typedValue, true)
                typedValue.data
            } else {
                ContextCompat.getColor(context, android.R.color.holo_red_dark)
            }
            setCardBackgroundColor(errorColor)
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

        // Error icon
        val icon = ImageView(context).apply {
            setImageResource(android.R.drawable.ic_dialog_alert)
            setColorFilter(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(48, 48).apply {
                marginEnd = 24
            }
        }

        // Message text
        val messageText = TextView(context).apply {
            text = event.shortMessage
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

        // Details text (if detailed message exists)
        if (event.detailedMessage.isNotEmpty()) {
            val detailsText = TextView(context).apply {
                text = "جزئیات"
                setTextColor(Color.WHITE)
                textSize = 12f
                alpha = 0.7f
            }

            container.addView(icon)
            container.addView(messageText)
            container.addView(detailsText)

            // Set click listener to show dialog
            cardView.setOnClickListener {
                showErrorDialog(context, event)
            }
        } else {
            container.addView(icon)
            container.addView(messageText)

            // Set click listener to dismiss
            cardView.setOnClickListener {
                dismissCurrentSnackbar()
            }
        }

        cardView.addView(container)
        return cardView
    }

    /**
     * Show error dialog
     */
    private fun showErrorDialog(context: android.content.Context, event: UiEvent.ShowErrorSnackbar) {
        if (event.detailedMessage.isEmpty()) return

        androidx.appcompat.app.AlertDialog.Builder(context)
            .setTitle(event.errorTitle)
            .setMessage(event.detailedMessage)
            .setPositiveButton("بستن") { dialog, _ ->
                dialog.dismiss()
                dismissCurrentSnackbar()
            }
            .setOnDismissListener {
                dismissCurrentSnackbar()
            }
            .show()
    }

    /**
     * Animate in
     */
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

    /**
     * Animate out
     */
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

    /**
     * Get status bar height
     */
    private fun getStatusBarHeight(rootView: View): Int {
        val insets = ViewCompat.getRootWindowInsets(rootView)
            ?: return 0
        val systemWindowInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        return systemWindowInsets.top
    }

    /**
     * Clear references
     */
    fun clear() {
        dismissCurrentSnackbar()
        currentState = null
        activity = null
        fragment = null
    }

    /**
     * State for error snackbar
     */
    private data class ErrorSnackbarState(
        val shortMessage: String,
        val detailedMessage: String,
        val errorTitle: String
    )
}

