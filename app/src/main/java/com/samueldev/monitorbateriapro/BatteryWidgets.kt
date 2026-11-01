package com.samueldev.monitorbateriapro

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/**
 * Widgets locais do Monitor de Bateria Pro.
 * Os valores são lidos diretamente do Android; não dependem de Firebase.
 */
object BatteryWidgetUpdater {
    fun updateAll(context: Context) {
        update(context, BatteryWidgetCircle::class.java, R.layout.widget_battery_circle)
        update(context, BatteryWidgetClassic::class.java, R.layout.widget_battery_classic)
        update(context, BatteryWidgetMinimal::class.java, R.layout.widget_battery_minimal)
    }

    private fun update(context: Context, provider: Class<out AppWidgetProvider>, layout: Int) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, provider))
        if (ids.isEmpty()) return

        val intent = context.registerReceiver(null, IntentFilterCompat.batteryChanged())
        val levelRaw = intent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = (intent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100) ?: 100).coerceAtLeast(1)
        val level = if (levelRaw >= 0) (levelRaw * 100 / scale).coerceIn(0, 100) else 0
        val status = when (intent?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)) {
            android.os.BatteryManager.BATTERY_STATUS_CHARGING -> "Carregando"
            android.os.BatteryManager.BATTERY_STATUS_FULL -> "Completa"
            android.os.BatteryManager.BATTERY_STATUS_DISCHARGING -> "Descarregando"
            else -> "Bateria"
        }
        val views = RemoteViews(context.packageName, layout)
        views.setTextViewText(R.id.widget_percent, "$level%")
        views.setTextViewText(R.id.widget_status, status)
        if (layout == R.layout.widget_battery_classic) {
            views.setProgressBar(R.id.widget_progress, 100, level, false)
        }
        manager.updateAppWidget(ids, views)
    }
}

private object IntentFilterCompat {
    fun batteryChanged() = android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED)
}

class BatteryWidgetCircle : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        BatteryWidgetUpdater.updateAll(context)
    }
}
class BatteryWidgetClassic : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        BatteryWidgetUpdater.updateAll(context)
    }
}
class BatteryWidgetMinimal : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        BatteryWidgetUpdater.updateAll(context)
    }
}
