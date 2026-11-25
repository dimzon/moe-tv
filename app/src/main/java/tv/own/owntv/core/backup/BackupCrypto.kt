package tv.own.owntv.core.backup

import android.util.Base64
import org.json.JSONObject
import java.security.GeneralSecurityException
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import java.nio.CharBuffer
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Field-level secret encryption for backups (NOT whole-file encryption — only individual secret values).
 *
 * Only individual secret values (source/proxy passwords) are encrypted; everything else in the backup
 * JSON stays human-readable — whole-file protection is [BackupContainer]'s job, which seals the
 * container with the same passphrase. A user-supplied passphrase is stretched with PBKDF2 over a
 * per-backup random salt (so the file is portable across devices — no Android Keystore), then each
 * field is sealed with AES-256-GCM using a fresh random IV. The GCM tag authenticates both the value
 * and lets us detect a wrong passphrase (decrypt throws) without ever comparing the passphrase itself.
 *
 * Detection on import: a secret stored as a JSON string is legacy plaintext (v5); a secret stored as a
 * JSON object `{ "iv":..., "ct":... }` is encrypted. The root `crypto` block carries the KDF params.
 *
 * Logging rule: this class never logs the passphrase, derived key, plaintext, or ciphertext.
 */
object BackupCrypto {
    const val SCHEME = "field-aes-gcm"
    const val KDF = "PBKDF2WithHmacSHA256"
    private const val KDF_SHA1 = "PBKDF2WithHmacSHA1"
    const val ITERATIONS = 210_000
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val KEY_BITS = 256
    private const val GCM_TAG_BITS = 128

    private val rng = SecureRandom()

    private fun b64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun unb64(s: String): ByteArray = Base64.decode(s, Base64.NO_WRAP)

    /** A fresh random salt for a new backup. */
    fun newSalt(): ByteArray = ByteArray(SALT_BYTES).also { rng.nextBytes(it) }

    /** Builds the root `crypto` metadata block for the given salt. */
    fun cryptoBlock(salt: ByteArray): JSONObject = JSONObject().apply {
        put("scheme", SCHEME)
        put("kdf", supportedKdf())
        put("iterations", ITERATIONS)
        put("salt", b64(salt))
    }

    /** SHA-256 PBKDF2 arrived in API 26; API 23/24 must still be able to seal a backup. */
    private fun supportedKdf(): String = try {
        SecretKeyFactory.getInstance(KDF)
        KDF
    } catch (_: NoSuchAlgorithmException) {
        KDF_SHA1
    }

    /** Derives the AES key from a passphrase + the backup's salt/iterations. */
    fun deriveKey(
        passphrase: CharArray,
        salt: ByteArray,
        iterations: Int,
        kdf: String = supportedKdf(),
    ): SecretKey {
        val spec = PBEKeySpec(passphrase, salt, iterations, KEY_BITS)
        return try {
            val factory = try {
                SecretKeyFactory.getInstance(kdf)
            } catch (error: NoSuchAlgorithmException) {
                if (kdf == KDF) {
                    // API 23/24 lack the provider alias, but HmacSHA256 itself exists. Derive the
                    // same bytes so a backup sealed on Android 8+ still opens on the minimum SDK.
                    return SecretKeySpec(pbkdf2HmacSha256(passphrase, salt, iterations), "AES")
                }
                throw error
            }
            SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun pbkdf2HmacSha256(password: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        require(iterations > 0) { "PBKDF2 iterations must be positive" }
        val encoded = Charsets.UTF_8.newEncoder().encode(CharBuffer.wrap(password))
        val passwordBytes = ByteArray(encoded.remaining()).also(encoded::get)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(passwordBytes, "HmacSHA256"))
        passwordBytes.fill(0)

        val hashLength = mac.macLength
        val keyLength = KEY_BITS / 8
        val blocks = (keyLength + hashLength - 1) / hashLength
        val output = ByteArray(blocks * hashLength)
            val saltAndIndex = ByteArray(salt.size + 4)

        salt.copyInto(saltAndIndex)
        for (block in 1..blocks) {
            saltAndIndex[salt.size] = (block ushr 24).toByte()
            saltAndIndex[salt.size + 1] = (block ushr 16).toByte()
            saltAndIndex[salt.size + 2] = (block ushr 8).toByte()
            saltAndIndex[salt.size + 3] = block.toByte()
            var u = mac.doFinal(saltAndIndex)
            val t = u.copyOf()
            repeat(iterations - 1) {
                u = mac.doFinal(u)
                for (i in 0 until hashLength) t[i] = (t[i].toInt() xor u[i].toInt()).toByte()
            }
            t.copyInto(output, (block - 1) * hashLength)
        }
        return output.copyOf(keyLength)
    }

    /** Derives the key from a [crypto] root block + passphrase. Returns null if the block is malformed. */
    fun deriveKey(passphrase: String, crypto: JSONObject?): SecretKey? {
        crypto ?: return null
        val salt = runCatching { unb64(crypto.getString("salt")) }.getOrNull() ?: return null
        val iters = crypto.optInt("iterations", ITERATIONS)
        val kdf = crypto.optString("kdf", KDF_SHA1).ifBlank { KDF_SHA1 }
        return try {
            deriveKey(passphrase.toCharArray(), salt, iters, kdf)
        } catch (_: GeneralSecurityException) {
            // A newer device may have sealed with SHA-256, which API 23/24 cannot derive. Treat it
            // like a wrong/unsupported passphrase instead of crashing the import.
            null
        }
    }

    /** Seals a plaintext secret into a `{ "iv":..., "ct":... }` object. */
    fun encrypt(key: SecretKey, plaintext: String): JSONObject {
        val iv = ByteArray(IV_BYTES).also { rng.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val ct = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return JSONObject().put("iv", b64(iv)).put("ct", b64(ct))
    }

    /**
     * Seals arbitrary bytes, returning `iv || ciphertext` as one blob — used by [BackupContainer] to
     * encrypt the WHOLE `.own` container, not just individual fields. The IV rides in front because a
     * container has nowhere to put a JSON wrapper: everything after the plaintext header is opaque.
     */
    fun encryptBytes(key: SecretKey, plaintext: ByteArray): ByteArray {
        val iv = ByteArray(IV_BYTES).also { rng.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return iv + cipher.doFinal(plaintext)
    }

    /**
     * Opens an [encryptBytes] blob that starts at [offset] in [bytes] — read in place, because a
     * backup can be tens of MB and a copy of it is one more allocation a TV may not have room for.
     * Throws `AEADBadTagException` on a wrong passphrase (GCM tag mismatch) or truncation.
     */
    fun decryptBytes(key: SecretKey, bytes: ByteArray, offset: Int): ByteArray {
        require(bytes.size - offset > IV_BYTES) { "Truncated encrypted backup" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, bytes, offset, IV_BYTES))
        return cipher.doFinal(bytes, offset + IV_BYTES, bytes.size - offset - IV_BYTES)
    }

    /** True if [value] is an encrypted secret object (vs a legacy plaintext string). */
    fun isEncrypted(value: Any?): Boolean = value is JSONObject && value.has("iv") && value.has("ct")

    /**
     * Opens a sealed secret object. Throws on a wrong passphrase (GCM tag mismatch) or malformed input —
     * callers use this both to validate the passphrase and to decrypt.
     */
    fun decrypt(key: SecretKey, sealed: JSONObject): String {
        val iv = unb64(sealed.getString("iv"))
        val ct = unb64(sealed.getString("ct"))
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(ct), Charsets.UTF_8)
    }
}
