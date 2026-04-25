package com.mtd.megawallet.ui.compose.screens.security

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mtd.domain.security.SecuritySnapshot

@Composable
fun SecuritySettingsSheet(
    visible: Boolean,
    snapshot: SecuritySnapshot?,
    biometricAvailable: Boolean,
    onClose: () -> Unit,
    onEnableAppLock: () -> Unit,
    onDisableAppLock: () -> Unit,
    onChangePasscode: () -> Unit,
    onBiometricToggle: (Boolean) -> Unit,
    onTimeoutSelect: (Int) -> Unit
) {
    if (!visible) return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f))
            .clickable { onClose() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = false) {}
                .padding(horizontal = 12.dp, vertical = 16.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                Text(
                    text = "امنیت برنامه",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.tertiary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "قفل برنامه با رمز عبور و اثر انگشت",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiary
                )

                Spacer(modifier = Modifier.height(20.dp))

                val enabled = snapshot?.appLockEnabled == true
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("قفل برنامه", color = MaterialTheme.colorScheme.tertiary)
                        Text(
                            if (enabled) "فعال" else "غیرفعال",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiary
                        )
                    }
                    Switch(
                        checked = enabled,
                        onCheckedChange = { checked ->
                            if (checked) onEnableAppLock() else onDisableAppLock()
                        }
                    )
                }

                if (enabled) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = onChangePasscode,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("تغییر رمز عبور")
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("ورود با اثر انگشت", color = MaterialTheme.colorScheme.tertiary)
                            Text(
                                if (biometricAvailable) "در دستگاه پشتیبانی می‌شود" else "این دستگاه پشتیبانی نمی‌کند",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiary
                            )
                        }
                        Switch(
                            checked = snapshot.biometricEnabled == true,
                            onCheckedChange = { onBiometricToggle(it) },
                            enabled = biometricAvailable
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "زمان قفل خودکار",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TimeoutChip(
                            label = "فوری",
                            selected = snapshot.lockTimeoutSeconds == 0,
                            onClick = { onTimeoutSelect(0) },
                            modifier = Modifier.weight(1f)
                        )
                        TimeoutChip(
                            label = "30 ثانیه",
                            selected = snapshot.lockTimeoutSeconds == 30,
                            onClick = { onTimeoutSelect(30) },
                            modifier = Modifier.weight(1f)
                        )
                        TimeoutChip(
                            label = "60 ثانیه",
                            selected = snapshot.lockTimeoutSeconds == 60,
                            onClick = { onTimeoutSelect(60) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = "برای امنیت بیشتر، رمز عبور را با کسی به اشتراک نگذارید.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun TimeoutChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        else MaterialTheme.colorScheme.surface
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onTertiary,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}


@Preview
@Composable
fun SecuritySettingsSheetPreview(){
    MaterialTheme(lightColorScheme()) {
        SecuritySettingsSheet(true, SecuritySnapshot(true,true,true,30,true),true,{},{},{},{},{},{})
    }
}