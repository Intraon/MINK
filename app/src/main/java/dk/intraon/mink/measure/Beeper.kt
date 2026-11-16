package dk.intraon.mink.measure

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * Kort bip pr. måling. Tonen følger niveauet (dBFS − gain), ikke "dB over støjen": niveauet er det samme
 * ved ethvert gain, så tonen flytter sig kun, når signalet gør. 30 Hz pr. dB, fra 300 Hz ved −96 dB til
 * 2400 Hz ved −26 dB.
 */
object Beeper {
    private const val RATE = 22050
    const val LEVEL_LOW = -96.0
    const val LEVEL_SPAN = 70.0

    fun toneHz(level: Double): Double = 300.0 + (level - LEVEL_LOW).coerceIn(0.0, LEVEL_SPAN) * 30.0

    fun beep(level: Double, volume: Double) {
        if (volume <= 0.0) return
        val f = toneHz(level)
        val ms = 160
        val n = RATE * ms / 1000
        val pcm = ShortArray(n)
        val fade = RATE / 100
        val amp = volume.coerceIn(0.0, 1.0) * Short.MAX_VALUE
        for (i in 0 until n) {
            val env = min(1.0, min(i, n - 1 - i).toDouble() / fade)
            pcm[i] = (sin(2 * PI * f * i / RATE) * env * amp).toInt().toShort()
        }
        Thread {
            try {
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(n * 2)
                    .build()
                track.write(pcm, 0, n)
                track.play()
                Thread.sleep(ms + 80L)
                track.release()
            } catch (_: Exception) {}
        }.start()
    }
}
