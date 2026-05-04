package ru.wildberries.collage.model

data class CollageImage(
    val imageId: Int,
    val width: Float,
    val height: Float,
)

data class SizeAttrs(
    val width: Float,
    val height: Float,
)

internal data class RowPlan(
    val startIndexInclusive: Int,
    val endIndexInclusive: Int,
    val rowHeight: Float,
    val loss: Double,
    val boxes: List<RectF> = emptyList(),
)
