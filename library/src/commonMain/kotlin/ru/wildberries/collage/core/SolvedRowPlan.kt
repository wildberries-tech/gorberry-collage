package ru.wildberries.collage.core

import ru.wildberries.collage.model.RectF

internal data class SolvedRowPlan(
    val height: Float,
    val boxes: List<RectF>,
    val loss: Double,
)
