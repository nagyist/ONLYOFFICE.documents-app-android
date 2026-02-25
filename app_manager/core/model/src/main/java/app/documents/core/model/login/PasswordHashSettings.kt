package app.documents.core.model.login

import kotlinx.serialization.Serializable

@Serializable
data class PasswordHashSettings(
    val size: Int,
    val iterations: Int,
    val salt: String
)

@Serializable
data class SettingsResponse(
    val passwordHash: PasswordHashSettings? = null
)
