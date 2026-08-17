package com.mtd.megawallet.ui.compose.screens.welcome

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import com.mtd.megawallet.R

/**
 * رنگ‌های ستاره‌های ریز.
 *
 * زرد و نارنجی از همان پالتِ خودِ المان‌ها می‌آیند (`--graphic-yellow` و نارنجیِ برنامه)، تا
 * ستاره‌ها بینِ شکل‌های اصلی بیگانه نباشند.
 */
private val SparkStone = Color(0xFFA8A29E)
private val SparkYellow = Color(0xFFF5C84C)
private val SparkOrange = Color(0xFFFF9F45)

/**
 * چیدمانِ المان‌های صفحهٔ خوش‌آمد.
 *
 * المان‌ها فایل برداری‌اند (`ic_intro_*`) و از SVGهای طراح با
 * `tools/svg_board_to_vector.py` ساخته شده‌اند. پیش از این با کد و `Canvas` کشیده می‌شدند؛
 * چون آن‌ها تک‌رنگ بودند و این‌ها چندرنگ، رنگ دیگر از پالتِ برنامه نمی‌آید و داخلِ خودِ فایل است.
 *
 * ⚠️ فایل‌ها را دستی ویرایش نکنید؛ اسکریپت را دوباره اجرا کنید.
 */
data class IntroElement(
    @DrawableRes val res: Int,
    /**
     * نسخهٔ پوستهٔ روشن، فقط برای آن‌هایی که «درخششِ» سفیدِ جدا از شکل دارند.
     *
     * سفیدِ جدا روی پس‌زمینهٔ روشنِ صفحه ناپدید می‌شود. سفیدی که **روی** شکل نشسته
     * (سه‌نقطهٔ حبابِ گفتگو، سوراخِ دونات) مشکلی ندارد و نسخهٔ دومی نمی‌گیرد.
     *
     * ⚠️ به `values-night` تکیه نمی‌کنیم: پوستهٔ برنامه انتخابیِ کاربر است و می‌تواند با
     * پوستهٔ سیستم یکی نباشد — همان اشتباهی که در اسپلش بود.
     */
    @DrawableRes val lightRes: Int? = null,
    /** مرکزِ المان، نسبت به عرض و ارتفاعِ صفحه. */
    val x: Float,
    val y: Float,
    val sizeDp: Float,
    /** چرخشِ نهایی. چرخشِ اولیهٔ حرکت از این مقدار مشتق می‌شود. */
    val rotation: Float,
    /**
     * رنگِ جایگزین.
     *
     * ⚠️ فقط برای شکل‌های **تک‌رنگ** معنی دارد: تینت روی کلِ تصویر اعمال می‌شود، پس روی یک
     * المانِ چندرنگ همه‌چیز را یک‌دست می‌کند. تنها مصرفش ستاره‌های ریز است، تا یک فایل چند رنگ
     * بدهد و نیازی به نسخهٔ جدا برای هر رنگ نباشد.
     */
    val tint: Color? = null
)

/**
 * لایهٔ پشتی — لکه‌های محوِ کم‌رنگ که فضای خالی را پُر می‌کنند و پشتِ بقیه می‌نشینند.
 *
 * همان یک فایل چند بار با اندازه و چرخشِ متفاوت تکرار شده است؛ چون شکلش بی‌جهت و نرم است،
 * تکرارش دیده نمی‌شود.
 *
 * ⚠️ عمداً از المان‌های اصلی کوچک‌ترند. در نسخهٔ اول تا ۱۴۸dp بودند و چون بزرگ‌ترین چیزِ صفحه
 * می‌شدند، خودشان جلو می‌زدند و شکل‌ها را پسِ خودشان می‌بردند — درست برعکسِ کارِ یک لایهٔ پشتی.
 */
val INTRO_BACKDROP: List<IntroElement> = listOf(
    IntroElement(R.drawable.ic_intro_17, x = 0.17f, y = 0.11f, sizeDp = 60f, rotation = -8f),
    IntroElement(R.drawable.ic_intro_17, x = 0.86f, y = 0.29f, sizeDp = 52f, rotation = 12f),
    IntroElement(R.drawable.ic_intro_17, x = 0.31f, y = 0.49f, sizeDp = 46f, rotation = 6f),
    IntroElement(R.drawable.ic_intro_17, x = 0.72f, y = 0.55f, sizeDp = 42f, rotation = -10f)
)

/**
 * المان‌های اصلی.
 *
 * در چهار نوارِ افقی از بالای صفحه تا حدودِ ۰.۵۸ چیده شده‌اند و پایین‌تر عمداً خالی است، چون
 * عنوان و دکمه‌ها آن‌جا می‌نشینند. چند المان با `x` نزدیکِ ۰ و ۱ از لبه بیرون می‌زنند تا میدان
 * بریده به نظر برسد و نه چیده‌شده در یک کادر.
 */
