# Bidrag til MINK

Bidrag er velkomne: fejlmeldinger, ønsker og kode.

*English: contributions are welcome. The user interface, comments and documentation are in Danish; issues and pull requests in English are fine.*

## Fejl og ønsker

Opret et issue med en af skabelonerne. Til en fejl hjælper det med appens version (⚙ → Om MINK), telefonmodel, Android-version, dongle og tuner, og turens log. Loggen rummer dine GPS-positioner; klip dem ud, hvis du ikke vil dele dem.

## Kode

1. Læs `docs/arkitektur.md`. Koden er delt i mapper efter komponent, og en ændring hører som regel hjemme i én af dem.
2. Byg og test med `sh tools/build.sh` (Linux og Mac) eller `.\tools\build.ps1` (Windows). Scriptet kører unittestene og bygger `dist/MINK.apk`.
3. Følg reglerne i [AGENTS.md](AGENTS.md). De er skrevet til AI-assistenter, men gælder alle:
   - Det, der kan regnes uden en telefon, ligger i ren Kotlin og har en test.
   - Kommentarer beskriver koden, ikke dens historie.
   - Der er en test for hver regel, appen skal overholde, og ikke flere end det.
4. Opret en pull request med skabelonen. Hold beskrivelsen teknisk, og skriv ærligt, hvad du har prøvet på en rigtig telefon.

### Commit-beskeder

Beslutningerne står i commit-beskederne, ikke i koden og ikke i pull requesten. Første linje siger, hvad der ændres, i bydeform. Kroppen siger, hvorfor: hvad der var galt, hvad der blev valgt, og hvad der blev valgt fra.

```
Regn kompasretningen ud fra magnetometer og tyngdekraft

Androids rotationsvektor bygger på gyroskopet og drev mange grader på
en testtelefon, mens telefonens egen kompas-app stod stille. Retningen
regnes nu som i en kompas-app. Magnetfeltet udjævnes over 0,1 s.
```

## Med en AI-assistent

Repoet er sat op til det:

| Fil | Bruges af |
|---|---|
| `AGENTS.md` | Alle assistenter, der læser den (Codex, Cursor, Claude Code m.fl.). Projektets regler |
| `CLAUDE.md` | Claude Code. Henter `AGENTS.md` ind |
| `.claude/commands/tjek.md` | Claude Code: `/tjek` gennemgår en ændring mod reglerne og skriver udkast til commit-besked og pull request |
| `.claude/settings.json` | Claude Code: må køre byg og test uden at spørge, og må ikke læse signeringsnøgler |
| `.github/copilot-instructions.md` | GitHub Copilot |

Du har ansvaret for det, assistenten skriver. Læs ændringen igennem, og prøv den på en telefon, hvis den rører skærm, kamera, kompas eller deling.

## Det må aldrig med i repoet

- Signeringsnøgler og `keystore.properties`.
- Egne adresser, webhooks, positioner og logfiler fra rigtige ture.

## Udgivelser

Udgivelser bygges af workflowet **Udgiv** og signeres med projektets nøgle, som kun vedligeholderen har. En APK bygget uden nøglen kan ikke installeres oven i en udgivet version.
