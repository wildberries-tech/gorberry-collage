package ru.wildberries.collage.strategy

import ru.wildberries.collage.core.TileLossDecision
import ru.wildberries.collage.core.TileGeometryMapper
import ru.wildberries.collage.model.CollageImage
import ru.wildberries.collage.model.RectF
import ru.wildberries.collage.model.CollageTile
import kotlin.math.max
import kotlin.math.min

/**
 * Converts a tile frame and fit decision into final image content geometry
 *
 * COVER fills the whole frame and may crop the image
 * CONTAIN keeps the whole image visible and may leave empty space inside the frame
 */
internal class DefaultTileGeometryMapper : TileGeometryMapper {

    override fun materialize(collageImage: CollageImage, box: RectF, decision: TileLossDecision): CollageTile {
        val useCover = decision.useCover

        val safePhotoWidth = collageImage.width.coerceAtLeast(1f)
        val safePhotoHeight = collageImage.height.coerceAtLeast(1f)

        val scale = if (useCover) {
            max(box.w / safePhotoWidth, box.h / safePhotoHeight)
        } else {
            min(box.w / safePhotoWidth, box.h / safePhotoHeight)
        }

        val contentW = scale * safePhotoWidth
        val contentH = scale * safePhotoHeight
        val contentX = box.x + (box.w - contentW) / 2f
        val contentY = box.y + (box.h - contentH) / 2f

        return CollageTile(
            imageId = collageImage.imageId,
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
