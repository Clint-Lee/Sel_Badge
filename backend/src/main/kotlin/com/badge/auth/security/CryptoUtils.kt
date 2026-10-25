package com.badge.auth.security

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoUtils {
    private val log = LoggerFactory.getLogger(CryptoUtils::class.java)

    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val GCM_IV_LENGTH = 12 // 96-bit IV
    private const val GCM_TAG_LENGTH = 128 // 128-bit authentication tag
    private val SECURE_RANDOM = SecureRandom()

    private var masterKeyBytes: ByteArray

    init {
        val digest = MessageDigest.getInstance("SHA-256")
        masterKeyBytes = digest.digest("BadgeAuthPlatformSecureMasterKeyAES256@2026!!".toByteArray(StandardCharsets.UTF_8))
    }

    fun initKey(secretPhrase: String) {
        val digest = MessageDigest.getInstance("SHA-256")
        masterKeyBytes = digest.digest(secretPhrase.toByteArray(StandardCharsets.UTF_8))
    }

    /**
     * Encrypt plaintext string into Base64 ciphertext with AES-256-GCM.
     */
    fun encrypt(plaintext: String?): String? {
        if (plaintext.isNullOrEmpty()) return plaintext
        return try {
            val iv = ByteArray(GCM_IV_LENGTH)
            SECURE_RANDOM.nextBytes(iv)

            val key: SecretKey = SecretKeySpec(masterKeyBytes, "AES")
            val cipher = Cipher.getInstance(ALGORITHM)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.ENCRYPT_MODE, key, spec)

            val cipherText = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))
            val combined = ByteArray(iv.size + cipherText.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)

            Base64.getEncoder().encodeToString(combined)
        } catch (e: Exception) {
            log.error("AES-GCM encryption error: {}", e.message)
            throw RuntimeException("Encryption failed", e)
        }
    }

    /**
     * Decrypt Base64 ciphertext into original plaintext.
     */
    fun decrypt(encryptedBase64: String?): String? {
        if (encryptedBase64.isNullOrEmpty()) return encryptedBase64
        return try {
            val combined = Base64.getDecoder().decode(encryptedBase64)
            if (combined.size <= GCM_IV_LENGTH) return encryptedBase64

            val iv = ByteArray(GCM_IV_LENGTH)
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH)

            val cipherLength = combined.size - GCM_IV_LENGTH
            val cipherText = ByteArray(cipherLength)
            System.arraycopy(combined, GCM_IV_LENGTH, cipherText, 0, cipherLength)

            val key: SecretKey = SecretKeySpec(masterKeyBytes, "AES")
            val cipher = Cipher.getInstance(ALGORITHM)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, spec)

            val plainText = cipher.doFinal(cipherText)
            String(plainText, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            log.warn("AES-GCM decryption failed, returning raw string: {}", e.message)
            encryptedBase64
        }
    }

    fun generateRandomToken(byteLength: Int = 32): String {
        val bytes = ByteArray(byteLength)
        SECURE_RANDOM.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun maskPhone(phone: String?): String? {
        if (phone == null || phone.length < 7) return phone
        return phone.substring(0, 3) + "****" + phone.substring(phone.length - 4)
    }

    fun maskEmail(email: String?): String? {
        if (email == null || !email.contains("@")) return email
        val atIndex = email.indexOf('@')
        if (atIndex <= 2) return email
        return email[0] + "***" + email[atIndex - 1] + email.substring(atIndex)
    }
}

@Converter
class AesGcmCryptoConverter : AttributeConverter<String, String> {
    override fun convertToDatabaseColumn(attribute: String?): String? {
        return CryptoUtils.encrypt(attribute)
    }

    override fun convertToEntityAttribute(dbData: String?): String? {
        return CryptoUtils.decrypt(dbData)
    }
}
