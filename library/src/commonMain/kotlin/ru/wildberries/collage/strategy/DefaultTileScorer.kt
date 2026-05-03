package ru.wildberries.collage.strategy

import ru.wildberries.collage.core.CollageTuning
import ru.wildberries.collage.core.LossDecision
import ru.wildberries.collage.core.LossModel
import ru.wildberries.collage.core.MathUtil
import ru.wildberries.collage.core.MathUtil.PowerLookupTable
import ru.wildberries.collage.core.TileScorer
import ru.wildberries.collage.model.Photo
import ru.wildberries.collage.model.RectF
import kotlin.math.max

/**
 * Score (COVER vs CONTAIN) for a tile. Uses shared LUT by default
 */
internal class DefaultTileScorer(
    private val weights: FitWeights = FitWeights(),
    private val lut: PowerLookupTable = CollageTuning.default.resources.powerLookupTable,
) : TileScorer {

    private fun aspectSafe(width: Float, height: Float): Float = MathUtil.aspect(width, height)

    private fun planningAspect(imageAspectRatio: Float): Float =
        MathUtil.clampAspectForPlanning(
            aspect = imageAspectRatio,
            maxAspect = weights.planningAspectLimit,
        )

    override fun decide(photo: Photo, box: RectF): LossDecision {
        val rawCropRatio = LossModel.coverCropRatio(photo, box).coerceIn(0f, 1f)
        val gapRatio = LossModel.containGapRatio(photo, box).coerceIn(0f, 1f)
        val boxArea = (box.w * box.h).coerceAtLeast(1f)

        val imageAspectRatio = aspectSafe(photo.width, photo.height)
        val boxAspectRatio = aspectSafe(box.w, box.h)
        val planningAspectRatio = planningAspect(imageAspectRatio)

        val mismatch = max(
            planningAspectRatio / boxAspectRatio,
            boxAspectRatio / planningAspectRatio,
        ) - 1f

        val cropRatio = MathUtil.normalizeCropRatioAfterFreeCropAllowance(
            rawCropRatio = rawCropRatio,
            imageAspectRatio = imageAspectRatio,
            boxAspectRatio = boxAspectRatio,
            freeCropAspectLimit = weights.freeCropAspectLimit,
        )

        val effectiveCropPow = (
            weights.cropPow +
                weights.mismatchAmplify * mismatch
            ).coerceIn(1.0f, 3.5f)

        val coverBase = weights.lambdaCrop *
            boxArea *
            lut.power(
                valueInput = cropRatio,
                exponentInput = effectiveCropPow,
            )

        val containBase = weights.lambdaGap *
            boxArea *
            lut.power(
                valueInput = gapRatio,
                exponentInput = weights.gapPow,
            )

        var coverTotal = coverBase

        if (cropRatio > weights.extremeCropHard) {
            val over = cropRatio - weights.extremeCropHard
            coverTotal += weights.lambdaCrop *
                boxArea *
                weights.extremeCropAlpha *
                over *
                over *
                (1f + mismatch)
        }

        val preferContainGuard = 0.001f
        var useCover = coverTotal < containBase * (1f - preferContainGuard)

        if (cropRatio > weights.cropCutoff && coverTotal >= containBase) {
            useCover = false
        }

        if (gapRatio > weights.gapCutoff && containBase >= coverTotal) {
            useCover = true
        }

        return LossDecision(
            cover = coverTotal,
            contain = containBase,
            crop = cropRatio,
            useCover = useCover,
        )
    }
}
