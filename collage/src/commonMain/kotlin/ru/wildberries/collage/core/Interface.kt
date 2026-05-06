package ru.wildberries.collage.core

import ru.wildberries.collage.model.CollageImage
import ru.wildberries.collage.model.RectF
import ru.wildberries.collage.model.CollageTile

internal data class TileLossDecision(val cover: Float, val contain: Float, val crop: Float, val useCover: Boolean)

internal interface TileFitScorer {

    fun decide(collageImage: CollageImage, box: RectF): TileLossDecision
}

internal interface TileGeometryMapper {

    fun materialize(collageImage: CollageImage, box: RectF, decision: TileLossDecision): CollageTile
}
