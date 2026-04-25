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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mtd.domain.security.AppLockManager

@Composable
fun PasscodeSetupSheet(
    visible: Boolean,
    biometricAvailable: Boolean,
    defaultBiometricEnabled: Boolean,
    onClose: () -> Unit,
    onSubmit: (passcode: String, biometricEnabled: Boolean) -> Unit
) {
    if (!visible) return

    var passcode by rememberSaveable { mutableStateOf("") }
    var confirmPasscode by rememberSaveable { mutableStateOf("") }
    var biometricEnabled by rememberSaveable { mutableStateOf(defaultBiometricEnabled) }

    val digitsOnlyPasscode = passcode.filter { it.isDigit() }.take(AppLockManager.PASSCODE_LENGTH)
    val digitsOnlyConfirm = confirmPasscode.filter { it.isDigit() }.take(AppLockManager.PASSCODE_LENGTH)
    val matches = digitsOnlyPasscode == digitsOnlyConfirm
    val ready = digitsOnlyPasscode.length == AppLockManager.PASSCODE_LENGTH &&
            digitsOnlyConfirm.length == AppLockManager.PASSCODE_LENGTH &&
            matches

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
                    .padding(18.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "تنظیم رمز عبور برنامه",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.tertiary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "یک رمز ${AppLockManager.PASSCODE_LENGTH} رقمی انتخاب کنید.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiary
                )

                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = digitsOnlyPasscode,
                    onValueChange = { passcode = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("رمز عبور") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation()
                )

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = digitsOnlyConfirm,
                    onValueChange = { confirmPasscode = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("تکرار رمز عبور") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation()
                )

                if (digitsOnlyConfirm.isNotEmpty() && !matches) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "رمز وارد شده یکسان نیست.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("ورود با اثر انگشت", color = MaterialTheme.colorScheme.tertiary)
                        Text(
                            if (biometricAvailable) "در صورت پشتیبانی دستگاه" else "این دستگاه پشتیبانی نمی‌کند",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiary
                        )
                    }
                    Switch(
                        checked = biometricEnabled,
                        onCheckedChange = { biometricEnabled = it },
                        enabled = biometricAvailable
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { onSubmit(digitsOnlyPasscode, biometricEnabled && biometricAvailable) },
                    enabled = ready,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("تایید و فعال‌سازی")
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "با فعال‌سازی، هنگام بازگشت به برنامه نیاز به تایید هویت دارید.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Preview
@Composable
fun PasscodeSetupSheetPreview(){
    MaterialTheme(lightColorScheme()) {
        PasscodeSetupSheet(true,true,true,{}, {s,b-> })
    }
}