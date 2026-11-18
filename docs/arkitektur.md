# Arkitektur

MINK er skrevet i Kotlin med Androids egne views. Der er ingen XML-layouts, ingen AndroidX og kun ét bibliotek: osmdroid til kortet. APK'en er ca. 3 MB.

Al logik, der kan regnes uden en telefon, ligger i ren Kotlin uden Android-importer og er dækket af unittests. Android-klasserne omkring den er tynde: de henter data fra sensorer og donglen, og de tegner.

## Dataflow

```
RTL-SDR ──USB──▶ "Rtl-sdr driver" (marto.rtl_tcp_andro)
                        │ rtl_tcp på 127.0.0.1:1234
                        ▼
              radio/RtlTcpClient ── blokke a 2048 IQ-samples
                        │
              measure/MeasureService
                 ├─ dsp/BurstDetector ──▶ målinger ──▶ radio/SourceTracker (hvilken sender, er den målet)
                 ├─ dsp/IqRing ─────────▶ trip/IqWav (bølgeformen)
                 ├─ radio/GainControl    (beskyttelse mod mætning)
                 ├─ trip/JsonlLog, measure/Uploader, measure/Beeper
                 └─ waterfall/Scope      (vandfaldets data)
                        │
                    core/Hub  (fælles tilstand + lyttere)
                        │
              ui/MainActivity ── waterfall/, map/, replay/ReplayPane, direction/CameraPane, ui/SettingsScreen
```

`MeasureService` er en foreground service af typen `location` med en delvis wake lock, så målingen fortsætter med skærmen slukket. Den er `START_NOT_STICKY`: dræber telefonen processen, kan målingen ikke genoptages uden brugeren, fordi driveren skal startes fra en skærm.

## Mapperne

Kildekoden ligger i `app/src/main/java/dk/intraon/mink/`, én mappe pr. komponent. Testene ligger i de samme mapper under `app/src/test/`.

| Mappe | Ansvar | Ren Kotlin | Android |
|---|---|---|---|
| `dsp/` | Signalbehandling: FFT, detektoren, ringlageret med rå samples | `Fft`, `BurstDetector`, `IqRing` | – |
| `radio/` | Donglen: rtl_tcp-klienten, gain, frekvensrettelse, hvilke sendere der høres | `GainControl`, `Calibrate`, `SourceTracker` | `RtlTcpClient` (kun sockets) |
| `measure/` | Selve målingen og det, den sætter i gang | `Beeper.toneHz`, `Outbox` | `MeasureService`, `Uploader`, `Beeper` |
| `core/` | Det, alle deler: tilstanden mellem måling og skærm, brugerens indstillinger og mærkernes typer | `MarkKind` | `Hub`, `Settings` |
| `waterfall/` | Vandfaldets data og visning, og målebilledet | `Scope`, `Figure`, `Pen` | `WaterfallView`, `CanvasPen`, `FigureExport` |
| `map/` | Kortlaget, turens spor, varmekortet og tidligere ture på kortet, og baggrundskortet til kortfilen | `Track`, `HeatGrid`, `TripPoints`, `VectorTile`, `BaseMap`, `TileSource` | `PointsOverlay`, `MapData`, `MapExport` |
| `replay/` | En tidligere tur som vandfald, genskabt fra log og bølgeform | `Replay`, `ClipSpectrum` | `IqClips`, `ReplayView`, `ReplayPane` |
| `direction/` | Retningsantenne: retninger, roset, kompas og kameraskærm | `Bearing`, `Rose`, `CompassCheck`, `HeadingFilter`, `FlatLayout`, `CameraWatch` | `Compass`, `CameraPane` |
| `trip/` | Turens filer: log, bølgeform, hvilke filer der hører til en tur, kortfilen og deling | `TripFiles`, `ZipPack`, `MapPage` | `JsonlLog`, `IqWav`, `Export`, `TripShare`, `MapPageExport` |
| `update/` | Opdatering fra GitHub-udgivelser | `Releases` | `Updater` |
| `ui/` | Hovedskærmen, indstillingerne, vejledningen, grafen og fælles farver | – | `MainActivity`, `SettingsScreen`, `Guide`, `LevelGraphView`, `Ui` |

Afhængighederne går nedad i dataflowet: `dsp/` kender ingen andre, `radio/` kender `dsp/` og vandfaldets data, og `ui/` kender alle. Skærmkomponenterne uden for `ui/` (`CameraPane`, `Updater`, `TripShare`) får `MainActivity` med ind for at kunne vise dialoger og læse indstillinger.

## Kortfilen

`MapPage` laver turenes data til kortfilen ud fra logfilernes linjer: målets målinger, stille perioder, mærker og pejlinger, med ture lige efter hinanden slået sammen. Pejlingens retning regnes som midten af den brede top i målingerne fra drejningen på stedet (`MapPage.lobe`). `BaseMap` vælger de vektorfliser, turene går igennem, og gør dem til baggrundskortets linjer og flader; `VectorTile` læser fliserne, og `TileSource` henter dem fra kilden i `gradle.properties` (`mink.mapTiles`). `MapPageExport` sætter det hele ind i skabelonen `app/src/main/assets/kort.html` sammen med pejlingernes fotos og gemmer filen. Skabelonen tegner selv kortet på et lærred og bruger intet bibliotek, så filen ikke henter noget, når den åbnes.

