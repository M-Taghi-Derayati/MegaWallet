package com.mtd.megawallet.viewmodel



import android.content.Intent
import com.mtd.data.datasource.ICloudDataSource
import com.mtd.data.repository.IBackupRepository
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.repository.IAuthManager
import com.mtd.megawallet.core.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backupRepository: IBackupRepository,
     var cloudDataSource: ICloudDataSource,
    private val authManager: IAuthManager,
    errorManager: com.mtd.core.manager.ErrorManager
) : BaseViewModel(errorManager) {

    private val _uiState = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
    val uiState = _uiState.asStateFlow()



    init {
        // در ابتدای کار، بررسی می‌کنیم که آیا کاربر قبلاً وارد شده است یا خیر.
        if (cloudDataSource.isInitialized()) {
            _uiState.value = BackupUiState.SignedIn("با موفقیت وارد شدید.")
        }
    }

    fun getSignInIntent(): Intent {
        return authManager.getSignInIntent()
    }

   /* fun startSignInFlow(activity: Activity) {
        viewModelScope.launch {
            _uiState.value = BackupUiState.Loading("در حال اتصال به حساب گوگل...")
            val credentialManager = CredentialManager.create(activity)

            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(webClientId)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            try {
                val result = credentialManager.getCredential(activity, request)
                val credential = result.credential

                if (credential is GoogleIdTokenCredential) {
                    val authCode =
                        credential.data.getString("com.google.android.libraries.identity.googleid.BUNDLE_KEY_SERVER_AUTH_CODE")
                    if (authCode != null) {
                        cloudDataSource.initializeWithAuthCode(authCode)
                        _uiState.value = BackupUiState.SignedIn("اتصال با موفقیت برقرار شد.")
                    } else {
                        _uiState.value =
                            BackupUiState.Error("خطا: کد احراز هویت از گوگل دریافت نشد.")
                    }
                }
            } catch (e: GetCredentialException) {
                _uiState.value = BackupUiState.Error("عملیات ورود لغو شد یا با خطا مواجه شد.")
            }
        }
    }*/

    fun performBackup(mnemonic: String, password: String) {
        if (!cloudDataSource.isInitialized()) {
            _uiState.value = BackupUiState.Error("لطفاً ابتدا به حساب گوگل خود وارد شوید.")
            return
        }
        launchSafe {
            _uiState.value = BackupUiState.Loading("در حال آپلود پشتیبان...")
           /* when (val result = backupRepository.backupMnemonic(mnemonic, password)) {
                is ResultResponse.Success -> _uiState.value =
                    BackupUiState.OperationSuccess("پشتیبان‌گیری با موفقیت انجام شد.")

                is ResultResponse.Error -> _uiState.value =
                    BackupUiState.Error("خطا در پشتیبان‌گیری: ${result.exception.message}")
            }*/
        }
    }

    fun performRestore(password: String) {
        if (!cloudDataSource.isInitialized()) {
            _uiState.value = BackupUiState.Error("لطفاً ابتدا به حساب گوگل خود وارد شوید.")
            return
        }
        launchSafe {
            _uiState.value = BackupUiState.Loading("در حال بازیابی اطلاعات...")
            /*when (val result = backupRepository.restoreMnemonic(password)) {
                is ResultResponse.Success -> _uiState.value =
                    BackupUiState.OperationSuccess("بازیابی موفق! Mnemonic: ${result.data}")

                is ResultResponse.Error -> _uiState.value =
                    BackupUiState.Error("خطا در بازیابی. آیا پسورد صحیح است؟")
            }*/
        }
    }

    fun checkBackupStatus() {
        if (!cloudDataSource.isInitialized()) {
            _uiState.value = BackupUiState.Error("لطفاً ابتدا به حساب گوگل خود وارد شوید.")
            return
        }
        launchSafe {
            _uiState.value = BackupUiState.Loading("در حال بررسی وضعیت پشتیبان...")
            val hasBackup = backupRepository.hasCloudBackup()
            val message =
                if (hasBackup) "فایل پشتیبان در گوگل درایو پیدا شد." else "هیچ فایل پشتیبانی پیدا نشد."
            _uiState.value = BackupUiState.OperationSuccess(message)
        }
    }

    fun handleSignInResult(data: Intent?) {
        launchSafe {
            _uiState.value = BackupUiState.Loading("در حال پردازش اطلاعات...")
            when (val result = authManager.processSignInResult(data)) {
                is ResultResponse.Success -> {
                    // AuthCode را به DataSource می‌دهیم
                    cloudDataSource.initializeWithAuthCode(result.data)
                    _uiState.value = BackupUiState.SignedIn("اتصال با موفقیت برقرار شد.")
                }
                is ResultResponse.Error -> {
                    _uiState.value = BackupUiState.Error("خطا در ورود: ${result.exception.message}")
                }
            }
        }
    }
}
// کلاس State برای مدیریت تمام حالت‌های UI
sealed class BackupUiState {
    object Idle : BackupUiState()
    data class Loading(val message: String) : BackupUiState()
    data class SignedIn(val message: String) : BackupUiState()
    data class Error(val message: String) : BackupUiState()
    data class OperationSuccess(val message: String) : BackupUiState()
}
