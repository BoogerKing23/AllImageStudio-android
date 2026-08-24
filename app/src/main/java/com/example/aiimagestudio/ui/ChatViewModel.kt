package com.example.aiimagestudio.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aiimagestudio.BuildConfig
import com.example.aiimagestudio.data.GeminiApiClient
import com.example.aiimagestudio.model.ChatMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Orkiestruje cały przepływ "2 AI w czacie", w pełni automatycznie:
 *
 *  1. AI Opisujące zamienia prośbę użytkownika w bardzo szczegółowy opis.
 *  2. AI Generujące tworzy na jego podstawie pierwszą wersję obrazu.
 *  3. AI Opisujące (jako recenzent) ogląda obraz, aktywnie szuka rzeczy do
 *     poprawy i albo zatwierdza go jako w 100% poprawny, albo samo pisze
 *     precyzyjny "prompt korygujący" (co zmienić, a co zostawić bez zmian).
 *  4. Ten prompt trafia automatycznie (bez udziału użytkownika) do AI
 *     Generującego, które edytuje obraz.
 *  5. Punkty 3–4 powtarzają się — w czacie widać każdą rundę osobno.
 *
 * Zakończenie pętli następuje na jeden z trzech sposobów:
 *  - recenzent uzna obraz za w pełni poprawny,
 *  - zostanie osiągnięty limit rund ([MAX_ITERATIONS_NORMAL] albo, w trybie
 *    "ulepszaj aż zaakceptuje", znacznie wyższy [MAX_ITERATIONS_UNTIL_APPROVED]),
 *  - użytkownik ręcznie kliknie „Stop” ([stopProcessing]).
 */
class ChatViewModel : ViewModel() {

    private companion object {
        // Tryb zwykły: rozsądny, ograniczony limit rund — szybka, jedna próba.
        const val MAX_ITERATIONS_NORMAL = 8

        // Tryb "ulepszaj aż zaakceptuje": praktycznie bez limitu — jedyny
        // sposób na zatrzymanie to zatwierdzenie przez recenzenta albo
        // ręczne kliknięcie „Stop”. Ta stała to tylko techniczne
        // zabezpieczenie awaryjne (np. gdyby recenzent w kółko czepiał się
        // drobiazgów) — w normalnym użyciu nie powinna zostać osiągnięta.
        const val MAX_ITERATIONS_UNTIL_APPROVED = 50
    }

    private val apiClient = GeminiApiClient(BuildConfig.GEMINI_API_KEY)

    /** Cała historia czatu — użytkownik + obie AI. UI tylko to odczytuje. */
    val messages = mutableStateListOf<ChatMessage>()

    var isProcessing by mutableStateOf(false)
        private set

    /** Krótki opis aktualnie wykonywanego kroku, do wyświetlenia nad polem tekstowym. */
    var statusLabel by mutableStateOf<String?>(null)
        private set

    /** Tryb: ulepszaj w kółko, aż recenzent zaakceptuje (praktycznie bez limitu rund). */
    var improveUntilApprovedMode by mutableStateOf(false)
        private set

    private var pipelineJob: Job? = null

    fun updateImproveUntilApprovedMode(enabled: Boolean) {
        improveUntilApprovedMode = enabled
    }

    fun sendUserRequest(rawText: String) {
        val text = rawText.trim()
        if (text.isEmpty() || isProcessing) return

        if (BuildConfig.GEMINI_API_KEY.isBlank()) {
            messages.add(
                ChatMessage.ErrorMessage(
                    "Brak klucza API Gemini. Dodaj GEMINI_API_KEY w pliku local.properties " +
                        "i zbuduj aplikację ponownie."
                )
            )
            return
        }

        messages.add(ChatMessage.UserPrompt(text))
        isProcessing = true

        val maxIterations =
            if (improveUntilApprovedMode) MAX_ITERATIONS_UNTIL_APPROVED else MAX_ITERATIONS_NORMAL

        pipelineJob = viewModelScope.launch {
            try {
                runPipeline(text, maxIterations)
            } catch (e: CancellationException) {
                messages.add(ChatMessage.StatusMessage("⏹️ Zatrzymano przez użytkownika."))
                throw e // zgodnie z dobrą praktyką coroutines: anulowanie zawsze propagujemy dalej
            } catch (e: Exception) {
                messages.add(
                    ChatMessage.ErrorMessage("Wystąpił błąd: ${e.message ?: "nieznany błąd"}.")
                )
            } finally {
                statusLabel = null
                isProcessing = false
                pipelineJob = null
            }
        }
    }

    /**
     * Natychmiast przerywa trwający proces — łącznie z aktualnie wysłanym
     * zapytaniem sieciowym (patrz GeminiApiClient.executeRequest), więc
     * działa bez zauważalnego opóźnienia niezależnie od tego, w którym
     * kroku aktualnie jesteśmy.
     */
    fun stopProcessing() {
        pipelineJob?.cancel()
    }

    private suspend fun runPipeline(userRequest: String, maxIterations: Int) {
        statusLabel = "🖋️ AI Opisujące tworzy szczegółowy opis…"
        val description = apiClient.generateDescription(userRequest)
        messages.add(ChatMessage.DescriberMessage(description))

        statusLabel = "🎨 AI Generujące tworzy pierwszą wersję obrazu…"
        var image = apiClient.generateImage(description)
        var iteration = 1
        messages.add(ChatMessage.GeneratedImageMessage(image, iteration))

        while (iteration < maxIterations) {
            statusLabel = "🔍 AI Opisujące analizuje obraz (runda ${iteration}${roundSuffix(maxIterations)})…"
            val review = apiClient.reviewImage(userRequest, description, image)

            if (review.isPerfect) {
                messages.add(
                    ChatMessage.ReviewerMessage(review.feedback, isApproved = true, reviewedIteration = iteration)
                )
                markLastImageAsFinal()
                return
            }

            // Recenzent znalazł coś do poprawy — jego odpowiedź JEST już gotowym
            // promptem korygującym (dokładnie co zmienić / co zostawić bez zmian).
            messages.add(
                ChatMessage.ReviewerMessage(review.feedback, isApproved = false, reviewedIteration = iteration)
            )

            statusLabel =
                "🛠️ AI Generujące automatycznie nanosi poprawki (runda ${iteration + 1}${roundSuffix(maxIterations)})…"
            image = apiClient.editImage(image, review.feedback)
            iteration++
            messages.add(ChatMessage.GeneratedImageMessage(image, iteration))
        }

        messages.add(
            ChatMessage.StatusMessage(
                "Osiągnięto limit $maxIterations rund poprawek – to najlepsza wersja, jaką udało się uzyskać. " +
                    "Możesz wysłać nową prośbę, żeby spróbować jeszcze raz."
            )
        )
    }

    // W trybie "aż zaakceptuje" nie pokazujemy "/50" w statusie, żeby nie
    // sugerować użytkownikowi fałszywego celu — w tym trybie liczy się
    // wyłącznie zatwierdzenie albo ręczny Stop.
    private fun roundSuffix(maxIterations: Int): String =
        if (improveUntilApprovedMode) "" else "/$maxIterations"

    private fun markLastImageAsFinal() {
        val index = messages.indexOfLast { it is ChatMessage.GeneratedImageMessage }
        if (index != -1) {
            messages[index] = (messages[index] as ChatMessage.GeneratedImageMessage).asFinal()
        }
    }
}
