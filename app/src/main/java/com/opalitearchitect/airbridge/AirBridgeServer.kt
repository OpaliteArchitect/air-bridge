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
import android.net.Uri
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class AuthRequest(val pin: String)

@Serializable
data class AuthResponse(val success: Boolean)

object AirBridgeServer {
    private var serverInstance: EmbeddedServer<*, *>? = null

    // In-memory state tied to server runtime
    val files = ConcurrentHashMap<String, SharedFile>()
    val activeTokens = ConcurrentHashMap.newKeySet<String>()
    var activePin: String = "1234"
        private set

    val isRunning: Boolean
        get() = serverInstance != null

    fun start(context: Context, port: Int = 8080): String {
        if (isRunning) return getLocalIpUrl(port)

        // Generate fresh PIN and clear past sessions on every start
        activePin = (1000..9999).random().toString()
        files.clear()
        activeTokens.clear()

        serverInstance = embeddedServer(CIO, host = "0.0.0.0", port = port) {
            install(ContentNegotiation) { json() }

            routing {
                // Serve React frontend from assets/web
                staticResources("/", "web") {
                    default("index.html")
                }

                route("/api") {
                    post("/auth") {
                        val request = call.receive<AuthRequest>()
                        if (request.pin == activePin) {
                            val sessionToken = UUID.randomUUID().toString()
                            activeTokens.add(sessionToken)

                            call.response.cookies.append(
                                Cookie(
                                    name = "air_bridge_session",
                                    value = sessionToken,
                                    httpOnly = true,
                                    path = "/"
                                )
                            )
                            call.respond(AuthResponse(success = true))
                        } else {
                            call.respond(HttpStatusCode.Unauthorized, AuthResponse(success = false))
                        }
                    }

                    get("/files") {
                        if (!isAuthorized(call)) {
                            call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
                            return@get
                        }
                        val fileList = files.values.map { SharedFileDto(it.id, it.name, it.size) }
                        call.respond(fileList)
                    }

                    get("/download/{id}") {
                        if (!isAuthorized(call)) {
                            call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
                            return@get
                        }

                        val id = call.parameters["id"]
                        val fileItem = files[id] ?: run {
                            call.respond(HttpStatusCode.NotFound, "File not found")
                            return@get
                        }

                        val inputStream = context.contentResolver.openInputStream(fileItem.uri) ?: run {
                            call.respond(HttpStatusCode.InternalServerError, "Unable to read file")
                            return@get
                        }

                        call.response.headers.append(
                            HttpHeaders.ContentDisposition,
                            "attachment; filename=\"${fileItem.name}\""
                        )

                        call.respondBytesWriter(contentType = ContentType.Application.OctetStream) {
                            inputStream.use { input -> input.copyTo(this.toOutputStream()) }
                        }
                    }
                }
            }
        }.start(wait = false)

        return getLocalIpUrl(port)
    }

    fun stop() {
        serverInstance?.stop(1000, 2000)
        serverInstance = null
        files.clear()
        activeTokens.clear()
    }

    private fun isAuthorized(call: ApplicationCall): Boolean {
        val token = call.request.cookies["air_bridge_session"]
        return token != null && activeTokens.contains(token)
    }

    private fun getLocalIpUrl(port: Int): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return "http://localhost:$port"
            while (interfaces.hasMoreElements()) {
                val netInterface = interfaces.nextElement()
                if (!netInterface.isUp || netInterface.isLoopback) continue
                val addresses = netInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return "http://${addr.hostAddress}:$port"
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return "http://localhost:$port"
    }
}