package com.rotacerta.entregador.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.rotacerta.entregador.data.NavApp
import com.rotacerta.entregador.data.RouteSortDirection
import com.rotacerta.entregador.viewmodel.RotaViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(viewModel: RotaViewModel) {
    val config by viewModel.config.collectAsState()
    var originText by remember(config.originAddress) { mutableStateOf(config.originAddress) }
    var valorText by remember(config.defaultValue) { mutableStateOf(config.defaultValue.toString()) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) viewModel.setOriginFromGps() }

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Ponto de partida", style = MaterialTheme.typography.labelLarge)
        OutlinedTextField(
            value = originText,
            onValueChange = { originText = it },
            label = { Text("Endereço de partida (opcional)") },
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                TextButton(onClick = { viewModel.setOrigin(originText) }) { Text("Definir") }
            }
        )
        OutlinedButton(
            onClick = { locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Usar minha localização atual (GPS)") }

        val context = LocalContext.current
        var overlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
        LaunchedEffect(Unit) { overlayGranted = Settings.canDrawOverlays(context) }

        Text("Aviso de chegada", style = MaterialTheme.typography.labelLarge)
        Text(
            "Pra mostrar \"Você chegou\" por cima de outros apps (tipo o Waze) enquanto você navega, " +
                "o Android exige uma permissão especial.",
            style = MaterialTheme.typography.bodySmall,
            color = com.rotacerta.entregador.ui.theme.Muted
        )
        OutlinedButton(
            onClick = {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
                context.startActivity(intent)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (overlayGranted) "✓ Sobreposição autorizada" else "Autorizar aparecer sobre outros apps")
        }

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        var batteryExempt by remember { mutableStateOf(powerManager.isIgnoringBatteryOptimizations(context.packageName)) }
        LaunchedEffect(Unit) { batteryExempt = powerManager.isIgnoringBatteryOptimizations(context.packageName) }

        Text(
            "Alguns celulares (Samsung, Xiaomi e outros) fecham o app sozinho em segundo plano " +
                "pra economizar bateria — isso impede o aviso de chegada de funcionar quando você " +
                "está usando outro app pra navegar. Libere abaixo pra evitar isso.",
            style = MaterialTheme.typography.bodySmall,
            color = com.rotacerta.entregador.ui.theme.Muted
        )
        OutlinedButton(
            onClick = {
                val intent = Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:${context.packageName}")
                )
                context.startActivity(intent)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (batteryExempt) "✓ Sem economia de bateria" else "Não deixar o sistema fechar o app")
        }

        Text("Ordem da rota", style = MaterialTheme.typography.labelLarge)
        SingleChoiceRow(
            options = listOf(
                "Mais próxima primeiro" to RouteSortDirection.NEAREST_FIRST,
                "Mais distante primeiro" to RouteSortDirection.FARTHEST_FIRST
            ),
            selected = config.sortDirection
        ) { viewModel.updateConfig { c -> c.copy(sortDirection = it) } }

        Text("Tipo de percurso", style = MaterialTheme.typography.labelLarge)
        SingleChoiceRow(
            options = listOf("Só ida" to false, "Ida e volta" to true),
            selected = config.roundTrip
        ) { viewModel.updateConfig { c -> c.copy(roundTrip = it) } }

        Text("App de navegação", style = MaterialTheme.typography.labelLarge)
        SingleChoiceRow(
            options = listOf("Google Maps" to NavApp.GOOGLE, "Waze" to NavApp.WAZE),
            selected = config.navApp
        ) { viewModel.updateConfig { c -> c.copy(navApp = it) } }

        Text("Valor padrão por entrega", style = MaterialTheme.typography.labelLarge)
        OutlinedTextField(
            value = valorText,
            onValueChange = {
                valorText = it
                it.replace(",", ".").toDoubleOrNull()?.let { v -> viewModel.updateConfig { c -> c.copy(defaultValue = v) } }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Notificações de entrega", style = MaterialTheme.typography.labelLarge)
            Switch(
                checked = config.notifications,
                onCheckedChange = { viewModel.updateConfig { c -> c.copy(notifications = it) } }
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Tema claro", style = MaterialTheme.typography.labelLarge)
            Switch(
                checked = config.lightTheme,
                onCheckedChange = { viewModel.updateConfig { c -> c.copy(lightTheme = it) } }
            )
        }

        Divider(Modifier.padding(vertical = 8.dp))

        var confirmResetHistory by remember { mutableStateOf(false) }

        OutlinedButton(
            onClick = { viewModel.clearAllDeliveries() },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = com.rotacerta.entregador.ui.theme.Danger),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Remover todas as entregas da rota") }

        OutlinedButton(
            onClick = { confirmResetHistory = true },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = com.rotacerta.entregador.ui.theme.Danger),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Resetar histórico de entregas") }

        if (confirmResetHistory) {
            AlertDialog(
                onDismissRequest = { confirmResetHistory = false },
                title = { Text("Resetar histórico?") },
                text = { Text("Isso apaga todo o histórico de entregas já concluídas. Essa ação não pode ser desfeita.") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.resetHistory()
                        confirmResetHistory = false
                    }) { Text("Apagar", color = com.rotacerta.entregador.ui.theme.Danger) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmResetHistory = false }) { Text("Cancelar") }
                }
            )
        }
    }
}

@Composable
private fun <T> SingleChoiceRow(options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (label, value) ->
            FilterChip(selected = value == selected, onClick = { onSelect(value) }, label = { Text(label) })
        }
    }
}
