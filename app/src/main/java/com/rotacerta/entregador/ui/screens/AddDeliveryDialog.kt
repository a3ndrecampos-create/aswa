package com.rotacerta.entregador.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.rotacerta.entregador.data.Priority
import com.rotacerta.entregador.network.CepResponse
import com.rotacerta.entregador.viewmodel.RotaViewModel
import kotlinx.coroutines.launch

// Mostra "12345-678" na tela mas mantém só os dígitos guardados no estado —
// assim o cursor nunca "pula" quando o traço aparece/some enquanto digita.
private val CepVisualTransformation = VisualTransformation { text ->
    val digits = text.text
    val formatted = buildString {
        digits.forEachIndexed { i, c ->
            if (i == 5) append('-')
            append(c)
        }
    }
    val offsetMapping = object : OffsetMapping {
        override fun originalToTransformed(offset: Int) = if (offset <= 5) offset else offset + 1
        override fun transformedToOriginal(offset: Int) = if (offset <= 5) offset else (offset - 1).coerceAtLeast(0)
    }
    TransformedText(AnnotatedString(formatted), offsetMapping)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDeliveryDialog(viewModel: RotaViewModel, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val config by viewModel.config.collectAsState()

    var cepDigits by remember { mutableStateOf("") }
    var numero by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf(Priority.MEDIA) }
    var deadline by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    var cepData by remember { mutableStateOf<CepResponse?>(null) }
    var cepHint by remember { mutableStateOf("") }
    var trackingCode by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }

    fun cepFormatted() = if (cepDigits.length > 5) cepDigits.substring(0, 5) + "-" + cepDigits.substring(5) else cepDigits

    fun composeFromCep(data: CepResponse) {
        val street = data.logradouro.orEmpty() + if (numero.isNotBlank()) ", $numero" else ""
        address = listOfNotNull(street, data.bairro, data.localidade?.let { "$it - ${data.uf}" }, cepFormatted())
            .filter { it.isNotBlank() }.joinToString(", ")
    }

    // Busca o endereço a partir de um CEP (usado tanto na digitação manual quanto na foto)
    fun buscarPorCep(digits: String) {
        cepDigits = digits
        if (digits.length == 8) {
            cepHint = "Buscando endereço..."
            scope.launch {
                try {
                    val data = viewModel.lookupCep(digits)
                    cepData = data
                    composeFromCep(data)
                    cepHint = "✓ ${data.logradouro}, ${data.bairro} — ${data.localidade}/${data.uf}"
                } catch (e: Exception) {
                    cepHint = e.message ?: "CEP não encontrado"
                }
            }
        } else cepHint = ""
    }

    var showCepScanner by remember { mutableStateOf(false) }

    if (showCepScanner) {
        CepScannerDialog(
            onResult = { cepEncontrado, numeroEncontrado, trackingEncontrado ->
                showCepScanner = false
                numeroEncontrado?.let { numero = it }
                trackingEncontrado?.let { trackingCode = it }
                buscarPorCep(cepEncontrado.filter { it.isDigit() })
            },
            onDismiss = { showCepScanner = false }
        )
    }

    val xlsxLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { viewModel.importXlsx(it); onDismiss() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nova entrega") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showCepScanner = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp)); Text("Foto do CEP", style = MaterialTheme.typography.labelMedium)
                    }
                    OutlinedButton(onClick = {
                        xlsxLauncher.launch("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp)); Text("Importar .xlsx", style = MaterialTheme.typography.labelMedium)
                    }
                }
                if (trackingCode.isNotBlank()) {
                    Text(
                        "📦 Código de rastreio capturado: $trackingCode",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = cepDigits,
                    onValueChange = { raw -> buscarPorCep(raw.filter { it.isDigit() }.take(8)) },
                    label = { Text("CEP") },
                    visualTransformation = CepVisualTransformation,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = { if (cepHint.isNotBlank()) Text(cepHint) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = numero,
                    onValueChange = { numero = it; cepData?.let { d -> composeFromCep(d) } },
                    label = { Text("Número") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Endereço completo") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    minLines = 2
                )

                Text("Prioridade", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                    Priority.entries.forEach { p ->
                        FilterChip(
                            selected = priority == p,
                            onClick = { priority = p },
                            label = { Text(p.name.lowercase().replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }

                OutlinedTextField(
                    value = deadline, onValueChange = { deadline = it },
                    label = { Text("Prazo (HH:mm, opcional)") },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                )
                OutlinedTextField(
                    value = value, onValueChange = { value = it },
                    label = { Text("Valor da entrega (R$)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    placeholder = { Text(config.defaultValue.toString()) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = address.isNotBlank() && !submitting,
                onClick = {
                    submitting = true
                    viewModel.addDelivery(
                        address = address, priority = priority, deadline = deadline,
                        value = value.replace(",", ".").toDoubleOrNull() ?: config.defaultValue,
                        cepData = cepData, numero = numero, trackingCode = trackingCode
                    )
                    onDismiss()
                }
            ) { Text("Localizar e adicionar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
