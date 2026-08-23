package com.example.aiimagestudio.data

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Lekki klient REST dla Gemini API (bez oficjalnego SDK — mniej zależności,
 * pełna kontrola nad zapytaniami). Odpowiada za cztery operacje w naszej
 * pętli "2 AI":
 *
 *  1. [generateDescription] — AI Opisujące: prośba użytkownika -> szczegółowy opis
 *  2. [generateImage]       — AI Generujące: opis -> obraz
 *  3. [reviewImage]         — AI Opisujące (jako recenzent): obraz -> ocena (JSON)
 *  4. [editImage]           — AI Generujące: poprzedni obraz + uwagi -> poprawiony obraz
 *
 * Używamy klasycznego, w pełni nadal wspieranego `generateContent` (a nie
 * nowszego Gemini Interactions API) — do tego prostego, jednorazowego
 * przepływu jest prostszy i stabilniejszy; stan "rozmowy" i tak trzymamy
 * sami w ChatViewModel.
 */
class GeminiApiClient(private val apiKey: String) {

    companion object {
        // Modele łatwo podmienić w jednym miejscu, gdyby Google zmienił
        // nazewnictwo albo gdybyś chciał inny balans jakość/koszt.
        private const val TEXT_MODEL = "gemini-2.5-flash"          // opis + recenzja (tekst i wizja)
        private const val IMAGE_MODEL = "gemini-3.1-flash-image"   // "Nano Banana 2" — generowanie/edycja obrazu
        // Wyższa jakość, wyższy koszt: "gemini-3-pro-image" ("Nano Banana Pro")

        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    // ---------- 1. AI Opisujące: tworzy bardzo dokładny opis obrazu ----------

    suspend fun generateDescription(userRequest: String): String = withContext(Dispatchers.IO) {
        val systemInstruction = """
            Jesteś ekspertem od tworzenia promptów do generatorów obrazów AI.
            Użytkownik poda Ci ogólny pomysł na obraz. Twoim zadaniem jest napisać
            bardzo dokładny, szczegółowy opis tego, co ma się znaleźć na obrazie:
            kompozycja, główny obiekt/bohater, tło i otoczenie, oświetlenie, pora
            dnia, paleta kolorów, styl artystyczny, perspektywa/kadr, nastrój oraz
            istotne detale. Pisz po polsku, w formie zwartego, płynnego opisu
            (nie w punktach), gotowego do przekazania modelowi generującemu obrazy.
            Nie zadawaj pytań i nie dodawaj żadnych komentarzy poza samym opisem.
        """.trimIndent()

        val body = JSONObject().apply {
            put("systemInstruction", textPart(systemInstruction).let { part ->
                JSONObject().put("parts", JSONArray().put(part))
            })
            put("contents", JSONArray().put(userContent(textPart(userRequest))))
            put("generationConfig", JSONObject().put("temperature", 0.8))
        }

        val response = executeRequest(TEXT_MODEL, body)
        extractText(response) ?: throw IllegalStateException("Model tekstowy nie zwrócił opisu.")
    }

    // ---------- 2. AI Generujące: tworzy obraz na podstawie opisu ----------

    suspend fun generateImage(description: String): ByteArray = withContext(Dispatchers.IO) {
        val prompt = """
            Wygeneruj wysokiej jakości obraz dokładnie na podstawie poniższego
            opisu. Odwzoruj każdy szczegół tak precyzyjnie, jak to możliwe:

            $description
        """.trimIndent()

        val body = JSONObject().apply {
            put("contents", JSONArray().put(userContent(textPart(prompt))))
            put(
                "generationConfig",
                JSONObject().put("responseModalities", JSONArray().put("TEXT").put("IMAGE"))
            )
        }

        val response = executeRequest(IMAGE_MODEL, body)
        extractImage(response) ?: throw IllegalStateException("Model graficzny nie zwrócił obrazu.")
    }

    // ---------- 3. AI Generujące: edytuje obraz na podstawie uwag recenzenta ----------

    suspend fun editImage(previousImage: ByteArray, fixInstructions: String): ByteArray =
        withContext(Dispatchers.IO) {
            val instructionText = """
                Popraw poniższy obraz zgodnie z poniższymi uwagami. Zmień WYŁĄCZNIE
                to, co jest opisane w uwagach — resztę obrazu (kompozycję, tło,
                pozostałe elementy, styl, kolory) pozostaw dokładnie taką, jaka jest:

                $fixInstructions
            """.trimIndent()

            val body = JSONObject().apply {
                put(
                    "contents",
                    JSONArray().put(
                        userContent(
                            textPart(instructionText),
                            imagePart(previousImage)
                        )
                    )
                )
                put(
                    "generationConfig",
                    JSONObject().put("responseModalities", JSONArray().put("TEXT").put("IMAGE"))
                )
            }

            val response = executeRequest(IMAGE_MODEL, body)
            extractImage(response)
                ?: throw IllegalStateException("Model graficzny nie zwrócił poprawionego obrazu.")
        }

    // ---------- 4. AI Opisujące: recenzuje wygenerowany obraz ----------

    data class ReviewResult(val isPerfect: Boolean, val feedback: String)

    suspend fun reviewImage(
        originalRequest: String,
        description: String,
        image: ByteArray
    ): ReviewResult = withContext(Dispatchers.IO) {
        val systemInstruction = """
            Jesteś rygorystycznym recenzentem obrazów wygenerowanych przez AI.
            Otrzymasz: (1) oryginalną prośbę użytkownika, (2) szczegółowy opis,
            który obraz miał odwzorować, oraz (3) sam obraz. Twoje zadanie:
            - Dokładnie porównaj obraz z opisem i z oczekiwaniami użytkownika:
              kompozycję, liczbę i wygląd obiektów, kolory, proporcje, ewentualny
              tekst na obrazie, styl oraz drobne błędy (np. deformacje).
            - Bądź wymagający i skrupulatny — aktywnie szukaj nawet drobnych
              niedoskonałości (proporcje, symetria, cienie/światło, detale tła,
              liczba palców/kończyn, spójność stylu). Nie zatwierdzaj obrazu
              "na wyrost", jeśli dostrzegasz cokolwiek do poprawienia.
            - Jeśli obraz w 100% spełnia opis i oczekiwania — ustaw isPerfect=true,
              a w polu feedback krótko potwierdź, co się udało.
            - Jeśli cokolwiek odbiega od opisu — ustaw isPerfect=false i w polu
              feedback napisz PRECYZYJNIE, co dokładnie trzeba poprawić, a na
              końcu wyraźnie zaznacz, że reszta obrazu ma pozostać bez zmian.
            Odpowiadaj wyłącznie po polsku, wyłącznie w formacie JSON zgodnym ze
            schematem.
        """.trimIndent()

        val userText = """
            Oryginalna prośba użytkownika: "$originalRequest"

            Docelowy opis obrazu: "$description"

            Oceń załączony obraz.
        """.trimIndent()

        val schema = JSONObject().apply {
            put("type", "OBJECT")
            put(
                "properties",
                JSONObject()
                    .put("isPerfect", JSONObject().put("type", "BOOLEAN"))
                    .put("feedback", JSONObject().put("type", "STRING"))
            )
            put("required", JSONArray().put("isPerfect").put("feedback"))
        }

        val body = JSONObject().apply {
            put("systemInstruction", JSONObject().put("parts", JSONArray().put(textPart(systemInstruction))))
            put("contents", JSONArray().put(userContent(textPart(userText), imagePart(image))))
            put(
                "generationConfig",
                JSONObject()
                    .put("responseMimeType", "application/json")
                    .put("responseSchema", schema)
            )
        }

        val response = executeRequest(TEXT_MODEL, body)
        val text = extractText(response)
            ?: throw IllegalStateException("Model recenzujący nie zwrócił odpowiedzi.")
        val json = JSONObject(text)
        ReviewResult(
            isPerfect = json.optBoolean("isPerfect", false),
            feedback = json.optString("feedback", "")
        )
    }

    // ---------- Budowanie fragmentów zapytania ----------

    private fun textPart(text: String): JSONObject = JSONObject().put("text", text)

    private fun imagePart(bytes: ByteArray): JSONObject = JSONObject().put(
        "inlineData",
        JSONObject()
            .put("mimeType", "image/png")
            .put("data", Base64.encodeToString(bytes, Base64.NO_WRAP))
    )

    private fun userContent(vararg parts: JSONObject): JSONObject {
        val partsArray = JSONArray()
        parts.forEach { partsArray.put(it) }
        return JSONObject().put("role", "user").put("parts", partsArray)
    }

    // ---------- Wspólna logika żądań i parsowania odpowiedzi ----------

    // Zapytanie zaimplementowane asynchronicznie (Call.enqueue), a nie
    // blokującym Call.execute() — dzięki temu, gdy użytkownik kliknie
    // „Stop” i coroutine zostanie anulowany, invokeOnCancellation od razu
    // przerywa faktyczne połączenie sieciowe (call.cancel()), zamiast
    // czekać, aż samo się zakończy.
    private suspend fun executeRequest(model: String, body: JSONObject): JSONObject {
        val requestBody = body.toString().toRequestBody(jsonMedia)
        val request = Request.Builder()
            .url("$BASE_URL/$model:generateContent")
            .addHeader("x-goog-api-key", apiKey)
            .post(requestBody)
            .build()

        val call = client.newCall(request)

        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }

            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isCancelled) return
                    continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    if (continuation.isCancelled) {
                        response.close()
                        return
                    }
                    response.use { resp ->
                        val bodyString = resp.body?.string().orEmpty()
                        if (!resp.isSuccessful) {
                            continuation.resumeWithException(
                                IllegalStateException("Błąd Gemini API (${resp.code}): ${bodyString.take(500)}")
                            )
                            return
                        }
                        try {
                            continuation.resume(JSONObject(bodyString))
                        } catch (e: Exception) {
                            continuation.resumeWithException(e)
                        }
                    }
                }
            })
        }
    }

    private fun extractText(response: JSONObject): String? {
        val parts = firstCandidateParts(response) ?: return null
        val sb = StringBuilder()
        for (i in 0 until parts.length()) {
            val part = parts.getJSONObject(i)
            if (part.has("text")) sb.append(part.getString("text"))
        }
        return sb.toString().trim().ifEmpty { null }
    }

    private fun extractImage(response: JSONObject): ByteArray? {
        val parts = firstCandidateParts(response) ?: return null
        for (i in 0 until parts.length()) {
            val part = parts.getJSONObject(i)
            if (part.has("inlineData")) {
                val data = part.getJSONObject("inlineData").getString("data")
                return Base64.decode(data, Base64.DEFAULT)
            }
        }
        return null
    }

    private fun firstCandidateParts(response: JSONObject): JSONArray? {
        val candidates = response.optJSONArray("candidates")
        if (candidates == null || candidates.length() == 0) {
            val blockReason = response.optJSONObject("promptFeedback")?.optString("blockReason")
            if (!blockReason.isNullOrBlank()) {
                throw IllegalStateException(
                    "Treść została zablokowana przez filtry bezpieczeństwa Gemini ($blockReason)."
                )
            }
            return null
        }
        val content = candidates.getJSONObject(0).optJSONObject("content") ?: return null
        return content.optJSONArray("parts")
    }
}
