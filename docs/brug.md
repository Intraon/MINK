# Brugervejledning

MINK er lavet til én opgave: du har en smal, forstyrrende sender et sted i området og ved ikke hvor. Du kender nogenlunde frekvensområdet, men ikke nødvendigvis kanalen, hvor længe den sender ad gangen, eller hvor tit.

Med en almindelig, rundstrålende antenne peger appen ikke på senderen. Den fortæller, om du kommer tættere på, og kortet viser, hvor signalet er stærkest. Det bringer dig fra "et sted i kommunen" ned til et par hundrede meter. Med en retningsantenne kan du desuden tage pejlinger.

## Hovedskærmen

| Felt | Viser |
|---|---|
| Statuslinje | Forbindelsen til donglen, tuner, gain, GPS-nøjagtighed og fart, og om loggen skrives |
| Det store tal | Målets styrke i dB over støjen ved seneste måling |
| Pilen | ▲ tættere på, ▼ længere væk, ▬ uændret, sammenlignet med de tre forrige målinger |
| Detaljelinjen | Niveau, frekvens, varighed, tid siden seneste måling, målt rytme og målt bredde |
| Mål-linjen | Målets frekvens og de andre sendere, der høres. Tryk for at vælge mål på en liste |
| Gul bjælke | Noget, du bør reagere på. Flere af dem kan trykkes |
| Grafen | Målets niveau over tid. Tryk skifter mellem de seneste 5 minutter og hele turen |
| Fanerne | **Vandfald**, **Kort** og, med retningsantenne, **Kamera**. **?** åbner vejledningen |

Knapperne nederst:

