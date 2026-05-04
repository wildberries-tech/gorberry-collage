package ru.wildberries.collage.core

import ru.wildberries.collage.model.CollageImage
import ru.wildberries.collage.model.RectF
import kotlin.math.max
import kotlin.math.min

/**
 * Tile fit metrics used by the scoring model.
 *
 * coverCropRatio estimates how much image area is cropped in COVER mode.
 * containGapRatio estimates how much tile area stays empty in CONTAIN mode.
 *
 * Both metrics are normalized to [0, 1].
 */
internal object TileFitLossModel {

    fun coverCropRatio(collageImage: CollageImage, box: RectF): Float {
        val scale = max(box.w / max(collageImage.width, 1e-6f), box.h / max(collageImage.height, 1e-6f))

        val scaledW = scale * collageImage.width
        val scaledH = scale * collageImage.height

        val scaledArea = max(1f, scaledW * scaledH)
        val visibleArea = max(1f, box.w * box.h)

        return (1f - visibleArea / scaledArea).coerceIn(0f, 1f)
    }

    fun containGapRatio(collageImage: CollageImage, box: RectF): Float {
        val scale = min(box.w / max(collageImage.width, 1e-6f), box.h / max(collageImage.height, 1e-6f))

        val fittedW = scale * collageImage.width
        val fittedH = scale * collageImage.height

        val emptyArea = (box.w * box.h) - (fittedW * fittedH)

        val boxArea = max(1f, box.w * box.h)
        return (max(0f, emptyArea) / boxArea).coerceIn(0f, 1f)
    }
}
