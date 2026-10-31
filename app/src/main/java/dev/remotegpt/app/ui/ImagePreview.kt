@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package dev.remotegpt.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import dev.remotegpt.app.R
import dev.remotegpt.app.tr
import java.io.File

@Composable internal fun ImageThumbnail(
    model: Any,
    name: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    var state by remember { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }
    Box(modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainer)) {
        AsyncImage(
            model = model,
            contentDescription = name,
            contentScale = contentScale,
            onState = { state = it },
            modifier = Modifier.fillMaxSize(),
        )
        if (state is AsyncImagePainter.State.Loading) {
            CircularProgressIndicator(Modifier.size(18.dp).align(Alignment.Center), strokeWidth = 2.dp)
        } else if (state is AsyncImagePainter.State.Error) {
            Icon(WorkbenchIcons.Image, tr(R.string.ui_image_unavailable), Modifier.size(20.dp).align(Alignment.Center),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable internal fun RemoteImageViewer(path: String, onClose: () -> Unit) {
    val context = LocalContext.current
    var scale by remember(path) { mutableFloatStateOf(1f) }
    var offsetX by remember(path) { mutableFloatStateOf(0f) }
    var offsetY by remember(path) { mutableFloatStateOf(0f) }
    var state by remember(path) { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }
    val reset = {
        scale = 1f
        offsetX = 0f
        offsetY = 0f
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().systemBarsPadding()) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(File(path)).build(),
                contentDescription = tr(R.string.ui_remote_image_preview),
                contentScale = ContentScale.Fit,
                onState = { state = it },
                modifier = Modifier.fillMaxSize()
                    .pointerInput(path) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 6f)
                            val limit = (scale - 1f) * 720f
                            offsetX = (offsetX + pan.x).coerceIn(-limit, limit)
                            offsetY = (offsetY + pan.y).coerceIn(-limit, limit)
                        }
                    }
                    .pointerInput(path) { detectTapGestures(onDoubleTap = { reset() }) }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offsetX
                        translationY = offsetY
                    },
            )
            if (state is AsyncImagePainter.State.Loading || state is AsyncImagePainter.State.Empty) {
                CircularProgressIndicator(Modifier.size(32.dp).align(Alignment.Center), strokeWidth = 2.dp)
            }
            if (state is AsyncImagePainter.State.Error) {
                Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(WorkbenchIcons.Image, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(tr(R.string.ui_image_unavailable), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Surface(Modifier.align(Alignment.TopStart).padding(8.dp), shape = RoundedCornerShape(20.dp),
                color = glassSurfaceColor(lightAlpha = .88f, darkAlpha = .88f), border = BorderStroke(1.dp, glassBorderColor())) {
                IconButton(onClick = onClose) { Icon(WorkbenchIcons.Close, tr(R.string.ui_close), Modifier.size(22.dp)) }
            }
            if (scale > 1.01f) {
                TextButton(onClick = reset, Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)) {
                    Text(tr(R.string.ui_reset_image_zoom))
                }
            }
        }
    }
}
