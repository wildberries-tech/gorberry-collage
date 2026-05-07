package ru.wildberries.collage.strategy

import ru.wildberries.collage.core.TileGeometryMapper
import ru.wildberries.collage.core.TileLossDecision
import ru.wildberries.collage.model.CollageBox
import ru.wildberries.collage.model.CollageImage
import ru.wildberries.collage.model.CollageTile
import ru.wildberries.collage.model.RectF
import ru.wildberries.collage.model.TileFit
import kotlin.math.max
import kotlin.math.min

/**
 * Converts a tile box and fit decision into final image content geometry.
 *
 * COVER fills the whole tile box and may crop the image.
 * CONTAIN keeps the whole image visible and may leave empty areas inside the box.
 */
internal class DefaultTileGeometryMapper : TileGeometryMapper {

    override fun materialize(
        collageImage: CollageImage,
        box: RectF,
        decision: TileLossDecision,
    ): CollageTile {
        val useCover = decision.useCover

        val safeImageWidth = collageImage.width.coerceAtLeast(1f)
        val safeImageHeight = collageImage.height.coerceAtLeast(1f)

        val scale = if (useCover) {
            max(box.w / safeImageWidth, box.h / safeImageHeight)
        } else {
            min(box.w / safeImageWidth, box.h / safeImageHeight)
        }

        val contentWidth = scale * safeImageWidth
        val contentHeight = scale * safeImageHeight
        val contentX = box.x + (box.w - contentWidth) / 2f
        val contentY = box.y + (box.h - contentHeight) / 2f

        return CollageTile(
            imageId = collageImage.imageId,
            box = CollageBox(
                x = box.x,
                y = box.y,
                width = box.w,
                height = box.h,
            ),
            contentBox = CollageBox(
                x = contentX,
                y = contentY,
                width = contentWidth,
                height = contentHeight,
            ),
            scale = scale,
            fit = if (useCover) TileFit.COVER else TileFit.CONTAIN,
            cropRatio = if (useCover) decision.crop else 0f,
        )
    }
}
