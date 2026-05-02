package ru.wildberries.collage.strategy

import ru.wildberries.collage.core.LossDecision
import ru.wildberries.collage.core.TileRenderer
import ru.wildberries.collage.model.Photo
import ru.wildberries.collage.model.RectF
import ru.wildberries.collage.model.TileGeometry
import kotlin.math.max
import kotlin.math.min

/**
 * Materialize tile geometry by scorer decision
 * If useCover==true then scale for big side (COVER), another time for small side (CONTAIN)
 */
internal class DefaultTileRenderer : TileRenderer {

    override fun materialize(photo: Photo, box: RectF, decision: LossDecision): TileGeometry {
        val useCover = decision.useCover

        val safePhotoWidth = photo.width.coerceAtLeast(1f)
        val safePhotoHeight = photo.height.coerceAtLeast(1f)

        val scale = if (useCover) {
            max(box.w / safePhotoWidth, box.h / safePhotoHeight)
        } else {
            min(box.w / safePhotoWidth, box.h / safePhotoHeight)
        }

        val contentW = scale * safePhotoWidth
        val contentH = scale * safePhotoHeight
        val contentX = box.x + (box.w - contentW) / 2f
        val contentY = box.y + (box.h - contentH) / 2f

        return TileGeometry(
            imageId = photo.imageId,
            boxX = box.x,
            boxY = box.y,
            boxW = box.w,
            boxH = box.h,
            contentX = contentX,
            contentY = contentY,
            contentW = contentW,
            contentH = contentH,
            scale = scale,
            fit = if (useCover) TileFit.COVER else TileFit.CONTAIN,
            cropRatio = if (useCover) decision.crop else 0f
        )
    }
}

enum class TileFit {
    COVER,
    CONTAIN,
}
