package com.mtd.megawallet.ui.compose.screens.createwallet

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtd.megawallet.ui.compose.animations.constants.AnimationConstants
import com.mtd.megawallet.ui.compose.components.PrimaryButton
import com.mtd.megawallet.ui.compose.components.TopHeader
import com.mtd.megawallet.viewmodel.CreateWalletViewModel
import com.mtd.common_ui.theme.IranSansLightLight
import com.mtd.common_ui.theme.IranSansRegular

/**
 * Terms and conditions data.
 */
private val TERMS = listOf(
    "من می‌پذیرم که مسئولیت کامل امنیت و تهیه نسخه پشتیبان از کیف پول‌هایم بر عهده اینجانب است، نه مگاولت",
    "من می‌پذیرم که استفاده از مگاولت برای هرگونه اهداف غیرقانونی اکیداً ممنوع و خلاف شرایط و ضوابط ما است",
    "من می‌پذیرم که مگاولت یک بانک، صرافی یا موسسه مالی متمرکز نیست",
    "من می‌پذیرم که اگر در هر زمانی دسترسی به کیف پول‌های خود را از دست بدهم، مگاولت هیچ مسئولیتی نداشته و به هیچ وجه قادر به کمک نیست"
)

/**
 * Component for terms acceptance step in create wallet flow.
 */
@Composable
fun TermsPart(
    viewModel: CreateWalletViewModel,
    modifier: Modifier = Modifier
) {
    val animatedButtonColor by animateColorAsState(
        targetValue = viewModel.selectedColor,
        animationSpec = tween(
            durationMillis = AnimationConstants.GENERATING_ANIMATION_DURATION
        ),
        label = "ButtonColorAnimation"
    )

    TermsPart(
        terms = TERMS,
        accepted = listOf(
            viewModel.term1Accepted,
            viewModel.term2Accepted,
            viewModel.term3Accepted,
            viewModel.term4Accepted
        ),
        accentColor = animatedButtonColor,
        onToggle = { index ->
            when (index) {
                0 -> viewModel.term1Accepted = !viewModel.term1Accepted
                1 -> viewModel.term2Accepted = !viewModel.term2Accepted
                2 -> viewModel.term3Accepted = !viewModel.term3Accepted
                3 -> viewModel.term4Accepted = !viewModel.term4Accepted
            }
        },
        onContinue = { viewModel.nextStep() },
        modifier = modifier
    )
}

/**
 * همان صفحهٔ تاییدِ شرایط، بدون وابستگی به ViewModelِ ساختِ کیف پول. متن‌ها و رنگِ تاکیدی پارامتر
 * شده‌اند تا فلوهای دیگر (مثلِ حذفِ کیف پول) همین اجزا و همین قاعده را داشته باشند: دکمه تا وقتی
 * همهٔ تیک‌ها زده نشده‌اند غیرفعال است. نسخهٔ بالا فقط پوسته‌ای است روی همین تابع.
 *
 * @param accepted وضعیتِ تیکِ هر بند، هم‌ترتیب با [terms].
 * @param topBar نوارِ اختیاریِ بالای صفحه (بازگشت/نشان/راهنما)؛ در فلوی ساختِ کیف پول خالی است.
 */
@Composable
fun TermsPart(
    terms: List<String>,
    accepted: List<Boolean>,
    accentColor: Color,
    onToggle: (Int) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "قوانین امنیتی را تایید کنید",
    subtitle: String = "برای ادامه، باید موارد زیر را مطالعه کرده و تایید نمایید",
    footerNote: String = "لطفاً با دقت تمام موارد را بررسی کنید، این موارد برای امنیت دارایی شما حیاتی هستند",
    buttonText: String = "موارد فوق را قبول دارم، ادامه",
    topBar: (@Composable () -> Unit)? = null
) {
    // بندی که هنوز رندر نشده را «پذیرفته» حساب نمی‌کنیم؛ لیستِ کوتاه‌تر یعنی دکمه غیرفعال می‌ماند.
    val allAccepted = terms.indices.all { accepted.getOrElse(it) { false } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
            // حاشیهٔ افقی نداشت و متن به لبهٔ صفحه می‌چسبید. `TopHeader` حاشیهٔ خودش را ندارد،
            // پس همین یک padding هر چهار بخش را هم‌تراز می‌کند.
            .padding(horizontal = 24.dp)
            .padding(top = 40.dp)
    ) {
        topBar?.invoke()

        TopHeader(
            title = title,
            subtitle = subtitle
        )

        Spacer(modifier = Modifier.height(36.dp))

        // فاصلهٔ بندها از خودِ چیدمان می‌آید نه از Spacerهای دستی، تا آخرین بند هم فاصلهٔ
        // اضافهٔ انتهایی نگیرد.
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            terms.forEachIndexed { index, termText ->
                TermItem(
                    text = termText,
                    isSelected = accepted.getOrElse(index) { false },
                    color = accentColor,
                    onToggle = { onToggle(index) }
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = footerNote,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onTertiary,
            fontFamily = IranSansLightLight,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(14.dp))

        PrimaryButton(
            text = buttonText,
            onClick = onContinue,
            enabled = allAccepted,
            containerColor = accentColor
        )

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun TermItem(
    text: String,
    isSelected: Boolean,
    color: Color,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                onClick = onToggle,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            )
            .padding(vertical = 4.dp),
        // بالا، نه وسط: بندها دو یا سه سطری‌اند و چک‌باکسِ وسط‌چین وسطِ پاراگراف می‌افتاد.
        verticalAlignment = Alignment.Top
    ) {

        // Custom animated checkbox
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(6.dp))
                .border(
                    width = 2.dp,
                    color = if (isSelected) color else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(6.dp)
                )
                .background(if (isSelected) color else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.animation.AnimatedVisibility(
                visible = isSelected,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.tertiary,
            fontFamily = IranSansRegular,
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
            // `Start` نه `Right` — در راست‌به‌چپ یکی‌اند، ولی این یکی به جهتِ چیدمان وابسته است.
            textAlign = TextAlign.Start,
            fontSize = 15.sp,
            // چک‌باکس ۲۴dp است و متن ۱۵sp؛ بدونِ این، سطرِ اول کمی بالاتر از مرکزِ چک‌باکس
            // می‌نشیند و ردیف کج به نظر می‌رسد.
            lineHeight = 24.sp
        )

    }
}
