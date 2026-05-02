package ru.wildberries.collage.core

import ru.wildberries.collage.model.Photo
import ru.wildberries.collage.model.RectF
import kotlin.math.max
import kotlin.math.min

/**
 * Metrics for tile:
 *  - coverCropRatio — COVER means how muc crop
 *  - containGapRatio — CONTAIN means how much empty spaces
 *
 * metrics normalize [0..1]
 * Only for scoring
 */
internal object LossModel {

    fun coverCropRatio(photo: Photo, box: RectF): Float {
        val scale = max(box.w / max(photo.width, 1e-6f), box.h / max(photo.height, 1e-6f))

        val scaledW = scale * photo.width
        val scaledH = scale * photo.height

        val scaledArea = max(1f, scaledW * scaledH)
        val visibleArea = max(1f, box.w * box.h)

        return (1f - visibleArea / scaledArea).coerceIn(0f, 1f)
    }

    fun containGapRatio(photo: Photo, box: RectF): Float {
        val scale = min(box.w / max(photo.width, 1e-6f), box.h / max(photo.height, 1e-6f))

        val fittedW = scale * photo.width
        val fittedH = scale * photo.height

        val emptyArea = (box.w * box.h) - (fittedW * fittedH)

        val boxArea = max(1f, box.w * box.h)
        return (max(0f, emptyArea) / boxArea).coerceIn(0f, 1f)
    }
}
