package com.example.aiimagestudio.ui.components

import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun FullScreenImageDialog(imageBytes: ByteArray, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bitmap = remember(imageBytes) {
        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Podgląd obrazu w pełnym rozmiarze",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onDismiss) {
                    Text("✕", color = Color.White, style = MaterialTheme.typography.headlineSmall)
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(onClick = {
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            runCatching { saveImageToGallery(context, imageBytes) }
                        }
                        result.onSuccess {
                            Toast.makeText(
                                context,
                                "Zapisano w galerii (Pictures/AIImageStudio)",
                                Toast.LENGTH_SHORT
                            ).show()
                        }.onFailure {
                            Toast.makeText(
                                context,
                                "Nie udało się zapisać obrazu: ${it.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }) {
                    Text("💾 Zapisz do galerii")
                }
            }
        }
    }
}

private fun saveImageToGallery(context: Context, imageBytes: ByteArray) {
    val filename = "ai_image_studio_${System.currentTimeMillis()}.png"
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, filename)
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/AIImageStudio")
    }
    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        ?: error("Nie udało się utworzyć wpisu w MediaStore")
    val outputStream = context.contentResolver.openOutputStream(uri)
        ?: error("Nie udało się otworzyć strumienia zapisu")
    outputStream.use { it.write(imageBytes) }
}
