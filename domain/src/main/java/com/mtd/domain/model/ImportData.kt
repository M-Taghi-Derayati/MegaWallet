package com.mtd.domain.model

sealed class ImportData {
    data class Mnemonic(val words: List<String>) : ImportData()
    data class PrivateKey(val key: String) : ImportData()
}