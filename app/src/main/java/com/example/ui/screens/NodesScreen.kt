package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.data.model.ProxyNode
import com.example.ui.MainViewModel

enum class NodeSortMode {
    DEFAULT,     // 默认顺序
    PING_ASC,    // 延迟优先 (极速)
    NAME_ASC,    // 名称排序 (A-Z)
    PROTOCOL,    // 协议类型
    ENABLED_FIRST // 启用优先
}

@Composable
fun NodesScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val isZh by viewModel.isChineseMode.collectAsStateWithLifecycle()
    val nodes by viewModel.nodes.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var selectedProtocol by remember { mutableStateOf("All") }
    var selectedSortMode by remember { mutableStateOf(NodeSortMode.DEFAULT) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showDeleteAllConfirm by remember { mutableStateOf(false) }
    var showDeleteInvalidConfirm by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }

    val protocols = listOf("All", "vless", "vmess", "ss", "trojan", "hysteria2", "anytls", "socks", "http")

    val filteredAndSortedNodes = remember(nodes, searchQuery, selectedProtocol, selectedSortMode) {
        val filtered = nodes.filter { node ->
            val matchesSearch = searchQuery.isBlank() ||
                    node.name.contains(searchQuery, ignoreCase = true) ||
                    node.server.contains(searchQuery, ignoreCase = true)

            val matchesProtocol = selectedProtocol == "All" ||
                    node.protocol.equals(selectedProtocol, ignoreCase = true)

            matchesSearch && matchesProtocol
        }

        when (selectedSortMode) {
            NodeSortMode.DEFAULT -> filtered
            NodeSortMode.PING_ASC -> filtered.sortedWith(
                compareBy<ProxyNode> {
                    when {
                        it.pingMs > 0 -> 0
                        it.pingMs == 0 -> 1
                        else -> 2
                    }
                }.thenBy { if (it.pingMs > 0) it.pingMs else Long.MAX_VALUE }
                 .thenBy { it.name }
            )
            NodeSortMode.NAME_ASC -> filtered.sortedBy { it.name.lowercase() }
            NodeSortMode.PROTOCOL -> filtered.sortedBy { it.protocol.lowercase() }
            NodeSortMode.ENABLED_FIRST -> filtered.sortedWith(
                compareByDescending<ProxyNode> { it.enabled }.thenBy { it.name }
            )
        }
    }

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
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        // Top Item 1: Import Node Source Entry Bar (Styled matching OutlinedTextField search box)
        item {
            Surface(
                onClick = { showImportDialog = true },
                shape = RoundedCornerShape(12.dp),
                color = Color.Transparent,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("import_node_source_bar")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddLink,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (isZh) "导入节点源" else "Import Node Source",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isZh) "点击导入 URL 订阅链接、剪贴板节点或配置文件..." else "Tap to import subscription URLs or node links...",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = { showImportDialog = true },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isZh) "去导入" else "Import", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // Top Item 2: Search Bar
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(if (isZh) "搜索节点名称或服务器..." else "Search nodes...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("node_search_input")
            )
        }

        // Action Buttons Toolbar: Ping All, Deduplicate, Delete All
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Ping All Button
                Button(
                    onClick = { viewModel.testAllNodesPing() },
                    modifier = Modifier
                        .height(36.dp)
                        .testTag("ping_all_button")
                ) {
                    Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isZh) "批量测速" else "Ping All", style = MaterialTheme.typography.labelSmall)
                }

                // Deduplicate Button
                OutlinedButton(
                    onClick = { viewModel.deduplicateNodes() },
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(Icons.Default.FilterAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isZh) "一键去重复" else "Deduplicate", style = MaterialTheme.typography.labelSmall)
                }

                // Delete Invalid Nodes Button
                OutlinedButton(
                    onClick = { showDeleteInvalidConfirm = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isZh) "删除无效节点" else "Delete Invalid", style = MaterialTheme.typography.labelSmall)
                }

                // Delete All Button
                OutlinedButton(
                    onClick = { showDeleteAllConfirm = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isZh) "删除所有节点" else "Delete All", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        // Protocol Filter Chips
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                protocols.forEach { proto ->
                    FilterChip(
                        selected = selectedProtocol == proto,
                        onClick = { selectedProtocol = proto },
                        label = {
                            Text(
                                if (proto == "All") (if (isZh) "全部协议" else "All Protocols") else proto.uppercase(),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isZh) "解析的节点列表 (${filteredAndSortedNodes.size} / ${nodes.size})" else "Extracted Nodes (${filteredAndSortedNodes.size} / ${nodes.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Box {
                    OutlinedButton(
                        onClick = { showSortMenu = true },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("node_sort_button")
                    ) {
                        Icon(Icons.Default.Sort, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = when (selectedSortMode) {
                                NodeSortMode.DEFAULT -> if (isZh) "默认" else "Default"
                                NodeSortMode.PING_ASC -> if (isZh) "延迟优先" else "Lowest Ping"
                                NodeSortMode.NAME_ASC -> if (isZh) "名称 A-Z" else "Name A-Z"
                                NodeSortMode.PROTOCOL -> if (isZh) "协议分类" else "By Protocol"
                                NodeSortMode.ENABLED_FIRST -> if (isZh) "启用优先" else "Enabled First"
                            },
                            style = MaterialTheme.typography.labelSmall
                        )
                    }

                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (isZh) "⚙️ 默认顺序" else "⚙️ Default Order") },
                            onClick = { selectedSortMode = NodeSortMode.DEFAULT; showSortMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text(if (isZh) "⚡ 延迟优先 (极速)" else "⚡ Lowest Latency First") },
                            onClick = { selectedSortMode = NodeSortMode.PING_ASC; showSortMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text(if (isZh) "🔤 名称排序 (A-Z)" else "🔤 Name (A-Z)") },
                            onClick = { selectedSortMode = NodeSortMode.NAME_ASC; showSortMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text(if (isZh) "🧩 协议类型" else "🧩 Protocol") },
                            onClick = { selectedSortMode = NodeSortMode.PROTOCOL; showSortMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text(if (isZh) "📌 启用状态优先" else "📌 Enabled First") },
                            onClick = { selectedSortMode = NodeSortMode.ENABLED_FIRST; showSortMenu = false }
                        )
                    }
                }
            }
        }

        if (filteredAndSortedNodes.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Text(
                        text = if (nodes.isEmpty()) (if (isZh) "暂无节点，请先添加订阅链接" else "No nodes available. Add a subscription first.")
                               else (if (isZh) "没有符合筛选条件的节点" else "No nodes matched your search filter."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        } else {
            items(filteredAndSortedNodes) { node ->
                NodeCard(
                    node = node,
                    isZh = isZh,
                    onToggleEnabled = { viewModel.toggleNodeEnabled(node) },
                    onPing = { viewModel.testNodePing(node) },
                    onCopyLink = {
                        val shareUrl = if (node.rawUri.isNotBlank()) {
                            node.rawUri
                        } else {
                            node.toUri()
                        }
                        copyToClipboard(shareUrl, node.name)
                    },
                    onDeleteNode = { viewModel.deleteNode(node.id) }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(20.dp)) }
    }

    // Delete Invalid Nodes Confirmation Dialog
    if (showDeleteInvalidConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteInvalidConfirm = false },
            title = { Text(if (isZh) "确认删除无效节点？" else "Delete Invalid Nodes?") },
            text = { Text(if (isZh) "此操作将清理所有测试失败或超时的节点 (Ping < 0ms)，无法撤销。" else "This action will remove all nodes that timed out or failed latency test.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteInvalidNodes()
                        showDeleteInvalidConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(if (isZh) "确认清理" else "Delete Invalid")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteInvalidConfirm = false }) {
                    Text(if (isZh) "取消" else "Cancel")
                }
            }
        )
    }

    // Delete All Confirmation Dialog
    if (showDeleteAllConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteAllConfirm = false },
            title = { Text(if (isZh) "确认删除所有节点？" else "Delete All Nodes?") },
            text = { Text(if (isZh) "此操作将清空列表中保存的所有代理节点，无法撤销。" else "This action will clear all saved proxy nodes.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteAllNodes()
                        showDeleteAllConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(if (isZh) "确认清空" else "Delete All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllConfirm = false }) {
                    Text(if (isZh) "取消" else "Cancel")
                }
            }
        )
    }

    // Full-screen Import Node Source Dialog
    if (showImportDialog) {
        AddSubscriptionDialog(
            isZh = isZh,
            onDismiss = { showImportDialog = false },
            onAdd = { name, urlOrContent ->
                viewModel.addSubscription(name, urlOrContent)
            }
        )
    }
}

