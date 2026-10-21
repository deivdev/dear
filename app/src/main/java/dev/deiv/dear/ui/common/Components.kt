package dev.deiv.dear.ui.common

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.video.videoFrameMillis
import dev.deiv.dear.R
import dev.deiv.dear.data.model.CatalogStatus
import dev.deiv.dear.data.model.EntryType
import java.io.File

/** Dot-matrix page title with optional icon actions on the right; the same on every top-level page. */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        actions()
    }
}

/** Small uppercase mono label, for metadata and section names. */
@Composable
fun MonoLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    maxLines: Int = Int.MAX_VALUE,
) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Three dots lighting up in turn: something is being worked on. */
@Composable
fun WorkingDots(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.tertiary,
    dotSize: Dp = 6.dp,
) {
    val transition = rememberInfiniteTransition(label = "dots")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1200), RepeatMode.Restart),
        label = "phase",
    )
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(dotSize * 0.6f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { i ->
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .alpha(if (phase.toInt() == i) 1f else 0.25f)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

@Composable
fun Dot(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 6.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagPills(tags: List<String>, modifier: Modifier = Modifier) {
    if (tags.isEmpty()) return
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        tags.forEach { tag ->
            Text(
                text = tag,
                modifier = Modifier
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** Catalog state of an entry; nothing at all once it is done. */
@Composable
fun CatalogStatusLabel(status: CatalogStatus, modifier: Modifier = Modifier) {
    when (status) {
        CatalogStatus.DONE -> Unit
        CatalogStatus.PROCESSING -> Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WorkingDots(dotSize = 4.dp)
            MonoLabel(stringResource(R.string.status_processing), color = MaterialTheme.colorScheme.tertiary)
        }
        CatalogStatus.PENDING -> Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Dot(MaterialTheme.colorScheme.onSurfaceVariant, size = 4.dp)
            MonoLabel(stringResource(R.string.status_pending))
        }
        CatalogStatus.FAILED -> Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Dot(MaterialTheme.colorScheme.error, size = 4.dp)
            MonoLabel(stringResource(R.string.status_failed), color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
fun typeLabel(type: EntryType): String = stringResource(
    when (type) {
        EntryType.IMAGE -> R.string.type_image
        EntryType.LINK -> R.string.type_link
        EntryType.TEXT -> R.string.type_text
        EntryType.VIDEO -> R.string.type_video
        EntryType.AUDIO -> R.string.type_audio
        EntryType.FILE -> R.string.type_file
    }
)

/** Rounded pill button with a mono uppercase label: filled (or amber when [accent]) or outlined. */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    outlined: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val (container, content) = when {
        outlined -> Color.Transparent to colors.onSurface
        accent -> colors.tertiary to colors.onTertiary
        else -> colors.primary to colors.onPrimary
    }
    Box(
        modifier = modifier
            .clip(CircleShape)
            .then(if (outlined) Modifier.border(1.dp, colors.outline, CircleShape) else Modifier)
            .background(if (enabled) container else colors.surfaceContainerHigh)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) content else colors.onSurfaceVariant,
        )
    }
}

/**
 * A photo, or a frame of a video, from app storage; Coil handles downsampling, EXIF rotation and
 * caching. Video frames are taken a second in, past the usual black first frame.
 */
@Composable
fun EntryImage(
    path: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    isVideo: Boolean = false,
) {
    val context = LocalContext.current
    val request = remember(path, isVideo) {
        ImageRequest.Builder(context)
            .data(File(path))
            .apply { if (isVideo) videoFrameMillis(VIDEO_FRAME_MS) }
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
    )
}

/** Video thumbnail with a "▶ 0:42" badge. */
@Composable
fun VideoThumbnail(
    path: String,
    durationMs: Long?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    Box(modifier = modifier) {
        EntryImage(path, contentDescription = null, contentScale = contentScale, isVideo = true, modifier = Modifier.matchParentSize())
        Text(
            text = "▶" + (durationMs?.let { " " + formatDuration(it) } ?: ""),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
        )
    }
}

private const val VIDEO_FRAME_MS = 1000L
