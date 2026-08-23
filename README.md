# Kreator Obrazów AI — Android

Aplikacja czatowa (Kotlin + Jetpack Compose), w której **dwa modele AI (Gemini)**
w pełni automatycznie i w wielu widocznych rundach pracują nad obrazem, aż
będzie zgodny z Twoją prośbą pod każdym względem:

1. **AI Opisujące** zamienia Twoją krótką prośbę w bardzo szczegółowy opis obrazu.
2. **AI Generujące** (Gemini "Nano Banana 2") tworzy pierwszą wersję obrazu.
3. **AI Opisujące** ogląda wynik jako recenzent — aktywnie szuka niedoskonałości
   (proporcje, kolory, detale, zgodność z opisem…). Jeśli wszystko jest w porządku,
   zatwierdza obraz jako finalny. Jeśli nie — samo pisze precyzyjny "prompt
   korygujący": co dokładnie zmienić, a co zostawić bez żadnych zmian.
4. Ten prompt trafia **automatycznie** (bez Twojego udziału) do AI Generującego,
   które edytuje obraz. Wracamy do punktu 3.

Kroki 3–4 powtarzają się — każda runda widoczna jest w czacie jako osobne
wiadomości (analiza → nowa wersja obrazu → kolejna analiza…) — aż recenzent
zatwierdzi obraz albo zostanie osiągnięty limit `MAX_ITERATIONS = 8` rund
(zabezpieczenie przed nieskończoną pętlą i niepotrzebnymi kosztami API; do
zmiany w jednym miejscu — patrz niżej). Cały proces, wliczając kilka rund
generowania i analizy obrazu, może naturalnie zająć od kilkudziesięciu sekund
do kilku minut — i to jest zamierzone: masz czas obejrzeć, jak obie AI
faktycznie pracują nad obrazem krok po kroku, a nie tylko widzisz efekt końcowy.

---

## ⚠️ WAŻNE: Twój klucz API wyciekł

W wiadomości, w której poprosiłeś o tę aplikację, wkleiłeś swój prawdziwy klucz
API do Gemini. Taki klucz w każdej chwili może posłużyć komuś innemu do
generowania zapytań na Twój koszt.

**Zanim zrobisz cokolwiek innego:**
1. Wejdź na [Google AI Studio](https://aistudio.google.com/app/apikey) (lub
   Google Cloud Console, jeśli klucz pochodzi stamtąd).
2. Usuń / unieważnij (revoke) ten klucz.
3. Wygeneruj nowy i użyj **tylko jego**, zgodnie z instrukcją poniżej.

Nigdy nie wklejaj kluczy API w czatach, kodzie ani nie commituj ich do Gita — w
tym projekcie klucz celowo nie jest nigdzie zapisany na stałe w kodzie.

---

## Jak uruchomić

1. Rozpakuj to archiwum i otwórz folder `AIImageStudio` w Android Studio
   (File → Open). Ten projekt nie zawiera plików `gradlew` / `gradle-wrapper.jar`
   — przy pierwszym otwarciu Android Studio samo je uzupełni i może zaproponować
   aktualizację Gradle/AGP/Kotlina — zaakceptuj, to normalne.
2. W głównym folderze projektu utwórz plik `local.properties` (jeśli Android
   Studio go jeszcze nie utworzyło) na podstawie `local.properties.example` i
   wklej tam **nowy, świeżo wygenerowany** klucz:
   ```
   GEMINI_API_KEY=twoj_nowy_klucz
   ```
3. Zsynchronizuj Gradle i uruchom aplikację na telefonie/emulatorze z
   Androidem 10 (API 29) lub nowszym.

Klucz nigdy nie trafia do repozytorium — `local.properties` jest w `.gitignore`.
Pamiętaj jednak, że w zwykłej aplikacji mobilnej (bez własnego serwera-
pośrednika) klucz i tak jest częścią zbudowanego pliku APK i teoretycznie da
się go stamtąd wydobyć. Do prywatnego / testowego użytku to normalna praktyka,
ale przed publikacją w Sklepie Play warto przenieść wywołania do Gemini na
własny backend, żeby klucz nigdy nie trafiał na urządzenie użytkownika.

---

## Struktura projektu

```
app/src/main/java/com/example/aiimagestudio/
├── MainActivity.kt                        punkt wejścia — ustawia motyw i ChatScreen
├── data/GeminiApiClient.kt                cała komunikacja z Gemini API (REST, OkHttp)
├── model/ChatMessage.kt                   typy wiadomości w czacie (sealed class)
└── ui/
    ├── ChatViewModel.kt                   logika „2 AI” — cała pętla opis→obraz→recenzja→poprawka
    ├── ChatScreen.kt                      ekran czatu (lista wiadomości + pole input + status)
    ├── components/
    │   ├── MessageBubble.kt               dymki czatu (osobny wygląd dla każdego „aktora”)
    │   ├── InputBar.kt                    pole tekstowe + przycisk wysyłania
    │   └── FullScreenImageDialog.kt       podgląd obrazu na pełnym ekranie + zapis do galerii
    └── theme/                             kolory / typografia / motyw Material3
```

## Co łatwo zmienić

- `GeminiApiClient.TEXT_MODEL` / `IMAGE_MODEL` (stałe na górze pliku) — nazwy
  modeli Gemini. Domyślnie: `gemini-2.5-flash` (opis + recenzja) oraz
  `gemini-3.1-flash-image` / „Nano Banana 2” (generowanie/edycja obrazu — dla
  jeszcze wyższej jakości kosztem ceny i czasu możesz podmienić na
  `gemini-3-pro-image`, „Nano Banana Pro”).
- `ChatViewModel.MAX_ITERATIONS` — limit rund poprawek (domyślnie 8). Im
  wyższy, tym dłużej i dokładniej AI mogą pracować nad obrazem, zanim proces
  się zatrzyma.
- Rygor recenzenta — w `GeminiApiClient.reviewImage` instrukcja systemowa
  wprost każe AI aktywnie szukać niedoskonałości, zamiast zatwierdzać obraz
  „na wyrost”. Możesz ją złagodzić lub zaostrzyć.

## Dlaczego `generateContent`, a nie nowe Gemini Interactions API?

Google udostępniło w 2026 roku nowe, „agentowe” Interactions API. Do tego
przepływu (opis → obraz → recenzja → poprawka, w kółko) klasyczne, w pełni
nadal wspierane `generateContent` jest prostsze, stabilniejsze i lepiej
udokumentowane — a stan „rozmowy” i tak trzymamy sami, w `ChatViewModel`.
Gdybyś chciał w przyszłości dodać bardziej złożone zachowania agentowe, to
naturalny kierunek migracji.
