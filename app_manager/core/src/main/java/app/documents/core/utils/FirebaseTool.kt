package app.documents.core.utils

interface FirebaseTool {

    suspend fun isCoauthoring(): Boolean

    suspend fun checkCoauthoring(sdkVersion: String?): Boolean
}