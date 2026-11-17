# Logformat

En tur giver op til tre slags filer på telefonen:

| Fil | Indhold |
|---|---|
| `Download/mink/mink_<tid>.jsonl.txt` | Loggen: én JSON-post pr. linje |
| `Download/mink/mink_<tid>.iq.wav` | Bølgeformen: IQ-udsnit af hver måling |
| `Pictures/MINK/MINK_<tid>.png`, `MINK_kort_<tid>.png` (`MINK_kort_<tur>_<tid>.png` for et billede af en tidligere tur, `MINK_kort_<tur>.png` for kortet, der laves ved deling) og `MINK_pejling_<tid>.jpg` | Målebilleder, kortbilleder og pejlingsfotos |

`<tid>` er turens start som `yyyyMMdd_HHmmss`. Et billede hører til den tur, det er taget under: dets eget tidsstempel ligger efter turens start og før den næste turs.

Loggen har endelsen `.txt`, fordi Android ellers selv sætter den på, og fordi en tekstfil kan deles med alt. Mappen `Download/mink/` har en `.nomedia`-fil, så bølgeform-filerne ikke dukker op i telefonens musikafspiller.

## Fælles felter

| Felt | Betydning |
|---|---|
| `type` | Postens art, se nedenfor |
| `t` | Tidspunkt, ISO 8601 med millisekunder og tidszone |
| `lat`, `lon` | Position i grader. `null`, hvis seneste GPS-fix er ældre end 10 sekunder |
| `acc` | GPS-nøjagtighed i meter |
| `spd` | Fart i km/t |

## `meta`: turens første post

```json
{"type":"meta","session":"20261114_221500","version":"0.4.460700","device":"Pixel 8","ppm":-48,"tune":869825000,
 "band_lo":869380000,"band_hi":869750000,"signal_bw":6000,"chan":869618000,"chan_bw":62500,"target":null,
 "search_lo":869377250,"search_hi":869760250,"on_snr":5,"min_burst_ms":40,"rate":1024000,"nfft":2048,"tick_ms":10000,
 "iq_file":"mink_20261114_221500.iq.wav","iq_rate":16000}
```

| Felt | Betydning |
|---|---|
| `ppm` | PPM-korrektionen ved turens start |
| `tune` | Tunerens frekvens i Hz |
| `band_lo`, `band_hi` | Det lyttede bånd i Hz |
| `search_lo`, `search_hi` | Den laveste og højeste frekvens, et signals midte kan have og stadig blive fundet |
| `signal_bw` | Signalbredden ved turens start, i Hz |
| `chan`, `chan_bw` | Brugerens kanal: midte og bredde i Hz |
| `target` | Fast målfrekvens i Hz, eller `null` |
| `on_snr`, `min_burst_ms` | Tærskel i dB og korteste signal |
| `rate`, `nfft` | Sample rate og FFT-størrelse |
| `tick_ms` | Længden af en periode |
| `iq_file`, `iq_rate` | Bølgeform-filens navn og sample rate; `iq_file` er `null`, hvis bølgeformen ikke gemmes |

## `burst`: en måling

```json
{"type":"burst","t":"2026-11-14T22:15:04.120+01:00","lat":56.0371,"lon":10.4934,"acc":4,"spd":52.3,"brg":81,
 "f":"869.6125","src":1,"snr":27.4,"msnr":24.9,"dbfs":-50.3,"lvl":-71.0,"gain":20.7,"floor":-78.2,"dur":402,
 "clip":0.0,"bw":10000,"hdg":243.5,"tilt":-8.4,"tgt":true,"iq":0,"iqn":6141,"iqfc":869612500,"iqpre":20}
```

| Felt | Betydning |
|---|---|
| `brg` | GPS-kurs i grader, når telefonen bevæger sig |
| `f` | Frekvens i MHz (effektvægtet midte) |
| `src` | Kildens nummer. Samme sender har samme nummer inden for en tur |
| `tgt` | `true`, hvis kilden er målet |
| `snr`, `msnr` | Højeste og gennemsnitlig dB over støjen |
| `dbfs` | Højeste effekt i dBFS |
| `lvl` | Niveauet: `dbfs − gain`. Det tal, der sammenlignes over tid og mellem ture |
| `gain` | Det gain, donglen stod på |
| `floor` | Støjgulvet i dBFS |
| `dur` | Varighed i ms |
| `clip` | Andel mættede samples i procent |
| `bw` | Målt bredde i Hz. Mangler, hvis signalet var for svagt til at måle |
| `cont` | `true`, hvis posten er en del af en lang udsendelse |
| `hdg`, `tilt` | Med retningsantenne: retningen, telefonen pegede i (grader fra sand nord), og kameraets hældning over vandret |
| `hdg_poor` | `true`, hvis kompasset ikke var til at stole på |
| `iq`, `iqn`, `iqfc`, `iqpre` | Peger på målingens udsnit i bølgeform-filen, se nedenfor |