val INTRO_ELEMENTS: List<IntroElement> = listOf(
    // نوار ۱
    IntroElement(R.drawable.ic_intro_03, x = 0.22f, y = 0.09f, sizeDp = 72f, rotation = -10f),
    IntroElement(R.drawable.ic_intro_08, x = 0.50f, y = 0.05f, sizeDp = 44f, rotation = 8f),
    IntroElement(R.drawable.ic_intro_04, x = 0.79f, y = 0.10f, sizeDp = 78f, rotation = 6f),
    // نوار ۲
    IntroElement(R.drawable.ic_intro_02, x = 0.08f, y = 0.22f, sizeDp = 44f, rotation = -14f),
    IntroElement(
        R.drawable.ic_intro_12, R.drawable.ic_intro_12_light,
        x = 0.36f, y = 0.21f, sizeDp = 58f, rotation = 10f
    ),
    IntroElement(R.drawable.ic_intro_09, x = 0.63f, y = 0.20f, sizeDp = 66f, rotation = -6f),
    IntroElement(R.drawable.ic_intro_13, x = 0.92f, y = 0.24f, sizeDp = 50f, rotation = 0f),
    // نوار ۳
    IntroElement(
        R.drawable.ic_intro_05, R.drawable.ic_intro_05_light,
        x = 0.17f, y = 0.36f, sizeDp = 66f, rotation = 12f
    ),
    IntroElement(R.drawable.ic_intro_07, x = 0.44f, y = 0.35f, sizeDp = 60f, rotation = -8f),
    IntroElement(R.drawable.ic_intro_11, x = 0.70f, y = 0.34f, sizeDp = 58f, rotation = 8f),
    IntroElement(R.drawable.ic_intro_06, x = 0.93f, y = 0.41f, sizeDp = 46f, rotation = -18f),
    // نوار ۴
    IntroElement(R.drawable.ic_intro_10, x = 0.09f, y = 0.50f, sizeDp = 44f, rotation = 10f),
    IntroElement(R.drawable.ic_intro_14, x = 0.31f, y = 0.53f, sizeDp = 50f, rotation = -8f),
    IntroElement(R.drawable.ic_intro_16, x = 0.56f, y = 0.49f, sizeDp = 62f, rotation = 6f),
    IntroElement(R.drawable.ic_intro_15, x = 0.80f, y = 0.52f, sizeDp = 54f, rotation = -12f),
    IntroElement(
        R.drawable.ic_intro_01, R.drawable.ic_intro_01_light,
        x = 0.92f, y = 0.57f, sizeDp = 52f, rotation = 14f
    )
)

/**
 * ستاره‌های ریز — همان یک فایل، دوازده بار، در سه رنگ.
 *
 * کارشان پُرکردنِ فاصله‌های بینِ المان‌های بزرگ است، پس عمداً کوچک و بی‌قاعده‌اند.
 *
 * رنگ‌ها طوری پخش شده‌اند که هیچ دو ستارهٔ همسایه هم‌رنگ نباشند؛ با یک رنگِ واحد، دوازده نقطهٔ
 * یکسان بیشتر شبیهِ نقطه‌چینِ الگو دیده می‌شد تا پراکندگیِ طبیعی.
 */
val INTRO_SPARKS: List<IntroElement> = listOf(
    IntroElement(R.drawable.ic_intro_18, x = 0.05f, y = 0.06f, sizeDp = 18f, rotation = -12f, tint = SparkYellow),
    IntroElement(R.drawable.ic_intro_18, x = 0.40f, y = 0.14f, sizeDp = 14f, rotation = 20f, tint = SparkOrange),
    IntroElement(R.drawable.ic_intro_18, x = 0.68f, y = 0.07f, sizeDp = 16f, rotation = -8f, tint = SparkStone),
    IntroElement(R.drawable.ic_intro_18, x = 0.86f, y = 0.17f, sizeDp = 15f, rotation = 14f, tint = SparkYellow),
    IntroElement(R.drawable.ic_intro_18, x = 0.26f, y = 0.30f, sizeDp = 14f, rotation = -18f, tint = SparkOrange),
    IntroElement(R.drawable.ic_intro_18, x = 0.55f, y = 0.28f, sizeDp = 13f, rotation = 10f, tint = SparkYellow),
    IntroElement(R.drawable.ic_intro_18, x = 0.06f, y = 0.42f, sizeDp = 16f, rotation = 16f, tint = SparkStone),
    IntroElement(R.drawable.ic_intro_18, x = 0.49f, y = 0.44f, sizeDp = 13f, rotation = -14f, tint = SparkOrange),
    IntroElement(R.drawable.ic_intro_18, x = 0.85f, y = 0.47f, sizeDp = 15f, rotation = 8f, tint = SparkYellow),
    IntroElement(R.drawable.ic_intro_18, x = 0.21f, y = 0.60f, sizeDp = 14f, rotation = -10f, tint = SparkOrange),
    IntroElement(R.drawable.ic_intro_18, x = 0.41f, y = 0.61f, sizeDp = 12f, rotation = 18f, tint = SparkStone),
    IntroElement(R.drawable.ic_intro_18, x = 0.66f, y = 0.61f, sizeDp = 16f, rotation = -6f, tint = SparkOrange)
)
