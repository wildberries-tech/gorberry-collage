package ru.wildberries.collage.core

import ru.wildberries.collage.model.Photo
import ru.wildberries.collage.model.RectF
import ru.wildberries.collage.model.TileGeometry

data class LossDecision(val cover: Float, val contain: Float, val crop: Float, val useCover: Boolean)

interface TileScorer {

    fun decide(photo: Photo, box: RectF): LossDecision
}

interface TileRenderer {

    fun materialize(photo: Photo, box: RectF, decision: LossDecision): TileGeometry
}