## `tick`: periodens opgørelse

Skrives hvert 10. sekund.

```json
{"type":"tick","t":"…","lat":…,"lon":…,"acc":…,"spd":…,"gain":20.7,"floor":-78.4,"bursts":3,"tb":2,"wide":41,"maxsnr":3.1,"clip":0.0,"frames":5000}
```

| Felt | Betydning |
|---|---|
| `bursts` | Målinger i perioden, alle kilder |
| `tb` | Målinger fra målet. `0` er et stille punkt. `null`, hvis intet mål var valgt, eller det blev valgt midt i perioden |
| `wide` | Blokke med kraftig, bredbåndet energi (fx LoRa) |
| `maxsnr` | Højeste dB over støjen i perioden, også under tærsklen |
| `frames` | Antal blokke i perioden |

## Øvrige poster

| `type` | Felter | Skrives, når |
|---|---|---|
| `mark` | `n`, `kind` | Brugeren sætter et mærke. `kind` er `mark` (almindeligt), `possible` (mulig kilde) eller `found` (kilde fundet). Mangler feltet, er mærket almindeligt |
| `target` | `f` (MHz eller `null`), `src` | Målet vælges om undervejs |
| `gain` | `from`, `to`, `why` | Gain skifter. `why` er `clip` eller `kraftigt` (beskyttelsen skruer ned), `tilbage` (op mod det valgte igen) eller `manuel` |
| `ppm` | `to` | PPM-korrektionen ændres. Frekvenserne i de følgende poster ligger på den nye plads |
| `signal_bw` | `from`, `to` (Hz) | Signalbredden stiller sig ind på målet |
| `bearing` | `hdg`, `tilt`, `lvl`, `snr`, `best`, `n`, `cmp`, `ut`, `flat`, `photo` | Brugeren tager en pejling |

Felterne i `bearing`:

| Felt | Betydning |
|---|---|
| `hdg`, `tilt` | Retning og hældning, som i `burst` |
| `lvl`, `snr` | Målets styrke, hvis det blev hørt inden for de seneste 6 sekunder; ellers `null` |
| `best` | Rosettens stærkeste retning på tidspunktet. Mangler, hvis færre end fire retninger var målt |
| `n` | Pejlingens nummer på turen |
| `cmp` | `ok` eller `poor`: om kompasset var til at stole på |
| `ut` | Det målte magnetfelt i µT |
| `flat` | `true`, hvis retningen er telefonens øverste kant; ellers er det kameraets |
| `photo` | Fotoets sti i galleriet |

## Bølgeform-filen

En WAV-fil i stereo, 16 bit: venstre kanal er I, højre er Q. Sample raten står i `meta` (`iq_rate`). Alle turens udsnit ligger efter hinanden, og hver `burst` peger på sit:

| Felt | Betydning |
|---|---|
| `iq` | Udsnittets start i filen, i samples |
| `iqn` | Antal samples |
| `iqfc` | Den frekvens i Hz, der ligger i 0 Hz i udsnittet |
| `iqpre` | Så mange ms af udsnittet ligger før målingens starttid `t` |

Skalaen er 256 pr. trin på donglens 8-bit ADC ved det gain, der står på målingen. Filen åbner i Audacity, inspectrum, SDR# og lignende. Af en lang udsendelse gemmes kun det første sekund.

## Webhook

Er der skrevet en webhook-adresse under ⚙, sendes posterne dertil med POST hvert 10. sekund, højst 30 poster pr. kald, med en kø på op til 5000:

```json
{"session":"20261114_221500","seq":42,"version":"0.4.460700","records":[ …samme poster som i loggen… ]}
```

En pakke beholder sit `seq`, til den er afleveret. Et hul i rækken betyder derfor tab, og et gensendt kald er ikke en ny pakke. Bølgeformen sendes ikke.
