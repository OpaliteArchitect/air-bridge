package com.opalitearchitect.airbridge

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.ktor.server.engine.*
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.UUID

class MainActivity : ComponentActivity() {
    private var serverInstance: EmbeddedServer<*, *>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            var isServerRunning by remember { mutableStateOf(false) }
            var serverAddress by remember { mutableStateOf("") }
            var pin by remember { mutableStateOf("1234") }

            // Track files in UI state so the list recomposes when files are added/removed
            val sharedFiles = remember { mutableStateListOf<SharedFile>() }

            val filePickerLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.GetContent()
            ) { uri: Uri? ->
                if (uri != null) {
                    val fileName = getFileName(context, uri) ?: "unknown_file"
                    val fileId = UUID.randomUUID().toString().substring(0, 8)
                    val fileSize = "Unknown size" // You can query OpenableColumns.SIZE if needed

                    val newFile = SharedFile(id = fileId, name = fileName, size = fileSize, uri = uri)
                    FileRepository.files[fileId] = newFile
                    sharedFiles.add(newFile)
                }
            }

            Surface(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(32.dp))
                    Text("Air Bridge Host", style = MaterialTheme.typography.headlineMedium)

                    Spacer(modifier = Modifier.height(16.dp))

                    // Server Toggle Button
                    Button(
                        onClick = {
                            if (isServerRunning) {
                                serverInstance?.stop(1000, 2000)
                                serverInstance = null
                                isServerRunning = false
                                serverAddress = ""
                                FileRepository.files.clear()
                                sharedFiles.clear()
                            } else {
                                pin = (1000..9999).random().toString()
                                FileRepository.activePin = pin
                                serverInstance = startKtorServer(context)
                                isServerRunning = true
                                serverAddress = "http://${getLocalIpAddress()}:8080"
                            }
                        }
                    ) {
                        Text(if (isServerRunning) "Stop Server" else "Start Server")
                    }

                    if (isServerRunning) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("PIN: $pin", style = MaterialTheme.typography.titleLarge)
                        Text("URL: $serverAddress", style = MaterialTheme.typography.bodyMedium)

                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { filePickerLauncher.launch("*/*") }) {
                            Text("Add File to Share")
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Active Shared Files List
                        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                            items(sharedFiles) { file ->
                                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(file.name, style = MaterialTheme.typography.bodyLarge)
                                            Text("ID: ${file.id}", style = MaterialTheme.typography.bodySmall)
                                        }
                                        TextButton(onClick = {
                                            FileRepository.files.remove(file.id)
                                            sharedFiles.remove(file)
                                        }) {
                                            Text("Remove", color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serverInstance?.stop(1000, 2000)
    }
}

fun getFileName(context: Context, uri: Uri): String? {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    result = it.getString(index)
                }
            }
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/') ?: -1
        if (cut != -1) {
            result = result?.substring(cut + 1)
        }
    }
    return result
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