package ru.wildberries.collage.sample

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import ru.wildberries.collage.CollageEngine
import ru.wildberries.collage.SearchQuality
import ru.wildberries.collage.model.CollageLayout
import ru.wildberries.collage.model.CollageTile
import ru.wildberries.collage.model.TileFitPolicy
import kotlin.math.roundToInt

@Composable
fun CollageDemoScreen() {
    var debugOverlay by rememberSaveable { mutableStateOf(false) }
    var zeroSpacing by rememberSaveable { mutableStateOf(false) }
    var coverOnly by rememberSaveable { mutableStateOf(true) }

    val normalEngine = remember(zeroSpacing, coverOnly) {
        createDemoEngine(
            zeroSpacing = zeroSpacing,
            coverOnly = coverOnly,
            allowHeightOverflow = false,
        )
    }

    val overflowEngine = remember(zeroSpacing, coverOnly) {
        createDemoEngine(
            zeroSpacing = zeroSpacing,
            coverOnly = coverOnly,
            allowHeightOverflow = true,
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        item {
            Text(
                text = "Gorberry Collage",
                style = MaterialTheme.typography.headlineMedium,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = zeroSpacing,
                    onCheckedChange = { zeroSpacing = it },
                )
                Text("Zero spacing")
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = coverOnly,
                    onCheckedChange = { coverOnly = it },
                )
                Text("Cover only")
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = debugOverlay,
                    onCheckedChange = { debugOverlay = it },
                )
                Text("Debug overlay")
            }

            Spacer(modifier = Modifier.height(12.dp))
        }

        items(
            items = demoFeedItems,
            key = { it.key },
        ) { feedItem ->
            DemoFeedBubble(
                engine = if (feedItem.demoCase.allowHeightOverflow) {
                    overflowEngine
                } else {
                    normalEngine
                },
                feedItem = feedItem,
                debugOverlay = debugOverlay,
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

private fun createDemoEngine(
    zeroSpacing: Boolean,
    coverOnly: Boolean,
    allowHeightOverflow: Boolean,
): CollageEngine {
    return CollageEngine {
        spacing = if (zeroSpacing) 0f else 6f
        minTileWidth = 42f
        minTileHeight = 42f
        maxTilesPerRow = 4
        maxLandscapeTilesPerRow = 2
        searchQuality = SearchQuality.Balanced
        this.allowHeightOverflow = allowHeightOverflow
        tileFitPolicy = if (coverOnly) {
            TileFitPolicy.CoverOnly
        } else {
            TileFitPolicy.Auto
        }
    }
}

@Composable
private fun DemoFeedBubble(
    engine: CollageEngine,
    feedItem: DemoFeedItem,
    debugOverlay: Boolean,
) {
    val demoCase = feedItem.demoCase
    val widthVariant = feedItem.widthVariant

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
        ) {
            Text(
                text = "${demoCase.title} · ${widthVariant.title}",
                style = MaterialTheme.typography.titleMedium,
            )

            Text(
                text = demoCase.description,
                style = MaterialTheme.typography.bodySmall,
            )

            if (demoCase.allowHeightOverflow) {
                Text(
                    text = "Height overflow is enabled for this case.",
                    style = MaterialTheme.typography.labelSmall,
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            DemoSingleWidthBubble(
                engine = engine,
                demoCase = demoCase,
                widthVariant = widthVariant,
                debugOverlay = debugOverlay,
            )
        }
    }
}

@Composable
private fun DemoSingleWidthBubble(
    engine: CollageEngine,
    demoCase: DemoCase,
    widthVariant: WidthVariant,
    debugOverlay: Boolean,
) {
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth(),
    ) {
        val parentWidthPx = with(density) { maxWidth.toPx() }
        val minBubbleWidthPx = with(density) { 140.dp.toPx() }
        val innerPaddingPx = with(density) { 8.dp.toPx() }

        val bubbleWidthPx = (parentWidthPx * widthVariant.fraction)
            .coerceIn(minBubbleWidthPx, parentWidthPx)

        val layoutWidthPx = (bubbleWidthPx - innerPaddingPx * 2f)
            .coerceAtLeast(1f)

        val minHeightPx = with(density) { demoCase.minHeight.toPx() }
        val maxHeightPx = demoCase.maxHeight?.let { maxHeight ->
            with(density) { maxHeight.toPx() }
        } ?: defaultMessageMaxHeightPx(layoutWidthPx, density)

        val layout = remember(
            engine,
            demoCase,
            widthVariant,
            layoutWidthPx,
            minHeightPx,
            maxHeightPx,
        ) {
            calculateLayout(
                engine = engine,
                demoCase = demoCase,
                widthPx = layoutWidthPx,
                minHeightPx = minHeightPx,
                maxHeightPx = maxHeightPx,
            )
        }

        Box(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .width(with(density) { bubbleWidthPx.toDp() })
                    .align(Alignment.CenterEnd)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(8.dp),
            ) {
                Text(
                    text = metadataText(
                        demoCase = demoCase,
                        layout = layout,
                        layoutWidthPx = layoutWidthPx,
                        maxHeightPx = maxHeightPx,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                )

                Spacer(modifier = Modifier.height(6.dp))

                CollageLayoutView(
                    layout = layout,
                    debugOverlay = debugOverlay,
                )
            }
        }
    }
}

private fun calculateLayout(
    engine: CollageEngine,
    demoCase: DemoCase,
    widthPx: Float,
    minHeightPx: Float,
    maxHeightPx: Float,
): CollageLayout {
    val collageImages = demoCase.images.map { it.toCollageImage() }

    return engine.layout(
        images = collageImages,
        width = widthPx,
        minHeight = minHeightPx,
        maxHeight = maxHeightPx,
    )
}

private fun defaultMessageMaxHeightPx(
    widthPx: Float,
    density: Density,
): Float {
    return with(density) {
        (widthPx * 1.35f).coerceIn(
            minimumValue = 220.dp.toPx(),
            maximumValue = 520.dp.toPx(),
        )
    }
}

private fun metadataText(
    demoCase: DemoCase,
    layout: CollageLayout,
    layoutWidthPx: Float,
    maxHeightPx: Float,
): String {
    return buildString {
        append(demoCase.images.size)
        append(" images")
        append(", width=")
        append(layoutWidthPx.roundToInt())
        append("px")
        append(", height=")
        append(layout.height.roundToInt())
        append("px")
        append(", maxHeight=")
        append(maxHeightPx.roundToInt())
        append("px")

        if (demoCase.allowHeightOverflow) {
            append(", overflow allowed")
        }
    }
}

@Composable
private fun CollageLayoutView(
    layout: CollageLayout,
    debugOverlay: Boolean,
) {
    val density = LocalDensity.current

    Box(
        modifier = Modifier
            .size(
                width = with(density) { layout.width.toDp() },
                height = with(density) { layout.height.toDp() },
            )
            .clip(RoundedCornerShape(12.dp)),
    ) {
        layout.rows.forEach { row ->
            row.tiles.forEach { tile ->
                CollageTileView(
                    tile = tile,
                    debugOverlay = debugOverlay,
                )
            }
        }
    }
}

@Composable
private fun CollageTileView(
    tile: CollageTile,
    debugOverlay: Boolean,
) {
    val density = LocalDensity.current
    val box = tile.box
    val contentBox = tile.contentBox

    Box(
        modifier = Modifier
            .offset(
                x = with(density) { box.x.toDp() },
                y = with(density) { box.y.toDp() },
            )
            .size(
                width = with(density) { box.width.toDp() },
                height = with(density) { box.height.toDp() },
            )
            .clipToBounds()
            .then(
                if (debugOverlay) {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.primary)
                } else {
                    Modifier
                }
            ),
    ) {
        AsyncImage(
            model = tile.imageId,
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .offset(
                    x = with(density) { tile.contentOffsetX.toDp() },
                    y = with(density) { tile.contentOffsetY.toDp() },
                )
                .requiredSize(
                    width = with(density) { contentBox.width.toDp() },
                    height = with(density) { contentBox.height.toDp() },
                ),
        )

        if (debugOverlay) {
            Text(
                text = tile.debugText(),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

private fun CollageTile.debugText(): String {
    val fitName = fit.toString()
    val isContain = fitName.contains("CONTAIN", ignoreCase = true)

    val metric = if (isContain) {
        containGapRatio()
    } else {
        cropRatio
    }

    val metricName = if (isContain) {
        "gap"
    } else {
        "crop"
    }

    return "$fitName $metricName=${(metric * 100f).roundToInt()}%"
}

private fun CollageTile.containGapRatio(): Float {
    val boxArea = (box.width * box.height).coerceAtLeast(1f)
    val contentArea = (contentBox.width * contentBox.height).coerceAtLeast(0f)
    return (1f - contentArea / boxArea).coerceIn(0f, 1f)
}
