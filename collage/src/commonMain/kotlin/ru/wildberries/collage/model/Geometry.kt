package ru.wildberries.collage.model

import ru.wildberries.collage.strategy.TileFit

data class RectF(val x: Float, val y: Float, val w: Float, val h: Float)

data class CollageTile(
    val imageId: Int,

    /**
     * Visible tile bounds in the collage coordinate system.
     *
     * This rectangle is the clipping viewport for the image content.
     */
    val boxX: Float,
    val boxY: Float,
    val boxW: Float,
    val boxH: Float,

    /**
     * Scaled image bounds in the collage coordinate system.
     *
     * In COVER mode, this rectangle can be larger than the tile bounds and should
     * be clipped by the tile viewport.
     *
     * In CONTAIN mode, this rectangle is inside the tile bounds and empty areas
     * can remain around it.
     */
    val contentX: Float,
    val contentY: Float,
    val contentW: Float,
    val contentH: Float,
    val scale: Float,
    val fit: TileFit,
    val cropRatio: Float,
)

data class CollageRow(val y: Float, val height: Float, val tiles: List<CollageTile>)
data class CollageLayout(val width: Float, val height: Float, val rows: List<CollageRow>)
