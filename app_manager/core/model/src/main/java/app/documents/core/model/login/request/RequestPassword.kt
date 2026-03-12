package app.documents.core.model.login.request

import kotlinx.serialization.Serializable

@Serializable
data class RequestPassword(
    val email: String,
    val recaptchaResponse: String,
    val recaptchaType: Int = 3
)