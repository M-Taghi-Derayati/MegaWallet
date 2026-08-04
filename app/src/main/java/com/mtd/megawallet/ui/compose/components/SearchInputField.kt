package com.mtd.megawallet.ui.compose.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mtd.common_ui.theme.IranSansRegular
import com.mtd.common_ui.theme.MegaWalletTheme

/**
 * فیلد جست‌وجوی بالای لیست دارایی.
 *
 * جدا از [RecipientInputSection] است چون کارِ دیگری می‌کند، ولی ابعاد و استایلش از همان
 * ثابت‌های [FIELD_CORNER_RADIUS]… می‌آید تا دو صفحه هم‌شکل بمانند.
 */
@Composable
internal fun SearchInputField(
    value: String,
    label: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val hasInput = value.isNotBlank()

    Surface(
        modifier = modifier.fillMaxWidth().animateContentSize(),
        shape = RoundedCornerShape(FIELD_CORNER_RADIUS),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = FIELD_PADDING_HORIZONTAL, vertical = FIELD_PADDING_VERTICAL),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(FIELD_LABEL_CORNER_RADIUS))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = label,
                    color = MaterialTheme.colorScheme.onTertiary,
                    fontFamily = IranSansRegular,
                    fontSize = FIELD_LABEL_FONT_SIZE
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.tertiary,
                        fontSize = FIELD_TEXT_FONT_SIZE,
                        fontFamily = IranSansRegular,
                        textAlign = TextAlign.Right
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { inner ->
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                color = MaterialTheme.colorScheme.onTertiary.copy(alpha = 0.5f),
                                fontFamily = IranSansRegular,
                                fontSize = FIELD_TEXT_FONT_SIZE,
                                textAlign = TextAlign.Right,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        inner()
                    }
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Crossfade(
                targetState = hasInput,
                animationSpec = tween(durationMillis = 220),
                label = "SearchAction"
            ) { showClear ->
                Box(
                    modifier = Modifier
                        .size(FIELD_ACTION_SIZE)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(enabled = showClear) { onValueChange("") },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (showClear) Icons.Default.Close else Icons.Default.Search,
                        contentDescription = if (showClear) "Clear" else null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(FIELD_ACTION_ICON_SIZE)
                    )
                }
            }
        }
    }
}

// ============================================
// Previews
// ============================================

@Preview(name = "SearchInputField empty - Light")
@Composable
private fun SearchInputFieldEmptyLightPreview() {
    MegaWalletTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.padding(16.dp)) {
                SearchInputField(
                    value = "",
                    label = "جست‌وجو",
                    placeholder = "جست‌وجو در دارایی‌های شما",
                    onValueChange = {}
                )
            }
        }
    }
}

@Preview(name = "SearchInputField filled - Dark")
@Composable
private fun SearchInputFieldFilledDarkPreview() {
    MegaWalletTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.padding(16.dp)) {
                SearchInputField(
                    value = "تتر",
                    label = "جست‌وجو",
                    placeholder = "جست‌وجو در دارایی‌های شما",
                    onValueChange = {}
                )
            }
        }
    }
}
