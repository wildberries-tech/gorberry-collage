package ru.wildberries.collage.model

import ru.wildberries.collage.strategy.TileFit

data class RectF(val x: Float, val y: Float, val w: Float, val h: Float)

data class TileGeometry(
    val imageId: Int,
    val boxX: Float,
    val boxY: Float,
    val boxW: Float,
    val boxH: Float,
    val contentX: Float,
    val contentY: Float,
    val contentW: Float,
    val contentH: Float,
    val scale: Float,
    val fit: TileFit,
    val cropRatio: Float,
)

data class RowGeometry(val y: Float, val height: Float, val tiles: List<TileGeometry>)
data class CollageLayout(val width: Float, val height: Float, val rows: List<RowGeometry>)
