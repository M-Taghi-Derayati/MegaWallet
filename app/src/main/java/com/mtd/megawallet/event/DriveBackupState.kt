package com.mtd.megawallet.event

sealed class DriveBackupState {
        object Checking : DriveBackupState()
        object NotConnected : DriveBackupState()
        object BackupFound : DriveBackupState()
        object NoBackup : DriveBackupState()
        data class Error(val message: String) : DriveBackupState()
    }