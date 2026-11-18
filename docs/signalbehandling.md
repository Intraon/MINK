# Signalbehandling

Dette dokument beskriver, hvordan MINK finder og måler signaler, og de regler, der følger af det. Koden ligger i `dsp/`, `radio/`, `waterfall/` og `direction/`.

## Detektoren (`dsp/BurstDetector`)

Detektoren kender hverken kanal, varighed eller gentagelsestid. Den finder smalle signaler i et bånd.

| Trin | Hvad |
|---|---|
| Tuner | Lægges 75 kHz over båndet (`tuneFor`), så DC-spidsen midt i spektret ikke rammer det, der lyttes efter. Er båndet for bredt til det, lægges den i midten. Trækker brugeren vandfaldet til siden, flyttes båndet i trin a 5 kHz (`BandShift`), tuneren følger med, og detektoren bygges om til det nye bånd. De første 300 ms efter flytningen giver ingen målinger, mens tuneren falder til ro |
| FFT | 2048 punkter (500 Hz pr. bin), én pr. blok (2 ms), rektangulært vindue. De seneste 4 blokke midles (8 ms) |
| Støjgulv | Medianen af alle brugbare bins (±450 kHz, uden ±3 kHz om DC), omregnet til middel (÷ ln 2). Genberegnes hver 8. blok |
| Søgning | Et vindue på signalbredden glider over båndet. Der er en kandidat, hvor vinduet ligger mindst tærsklen over gulvet. Vinduets midte må ligge op til 10 kHz uden for båndet (`EDGE_AIR_HZ`) |
| Smalt? | Naboområderne på hver side (samme bredde, en halv bredde væk) skal være mørke: signalets overskud skal være mindst 6 dB større end det kraftigste naboområdes |
| Bliver på sin frekvens | Mens et signal er i gang, søges kun ±½ signalbredde omkring startfrekvensen |
| Hysterese | Start ved tærsklen (standard 5 dB), fortsæt ned til det halve |
| Flere ad gangen | Op til 6 signaler på hver sin frekvens følges samtidig, så to sendere, der overlapper i tid, får hver sin måling |
| Slut | 12 ms uden signal. Kortere end "korteste signal" (40 ms), eller med signal i under 70 % af tiden: kasseres |
| Lange signaler | Meldes i stykker a 2 sekunder, mærket `cont`, så en konstant sender også giver løbende målinger |

**Hvorfor rektangulært vindue og midling.** Med et Hann-vindue ser en blok kun den midterste del af en LoRa-chirp, og en LoRa-pakke ligner så en række smalle signaler. Med rektangulært vindue over mindst ét symbol fylder chirpen sit spektrum jævnt ud, lyser naboområderne op og falder fra som bred. Det er sådan, LoRa fra MeshCore, Meshtastic og LoRaWAN sorteres fra.

**Hvorfor der søges uden for båndet.** Er signalet bredere end vinduet, rammer vinduet kun en del af det, og resten skal ligge i mellemrummet mellem vindue og naboområde. Støder vinduet mod båndets kant, lyser signalets egen kant naboområdet op, og målingen falder fra som bred. En dongle, der driver, kan flytte et signal få kHz ud over kanten.

For hver måling opgøres starttid, varighed, frekvens (effektvægtet midte), højeste og gennemsnitlig SNR, højeste dBFS (vinduets samlede effekt; 0 = fuldt udstyret bærebølge), støjgulv, andel mættede samples og bredde.

**Bredden** måles i hver blok, hvor vinduet ligger mindst 13 dB over støjen: det stykke af vinduet og naboområderne (i alt 4 × signalbredden), der rummer de midterste 90 % af effekten over støjen. Kun bins over 2 × gulvet tæller med. Målingens bredde er medianen over blokkene; kunne færre end 5 blokke måles, er der ingen bredde.

**Niveauet** er `højeste dBFS − gain`. Det er det tal, der sammenlignes over tid og mellem ture.

Blokkenes tid regnes ud fra antallet af samples siden forbindelsen og genforankres ved mere end 0,4 sekunders afvigelse, så buffering i forbindelsen ikke flytter en målings starttid.

