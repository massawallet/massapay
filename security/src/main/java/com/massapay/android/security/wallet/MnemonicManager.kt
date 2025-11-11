package com.massapay.android.security.wallet

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlin.experimental.and
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lightweight local mnemonic manager used when the external BIP39 library is not available.
 * This provides:
 *  - generateMnemonic: produces a placeholder mnemonic (random hex-like words) of requested word count
 *  - validateMnemonic: checks word count and non-empty words
 *  - generateSeedFromMnemonic: derives a 64-byte seed using PBKDF2WithHmacSHA512 (compatible with BIP39 derivation)
 *
 * Note: the generated mnemonic words are not from the BIP39 wordlist. The seed derivation follows the
 * PBKDF2 parameters used by BIP39 so downstream code that uses the resulting seed can continue to work.
 */
@Singleton
class MnemonicManager @Inject constructor() {

    private val random = SecureRandom()

    fun generateMnemonic(wordCount: Int = 24): String {
        val wc = when (wordCount) {
            12 -> 12
            24 -> 24
            else -> 24
        }

        // BIP-39: ENT bits -> CS = ENT/32 bits checksum; (ENT+CS)/11 = word count
        val entBits = when (wc) {
            12 -> 128
            24 -> 256
            else -> 256
        }

        val entBytes = entBits / 8
        val entropy = ByteArray(entBytes)
        random.nextBytes(entropy)

        val sha256 = MessageDigest.getInstance("SHA-256").digest(entropy)
        val checksumBits = entBits / 32

        // Build bitstring of entropy + checksum
        val bitBuffer = StringBuilder()
        for (b in entropy) {
            bitBuffer.append(String.format("%8s", Integer.toBinaryString(b.toInt() and 0xFF)).replace(' ', '0'))
        }
        val shaByte = sha256[0]
        val shaBits = String.format("%8s", Integer.toBinaryString(shaByte.toInt() and 0xFF)).replace(' ', '0')
        bitBuffer.append(shaBits.substring(0, checksumBits))

        // Split into 11-bit words
        val bits = bitBuffer.toString()
        val words = mutableListOf<String>()
        var idx = 0
        while (idx < bits.length) {
            val end = (idx + 11).coerceAtMost(bits.length)
            val chunk = bits.substring(idx, end)
            val value = Integer.parseInt(chunk, 2)
            // map to English wordlist (size 2048)
            val word = ENGLISH_WORDLIST.getOrElse(value) { "" }
            words.add(word)
            idx += 11
        }

        return words.joinToString(" ")
    }

    fun validateMnemonic(mnemonic: String): Boolean {
        val words = mnemonic.trim().split(Regex("\\s+"))
            .filter { it.isNotBlank() }
        if (!(words.size == 12 || words.size == 24)) return false
        // Check that all words are in the English wordlist
        return words.all { ENGLISH_WORDLIST.contains(it) }
    }

    /**
     * Derive a 64-byte seed from the mnemonic and passphrase using PBKDF2WithHmacSHA512,
     * following Bearby specification: iterations=2048 (Bearby standard), keylen=64 bytes, salt = "mnemonic" + passphrase
     */
    fun generateSeedFromMnemonic(
        mnemonic: String,
        passphrase: String = "",
        iterationsOverride: Int? = null
    ): ByteArray {
        val iterations = iterationsOverride ?: 2048  // Bearby standard: 2048 iterations (NOT 16384)
        val keyLenBits = 64 * 8
        val salt = ("mnemonic" + passphrase).toByteArray(Charsets.UTF_8)
        val spec = PBEKeySpec(mnemonic.toCharArray(), salt, iterations, keyLenBits)
        val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
        val seed = skf.generateSecret(spec).encoded
        android.util.Log.d("MnemonicManager", "PBKDF2 iterations=$iterations seedFirst16=${seed.take(16).joinToString("") { "%02x".format(it) }}")
        return seed
    }
}