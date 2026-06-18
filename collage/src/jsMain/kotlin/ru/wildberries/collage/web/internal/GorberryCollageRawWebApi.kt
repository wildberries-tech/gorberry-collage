@file:OptIn(ExperimentalJsExport::class)

package ru.wildberries.collage.web.internal

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.js.JsName
import ru.wildberries.collage.CollageConfiguration
import ru.wildberries.collage.CollageEngine
import ru.wildberries.collage.SearchQuality
import ru.wildberries.collage.model.CollageImage
import ru.wildberries.collage.model.CollageTile
import ru.wildberries.collage.model.TileFit
import ru.wildberries.collage.model.TileFitPolicy

/**
 * Internal image model for the JavaScript wrapper.
 *
 * Product code should not use this class directly.
 * The public npm API accepts regular JavaScript objects:
 *
 * { id, width, height }
 */
@JsExport
class RawCollageImage(
    val imageIndex: Int,
    val width: Double,
    val height: Double,
)

/**
 * Internal layout result for the JavaScript wrapper.
 *
 * The public npm API reshapes this into a normal TypeScript object:
 *
 * {
 *   width,
 *   height,
 *   tiles: [{ box, imageBox, ... }]
 * }
 */
@JsExport
class RawCollageLayout(
    val width: Double,
    val height: Double,
    val tiles: Array<RawCollageTile>,
)

/**
 * Internal tile result.
 *
 * The fields are intentionally flat. It is safer and simpler to cross the
 * Kotlin/JS boundary with primitive values, and then build a nicer object
 * in index.js.
 */
@JsExport
class RawCollageTile(
    val imageIndex: Int,
    val rowIndex: Int,
    val columnIndex: Int,

    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,

    val imageX: Double,
    val imageY: Double,
    val imageWidth: Double,
    val imageHeight: Double,

    val imageFit: String,
    val cropRatio: Double,
    val gapRatio: Double,
)

/**
 * Internal Kotlin/JS entrypoint.
 *
 * This function is called by npm/gorberry-collage/index.js.
 * It is not the public API of the npm package.
 */
@JsExport
@JsName("__calculateCollageLayoutRaw")
fun calculateCollageLayoutRaw(
    images: Array<RawCollageImage>,

    width: Double,
    minHeight: Double,
    maxHeight: Double,

    spacing: Double,
    minTileWidth: Double,
    minTileHeight: Double,
    maxTilesPerRow: Int,
    maxLandscapeTilesPerRow: Int,
    quality: String,
    allowHeightOverflow: Boolean,
    fitPolicy: String,
): RawCollageLayout {
    require(images.isNotEmpty()) {
        "images must not be empty"
    }

    val engine = CollageEngine(
        CollageConfiguration().apply {
            this.spacing = spacing.toNonNegativeFiniteFloat("spacing")
            this.minTileWidth = minTileWidth.toPositiveFiniteFloat("minTileWidth")
            this.minTileHeight = minTileHeight.toPositiveFiniteFloat("minTileHeight")
            this.maxTilesPerRow = maxTilesPerRow
            this.maxLandscapeTilesPerRow = maxLandscapeTilesPerRow
            this.searchQuality = quality.toSearchQuality()
            this.allowHeightOverflow = allowHeightOverflow
            this.tileFitPolicy = fitPolicy.toTileFitPolicy()
        }
    )

    val coreImages = images.mapIndexed { index, image ->
        require(image.imageIndex == index) {
            "images[$index].imageIndex must be $index"
        }

        CollageImage(
            imageId = index,
            width = image.width.toPositiveFiniteFloat("images[$index].width"),
            height = image.height.toPositiveFiniteFloat("images[$index].height"),
        )
    }

    val layout = engine.layout(
        images = coreImages,
        width = width.toPositiveFiniteFloat("width"),
        minHeight = minHeight.toNonNegativeFiniteFloat("minHeight"),
        maxHeight = maxHeight.toMaxHeightFloat(),
    )

    val rawTiles = layout.rows
        .flatMapIndexed { rowIndex, row ->
            row.tiles.mapIndexed { columnIndex, tile ->
                tile.toRawTile(
                    rowIndex = rowIndex,
                    columnIndex = columnIndex,
                )
            }
        }
        .toTypedArray()

    return RawCollageLayout(
        width = layout.width.toDouble(),
        height = layout.height.toDouble(),
        tiles = rawTiles,
    )
}

private fun CollageTile.toRawTile(
    rowIndex: Int,
    columnIndex: Int,
): RawCollageTile {
    val tileBox = box
    val imageBox = contentBox
    val rawImageFit = toRawImageFit()

    return RawCollageTile(
        imageIndex = imageId,
        rowIndex = rowIndex,
        columnIndex = columnIndex,

        x = tileBox.x.toDouble(),
        y = tileBox.y.toDouble(),
        width = tileBox.width.toDouble(),
        height = tileBox.height.toDouble(),
        imageX = contentOffsetX.toDouble(),
        imageY = contentOffsetY.toDouble(),
        imageWidth = imageBox.width.toDouble(),
        imageHeight = imageBox.height.toDouble(),

        imageFit = rawImageFit.toJsValue(),
        cropRatio = cropRatio.toDouble(),
        gapRatio = calculateGapRatio(rawImageFit),
    )
}

private enum class RawImageFit {
    Cover,
    Contain,
}

private fun CollageTile.toRawImageFit(): RawImageFit {
    return when (fit) {
        TileFit.COVER -> RawImageFit.Cover
        TileFit.CONTAIN -> RawImageFit.Contain
    }
}

private fun RawImageFit.toJsValue(): String {
    return when (this) {
        RawImageFit.Cover -> "cover"
        RawImageFit.Contain -> "contain"
    }
}

private fun CollageTile.calculateGapRatio(
    rawImageFit: RawImageFit,
): Double {
    if (rawImageFit != RawImageFit.Contain) {
        return 0.0
    }

    val tileArea = (box.width * box.height).coerceAtLeast(1f)
    val imageArea = (contentBox.width * contentBox.height).coerceAtLeast(0f)

    return (1f - imageArea / tileArea)
        .coerceIn(0f, 1f)
        .toDouble()
}

private fun String.toSearchQuality(): SearchQuality {
    return when (lowercase()) {
        "fast" -> SearchQuality.Fast
        "balanced" -> SearchQuality.Balanced
        "high" -> SearchQuality.High
        else -> error("Unknown quality='$this'. Expected: fast, balanced, high.")
    }
}

private fun String.toTileFitPolicy(): TileFitPolicy {
    return when (lowercase()) {
        "auto" -> TileFitPolicy.Auto
        "cover-only", "coveronly", "cover_only", "cover" -> TileFitPolicy.CoverOnly
        else -> error("Unknown fitPolicy='$this'. Expected: auto, cover-only.")
    }
}

private fun Double.toPositiveFiniteFloat(name: String): Float {
    require(isFinite() && this > 0.0) {
        "$name must be finite and > 0"
    }

    return toFloat()
}

private fun Double.toNonNegativeFiniteFloat(name: String): Float {
    require(isFinite() && this >= 0.0) {
        "$name must be finite and >= 0"
    }

    return toFloat()
}

private fun Double.toMaxHeightFloat(): Float {
    require((isFinite() && this > 0.0) || this == Double.POSITIVE_INFINITY) {
        "maxHeight must be finite and > 0 or Infinity"
    }

    return toFloat()
}
