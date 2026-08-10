package com.mtd.megawallet.ui.compose.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtd.domain.model.BlockchainConnectionMode
import com.mtd.megawallet.viewmodel.SettingsViewModel

private enum class SettingsDialog { ADDRESS_BOOK, PASSKEY, SIGNING, CONNECTION, THEME, NOTIFICATIONS, ABOUT, SUPPORT }
private data class SettingsItem(val title: String, val subtitle: String, val icon: ImageVector, val dialog: SettingsDialog)

@Composable
fun SettingsScreen(onSecurityClick: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier, viewModel: SettingsViewModel = hiltViewModel()) {
    val connectionMode by viewModel.connectionMode.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<SettingsDialog?>(null) }
    val items = listOf(
        SettingsItem("امنیت", "اثر انگشت، passcode و قفل برنامه", Icons.Default.Fingerprint, SettingsDialog.SIGNING),
        SettingsItem("امضای امن", "امضا با اثر انگشت یا passkey", Icons.Default.Fingerprint, SettingsDialog.SIGNING),
        SettingsItem("Passkey", "مدیریت passkey برای احراز هویت", Icons.Default.Link, SettingsDialog.PASSKEY),
        SettingsItem("Address Book", "مدیریت مخاطب‌ها و آدرس‌ها", Icons.Default.Book, SettingsDialog.ADDRESS_BOOK),
        SettingsItem("Connection", if (connectionMode == BlockchainConnectionMode.PROXY) "Proxy" else "Direct", Icons.Default.SwapHoriz, SettingsDialog.CONNECTION),
        SettingsItem("Theme", "System، روشن یا تاریک", Icons.Default.Palette, SettingsDialog.THEME),
        SettingsItem("Notifications", "مدیریت اعلان‌های کیف پول", Icons.Default.Notifications, SettingsDialog.NOTIFICATIONS),
        SettingsItem("About / Developer", "نسخه برنامه و اطلاعات فنی", Icons.Default.Info, SettingsDialog.ABOUT),
        SettingsItem("Help & Support", "راهنما و پشتیبانی", Icons.Default.HelpOutline, SettingsDialog.SUPPORT)
    )
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Default.ArrowBack, "بستن تنظیمات") }
                Text("تنظیمات", Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(48.dp))
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                item { Header("امنیت") }
                item { RowItem(items[0]) { onSecurityClick() } }
                item { RowItem(items[1]) { dialog = it.dialog } }
                item { RowItem(items[2]) { dialog = it.dialog } }
                item { Header("کیف پول") }
                item { RowItem(items[3]) { dialog = it.dialog } }
                item { Header("اتصال") }
                item { RowItem(items[4]) { dialog = it.dialog } }
                item { Header("تنظیمات برنامه") }
                item { RowItem(items[5]) { dialog = it.dialog } }
                item { RowItem(items[6]) { dialog = it.dialog } }
                item { Header("اطلاعات") }
                item { RowItem(items[7]) { dialog = it.dialog } }
                item { RowItem(items[8]) { dialog = it.dialog } }
            }
        }
    }
    when (dialog) {
        SettingsDialog.CONNECTION -> ConnectionDialog(connectionMode, { viewModel.setConnectionMode(it); dialog = null }) { dialog = null }
        SettingsDialog.ADDRESS_BOOK -> PlaceholderDialog("Address Book", "بخش Address Book اضافه شد. اتصال زیرساخت ذخیره و مدیریت آدرس‌ها در مرحله بعد انجام می‌شود.") { dialog = null }
        SettingsDialog.PASSKEY -> PlaceholderDialog("Passkey", "زیرساخت passkey هنوز متصل نشده است. این بخش برای فعال‌سازی بعدی آماده است.") { dialog = null }
        SettingsDialog.SIGNING -> PlaceholderDialog("امضای امن", "امضای transaction با biometric/passkey بعد از تکمیل auth provider فعال می‌شود.") { dialog = null }
        SettingsDialog.THEME -> PlaceholderDialog("Theme", "انتخاب System، روشن یا تاریک در مرحله اتصال preferenceهای theme فعال می‌شود.") { dialog = null }
        SettingsDialog.NOTIFICATIONS -> PlaceholderDialog("Notifications", "Permission و FCM در پروژه موجود است. کنترل‌های جزئی اعلان‌ها بعداً متصل می‌شوند.") { dialog = null }
        SettingsDialog.ABOUT -> PlaceholderDialog("About / Developer", "نسخه، endpoint و diagnostics توسعه‌دهنده در مرحله بعد متصل می‌شوند.") { dialog = null }
        SettingsDialog.SUPPORT -> PlaceholderDialog("Help & Support", "راهنما و مسیر پشتیبانی در این بخش قرار می‌گیرد.") { dialog = null }
        null -> Unit
    }
}

@Composable private fun Header(text: String) { Text(text, Modifier.padding(start = 24.dp, top = 22.dp, bottom = 8.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold) }
@Composable private fun RowItem(item: SettingsItem, onClick: (SettingsItem) -> Unit) { Column { Row(Modifier.fillMaxWidth().clickable { onClick(item) }.padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) { Icon(item.icon, null, tint = MaterialTheme.colorScheme.primary); Column(Modifier.weight(1f)) { Text(item.title, style = MaterialTheme.typography.titleMedium); Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Text("›", style = MaterialTheme.typography.headlineSmall) }; HorizontalDivider(Modifier.padding(start = 24.dp)) } }
@Composable private fun ConnectionDialog(selected: BlockchainConnectionMode, onSelected: (BlockchainConnectionMode) -> Unit, onDismiss: () -> Unit) { AlertDialog(onDismissRequest = onDismiss, title = { Text("Connection") }, text = { Column { BlockchainConnectionMode.entries.forEach { mode -> Row(Modifier.fillMaxWidth().clickable { onSelected(mode) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { RadioButton(selected == mode, { onSelected(mode) }); Text(if (mode == BlockchainConnectionMode.PROXY) "Proxy" else "Direct") } } } }, confirmButton = { TextButton(onDismiss) { Text("بستن") } }) }
@Composable private fun PlaceholderDialog(title: String, message: String, onDismiss: () -> Unit) { AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(message) }, confirmButton = { TextButton(onDismiss) { Text("متوجه شدم") } }) }
