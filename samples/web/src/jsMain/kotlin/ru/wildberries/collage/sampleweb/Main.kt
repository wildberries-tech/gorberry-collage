package ru.wildberries.collage.sampleweb

import kotlinx.browser.document
import org.w3c.dom.DocumentReadyState
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLImageElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.LOADING
import ru.wildberries.collage.CollageEngine
import ru.wildberries.collage.SearchQuality
import ru.wildberries.collage.model.CollageLayout
import ru.wildberries.collage.model.CollageTile
import ru.wildberries.collage.model.TileFitPolicy
import kotlin.math.roundToInt

private var zeroSpacing = false
private var coverOnly = true
private var debugOverlay = false

fun main() {
    if (document.readyState == DocumentReadyState.LOADING) {
        document.addEventListener("DOMContentLoaded", { _ ->
            renderApp()
        })
    } else {
        renderApp()
    }
}

private fun renderApp() {
    val root = document.getElementById("root") as? HTMLElement
        ?: error("Root element with id='root' was not found")

    root.innerHTML = ""

    val normalEngine = createDemoEngine(
        zeroSpacing = zeroSpacing,
        coverOnly = coverOnly,
        allowHeightOverflow = false,
    )

    val overflowEngine = createDemoEngine(
        zeroSpacing = zeroSpacing,
        coverOnly = coverOnly,
        allowHeightOverflow = true,
    )

    root.appendChild(renderHeader())

    val availableWidth = (root.clientWidth - 24)
        .coerceAtLeast(1)
        .toFloat()

    demoFeedItems.forEach { feedItem ->
        val engine = if (feedItem.demoCase.allowHeightOverflow) {
            overflowEngine
        } else {
            normalEngine
        }

        root.appendChild(
            renderFeedBubble(
                engine = engine,
                feedItem = feedItem,
                availableWidth = availableWidth,
            )
        )
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

private fun renderHeader(): HTMLElement {
    val container = div("controls")

    val title = document.createElement("h1") as HTMLElement
    title.textContent = "Gorberry Collage"
    title.style.margin = "0 0 8px"
    container.appendChild(title)

    container.appendChild(
        checkboxRow(
            label = "Zero spacing",
            checked = zeroSpacing,
        ) { checked ->
            zeroSpacing = checked
            renderApp()
        }
    )

    container.appendChild(
        checkboxRow(
            label = "Cover only",
            checked = coverOnly,
        ) { checked ->
            coverOnly = checked
            renderApp()
        }
    )

    container.appendChild(
        checkboxRow(
            label = "Debug overlay",
            checked = debugOverlay,
        ) { checked ->
            debugOverlay = checked
            renderApp()
        }
    )

    return container
}

private fun checkboxRow(
    label: String,
    checked: Boolean,
    onChanged: (Boolean) -> Unit,
): HTMLElement {
    val row = div("control-row")
    val input = document.createElement("input") as HTMLInputElement
    input.type = "checkbox"
    input.checked = checked
    input.onchange = {
        onChanged(input.checked)
        null
    }

    val text = document.createElement("span") as HTMLElement
    text.textContent = label

    row.appendChild(input)
    row.appendChild(text)

    return row
}

private fun renderFeedBubble(
    engine: CollageEngine,
    feedItem: DemoFeedItem,
    availableWidth: Float,
): HTMLElement {
    val demoCase = feedItem.demoCase
    val widthVariant = feedItem.widthVariant

    val card = div("card")

    val title = div("title")
    title.textContent = "${demoCase.title} · ${widthVariant.title}"
    card.appendChild(title)

    val description = div("description")
    description.textContent = demoCase.description
    card.appendChild(description)

    if (demoCase.allowHeightOverflow) {
        val overflow = div("label")
        overflow.textContent = "Height overflow is enabled for this case."
        card.appendChild(overflow)
    }

    val bubbleWidth = (availableWidth * widthVariant.fraction)
        .coerceIn(140f, availableWidth)

    val innerPadding = 8f
    val layoutWidth = (bubbleWidth - innerPadding * 2f)
        .coerceAtLeast(1f)

    val maxHeight = demoCase.maxHeight
        ?: defaultMessageMaxHeight(layoutWidth)

    val layout = calculateLayout(
        engine = engine,
        demoCase = demoCase,
        width = layoutWidth,
        minHeight = demoCase.minHeight,
        maxHeight = maxHeight,
    )

    val bubble = div("bubble")
    bubble.style.width = "${bubbleWidth}px"

    val metadata = div("label")
    metadata.textContent = metadataText(
        demoCase = demoCase,
        layout = layout,
        layoutWidth = layoutWidth,
        maxHeight = maxHeight,
    )
    bubble.appendChild(metadata)

    bubble.appendChild(
        renderCollage(
            layout = layout,
            imagesById = demoCase.images.associateBy { it.id },
        )
    )

    card.appendChild(bubble)

    return card
}

private fun calculateLayout(
    engine: CollageEngine,
    demoCase: DemoCase,
    width: Float,
    minHeight: Float,
    maxHeight: Float,
): CollageLayout {
    return engine.layout(
        images = demoCase.images.map { it.toCollageImage() },
        width = width,
        minHeight = minHeight,
        maxHeight = maxHeight,
    )
}

private fun defaultMessageMaxHeight(width: Float): Float {
    return (width * 1.35f).coerceIn(
        minimumValue = 220f,
        maximumValue = 520f,
    )
}

private fun metadataText(
    demoCase: DemoCase,
    layout: CollageLayout,
    layoutWidth: Float,
    maxHeight: Float,
): String {
    return buildString {
        append(demoCase.images.size)
        append(" images")
        append(", width=")
        append(layoutWidth.roundToInt())
        append("px")
        append(", height=")
        append(layout.height.roundToInt())
        append("px")
        append(", maxHeight=")
        append(maxHeight.roundToInt())
        append("px")

        if (demoCase.allowHeightOverflow) {
            append(", overflow allowed")
        }
    }
}

private fun renderCollage(
    layout: CollageLayout,
    imagesById: Map<Int, DemoImage>,
): HTMLElement {
    val container = div()

    container.css("position", "relative")
    container.cssPx("width", layout.width)
    container.cssPx("height", layout.height)
    container.css("overflow", "hidden")
    container.css("border-radius", "12px")
    container.css("background", "#ddd")

    layout.rows.forEach { row ->
        row.tiles.forEach { tile ->
            val image = imagesById[tile.imageId] ?: return@forEach
            container.appendChild(renderTile(tile, image))
        }
    }

    return container
}

private fun renderTile(
    tile: CollageTile,
    image: DemoImage,
): HTMLElement {
    val box = tile.box
    val contentBox = tile.contentBox

    val tileElement = div()

    tileElement.css("position", "absolute")
    tileElement.cssPx("left", box.x)
    tileElement.cssPx("top", box.y)
    tileElement.cssPx("width", box.width)
    tileElement.cssPx("height", box.height)
    tileElement.css("overflow", "hidden")
    tileElement.css("background", "#eee")

    if (debugOverlay) {
        tileElement.css("outline", "1px solid #4c6fff")
    }

    val img = document.createElement("img") as HTMLImageElement

    img.src = image.url
    img.alt = ""

    img.css("position", "absolute")
    img.cssPx("left", contentBox.x - box.x)
    img.cssPx("top", contentBox.y - box.y)
    img.cssPx("width", contentBox.width)
    img.cssPx("height", contentBox.height)
    img.css("object-fit", "fill")
    img.css("display", "block")
    img.css("max-width", "none")

    tileElement.appendChild(img)

    if (debugOverlay) {
        val label = div("collage-debug-label")
        label.textContent = tile.debugText()
        tileElement.appendChild(label)
    }

    return tileElement
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

private fun div(className: String? = null): HTMLDivElement {
    val element = document.createElement("div") as HTMLDivElement

    if (className != null) {
        element.className = className
    }

    return element
}

private fun HTMLElement.css(
    name: String,
    value: String,
) {
    style.setProperty(name, value)
}

private fun HTMLElement.cssPx(
    name: String,
    value: Float,
) {
    css(name, "${value}px")
}