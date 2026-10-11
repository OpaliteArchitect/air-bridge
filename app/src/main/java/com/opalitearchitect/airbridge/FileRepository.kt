package com.opalitearchitect.airbridge

import android.net.Uri
import java.util.concurrent.ConcurrentHashMap

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
}