@file:Suppress("DEPRECATION")

package com.mtd.common_ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Build
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.View
import android.view.WindowManager
import android.view.animation.Animation
import android.view.animation.Interpolator
import android.view.animation.TranslateAnimation
import android.widget.FrameLayout
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.core.app.ComponentActivity
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import coil.load
import coil.size.Scale
import coil.transform.RoundedCornersTransformation
import com.jakewharton.rxbinding4.view.clicks
import com.jakewharton.rxbinding4.widget.textChanges
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs

fun AppCompatTextView.setInTextColor(@ColorRes id: Int) {
    if (Build.VERSION.SDK_INT >= 23) {
        this.setTextColor(context.getColor(id))
    } else {
        this.setTextColor(context.resources.getColor(id))
    }
}

fun TextView.setInTextColor(@ColorRes id: Int) {
    if (Build.VERSION.SDK_INT >= 23) {
        this.setTextColor(context.getColor(id))
    } else {
        this.setTextColor(context.resources.getColor(id))
    }
}

fun LinearLayoutCompat.setBackgroundColors(@ColorRes id: Int) {
    if (Build.VERSION.SDK_INT >= 23) {
        this.setBackgroundColor(context.getColor(id))
    } else {
        this.setBackgroundColor(context.resources.getColor(id))
    }
}

fun FrameLayout.setBackgroundColors(@ColorRes id: Int) {
    if (Build.VERSION.SDK_INT >= 23) {
        this.setBackgroundColor(context.getColor(id))
    } else {
        this.setBackgroundColor(context.resources.getColor(id))
    }
}

fun AppCompatEditText.setInTextColor(@ColorRes id: Int) {
    if (Build.VERSION.SDK_INT >= 23) {
        this.setTextColor(context.getColor(id))
    } else {
        this.setTextColor(context.resources.getColor(id))
    }
}

fun AppCompatTextView.setInTextBackgroundColor(@ColorRes id: Int) {
    if (Build.VERSION.SDK_INT >= 23) {
        this.setBackgroundColor(context.getColor(id))
    } else {
        this.setBackgroundColor(context.resources.getColor(id))
    }
}

@ColorInt
fun Context.getColors(@ColorRes id: Int): Int {
    return if (Build.VERSION.SDK_INT >= 23) {
        getColor(id)
    } else {
        resources.getColor(id)
    }
}

fun Context.getCountryDrawableFromAsset(countrySymbol: String): Drawable? {
    return try {
        Drawable.createFromResourceStream(
            resources,
            TypedValue(),
            resources.assets.open("country_icons/${countrySymbol.toLowerCase(Locale.ROOT)}.png"),
            null
        )
    } catch (e: Exception) {
        null
    }
}

fun AppCompatTextView.setInTextBackgroundTintColor(@ColorRes id: Int) {
    if (Build.VERSION.SDK_INT >= 23) {
        this.backgroundTintList = ColorStateList.valueOf((context.getColor(id)))
    } else {
        this.backgroundTintList = ColorStateList.valueOf((context.resources.getColor(id)))
    }
}

fun getSoftColorFromText(text: String): Int {
    // یک seed بهتر برای hash سازی
    var hash = 0
    for (c in text) {
        hash = c.toInt() + (hash shl 6) + (hash shl 16) - hash
    }

    val hueRange = 360
    val saturationRange = 100
    val brightnessRange = 50

    val hue = abs(hash) % hueRange // Hue: 0-360
    val saturation = 50 + abs(hash shr 8) % saturationRange // Saturation: 50%-100%
    val brightness = 70 + abs(hash shr 16) % brightnessRange // Brightness: 70%-120%

    val hsv = floatArrayOf(
        hue.toFloat(),
        saturation / 100f,     // 0.5f to 1f
        brightness / 100f      // 0.7f to 1.2f
    )

    return Color.HSVToColor(hsv)
}

fun AppCompatImageView.loaded(link: String) {
    this.load(link) {
        crossfade(true)
        crossfade(500)
        transformations(RoundedCornersTransformation(8f))
    }
}

fun AppCompatImageView.loadedCorner(@DrawableRes res: Int) {
    this.load(res) {
        crossfade(true)
        crossfade(500)
        transformations(RoundedCornersTransformation(8f))
    }
}

fun AppCompatImageView.loaded(@DrawableRes res: Int) {
    this.load(res) {
        crossfade(true)
        crossfade(500)
    }
}

fun AppCompatImageView.loadedNotCross(@DrawableRes res: Int) {
    this.load(res)
}


fun AppCompatImageView.loadedBanner(link: String) {
    this.load(link) {
        crossfade(true)
        crossfade(500)
        scale(Scale.FILL)
    }
}


