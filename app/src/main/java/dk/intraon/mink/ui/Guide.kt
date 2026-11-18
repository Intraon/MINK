package dk.intraon.mink.ui

import android.app.AlertDialog
import android.content.Context

/** Den korte vejledning, der vises første gang appen åbnes, og bag ?-knappen. */
object Guide {
    private val TEXT = """
        1. Sæt RTL-SDR-donglen i telefonen, og tryk Start. Mangler driveren, henter appen den i Play Butik.

        2. Vandfaldet viser alt, hvad donglen hører: frekvensen vandret, tiden nedad. Det grønne felt er din egen kanal.

        3. Tryk på et signal i vandfaldet. Så er det målet (rød linje). Det store tal er målets styrke over støjen, og pilen og bippet fortæller, om du kommer tættere på.

        4. Gå eller kør rundt. Hver måling gemmes med position, og fanen Kort viser, hvor signalet er stærkest.

        KNAPPERNE
        ⚑ Mærke: sætter et mærke dér, hvor du står, som mulig kilde, fundet kilde eller almindeligt mærke.
        − og + Gain: donglens forstærkning. Appen skruer selv ned ved mætning, men aldrig op.
        Del: sender den tur, du ser. Ser du den aktuelle tur, er det turene fra i dag, som du ikke har delt endnu. Du krydser af, hvad der skal med: log, bølgeform, billeder og kort. Det hele kan pakkes i én zip-fil.
        ◀ Aktuel tur ▶ nederst i vandfaldet og på kortet: bladrer i tidligere ture. Tryk på navnet for at få listen over ture.
        Billede: gemmer det, du ser, i galleriet. I vandfaldet er det en figur af de seneste 30 sekunder, på kortet er det kortet.
        ⚙ Indstil: din kanal, det bånd der lyttes i, og resten.
        ?: denne vejledning.

        RETNINGSANTENNE
        Har du en retningsantenne, så slå den til under ⚙. Så kommer fanen Kamera frem. Hold telefonen i samme retning som antennen, som når du tager et billede – eller læg den fladt med toppen i antennens retning, så viser den en kompasrose i stedet for kameraet. Drej langsomt rundt: farvebåndet langs horisonten viser, hvor signalet er stærkest. Første gang beder appen dig kalibrere kompasset med 8-taller; gør det væk fra magneter og metal. "Tag pejling" gemmer et foto og en streg på kortet. To-tre pejlinger fra forskellige steder krydser hinanden ved senderen. Uden retningsantenne kan du holde antennen ind mod brystet og dreje rundt: kroppen skygger bagud.

        DONGLENS FREKVENS
        En billig dongle viser gerne 30–60 kHz forkert. Når appen har set trafik på din kanal, tilbyder den selv at rette det i en gul bjælke. Tryk på den.
        """.trimIndent()

    fun show(ctx: Context) {
        AlertDialog.Builder(ctx)
            .setTitle("Sådan bruger du MINK")
            .setMessage(TEXT)
            .setPositiveButton("OK", null)
            .show()
    }
}
