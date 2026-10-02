package com.rotacerta.entregador.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.rotacerta.entregador.ui.theme.Success

/**
 * Câmera ao vivo embutida no app (não abre o app de câmera do sistema), com visual de
 * scanner moderno: área ao redor da mira escurecida, cantos em L, linha de varredura
 * animada, e a mira fica verde quando um candidato está sendo confirmado. Chama onFrame
 * a cada imagem capturada — quem usa decide o que fazer com ela (OCR, código de barras...).
 *
 * @param found true quando já achou um candidato e está confirmando (mira fica verde,
 * passa mais confiança visual de que está funcionando em vez de ficar sempre neutro).
 */
@Composable
fun EmbeddedScannerDialog(
    instructions: String,
    onFrame: (ImageProxy) -> Unit,
    onDismiss: () -> Unit,
    found: Boolean = false
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val hasCameraPermission = ContextCompat.checkSelfPermission(
        context, Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED
    var permissionGranted by remember { mutableStateOf(hasCameraPermission) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionGranted = granted
        if (!granted) onDismiss()
    }

    DisposableEffect(Unit) {
        if (!permissionGranted) permissionLauncher.launch(Manifest.permission.CAMERA)
        onDispose { }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize()) {
            if (permissionGranted) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        val previewView = PreviewView(ctx)
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }
                            val analysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                            analysis.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { imageProxy ->
                                onFrame(imageProxy)
                            }
                            try {
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis
                                )
                            } catch (_: Exception) { }
                        }, ContextCompat.getMainExecutor(ctx))
                        previewView
                    }
                )

                val frameColor by animateColorAsState(
                    targetValue = if (found) Success else Color.White,
                    label = "frameColor"
                )

                val infiniteTransition = rememberInfiniteTransition(label = "scan")
                val scanProgress by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1800, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "scanProgress"
                )

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val rectWidth = size.width * 0.82f
                    val rectHeight = size.height * 0.2f
                    val left = (size.width - rectWidth) / 2
                    val top = (size.height - rectHeight) / 2
                    val cornerRadiusPx = 20f
                    val cutout = Path().apply {
                        addRoundRect(
                            androidx.compose.ui.geometry.RoundRect(
                                left, top, left + rectWidth, top + rectHeight,
                                CornerRadius(cornerRadiusPx, cornerRadiusPx)
                            )
                        )
                    }

                    // Escurece tudo ao redor da mira — só a área da mira fica com a
                    // câmera "limpa", exatamente como apps de scanner de QR code fazem.
                    clipPath(cutout, clipOp = ClipOp.Difference) {
                        drawRect(Color.Black.copy(alpha = 0.55f))
                    }

                    // Cantos em L (visual mais moderno que uma borda fechada inteira)
                    val cornerLen = rectWidth.coerceAtMost(rectHeight) * 0.18f
                    val strokeW = 6f
                    fun corner(x: Float, y: Float, dx: Int, dy: Int) {
                        drawLine(frameColor, Offset(x, y), Offset(x + cornerLen * dx, y), strokeWidth = strokeW)
                        drawLine(frameColor, Offset(x, y), Offset(x, y + cornerLen * dy), strokeWidth = strokeW)
                    }
                    corner(left, top, 1, 1)
                    corner(left + rectWidth, top, -1, 1)
                    corner(left, top + rectHeight, 1, -1)
                    corner(left + rectWidth, top + rectHeight, -1, -1)

                    // Linha de varredura animada subindo e descendo dentro da mira —
                    // reforça visualmente que o app está "trabalhando" o tempo todo.
                    if (!found) {
                        val scanY = top + rectHeight * scanProgress
                        drawLine(
                            brush = Brush.horizontalGradient(
                                listOf(Color.Transparent, frameColor.copy(alpha = 0.9f), Color.Transparent)
                            ),
                            start = Offset(left + 12f, scanY),
                            end = Offset(left + rectWidth - 12f, scanY),
                            strokeWidth = 4f
                        )
                    }
                }

                // Botão de fechar com fundo circular semi-transparente — fica legível
                // em cima de qualquer cena da câmera, clara ou escura.
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f))
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Fechar", tint = Color.White)
                }

                // Instrução em "pílula" com fundo, em vez de texto solto direto na
                // imagem da câmera (que fica ilegível dependendo da cena).
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 40.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = frameColor, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(
                        instructions,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
