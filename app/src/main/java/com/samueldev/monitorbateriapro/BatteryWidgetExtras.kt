package com.samueldev.monitorbateriapro

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.util.Locale

/** Widgets adicionais adaptados dos modelos fornecidos para o Monitor de Bateria Pro. */
object BatteryWidgetExtrasUpdater {
    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val battery = context.registerReceiver(null, ExtrasIntentFilterCompat.batteryChanged())
        val temperatureTenths = battery?.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            ?: Int.MIN_VALUE
        val temperature = if (temperatureTenths != Int.MIN_VALUE && temperatureTenths > 0) {
            String.format(Locale.getDefault(), "%.1f°C", temperatureTenths / 10.0)
        } else {
            "--°C"
        }
        val temperatureStatus = when {
            temperatureTenths == Int.MIN_VALUE || temperatureTenths <= 0 -> "Leitura indisponível"
            temperatureTenths >= 450 -> "Temperatura elevada"
            temperatureTenths >= 400 -> "Atenção à temperatura"
            else -> "Temperatura da bateria"
        }
        val temperatureIds = manager.getAppWidgetIds(ComponentName(context, BatteryWidgetTemperature::class.java))
        if (temperatureIds.isNotEmpty()) {
            val views = RemoteViews(context.packageName, R.layout.widget_battery_temperature)
            views.setTextViewText(R.id.widget_temperature, temperature)
            views.setTextViewText(R.id.widget_status, temperatureStatus)
            manager.updateAppWidget(temperatureIds, views)
        }

        // O tempo de tela exige acesso especial a Usage Access. Não estimamos esse valor
        // nem solicitamos uma permissão automaticamente; o widget informa a limitação.
        val screenIds = manager.getAppWidgetIds(ComponentName(context, BatteryWidgetScreenTime::class.java))
        if (screenIds.isNotEmpty()) {
            val views = RemoteViews(context.packageName, R.layout.widget_battery_screen_time)
            views.setTextViewText(R.id.widget_screen_time, "—")
            views.setTextViewText(R.id.widget_status, "Consulta disponível no app")
            manager.updateAppWidget(screenIds, views)
        }
    }
}

class BatteryWidgetTemperature : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        BatteryWidgetExtrasUpdater.updateAll(context)
    }
}
class BatteryWidgetScreenTime : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        BatteryWidgetExtrasUpdater.updateAll(context)
    }
}

private object ExtrasIntentFilterCompat {
    fun batteryChanged() = android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED)
}
