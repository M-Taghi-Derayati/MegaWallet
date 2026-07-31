package com.mtd.data.datasource

import com.mtd.core.network.BlockchainNetwork
import com.mtd.core.utils.hexToBytes
import com.mtd.core.utils.toHexString
import com.mtd.domain.model.UtxoInput
import com.mtd.domain.model.core.NetworkName
import org.bitcoinj.base.Address
import org.bitcoinj.base.Coin
import org.bitcoinj.base.ScriptType
import org.bitcoinj.base.Sha256Hash
import org.bitcoinj.core.NetworkParameters
import org.bitcoinj.core.Transaction
import org.bitcoinj.core.TransactionInput
import org.bitcoinj.core.TransactionWitness
import org.bitcoinj.crypto.ECKey
import org.bitcoinj.crypto.TransactionSignature
import org.bitcoinj.script.Script.parse
import org.bitcoinj.script.ScriptBuilder
import org.bitcoinj.script.ScriptBuilder.createOutputScript

/**
 * Production [UtxoTxBuilder] — coin selection + build + sign with bitcoinj, mirroring the DIRECT
 * `BitcoinDataSource` send path exactly (smallest-first selection, recipient + change outputs, dust
 * folding into fee, P2WPKH witness vs P2PKH script-sig signing). DIRECT is left untouched; this is a
 * self-contained PROXY-only copy so the two transports cannot regress each other.
 *
 * [networkParameters] is resolved per-network by the data-source factory (same resolver the DIRECT
 * UTXO source uses), so this builder is network-correct for BTC / LTC / DOGE main + testnets.
 */
class BitcoinjUtxoTxBuilder(
    private val network: BlockchainNetwork,
    private val networkParameters: NetworkParameters
) : UtxoTxBuilder {

    override fun deriveAddress(privateKeyHex: String): String {
        val key = ECKey.fromPrivate(privateKeyHex.hexToBytes())
        return key.toAddress(resolveInputScriptType(), networkParameters.network()).toString()
    }

    override fun buildSignedTx(
        privateKeyHex: String,
        recipient: String,
        amountSat: Long,
        feeRateSatPerVByte: Long,
        utxos: List<UtxoInput>
    ): String {
        val key = ECKey.fromPrivate(privateKeyHex.hexToBytes())
        val scriptType = resolveInputScriptType()
        val fromAddress = key.toAddress(scriptType, networkParameters.network())

        if (utxos.isEmpty()) throw IllegalStateException("No spendable UTXOs available to spend.")

        // Smallest-first accumulation until the amount is covered (matches the DIRECT source).
        val selected = mutableListOf<UtxoInput>()
        var totalInputValue = 0L
        for (utxo in utxos.sortedBy { it.valueSat }) {
            totalInputValue += utxo.valueSat
            selected.add(utxo)
            if (totalInputValue >= amountSat) break
        }
        if (totalInputValue < amountSat) {
            throw IllegalStateException("Insufficient funds to cover amount.")
        }

        val fee = estimateTxFeeBasedOnRate(selected.size, outputs = 2, feeRate = feeRateSatPerVByte, scriptType = scriptType)
        val requiredTotal = amountSat + fee
        if (totalInputValue < requiredTotal) {
            throw IllegalStateException(
                "Insufficient funds to cover transaction fee. Required: $requiredTotal, Available: $totalInputValue"
            )
        }

        val tx = Transaction(networkParameters)
        selected.forEach { utxo ->
            tx.addInput(Sha256Hash.wrap(utxo.txid), utxo.vout.toLong(), parse(ByteArray(0)))
        }

        tx.addOutput(Coin.valueOf(amountSat), Address.fromString(networkParameters, recipient))

        val changeAmount = totalInputValue - amountSat - fee
        if (changeAmount >= dustThreshold()) {
            tx.addOutput(Coin.valueOf(changeAmount), fromAddress)
        }
        // Sub-dust change is folded into the miner fee (no change output).

        val signedInputs = when (scriptType) {
            ScriptType.P2WPKH -> signSegwitInputs(tx, selected, key)
            ScriptType.P2PKH -> signLegacyInputs(tx, key, fromAddress)
            else -> throw IllegalStateException("Unsupported UTXO script type: $scriptType")
        }

        tx.clearInputs()
        signedInputs.forEach(tx::addInput)

        return tx.serialize().toHexString()
    }

    private fun resolveInputScriptType(): ScriptType = when (network.name) {
        NetworkName.DOGE, NetworkName.DOGETESTNET -> ScriptType.P2PKH
        else -> ScriptType.P2WPKH
    }

    private fun dustThreshold(): Long = when (network.name) {
        NetworkName.DOGE, NetworkName.DOGETESTNET -> 1_000_000L // 0.01 DOGE
        else -> 546L
    }

    private fun estimateTxFeeBasedOnRate(
        inputs: Int,
        outputs: Int,
        feeRate: Long,
        scriptType: ScriptType
    ): Long {
        val txVBytes = when (scriptType) {
            ScriptType.P2PKH -> (inputs * 148) + (outputs * 34) + 10
            ScriptType.P2WPKH -> (inputs * 68) + (outputs * 31) + 11
            else -> (inputs * 148) + (outputs * 34) + 10
        }
        val calculatedFee = txVBytes * feeRate
        val minRelayFee = when (network.name) {
            NetworkName.DOGE, NetworkName.DOGETESTNET -> 1_000_000L
            else -> 0L
        }
        return if (minRelayFee > 0L && calculatedFee < minRelayFee) minRelayFee else calculatedFee
    }

    private fun signSegwitInputs(
        tx: Transaction,
        inputs: List<UtxoInput>,
        key: ECKey
    ): List<TransactionInput> {
        val signedInputs = mutableListOf<TransactionInput>()
        for (i in inputs.indices) {
            val utxoValue = Coin.valueOf(inputs[i].valueSat)
            val scriptCode = ScriptBuilder.createP2PKHOutputScript(key).program
            val sighash = tx.hashForWitnessSignature(
                i,
                scriptCode,
                utxoValue,
                Transaction.SigHash.ALL,
                false
            )
            val signature = key.sign(sighash)
            val txSignature = TransactionSignature(signature, Transaction.SigHash.ALL, false)
            val witness = TransactionWitness.redeemP2WPKH(txSignature, key)
            signedInputs.add(tx.getInput(i.toLong()).withWitness(witness))
        }
        return signedInputs
    }

    private fun signLegacyInputs(
        tx: Transaction,
        key: ECKey,
        fromAddress: Address
    ): List<TransactionInput> {
        val signedInputs = mutableListOf<TransactionInput>()
        val connectedScript = createOutputScript(fromAddress)
        for (i in 0 until tx.inputs.size) {
            val txSignature = tx.calculateSignature(
                i,
                key,
                connectedScript,
                Transaction.SigHash.ALL,
                false
            )
            val scriptSig = ScriptBuilder.createInputScript(txSignature, key)
            signedInputs.add(tx.getInput(i.toLong()).withScriptSig(scriptSig))
        }
        return signedInputs
    }

}
