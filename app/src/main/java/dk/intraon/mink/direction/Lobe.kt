package dk.intraon.mink.direction

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * Toppen i en omgang rundt på ét sted: den retning, signalet er stærkest i. Toppen er bred og ofte skæv, og den ene
 * kraftigste måling kan ligge 30 grader fra dens midte, så retningen er midten af toppen ([center]). [top] er
 * niveauet i toppen og [swing] forskellen ned til den svageste retning. [others] er andre toppe, der er næsten lige
 * så stærke: et tegn på genskin, hvor ingen af retningerne kan stoles på alene. Ren Kotlin.
 */
class Lobe(val center: Double, val top: Double, val swing: Double, val others: List<Double>) {
    /** Der er mere end én retning, signalet kommer lige stærkt fra. */
    val several: Boolean get() = others.isNotEmpty()

    companion object {
        /** Toppen måles i retninger med så mange graders mellemrum … */
        private const val STEP = 10
        /** … hver er medianen af målingerne inden for så mange grader til hver side … */
        private const val HALF = 20.0
        /** … og tæller først med så mange målinger, så en enkelt afviger ikke bliver til en top. */
        private const val MIN = 2
        /** Toppen er de sammenhængende retninger inden for så mange dB af den kraftigste. */
        private const val WITHIN_DB = 6.0
        /** En anden top tæller som en retning mere, når den når inden for så mange dB af den kraftigste. */
        private const val OTHER_DB = 5.0
        /** Så mange retninger i træk må mangle målinger, uden at en top regnes for afbrudt. */
        private const val GAP = 3

        /**
         * Toppen ud fra målingerne under en drejning: (retning i grader, niveau i dB). Retningerne i toppen vejes med
         * deres effekt. Null, hvis der er målt nok i færre end [minDirs] retninger; en pejling kræver en halv omgang.
         */
        fun of(samples: List<Pair<Double, Double>>, minDirs: Int = 360 / STEP / 2): Lobe? {
            val n = 360 / STEP
            val level = DoubleArray(n) { k ->
                val near = samples.filter { abs(Bearing.diff(it.first, (k * STEP).toDouble())) <= HALF }.map { it.second }.sorted()
                if (near.size < MIN) Double.NaN else if (near.size % 2 == 1) near[near.size / 2] else (near[near.size / 2 - 1] + near[near.size / 2]) / 2
            }
            val have = (0 until n).filter { !level[it].isNaN() }
            if (have.isEmpty() || have.size < minDirs) return null
            val top = have.maxByOrNull { level[it] }!!
            val limit = level[top] - WITHIN_DB
            fun inTop(k: Int) = !level[k].isNaN() && level[k] >= limit
            /**
             * De sammenhængende retninger omkring [start], hvor niveauet holder sig oppe. Retninger uden målinger
             * springes over, hvis hullet er højst [GAP] bredt: er der drejet hurtigt, ligger målingerne spredt.
             */
            fun run(start: Int, not: Set<Int>): Set<Int> {
                val run = HashSet<Int>().apply { add(start) }
                for (dir in intArrayOf(1, -1)) {
                    var k = start; var gap = 0
                    while (true) {
                        k = (k + dir + n) % n
                        if (k in run || k in not) break
                        if (level[k].isNaN()) { if (++gap > GAP) break else continue }
                        if (!inTop(k)) break
                        run.add(k); gap = 0
                    }
                }
                return run
            }
            // toppen: fra den kraftigste retning og ud til begge sider
            val main = run(top, emptySet())
            // andre toppe: retninger uden for toppen, som også holder sig oppe
            val others = ArrayList<Double>()
            val seen = HashSet<Int>(main)
            for (start in have.sortedByDescending { level[it] }) {
                if (start in seen || !inTop(start)) continue
                val other = run(start, seen)
                seen.addAll(other)
                if (level[start] >= level[top] - OTHER_DB) others.add(middle(other, level))
            }
            return Lobe(middle(main, level), level[top], level[top] - have.minOf { level[it] }, others.sorted())
        }

        /** Tyngdepunktet af retningerne [dirs], vejet med deres effekt. */
        private fun middle(dirs: Set<Int>, level: DoubleArray): Double {
            var x = 0.0; var y = 0.0
            for (k in dirs) {
                val w = 10.0.pow(level[k] / 10); val a = Math.toRadians((k * STEP).toDouble())
                x += w * sin(a); y += w * cos(a)
            }
            return Bearing.norm(Math.toDegrees(atan2(x, y)))
        }
    }
}
