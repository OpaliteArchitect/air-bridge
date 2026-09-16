package com.opalitearchitect.airbridge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.ui.unit.dp

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

@Composable
fun ComposableFun() {
    var serverAddress by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Button(
            onClick = {
                startKtorServer()
                serverAddress = "http://${getLocalIpAddress()}:8080"
            }
        ) {
            Text(text = "Click Me")
        }

        if (serverAddress.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = serverAddress)
        }
    }
}

fun startKtorServer() {
    thread {
        embeddedServer(CIO, host = "0.0.0.0", port = 8080) {
            routing {
                get("/") {
                    call.respondText("Hello World!")
                }
            }
        }.start(wait = true)
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