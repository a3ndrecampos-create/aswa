package com.rotacerta.entregador

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.rotacerta.entregador.billing.BillingManager
import com.rotacerta.entregador.billing.PremiumAccessManager
import com.rotacerta.entregador.billing.TrialManager
import com.rotacerta.entregador.ui.screens.*
import com.rotacerta.entregador.ui.theme.RotaCertaTheme
import com.rotacerta.entregador.viewmodel.RotaViewModel

private data class Tab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val TABS = listOf(
    Tab("rota", "Rota", Icons.Default.List),
    Tab("mapa", "Mapa", Icons.Default.Map),
    Tab("historico", "Histórico", Icons.Default.History),
    Tab("config", "Config", Icons.Default.Settings)
)

class MainActivity : ComponentActivity() {
    private val viewModel: RotaViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        setContent {
            val config by viewModel.config.collectAsState()
            RotaCertaTheme(lightTheme = config.lightTheme) {
                val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
                LaunchedEffect(Unit) {
                    permissionLauncher.launch(
                        arrayOf(Manifest.permission.CAMERA, Manifest.permission.POST_NOTIFICATIONS)
                    )
                }

                val snackbarHostState = remember { SnackbarHostState() }
                LaunchedEffect(Unit) {
                    viewModel.toast.collect { msg -> snackbarHostState.showSnackbar(msg) }
                }

                var showAddDialog by remember { mutableStateOf(false) }
                val navController = rememberNavController()
                val isOnline by viewModel.isOnline.collectAsState()
                val context = LocalContext.current

                // Trava o app inteiro atrás do teste grátis de 10 dias / assinatura mensal
                // (diferente de travar só algumas abas — aqui não existe versão "sempre
                // grátis", então não faz sentido deixar meia tela usável).
                val hasAccess by PremiumAccessManager.hasAccess.collectAsState()
                val monthlyPriceLabel by BillingManager.monthlyPriceLabel.collectAsState()
                var isPurchasing by remember { mutableStateOf(false) }

                // Reseta o "comprando..." ao voltar pro app (cobre tanto compra concluída
                // quanto cancelada — nos dois casos o usuário volta pro app depois de mexer
                // no diálogo nativo do Google Play).
                val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) isPurchasing = false
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                if (!hasAccess) {
                    PaywallScreen(
                        daysRemaining = TrialManager.daysRemaining(context),
                        monthlyPriceLabel = monthlyPriceLabel,
                        isPurchasing = isPurchasing,
                        onSubscribeClick = {
                            isPurchasing = true
                            BillingManager.launchPurchase(this@MainActivity)
                        }
                    )
                    return@RotaCertaTheme
                }

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    topBar = {
                        // Discreto: só aparece quando falta internet, já que só a busca de
                        // endereço novo e os tiles do mapa realmente precisam de rede — o
                        // resto do app (ver/organizar entregas, marcar entregue, histórico)
                        // continua funcionando 100% normalmente offline.
                        AnimatedVisibility(visible = !isOnline) {
                            Surface(color = com.rotacerta.entregador.ui.theme.Danger.copy(alpha = 0.16f)) {
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.Default.CloudOff, contentDescription = null, tint = com.rotacerta.entregador.ui.theme.Danger, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "Sem internet — mapa e endereços novos ficam indisponíveis até reconectar",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = com.rotacerta.entregador.ui.theme.Danger
                                    )
                                }
                            }
                        }
                    },
                    bottomBar = {
                        NavigationBar {
                            val backStackEntry by navController.currentBackStackEntryAsState()
                            val currentRoute = backStackEntry?.destination?.route
                            TABS.forEach { tab ->
                                NavigationBarItem(
                                    selected = currentRoute == tab.route,
                                    onClick = {
                                        navController.navigate(tab.route) {
                                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = { Icon(tab.icon, contentDescription = tab.label) },
                                    label = { Text(tab.label) }
                                )
                            }
                        }
                    }
                ) { padding ->
                    NavHost(navController = navController, startDestination = "rota", modifier = Modifier.padding(padding)) {
                        composable("rota") { RotaScreen(viewModel, onAddClick = { showAddDialog = true }) }
                        composable("mapa") { MapScreen(viewModel) }
                        composable("historico") { HistoricoScreen(viewModel) }
                        composable("config") { ConfigScreen(viewModel) }
                    }
                }

                if (showAddDialog) {
                    AddDeliveryDialog(viewModel = viewModel, onDismiss = { showAddDialog = false })
                }
            }
        }
    }
}
