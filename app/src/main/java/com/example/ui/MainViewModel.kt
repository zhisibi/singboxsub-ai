package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.model.ProxyNode
import com.example.data.model.SavedCustomSubscription
import com.example.data.model.ServerLog
import com.example.data.model.Subscription
import com.example.data.parser.SubscriptionParser
import com.example.server.LocalHttpServer
import com.example.server.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    val httpServer = LocalHttpServer(application)
    private val prefs = application.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    val subscriptions: StateFlow<List<Subscription>> = db.subscriptionDao().getAllSubscriptions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val nodes: StateFlow<List<ProxyNode>> = db.proxyNodeDao().getAllNodesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val serverLogs: StateFlow<List<ServerLog>> = db.serverLogDao().getRecentLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val savedCustomSubs: StateFlow<List<SavedCustomSubscription>> = db.savedCustomSubDao().getAllSavedCustomSubs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _routingMode = MutableStateFlow("Rule") // Rule, Global, Direct
    val routingMode: StateFlow<String> = _routingMode.asStateFlow()

    private val _localIp = MutableStateFlow("127.0.0.1")
    val localIp: StateFlow<String> = _localIp.asStateFlow()

    // 1. Language State (Default Chinese as requested: "请整体适配中文，有中英文切换到按钮")
    private val _isChineseMode = MutableStateFlow(prefs.getBoolean("is_chinese", true))
    val isChineseMode: StateFlow<Boolean> = _isChineseMode.asStateFlow()

    // 2. Wallpaper Preferences ("有自定义软件背景壁纸的功能")
    private val _wallpaperType = MutableStateFlow(prefs.getString("wallpaper_type", "Gradient") ?: "Gradient")
    val wallpaperType: StateFlow<String> = _wallpaperType.asStateFlow()

    private val _customWallpaperUri = MutableStateFlow(prefs.getString("custom_wallpaper_uri", "") ?: "")
    val customWallpaperUri: StateFlow<String> = _customWallpaperUri.asStateFlow()

    private val _wallpaperOpacity = MutableStateFlow(prefs.getFloat("wallpaper_opacity", 0.4f))
    val wallpaperOpacity: StateFlow<Float> = _wallpaperOpacity.asStateFlow()

    // 3. Custom Node Selection for Subscription Generator
    private val _selectedNodeIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedNodeIds: StateFlow<Set<Long>> = _selectedNodeIds.asStateFlow()

    private val _customToken = MutableStateFlow(prefs.getString("custom_token", "mytoken") ?: "mytoken")
    val customToken: StateFlow<String> = _customToken.asStateFlow()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    init {
        updateLocalIp()
        val savedPort = prefs.getInt("server_port", 8080)
        val savedToken = prefs.getString("server_token", "") ?: ""
        if (savedToken.isNotBlank()) {
            _customToken.value = savedToken
        }
        httpServer.startServer(port = savedPort, secretToken = savedToken)
    }

    fun toggleLanguage() {
        val nextMode = !_isChineseMode.value
        _isChineseMode.value = nextMode
        prefs.edit().putBoolean("is_chinese", nextMode).apply()
        _statusMessage.value = if (nextMode) "语言已切换为 中文" else "Language switched to English"
    }

    fun setWallpaperType(type: String) {
        _wallpaperType.value = type
        prefs.edit().putString("wallpaper_type", type).apply()
    }

    fun setCustomWallpaperUri(uri: String) {
        _customWallpaperUri.value = uri
        _wallpaperType.value = "Custom"
        prefs.edit()
            .putString("custom_wallpaper_uri", uri)
            .putString("wallpaper_type", "Custom")
            .apply()
    }

    fun setWallpaperOpacity(opacity: Float) {
        _wallpaperOpacity.value = opacity
        prefs.edit().putFloat("wallpaper_opacity", opacity).apply()
    }

    fun setCustomToken(token: String) {
        _customToken.value = token
        prefs.edit().putString("custom_token", token).apply()
    }

    fun toggleNodeSelection(nodeId: Long) {
        val current = _selectedNodeIds.value.toMutableSet()
        if (current.contains(nodeId)) {
            current.remove(nodeId)
        } else {
            current.add(nodeId)
        }
        _selectedNodeIds.value = current
    }

    fun setSelectedNodeIds(ids: Set<Long>) {
        _selectedNodeIds.value = ids
    }

    fun selectAllNodes() {
        val allIds = nodes.value.map { it.id }.toSet()
        _selectedNodeIds.value = allIds
    }

    fun clearSelectedNodes() {
        _selectedNodeIds.value = emptySet()
    }

    fun updateLocalIp() {
        viewModelScope.launch(Dispatchers.IO) {
            _localIp.value = NetworkUtils.getLocalIpAddress()
        }
    }

    fun setRoutingMode(mode: String) {
        _routingMode.value = mode
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun toggleServer(port: Int = 8080, token: String = "") {
        prefs.edit().putInt("server_port", port).putString("server_token", token).apply()
        if (token.isNotBlank()) {
            setCustomToken(token)
        }
        if (httpServer.isRunning.value) {
            httpServer.stopServer()
            _statusMessage.value = if (_isChineseMode.value) "局域网服务器已停止" else "Local LAN server stopped"
        } else {
            updateLocalIp()
            httpServer.startServer(port, token)
            _statusMessage.value = if (_isChineseMode.value) "服务器已启动: http://${_localIp.value}:$port" else "Server started on http://${_localIp.value}:$port"
        }
    }

    fun addSubscription(name: String, urlOrContent: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isRefreshing.value = true
            try {
                val isUrl = urlOrContent.startsWith("http://", ignoreCase = true) ||
                            urlOrContent.startsWith("https://", ignoreCase = true)

                val subName = if (name.isBlank()) (if (isUrl) "订阅链接 ${System.currentTimeMillis() % 1000}" else "自定义节点") else name
                val initialSub = Subscription(name = subName, url = if (isUrl) urlOrContent else "")

                val subId = db.subscriptionDao().insertSubscription(initialSub)

                var rawText = urlOrContent
                if (isUrl) {
                    rawText = fetchUrlContent(urlOrContent)
                }

                val parsedNodes = SubscriptionParser.parseContent(rawText, subId)

                // Save nodes
                db.proxyNodeDao().deleteNodesForSubscription(subId)
                db.proxyNodeDao().insertNodes(parsedNodes)

                // Update sub node count
                db.subscriptionDao().updateSubscription(
                    initialSub.copy(
                        id = subId,
                        rawContent = rawText,
                        nodeCount = parsedNodes.size,
                        lastUpdated = System.currentTimeMillis()
                    )
                )

                _statusMessage.value = if (_isChineseMode.value) "成功导入 ${parsedNodes.size} 个节点" else "Successfully imported ${parsedNodes.size} nodes"
            } catch (e: Exception) {
                _statusMessage.value = if (_isChineseMode.value) "导入失败: ${e.localizedMessage}" else "Error importing: ${e.localizedMessage}"
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun refreshSubscription(subscription: Subscription) {
        if (subscription.url.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            _isRefreshing.value = true
            try {
                val rawText = fetchUrlContent(subscription.url)
                val parsedNodes = SubscriptionParser.parseContent(rawText, subscription.id)

                db.proxyNodeDao().deleteNodesForSubscription(subscription.id)
                db.proxyNodeDao().insertNodes(parsedNodes)

                db.subscriptionDao().updateSubscription(
                    subscription.copy(
                        rawContent = rawText,
                        nodeCount = parsedNodes.size,
                        lastUpdated = System.currentTimeMillis()
                    )
                )

                _statusMessage.value = if (_isChineseMode.value) "已更新 '${subscription.name}': ${parsedNodes.size} 个节点" else "Updated '${subscription.name}': ${parsedNodes.size} nodes"
            } catch (e: Exception) {
                _statusMessage.value = if (_isChineseMode.value) "刷新失败: ${e.localizedMessage}" else "Refresh failed: ${e.localizedMessage}"
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun deleteSubscription(subscriptionId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            db.proxyNodeDao().deleteNodesForSubscription(subscriptionId)
            db.subscriptionDao().deleteSubscription(subscriptionId)
            _statusMessage.value = if (_isChineseMode.value) "订阅已删除" else "Subscription deleted"
        }
    }

    fun toggleNodeEnabled(node: ProxyNode) {
        viewModelScope.launch(Dispatchers.IO) {
            db.proxyNodeDao().updateNode(node.copy(enabled = !node.enabled))
        }
    }

    fun testNodePing(node: ProxyNode) {
        viewModelScope.launch(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            var ping = -2
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(node.server, node.port), 3000)
                ping = (System.currentTimeMillis() - start).toInt()
                socket.close()
            } catch (e: Exception) {
                ping = -2
            }
            db.proxyNodeDao().updatePing(node.id, ping)
        }
    }

    fun testAllNodesPing() {
        viewModelScope.launch(Dispatchers.IO) {
            _isRefreshing.value = true
            val currentNodes = nodes.value
            currentNodes.forEach { node ->
                val start = System.currentTimeMillis()
                var ping = -2
                try {
                    val socket = Socket()
                    socket.connect(InetSocketAddress(node.server, node.port), 2000)
                    ping = (System.currentTimeMillis() - start).toInt()
                    socket.close()
                } catch (e: Exception) {
                    ping = -2
                }
                db.proxyNodeDao().updatePing(node.id, ping)
            }
            _isRefreshing.value = false
            _statusMessage.value = if (_isChineseMode.value) "批量延迟测试完成" else "Batch latency test completed"
        }
    }

    fun clearAllLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            db.serverLogDao().clearLogs()
        }
    }

    fun deleteNode(nodeId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            db.proxyNodeDao().deleteNodeById(nodeId)
            _statusMessage.value = if (_isChineseMode.value) "节点已删除" else "Node deleted"
        }
    }

    fun deduplicateNodes() {
        viewModelScope.launch(Dispatchers.IO) {
            val allNodes = db.proxyNodeDao().getAllNodes()
            if (allNodes.isEmpty()) {
                _statusMessage.value = if (_isChineseMode.value) "暂无节点" else "No nodes to deduplicate"
                return@launch
            }

            val seenKeys = mutableSetOf<String>()
            val idsToDelete = mutableListOf<Long>()

            for (node in allNodes) {
                val key = if (node.rawUri.isNotBlank()) {
                    node.rawUri.trim()
                } else {
                    "${node.protocol.lowercase()}://${node.uuidOrPassword}@${node.server.lowercase()}:${node.port}${node.path}${node.sni}"
                }

                if (seenKeys.contains(key)) {
                    idsToDelete.add(node.id)
                } else {
                    seenKeys.add(key)
                }
            }

            if (idsToDelete.isNotEmpty()) {
                idsToDelete.forEach { id ->
                    db.proxyNodeDao().deleteNodeById(id)
                }
                _statusMessage.value = if (_isChineseMode.value) "成功删除 ${idsToDelete.size} 个重复节点" else "Removed ${idsToDelete.size} duplicate nodes"
            } else {
                _statusMessage.value = if (_isChineseMode.value) "未发现重复节点" else "No duplicate nodes found"
            }
        }
    }

    fun deleteAllNodes() {
        viewModelScope.launch(Dispatchers.IO) {
            db.proxyNodeDao().deleteAllNodes()
            _statusMessage.value = if (_isChineseMode.value) "已清空所有节点" else "All nodes deleted"
        }
    }

    fun deleteInvalidNodes() {
        viewModelScope.launch(Dispatchers.IO) {
            val allNodes = db.proxyNodeDao().getAllNodes()
            val invalidNodes = allNodes.filter { it.pingMs < 0 }
            if (invalidNodes.isEmpty()) {
                _statusMessage.value = if (_isChineseMode.value) "未发现测试无效的节点" else "No invalid nodes found"
                return@launch
            }
            invalidNodes.forEach { node ->
                db.proxyNodeDao().deleteNodeById(node.id)
            }
            _statusMessage.value = if (_isChineseMode.value) "已成功删除 ${invalidNodes.size} 个无效节点" else "Deleted ${invalidNodes.size} invalid nodes"
        }
    }

    fun saveCustomSubscription(name: String, format: String, token: String, nodeIds: List<Long>, id: Long = 0) {
        viewModelScope.launch(Dispatchers.IO) {
            val subName = if (name.isBlank()) "自定义订阅 (${format.uppercase()})" else name
            val idsStr = nodeIds.joinToString(",")
            val customSub = SavedCustomSubscription(
                id = id,
                name = subName,
                format = format,
                token = token,
                nodeIds = idsStr
            )
            db.savedCustomSubDao().insertSavedCustomSub(customSub)
            _statusMessage.value = if (_isChineseMode.value) "已保存自定义订阅: $subName" else "Saved custom sub: $subName"
        }
    }

    fun deleteCustomSubscription(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            db.savedCustomSubDao().deleteSavedCustomSub(id)
            _statusMessage.value = if (_isChineseMode.value) "自定义订阅已删除" else "Custom sub deleted"
        }
    }

    private suspend fun fetchUrlContent(urlStr: String): String = withContext(Dispatchers.IO) {
        val userAgents = listOf(
            "SingBoxSub-Android/1.0 (sing-box; mihomo)",
            "ClashForAndroid/2.5.12",
            "v2rayN/6.0",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        )
        var lastException: Exception? = null
        for (ua in userAgents) {
            try {
                val request = Request.Builder()
                    .url(urlStr)
                    .header("User-Agent", ua)
                    .build()
                okHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        if (body.isNotBlank()) return@withContext body
                    } else {
                        lastException = Exception("HTTP ${response.code}: ${response.message}")
                    }
                }
            } catch (e: Exception) {
                lastException = e
            }
        }
        throw lastException ?: Exception("Failed to fetch subscription content")
    }
}
