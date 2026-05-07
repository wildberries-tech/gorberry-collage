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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import ru.wildberries.collage.CollageEngine
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
            items = demoCases,
            key = { it.title },
        ) { demoCase ->
            DemoFeedMessage(
                engine = if (demoCase.allowHeightOverflow) {
                    overflowEngine
                } else {
                    normalEngine
                },
                demoCase = demoCase,
                debugOverlay = debugOverlay,
            )

            Spacer(modifier = Modifier.height(20.dp))
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
        searchQuality = CollageEngine.SearchQuality.Balanced
        this.allowHeightOverflow = allowHeightOverflow
        tileFitPolicy = if (coverOnly) {
            TileFitPolicy.CoverOnly
        } else {
            TileFitPolicy.Auto
        }
    }
}

@Composable
private fun DemoFeedMessage(
    engine: CollageEngine,
    demoCase: DemoCase,
    debugOverlay: Boolean,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
        ) {
            Text(
                text = demoCase.title,
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

            Spacer(modifier = Modifier.height(12.dp))

            widthVariants.forEach { widthVariant ->
                DemoWidthVariantMessage(
                    engine = engine,
                    demoCase = demoCase,
                    widthVariant = widthVariant,
                    debugOverlay = debugOverlay,
                )

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun DemoWidthVariantMessage(
    engine: CollageEngine,
    demoCase: DemoCase,
    widthVariant: WidthVariant,
    debugOverlay: Boolean,
) {
    val density = LocalDensity.current

    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = widthVariant.title,
            style = MaterialTheme.typography.labelMedium,
        )

        Text(
            text = widthVariant.description,
            style = MaterialTheme.typography.labelSmall,
        )

        Spacer(modifier = Modifier.height(6.dp))

        Box(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(widthVariant.fraction)
                    .align(Alignment.CenterEnd)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(8.dp),
            ) {
                BoxWithConstraints(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    val widthPx = with(density) { maxWidth.toPx() }
                    val minHeightPx = with(density) { demoCase.minHeight.toPx() }
                    val maxHeightPx = demoCase.maxHeight?.let { maxHeight ->
                        with(density) { maxHeight.toPx() }
                    } ?: defaultMessageMaxHeightPx(widthPx, density)

                    val layout = remember(
                        engine,
                        demoCase,
                        widthVariant,
                        widthPx,
                        minHeightPx,
                        maxHeightPx,
                    ) {
                        calculateLayout(
                            engine = engine,
                            demoCase = demoCase,
                            widthPx = widthPx,
                            minHeightPx = minHeightPx,
                            maxHeightPx = maxHeightPx,
                        )
                    }

                    Column {
                        Text(
                            text = buildString {
                                append(demoCase.images.size)
                                append(" images")
                                append(", width=")
                                append(widthPx.roundToInt())
                                append("px")
                                append(", height=")
                                append(layout.height.roundToInt())
                                append("px")

                                demoCase.maxHeight?.let {
                                    append(", maxHeight=")
                                    append(maxHeightPx.roundToInt())
                                    append("px")
                                }
                            },
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
    }
}

private fun defaultMessageMaxHeightPx(
    widthPx: Float,
    density: androidx.compose.ui.unit.Density,
): Float {
    return with(density) {
        (widthPx * 1.35f)
            .coerceIn(
                minimumValue = 220.dp.toPx(),
                maximumValue = 520.dp.toPx(),
            )
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
        collageImages,
        width = widthPx,
        minHeight = minHeightPx,
        maxHeight = maxHeightPx,
    )
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
        Image(
            painter = painterResource(id = tile.imageId),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .offset(
                    x = with(density) { (contentBox.x - box.x).toDp() },
                    y = with(density) { (contentBox.y - box.y).toDp() },
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
