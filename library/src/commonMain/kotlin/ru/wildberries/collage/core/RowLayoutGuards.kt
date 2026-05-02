package ru.wildberries.collage.core

import ru.wildberries.collage.model.Photo
import ru.wildberries.collage.model.RectF
import kotlin.math.max
import kotlin.math.min

internal object RowLayoutGuards {

    private const val WIDTH_GUARD_ASPECT_LIMIT = 0.90f
    private const val ULTRA_TALL_STICK_BOX_ASPECT_LIMIT = 0.42f
    private const val EPSILON = 1e-3f

    fun computeOriginalPhotoAspectRatios(
        context: RowLayoutContext,
    ): FloatArray = FloatArray(context.length) { tileIndex ->
        MathUtil.aspect(
            width = context.photoAt(tileIndex).width,
            height = context.photoAt(tileIndex).height,
        )
    }

    fun hasSoftWidthFloorViolation(
        context: RowLayoutContext,
        boxes: List<RectF>,
        planningAspectRatios: FloatArray,
        tuning: CollageTuning.Snapshot,
    ): Boolean {
        val softWidthFraction = tuning.heuristics.softRelMinFrac(context.length)
        if (softWidthFraction <= 0f) return false

        val cellWidth = computeCellWidth(context)
        val minimumWidth = max(context.minItemWidth, softWidthFraction * cellWidth)

        var tileIndex = 0
        while (tileIndex < boxes.size) {
            val aspectRatio = planningAspectRatios[tileIndex]
            if (
                aspectRatio >= WIDTH_GUARD_ASPECT_LIMIT &&
                boxes[tileIndex].w <= minimumWidth + EPSILON
            ) {
                return true
            }
            tileIndex++
        }

        return false
    }

    fun hasUltraNarrowTile(
        context: RowLayoutContext,
        boxes: List<RectF>,
        planningAspectRatios: FloatArray,
        tuning: CollageTuning.Snapshot,
    ): Boolean {
        val ultraNarrowFraction = tuning.heuristics.ultraNarrowFrac(context.length)
        if (ultraNarrowFraction <= 0f) return false

        val cellWidth = computeCellWidth(context)
        val minimumWidth = max(context.minItemWidth, ultraNarrowFraction * cellWidth)

        var tileIndex = 0
        while (tileIndex < boxes.size) {
            val aspectRatio = planningAspectRatios[tileIndex]
            if (
                aspectRatio >= WIDTH_GUARD_ASPECT_LIMIT &&
                boxes[tileIndex].w <= minimumWidth + EPSILON
            ) {
                return true
            }
            tileIndex++
        }

        return false
    }

    fun hasUltraTallStickTile(
        context: RowLayoutContext,
        boxes: List<RectF>,
        originalAspectRatios: FloatArray,
        tuning: CollageTuning.Snapshot,
    ): Boolean {
        if (context.length < 3) return false

        val ultraTallAspectLimit = 1f / tuning.heuristics.ultraWideSoloAspect

        var tileIndex = 0
        while (tileIndex < boxes.size) {
            val imageAspectRatio = originalAspectRatios[tileIndex]
            val boxAspectRatio = MathUtil.aspect(
                width = boxes[tileIndex].w,
                height = boxes[tileIndex].h,
            )

            if (
                imageAspectRatio <= ultraTallAspectLimit &&
                boxAspectRatio <= ULTRA_TALL_STICK_BOX_ASPECT_LIMIT
            ) {
                return true
            }

            tileIndex++
        }

        return false
    }

    fun hasMatchstickTile(
        boxes: List<RectF>,
        tuning: CollageTuning.Snapshot,
    ): Boolean {
        val tileCount = boxes.size
        if (tileCount < 3) return false

        var widthSum = 0f
        var minimumWidth = Float.POSITIVE_INFINITY

        for (box in boxes) {
            widthSum += box.w
            minimumWidth = min(minimumWidth, box.w)
        }

        val averageWidth = (widthSum / tileCount).coerceAtLeast(1e-3f)
        val widthRatio = minimumWidth / averageWidth
        val minimumAllowedRatio = tuning.planner.matchstickGamma(tileCount)

        return widthRatio < minimumAllowedRatio - 1e-4f
    }

