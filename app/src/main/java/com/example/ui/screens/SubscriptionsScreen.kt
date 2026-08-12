package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.SavedCustomSubscription
import com.example.data.model.Subscription
import com.example.ui.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SubscriptionsScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val isZh by viewModel.isChineseMode.collectAsStateWithLifecycle()
    val subscriptions by viewModel.subscriptions.collectAsStateWithLifecycle()
    val savedCustomSubs by viewModel.savedCustomSubs.collectAsStateWithLifecycle()
    val port by viewModel.httpServer.port.collectAsStateWithLifecycle()
    val token by viewModel.httpServer.secretToken.collectAsStateWithLifecycle()
    val localIp by viewModel.localIp.collectAsStateWithLifecycle()

    var showAddSourceDialog by remember { mutableStateOf(false) }
    var showCustomSubDialog by remember { mutableStateOf(false) }
    var subToEdit by remember { mutableStateOf<SavedCustomSubscription?>(null) }

    var qrModalUrl by remember { mutableStateOf<String?>(null) }
    var qrModalTitle by remember { mutableStateOf("") }

    val tokenParam = if (token.isNotEmpty()) "&token=$token" else ""

    val copyToClipboard: (String, String) -> Unit = { text, label ->
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, if (isZh) "已复制 $label 到剪贴板" else "$label copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            // Top Action Header Toolbar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isZh) "🔗 订阅链接管理中心" else "🔗 Subscriptions Hub",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                OutlinedButton(
                    onClick = { showAddSourceDialog = true },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .height(36.dp)
                        .testTag("add_sub_source_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isZh) "导入订阅源" else "Import Sub", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        // Action Hero Banner Card for Creating Custom Subscription
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        subToEdit = null
                        showCustomSubDialog = true
                    }
                    .testTag("create_custom_sub_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Tune,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = if (isZh) "🎯 生成专属自定义订阅" else "🎯 Create Custom Subscription",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isZh) "自由勾选节点与格式(Sing-Box/Mihomo/Base64)" else "Filter nodes & formats into tailored links",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            subToEdit = null
                            showCustomSubDialog = true
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("create_custom_sub_button")
                    ) {
                        Text(if (isZh) "去生成" else "Create")
                    }
                }
            }
        }

        // Section 1: Saved Custom Subscriptions
        item {
            Text(
                text = if (isZh) "🎯 自定义生成的订阅链接 (${savedCustomSubs.size})" else "🎯 Custom Generated Subscriptions (${savedCustomSubs.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        if (savedCustomSubs.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(18.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = if (isZh) "暂无自定义订阅。点击上方的‘去生成’，可选择指定节点与格式并永久保存，随时点击编辑！" else "No saved custom subscriptions. Tap 'Create' above to generate tailored links with full editing support.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(savedCustomSubs) { customSub ->
                SavedCustomSubCard(
                    customSub = customSub,
                    port = port,
                    localIp = localIp,
                    isZh = isZh,
                    onEdit = {
                        subToEdit = customSub
                        showCustomSubDialog = true
                    },
                    onCopyLocal = { url -> copyToClipboard(url, "${customSub.name} (Local)") },
                    onCopyLan = { url -> copyToClipboard(url, "${customSub.name} (LAN)") },
                    onQrCode = { url ->
                        qrModalUrl = url
                        qrModalTitle = "${customSub.name} 二维码"
                    },
                    onDelete = { viewModel.deleteCustomSubscription(customSub.id) }
                )
            }
        }

        // Section 2: Default All-Nodes Subscription Links
        item {
            Text(
                text = if (isZh) "📦 默认全节点订阅链接" else "📦 Default All-Nodes Subscription Links",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        // Sing-Box 1.14+
        item {
            val singboxLocalUrl = "http://127.0.0.1:$port/sub?type=singbox$tokenParam"
            val singboxLanUrl = "http://$localIp:$port/sub?type=singbox$tokenParam"
            DefaultSubCard(
                formatTag = "SING-BOX 1.14+",
                formatType = "singbox",
                title = if (isZh) "Sing-Box 官方JSON 订阅" else "Sing-Box Official JSON Sub",
                subtitle = if (isZh) "全节点 • 包含全新 Dns.Servers & RuleSet" else "All Nodes • Latest Sing-Box DNS & RuleSet schemas",
                localUrl = singboxLocalUrl,
                lanUrl = singboxLanUrl,
                isZh = isZh,
                onCopyLocal = { copyToClipboard(singboxLocalUrl, "Sing-Box Local Link") },
                onCopyLan = { copyToClipboard(singboxLanUrl, "Sing-Box LAN Link") },
                onQrCode = {
                    qrModalUrl = singboxLanUrl
                    qrModalTitle = if (isZh) "Sing-Box 局域网订阅二维码" else "Sing-Box LAN QR Code"
                }
            )
        }

        // Mihomo (Clash Meta)
        item {
            val mihomoLocalUrl = "http://127.0.0.1:$port/sub?type=mihomo$tokenParam"
            val mihomoLanUrl = "http://$localIp:$port/sub?type=mihomo$tokenParam"
            DefaultSubCard(
                formatTag = "MIHOMO / CLASH",
                formatType = "mihomo",
                title = if (isZh) "Mihomo / Clash YAML 订阅" else "Mihomo / Clash Meta YAML Sub",
                subtitle = if (isZh) "全节点 • 兼容 Clash Verge, NekoBox, Shadowrocket" else "All Nodes • Compatible with Clash Verge, NekoBox & Shadowrocket",
                localUrl = mihomoLocalUrl,
                lanUrl = mihomoLanUrl,
                isZh = isZh,
                onCopyLocal = { copyToClipboard(mihomoLocalUrl, "Mihomo Local Link") },
                onCopyLan = { copyToClipboard(mihomoLanUrl, "Mihomo LAN Link") },
                onQrCode = {
                    qrModalUrl = mihomoLanUrl
                    qrModalTitle = if (isZh) "Mihomo 局域网订阅二维码" else "Mihomo LAN QR Code"
                }
            )
        }

        // Base64 Universal
        item {
            val base64LocalUrl = "http://127.0.0.1:$port/sub?type=base64$tokenParam"
            val base64LanUrl = "http://$localIp:$port/sub?type=base64$tokenParam"
            DefaultSubCard(
                formatTag = "BASE64 通用",
                formatType = "base64",
                title = if (isZh) "Base64 通用节点订阅" else "Base64 Universal Sub",
                subtitle = if (isZh) "全节点 • 适配 v2rayNG, Shadowrocket, PassWall" else "All Nodes • Standard base64 encoded node URI list",
                localUrl = base64LocalUrl,
                lanUrl = base64LanUrl,
                isZh = isZh,
                onCopyLocal = { copyToClipboard(base64LocalUrl, "Base64 Local Link") },
                onCopyLan = { copyToClipboard(base64LanUrl, "Base64 LAN Link") },
                onQrCode = {
                    qrModalUrl = base64LanUrl
                    qrModalTitle = if (isZh) "Base64 局域网订阅二维码" else "Base64 LAN QR Code"
                }
            )
        }

        // Section 3: Imported Subscription Sources
        item {
            Text(
                text = if (isZh) "🌐 已导入的节点订阅源 (${subscriptions.size})" else "🌐 Imported Subscription Sources (${subscriptions.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        if (subscriptions.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.RssFeed, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(18.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = if (isZh) "暂未导入外部订阅源。点击右上方‘导入订阅源’添加你的节点订阅链接。" else "No imported sub sources yet. Tap 'Import Sub' at top right.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(subscriptions) { sub ->
                ImportedSubCard(
                    subscription = sub,
                    isZh = isZh,
                    onRefresh = { viewModel.refreshSubscription(sub) },
                    onDelete = { viewModel.deleteSubscription(sub.id) }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(20.dp)) }
    }

    // Add Subscription Source Dialog
    if (showAddSourceDialog) {
        AddSubscriptionDialog(
            onDismiss = { showAddSourceDialog = false },
            onAdd = { name, urlOrContent ->
                viewModel.addSubscription(name, urlOrContent)
                showAddSourceDialog = false
            }
        )
    }

    // Custom Sub Dialog (Create or Edit)
    if (showCustomSubDialog) {
        CustomSubDialog(
            viewModel = viewModel,
            subToEdit = subToEdit,
            onDismiss = {
                showCustomSubDialog = false
                subToEdit = null
            }
        )
    }

    // QR Code Modal Dialog
    qrModalUrl?.let { url ->
        QrCodeModal(
            title = qrModalTitle,
            url = url,
            onDismiss = { qrModalUrl = null }
        )
    }
}

@Composable
fun DefaultSubCard(
    formatTag: String,
    formatType: String,
    title: String,
    subtitle: String,
    localUrl: String,
    lanUrl: String,
    isZh: Boolean,
    onCopyLocal: () -> Unit,
    onCopyLan: () -> Unit,
    onQrCode: () -> Unit
) {
    val avatarBgColor = when (formatType) {
        "singbox" -> MaterialTheme.colorScheme.primaryContainer
        "mihomo" -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.tertiaryContainer
    }
    val avatarIconColor = when (formatType) {
        "singbox" -> MaterialTheme.colorScheme.onPrimaryContainer
        "mihomo" -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onTertiaryContainer
    }
    val formatIcon = when (formatType) {
        "singbox" -> Icons.Default.Terminal
        "mihomo" -> Icons.Default.Router
        else -> Icons.Default.Dns
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Row: Avatar + Title Stack + Format Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = avatarBgColor,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = formatIcon,
                                contentDescription = null,
                                tint = avatarIconColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = avatarBgColor
                ) {
                    Text(
                        text = formatTag,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = avatarIconColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Address Box
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = if (isZh) "局域网订阅地址:" else "LAN Sub Address:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = lanUrl,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Footer Action Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onQrCode,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isZh) "二维码" else "QR", style = MaterialTheme.typography.labelSmall)
                }

                Spacer(modifier = Modifier.width(8.dp))

                OutlinedButton(
                    onClick = onCopyLocal,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isZh) "复制本机" else "Local", style = MaterialTheme.typography.labelSmall)
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = onCopyLan,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isZh) "复制局域网" else "Copy LAN", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
fun SavedCustomSubCard(
    customSub: SavedCustomSubscription,
    port: Int,
    localIp: String,
    isZh: Boolean,
    onEdit: () -> Unit,
    onCopyLocal: (String) -> Unit,
    onCopyLan: (String) -> Unit,
    onQrCode: (String) -> Unit,
    onDelete: () -> Unit
) {
    val tokenParam = if (customSub.token.isNotBlank()) "&token=${customSub.token}" else ""
    val nodeParam = if (customSub.nodeIds.isNotBlank()) "&nodes=${customSub.nodeIds}" else ""

    val localUrl = "http://127.0.0.1:$port/sub?type=${customSub.format}$tokenParam$nodeParam"
    val lanUrl = "http://$localIp:$port/sub?type=${customSub.format}$tokenParam$nodeParam"

    val nodeCountText = if (customSub.nodeIds.isBlank()) {
        if (isZh) "所有节点" else "All Nodes"
    } else {
        val count = customSub.nodeIds.split(",").size
        if (isZh) "$count 个筛选节点" else "$count Filtered Nodes"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Row: Avatar + Title Stack + Edit / Delete Icons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = customSub.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "$nodeCountText • Token: ${if (customSub.token.isBlank()) "无" else customSub.token}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = customSub.format.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Address Box
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = if (isZh) "专属局域网订阅地址:" else "Custom LAN Sub Address:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = lanUrl,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Footer Action Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { onQrCode(lanUrl) },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isZh) "二维码" else "QR", style = MaterialTheme.typography.labelSmall)
                }

                Spacer(modifier = Modifier.width(8.dp))

                OutlinedButton(
                    onClick = { onCopyLocal(localUrl) },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isZh) "复制本机" else "Local", style = MaterialTheme.typography.labelSmall)
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = { onCopyLan(lanUrl) },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isZh) "复制局域网" else "LAN", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
fun ImportedSubCard(
    subscription: Subscription,
    isZh: Boolean,
    onRefresh: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    val timeStr = remember(subscription.lastUpdated) {
        if (subscription.lastUpdated == 0L) (if (isZh) "未更新" else "Never")
        else dateFormat.format(Date(subscription.lastUpdated))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Row: Avatar + Title Stack + Refresh & Delete Icons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (subscription.url.isBlank()) Icons.Default.Link else Icons.Default.RssFeed,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = subscription.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (subscription.url.isBlank()) (if (isZh) "手动输入的节点" else "Manual pasted nodes") else subscription.url,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (subscription.url.isNotBlank()) {
                        IconButton(
                            onClick = onRefresh,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", modifier = Modifier.size(18.dp))
                        }
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Details Meta Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = if (isZh) "包含 ${subscription.nodeCount} 个节点" else "${subscription.nodeCount} nodes",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Text(
                    text = if (isZh) "更新于 $timeStr" else "Updated: $timeStr",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

