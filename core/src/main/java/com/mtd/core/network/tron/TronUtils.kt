package com.mtd.core.network.tron

import com.mtd.core.network.tron.TronUtils.Base58.bytesToHex
import com.mtd.core.network.tron.TronUtils.Base58.decodeBase58
import com.mtd.core.network.tron.TronUtils.Base58.hexDecode
import com.mtd.core.network.tron.TronUtils.Base58.verifyChecksum
import org.web3j.crypto.Hash
import org.web3j.crypto.Keys
import java.math.BigInteger
import java.security.MessageDigest
import java.util.Arrays


object TronUtils {

    /**
     * Converts a Public Key (BigInteger) to a TRON address string.
     * Steps:
     * 1. Get raw public key bytes (64 bytes).
     * 2. Keccak-256 hash.
     * 3. Take last 20 bytes.
     * 4. Prepend 0x41.
     * 5. Encode with Base58Check.
     */
    fun getAddressFromPublicKey(publicKey: BigInteger): String {
        // 1. Get raw public key bytes (64 bytes, uncompressed)
        // Web3j Keys.getAddress() logic does steps 1-3 for ETH. We can reuse part of it or re-implement.

        // This gives us the last 20 bytes of the Keccak hash of the public key
        val ethAddressBytes = hexDecode(Keys.getAddress(publicKey))

        // 4. Prepend 0x41 (65 decimal)
        val addressBytes = ByteArray(21)
        addressBytes[0] = 0x41.toByte()
        System.arraycopy(ethAddressBytes, 0, addressBytes, 1, 20)

        // 5. Base58Check Encode
        return encode58Check(addressBytes)
    }

    private fun encode58Check(input: ByteArray): String {
        val hash0 = Hash.sha256(input)
        val hash1 = Hash.sha256(hash0)

        // Append first 4 bytes of checksum
        val inputCheck = ByteArray(input.size + 4)
        System.arraycopy(input, 0, inputCheck, 0, input.size)
        System.arraycopy(hash1, 0, inputCheck, input.size, 4)

        return Base58.encode(inputCheck)
    }

    /**
     * تبدیل آدرس Base58 (T...) به هگز (41...)
     */
    fun toHex(base58Address: String): String {
        val decoded = decodeBase58(base58Address)
        if (!verifyChecksum(decoded)) {
            throw IllegalArgumentException("Invalid Tron Address: Checksum failed")
        }

        // جدا کردن بدنه اصلی (حذف ۴ بایت آخر Checksum)
        val body = decoded.copyOfRange(0, decoded.size - 4)
        return bytesToHex(body)
    }


    // Internal Base58 implementation or we can use BitcoinJ's if available
    // For standalone utility, a simple Base58 encoder is sufficient.
    object Base58 {
        private const val ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
        private val ENCODED_ZERO = ALPHABET[0]
        private val INDEXES = IntArray(128)

        init {
            Arrays.fill(INDEXES, -1)
            for (i in ALPHABET.indices) {
                INDEXES[ALPHABET[i].code] = i
            }
        }

        fun encode(input: ByteArray): String {
            if (input.isEmpty()) {
                return ""
            }
            var inputCopy = input.copyOf(input.size)
            // Count leading zeroes
            var zeroCount = 0
            while (zeroCount < inputCopy.size && inputCopy[zeroCount].toInt() == 0) {
                ++zeroCount
            }
            // The actual encoding
            val temp = ByteArray(inputCopy.size * 2)
            var j = temp.size
            var startAt = zeroCount
            while (startAt < inputCopy.size) {
                val mod = divmod58(inputCopy, startAt)
                if (inputCopy[startAt].toInt() == 0) {
                    ++startAt
                }
                temp[--j] = ALPHABET[mod.toInt()].toByte()
            }
            // Strip extra '1' if any
            while (j < temp.size && temp[j] == ENCODED_ZERO.code.toByte()) {
                ++j
            }
            // Add number of leading zeroes
            while (--zeroCount >= 0) {
                temp[--j] = ENCODED_ZERO.code.toByte()
            }
            return String(temp, j, temp.size - j)
        }

        private fun divmod58(number: ByteArray, startAt: Int): Byte {
            var remainder = 0
            for (i in startAt until number.size) {
                val digit256 = number[i].toInt() and 0xFF
                val temp = remainder * 256 + digit256
                number[i] = (temp / 58).toByte()
                remainder = temp % 58
            }
            return remainder.toByte()
        }


