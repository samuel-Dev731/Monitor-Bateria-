package com.samueldev.monitorbateriapro

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Locale

private val Background = Color(0xFF090F14)
private val SurfaceCard = Color(0xFF151F27)
private val SurfaceSoft = Color(0xFF1B2931)
private val Accent = Color(0xFF45E58B)
private val Muted = Color(0xFF91A0A8)
private val White = Color(0xFFF3F7F5)

private data class BatteryReadings(
    val level: Int = 0, val tempC: Float? = null, val voltageMv: Int? = null,
    val currentMa: Int? = null, val chargeMah: Int? = null, val powerW: Float? = null,
    val status: String = "Verificando…", val source: String = "—", val health: String = "—"
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        setContent {
            var readings by remember { mutableStateOf(BatteryReadings()) }
            LaunchedEffect(Unit) {
                while (true) {
                    val intent = registerReceiver(null, filter)
                    readings = readBattery(this@MainActivity, intent)
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
        BatteryManager.BATTERY_STATUS_FULL -> "Carga completa"
        BatteryManager.BATTERY_STATUS_DISCHARGING -> "Descarregando"
        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Sem carregamento"
        else -> "Estado desconhecido"
    }
    val plug = when (i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)) {
        BatteryManager.BATTERY_PLUGGED_AC -> "Tomada"
        BatteryManager.BATTERY_PLUGGED_USB -> "USB"
        BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Sem fio"
        else -> "Desconectada"
    }
    val health = when (i.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)) {
        BatteryManager.BATTERY_HEALTH_GOOD -> "Boa"
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
    MaterialTheme(colorScheme = darkColorScheme(
        background = Background, surface = SurfaceCard, primary = Accent,
        onBackground = White, onSurface = White
    )) {
        Surface(Modifier.fillMaxSize(), color = Background) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF183A2B)), contentAlignment = Alignment.Center) {
                        Text("⚡", fontSize = 23.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("MONITOR DE BATERIA", color = White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp)
                        Text("PRO  •  PAINEL EM TEMPO REAL", color = Muted, fontSize = 9.sp, letterSpacing = 1.2.sp)
                    }
                    Box(Modifier.size(9.dp).clip(CircleShape).background(Accent))
                    Spacer(Modifier.width(6.dp))
                    Text("AO VIVO", color = Accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.height(6.dp))
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(26.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceCard)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("NÍVEL DA BATERIA", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                            Spacer(Modifier.height(16.dp))
                            BatteryGauge(data.level, data.status)
                            Spacer(Modifier.height(16.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.clip(CircleShape).background(Color(0xFF1D3C2D)).padding(horizontal = 12.dp, vertical = 7.dp)) {
                                    Text(data.status.uppercase(Locale.getDefault()), color = Accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("Fonte: ${data.source}", color = Muted, fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    SectionHeader("MEDIÇÕES EM TEMPO REAL", "Atualização a cada 2 segundos")
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MetricTile("Corrente", data.currentMa?.let { "$it mA" } ?: "Indisponível", "↯", Modifier.weight(1f))
                        MetricTile("Potência", data.powerW?.let { String.format(Locale.getDefault(), "%.2f W", it) } ?: "Indisponível", "ϟ", Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MetricTile("Tensão", data.voltageMv?.let { String.format(Locale.getDefault(), "%.2f V", it / 1000f) } ?: "Indisponível", "⌁", Modifier.weight(1f))
                        MetricTile("Carga restante", data.chargeMah?.let { "$it mAh" } ?: "Indisponível", "▣", Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MetricTile("Temperatura", data.tempC?.let { String.format(Locale.getDefault(), "%.1f °C", it) } ?: "Indisponível", "♨", Modifier.weight(1f))
                        MetricTile("Saúde", data.health, "♡", Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(18.dp))
                    SectionHeader("INFORMAÇÕES DA BATERIA", "Dados fornecidos pelo Android")
                    Spacer(Modifier.height(10.dp))
                    InfoRow("Estado", data.status)
                    InfoRow("Conexão", data.source)
                    InfoRow("Condição", data.health)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "As medições dependem dos sensores e do suporte do aparelho. O app não estima saúde ou autonomia sem dados confiáveis.",
                        color = Muted, fontSize = 11.sp, lineHeight = 16.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                    )
                    Spacer(Modifier.height(20.dp))
                }
                Row(
                    Modifier.fillMaxWidth().background(Color(0xFF111A20)).padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("●", color = Accent, fontSize = 10.sp)
                    Spacer(Modifier.width(7.dp))
                    Text("MONITORAMENTO ATIVO", color = Muted, fontSize = 10.sp, letterSpacing = 1.sp)
                }
            }
        }
    }
}

@Composable
private fun BatteryGauge(level: Int, status: String) {
    Box(Modifier.size(206.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 13.dp.toPx()
            drawArc(Color(0xFF29363D), -90f, 360f, false, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = StrokeCap.Round))
            drawArc(Accent, -90f, 360f * level.coerceIn(0, 100) / 100f, false, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🔋", fontSize = 29.sp)
            Text("$level%", color = Accent, fontSize = 49.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-2).sp)
            Text("CAPACIDADE", color = Muted, fontSize = 9.sp, letterSpacing = 1.5.sp)
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Text(title, color = White, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.7.sp, modifier = Modifier.weight(1f))
        Text(subtitle, color = Muted, fontSize = 9.sp)
    }
}

@Composable
private fun MetricTile(title: String, value: String, symbol: String, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = SurfaceCard)) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(27.dp).clip(RoundedCornerShape(9.dp)).background(SurfaceSoft), contentAlignment = Alignment.Center) {
                    Text(symbol, color = Accent, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(7.dp))
                Text(title, color = Muted, fontSize = 11.sp)
            }
            Spacer(Modifier.height(12.dp))
            Text(value, color = White, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
private fun InfoRow(title: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(13.dp)).background(SurfaceCard).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text(value, color = White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}
