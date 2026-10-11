package com.opalitearchitect.airbridge

import android.content.Context
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.cio.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.http.content.*
import io.ktor.utils.io.jvm.javaio.toOutputStream
import kotlinx.serialization.Serializable

@Serializable
data class AuthRequest(val pin: String)

@Serializable
data class AuthResponse(val success: Boolean)

fun startKtorServer(context: Context, serverPort: Int = 8080): EmbeddedServer<*, *> {
    return embeddedServer(CIO, host = "0.0.0.0", port = serverPort) {
        install(ContentNegotiation) {
            json()
        }

        routing {
            // 1. Serve React static files from assets/web directory
            staticResources("/", "web") {
                default("index.html")
            }

            // 2. API Routes
            route("/api") {
                // PIN Verification endpoint
                post("/auth") {
                    val request = call.receive<AuthRequest>()
                    val isValid = request.pin == FileRepository.activePin
                    call.respond(AuthResponse(success = isValid))
                }

                // Get file list (exposed as IDs, hiding local paths/URIs)
                get("/files") {
                    val fileList = FileRepository.files.values.map {
                        SharedFileDto(it.id, it.name, it.size)
                    }
                    call.respond(fileList)
                }

                // Stream file download by ID
                get("/download/{id}") {
                    val id = call.parameters["id"]
                    val fileItem = FileRepository.files[id]

                    if (fileItem == null) {
                        call.respond(HttpStatusCode.NotFound, "File not found")
                        return@get
                    }

                    val inputStream = context.contentResolver.openInputStream(fileItem.uri)
                    if (inputStream == null) {
                        call.respond(HttpStatusCode.InternalServerError, "Unable to read file")
                        return@get
                    }

                    call.response.headers.append(
                        HttpHeaders.ContentDisposition,
                        "attachment; filename=\"${fileItem.name}\""
                    )

                    call.respondBytesWriter(contentType = ContentType.Application.OctetStream) {
                        inputStream.use { input ->
                            input.copyTo(this.toOutputStream())
                        }
                    }
                }
            }
        }
    }.start(wait = false)
}