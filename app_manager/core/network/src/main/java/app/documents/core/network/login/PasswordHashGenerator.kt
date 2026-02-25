package app.documents.core.network.login

import app.documents.core.model.login.PasswordHashSettings
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object PasswordHashGenerator {

    fun generate(password: String, settings: PasswordHashSettings): String {
        val salt = settings.salt.toByteArray(Charsets.UTF_8)
        val keyLengthBits = settings.size

        val spec = PBEKeySpec(
            password.toCharArray(),
            salt,
            settings.iterations,
            keyLengthBits
        )

        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hash = factory.generateSecret(spec).encoded

        return bytesToHex(hash)
    }

    private fun bytesToHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }
}

