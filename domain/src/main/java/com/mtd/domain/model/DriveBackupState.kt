package com.mtd.domain.model

sealed class DriveBackupState {
        object Checking : DriveBackupState()
        object NotConnected : DriveBackupState()
        object BackupFound : DriveBackupState()
        object NoBackup : DriveBackupState()
        data class Error(val message: String) : DriveBackupState()
    }