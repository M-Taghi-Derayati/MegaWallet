package com.mtd.megawallet.ui.compose.screens.wallet.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtd.common_ui.R
import com.mtd.common_ui.theme.IranSansBold
import com.mtd.common_ui.theme.IranSansRegular
import com.mtd.megawallet.ui.compose.components.BottomSecuritySection
import com.mtd.megawallet.ui.compose.components.PrimaryButton


val REMOVAL_TERMS: List<String> = listOf(
    "می‌دانم حذفِ کیف پول آن را پاک نمی‌کند، فقط از این دستگاه برمی‌دارد.",
    "می‌دانم اگر پیش از حذف پشتیبان نگرفته باشم، ممکن است دسترسی‌ام به این کیف پول را از دست بدهم.",
    "می‌دانم بازیابیِ کیف پول تنها با عبارتِ بازیابی یا کلید خصوصیِ خودم ممکن است.",
    "می‌دانم این کار برگشت‌ناپذیر است و باید پیش از ادامه از پشتیبانم مطمئن باشم."
)

/**
 * مرحلهٔ پایانی: کارت رفته و جایش این متن‌ها می‌نشیند.
 *
 * وسط‌چین است نه بالاچین — چیزی بالای صفحه نمانده که با آن هم‌تراز شود.
 */
@Composable
fun WalletRemovalDone(
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(1f))

        Text(
            text = "کیف پول حذف شد",
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = IranSansBold,
            fontSize = 24.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "این کیف پول از دستگاه شما برداشته شد. هر وقت خواستید می‌توانید با روش بازیابی دوباره اضافه‌اش کنید.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = IranSansRegular,
            fontSize = 15.sp,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.weight(1f))



        BottomSecuritySection("اگر از کیف پول خود پشتیبان گرفته باشید، می‌توانید بعداً با یک روش بازیابی معتبر دوباره آن را اضافه کنید.",
            icon = R.drawable.ic_restore)

        Spacer(Modifier.height(16.dp))

        PrimaryButton(text = "انجام شد", onClick = onDone)

        Spacer(Modifier.height(20.dp))
    }
}

/**
 * مرحلهٔ اولِ حذفِ کیف‌پول: متن‌ها بالای کارت، هشدار و دکمه پایینش.
 *
 * خودِ کارت این‌جا کشیده **نمی‌شود** — همان کارتِ داخلِ فهرست است که با حالتِ `removing` به وسط
 * می‌آید. اگر این‌جا کارت بسازیم، دو کارت می‌شود و حرکتِ پیوسته از بین می‌رود.
 * وسطِ صفحه عمداً خالی گذاشته شده تا کارت دقیقاً همان‌جا بنشیند.
 */
@Composable
fun WalletRemovalIntro(
    onClose: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(56.dp))

        Text(
            text = "حذف کیف پول",
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = IranSansBold,
            fontSize = 26.sp
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "این کیف پول برای همیشه از این دستگاه حذف می‌شود.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = IranSansRegular,
            fontSize = 15.sp
        )

        // جای کارت. کارت از فهرست به این ناحیه می‌آید، پس این‌جا فقط فضا نگه داشته می‌شود.
        Spacer(Modifier.weight(1f))

        BottomSecuritySection("مطمئن شوید از کیف پول خود پشتیبان دستی یا ابری گرفته‌اید؛ در غیر این صورت دسترسی به آن را برای همیشه از دست می‌دهید.",
            icon = R.drawable.ic_danger)


        Spacer(Modifier.height(16.dp))

        PrimaryButton(
            text = "ادامه",
            onClick = onContinue,
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = Color.White
        )

        Spacer(Modifier.height(20.dp))
    }
}
