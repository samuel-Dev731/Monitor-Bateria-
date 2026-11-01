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
    private fun startBatteryMonitorWhenAllowed() {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001)
        } else {
            startBatteryMonitorService()
        }
    }

    private fun startBatteryMonitorService() {
        val intent = Intent(this, BatteryMonitorService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) startForegroundService(intent)
        else startService(intent)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001 && grantResults.firstOrNull() == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            startBatteryMonitorService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        startBatteryMonitorWhenAllowed()
        setContent {
            var readings by remember { mutableStateOf(BatteryReadings()) }
            var showWidgetHelp by remember { mutableStateOf(false) }
            var showWifiHelp by remember { mutableStateOf(false) }
            var alertThreshold by remember { mutableIntStateOf(getSharedPreferences("battery_alerts", MODE_PRIVATE).getInt("threshold", 20).coerceIn(5, 95)) }
            LaunchedEffect(Unit) {
                while (true) {
                    val intent = registerReceiver(null, filter)
                    readings = readBattery(this@MainActivity, intent)
                    delay(1000)
                }
            }
            BatteryDashboard(readings, alertThreshold, onThresholdChange = { value ->
                alertThreshold = value
                getSharedPreferences("battery_alerts", MODE_PRIVATE).edit().putInt("threshold", value).apply()
            }, onOpenWidgets = { showWidgetHelp = true }, onOpenWifi = { startActivity(Intent(this@MainActivity, WifiScanActivity::class.java)) })
            if (showWidgetHelp) {
                AlertDialog(
                    onDismissRequest = { showWidgetHelp = false },
                    title = { Text("Central de Widgets") },
                    text = {
                        Column {
                            Text("Escolha um dos três estilos de widget:")
                            Spacer(Modifier.height(8.dp))
                            Text("• Circular: porcentagem em destaque")
                            Text("• Clássico: porcentagem e barra de nível")
                            Text("• Minimalista: formato compacto")
                            Spacer(Modifier.height(10.dp))
                            Text("Para adicionar: pressione e segure um espaço vazio na tela inicial do celular, toque em Widgets, procure Monitor de Bateria Pro e arraste o modelo desejado para a tela.")
                            Spacer(Modifier.height(8.dp))
                            Text("Os widgets mostram a bateria deste celular. Com o monitoramento ativo, são atualizados junto das medições a cada segundo.")
                        }
                    },
                    confirmButton = { TextButton(onClick = { showWidgetHelp = false }) { Text("Entendi") } }
                )
            }
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
private fun BatteryDashboard(data: BatteryReadings, alertThreshold: Int, onThresholdChange: (Int) -> Unit, onOpenWidgets: () -> Unit, onOpenWifi: () -> Unit) {
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
                        Text("PRO • 1.1.0", color = Muted, fontSize = 9.sp, letterSpacing = 1.sp)
                    }
                    TextButton(onClick = onOpenWidgets, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                        Text("Widgets", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Box(Modifier.size(9.dp).clip(CircleShape).background(Accent))
                    Spacer(Modifier.width(6.dp))
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
                            BatteryGauge(data.level, data.status, alertThreshold)
                            Spacer(Modifier.height(12.dp))
                            Text("Alerta vibratório: ${alertThreshold}%", color = White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Slider(value = alertThreshold.toFloat(), onValueChange = { onThresholdChange(it.toInt()) }, valueRange = 5f..95f, steps = 17, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp))
                            Text("O celular vibra uma vez quando a bateria estiver descarregando e atingir esse limite.", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 24.dp))
                            Spacer(Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.clip(CircleShape).background(Color(0xFF1D3C2D)).padding(horizontal = 12.dp, vertical = 7.dp)) {
                                    Text(data.status.uppercase(Locale.getDefault()), color = Accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("Fonte: ${data.source}", color = Muted, fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    OutlinedButton(onClick = onOpenWifi, modifier = Modifier.fillMaxWidth()) { Text("Procurar redes Wi-Fi próximas") }
                    Spacer(Modifier.height(20.dp))
                    SectionHeader("MEDIÇÕES EM TEMPO REAL", "Atualização a cada 1 segundo")
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
private fun BatteryGauge(level: Int, status: String, alertThreshold: Int) {
    Box(Modifier.size(206.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 13.dp.toPx()
            drawArc(Color(0xFF29363D), -90f, 360f, false, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = StrokeCap.Round))
            drawArc(Accent, -90f, 360f * level.coerceIn(0, 100) / 100f, false, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = StrokeCap.Round))
            val angle = Math.toRadians((alertThreshold * 3.6 - 90.0))
            val radius = size.minDimension / 2f
            val markerRadius = radius - stroke / 2f
            val marker = Offset(size.width / 2f + kotlin.math.cos(angle).toFloat() * markerRadius, size.height / 2f + kotlin.math.sin(angle).toFloat() * markerRadius)
            drawCircle(Color(0xFFFFC857), radius = 7.dp.toPx(), center = marker)
            drawCircle(Color(0xFF090F14), radius = 3.dp.toPx(), center = marker)
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
