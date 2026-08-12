package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.ui.MainViewModel
import com.example.ui.screens.ConfigScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.NodesScreen
import com.example.ui.screens.SubscriptionsScreen
import com.example.ui.screens.WallpaperSettingsDialog
import com.example.ui.theme.MyApplicationTheme
import com.example.util.CrashHandler

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashHandler.init(applicationContext)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainAppScreen(viewModel = viewModel)
            }
        }
    }
}

sealed class NavigationTab(val route: String, val titleEn: String, val titleZh: String, val icon: ImageVector) {
    object Server : NavigationTab("server", "Dashboard", "首页", Icons.Default.Dns)
    object Subscriptions : NavigationTab("subscriptions", "Subscriptions", "订阅列表", Icons.Default.RssFeed)
    object Nodes : NavigationTab("nodes", "Nodes", "节点列表", Icons.Default.List)
    object Config : NavigationTab("config", "Converter Rules", "配置转换", Icons.Default.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val isZh by viewModel.isChineseMode.collectAsStateWithLifecycle()
    val wallpaperType by viewModel.wallpaperType.collectAsStateWithLifecycle()
    val customWallpaperUri by viewModel.customWallpaperUri.collectAsStateWithLifecycle()
    val wallpaperOpacity by viewModel.wallpaperOpacity.collectAsStateWithLifecycle()

    var crashLog by remember { mutableStateOf(CrashHandler.getCrashLog(context)) }
    var showWallpaperDialog by remember { mutableStateOf(false) }

    if (crashLog != null) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text(if (isZh) "应用崩溃/闪退日志" else "App Crash Log") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(text = crashLog ?: "", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Button(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Crash Log", crashLog)
                    clipboard.setPrimaryClip(clip)
                }) {
                    Text(if (isZh) "复制日志" else "Copy Log")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    CrashHandler.clearCrashLog(context)
                    crashLog = null
                }) {
                    Text(if (isZh) "清理并关闭" else "Clear & Close")
                }
            }
        )
    }

    val tabs = listOf(
        NavigationTab.Server,
        NavigationTab.Subscriptions,
        NavigationTab.Nodes,
        NavigationTab.Config
    )

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()

    LaunchedEffect(statusMessage) {
        statusMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Background Wallpaper Rendering
        when (wallpaperType) {
            "Gradient" -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF1E1B4B), Color(0xFF312E81), Color(0xFF0F172A))
                            )
                        )
                )
            }
            "Cyberpunk" -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF4C1D95), Color(0xFF0284C7), Color(0xFF020617))
                            )
                        )
                )
            }
            "DeepDark" -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF090D16), Color(0xFF111827), Color(0xFF030712))
                            )
                        )
                )
            }
            "Custom" -> {
                if (customWallpaperUri.isNotEmpty()) {
                    AsyncImage(
                        model = customWallpaperUri,
                        contentDescription = "Custom Background Wallpaper",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        // Wallpaper Dimmer Overlay
        if (wallpaperType != "None") {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = wallpaperOpacity))
            )
        }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = if (wallpaperType == "None") MaterialTheme.colorScheme.background else Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = if (isZh) tabs[selectedTabIndex].titleZh else tabs[selectedTabIndex].titleEn,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    actions = {
                        // Wallpaper Picker Button
                        IconButton(onClick = { showWallpaperDialog = true }) {
                            Icon(
                                Icons.Default.Image,
                                contentDescription = "Wallpaper Settings",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Language Toggle Switcher Button ("中 / EN")
                        OutlinedButton(
                            onClick = { viewModel.toggleLanguage() },
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isZh) "中" else "EN",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = if (wallpaperType == "None") MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        titleContentColor = MaterialTheme.colorScheme.primary
                    )
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = if (wallpaperType == "None") MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                ) {
                    tabs.forEachIndexed { index, tab ->
                        NavigationBarItem(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            icon = { Icon(tab.icon, contentDescription = if (isZh) tab.titleZh else tab.titleEn) },
                            label = {
                                Text(
                                    if (isZh) tab.titleZh else tab.titleEn,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            modifier = Modifier.testTag("nav_tab_${tab.route}")
                        )
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (selectedTabIndex) {
                    0 -> DashboardScreen(viewModel = viewModel)
                    1 -> SubscriptionsScreen(viewModel = viewModel)
                    2 -> NodesScreen(viewModel = viewModel)
                    3 -> ConfigScreen(viewModel = viewModel)
                }
            }
        }
    }

    if (showWallpaperDialog) {
        WallpaperSettingsDialog(
            viewModel = viewModel,
            onDismiss = { showWallpaperDialog = false }
        )
    }
}
