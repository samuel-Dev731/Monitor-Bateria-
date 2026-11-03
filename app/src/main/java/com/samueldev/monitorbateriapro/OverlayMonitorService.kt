package com.samueldev.monitorbateriapro

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.TextView
import androidx.core.app.NotificationCompat

class OverlayMonitorService : Service() {
    private lateinit var wm: WindowManager
    private var panel: TextView? = null
    private var expanded = false
    private val handler = Handler(Looper.getMainLooper())
    private var previousCounters: LongArray? = null
    private var oldProc = Process.getElapsedCpuTime()
    private var oldTime = SystemClock.elapsedRealtime()
    private val tick = object : Runnable {
        override fun run() { refresh(); handler.postDelayed(this, 1000L) }
    }

    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return }
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel("overlay_monitor", "Painel flutuante", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(this, "overlay_monitor")
            .setSmallIcon(R.drawable.ic_battery_notification).setContentTitle("Monitor de Bateria Pro")
            .setContentText("Painel flutuante ativo").setContentIntent(open).setOngoing(true).build()
        startForeground(106, notification)
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        showPanel()
        handler.post(tick)
    }

    private fun showPanel() {
        val v = TextView(this).apply {
            setTextColor(Color.WHITE); textSize = 12f; setPadding(16, 12, 16, 12)
            setBackgroundColor(Color.rgb(20, 35, 43))
            text = "MONITOR PRO\nToque: alternar modo • Segure: fechar"
            setOnClickListener { expanded = !expanded; refresh() }
            setOnLongClickListener { stopSelf(); true }
            setOnTouchListener(object : View.OnTouchListener {
                var sx=0; var sy=0; var tx=0f; var ty=0f
                override fun onTouch(view: View, event: MotionEvent): Boolean {
                    val lp = view.layoutParams as WindowManager.LayoutParams
                    when(event.action) {
                        MotionEvent.ACTION_DOWN -> { sx=lp.x; sy=lp.y; tx=event.rawX; ty=event.rawY }
                        MotionEvent.ACTION_MOVE -> { lp.x=sx+(event.rawX-tx).toInt(); lp.y=sy+(event.rawY-ty).toInt(); wm.updateViewLayout(view,lp); return true }
                    }
                    return false
                }
            })
        }
        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        val lp = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT)
        lp.gravity=Gravity.TOP or Gravity.START; lp.x=12; lp.y=140
        panel=v; wm.addView(v,lp)
    }

    private fun refresh() {
        val v = panel ?: return
        val counters = try { NativeCpuMonitor.readCounters() } catch (_: UnsatisfiedLinkError) { null }
        val previous = previousCounters
        var totalCpu: Double? = null
        val coreValues = mutableListOf<Double>()
        if (counters != null && previous != null && counters.size == previous.size && counters.size >= 2) {
            fun usage(index: Int): Double? {
                val base = index * 2
                val totalDelta = counters[base] - previous[base]
                val idleDelta = counters[base + 1] - previous[base + 1]
                if (totalDelta <= 0L || idleDelta < 0L || idleDelta > totalDelta) return null
                return ((totalDelta - idleDelta) * 100.0 / totalDelta).coerceIn(0.0, 100.0)
            }
            val dotnetPercent = try {
                NativeCpuMonitor.calculateDotNetPercent(
                    previous[0], previous[1], counters[0], counters[1]
                )
            } catch (_: UnsatisfiedLinkError) { -1.0 }
            totalCpu = if (dotnetPercent in 0.0..100.0) dotnetPercent else usage(0)
            for (index in 1 until counters.size / 2) usage(index)?.let { coreValues.add(it) }
        }
        previousCounters = counters
        val now = SystemClock.elapsedRealtime()
        val proc = Process.getElapsedCpuTime()
        val dt = (now - oldTime).coerceAtLeast(1L)
        val procPct = (((proc - oldProc).toDouble() / dt) * 100.0).coerceIn(0.0, 100.0)
        val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
        val ma = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            .takeIf { it != Int.MIN_VALUE }?.div(1000)
        val lines = mutableListOf(
            "MONITOR PRO • " + if (expanded) "COMPLETO" else "MINI",
            "Bateria: " + (ma?.toString()?.plus(" mA") ?: "N/D"),
            "CPU total: " + (totalCpu?.let { "%.1f%%".format(it) } ?: "Aguardando leitura")
        )
        if (expanded) {
            lines.add("CPU deste app: %.1f%%".format(procPct))
            lines.add("Núcleos lógicos: " + if (coreValues.isEmpty()) "N/D" else coreValues.size.toString())
            coreValues.forEachIndexed { index, value -> lines.add("CPU " + (index + 1) + ": %.1f%%".format(value)) }
            lines.add("GPU total: indisponível pelo driver/API")
            lines.add("GPU deste app: indisponível")
            lines.add("Toque para modo compacto")
        } else lines.add("Toque para detalhes")
        v.text = lines.joinToString("\n")
        oldProc = proc
        oldTime = now
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        panel?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        panel=null; super.onDestroy()
    }
    override fun onBind(intent: Intent?) = null
}