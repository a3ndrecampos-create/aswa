package com.rotacerta.entregador

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.rotacerta.entregador.ui.screens.*
import com.rotacerta.entregador.ui.theme.RotaCertaTheme
import com.rotacerta.entregador.viewmodel.RotaViewModel

private data class Tab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val TABS = listOf(
    Tab("rota", "Rota", Icons.Default.List),
    Tab("historico", "Histórico", Icons.Default.History),
    Tab("config", "Config", Icons.Default.Settings)
)

class MainActivity : ComponentActivity() {
    private val viewModel: RotaViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            RotaCertaTheme {
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

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
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
