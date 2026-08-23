package com.example.aiimagestudio.model

import java.util.UUID

/**
 * Pojedyncza "wypowiedź" widoczna w czacie. Oprócz wiadomości użytkownika
 * mamy tu wiadomości od dwóch "aktorów" AI:
 *  - AI Opisujące  -> pisze szczegółowy opis obrazu, a potem go recenzuje
 *  - AI Generujące -> tworzy / edytuje sam obraz
 */
sealed class ChatMessage {
    abstract val id: String

    /** Prośba użytkownika o obraz. */
    data class UserPrompt(
        val text: String,
        override val id: String = UUID.randomUUID().toString()
    ) : ChatMessage()

    /** Szczegółowy opis obrazu napisany przez AI Opisujące. */
    data class DescriberMessage(
        val text: String,
        override val id: String = UUID.randomUUID().toString()
    ) : ChatMessage()

    /** Wygenerowany (lub poprawiony) obraz — jedna "wersja" w pętli poprawek. */
    class GeneratedImageMessage(
        val imageBytes: ByteArray,
        val iteration: Int,
        val isFinal: Boolean = false,
        override val id: String = UUID.randomUUID().toString()
    ) : ChatMessage() {
        fun asFinal(): GeneratedImageMessage =
            GeneratedImageMessage(imageBytes, iteration, isFinal = true, id = id)
    }

    /** Recenzja obrazu — albo zatwierdzenie, albo uwagi do poprawy. */
    data class ReviewerMessage(
        val text: String,
        val isApproved: Boolean,
        val reviewedIteration: Int,
        override val id: String = UUID.randomUUID().toString()
    ) : ChatMessage()

    /** Neutralny komunikat systemowy (np. osiągnięto limit poprawek). */
    data class StatusMessage(
        val text: String,
        override val id: String = UUID.randomUUID().toString()
    ) : ChatMessage()

    /** Błąd komunikacji z API. */
    data class ErrorMessage(
        val text: String,
        override val id: String = UUID.randomUUID().toString()
    ) : ChatMessage()
}
