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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
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

    val generatedSubUrl = "http://$effectiveHost:$port/sub?type=$selectedFormat$tokenParam$nodeParam"

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
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
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isZh) "自定义生成订阅链接" else "Custom Sub Generator",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Subscription Name Field
                OutlinedTextField(
                    value = customNameInput,
                    onValueChange = { customNameInput = it },
                    label = { Text(if (isZh) "自定义订阅名称" else "Custom Subscription Name") },
                    placeholder = { Text(if (isZh) "例如: 香港+日本专线订阅" else "e.g. HK & JP Nodes Only") },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_sub_name_input")
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Custom Token Field
                OutlinedTextField(
                    value = tokenInput,
                    onValueChange = {
                        tokenInput = it
                        viewModel.setCustomToken(it)
                    },
                    label = { Text(if (isZh) "鉴权 Token (可选)" else "Security Token (Optional)") },
                    placeholder = { Text("mytoken") },
                    leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_sub_token_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Format & Host Options
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = if (isZh) "订阅格式与监听地址:" else "Format & Server Address:",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = selectedFormat == "singbox",
                            onClick = { selectedFormat = "singbox" },
                            label = { Text("Sing-Box 1.14+", maxLines = 1) }
                        )
                        FilterChip(
                            selected = selectedFormat == "mihomo",
                            onClick = { selectedFormat = "mihomo" },
                            label = { Text("Mihomo / Clash", maxLines = 1) }
                        )
                        FilterChip(
                            selected = selectedFormat == "base64",
                            onClick = { selectedFormat = "base64" },
                            label = { Text("Base64 通用", maxLines = 1) }
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = selectedHost == "127.0.0.1",
                            onClick = { selectedHost = "127.0.0.1" },
                            label = { Text(if (isZh) "本机 (127.0.0.1)" else "Local (127.0.0.1)", maxLines = 1) }
                        )
                        FilterChip(
                            selected = selectedHost == "LAN",
                            onClick = { selectedHost = "LAN" },
                            label = { Text(if (isZh) "局域网 ($localIp)" else "LAN ($localIp)", maxLines = 1) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Divider()
                Spacer(modifier = Modifier.height(8.dp))

                // Node Selection Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isZh) "勾选节点 (${selectedIds.size} / ${nodes.size}):" else "Select Nodes (${selectedIds.size} / ${nodes.size}):",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Row {
                        OutlinedButton(
                            onClick = { viewModel.selectAllNodes() },
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text(if (isZh) "全选" else "Select All", style = MaterialTheme.typography.labelSmall)
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        OutlinedButton(
                            onClick = { viewModel.clearSelectedNodes() },
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text(if (isZh) "清空" else "Clear", style = MaterialTheme.typography.labelSmall)
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
                    } else {
                        items(nodes) { node ->
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
                        Text(
                            text = if (isZh) "生成的专属订阅链接:" else "Generated Custom Subscription URL:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )

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
                                    Toast.makeText(context, if (isZh) "已成功保存此自定义订阅！" else "Custom subscription saved!", Toast.LENGTH_SHORT).show()
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
                                Text(if (isZh) "保存此订阅" else "Save Subscription", style = MaterialTheme.typography.labelMedium, maxLines = 1)
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
        QrCodeModal(
            title = if (isZh) "专属订阅二维码" else "Custom Subscription QR Code",
            url = generatedSubUrl,
            onDismiss = { showQrModal = false }
        )
    }
}
