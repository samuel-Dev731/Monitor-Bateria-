package com.samueldev.monitorbateriapro

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Locale

private data class BatteryReadings(
    val level: Int = 0, val tempC: Float? = null, val voltageMv: Int? = null,
    val currentMa: Int? = null, val chargeMah: Int? = null, val powerW: Float? = null,
    val status: String = "Verificando…", val source: String = "—", val health: String = "—"
)

class MainActivity : ComponentActivity() {
    private var latestIntent: Intent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        latestIntent = registerReceiver(null, filter)
        setContent {
            var readings by remember { mutableStateOf(BatteryReadings()) }
            LaunchedEffect(Unit) {
                while (true) {
                    latestIntent = registerReceiver(null, filter)
                    readings = readBattery(this@MainActivity, latestIntent)
                    delay(2000)
                }
            }
            BatteryDashboard(readings)
        }
    }
}

private fun readBattery(context: Context, intent: Intent?): BatteryReadings {
    val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    val i = intent ?: return BatteryReadings()
    val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
    val percent = if (level >= 0) (level * 100 / scale).coerceIn(0, 100) else 0
    val temp = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1).takeIf { it >= 0 }?.div(10f)
    val voltage = i.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1).takeIf { it > 0 }
    val currentUa = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
    val currentMa = currentUa.takeIf { it != Int.MIN_VALUE }?.div(1000)
    val chargeUah = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
    val chargeMah = chargeUah.takeIf { it != Int.MIN_VALUE && it >= 0 }?.div(1000)
    val power = if (voltage != null && currentMa != null) voltage.toFloat() * currentMa / 1_000_000f else null
    val status = when (i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
        BatteryManager.BATTERY_STATUS_CHARGING -> "Carregando"
        BatteryManager.BATTERY_STATUS_FULL -> "Carregada"
        BatteryManager.BATTERY_STATUS_DISCHARGING -> "Descarregando"
        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Não está carregando"
        else -> "Estado desconhecido"
    }
    val plug = when (i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)) {
        BatteryManager.BATTERY_PLUGGED_AC -> "Tomada"
        BatteryManager.BATTERY_PLUGGED_USB -> "USB"
        BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Sem fio"
        else -> "Desconectada"
    }
    val health = when (i.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)) {
        BatteryManager.BATTERY_HEALTH_GOOD -> "Normal"
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Superaquecida"
        BatteryManager.BATTERY_HEALTH_DEAD -> "Fim da vida útil"
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Sobretensão"
        BatteryManager.BATTERY_HEALTH_COLD -> "Fria"
        else -> "Não informado"
    }
    return BatteryReadings(percent, temp, voltage, currentMa, chargeMah, power, status, plug, health)
}

@Composable
private fun BatteryDashboard(data: BatteryReadings) {
    val bg = Color(0xFF07110C)
    val panel = Color(0xFF14221B)
    val green = Color(0xFF35E66B)
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = bg) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("MONITOR DE BATERIA PRO", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(20.dp))
                Box(Modifier.size(210.dp).background(panel, RoundedCornerShape(110.dp)), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🔋", fontSize = 38.sp)
                        Text("${data.level}%", color = green, fontSize = 52.sp, fontWeight = FontWeight.ExtraBold)
                        Text(data.status, color = Color.White, fontSize = 17.sp)
                    }
                }
                Spacer(Modifier.height(18.dp))
                Text("MEDIÇÕES EM TEMPO REAL", color = green, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                MetricCard("Corrente", data.currentMa?.let { "${it} mA" } ?: "Não disponível", "Carga (+) / descarga (−)")
                MetricCard("Potência", data.powerW?.let { String.format(Locale.getDefault(), "%.3f W", it) } ?: "Não disponível", "Calculada por tensão × corrente")
                MetricCard("Tensão", data.voltageMv?.let { "${it} mV" } ?: "Não disponível", "Voltagem instantânea")
                MetricCard("Carga restante", data.chargeMah?.let { "${it} mAh" } ?: "Não disponível", "Contador fornecido pelo aparelho")
                MetricCard("Temperatura", data.tempC?.let { String.format(Locale.getDefault(), "%.1f °C", it) } ?: "Não disponível", "Temperatura da bateria")
                Spacer(Modifier.height(12.dp))
                InfoCard("Fonte de energia", data.source)
                InfoCard("Saúde informada pelo Android", data.health)
                Spacer(Modifier.height(12.dp))
                Text("As leituras dependem do hardware e podem não estar disponíveis em todos os celulares.", color = Color.LightGray, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun MetricCard(title: String, value: String, detail: String) {
    Card(Modifier.fillMaxWidth().padding(vertical = 5.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF14221B)), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.LightGray, fontSize = 14.sp)
                Text(value, color = Color(0xFF35E66B), fontSize = 25.sp, fontWeight = FontWeight.Bold)
                Text(detail, color = Color.Gray, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, value: String) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF14221B))) {
        Column(Modifier.padding(14.dp)) {
            Text(title, color = Color.LightGray, fontSize = 13.sp)
            Text(value, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
