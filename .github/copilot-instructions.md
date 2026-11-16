# Instruktioner til Copilot

Projektets regler står i `AGENTS.md` i repoets rod. Læs den først. Det vigtigste:

- Sproget er dansk: kommentarer, tekster til brugeren og dokumentation.
- Ren Kotlin og Androids egne views. Ingen AndroidX, ingen XML-layouts, ingen nye biblioteker.
- Én mappe pr. komponent under `app/src/main/java/dk/intraon/mink/`, én klasse pr. fil, testene i samme mappe under `app/src/test/`.
- Logik, der kan regnes uden en telefon, lægges i ren Kotlin uden Android-importer og får en unittest.
- Ingen faste frekvenser eller standardværdier i koden; de står i `gradle.properties`.
- Kommentarer beskriver, hvad koden gør, og hvorfor. Historien hører til i commit-beskeden.
- Pull requests holder sig til det tekniske: hvad der er ændret, hvor, og hvordan det er testet.
- Byg og test med `sh tools/build.sh`.