fun AppCompatTextView.setTextColorAnim(
    @ColorRes colorFrom: Int,
    @ColorRes colorTo: Int,
    inter: Interpolator,
    dur: Long = 300
) {
    val colorAnimation = if (Build.VERSION.SDK_INT >= 23) {
        ObjectAnimator.ofArgb(
            this,
            "textColor",
            context.getColor(colorFrom),
            context.getColor(colorTo)
        ).apply {
            this.duration = dur
            this.interpolator = inter
        }
    } else {
        ObjectAnimator.ofArgb(
            this,
            "textColor",
            context.resources.getColor(colorFrom),
            context.resources.getColor(colorTo)
        ).apply {
            this.duration = dur
            this.interpolator = inter
        }
    }
    AnimatorSet().apply {
        playSequentially(colorAnimation)
        start()
    }
}

fun formatNumberDecimal(
    number: BigDecimal,
    floorNumber: Int = 3
): BigDecimal {
    return number.scale(floorNumber).stripTrailingZeros()
}

fun AppCompatTextView.formatNumber(
    number: BigDecimal,
    floorNumber: Int = 3,
    preFix: String = "",
    sufFix: String = ""
) {
    this.text = "${preFix}${formatNumberDecimal(number, floorNumber).toPlainString()}${sufFix}"
}


fun BigDecimal.scale(floorNumber: Int, roundMode: RoundingMode = RoundingMode.FLOOR): BigDecimal {
    return this.setScale(floorNumber, roundMode).stripTrailingZeros()
}


fun AppCompatEditText.text(pattern: String = "#,###.####################"): BigDecimal? {
    val decimalFormat = DecimalFormat(pattern)
    return try {
        decimalFormat.parse(this.text.toString())?.toDouble()?.toBigDecimal()?.stripTrailingZeros()
            ?: BigDecimal(0)
    } catch (e: Exception) {
        null
    }
}

fun AppCompatEditText.setNumberSeparatorListener(allowDecimal: Boolean = true) {
    this.addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {

        }

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            this@setNumberSeparatorListener.removeTextChangedListener(this)

            try {
                val formattedString =
                    if (this@setNumberSeparatorListener.readNumber(true) == null &&
                        allowDecimal && this@setNumberSeparatorListener.text?.contains(Regex("^[0-9]+\\.?[0-9]*$")) == true ||
                        formatNumb(
                            (this@setNumberSeparatorListener.readNumber(allowDecimal)
                                ?: BigDecimal.ZERO)
                        ) == "0"
                    ) {
                        this@setNumberSeparatorListener.text.toString()
                    } else {
                        val number =
                            (this@setNumberSeparatorListener.readNumber() ?: BigDecimal.ZERO)
                        formatNumb(
                            (this@setNumberSeparatorListener.readNumber() ?: BigDecimal.ZERO),
                            if (number.scale() == 0) -1 else null
                        )
                    }

                //setting text after format to EditText
                this@setNumberSeparatorListener.setText(formattedString)
                this@setNumberSeparatorListener.setSelection(
                    this@setNumberSeparatorListener.getText()?.length ?: 0
                )

            } catch (nfe: NumberFormatException) {
                nfe.printStackTrace()
            }

            this@setNumberSeparatorListener.addTextChangedListener(this)
        }

        override fun afterTextChanged(s: Editable?) {

        }

    })
}

fun AppCompatTextView.readNumber(): BigDecimal? {
    return try {
        this.text.replace(Regex(","), "").run {
            BigDecimal(this)
        }
    } catch (e: Exception) {
        null
    }
}

fun AppCompatEditText.readNumber(allowDecimal: Boolean = false): BigDecimal? {
    return try {
        val text = this.text?.replace(Regex("[,٬]"), "")?.replace(Regex("٫"), ".")
        if (allowDecimal && text?.last() == '.') return null
        val data = BigDecimal(text)
        data.run {
            if (allowDecimal && this.toPlainString()?.contains(".") == true && this.toPlainString()
                    .indexOf(".").let { this.toPlainString().length == it + 1 }
            ) null
            else this
        }
    } catch (e: Exception) {
        null
    }
}

fun AppCompatTextView.setNumber(
    number: BigDecimal,
    floorNumber: Int = -1,
    prefix: String? = null,
    suffix: String? = null
) {
    this.text = with(StringBuilder()) {
        if (!prefix.isNullOrBlank()) this.append(prefix).append(" ")
        this.append(formatNumb(number, floorNumber))
        if (!suffix.isNullOrBlank()) this.append(" ").append(suffix)
        this
    }
}