## Tråde

| Tråd | Arbejde |
|---|---|
| `rtl_tcp` (i `RtlTcpClient`) | Læser blokke fra driveren og kører detektoren på dem. Målinger meldes herfra |
| `rtl_tcp-cmd` (i `RtlTcpClient`) | Sender kommandoer til driveren (gain, PPM). Android afviser netværk på hovedtråden |
| `mink-tick` | Periodens opgørelse hvert 10. sekund, og GPS-opdateringer |
| `uploader` | Sender til webhooken |
| Hovedtråden | Skærmen. Den læser `Hub` og får besked gennem `Hub.changed()`. Kameraets tilbagekald kommer også her |

`RtlTcpClient.stop()` venter på læsetråden (højst et halvt sekund), så ingen blok er under behandling, når måletjenesten lukker log og bølgeform eller starter en ny forbindelse.

`Hub` er den eneste kanal mellem måling og skærm. Felterne er `@Volatile` eller trådsikre lister; skærmen ændrer aldrig måletilstanden direkte, men sender en handling til `MeasureService` (`MeasureService.send`, `sendGain`, `sendPpm`, `sendTarget`, `sendBearing`).

## Start af en tur

1. **Start** tjekker indstillingerne og beder om adgang til position og notifikationer.
2. Driveren startes med en `iqsrc://`-intent. Mangler den, tilbyder appen at hente den i Play Butik.
3. `MeasureService` forbinder til `127.0.0.1:1234`, læser headeren og sender sample rate (1.024.000), PPM, tunerfrekvens og manuel gain.
4. Trækkes donglen ud, viser skærmen en gul bjælke. Et tryk starter driveren igen og genforbinder uden at afbryde turen.

## Standardværdier

Frekvenser og lignende står ikke i koden. Værdierne ved første start ligger i `gradle.properties` (`mink.*`) og bygges ind som `BuildConfig`-felter:

| Nøgle | Betydning |
|---|---|
| `mink.channelMhz`, `mink.channelKhz` | Brugerens kanal (MeshCore EU/UK narrow: 869,618 MHz, 62,5 kHz) |
| `mink.bandLoMhz`, `mink.bandHiMhz` | Båndet, der lyttes i |
| `mink.signalKhz` | Signalbredden, hver tur starter med |
| `mink.targetMhz` | Fast mål; tom = målet vælges i vandfaldet |
| `mink.ppm` | PPM-korrektion |
| `mink.webhook` | Skal være tom i repoet |
| `mink.repo` | GitHub-repoet (`ejer/navn`), opdateringer hentes fra; tom = ingen opdatering |

## Byg, version og signering

- `tools/build.sh` og `tools/build.ps1` kører unittestene og bygger `dist/MINK.apk`. De henter selv Android SDK'et første gang.
- `minSdk 29`, `targetSdk 35`, `compileSdk 36`, JDK 17 eller nyere.
- `versionCode` er antal minutter siden 1. januar 2026, og `versionName` er `0.4.<versionCode>`. Hver build får derfor et højere nummer uden at nogen skal huske at tælle op.
- Signeringsnøglen ligger aldrig i repoet. Findes `keystore.properties` i repoets rod (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`), signeres der med den; ellers med Androids debug-nøgle.

## Udgivelse og opdatering

Workflowet **Udgiv** (`.github/workflows/release.yml`) startes i hånden. Det bygger med projektets nøgle fra repoets hemmeligheder og opretter en udgivelse med mærket `v<version>` og filen `MINK.apk`. Udgivelsens noter er første linje af hver commit siden forrige udgivelse; det er dem, appen viser, når den tilbyder opdateringen.

Appen finder nye versioner ud fra de to ting:

1. `update/Releases` læser `https://api.github.com/repos/<mink.repo>/releases/latest`, tager versionen fra mærket og finder filen, der hedder `MINK.apk`. Versioner sammenlignes led for led som tal.
2. `update/Updater` spørger højst hvert 12. time og aldrig under en måling. Er der en nyere version, vises noterne som ren tekst (`Releases.readable`), og filen hentes til appens cache-mappe og afleveres til Androids `PackageInstaller`. Systemets svar kommer tilbage til `MainActivity.onNewIntent`. Kan filen ikke hentes eller installeres, åbnes udgivelsen i browseren.

Workflowet **Byg og test** (`.github/workflows/ci.yml`) kører det samme byggescript for hver pull request.

## Test

`sh tools/build.sh` kører alle unittests (`./gradlew testReleaseUnitTest`). Testene dækker den rene Kotlin: detektoren, ringlageret, gain-reglerne, senderlisten, frekvensrettelsen, vandfaldets data, turens spor, varmekortet, genlæsningen af en tur, retningsregningen, tur-filerne og udgivelseslæseren. Der er én test pr. regel, appen skal overholde, ikke én pr. funktion.

Skærme, kamera, kompas, deling og installation kan kun prøves på en telefon.

Til en emulator uden dongle findes `tools/fakesrv.js`, en falsk rtl_tcp-server med støj, et smalt signal og LoRa-pakker. `tools/FigurePreview.java` tegner målebilledet på en pc.
