package com.samueldev.monitorbateriapro

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

class WifiScanActivity : ComponentActivity() {
    private var networks by mutableStateOf<List<String>>(emptyList())
    private var message by mutableStateOf("Toque em Procurar redes para iniciar uma busca.")
    private var receiverRegistered = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) scanNetworks() else message = "Permissão de localização negada. Ela é exigida pelo Android para mostrar redes Wi-Fi próximas."
    }

    private val scanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            val results = wifi.scanResults
            networks = results
                .filter { it.SSID.isNotBlank() }
                .sortedByDescending { it.level }
                .map { "${it.SSID}  •  ${it.level} dBm  •  ${it.frequency} MHz" }
                .distinct()
            message = if (networks.isEmpty()) {
                "Nenhuma rede encontrada. Verifique se o Wi-Fi e a localização estão ativados."
            } else {
                "Redes encontradas: ${networks.size}. A busca acontece somente quando você solicita."
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().padding(20.dp)) {
                        Text("Redes Wi-Fi próximas", style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(8.dp))
                        Text(message, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { beginScan() }, modifier = Modifier.fillMaxWidth()) {
                            Text("Procurar redes Wi-Fi")
                        }
                        Spacer(Modifier.height(12.dp))
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(networks) { network ->
                                Card(Modifier.fillMaxWidth()) {
                                    Text(network, modifier = Modifier.padding(14.dp))
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("Privacidade: o app consulta os nomes e sinais das redes no próprio celular. Não envia sua localização nem a lista de redes para a internet.")
                    }
                }
            }
        }
    }

    private fun beginScan() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            return
        }
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && !locationManager.isLocationEnabled) {
            message = "Ative os serviços de localização nas configurações do Android para procurar redes."
            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            return
        }
        scanNetworks()
    }

    @Suppress("DEPRECATION")
    private fun scanNetworks() {
        val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        if (!wifi.isWifiEnabled) {
            message = "Ative o Wi-Fi nas configurações rápidas e tente novamente."
            return
        }
        if (!receiverRegistered) {
            val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
            ContextCompat.registerReceiver(this, scanReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            receiverRegistered = true
        }
        message = "Procurando redes próximas…"
        val started = wifi.startScan()
        if (!started) {
            message = "O Android limitou a busca neste momento. Aguarde um pouco e tente novamente."
        }
    }

    override fun onDestroy() {
        if (receiverRegistered) {
            unregisterReceiver(scanReceiver)
            receiverRegistered = false
        }
        super.onDestroy()
    }
}
