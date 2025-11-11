package com.massapay.android

import org.junit.Test
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.digests.Blake3Digest
import java.security.MessageDigest
import java.math.BigInteger

/**
 * Test diagnóstico para comparar generación de dirección con Bearby/Massa Station
 * 
 * Caso de prueba:
 * S1: S1erANgziQrtH6KWhQEXzyLhnQeo5HVsWhv5Qj9hNtLxBGn7PSV
 * Dirección esperada (Bearby/Station): AU12HZrAy4GVZfiPfY4VMLRucGP8rj2J4rKU9CykjNzzovRrSrsoU
 * Dirección actual (App): AU1u9AHzuNk7pmv55ctdd2i28uyLU2M7jHQJrd3DWWPi1N1B8v9y
 */
class DiagnosticAddressTest {
    
    @Test
    fun testAddressGeneration() {
        val s1Key = "S1erANgziQrtH6KWhQEXzyLhnQeo5HVsWhv5Qj9hNtLxBGn7PSV"
        
        println("=== DIAGNOSTIC: Address Generation ===")
        println("S1 Private Key: $s1Key")
        
        // 1. Decode S1 to raw private key
        val rawPriv = decodeS1PrivateKey(s1Key)
        println("1. Raw Private Key (32 bytes): ${rawPriv.joinToString("") { "%02x".format(it) }}")
        
        // 2. Derive public key from private key
        val privateKeyParams = Ed25519PrivateKeyParameters(rawPriv, 0)
        val publicKeyParams = privateKeyParams.generatePublicKey()
        val rawPub = publicKeyParams.encoded
        println("2. Raw Public Key (32 bytes): ${rawPub.joinToString("") { "%02x".format(it) }}")
        
        // 3. Hash public key with BLAKE3
        val blake3Digest = Blake3Digest()
        blake3Digest.update(rawPub, 0, rawPub.size)
        val pubKeyHash = ByteArray(32)
        blake3Digest.doFinal(pubKeyHash, 0)
        println("3. BLAKE3(PubKey) (32 bytes): ${pubKeyHash.joinToString("") { "%02x".format(it) }}")
        
        // 4. Create payload with Varint version
        val version = 0L
        val versionVarint = encodeVarint(version)
        println("4. Version Varint: ${versionVarint.joinToString("") { "%02x".format(it) }}")
        
        val payload = ByteArray(versionVarint.size + 32)
        System.arraycopy(versionVarint, 0, payload, 0, versionVarint.size)
        System.arraycopy(pubKeyHash, 0, payload, versionVarint.size, 32)
        println("5. Payload (version + hash): ${payload.joinToString("") { "%02x".format(it) }}")
        
        // 5. Calculate checksum (double SHA-256)
        val sha256 = MessageDigest.getInstance("SHA-256")
        val hash1 = sha256.digest(payload)
        val hash2 = sha256.digest(hash1)
        val checksum = hash2.copyOfRange(0, 4)
        println("6. Checksum (4 bytes): ${checksum.joinToString("") { "%02x".format(it) }}")
        
        // 6. Combine payload + checksum
        val addressBytes = ByteArray(payload.size + 4)
        System.arraycopy(payload, 0, addressBytes, 0, payload.size)
        System.arraycopy(checksum, 0, addressBytes, payload.size, 4)
        println("7. Address Bytes (payload + checksum): ${addressBytes.joinToString("") { "%02x".format(it) }}")
        
        // 7. Encode to Base58
        val base58 = encodeBase58(addressBytes)
        val address = "AU$base58"
        println("8. Final Address: $address")
        
        println("\n=== COMPARISON ===")
        println("Expected (Bearby/Station): AU12HZrAy4GVZfiPfY4VMLRucGP8rj2J4rKU9CykjNzzovRrSrsoU")
        println("Generated (This test):     $address")
        println("Match: ${address == "AU12HZrAy4GVZfiPfY4VMLRucGP8rj2J4rKU9CykjNzzovRrSrsoU"}")
    }
    
    private fun decodeS1PrivateKey(s1: String): ByteArray {
        require(s1.startsWith("S")) { "S1 key must start with 'S'" }
        val base58Part = s1.substring(1)
        val decoded = decodeBase58(base58Part)
        require(decoded.size == 37) { "Decoded S1 length unexpected (expected 37 got ${decoded.size})" }
        val version = decoded[0]
        require(version.toInt() == 0) { "Unsupported version byte $version" }
        return decoded.copyOfRange(1, 33)
    }
    
    private fun encodeVarint(value: Long): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        var v = value
        while (v >= 0x80) {
            output.write(((v and 0x7F) or 0x80).toInt())
            v = v ushr 7
        }
        output.write(v.toInt())
        return output.toByteArray()
    }
    
    private fun encodeBase58(input: ByteArray): String {
        val alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
        var num = BigInteger(1, input)
        val base = BigInteger.valueOf(58)
        val result = StringBuilder()
        
        while (num > BigInteger.ZERO) {
            val remainder = num.mod(base).toInt()
            result.insert(0, alphabet[remainder])
            num = num.divide(base)
        }
        
        for (b in input) {
            if (b.toInt() == 0) result.insert(0, '1')
            else break
        }
        
        return result.toString()
    }
    
    private fun decodeBase58(base58: String): ByteArray {
        val alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
        var num = BigInteger.ZERO
        val base = BigInteger.valueOf(58)
        
        for (char in base58) {
            val digit = alphabet.indexOf(char)
            require(digit >= 0) { "Invalid Base58 char $char" }
            num = num.multiply(base).add(BigInteger.valueOf(digit.toLong()))
        }
        
        var bytes = num.toByteArray()
        if (bytes.isNotEmpty() && bytes[0] == 0.toByte()) {
            bytes = bytes.copyOfRange(1, bytes.size)
        }
        
        val leadingOnes = base58.takeWhile { it == '1' }.length
        if (leadingOnes > 0) {
            bytes = ByteArray(leadingOnes) + bytes
        }
        
        return bytes
    }
}
