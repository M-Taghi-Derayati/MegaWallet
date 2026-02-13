package com.mtd.megawallet.ui.compose.screens.createwallet

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtd.common_ui.R
import com.mtd.megawallet.ui.compose.components.PrimaryButton
import com.mtd.megawallet.ui.compose.components.TopHeader
import com.mtd.megawallet.viewmodel.news.CreateWalletViewModel

/**
 * Component for wallet name input step in create wallet flow.
 */
@Composable
fun NameInputPart(
    viewModel: CreateWalletViewModel,
    modifier: Modifier = Modifier
) {


    //viewModel.deleteCloudBackup()
    Column(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .imePadding()
            .padding(top = 40.dp)
    ) {
        TopHeader(
            title = "کیف پول خود را نام گذاری کنید",
            subtitle = "یک نام مستعار برای کیف پول خود انتخاب کنید"
        )

        Spacer(modifier = Modifier.height(48.dp))

        // Wallet name input field
        Box(modifier = Modifier.fillMaxWidth()) {
            if (viewModel.walletName.isEmpty()) {
                Text(
                    text = "کیف پول جدید من",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onTertiary.copy(alpha = 0.5f),
                    fontFamily = FontFamily(Font(R.font.iransansmobile_fa_regular, FontWeight.Normal)),
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = 22.sp,
                    textAlign = TextAlign.Right
                )
            }

            BasicTextField(
                value = viewModel.walletName,
                onValueChange = { viewModel.walletName = it },
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.headlineMedium.copy(
                    color = MaterialTheme.colorScheme.tertiary,
                    textAlign = TextAlign.Right,
                    fontSize = 22.sp,
                    fontFamily = FontFamily(Font(R.font.iransansmobile_fa_regular, FontWeight.Normal))
                ),
                cursorBrush = SolidColor(viewModel.selectedColor),
                singleLine = true
            )
        }

        Spacer(modifier = Modifier.height(25.dp))

        Text(
            text = "نام مستعار شما خصوصی است و فقط برای شما قابل مشاهده است. شما می‌توانید آن را بعداً در هر زمانی تغییر دهید.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onTertiary.copy(alpha = 0.7f),
            fontFamily = FontFamily(Font(R.font.iransansmobile_fa_regular, FontWeight.Light)),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Right
        )

        Spacer(modifier = Modifier.weight(1f))

        // Continue button
        PrimaryButton(
            text = "ادامه",
            onClick = { viewModel.nextStep() },
            enabled = viewModel.walletName.isNotBlank(),
            containerColor = viewModel.selectedColor
        )

    }
}
