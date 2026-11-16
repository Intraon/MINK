---
description: Gennemgå den aktuelle ændring mod projektets regler, før den bliver til en pull request
---

Gennemgå ændringen i arbejdskopien (`git diff` mod `main`, også nye filer) mod `AGENTS.md`. Ret ikke noget; aflevér en liste.

Gør det i denne rækkefølge:

1. Kør `sh tools/build.sh`. Fejler det, så stop og vis fejlen.
2. Gå ændringen igennem, og notér hvert brud på reglerne med fil og linje:
   - Logik, der kunne regnes uden Android, men ligger i en Android-klasse.
   - Faste frekvenser eller andre standardværdier i koden.
   - Kommentarer, der fortæller historie i stedet for at beskrive koden.
   - Nye biblioteker, nye tilladelser i manifestet eller ændringer i udgivelses-workflowet.
   - Ændringer i logformatet uden en tilsvarende ændring i `docs/logformat.md`.
   - Ændringer i brugerfladen uden en tilsvarende ændring i `docs/brug.md`.
   - Hemmeligheder, rigtige adresser eller rigtige positioner.
3. Se på testene: mangler en regel en test, eller er der tests, der gentager hinanden eller tester formatering?
4. Skriv et udkast til commit-besked (første linje i bydeform; kroppen siger hvorfor) og til pull request-teksten efter `.github/pull_request_template.md` (kun det tekniske).

Afslut med, hvad der ikke kan afgøres uden en telefon.
