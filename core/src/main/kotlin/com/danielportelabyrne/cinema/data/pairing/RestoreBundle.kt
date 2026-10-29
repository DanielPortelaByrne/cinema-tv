package com.danielportelabyrne.cinema.data.pairing

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** The file could not be opened: the PIN is wrong, or the file was changed or is not a restore file. */
class RestoreException(message: String) : Exception(message)

/**
 * The encrypted restore file: the settings and sign-in tokens, sealed with a PIN the owner chooses on the phone.
 *
 * Layout: a version byte, the PBKDF2 iteration count (4 bytes, big-endian), a 16-byte salt, a 12-byte nonce, then the
 * AES-256-GCM ciphertext with its tag. The header is bound to the ciphertext as associated data, so changing any of it
 * makes [open] fail the same way a wrong PIN does. The key comes from PBKDF2-HMAC-SHA256 over the PIN.
 *
 * A PIN is only as strong as the owner makes it. Six digits can be guessed offline by anyone who has the file, so the
 * PIN must be at least [MIN_PIN_LENGTH] characters and the file should be kept private.
 */
object RestoreBundle {
    const val VERSION: Byte = 1
    const val MIN_PIN_LENGTH = 6
    const val ITERATIONS = 210_000
    private const val SALT_BYTES = 16
    private const val NONCE_BYTES = 12
    private const val KEY_BITS = 256
    private const val TAG_BITS = 128
    private const val HEADER_BYTES = 1 + 4 + SALT_BYTES + NONCE_BYTES

    /** Seals [plaintext] (usually JSON) with [pin]. Throws [IllegalArgumentException] when the PIN is too short. */
    fun seal(plaintext: ByteArray, pin: String, random: SecureRandom = SecureRandom()): ByteArray {
        require(pin.length >= MIN_PIN_LENGTH) { "PIN must be at least $MIN_PIN_LENGTH characters" }
        val salt = ByteArray(SALT_BYTES).also { random.nextBytes(it) }
        val nonce = ByteArray(NONCE_BYTES).also { random.nextBytes(it) }
        val header = ByteBuffer.allocate(HEADER_BYTES).put(VERSION).putInt(ITERATIONS).put(salt).put(nonce).array()
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, key(pin, salt, ITERATIONS), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(header)
        return header + cipher.doFinal(plaintext)
    }

    /** Opens a file made by [seal]. Throws [RestoreException] for a wrong PIN, a changed file, or a file of another kind. */
    fun open(sealed: ByteArray, pin: String): ByteArray {
        if (sealed.size <= HEADER_BYTES || sealed[0] != VERSION) throw RestoreException("Not a Cinema restore file")
        val buffer = ByteBuffer.wrap(sealed)
        buffer.get()
        val iterations = buffer.int
        if (iterations < 1 || iterations > MAX_ITERATIONS) throw RestoreException("Not a Cinema restore file")
        val salt = ByteArray(SALT_BYTES).also { buffer.get(it) }
        val nonce = ByteArray(NONCE_BYTES).also { buffer.get(it) }
        val header = sealed.copyOf(HEADER_BYTES)
        return try {
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.DECRYPT_MODE, key(pin, salt, iterations), GCMParameterSpec(TAG_BITS, nonce))
            cipher.updateAAD(header)
            cipher.doFinal(sealed, HEADER_BYTES, sealed.size - HEADER_BYTES)
        } catch (e: java.security.GeneralSecurityException) {
            throw RestoreException("The PIN is wrong, or the file was changed")
        }
    }

    private const val MAX_ITERATIONS = 10_000_000
    private const val TRANSFORM = "AES/GCM/NoPadding"

    private fun key(pin: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, KEY_BITS)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}
