package com.mtd.megawallet.event

sealed class ImportData {
    data class Mnemonic(val words: List<String>) : ImportData()
    data class PrivateKey(val key: String) : ImportData()
}