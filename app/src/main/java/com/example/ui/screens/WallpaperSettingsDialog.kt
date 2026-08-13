package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.MainViewModel

@Composable
fun WallpaperSettingsDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val isZh by viewModel.isChineseMode.collectAsStateWithLifecycle()
    val wallpaperType by viewModel.wallpaperType.collectAsStateWithLifecycle()
    val opacity by viewModel.wallpaperOpacity.collectAsStateWithLifecycle()
    val customUri by viewModel.customWallpaperUri.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                // ignore
            }
            viewModel.setCustomWallpaperUri(it.toString())
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Image,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isZh) "自定义应用背景壁纸" else "App Wallpaper Settings",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (isZh) "选择背景样式:" else "Select Preset Style:",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    WallpaperPresetCard(
                        name = if (isZh) "无" else "None",
                        isSelected = wallpaperType == "None",
                        bgBrush = Brush.linearGradient(listOf(Color(0xFF1E293B), Color(0xFF0F172A))),
                        onClick = { viewModel.setWallpaperType("None") },
                        modifier = Modifier.weight(1f)
                    )

                    WallpaperPresetCard(
                        name = if (isZh) "炫彩" else "Gradient",
                        isSelected = wallpaperType == "Gradient",
                        bgBrush = Brush.linearGradient(listOf(Color(0xFF312E81), Color(0xFF581C87), Color(0xFF0F172A))),
                        onClick = { viewModel.setWallpaperType("Gradient") },
                        modifier = Modifier.weight(1f)
                    )

                    WallpaperPresetCard(
                        name = if (isZh) "赛博" else "Cyber",
                        isSelected = wallpaperType == "Cyberpunk",
                        bgBrush = Brush.linearGradient(listOf(Color(0xFF0284C7), Color(0xFF7C3AED), Color(0xFF1E1B4B))),
                        onClick = { viewModel.setWallpaperType("Cyberpunk") },
                        modifier = Modifier.weight(1f)
                    )

                    WallpaperPresetCard(
                        name = if (isZh) "深邃" else "Dark",
                        isSelected = wallpaperType == "DeepDark",
                        bgBrush = Brush.linearGradient(listOf(Color(0xFF090D16), Color(0xFF111827))),
                        onClick = { viewModel.setWallpaperType("DeepDark") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Custom Gallery Image Option
                OutlinedButton(
                    onClick = { galleryLauncher.launch("image/*") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (customUri.isNotEmpty() && wallpaperType == "Custom") {
                            if (isZh) "已选自定义相册壁纸 (点击更换)" else "Custom Image Active (Click to change)"
                        } else {
                            if (isZh) "从相册选择自定义壁纸图片" else "Choose Picture from Gallery"
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Opacity Dimmer Slider
                Text(
                    text = "${if (isZh) "背景遮罩透明度" else "Overlay Dimming"}: ${(opacity * 100).toInt()}%",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Slider(
                    value = opacity,
                    onValueChange = { viewModel.setWallpaperOpacity(it) },
                    valueRange = 0.0f..0.8f,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isZh) "保存并关闭" else "Save & Apply")
                }
            }
        }
    }
}

@Composable
fun WallpaperPresetCard(
    name: String,
    isSelected: Boolean,
    bgBrush: Brush,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(64.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bgBrush)
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (isSelected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