        internal fun hexDecode(hex: String): ByteArray {
            val clean = hex.removePrefix("0x")
            require(clean.length % 2 == 0) { "Hex string must have even length" }
            return ByteArray(clean.length / 2) { i ->
                clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
        }


        /**
         * تبدیل آدرس T به فرمت Hex (با پیشوند 41)
         * مثال: TGuDyq... -> 414a2b...
         */
        internal fun base58ToHex(base58: String): String {
            val decoded = decodeBase58(base58)
            if (!verifyChecksum(decoded)) {
                throw IllegalArgumentException("آدرس نامعتبر است (Checksum Error)")
            }
            // حذف ۴ بایت آخر (Checksum)
            val body = decoded.copyOfRange(0, decoded.size - 4)
            return bytesToHex(body)
        }

        /**
         * تبدیل آدرس Hex به فرمت قابل نمایش T
         * مثال: 414a2b... -> TGuDyq...
         */
         fun hexToBase58(hex: String): String {
            // ۱. تمیز کردن و اطمینان از وجود پیشوند 41 (Address Prefix)
            var cleanHex = if (hex.startsWith("0x")) hex.substring(2) else hex

            // در ترون آدرس Hex باید با 41 شروع شود. اگر ندارد، اضافه می‌کنیم.
            if (!cleanHex.startsWith("41")) {
                cleanHex = "41$cleanHex"
            }

            val body = hexToBytes(cleanHex)

            // ۲. محاسبه Checksum (دو بار SHA-256)
            val digest = MessageDigest.getInstance("SHA-256")
            val hash1 = digest.digest(body)
            val hash2 = digest.digest(hash1)
            val checksum = hash2.copyOfRange(0, 4)

            // ۳. چسباندن بدنه (41 + PubKeyHash) و چک‌سام
            val resultBytes = ByteArray(body.size + 4)
            System.arraycopy(body, 0, resultBytes, 0, body.size)
            System.arraycopy(checksum, 0, resultBytes, body.size, 4)

            // ۳. تبدیل به Base58
            return encodeBase58(resultBytes)
        }

        // --- توابع داخلی و کمکی ---

        internal fun decodeBase58(input: String): ByteArray {
            var res = BigInteger.ZERO
            for (c in input) {
                val index = ALPHABET.indexOf(c)
                if (index == -1) throw IllegalArgumentException("کاراکتر غیرمجاز")
                res = res.multiply(BigInteger.valueOf(58)).add(BigInteger.valueOf(index.toLong()))
            }
            val bytes = res.toByteArray()
            val stripSignByte = bytes.size > 1 && bytes[0] == 0.toByte() && bytes[1] < 0
            return if (stripSignByte) bytes.copyOfRange(1, bytes.size) else bytes
        }

        internal fun encodeBase58(input: ByteArray): String {
            if (input.isEmpty()) return ""

            var value = BigInteger(1, input)
            val sb = StringBuilder()

            // تبدیل عدد به مبنای ۵۸
            val fiftyEight = BigInteger.valueOf(58)
            while (value > BigInteger.ZERO) {
                val mod = value.divideAndRemainder(fiftyEight)
                sb.append(ALPHABET[mod[1].toInt()])
                value = mod[0]
            }

            // مدیریت بایت‌های صفر ابتدایی
            // در پروتکل‌های ارز دیجیتال، هر بایت 0x00 در ابتدا تبدیل به کاراکتر '1' می‌شود
            for (b in input) {
                if (b.toInt() == 0) {
                    sb.append(ALPHABET[0])
                } else {
                    break
                }
            }

            return sb.reverse().toString()
        }

        internal fun verifyChecksum(data: ByteArray): Boolean {
            if (data.size < 4) return false
            val body = data.copyOfRange(0, data.size - 4)
            val checksum = data.copyOfRange(data.size - 4, data.size)
            val digest = MessageDigest.getInstance("SHA-256")
            val hash1 = digest.digest(body)
            val hash2 = digest.digest(hash1)
            return hash2[0] == checksum[0] && hash2[1] == checksum[1] &&
                    hash2[2] == checksum[2] && hash2[3] == checksum[3]
        }

        internal fun bytesToHex(bytes: ByteArray): String {
            val hexArray = "0123456789abcdef".toCharArray()
            val hexChars = CharArray(bytes.size * 2)
            for (i in bytes.indices) {
                val v = bytes[i].toInt() and 0xFF
                hexChars[i * 2] = hexArray[v ushr 4]
                hexChars[i * 2 + 1] = hexArray[v and 0x0F]
            }
            return String(hexChars)
        }

        internal fun hexToBytes(hex: String): ByteArray {
            val len = hex.length
            val data = ByteArray(len / 2)
            var i = 0
            while (i < len) {
                data[i / 2] =
                    ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
                i += 2
            }
            return data
        }

         fun decodeTronAddressToEthFormat(base58Address: String): String {
            return try {
                // استفاده از کلاس جدید برای تبدیل به هگز
                val hex = base58ToHex(base58Address)

                // در ترون هگز با 41 شروع می‌شود، برای فرمت اتریوم 41 را حذف و 0x می‌گذاریم
                if (hex.startsWith("41")) {
                    "0x" + hex.substring(2)
                } else {
                    "0x$hex"
                }
            } catch (e: Exception) {
                ""
            }
        }
    }
}