    fun hasInvalidSelectedTileContent(
        context: RowLayoutContext,
        boxes: List<RectF>,
        tuning: CollageTuning.Snapshot,
    ): Boolean {
        val tileCount = boxes.size
        if (tileCount <= 1) return false

        var tileIndex = 0
        while (tileIndex < boxes.size) {
            val photo = context.photoAt(tileIndex)
            val box = boxes[tileIndex]

            val rawDecision = context.tileScorer.decide(photo, box)
            val useCover = resolveUseCoverForFinalPreview(
                context = context,
                photo = photo,
                decision = rawDecision,
                tuning = tuning,
            )

            if (useCover) {
                val rawCropRatio = LossModel.coverCropRatio(photo, box)
                val normalizedCropRatio = MathUtil.normalizeCropRatioAfterFreeCropAllowance(
                    rawCropRatio = rawCropRatio,
                    imageAspectRatio = MathUtil.aspect(photo.width, photo.height),
                    boxAspectRatio = MathUtil.aspect(box.w, box.h),
                    freeCropAspectLimit = tuning.heuristics.freeCropAspectLimit,
                )

                if (normalizedCropRatio > maximumAllowedNormalizedCrop(tileCount) + EPSILON) {
                    return true
                }
            } else {
                val contentSize = computeContainContentSize(
                    photo = photo,
                    box = box,
                )

                if (contentSize.width < minimumAllowedVisibleContentWidth(context, tileCount) - EPSILON) {
                    return true
                }

                if (contentSize.height < minimumAllowedVisibleContentHeight(context, tileCount) - EPSILON) {
                    return true
                }
            }

            tileIndex++
        }

        return false
    }

    private data class ContentSize(
        val width: Float,
        val height: Float,
    )

    private fun computeContainContentSize(
        photo: Photo,
        box: RectF,
    ): ContentSize {
        val safePhotoWidth = max(photo.width, 1e-6f)
        val safePhotoHeight = max(photo.height, 1e-6f)

        val scale = min(box.w / safePhotoWidth, box.h / safePhotoHeight)

        return ContentSize(
            width = scale * safePhotoWidth,
            height = scale * safePhotoHeight,
        )
    }

    private fun minimumAllowedVisibleContentWidth(
        context: RowLayoutContext,
        tileCount: Int,
    ): Float {
        val factor = when {
            tileCount == 2 -> 0.85f
            tileCount == 3 -> 0.92f
            else -> 1.00f
        }

        return context.minItemWidth * factor
    }

    private fun minimumAllowedVisibleContentHeight(
        context: RowLayoutContext,
        tileCount: Int,
    ): Float {
        val factor = when {
            tileCount == 2 -> 0.85f
            tileCount == 3 -> 0.92f
            else -> 1.00f
        }

        return context.minItemHeight * factor
    }

    private fun maximumAllowedNormalizedCrop(
        tileCount: Int,
    ): Float = when {
        tileCount == 2 -> 0.34f
        tileCount == 3 -> 0.30f
        else -> 0.28f
    }

    private fun resolveUseCoverForFinalPreview(
        context: RowLayoutContext,
        photo: Photo,
        decision: LossDecision,
        tuning: CollageTuning.Snapshot,
    ): Boolean {
        val shouldForceContain = tuning.heuristics
            .shouldForceContainInNarrowContainerForMaterialization(
                layoutWidthPx = context.collageWidth,
                imageAspect = MathUtil.aspect(photo.width, photo.height),
                cropRatio = decision.crop,
            )

        return decision.useCover && !shouldForceContain
    }

    private fun computeCellWidth(
        context: RowLayoutContext,
    ): Float {
        val targetWidth = (context.collageWidth - context.horizontalGap * max(0, context.length - 1))
            .coerceAtLeast(0f)

        return if (context.length > 0) {
            targetWidth / context.length
        } else {
            1f
        }
    }
}
