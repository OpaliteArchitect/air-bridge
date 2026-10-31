package com.opalitearchitect.airbridge

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.util.UUID

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            var isServerRunning by remember { mutableStateOf(AirBridgeServer.isRunning) }
            var serverAddress by remember { mutableStateOf("") }
            var pin by remember { mutableStateOf(AirBridgeServer.activePin) }

            val sharedFiles = remember { mutableStateListOf<SharedFile>() }

            val filePickerLauncher =
                rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetContent()
                ) { uri: Uri? ->
                    if (uri != null) {
                        val fileName = getFileName(context, uri) ?: "unknown_file"
                        val fileId = UUID.randomUUID().toString().substring(0, 8)
                        val fileSize = "Unknown size"

                        val newFile =
                            SharedFile(id = fileId, name = fileName, size = fileSize, uri = uri)
                        AirBridgeServer.files[fileId] = newFile
                        sharedFiles.add(newFile)
                    }
                }

            Surface(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(modifier = Modifier.height(32.dp))
                    Text("AirBridge", style = MaterialTheme.typography.headlineMedium)

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            if (isServerRunning) {
                                AirBridgeServer.stop()
                                isServerRunning = false
                                serverAddress = ""
                                sharedFiles.clear()
                            } else {
                                serverAddress = AirBridgeServer.start(context)
                                isServerRunning = true
                                pin = AirBridgeServer.activePin
                            }
                        }
                    ) {
                        Text(if (isServerRunning) "Stop sending" else "Start sending")
                    }

                    if (isServerRunning) {
                        Spacer(modifier = Modifier.height(16.dp))

                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text("PIN: $pin", style = MaterialTheme.typography.titleLarge)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(serverAddress, style = MaterialTheme.typography.bodyMedium)

                                if (serverAddress.isNotEmpty()) {
                                    val qrBitmap =
                                        remember(serverAddress) {
                                            generateQRCodeBitmap(serverAddress)
                                        }
                                    qrBitmap?.let {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Image(
                                            bitmap = it.asImageBitmap(),
                                            contentDescription = "QR Code",
                                            modifier = Modifier.size(180.dp),
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { filePickerLauncher.launch("*/*") }) {
                            Text("Add file")
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (sharedFiles.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxWidth().weight(1f),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        "No files shared yet",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "Tap the button above to add files",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline,
                                    )
                                }
                            }
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                                items(sharedFiles) { file ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    file.name,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                )
                                                Text(
                                                    "ID: ${file.id}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                )
                                            }
                                            TextButton(
                                                onClick = {
                                                    AirBridgeServer.files.remove(file.id)
                                                    sharedFiles.remove(file)
                                                }
                                            ) {
                                                Text(
                                                    "Remove",
                                                    color = MaterialTheme.colorScheme.error,
                                                )
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
    }

    override fun onDestroy() {
        super.onDestroy()
        AirBridgeServer.stop()
    }
}

private fun generateQRCodeBitmap(content: String, width: Int = 512, height: Int = 512): Bitmap? {
    return try {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, width, height)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(
                    x,
                    y,
                    if (bitMatrix[x, y]) android.graphics.Color.BLACK
                    else android.graphics.Color.WHITE,
                )
            }
        }
        bitmap
    } catch (e: Exception) {
        null
    }
}

private fun getFileName(context: Context, uri: Uri): String? {
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
