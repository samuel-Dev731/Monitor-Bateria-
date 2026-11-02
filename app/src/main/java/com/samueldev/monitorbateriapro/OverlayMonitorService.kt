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
import java.io.File

class OverlayMonitorService : Service() {
    private lateinit var wm: WindowManager
    private var panel: TextView? = null
    private var expanded = false
    private val handler = Handler(Looper.getMainLooper())
    private var oldTotal = 0L
    private var oldIdle = 0L
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
        val notification = Notification.Builder(this, "overlay_monitor")
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
        val v=panel ?: return
        var total=0L; var idle=0L
        try {
            val p=File("/proc/stat").bufferedReader().use { it.readLine().trim().split(Regex("\\s+")) }
            val nums=p.drop(1).map { it.toLong() }; total=nums.sum(); idle=nums.getOrElse(3){0L}+nums.getOrElse(4){0L}
        } catch (_: Exception) {}
        val now=SystemClock.elapsedRealtime(); val proc=Process.getElapsedCpuTime()
        val dt=(now-oldTime).coerceAtLeast(1L); val dTotal=total-oldTotal; val dIdle=idle-oldIdle
        val cpu=if(oldTotal>0 && dTotal>0) ((dTotal-dIdle)*100.0/dTotal).coerceIn(0.0,100.0) else null
        val procPct=(((proc-oldProc).toDouble()/dt)*100.0).coerceIn(0.0,100.0)
        val bm=getSystemService(BATTERY_SERVICE) as BatteryManager
        val ma=bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW).takeIf { it!=Int.MIN_VALUE }?.div(1000)
        val lines= mutableListOf("MONITOR PRO • "+(if(expanded) "COMPLETO" else "MINI"), "Bateria: "+(ma?.toString()?.plus(" mA") ?: "N/D"))
        lines.add("CPU total: "+(cpu?.let { "%.0f%%".format(it) } ?: "N/D"))
        if(expanded) {
            lines.add("CPU deste app: %.1f%%".format(procPct))
            lines.add("Núcleos: leitura detalhada indisponível nesta versão")
            lines.add("GPU total: indisponível no Android deste aparelho")
            lines.add("GPU deste app: indisponível")
            lines.add("Toque para modo compacto")
        } else lines.add("Toque para detalhes")
        v.text=lines.joinToString("\n")
        oldTotal=total; oldIdle=idle; oldProc=proc; oldTime=now
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        panel?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        panel=null; super.onDestroy()
    }
    override fun onBind(intent: Intent?) = null
}