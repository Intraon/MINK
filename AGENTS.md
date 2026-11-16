# AGENTS.md

Regler for AI-assistenter, der arbejder i dette repo. De gælder også for mennesker; se `CONTRIBUTING.md`.

*This file tells AI coding agents how to work in this repository. The project language is Danish: code comments, user-facing text and documentation are written in Danish. Reply to the contributor in their own language.*

## Projektet

MINK er en Android-app, der med en RTL-SDR finder smalle forstyrrelser i og omkring en LoRa-mesh-kanal. Ren Kotlin og Androids egne views; ingen AndroidX, ingen XML-layouts, ét bibliotek (osmdroid).

Læs disse, før du ændrer noget:

- `docs/arkitektur.md`: mapperne, dataflowet og trådene.
- `docs/signalbehandling.md`: hvordan signaler findes og måles. Reglerne dér er appens opførsel, ikke forslag.
- `docs/logformat.md`: logfilen. Andre programmer læser den.

## Kommandoer

| Opgave | Kommando |
|---|---|
| Byg og test (det eneste, der skal til før en pull request) | `sh tools/build.sh` |
| Kun unittests | `./gradlew testReleaseUnitTest` |
| Én testklasse | `./gradlew testReleaseUnitTest --tests 'dk.intraon.mink.dsp.BurstDetectorTest'` |
| Se målebilledet på en pc | se kommentaren øverst i `tools/FigurePreview.java` |

Bygget kræver JDK 17+ og henter selv Android SDK'et første gang. Der er ingen lint- eller formateringskommando; følg stilen i filen, du retter.

## Hvor ting hører hjemme

Én mappe pr. komponent under `app/src/main/java/dk/intraon/mink/`, og testene i samme mappe under `app/src/test/`:

| Mappe | Ansvar |
|---|---|
| `dsp/` | FFT, detektoren, ringlageret |
| `radio/` | rtl_tcp-klienten, gain, frekvensrettelse, senderlisten |
| `measure/` | Måletjenesten, upload, bip |
| `core/` | `Hub` (tilstanden mellem måling og skærm) og `Settings` |
| `waterfall/` | Vandfaldet og målebilledet |
| `map/` | Kortlag, varmekort, tidligere ture |
| `direction/` | Retninger, roset, kompas, kameraskærm |
| `trip/` | Log, bølgeform, tur-filer, deling |
| `update/` | Opdatering fra GitHub |
| `ui/` | Hovedskærm, indstillinger, vejledning |

Én klasse pr. fil. En ny funktion lægges i den komponent, den hører til; passer den ingen steder, får den sin egen mappe.

## Regler for koden

1. **Regn uden Android.** Alt, der kan regnes uden en telefon, lægges i ren Kotlin uden Android-importer, og Android-klassen omkring det holdes tynd. Se `direction/Bearing` og `direction/Compass` som mønster.
2. **Ingen nye biblioteker** uden at det er aftalt i et issue. APK'en skal blive ved med at være lille.
3. **Ingen faste frekvenser eller andre standardværdier i koden.** De står i `gradle.properties` (`mink.*`).
4. **Måling og skærm taler kun sammen gennem `Hub` og `MeasureService.send…`.** Skærmen ændrer aldrig måletilstanden direkte.
5. **Netværk og fil-IO sker ikke på hovedtråden.**
6. **Logformatet er en kontrakt.** Nye felter må tilføjes; eksisterende felter må ikke skifte betydning. Opdatér `docs/logformat.md` i samme ændring.
7. **Tekster til brugeren** skrives på dansk, i hele sætninger, uden fagord, brugeren ikke selv har set i appen.

## Kommentarer

En kommentar siger, hvad koden gør, og hvorfor den må være sådan. Den fortæller ikke, hvordan koden blev til.

- Ja: "Kanterne måles 12 dB under toppen: de ligger lige langt fra midten, uanset hvor kraftigt signalet er."
- Nej: "Rettet efter felttest", "tidligere brugte vi …", "nu gør vi i stedet …", datoer, navne og henvisninger til issues.

Historien hører til i commit-beskeden. Klasser og funktioner, hvis opgave ikke fremgår af navnet, har en KDoc-kommentar. Kommentarer er på dansk.

## Tests

- Skriv en test for hver regel, appen skal overholde, ikke for hver funktion. En testfunktion hedder det, den viser: `maaletSkifterAldrigTilEnStaerkereSender`.
- Test den rene Kotlin med realistiske data (se `BurstDetectorTest`, som bygger rigtige IQ-blokke). Test ikke formatering, gettere eller Androids egne klasser.
- Én test, der viser reglen, er nok. Tilføj kun en ekstra, hvis den fanger en fejl, den første ikke gør.
- En rettet fejl får en test, der fejlede før rettelsen.

## Commits og pull requests

- **Commit-beskeden bærer beslutningerne.** Første linje siger, hvad der ændres, i bydeform og under 72 tegn. Kroppen siger, hvorfor: hvad der var galt, hvad der blev valgt, og hvad der blev valgt fra.
- **Pull requesten holder sig til det tekniske**: hvilke komponenter der er berørt, hvad koden gør nu, og hvordan det er testet. Ingen baggrundshistorie, ingen fremgangsmåde og ingen navne. Brug skabelonen i `.github/pull_request_template.md`.
- Én ændring pr. pull request. En oprydning, der ikke hører til ændringen, får sin egen.
- Sig ærligt, hvad der er prøvet på en rigtig telefon. Skærme, kamera, kompas, deling og installation kan ikke testes automatisk.

## Det må aldrig med i repoet

- Signeringsnøgler, `keystore.properties` og adgangskoder.
- Rigtige webhook-adresser, positioner og logfiler fra rigtige ture. Testdata bruger opdigtede koordinater.
- Byggeresultater (`dist/`, `build/`).

## Når du er i tvivl

Stil spørgsmålet i stedet for at gætte, hvis en ændring berører logformatet, standardværdierne, tilladelser i manifestet eller udgivelses-workflowet. De fire ting har følger uden for koden.
