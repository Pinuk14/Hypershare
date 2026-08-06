package com.hypershare.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import javax.crypto.AEADBadTagException

class SessionEncryptorTest {

    private val testSharedSecret = ByteArray(32) { (it + 1).toByte() }

    @Test
    fun encryptAndDecrypt_returnsOriginalPlaintext() {
        val encryptor = SessionEncryptor(testSharedSecret)
        val plaintext = "HyperShare encrypted disaster mesh message payload test".toByteArray(Charsets.UTF_8)

        val ciphertext = encryptor.encrypt(plaintext)
        assertNotNull(ciphertext)
        assertFalse(plaintext.contentEquals(ciphertext))

        val decrypted = encryptor.decrypt(ciphertext)
        assertArrayEquals("Decrypted payload must match original plaintext", plaintext, decrypted)
    }

    @Test
    fun encrypt_generatesUniqueIVForSubsequentCalls() {
        val encryptor = SessionEncryptor(testSharedSecret)
        val plaintext = "Same plaintext payload".toByteArray(Charsets.UTF_8)

        val ciphertext1 = encryptor.encrypt(plaintext)
        val ciphertext2 = encryptor.encrypt(plaintext)

        val iv1 = ciphertext1.copyOfRange(0, 12)
        val iv2 = ciphertext2.copyOfRange(0, 12)

        assertFalse("Unique random IV must be generated for each packet", iv1.contentEquals(iv2))
    }

    @Test(expected = Exception::class)
    fun decrypt_withTamperedPayload_throwsException() {
        val encryptor = SessionEncryptor(testSharedSecret)
        val plaintext = "Tamper check text".toByteArray(Charsets.UTF_8)

        val ciphertext = encryptor.encrypt(plaintext)
        // Corrupt a byte in the payload
        ciphertext[ciphertext.size - 1] = (ciphertext[ciphertext.size - 1].toInt() xor 0xFF).toByte()

        encryptor.decrypt(ciphertext)
    }
}
