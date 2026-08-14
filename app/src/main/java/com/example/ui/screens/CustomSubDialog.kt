package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import java.net.URLEncoder
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.LaunchedEffect
import com.example.data.model.SavedCustomSubscription
import com.example.ui.MainViewModel

@Composable
fun CustomSubDialog(
    viewModel: MainViewModel,
    subToEdit: SavedCustomSubscription? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isZh by viewModel.isChineseMode.collectAsStateWithLifecycle()
    val nodes by viewModel.nodes.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedNodeIds.collectAsStateWithLifecycle()
    val customToken by viewModel.customToken.collectAsStateWithLifecycle()
    val localIp by viewModel.localIp.collectAsStateWithLifecycle()
    val port by viewModel.httpServer.port.collectAsStateWithLifecycle()

    var customNameInput by remember { mutableStateOf(subToEdit?.name ?: "") }
    var tokenInput by remember(customToken) { mutableStateOf(subToEdit?.token ?: customToken) }
    var selectedFormat by remember { mutableStateOf(subToEdit?.format ?: "singbox") } // singbox, mihomo, base64
    var selectedHost by remember { mutableStateOf("127.0.0.1") } // 127.0.0.1 or LAN
    var showQrModal by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredNodes = remember(nodes, searchQuery) {
        if (searchQuery.isBlank()) {
            nodes
        } else {
            val q = searchQuery.trim()
            nodes.filter { node ->
                node.name.contains(q, ignoreCase = true) ||
                node.server.contains(q, ignoreCase = true) ||
                node.protocol.contains(q, ignoreCase = true) ||
                node.port.toString().contains(q)
            }
        }
    }

    LaunchedEffect(subToEdit) {
        if (subToEdit != null) {
            val initialIds = subToEdit.nodeIds.split(",")
                .mapNotNull { it.trim().toLongOrNull() }
                .toSet()
            viewModel.setSelectedNodeIds(initialIds)
        }
    }

    val effectiveHost = if (selectedHost == "127.0.0.1") "127.0.0.1" else localIp
    val tokenParam = if (tokenInput.isNotBlank()) "&token=$tokenInput" else ""
    val nodeParam = if (selectedIds.isNotEmpty()) "&nodes=${selectedIds.joinToString(",")}" else ""

    val generatedSubUrl = if (subToEdit != null) {
        "http://$effectiveHost:$port/sub?sid=${subToEdit.id}$tokenParam"
    } else {
        when (selectedFormat) {
            "singbox113" -> "http://$effectiveHost:$port/sub?type=singbox&ver=1.13$tokenParam$nodeParam"
            "singbox" -> "http://$effectiveHost:$port/sub?type=singbox$tokenParam$nodeParam"
            "mihomo" -> "http://$effectiveHost:$port/sub?type=mihomo$tokenParam$nodeParam"
            "base64" -> "http://$effectiveHost:$port/sub?type=base64$tokenParam$nodeParam"
            else -> "http://$effectiveHost:$port/sub?type=$selectedFormat$tokenParam$nodeParam"
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.94f),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (subToEdit != null) {
                                if (isZh) "编辑自定义订阅" else "Edit Custom Subscription"
                            } else {
                                if (isZh) "自定义生成订阅" else "Custom Sub Generator"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(20.dp))
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Compact Row for Subscription Name & Token Fields
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = customNameInput,
                        onValueChange = { customNameInput = it },
                        label = { Text(if (isZh) "订阅名称" else "Sub Name", style = MaterialTheme.typography.labelSmall) },
                        placeholder = { Text(if (isZh) "如: 香港+日本专线" else "HK & JP") },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1.2f)
                            .testTag("custom_sub_name_input")
                    )

                    OutlinedTextField(
                        value = tokenInput,
                        onValueChange = {
                            tokenInput = it
                            viewModel.setCustomToken(it)
                        },
                        label = { Text(if (isZh) "Token (可选)" else "Token", style = MaterialTheme.typography.labelSmall) },
                        placeholder = { Text("mytoken") },
                        leadingIcon = { Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        singleLine = true,
                        modifier = Modifier
                            .weight(0.8f)
                            .testTag("custom_sub_token_input")
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Format & Host Combined Scrollable Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isZh) "格式:" else "Format:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    FilterChip(
                        selected = selectedFormat == "singbox",
                        onClick = { selectedFormat = "singbox" },
                        label = { Text("Sing-Box 1.14+", style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.height(28.dp)
                    )
                    FilterChip(
                        selected = selectedFormat == "singbox113",
                        onClick = { selectedFormat = "singbox113" },
                        label = { Text("Sing-Box 1.13", style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.height(28.dp)
                    )
                    FilterChip(
                        selected = selectedFormat == "mihomo",
                        onClick = { selectedFormat = "mihomo" },
                        label = { Text("Mihomo/Clash", style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.height(28.dp)
                    )
                    FilterChip(
                        selected = selectedFormat == "base64",
                        onClick = { selectedFormat = "base64" },
                        label = { Text("Base64", style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.height(28.dp)
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = if (isZh) "地址:" else "Host:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    FilterChip(
                        selected = selectedHost == "127.0.0.1",
                        onClick = { selectedHost = "127.0.0.1" },
                        label = { Text("127.0.0.1", style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.height(28.dp)
                    )
                    FilterChip(
                        selected = selectedHost == "LAN",
                        onClick = { selectedHost = "LAN" },
                        label = { Text(if (isZh) "局域网" else "LAN", style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.height(28.dp)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Search & Filter Section
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text(if (isZh) "搜索节点 (名称/IP/协议/端口)" else "Search nodes (Name/IP/Protocol/Port)", style = MaterialTheme.typography.labelSmall) },
                    placeholder = { Text(if (isZh) "输入关键字如: 香港, vless, 443..." else "Search e.g. HK, vless...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear search", modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_sub_search_input")
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Quick Filter Tag Chips
                val quickTags = remember { listOf("香港", "日本", "美国", "新加坡", "台湾", "vless", "vmess", "hy2") }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isZh) "快捷:" else "Tags:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    quickTags.forEach { tag ->
                        FilterChip(
                            selected = searchQuery.equals(tag, ignoreCase = true),
                            onClick = {
                                searchQuery = if (searchQuery.equals(tag, ignoreCase = true)) "" else tag
                            },
                            label = { Text(tag, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.height(26.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(4.dp))

                // Node Selection Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val countText = if (searchQuery.isNotBlank()) {
                        if (isZh) "已选 ${selectedIds.size} / 筛选 ${filteredNodes.size} (总 ${nodes.size})"
                        else "Selected ${selectedIds.size} / Filtered ${filteredNodes.size} (Total ${nodes.size})"
                    } else {
                        if (isZh) "勾选节点 (${selectedIds.size} / ${nodes.size}):"
                        else "Select Nodes (${selectedIds.size} / ${nodes.size}):"
                    }

                    Text(
                        text = countText,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    Row {
                        OutlinedButton(
                            onClick = {
                                if (searchQuery.isNotBlank()) {
                                    val newIds = selectedIds + filteredNodes.map { it.id }
                                    viewModel.setSelectedNodeIds(newIds)
                                } else {
                                    viewModel.selectAllNodes()
                                }
                            },
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text(
                                text = if (searchQuery.isNotBlank()) (if (isZh) "全选筛选" else "Select Filtered") else (if (isZh) "全选" else "Select All"),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        OutlinedButton(
                            onClick = {
                                if (searchQuery.isNotBlank()) {
                                    val filteredIds = filteredNodes.map { it.id }.toSet()
                                    viewModel.setSelectedNodeIds(selectedIds - filteredIds)
                                } else {
                                    viewModel.clearSelectedNodes()
                                }
                            },
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text(
                                text = if (searchQuery.isNotBlank()) (if (isZh) "取消筛选" else "Deselect Filtered") else (if (isZh) "清空" else "Clear"),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Scrollable Node List
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (nodes.isEmpty()) {
                        item {
                            Text(
                                text = if (isZh) "当前没有节点，请先在‘节点管理’中添加订阅" else "No nodes available. Please add subscription first.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        }
                    } else if (filteredNodes.isEmpty()) {
                        item {
                            Text(
                                text = if (isZh) "未匹配到包含 '$searchQuery' 的节点" else "No nodes found matching '$searchQuery'",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        }
                    } else {
                        items(filteredNodes) { node ->
                            val isSelected = selectedIds.contains(node.id)
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.toggleNodeSelection(node.id) },
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { viewModel.toggleNodeSelection(node.id) }
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Column {
                                        Text(
                                            text = node.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${node.protocol.uppercase()} • ${node.server}:${node.port}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Generated URL Preview & Actions Box
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (subToEdit != null) {
                                    if (isZh) "固定专属订阅链接 (永久唯一):" else "Permanent Custom Sub URL:"
                                } else {
                                    if (isZh) "专属订阅链接预览:" else "Custom Subscription URL Preview:"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )

                            if (subToEdit != null) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = 4.dp)
                                ) {
                                    Text(
                                        text = if (isZh) "永久固定" else "PERMANENT",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = generatedSubUrl,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(8.dp),
                                maxLines = 2
                            )
                        }

                        if (subToEdit != null) {
                            Text(
                                text = if (isZh) "✨ 修改选中的节点后此链接保持不变，客户端只需点击‘更新订阅’即可自动同步最新节点！" else "✨ Node changes apply dynamically; client subscription URL stays unchanged.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Save Button
                            Button(
                                onClick = {
                                    viewModel.saveCustomSubscription(
                                        name = customNameInput,
                                        format = selectedFormat,
                                        token = tokenInput,
                                        nodeIds = selectedIds.toList(),
                                        id = subToEdit?.id ?: 0
                                    )
                                    Toast.makeText(context, if (isZh) "已成功保存！专属链接永久有效。" else "Saved! Permanent URL active.", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .testTag("save_custom_sub_button")
                            ) {
                                Icon(Icons.Default.BookmarkAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    if (subToEdit != null) {
                                        if (isZh) "保存修改 (链接不变)" else "Save (URL Unchanged)"
                                    } else {
                                        if (isZh) "保存并生成固定链接" else "Save & Get Link"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1
                                )
                            }

                            OutlinedButton(
                                onClick = { showQrModal = true },
                                modifier = Modifier.height(38.dp)
                            ) {
                                Icon(Icons.Default.QrCode, contentDescription = "QR Code", modifier = Modifier.size(16.dp))
                            }

                            Button(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Custom Subscription URL", generatedSubUrl)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, if (isZh) "专属订阅链接已复制" else "Custom URL copied", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.height(38.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy URL", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showQrModal) {
        val qrUrl = if (selectedFormat == "singbox" || selectedFormat == "singbox113") {
            val encodedUrl = URLEncoder.encode(generatedSubUrl, "UTF-8")
            val defaultName = if (selectedFormat == "singbox113") "Sing-Box 1.13" else "Sing-Box"
            val encodedName = URLEncoder.encode(customNameInput.ifBlank { defaultName }, "UTF-8")
            "sing-box://import-remote-profile?url=$encodedUrl#$encodedName"
        } else {
            generatedSubUrl
        }
        QrCodeModal(
            title = if (isZh) "专属订阅二维码" else "Custom Subscription QR Code",
            url = qrUrl,
            isZh = isZh,
            onDismiss = { showQrModal = false }
        )
    }
}