| Knap | Gør |
|---|---|
| **Start / Stop** | Starter og stopper en tur |
| **⚑ Mærke** | Sætter et mærke i loggen og på kortet dér, hvor du står. Du vælger, om stedet er en mulig kilde, en fundet kilde eller et almindeligt, nummereret mærke. Mærkerne står også på kortbillederne |
| **− og + Gain** | Flytter donglens forstærkning ét trin, med det samme |
| **Del** | Sender turens filer videre, se [Del](#del) |
| **⚙ Indstil** | Kanal, bånd og resten af indstillingerne |

## Vandfaldet og målet

Vandfaldet viser spektret af hele det lyttede bånd: frekvensen vandret, tiden nedad med det nyeste øverst, 30 sekunder tilbage. Farven er styrken over støjgulvet, fra mørkeblå over hvid til gul og rød. Det grønne felt er din egen kanal.

Tryk på et signal for at gøre det til **målet**. En rød linje markerer det, og fra da af er det kun målet, der styrer tallet, pilen, bippet, grafen og kortet. De andre sendere holdes adskilt efter frekvens og skrives stadig i loggen. Et tryk lidt ved siden af et signal rammer signalet; et tryk på et tomt sted flytter ikke et mål, der høres.

Målet skifter aldrig af sig selv, heller ikke når noget andet bliver stærkere. Intet mål er valgt på forhånd, og dit seneste valg huskes til næste tur.

Signalbredden følger målet: er målet bredere eller smallere end den bredde, appen står på, stiller den sig ind efter nogle målinger. Det gælder resten af turen.

## Styrke, pil og bip

Tallet er "dB over støjen". Bippet følger **niveauet**, som er styrken renset for gain: tonen flytter sig kun, når signalet gør, også når gain bliver skruet. Du kan derfor køre efter lyden uden at se på skærmen.

Appen skruer aldrig selv op for forstærkningen. Du vælger et gain, og det er loftet. Så ser et svagere signal også svagere ud, og tal og vandfald kan sammenlignes fra sted til sted. Det eneste automatiske er beskyttelsen mod mætning: helt tæt på en kraftig sender skruer appen ned og tilbage igen. Den kan slås fra under ⚙.

## Kortet

Hver måling gemmes med position, og turen tegnes som en linje, der skifter farve fra måling til måling: blå er svagest, rød er stærkest. Linjen er grå, hvor der blev lyttet uden at høre målet. De stykker er lige så vigtige: de viser, hvor senderen ikke kan høres. Den stærkeste måling har en hvid ring med sit niveau.

Går du samme vej to gange, ligger den stærkeste måling øverst. Din krop, en bygning eller et udfald kan kun gøre en måling svagere, så den stærkeste siger mest om afstanden. Holder målingen pause i mere end et halvt minut, begynder linjen forfra dér, hvor den kommer i gang igen.

- **▦** skifter til varmekortet: målingerne samles i felter, hvis størrelse følger zoom, fra 25 m og op. Det stærkeste felt får en hvid ramme med sit niveau. Der gættes ikke mellem felterne; hvor du ikke har været, er kortet tomt.
- **◉** følger din position.
- **◀ og ▶** bladrer gennem tidligere ture. Tryk på turens navn imellem dem for at få listen over ture.

## Se en tur igen

Listen **Ture** (tryk på turens navn på kortet, eller under ⚙) viser hver tur med dato, længde, antal målinger og det stærkeste niveau. Tryk på en tur for at åbne den: kortet viser turen, og fanen **Vandfald** viser turens vandfald i stedet for det levende. **Vælg flere …** lægger flere ture på kortet på én gang eller deler dem.

Vandfaldet er genskabt fra turens log. Hvert udbrud står på sin frekvens og sit klokkeslæt med sin bredde og varighed, for alle de sendere, der blev hørt. Støjen mellem udbruddene blev ikke gemt, så dér er vandfaldet tomt.

- Træk i vandfaldet for at gå frem og tilbage i tiden. Det seneste er øverst, som i det levende vandfald.
- Kurven øverst er målets niveau over hele turen. Tryk et sted på den for at springe derhen.
- Knappen med afstanden skifter mellem **overblik** (fem minutter på en skærm), **normal** (som det levende vandfald) og **tæt på** (et udbrud fylder en finger). I normal og tæt på vises udbruddets ægte signal fra turens bølgeform; uden bølgeform er hvert udbrud et felt farvet efter styrken.
- På kortet viser et sigtekorn, hvor du var på det klokkeslæt, vandfaldet står på.
- **✕** lukker turen og viser det levende vandfald igen.

## Donglens frekvens

En billig dongle viser gerne 30–60 kHz forkert ved 869 MHz, og fejlen flytter sig, mens donglen varmer op. Trafikken på din egen kanal ligger derimod præcist og er lige så bred som kanalen, så appen bruger den som lineal.

Ligger de brede udbrud ved siden af det grønne felt, kommer en gul bjælke: "Donglen viser 14 kHz for lavt – tryk her for at rette". Et tryk retter PPM-korrektionen med det samme, og målet og de andre sendere flytter med. Bjælken kan komme et par gange, til donglen er varm. Korrektionen kan også skrives i hånden under ⚙.

Lad donglen være tændt i 5–10 minutter, før du stoler på et frekvenstal.

## Retningsantenne og kamera

Slå **Jeg har en retningsantenne** til under ⚙. Så gemmer appen, hvilken vej telefonen peger, på hver måling, og fanen **Kamera** kommer frem. Det er dit ansvar, at telefon og antenne peger samme vej.

**Kalibrér først.** Appen beder dig bevæge telefonen i store 8-taller og følger selv med i bevægelsen og i, om magnetfeltet ligner Jordens. Gør det væk fra højttalere, magneter, bil og stål, og fra en antenne med magnetfod. Viser kompasset forkert, tager appen ingen pejling. Knappen **Kalibrér kompas** ligger øverst i kamerabilledet.

**På højkant**, som når du tager et billede, viser skærmen kameraet med et sigtekorn. Drej langsomt rundt: hver måling farver den retning, antennen pegede i, på et bånd langs horisonten, og en lille roset viser hele omgangen. Når fire retninger er målt, markerer appen den stærkeste.

**Fladt**, med kameraet mod jorden, slukker appen kameraet og viser en stor kompasrose. Så er det telefonens øverste kant, der skal pege samme vej som antennen. Nogle telefoners kompas er mest præcist sådan.

**Tag pejling** gemmer et foto med retning og styrke i galleriet og en streg på kortet. To-tre pejlinger fra forskellige steder krydser hinanden ved senderen.

Appen gemmer også selv en pejling for hvert sted, hvor du har drejet mindst en halv omgang rundt: stregen peger i den retning, rosen fandt stærkest, og fotoet er kamerabilledet fra stedets stærkeste måling. Det sker, når du går mere end 15 m videre, forlader fanen Kamera eller stopper målingen. Hvert sted gemmes én gang. **Mål** vælger målet uden at gå tilbage til vandfaldet.

Det skal du vide:

- Skarpheden bestemmes af antennen, ikke af kameraet. En lille Yagi ser omkring 60° ad gangen.
- Kompasset rammer ±10–15°. Tjek det mod et pejlemærke, du kender retningen til.
- Sender målet kun korte udbrud, får du én måling pr. udbrud. Drej langsomt.
- Rosetten begynder forfra, når du har flyttet dig 15 meter eller vælger et nyt mål.
- Uden retningsantenne kan du holde antennen ind mod brystet og dreje rundt: kroppen skygger bagud.

## Målebilledet

Knappen **Billede** i vandfaldet gemmer en figur af de seneste 30 sekunder i galleriet: vandfaldet med tiden vandret, din kanal markeret, og nedenunder styrken over tid for målet (eller for kanalen, hvis intet mål er valgt). Stiplede streger viser, hvor hvert udbrud begynder, så man ser, hvor tit det kommer.

På fanen **Kort** gemmer samme knap kortet, som du ser det: udsnittet, linjen eller varmekortet, pejlingerne og farveforklaringen, med dato, tur og mål skrevet under. Zoom og flyt kortet, til det viser det, du vil have med, før du trykker. Billedet følger med, når turen deles; bladrer du til en tidligere tur og tager billedet dér, hører det til den tur.

Vælger du **Det hele**, når du deler, laver appen desuden selv et kort over hele turen: udsnittet rummer alle steder, du har målt, og turen er tegnet som den farvede linje med mærker og pejlinger. Kortfliserne hentes fra nettet, så uden forbindelse kommer turen med på en tom baggrund. Der er ét sådant kort pr. tur; deler du turen igen, erstattes det.

## Del

Et tryk på **Del** åbner en dialog for den seneste tur. Du vælger, hvor meget der skal med: kun loggen, loggen og bølgeformen, eller det hele med et kort over turen og turens billeder. Dialogen viser, hvor meget hvert valg fylder. **Pak som én zip-fil** samler det valgte i én fil. Derefter åbner Androids delemenu. Appen siger, hvad den pakker, og hvis en fil ikke kan læses, nævner den filen og pakker resten. Appen skriver ingen tekst for dig, og valgene huskes til næste gang.

**Tidligere ture** i samme dialog, eller et langt tryk på Del, lader dig vælge en eller flere ældre ture, vise dem på kortet eller dele dem.

## Indstillinger

| Afsnit | Indstilling |
|---|---|
| Din kanal | Kanalens frekvens og bredde. Vises som det grønne felt og bruges til at rette donglens frekvens |
| Det der ledes efter | Båndet, der lyttes i (højst 880 kHz), signalets bredde, målets frekvens, tærsklen, korteste signal og frekvenser, der skal ignoreres |
| Modtager | PPM-korrektion, gain, og om appen må skrue ned ved mætning |
| Retningsantenne | Om retningen skal gemmes, og kamerafanen vises |
| Lyd og skærm | Bip, lydstyrke, og om skærmen holdes tændt |
| Bølgeform | Om IQ-udsnittet af hver måling gemmes |
| Upload | En webhook, der får målingerne løbende, se nedenfor |
| Om MINK | Version, opdatering, kildekode og licens |

Ændrer du gain, PPM eller retningsantenne, mens der måles, gælder det med det samme. Resten gælder fra næste Start.

Sæt båndet med luft omkring det, du leder efter. Driver målet hen mod kanten af det, appen søger i, siger en gul bjælke det.

## Home Assistant (valgfrit)

Appen kan sende målingerne til en webhook hvert 10. sekund. Loggen på telefonen er altid den fulde kilde. I Home Assistant er modtageren en trigger-baseret template-sensor:

```yaml
template:
  - triggers:
      - trigger: webhook
        webhook_id: mink_SKIFT_MIG_TIL_NOGET_TILFAELDIGT
        allowed_methods: [POST]
        local_only: false
    sensor:
      - name: "MINK pakke"
        unique_id: mink_pakke
        state: "{{ trigger.json.session }}-{{ trigger.json.seq }}"
        attributes:
          session: "{{ trigger.json.session }}"
          records: "{{ trigger.json.records }}"
```

Adressen i appen er så `https://<din-ha>/api/webhook/<webhook_id>`. Knappen **Test webhook** under ⚙ viser HTTP-koden. Formatet står i [logformat.md](logformat.md).

## Når noget driller

| Det sker | Gør sådan |
|---|---|
| "Rtl-sdr driver mangler" | Tryk på bjælken; appen åbner Play Butik |
| "Driveren svarer ikke" eller "Venter på driveren" | Tryk på bjælken. Har donglen været ude, starter driveren igen, og turen fortsætter |
| "Mætning" | En kraftig sender er tæt på, typisk din egen node. Skru ned med **−**, hvis beskyttelsen er slået fra |
| Målingen stopper, når skærmen slukker | Sæt batteribesparelse for MINK til "Ingen begrænsninger" (⚙ → Batteri-indstillinger for appen) |
| Målet høres ikke efter en pause | Donglen er drevet, mens den var slukket. Tryk på signalet i vandfaldet igen |
