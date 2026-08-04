package com.mtd.megawallet.ui.compose.screens.send

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtd.common_ui.R
import com.mtd.common_ui.theme.IranSansRegular
import com.mtd.common_ui.theme.IranSansRegularMedium
import com.mtd.common_ui.theme.MegaWalletTheme

@Composable
internal fun ScanAddressRow(onScanClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onScanClick)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_scan),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiary,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.Start) {
            Text(
                text = "QR اسکن کد",
                color = MaterialTheme.colorScheme.tertiary,
                fontFamily = IranSansRegularMedium,
                fontSize = 17.sp,
                textAlign = TextAlign.Right
            )
            Text(
                text = "برای اسکن آدرس ضربه بزنید",
                color = MaterialTheme.colorScheme.onTertiary,
                fontFamily = IranSansRegular,
                fontSize = 14.sp,
                textAlign = TextAlign.Right
            )
        }
    }
}

// ============================================
// Previews
// ============================================

@Preview(name = "ScanAddressRow - Light")
@Composable
private fun ScanAddressRowLightPreview() {
    MegaWalletTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.padding(16.dp)) { ScanAddressRow(onScanClick = {}) }
        }
    }
}
