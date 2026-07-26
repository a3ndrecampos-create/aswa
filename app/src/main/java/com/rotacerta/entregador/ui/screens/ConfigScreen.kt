package com.rotacerta.entregador.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.rotacerta.entregador.data.NavApp
import com.rotacerta.entregador.data.RouteSortDirection
import com.rotacerta.entregador.data.Vehicle
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

        Text("Ordem da rota", style = MaterialTheme.typography.labelLarge)
        SingleChoiceRow(
            options = listOf(
                "Mais próxima primeiro" to RouteSortDirection.NEAREST_FIRST,
                "Mais distante primeiro" to RouteSortDirection.FARTHEST_FIRST
            ),
            selected = config.sortDirection
        ) { viewModel.updateConfig { c -> c.copy(sortDirection = it) } }

        Text("Veículo", style = MaterialTheme.typography.labelLarge)
        SingleChoiceRow(
            options = Vehicle.entries.map { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } to it },
            selected = config.vehicle
        ) { viewModel.updateConfig { c -> c.copy(vehicle = it) } }

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

        Divider(Modifier.padding(vertical = 8.dp))

        OutlinedButton(
            onClick = { viewModel.clearAllDeliveries() },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = com.rotacerta.entregador.ui.theme.Danger),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Remover todas as entregas da rota") }
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
