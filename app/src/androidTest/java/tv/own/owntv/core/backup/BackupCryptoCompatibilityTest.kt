package tv.own.owntv.core.backup

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import org.junit.runner.RunWith

/** PBKDF2-HMAC-SHA256 must be byte-identical with and without the API 26 provider alias. */
@RunWith(AndroidJUnit4::class)
class BackupCryptoCompatibilityTest {

    @Test
    fun sha256DerivationMatchesTheKnownVectorOnEverySupportedApi() {
        val key = BackupCrypto.deriveKey(
            "password".toCharArray(),
            "salt".toByteArray(Charsets.UTF_8),
            iterations = 1,
            kdf = BackupCrypto.KDF,
        )
        val expected = hex(
            "120fb6cffcf8b32c43e7225256c4f837a86548c92ccc35480805987cb70be17b",
        )
        assertArrayEquals(expected, key.encoded)
    }

    private fun hex(value: String): ByteArray {
        val out = ByteArray(value.length / 2)
        for (i in out.indices) out[i] = value.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        return out
    }
}
