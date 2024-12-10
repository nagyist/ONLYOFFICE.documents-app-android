package app.documents.core.network.room.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class RequestCreateRoom(
    val title: String,
    val roomType: Int,
    @Transient
    val quota: Long? = null
)
