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
| **Del** | Sender den tur, du ser, eller dagens udelte ture videre, se [Del](#del) |
| **⚙ Indstil** | Kanal, bånd og resten af indstillingerne |

## Vandfaldet og målet

Vandfaldet viser spektret af hele det lyttede bånd: frekvensen vandret, tiden nedad med det nyeste øverst, 30 sekunder tilbage. Farven er styrken over støjgulvet, fra mørkeblå over hvid til gul og rød. Det grønne felt er din egen kanal.

Tryk på et signal for at gøre det til **målet**. En rød linje markerer det, og fra da af er det kun målet, der styrer tallet, pilen, bippet, grafen og kortet. De andre sendere holdes adskilt efter frekvens og skrives stadig i loggen. Et tryk lidt ved siden af et signal rammer signalet; et tryk på et tomt sted flytter ikke et mål, der høres.

**Træk til siden** i vandfaldet for at kigge på nabofrekvenserne. Båndet flytter med, og et blåt felt siger, hvad du ser. Ligger målet stadig i det, du ser, måles der videre. Ligger det udenfor, peger et mærke i kanten hen mod det, tallet og pilen holder pause, og der skrives ikke stille punkter imens. Sendere, du ser dér, gemmes i loggen som de andre. **Tilbage** fører hjem til turens bånd. Vælger du et mål under kigget, bliver båndet dér resten af turen, og det gemmes som båndet til næste tur.

Har du skrevet din egen kanal i indstillingerne, står der over vandfaldet, hvor optaget den er: **Din kanal er optaget 19 % af tiden af 2 sendere**. Tallet er den del af det seneste minut, hvor mindst én af de smalle sendere, appen hører, sendte inden for kanalen. Det siger, hvor meget en pakke på kanalen har at kæmpe med, uanset hvor senderne står. Appen tæller kun det, den kan høre: står du langt væk, eller peger en retningsantenne den anden vej, bliver tallet for lavt.

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

Nederst i vandfaldet og på kortet står, hvilken tur du ser: **◀ Aktuel tur ▶**. Pilene bladrer gennem tidligere ture, og et tryk på navnet åbner listen over ture. Navnet er blåt, når du ser en tidligere tur. Tallet, pilen og grafen øverst på skærmen viser altid den måling, der kører nu.

## Se en tur igen

Listen **Ture** (tryk på turens navn nederst i vandfaldet eller på kortet) viser hver tur med dato, længde, antal målinger og det stærkeste niveau. Øverst står **Aktuel tur**, som fører tilbage til den måling, der kører. Tryk på en tur for at åbne den: kortet viser turen, og fanen **Vandfald** viser turens vandfald i stedet for det levende.

Sæt flueben ved flere ture for at lægge dem på kortet på én gang (**Vis på kort**) eller dele dem (**Del …**). En tur, du har delt, har mærket **delt**. Mærket ligger på telefonen og ikke i loggen. Det sættes, når du har valgt en app i Androids delemenu, og det forsvinder igen, hvis turen er blevet længere siden.

Vandfaldet er genskabt fra turens log. Hvert udbrud står på sin frekvens og sit klokkeslæt med sin bredde og varighed, for alle de sendere, der blev hørt. Støjen mellem udbruddene blev ikke gemt, så dér er vandfaldet tomt.

- Træk i vandfaldet for at gå frem og tilbage i tiden. Det seneste er øverst, som i det levende vandfald.
- Kurven øverst er målets niveau over hele turen. Tryk et sted på den for at springe derhen.
- Knappen med afstanden skifter mellem **overblik** (fem minutter på en skærm), **normal** (som det levende vandfald) og **tæt på** (et udbrud fylder en finger). I normal og tæt på vises udbruddets ægte signal fra turens bølgeform; uden bølgeform er hvert udbrud et felt farvet efter styrken.
- På kortet viser et sigtekorn, hvor du var på det klokkeslæt, vandfaldet står på.
- **Billede** gemmer vandfaldet, som du ser det, med tur, klokkeslæt og mål skrevet under. Billedet følger med, når den tur deles.
- **✕** lukker turen og viser det levende vandfald igen.

## Donglens frekvens

En billig dongle viser gerne 30–60 kHz forkert ved 869 MHz, og fejlen flytter sig, mens donglen varmer op. Trafikken på din egen kanal ligger derimod præcist og er lige så bred som kanalen, så appen bruger den som lineal.

Ligger de brede udbrud ved siden af det grønne felt, kommer en gul bjælke: "Donglen viser 14 kHz for lavt – tryk her for at rette". Et tryk retter frekvensrettelsen (ppm) med det samme, og målet og de andre sendere flytter med. Bjælken kan komme et par gange, til donglen er varm. Korrektionen kan også skrives i hånden under ⚙.

Lad donglen være tændt i 5–10 minutter, før du stoler på et frekvenstal.

## Retningsantenne og kamera

Slå **Jeg har en retningsantenne** til under ⚙. Så gemmer appen, hvilken vej telefonen peger, på hver måling, og fanen **Kamera** kommer frem. Det er dit ansvar, at telefon og antenne peger samme vej.

**Kalibrér først.** Appen beder dig bevæge telefonen i store 8-taller og følger selv med i bevægelsen og i, om magnetfeltet ligner Jordens. Gør det væk fra højttalere, magneter, bil og stål, og fra en antenne med magnetfod. Viser kompasset forkert, tager appen ingen pejling. Knappen **Kalibrér kompas** ligger øverst i kamerabilledet.

**På højkant**, som når du tager et billede, viser skærmen kameraet med et sigtekorn. Drej langsomt rundt: hver måling farver den retning, antennen pegede i, på et bånd langs horisonten, og en lille roset viser hele omgangen. Når fire retninger er målt, markerer appen den stærkeste.

Ringen om rosetten viser, hvor langt omgangen er nået: den får et stykke for hver retning, der er målt nok i (mindst to udbrud), og ved siden af står, hvor der mangler, for eksempel **omgangen mangler 120° omkring NØ**. Når ringen er hel og grøn, står der **omgangen er færdig**. Så har stedet givet, hvad det kan, og du kan gå videre. Med et mål, der sender hvert fjerde sekund, tager en omgang to til tre minutter.

**Fladt**, med kameraet mod jorden, slukker appen kameraet og viser en stor kompasrose. Så er det telefonens øverste kant, der skal pege samme vej som antennen. Nogle telefoners kompas er mest præcist sådan.

**Tag pejling** gemmer et foto med retning og styrke i galleriet og en streg på kortet. To-tre pejlinger fra forskellige steder krydser hinanden ved senderen.

Retningen er midten af den brede top og ikke den ene kraftigste måling. På kortet ligger stregen i en kile på 10° til hver side, når stedet har en tydelig retning: så usikker er en pejling, især på afstand og gennem bebyggelse, og det er dér, hvor kilerne fra flere steder dækker hinanden, du skal lede. Kommer signalet næsten lige stærkt fra flere sider, skriver appen **flere retninger** og tegner dem stiplet uden kile. Det er genskin fra noget stort i nærheden, og så er stedet ikke til at pejle fra.

Appen gemmer også selv en pejling for hvert sted, hvor du har drejet mindst en halv omgang rundt: stregen peger mod midten af toppen, og fotoet er kamerabilledet fra den måling, der blev taget nærmest den retning. Det sker, så snart omgangen er færdig, og ellers når du går mere end 15 m videre, forlader fanen Kamera eller stopper målingen. Hvert sted gemmes én gang. **Mål** vælger målet uden at gå tilbage til vandfaldet.

Det skal du vide:

- Skarpheden bestemmes af antennen, ikke af kameraet. En lille Yagi ser omkring 60° ad gangen.
- Kompasset rammer ±10–15°. Tjek det mod et pejlemærke, du kender retningen til.
- Sender målet kun korte udbrud, får du én måling pr. udbrud. Drej langsomt.
- Rosetten begynder forfra, når du har flyttet dig 15 meter eller vælger et nyt mål.
- Uden retningsantenne kan du holde antennen ind mod brystet og dreje rundt: kroppen skygger bagud.

## Målebilledet

Knappen **Billede** står nederst til højre og siger, hvad den gemmer. I vandfaldet gemmer den en figur af de seneste 30 sekunder i galleriet: vandfaldet med tiden vandret, din kanal markeret, og nedenunder styrken over tid for målet (eller for kanalen, hvis intet mål er valgt). Stiplede streger viser, hvor hvert udbrud begynder, så man ser, hvor tit det kommer.

På fanen **Kort** gemmer samme knap kortet, som du ser det: udsnittet, linjen eller varmekortet, pejlingerne og farveforklaringen, med dato, tur og mål skrevet under. Zoom og flyt kortet, til det viser det, du vil have med, før du trykker. Billedet følger med, når turen deles; bladrer du til en tidligere tur og tager billedet dér, hører det til den tur.

Sætter du flueben ved **Et billede af kortet over turen**, når du deler, laver appen desuden selv et kort over hele turen: udsnittet rummer alle steder, du har målt, og turen er tegnet som den farvede linje med mærker og pejlinger. Kortfliserne hentes fra nettet, så uden forbindelse kommer turen med på en tom baggrund. Der er ét sådant kort pr. tur; deler du turen igen, erstattes det.

## Kortfilen

Sætter du flueben ved **Et kort, der kan åbnes i en browser**, når du deler, laver appen én HTML-fil med de valgte ture. Den åbnes i en browser på en pc eller en telefon og kan sendes som enhver anden fil.

- Sporet er en streg farvet efter målets niveau. Hvor ruten går samme vej flere gange, ligger den kraftigste måling øverst, og stille stykker er grå. Et tryk på stregen viser tid, niveau og sted.
- Hver pejling har en mærkat med nummer og retning. Et tryk åbner pejlingen med dens foto. Retningen er midten af den brede top fra drejningen på stedet. En tydelig retning er en lang streg i en kile på 10° til hver side, en svag er stiplet, og et sted med flere retninger viser dem alle stiplet.
- Ture lige efter hinanden vises som én, og hver tur kan slås til og fra. En skyder skjuler alt under et valgt niveau.

Baggrundskortet hentes, når filen laves, for de områder, turene går igennem, og lægges i selve filen sammen med fotoerne. Filen henter derfor intet, når den åbnes, og virker uden net. Har telefonen ikke net, når filen laves, bliver den lavet uden baggrund.

Filen hedder `MINK_kort_<tur>.html` og ligger i `Download/mink/`. Deler du de samme ture igen, erstattes den.

## Del

Et tryk på **Del** åbner en dialog. Ser du en tidligere tur, er det den, der deles. Ser du den aktuelle tur, er det alle ture fra i dag, som du ikke har delt endnu, og er de alle delt, er det den nyeste. Dialogen siger, hvilke ture det er, og **Flere ture …** åbner listen, hvis du vil vælge andre.

Du sætter flueben ved det, der skal med:

- **Loggen**
- **Bølgeformen**, hvis turen har en
- **Billederne fra turen**: målebilleder, kortbilleder og pejlingsfotos
- **Et billede af kortet over turen**, som appen laver ved delingen
- **Et kort, der kan åbnes i en browser**, se [Kortfilen](#kortfilen)

Dialogen viser, hvor meget filerne fylder. **Pak som én zip-fil** samler det valgte i én fil. Derefter åbner Androids delemenu. Appen siger, hvad den pakker, og hvis en fil ikke kan læses, nævner den filen og pakker resten. Appen skriver ingen tekst for dig, og valgene huskes til næste gang.

Når du har valgt en app i delemenuen, og loggen var med, får turene mærket **delt** i listen over ture.

## Indstillinger

| Afsnit | Indstilling |
|---|---|
| Kanal og bånd | Din kanals frekvens og bredde, og båndet, der lyttes i. En tegning viser båndet og din kanal i forhold til de 880 kHz, modtageren ser ad gangen |
| Målet | Målets frekvens og signalets bredde |
| Modtager | Gain, om appen må skrue ned ved mætning, og frekvensrettelsen (ppm) |
| Udstyr, lyd og skærm | Retningsantenne, bip, lydstyrke, og om skærmen holdes tændt |
| Det, der gemmes og sendes | Om bølgeformen af hver måling gemmes, og en webhook, der får målingerne løbende, se nedenfor |
| Finjustering | Tærsklen, korteste signal og frekvenser, der skal ignoreres. Foldet sammen, til du åbner den |
| Om MINK | Version, opdatering, batteri-indstillinger, kildekode og licens |

Et mærke ved overskriften eller feltet siger, om en ændring gælder med det samme eller fra næste Start. Gain, frekvensrettelse og retningsantenne gælder med det samme, også mens der måles. **Gem** og **Annullér** ligger fast i bunden.

Er båndet bredere end 880 kHz, står det under felterne med bredden i tal, og **Gem** er grå, til båndet passer. Knappen **Brug 880 kHz omkring din kanal** sætter det bredeste bånd, der kan lyttes på.

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
