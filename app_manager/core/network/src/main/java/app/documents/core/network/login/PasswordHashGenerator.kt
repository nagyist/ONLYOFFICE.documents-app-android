package app.documents.core.network.login

import app.documents.core.model.login.PasswordHashSettings
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object PasswordHashGenerator {

    fun generate(password: String, settings: PasswordHashSettings): String {
        val salt = hexToBytes(settings.salt)
        val keyLengthBits = settings.size

        val spec = PBEKeySpec(
            /* password = */ password.toCharArray(),
            /* salt = */ salt,
            /* iterationCount = */ settings.iterations,
            /* keyLength = */ keyLengthBits
        )

        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hash = factory.generateSecret(spec).encoded

        return bytesToHex(hash)
    }


    private fun hexToBytes(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        for (i in 0 until len step 2) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4)
                    + Character.digit(hex[i + 1], 16)).toByte()
        }
        return data
    }

    private fun bytesToHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }
}
