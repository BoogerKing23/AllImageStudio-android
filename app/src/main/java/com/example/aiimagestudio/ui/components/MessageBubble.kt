package com.example.aiimagestudio.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.example.aiimagestudio.model.ChatMessage
import com.example.aiimagestudio.ui.theme.DescriberBubble
import com.example.aiimagestudio.ui.theme.ReviewerApprovedBubble
import com.example.aiimagestudio.ui.theme.ReviewerFeedbackBubble
import com.example.aiimagestudio.ui.theme.UserBubble

@Composable
fun MessageBubble(message: ChatMessage, onImageClick: (ByteArray) -> Unit) {
    when (message) {
        is ChatMessage.UserPrompt -> TextBubble(
            text = message.text,
            label = "Ty",
            alignment = Alignment.End,
            backgroundColor = UserBubble,
            textColor = Color.White
        )

        is ChatMessage.DescriberMessage -> TextBubble(
            text = message.text,
            label = "🖋️ AI Opisujące — opis obrazu",
            alignment = Alignment.Start,
            backgroundColor = DescriberBubble,
            textColor = MaterialTheme.colorScheme.onSurface
        )

        is ChatMessage.ReviewerMessage -> TextBubble(
            text = message.text,
            label = if (message.isApproved)
                "✅ AI Opisujące — recenzja wersji ${message.reviewedIteration}: zatwierdzone!"
            else
                "🔍 AI Opisujące — recenzja wersji ${message.reviewedIteration}: znalazłem poprawki",
            alignment = Alignment.Start,
            backgroundColor = if (message.isApproved) ReviewerApprovedBubble else ReviewerFeedbackBubble,
            textColor = MaterialTheme.colorScheme.onSurface
        )

        is ChatMessage.ErrorMessage -> TextBubble(
            text = message.text,
            label = "⚠️ Błąd",
            alignment = Alignment.Start,
            backgroundColor = MaterialTheme.colorScheme.errorContainer,
            textColor = MaterialTheme.colorScheme.onErrorContainer
        )

        is ChatMessage.StatusMessage -> Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        is ChatMessage.GeneratedImageMessage -> ImageBubble(message = message, onClick = onImageClick)
    }
}

@Composable
private fun TextBubble(
    text: String,
    label: String,
    alignment: Alignment.Horizontal,
    backgroundColor: Color,
    textColor: Color
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(backgroundColor)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(text = text, color = textColor, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ImageBubble(
    message: ChatMessage.GeneratedImageMessage,
    onClick: (ByteArray) -> Unit
) {
    val bitmap = remember(message.id) {
        BitmapFactory.decodeByteArray(message.imageBytes, 0, message.imageBytes.size)
    }

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
        Text(
            text = if (message.isFinal)
                "✅ AI Generujące — wersja finalna"
            else
                "🎨 AI Generujące — wersja ${message.iteration}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )
        if (bitmap != null) {
            // Celowo ContentScale.Fit (bez przycinania) — to obraz, który
            // AI właśnie ocenia, więc żaden fragment nie powinien być ukryty.
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Wygenerowany obraz, wersja ${message.iteration}",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .widthIn(max = 260.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onClick(message.imageBytes) }
            )
        } else {
            Text("Nie udało się wyświetlić obrazu.", color = MaterialTheme.colorScheme.error)
        }
    }
}
