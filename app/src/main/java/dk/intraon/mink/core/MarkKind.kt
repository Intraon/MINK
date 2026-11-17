package dk.intraon.mink.core

/** Typen af et mærke, som den står i loggens felt "kind". Ren Kotlin. */
object MarkKind {
    /** Et almindeligt, nummereret mærke. */
    const val PLAIN = "mark"
    /** Stedet er en mulig kilde. */
    const val POSSIBLE = "possible"
    /** Kilden er fundet her. */
    const val FOUND = "found"

    /** Typen ud af loggens felt. Et mærke uden type, eller med en type appen ikke kender, er et almindeligt mærke. */
    fun of(logged: String?): String = if (logged == POSSIBLE || logged == FOUND) logged else PLAIN
}
