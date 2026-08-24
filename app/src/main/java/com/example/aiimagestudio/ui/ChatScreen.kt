package com.example.aiimagestudio.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aiimagestudio.ui.components.FullScreenImageDialog
import com.example.aiimagestudio.ui.components.InputBar
import com.example.aiimagestudio.ui.components.MessageBubble

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel = viewModel()) {
    val listState = rememberLazyListState()
    var fullScreenImage by remember { mutableStateOf<ByteArray?>(null) }

    // Automatyczne przewijanie do najnowszej wiadomości — dzięki temu widać
    // na żywo każdy kolejny krok obu AI, bez ręcznego przewijania.
    LaunchedEffect(viewModel.messages.size) {
        if (viewModel.messages.isNotEmpty()) {
            listState.animateScrollToItem(viewModel.messages.lastIndex)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Kreator Obrazów AI") })
        },
        bottomBar = {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Ulepszaj aż zaakceptuje (bez limitu rund)",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = viewModel.improveUntilApprovedMode,
                        onCheckedChange = { viewModel.updateImproveUntilApprovedMode(it) },
                        enabled = !viewModel.isProcessing
                    )
                }
                if (viewModel.isProcessing) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text(
                            text = viewModel.statusLabel ?: "Pracuję…",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { viewModel.stopProcessing() }) {
                            Text("⏹️ Stop")
                        }
                    }
                }
                InputBar(
                    enabled = !viewModel.isProcessing,
                    onSend = { viewModel.sendUserRequest(it) }
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(viewModel.messages, key = { _, item -> item.id }) { _, message ->
                MessageBubble(message = message, onImageClick = { fullScreenImage = it })
            }
        }
    }

    fullScreenImage?.let { bytes ->
        FullScreenImageDialog(imageBytes = bytes, onDismiss = { fullScreenImage = null })
    }
}