fun formatNumb(number: BigDecimal, floorNumber: Int? = -1): String {
    var result = StringBuffer()
    val mFloorNumber = if (floorNumber == null) number.scale()
    else if (floorNumber < 0)
        number.stripTrailingZeros().scale()
    else floorNumber


    try {
        val integerNumb = number.setScale(0, RoundingMode.DOWN)

        integerNumb.stripTrailingZeros().toPlainString().reversed().onEachIndexed { index, i ->
            if (index != 0 && index != integerNumb.toPlainString().length && index % 3 == 0) {
                result.append(',')
                result.append(i)
            } else {
                result.append(i)
            }
        }
        result = result.reverse()
        val decimal = number.subtract(integerNumb)
        val fractionalPart =
            if (decimal.toPlainString() != "0" && decimal.setScale(1, RoundingMode.DOWN)
                    .toFloat() == 0.0f
            ) {
                val scale =
                    if (mFloorNumber + 2 > decimal.toPlainString().length) decimal.toPlainString().length
                    else mFloorNumber + 2
                decimal.toPlainString().subSequence(2, scale).toString()
            } else decimal.movePointRight(
                number.setScale(mFloorNumber, BigDecimal.ROUND_DOWN).scale()
            ).setScale(0, RoundingMode.DOWN).toPlainString()

        if (floorNumber == null || fractionalPart.toBigDecimal() != BigDecimal.ZERO) {
            result.append(".")
            result.append(fractionalPart)
        }

    } catch (e: Exception) {
    }

    return result.toString()
}

fun AppCompatTextView.text(pattern: String = "#,###.########"): BigDecimal? {
    val decimalFormat = DecimalFormat(pattern)
    return try {
        decimalFormat.parse(this.text.toString())?.toDouble()?.toBigDecimal() ?: BigDecimal(0)
    } catch (e: Exception) {
        null
    }
}


fun AppCompatActivity.makeFullScreen() {
    window.apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LOW_PROFILE
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)
            setFlags(
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            )

            addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        } else {
            decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_FULLSCREEN

        }
    }
}

fun Dialog.makeFullScreen() {
    window?.apply {
        decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)
        setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
        addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            attributes?.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }
}


fun View.mClick(debounceTime: Long = 500L, action: () -> Unit): Disposable {
    return this.clicks()
        .throttleFirst(debounceTime, TimeUnit.MILLISECONDS)
        .subscribe {
            action.invoke()
        }
}


fun AppCompatActivity.StartActivity(cls: Class<*>, intent: ((Intent) -> Unit)? = null) {
    val int = Intent(this, cls)
    intent?.invoke(int)
    this.startActivity(int)
}
fun ComponentActivity.StartActivity(cls: Class<*>, intent: ((Intent) -> Unit)? = null) {
    val int = Intent(this, cls)
    intent?.invoke(int)
    this.startActivity(int)
}

fun Fragment.StartActivity(cls: Class<*>, intent: ((Intent) -> Unit)? = null) {
    val int = Intent(requireContext(), cls)
    intent?.invoke(int)
    this.startActivity(int)
}

fun View.show() {
    visibility = View.VISIBLE
}

fun View.gone() {
    visibility = View.GONE
}

fun View.hidden() {
    visibility = View.INVISIBLE
}

fun View.startShakeAnimation(
    repeatCount: Int = 10,
    duration: Long = 50,
    shouldHorizontal: Boolean = true
) {
    when (shouldHorizontal) {
        true -> {
            TranslateAnimation(0f, 10f, 0f, 0f)
        }

        false -> TranslateAnimation(0f, 0f, 0f, 10f)
    }.apply {
        this.duration = duration
        this.repeatCount = repeatCount
        repeatMode = Animation.REVERSE
        startAnimation(this)
    }

}

@SuppressLint("CheckResult")
fun AppCompatEditText.textChange(callBack: (String?) -> Unit) {

    this.textChanges()
        .debounce(400, TimeUnit.MILLISECONDS)
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe { itSeq ->
            callBack(itSeq.toString())
        }

}

fun Context.getDrawableByResId(@DrawableRes id: Int) =
    ResourcesCompat.getDrawable(resources, id, null)

fun Context.getColorByResId(@ColorRes id: Int) = ResourcesCompat.getColor(resources, id, null)

fun Context.isStringResource(input: String): Boolean {
    // Try parsing as a regular integer
    val intValue = input.toIntOrNull()
    return if (intValue != null) {
        true // It's a regular integer
    } else {
        // Try parsing as an Android resource string
        val resId = resources.getIdentifier(input, "integer", packageName)
        resId != 0 // If resId is not found, getIdentifier() returns 0
    }
}

