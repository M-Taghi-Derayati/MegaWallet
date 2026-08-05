package com.mtd.domain.interfaceRepository

import com.mtd.domain.model.assets.UserToken
import com.mtd.domain.model.assets.UserTokenSelection
import kotlinx.coroutines.flow.StateFlow

/**
 * فهرستِ توکنِ **خودِ کاربر** — به‌ازای هر کیف پول و ماندگار.
 *
 * این فقط نیمهٔ ذخیره‌سازی است. هیچ صفحه‌ای نباید فهرستِ نمایشی‌اش را از این‌جا بسازد: منبعِ واحدِ
 * ادغام‌شده [IAssetCatalog] است (باندل + افزوده‌های کاربر − پنهان‌شده‌ها). این تفکیک عمدی است —
 * پخش‌شدنِ منطقِ ادغام بین ViewModelها همان الگویی است که قبلاً سرِ آیکون‌ها و wallet.keys هزینه داد.
 */
interface IUserTokenRepository {

    /**
     * انتخابِ کیف پولِ فعال، قابلِ مشاهده. با سوئیچِ کیف پول خودش عوض می‌شود.
     */
    val selection: StateFlow<UserTokenSelection>

    /**
     * همان [selection] ولی همگام — چون [IAssetCatalog] غیر-suspend است و منبعِ ادغام‌شده باید
     * بتواند در لحظهٔ خواندن پاسخ بدهد. تا وقتی [prime] کامل نشده، [UserTokenSelection.EMPTY]
     * برمی‌گردد، یعنی بدترین حالتْ رفتارِ امروزیِ برنامه است نه یک فهرستِ نادرست.
     */
    fun current(): UserTokenSelection

    /** خواندنِ ماندگار از دیسک به کشِ حافظه. خارج از ترد اصلی صدا زده شود؛ idempotent است. */
    suspend fun prime()

    /** افزودن (یا به‌روزرسانیِ) یک توکن به فهرستِ کیف پولِ فعال. اگر پنهان بود، از پنهان‌ها درمی‌آید. */
    suspend fun add(token: UserToken)

    /** حذفِ یک توکنِ **افزوده‌شدهٔ کاربر**. روی دارایی‌های باندل بی‌اثر است — آن‌ها فقط پنهان می‌شوند. */
    suspend fun remove(networkId: String, contractAddress: String)

    /**
     * پنهان/آشکار کردنِ یک دارایی با شناسه‌اش — برای دارایی‌های باندل تنها راهِ «حذف» همین است،
     * چون باندل امضا شده و واقعاً حذف‌شدنی نیست.
     */
    suspend fun setHidden(assetId: String, hidden: Boolean)

    /** پاک‌سازیِ فهرستِ یک کیف پولِ حذف‌شده، تا ایمپورتِ دوباره از صفر شروع شود. */
    suspend fun forgetWallet(walletId: String)
}
