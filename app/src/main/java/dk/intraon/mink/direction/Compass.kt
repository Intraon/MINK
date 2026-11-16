package dk.intraon.mink.direction

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import dk.intraon.mink.core.Hub

/**
 * Hvilken vej telefonen peger. Lægger retningen i [Hub], rettet fra magnetisk til sand nord, når positionen kendes.
 *
 * Retningen regnes ud fra magnetometeret og tyngdekraften, som i en kompas-app. Androids rotationsvektor bruges
 * ikke: den bygger på gyroskopet og kan drive mange grader, mens magnetometeret står stille.
 *
 * Flere kan lytte på én gang (tjenesten, mens der måles, og kameraskærmen); de skriver det samme.
 */
class Compass(ctx: Context, private val motion: Boolean = false) : SensorEventListener {
    private val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val mag: Sensor? = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    /** Tyngdekraftens retning. Har telefonen ingen færdig tyngdesensor, bruges accelerometeret, udjævnet. */
    private val grav: Sensor? = sm.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gravIsRaw = grav?.type == Sensor.TYPE_ACCELEROMETER
    /** Gyroskopet bruges kun til at se, om brugeren laver 8-tallerne ([motion]). */
    private val gyro: Sensor? = if (motion) sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE) else null
    private var gyroAt = 0L

    private val g = FloatArray(3)
    private val m = FloatArray(3)
    private var gAt = 0L
    private var mAt = 0L
    private val r = FloatArray(9)
    private val smooth = HeadingFilter()
    private var tiltSmooth = Double.NaN
    private var tiltAt = 0L
    private var declination = 0f
    private var declinationAt = 0L
    private var on = false

    /** False, hvis telefonen ikke har et kompas. */
    val available get() = mag != null && grav != null
    /** False, hvis bevægelsen ikke kan måles – så må kalibreringen gå efter tid. */
    val hasGyro get() = gyro != null

    fun start() {
        if (on || mag == null || grav == null) return
        gAt = 0; mAt = 0; gyroAt = 0
        on = sm.registerListener(this, mag, SensorManager.SENSOR_DELAY_GAME)
        sm.registerListener(this, grav, SensorManager.SENSOR_DELAY_GAME)
        if (gyro != null) sm.registerListener(this, gyro, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() {
        if (!on) return
        sm.unregisterListener(this); on = false
    }

    /** Udjævner en målt vektor over [tauS] sekunder; første måling tages, som den er. */
    private fun lowPass(dst: FloatArray, src: FloatArray, lastNs: Long, nowNs: Long, tauS: Double) {
        val dt = (nowNs - lastNs) / 1e9
        if (lastNs == 0L || dt < 0 || dt > 1) { for (i in 0..2) dst[i] = src[i]; return }
        val a = (1 - Math.exp(-dt / tauS)).toFloat()
        for (i in 0..2) dst[i] += a * (src[i] - dst[i])
    }

    override fun onSensorChanged(e: SensorEvent) {
        when (e.sensor.type) {
            Sensor.TYPE_GYROSCOPE -> {
                if (gyroAt != 0L) {
                    val dt = (e.timestamp - gyroAt) / 1e9
                    if (dt in 0.0..0.5) for (i in 0..2) Hub.turn[i] += Math.abs(e.values[i]) * dt
                }
                gyroAt = e.timestamp
            }
            Sensor.TYPE_GRAVITY, Sensor.TYPE_ACCELEROMETER -> {
                // tyngdesensoren er allerede rolig; et råt accelerometer ryster med hånden og skal dæmpes mere
                lowPass(g, e.values, gAt, e.timestamp, if (gravIsRaw) 0.25 else 0.05)
                gAt = e.timestamp
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                val x = e.values[0].toDouble(); val y = e.values[1].toDouble(); val z = e.values[2].toDouble()
                Hub.magUt = Math.sqrt(x * x + y * y + z * z)
                lowPass(m, e.values, mAt, e.timestamp, 0.1)
                mAt = e.timestamp
                if (gAt != 0L) update(e.timestamp)
            }
        }
    }

    /** Retningen ud fra tyngdekraft og magnetfelt: samme matrix, som en kompas-app regner med. */
    private fun update(ns: Long) {
        // false = tyngdekraft og magnetfelt peger samme vej (frit fald eller tæt på en magnetisk pol): ingen retning
        if (!SensorManager.getRotationMatrix(r, null, g, m)) return
        val pt = Bearing.pointing(r, Hub.flat)
        val az = smooth.add(pt.deg, ns / 1e9)
        val dt = (ns - tiltAt) / 1e9
        tiltSmooth = if (tiltSmooth.isNaN() || dt < 0 || dt > 1) pt.tilt else tiltSmooth + (1 - Math.exp(-dt / 0.25)) * (pt.tilt - tiltSmooth)
        tiltAt = ns
        val now = System.currentTimeMillis()
        if (now - declinationAt > 60_000) {
            Hub.location?.let {
                val field = GeomagneticField(it.latitude.toFloat(), it.longitude.toFloat(), it.altitude.toFloat(), now)
                declination = field.declination
                Hub.magExpectedUt = field.fieldStrength / 1000.0      // nT → µT
                declinationAt = now
            }
        }
        Hub.flat = pt.flat
        Hub.headingDeg = Bearing.norm(az + declination)
        Hub.tiltDeg = tiltSmooth
        Hub.headingAt = now
    }

    override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {
        if (s?.type == Sensor.TYPE_MAGNETIC_FIELD) Hub.magAccuracy = accuracy
    }
}