### Signalbredden følger målet

Efter fem breddemålinger af målet sammenlignes målets bredde med den, detektoren står på (`BurstDetector.widthFor`). Er målet over 1,3 × eller under 0,6 × den, bygges detektoren om til målets bredde, afrundet til 0,5 kHz. Det sker højst hvert 30. sekund, venter til intet signal er i gang og gælder kun turen; næste tur starter på indstillingen igen. Bølgeformens sample rate ændres ikke undervejs.

## Sendere og mål (`radio/SourceTracker`)

Målinger med næsten samme frekvens (inden for 0,8 × signalbredden, mindst 3 kHz) hører til samme kilde, og kildens frekvens følger med målingerne, så en dongle, der driver, ikke deler én sender i flere. En kilde glemmes efter 2 minutter uden målinger.

Målet er den kilde, tal, pil, bip, graf og kort følger:

- Det vælges kun af brugeren: den kilde, der ligger inden for ±10 kHz af den valgte frekvens. Når den er fundet, følges den, selv om den driver længere væk.
- Det skifter aldrig af sig selv, heller ikke når en anden sender bliver stærkere.
- Uden et valgt mål viser tallet "—", og der gemmes ingen stille punkter. Vælges et nyt mål undervejs, starter graf, kort og trend forfra.

Alle kilders målinger skrives i loggen med kildens nummer; kun målets går videre til skærmen.

## Trend og friskhed

Trenden er `niveau − medianen af de tre forrige målingers niveau`. Pilen slår ud ved ±2 dB, eller ±3,5 dB, hvis gain er skiftet imellem, fordi tunerens gain-trin kun er nøjagtige til et par dB.

Appen måler selv den typiske tid mellem målingerne (medianen af de seneste). Tallet er hvidt, til 1,5 × den tid er gået, gråt til 3 ×, og derefter "—" med "intet hørt i N s". Før rytmen er kendt, bruges 4 sekunder.

## Gain (`radio/GainControl`)

Brugeren vælger et gain, og appen skruer aldrig højere op end det. "dB over støjen" og vandfaldet afhænger af gain, så skruede appen op, når signalet blev svagt, ville et fjernt signal se lige så tydeligt ud som et nært.

Bippet følger derfor niveauet og ikke "dB over støjen": 30 Hz pr. dB, fra 300 Hz ved −96 dB til 2400 Hz ved −26 dB (`Beeper.toneHz`).

Beskyttelsen mod mætning er det eneste automatiske. I en mættet modtager holder niveauet op med at stige, så uden den går retningen tabt tættest på senderen.

- Efter en måling: over 0,5 % mættede samples eller over −10 dBFS → 2 trin ned.
- I hver 10-sekunders periode: står gain under det valgte, og var periodens stærkeste måling under −45 dBFS → 1 trin op. To perioder uden målet → 1 trin op. Aldrig over det valgte.
- Der skrues aldrig op, hvis over 0,5 % af periodens samples var mættede. Så er en kraftig sender i nærheden, og mere gain gør det kun værre.
- Et automatisk skift venter, til intet signal er i gang, så skiftet ikke deler en måling i to. Niveauet regnes med det gain, donglen faktisk stod på.
- **−** og **+** flytter det valgte gain ét trin med det samme.

## Bølgeformen (`dsp/IqRing`, `trip/IqWav`)

De seneste ca. 2,2 sekunder rå IQ ligger i et ringlager (4,5 MB). Når en måling er slut, blandes blokkene fra 20 ms før til 10 ms efter ned, så den målte frekvens ligger i 0 Hz, og decimeres med et CIC-filter af 3. orden. Decimeringen vælges efter signalbredden, så udsnittets sample rate er mindst 2,6 × bredden (64 gange → 16 kHz ved 6 kHz bredde). Dæmpningen i kanten af det nyttige område er ca. 2,7 dB. Der gemmes højst 1 sekund pr. udsnit og kun første stykke af en lang udsendelse.

Udsnittene lægges efter hinanden i én stereo-WAV, og headeren skrives om efter hvert udsnit, så filen er gyldig, selv om appen lukkes midt i en tur. Formatet står i [logformat.md](logformat.md).

