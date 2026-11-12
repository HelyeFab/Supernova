package com.supernova.launcher.jp.reader

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.PowerManager

/**
 * Feeds the accelerometer to [BackTapDetector] while the screen is on and stops it
 * when the screen goes off, so the knock listener costs nothing in a pocket at night.
 * Callbacks arrive on the main thread.
 */
class BackTapSensor(
    private val context: Context,
    private val onDoubleTap: () -> Unit,
    private val onKnock: (Float) -> Unit = {},
) : SensorEventListener {
    private val main = Handler(Looper.getMainLooper())
    private val sensors = context.getSystemService(SensorManager::class.java)
    private val accelerometer: Sensor? = sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val detector = BackTapDetector(
        onDoubleTap = { main.post(onDoubleTap) },
        onKnock = { peak -> main.post { onKnock(peak) } },
    )
    private var thread: HandlerThread? = null
    private var listening = false
    private var started = false
    private var screenReceiverRegistered = false

    val available get() = accelerometer != null

    private val screen = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> listen()
                Intent.ACTION_SCREEN_OFF -> quiet()
            }
        }
    }

    /** [screenGated]: follow screen on/off (the service); false keeps listening (calibration). */
    fun start(threshold: Float, screenGated: Boolean = true) {
        detector.threshold = threshold
        if (started) return
        started = true
        if (screenGated) {
            context.registerReceiver(screen, IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_USER_PRESENT)
            })
            screenReceiverRegistered = true
            if (context.getSystemService(PowerManager::class.java)?.isInteractive != false) listen()
        } else listen()
    }

    fun stop() {
        if (!started) return
        started = false
        if (screenReceiverRegistered) { runCatching { context.unregisterReceiver(screen) }; screenReceiverRegistered = false }
        quiet()
        thread?.quitSafely(); thread = null
    }

    private fun listen() {
        val sensor = accelerometer ?: return
        if (listening || !started) return
        val worker = thread ?: HandlerThread("jp-backtap").also { it.start(); thread = it }
        detector.reset()
        // FASTEST (~200 Hz, more with HIGH_SAMPLING_RATE_SENSORS) keeps a 20 ms knock's peak.
        listening = sensors.registerListener(this, sensor, SensorManager.SENSOR_DELAY_FASTEST, Handler(worker.looper))
    }

    private fun quiet() {
        if (!listening) return
        sensors?.unregisterListener(this)
        listening = false
    }

    override fun onSensorChanged(event: SensorEvent) =
        detector.onSample(event.timestamp, event.values[0], event.values[1], event.values[2])

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
