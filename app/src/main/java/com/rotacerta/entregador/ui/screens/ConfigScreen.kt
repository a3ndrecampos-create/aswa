package com.rotacerta.entregador.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.rotacerta.entregador.data.AppConfig
import com.rotacerta.entregador.data.NavApp
import com.rotacerta.entregador.data.RouteSortDirection
import com.rotacerta.entregador.data.SavedDestination
import com.rotacerta.entregador.network.CepResponse
import com.rotacerta.entregador.ui.theme.Muted
import com.rotacerta.entregador.viewmodel.RotaViewModel
import kotlinx.coroutines.launch

// Mostra "12345-678" na tela mas guarda só os dígitos — cursor não pula ao digitar.
private val DestCepVisualTransformation = VisualTransformation { text ->
    val digits = text.text
    val formatted = buildString {
        digits.forEachIndexed { i, c -> if (i == 5) append('-'); append(c) }
    }
    val offsetMapping = object : OffsetMapping {
        override fun originalToTransformed(offset: Int) = if (offset <= 5) offset else offset + 1
        override fun transformedToOriginal(offset: Int) = if (offset <= 5) offset else (offset - 1).coerceAtLeast(0)
    }
    TransformedText(AnnotatedString(formatted), offsetMapping)
}

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

        if (config.roundTrip) {
            val scope = rememberCoroutineScope()
            val homePermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted -> if (granted) viewModel.setHomeFromGps() }

            Text("Destino final (casa)", style = MaterialTheme.typography.labelLarge)
            Text(
                "Com \"ida e volta\" ativado, a rota otimizada termina no destino selecionado abaixo. " +
                    "Busque pelo CEP + número em vez de digitar o endereço todo — reduz erro de busca. " +
                    "Você pode salvar até ${AppConfig.MAX_SAVED_DESTINATIONS} destinos e escolher pra qual voltar.",
                style = MaterialTheme.typography.bodySmall,
                color = Muted
            )

            if (config.savedDestinations.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    config.savedDestinations.forEach { dest ->
                        val isSelected = config.homeAddress == dest.address &&
                            config.homeLat == dest.lat && config.homeLng == dest.lng
                        Row(
                            Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) com.rotacerta.entregador.ui.theme.Accent.copy(alpha = 0.12f) else com.rotacerta.entregador.ui.theme.Surface2)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = isSelected, onClick = { viewModel.selectSavedDestination(dest) })
                            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                                Text(dest.label, style = MaterialTheme.typography.labelLarge)
                                Text(dest.address, style = MaterialTheme.typography.bodySmall, color = Muted, maxLines = 2)
                            }
                            IconButton(onClick = { viewModel.removeSavedDestination(dest) }) {
                                Icon(Icons.Outlined.DeleteOutline, contentDescription = "Remover destino", tint = Muted)
                            }
                        }
                    }
                }
            }

            if (config.savedDestinations.size < AppConfig.MAX_SAVED_DESTINATIONS) {
                var destLabel by remember { mutableStateOf("") }
                var destCepDigits by remember { mutableStateOf("") }
                var destNumero by remember { mutableStateOf("") }
                var destCepData by remember { mutableStateOf<CepResponse?>(null) }
                var destCepHint by remember { mutableStateOf("") }

                Text(
                    "Adicionar destino (${config.savedDestinations.size}/${AppConfig.MAX_SAVED_DESTINATIONS})",
                    style = MaterialTheme.typography.labelMedium
                )
                OutlinedTextField(
                    value = destLabel,
                    onValueChange = { destLabel = it },
                    label = { Text("Nome (ex: Casa, Trabalho)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = destCepDigits,
                    onValueChange = { raw ->
                        val digits = raw.filter { it.isDigit() }.take(8)
                        destCepDigits = digits
                        if (digits.length == 8) {
                            destCepHint = "Buscando..."
                            scope.launch {
                                try {
                                    val data = viewModel.lookupCep(digits)
                                    destCepData = data
                                    destCepHint = "✓ ${data.logradouro}, ${data.bairro} — ${data.localidade}/${data.uf}"
                                } catch (e: Exception) {
                                    destCepData = null
                                    destCepHint = e.message ?: "CEP não encontrado"
                                }
                            }
                        } else {
                            destCepData = null
                            destCepHint = ""
                        }
                    },
                    label = { Text("CEP") },
                    visualTransformation = DestCepVisualTransformation,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = { if (destCepHint.isNotBlank()) Text(destCepHint) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = destNumero,
                    onValueChange = { destNumero = it },
                    label = { Text("Número") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        destCepData?.let { data ->
                            viewModel.addSavedDestination(destLabel, data, destNumero)
                            destLabel = ""; destCepDigits = ""; destNumero = ""
                            destCepData = null; destCepHint = ""
                        }
                    },
                    enabled = destCepData != null,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Salvar destino") }

                OutlinedButton(
                    onClick = { homePermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Usar minha localização atual como destino (GPS)") }
            }
        }

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