## Vandfaldet (`waterfall/Scope`)

Vandfaldet viser det lyttede bånd med 10 kHz luft i hver side: det samme stykke, detektoren søger i. Hver række er gennemsnittet af 20 blokke (40 ms), og der gemmes 750 rækker, altså 30 sekunder. Hver værdi er dB over støjgulvet, pakket i en byte fra −2 til +40 dB.

Spektret får et Hann-vindue i frekvensdomænet, så et kraftigt signal står skarpt uden brede skørter. Det gælder kun visningen; detektoren bruger det rektangulære vindue.

## Donglens frekvensfejl (`radio/Calibrate`)

En billig dongle viser 30–60 kHz forkert ved 869 MHz, og fejlen flytter sig med temperaturen. Trafikken på brugerens egen kanal ligger præcist og er lige så bred som kanalen.

`Calibrate.offsetHz` leder i vandfaldets rækker efter udbrud, der er 0,75–1,5 × kanalens bredde, inden for ±90 kHz af kanalen. Kanterne måles 12 dB under rækkens top, så de ligger lige langt fra midten, uanset hvor kraftigt signalet er. Fejlen er medianen af udbruddenes midte minus kanalens midte. Der skal mindst 8 rækker til, og de skal være enige (kvartilafstand under 6 kHz). Kun rækker efter den seneste PPM-ændring tæller, så en rettelse ikke lægges oven i sig selv.

En positiv PPM-korrektion flytter signalerne opad i vandfaldet. Den nye korrektion er derfor `gammel − fejl / frekvens × 10⁶`. Ændringen sendes til driveren med det samme, og målet og de andre kilder flyttes lige så meget (`SourceTracker.shift`), så målet stadig er målet. Ser appen samme fejl igen lige efter en rettelse, har driveren ikke taget den, og appen forbinder igen i stedet for at rette to gange.

## Retninger (`direction/`)

**Retningen** regnes ud fra magnetometeret og tyngdekraften (`Compass`), som i en kompas-app, og rettes til sand nord med misvisningen for stedet. Magnetfeltet udjævnes over 0,1 sekund og retningen over 0,25 sekund (`HeadingFilter`, regnet som en pil, så 359° og 1° giver 0°).

**Hvad der peges med** (`Bearing.pointing`): holdes telefonen på højkant, er retningen dér, bagkameraet ser hen. Ser kameraet mere end 55° ned, ligger telefonen fladt, og retningen er dér, dens øverste kant peger. Den skifter først tilbage ved 40°, så den ikke flakker ved grænsen. De to retninger er enige i overgangen.

**Er kompasset til at stole på** (`CompassCheck`): nej, hvis magnetometeret selv melder lav præcision, eller hvis det målte felt afviger mere end 30 % fra Jordens felt på stedet. Så mærkes målingerne `hdg_poor`, de farver ikke rosetten, og der kan ikke tages en pejling. Kalibreringen kræver, at telefonen har drejet en hel omgang om hver af sine tre akser.

**Rosetten** (`Rose`) deler kompasset i felter a 10°. Hver måling af målet lægges i det felt, antennen pegede i, og flere målinger i samme felt udjævnes. Den stærkeste retning er tyngdepunktet af det stærkeste felt og dets to naboer, vægtet efter effekt, og meldes først, når fire felter er målt. Rosetten begynder forfra, når man har flyttet sig 15 meter eller vælger nyt mål.

**I kamerabilledet** ligger en retning `tan(forskel) / tan(synsfelt / 2) × halv billedbredde` fra midten (`Bearing.offsetPx`). Synsfeltet regnes ud fra kameraets sensorstørrelse og brændvidde.

## Målebilledet (`waterfall/Figure`)

Figuren tegnes gennem grænsefladen `Pen`, så den samme kode tegner på Android (`CanvasPen`) og på en pc (`tools/FigurePreview.java`). Kurven nederst er middeleffekten i ét frekvensområde pr. række: målet ± 1,5 × signalbredden, eller brugerens kanal, hvis intet mål er valgt. Et udbrud begynder, hvor kurven kommer over 8 dB efter at have ligget under i mindst et halvt sekund.
