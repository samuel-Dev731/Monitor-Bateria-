package com.samueldev.monitorbateriapro

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import java.util.Locale

class BatteryMonitorService : Service() {
    private val channelId = "battery_monitor_live"
    private val notificationId = 105
    private var lastLevel: Int? = null
    private var lastLevelChangeAt = 0L
    private var lastRatePerHour: Double? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val updateTask = object : Runnable {
        override fun run() {
            updateNotification()
            handler.postDelayed(this, 1_000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(notificationId, buildNotification())
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:BatteryMonitor").apply {
            setReferenceCounted(false)
            acquire()
        }
        handler.post(updateTask)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        updateNotification()
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(updateTask)
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun updateNotification() {
        BatteryWidgetUpdater.updateAll(this)
        val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val manager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = batteryIntent?.let {
            val raw = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
            if (raw >= 0) (raw * 100 / scale).coerceIn(0, 100) else null
        }
        val currentUa = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val currentMa = currentUa.takeIf { it != Int.MIN_VALUE }?.div(1000)
        val chargeUah = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        val chargeMah = chargeUah.takeIf { it != Int.MIN_VALUE && it >= 0 }?.div(1000)
        val voltageMv = batteryIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)?.takeIf { it > 0 }

        val now = System.currentTimeMillis()
        if (level != null) {
            val previous = lastLevel
            if (previous == null) {
                lastLevel = level
                lastLevelChangeAt = now
            } else if (level != previous) {
                val elapsed = now - lastLevelChangeAt
                if (elapsed > 0L) lastRatePerHour = (level - previous) * 3_600_000.0 / elapsed
                lastLevel = level
                lastLevelChangeAt = now
            }
        }

        val locale = Locale.getDefault()
        val rate = lastRatePerHour?.let { String.format(locale, "%+.1f", it) } ?: "Calculando"
        val lines = listOf(
            "mA: " + (currentMa?.toString() ?: "Indisponível"),
            "mAh: " + (chargeMah?.toString() ?: "Indisponível"),
            "mV: " + (voltageMv?.toString() ?: "Indisponível"),
            "%/h: " + rate
        )
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, buildNotification(lines))
    }

    private fun buildNotification(lines: List<String> = listOf("Obtendo medições da bateria…")): Notification {
        val openApp = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openApp,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val inbox = NotificationCompat.InboxStyle()
        lines.forEach { inbox.addLine(it) }
        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_battery_notification)
            .setContentTitle("Monitor de Bateria Pro")
            .setContentText(lines.joinToString("  •  "))
            .setStyle(inbox.setBigContentTitle("Monitor de Bateria Pro"))
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Medições da bateria",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Mostra as medições atuais da bateria continuamente."
                setShowBadge(false)
            }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }
}