@Composable
fun NodeCard(
    node: ProxyNode,
    isZh: Boolean,
    onToggleEnabled: () -> Unit,
    onPing: () -> Unit,
    onCopyLink: () -> Unit,
    onDeleteNode: () -> Unit
) {
    val protocolColor = getProtocolColor(node.protocol)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("node_card_${node.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Row: Protocol Tag + Node Name + Enable Switch
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
                        shape = RoundedCornerShape(8.dp),
                        color = protocolColor,
                        modifier = Modifier.height(26.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        ) {
                            Text(
                                text = node.protocol.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = node.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Switch(
                    checked = node.enabled,
                    onCheckedChange = { onToggleEnabled() },
                    modifier = Modifier.testTag("node_switch_${node.id}")
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Footer Status & Action Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (node.tls) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF10B981).copy(alpha = 0.15f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    Icons.Default.Security,
                                    contentDescription = "TLS Enabled",
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "TLS",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF10B981)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    // Ping latency badge
                    when {
                        node.pingMs > 0 -> {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = (if (node.pingMs < 200) Color(0xFF10B981) else Color(0xFFF59E0B)).copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "${node.pingMs} ms",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (node.pingMs < 200) Color(0xFF10B981) else Color(0xFFF59E0B),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        node.pingMs == -2 -> {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.errorContainer
                            ) {
                                Text(
                                    text = if (isZh) "超时" else "Timeout",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        else -> {
                            Text(
                                text = if (isZh) "未测试" else "Untested",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Copy Share Link Icon
                    IconButton(
                        onClick = onCopyLink,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = "Copy Link",
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Ping Test Icon
                    IconButton(
                        onClick = onPing,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.NetworkCheck,
                            contentDescription = "Test Ping",
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Single Node Delete Icon
                    IconButton(
                        onClick = onDeleteNode,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete Node",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun getProtocolColor(protocol: String): Color {
    return when (protocol.lowercase()) {
        "vless" -> Color(0xFF2563EB)
        "vmess" -> Color(0xFF7C3AED)
        "ss", "shadowsocks" -> Color(0xFF059669)
        "trojan" -> Color(0xFFDC2626)
        "hysteria2", "hy2" -> Color(0xFFD97706)
        "anytls" -> Color(0xFF8B5CF6)
        else -> Color(0xFF475569)
    }
}
