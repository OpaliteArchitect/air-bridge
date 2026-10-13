package com.opalitearchitect.airbridge

import android.net.Uri
import kotlinx.serialization.Serializable
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class SharedFileDto(
    val id: String,
    val name: String,
    val size: String
)

data class SharedFile(
    val id: String,
    val name: String,
    val size: String,
    val uri: Uri
)

object FileRepository {
    val files = ConcurrentHashMap<String, SharedFile>()
    var activePin: String = "1234"
    val activeTokens = ConcurrentHashMap.newKeySet<String>()
}