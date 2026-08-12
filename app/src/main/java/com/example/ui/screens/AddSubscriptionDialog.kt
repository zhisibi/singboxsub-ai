package com.example.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun AddSubscriptionDialog(
    isZh: Boolean = true,
    onDismiss: () -> Unit,
    onAdd: (name: String, urlOrContent: String) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var urlOrContent by remember { mutableStateOf("") }

    val pasteFromClipboard = {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val item = clipboard.primaryClip?.getItemAt(0)
            val text = item?.text?.toString() ?: ""
            if (text.isNotBlank()) {
                urlOrContent = text
                Toast.makeText(context, if (isZh) "已自动粘贴剪贴板内容" else "Pasted from clipboard", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, if (isZh) "剪贴板为空" else "Clipboard is empty", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, if (isZh) "读取剪贴板失败" else "Failed to read clipboard", Toast.LENGTH_SHORT).show()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .testTag("add_sub_dialog_fullscreen"),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Top Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(44.dp)
                        ) {
                            IconButton(onClick = {}) {
                                Icon(
                                    Icons.Default.Download,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (isZh) "📥 导入节点源" else "📥 Import Node Source",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = if (isZh) "支持 订阅 URL、剪贴板、多协议 URI 及配置文件" else "Support Subscription URLs, Clipboard, URIs & Configs",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_import_dialog_button")
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = if (isZh) "关闭" else "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Paste Clipboard Quick Action Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isZh) "📋 一键读取剪贴板" else "📋 Clipboard Quick Paste",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isZh) "自动识别剪贴板中的节点链接或订阅地址" else "Auto detect node links or subscription URL in clipboard",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = pasteFromClipboard,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isZh) "粘贴" else "Paste", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Input Field 1: Node Source Name
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(if (isZh) "节点源名称 (可选)" else "Node Source Name (Optional)") },
                    placeholder = { Text(if (isZh) "例如: 我的香港专线 / 我的机场订阅" else "e.g. My HK Subscription") },
                    leadingIcon = { Icon(Icons.Default.AssignmentTurnedIn, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sub_name_input")
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Input Field 2: URL or Content
                OutlinedTextField(
                    value = urlOrContent,
                    onValueChange = { urlOrContent = it },
                    label = { Text(if (isZh) "节点源地址 / 链接 / 原始节点内容" else "Node Source URL / Links / Content") },
                    placeholder = { Text(if (isZh) "支持粘贴: http://... 订阅链接，或者 vless://, vmess://, ss://, trojan://, hy2:// 单/多条节点 URI 或 Base64 / JSON" else "Paste http:// URL, or vless://, vmess://, ss://, trojan://, hy2:// URIs...") },
                    leadingIcon = { Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    minLines = 8,
                    maxLines = 14,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sub_url_input")
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Format Badges Indicator
                Text(
                    text = if (isZh) "💡 广泛兼容以下协议与配置格式：" else "💡 Compatible Protocols & Configs:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("VLESS", "VMess", "Shadowsocks", "Trojan", "Hysteria2", "Socks5", "Sing-Box", "Clash").forEach { protocol ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        ) {
                            Text(
                                text = protocol,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f, fill = false))
                Spacer(modifier = Modifier.height(24.dp))

                // Action Buttons Bottom Bar
                Button(
                    onClick = {
                        if (urlOrContent.isNotBlank()) {
                            onAdd(name, urlOrContent)
                            onDismiss()
                        }
                    },
                    enabled = urlOrContent.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("confirm_add_sub_button")
                ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isZh) "🚀 立即解析并导入节点" else "🚀 Parse & Import Nodes",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text(if (isZh) "取消" else "Cancel")
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

