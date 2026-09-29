package com.opalitearchitect.airbridge

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import java.net.Inet4Address
import java.net.NetworkInterface
import kotlin.concurrent.thread
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import android.net.Uri
import io.ktor.http.ContentType
import io.ktor.server.response.respondBytesWriter
import io.ktor.utils.io.jvm.javaio.toOutputStream

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ComposableFun()
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    ComposableFun()
}

var serverInstance: EmbeddedServer<
        CIOApplicationEngine,
        CIOApplicationEngine.Configuration
        >? = null

@Composable
fun ComposableFun() {
    val context = LocalContext.current
    var serverAddress by remember { mutableStateOf("") }
    var isServerRunning by remember { mutableStateOf(false) }
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedFileUri = uri
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Button(
            onClick = {
                if (serverInstance != null) {
                    stopKtorServer()
                    isServerRunning = false
                    serverAddress = ""
                    selectedFileUri = null
                } else {
                    startKtorServer(context) { selectedFileUri }
                    isServerRunning = true
                    serverAddress = "http://${getLocalIpAddress()}:8080"
                }
            }
        ) {
            Text(text = if (isServerRunning) "Stop server" else "Start server")
        }

        if (isServerRunning) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { filePickerLauncher.launch("*/*") }) {
                Text(text = if (selectedFileUri == null) "Select File" else "Change File")
            }
        }

        if (serverAddress.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = serverAddress)
        }
    }
}

fun startKtorServer(context: Context, getFileUri: () -> Uri?) {
    thread {
        serverInstance = embeddedServer(CIO, host = "0.0.0.0", port = 8080) {
            routing {
                get("/") {
                    call.respondText("Hello World!")
                }
                get("/download") {
                    val uri = getFileUri()
                    if (uri == null) {
                        call.respondText("No file selected yet.")
                        return@get
                    }

                    val inputStream = context.contentResolver.openInputStream(uri)
                    if (inputStream == null) {
                        call.respondText("Unable to read file.")
                        return@get
                    }

                    call.respondBytesWriter(contentType = ContentType.Application.OctetStream) {
                        inputStream.use { input ->
                            input.copyTo(this.toOutputStream())
                        }
                    }
                }
            }
        }
        serverInstance?.start(wait = true)
    }
}

fun stopKtorServer() {
    thread {
        serverInstance?.stop(1000, 2000)
        serverInstance = null
    }
}

fun getLocalIpAddress(): String {
    val result = mutableListOf<String>()
    try {
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return ""
        while (interfaces.hasMoreElements()) {
            val networkInterface = interfaces.nextElement()
            if (!networkInterface.isUp || networkInterface.isLoopback) continue
            val addresses = networkInterface.inetAddresses
            while (addresses.hasMoreElements()) {
                val address = addresses.nextElement()
                if (!address.isLoopbackAddress && address is Inet4Address) {
                    address.hostAddress?.let { result.add(it) }
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return result.joinToString(",")
}