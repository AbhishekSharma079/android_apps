package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.FileCategory
import com.example.ui.theme.FastShareBlue
import com.example.ui.theme.FastShareBlueDark
import com.example.ui.theme.FastShareCyan
import com.example.ui.theme.FastShareGreen
import com.example.ui.theme.FastShareOrange
import com.example.ui.theme.FastSharePurple
import com.example.ui.theme.FastShareRed
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FastShareTopAppBar(
    title: String,
    onBackClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        navigationIcon = {
            if (onBackClick != null) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier.testTag("nav_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}

@Composable
fun FileTypeIconBadge(
    mimeType: String,
    fileName: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp
) {
    val (bgColor, icon) = getFileTypeAttributes(mimeType, fileName)
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = fileName,
            tint = bgColor,
            modifier = Modifier.size(size * 0.55f)
        )
    }
}

fun getFileTypeAttributes(mimeType: String, fileName: String): Pair<Color, ImageVector> {
    val lowerMime = mimeType.lowercase()
    val lowerName = fileName.lowercase()
    return when {
        lowerMime.startsWith("image/") || lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") || lowerName.endsWith(".png") -> {
            FastShareGreen to Icons.Default.Image
        }
        lowerMime.startsWith("video/") || lowerName.endsWith(".mp4") || lowerName.endsWith(".mkv") || lowerName.endsWith(".mov") -> {
            FastShareRed to Icons.Default.Movie
        }
        lowerMime.startsWith("audio/") || lowerName.endsWith(".mp3") || lowerName.endsWith(".wav") || lowerName.endsWith(".m4a") -> {
            FastSharePurple to Icons.Default.AudioFile
        }
        lowerName.endsWith(".zip") || lowerName.endsWith(".rar") || lowerName.endsWith(".7z") -> {
            FastShareOrange to Icons.Default.FolderZip
        }
        lowerMime.contains("android.package-archive") || lowerName.endsWith(".apk") -> {
            FastShareGreen to Icons.Default.Android
        }
        lowerMime.contains("pdf") || lowerName.endsWith(".pdf") || lowerName.endsWith(".doc") || lowerName.endsWith(".docx") || lowerName.endsWith(".txt") -> {
            FastShareBlue to Icons.Default.Description
        }
        else -> {
            FastShareCyan to Icons.Default.Folder
        }
    }
}

@Composable
fun FastShareBottomBar(
    currentRoute: String,
    onNavigate: (String) -> Unit
) {
    NavigationBar(
        modifier = Modifier
            .navigationBarsPadding()
            .testTag("bottom_nav_bar"),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
    ) {
        val items = listOf(
            Triple("home", "Home", Icons.Default.Home),
            Triple("received", "Files", Icons.Default.Folder),
            Triple("transfers", "Transfers", Icons.Default.SwapHoriz),
            Triple("settings", "More", Icons.Default.MoreHoriz)
        )

        items.forEach { (route, label, icon) ->
            val isSelected = currentRoute == route
            NavigationBarItem(
                selected = isSelected,
                onClick = { onNavigate(route) },
                icon = {
                    Icon(
                        imageVector = icon,
                        contentDescription = label
                    )
                },
                label = { Text(label, fontSize = 12.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = FastShareBlue,
                    selectedTextColor = FastShareBlue,
                    indicatorColor = FastShareBlue.copy(alpha = 0.15f),
                    unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                ),
                modifier = Modifier.testTag("nav_tab_$route")
            )
        }
    }
}

@Composable
fun PulsingRadarAnimation(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "radar")
    val wave1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave1"
    )
    val wave2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, delayMillis = 700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave2"
    )
    val wave3 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, delayMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave3"
    )

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val maxRadius = size.minDimension / 2f

        listOf(wave1, wave2, wave3).forEach { progress ->
            val radius = maxRadius * progress
            val alpha = (1f - progress).coerceIn(0f, 1f) * 0.4f
            drawCircle(
                color = FastShareBlue.copy(alpha = alpha),
                radius = radius,
                center = center
            )
        }

        // Center dot
        drawCircle(
            color = FastShareBlue,
            radius = 12.dp.toPx(),
            center = center
        )
    }
}

@Composable
fun ConfettiCanvas(modifier: Modifier = Modifier) {
    val colors = listOf(
        FastShareGreen,
        FastShareBlue,
        FastShareOrange,
        FastShareRed,
        FastSharePurple,
        FastShareCyan
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val random = Random(42)
        for (i in 0..45) {
            val x = random.nextFloat() * size.width
            val y = random.nextFloat() * (size.height * 0.5f)
            val color = colors[i % colors.size]
            val radius = random.nextFloat() * 6f + 3f
            drawCircle(
                color = color.copy(alpha = 0.8f),
                radius = radius,
                center = Offset(x, y)
            )
        }
    }
}
