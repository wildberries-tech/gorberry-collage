package ru.wildberries.collage.model

/**
 * Internal rectangle used by the layout algorithm.
 *
 * Public API exposes [CollageBox] instead.
 */
internal data class RectF(
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
)

/**
 * A rectangle in the collage coordinate system.
 *
 * All values use the same unit as the requested collage width.
 */
data class CollageBox(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
)

/**
 * A single image tile in the resulting collage.
 *
 * [box] is the visible tile viewport. UI code should clip image content to this box.
 *
 * [contentBox] is the scaled image rectangle in the same collage coordinate system.
 * In [TileFit.COVER] mode it can be larger than [box].
 * In [TileFit.CONTAIN] mode it fits inside [box] and may leave empty areas.
 */
data class CollageTile(
    val imageId: Int,
    val box: CollageBox,
    val contentBox: CollageBox,
    val scale: Float,
    val fit: TileFit,
    val cropRatio: Float,
) {
    /**
     * Image content X offset relative to [box].
     */
    val contentOffsetX: Float
        get() = contentBox.x - box.x

    /**
     * Image content Y offset relative to [box].
     */
    val contentOffsetY: Float
        get() = contentBox.y - box.y
}

data class CollageRow(
    val y: Float,
    val height: Float,
    val tiles: List<CollageTile>,
)

data class CollageLayout(
    val width: Float,
    val height: Float,
    val rows: List<CollageRow>,
)

enum class TileFit {
    COVER,
    CONTAIN,
}
