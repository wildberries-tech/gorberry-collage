package ru.wildberries.collage.core

import ru.wildberries.collage.model.CollageImage
import ru.wildberries.collage.model.RectF
import ru.wildberries.collage.model.RowPlan
import kotlin.math.max

/**
 * Context for a single row planning
 */
internal data class RowLayoutContext(
    val collageImages: List<CollageImage>,
    val startIndexInclusive: Int,
    val endIndexInclusive: Int,
    val collageWidth: Float,
    val horizontalGap: Float,
    val verticalGap: Float,
    val minItemWidth: Float,
    val minItemHeight: Float,
    val rowHeightHint: Float? = null,
    val tileFitScorer: TileFitScorer,
) {

    val length: Int get() = endIndexInclusive - startIndexInclusive + 1
    fun photoAt(offset: Int): CollageImage = collageImages[startIndexInclusive + offset]
}

/**
 * Builds candidate geometry for a single row and rejects visually invalid layouts
 */
internal class RowPlanner(
    private val tuning: CollageTuning.Snapshot = CollageTuning.default,
) {
    private val tauH = tuning.dynamicProgrammingConfig.tauHorizontal
    private val planningAspectLimit = tuning.heuristics.planningAspectLimit
    private val freeCropAspectLimit = tuning.heuristics.freeCropAspectLimit

    private fun computePhotoAspectRatios(ctx: RowLayoutContext): FloatArray =
        FloatArray(ctx.length) { i ->
            MathUtil.clampAspectForPlanning(
                MathUtil.aspect(ctx.photoAt(i).width, ctx.photoAt(i).height),
                planningAspectLimit,
            )
        }

    private fun computeTargetWidthWithoutGaps(ctx: RowLayoutContext): Float =
        (ctx.collageWidth - ctx.horizontalGap * max(0, ctx.length - 1)).coerceAtLeast(0f)

    private data class FinalizedRowDraft(
        val rowHeight: Float,
        val boxes: List<RectF>,
    )

    private fun finalizeRowPlanWithGuardsAndSnapping(
        context: RowLayoutContext,
        initialRowHeight: Float,
        boxesBeforeSnap: List<RectF>,
        rowHeightCap: Float,
    ): Pair<Float, List<RectF>>? {
        val originalAspectRatios = RowLayoutGuards.computeOriginalPhotoAspectRatios(context)
        val planningAspectRatios = computePhotoAspectRatios(context)

        val snappedBoxes = snapToTargetWidth(
            boxes = boxesBeforeSnap,
            width = context.collageWidth,
            horizontalGap = context.horizontalGap,
            minWidth = context.minItemWidth,
            photoAspects = planningAspectRatios,
        )

        if (RowLayoutGuards.hasSoftWidthFloorViolation(
                context = context,
                boxes = snappedBoxes,
                planningAspectRatios = planningAspectRatios,
                tuning = tuning,
            )
        ) {
            return null
        }

        if (RowLayoutGuards.hasUltraNarrowTile(
                context = context,
                boxes = snappedBoxes,
                planningAspectRatios = planningAspectRatios,
                tuning = tuning,
            )
        ) {
            return null
        }

        if (RowLayoutGuards.hasUltraTallStickTile(
                context = context,
                boxes = snappedBoxes,
                originalAspectRatios = originalAspectRatios,
                tuning = tuning,
            )
        ) {
            return null
        }

        val guardedDraft = applyExtremeCropGuard(
            context = context,
            boxesIn = snappedBoxes,
            usedHIn = initialRowHeight,
            hMaxRaw = rowHeightCap,
            planningAspectRatios = planningAspectRatios,
            extremeCropThreshold = 0.34f,
        ).let { (rowHeight, boxes) ->
            FinalizedRowDraft(
                rowHeight = rowHeight,
                boxes = boxes,
            )
        }

        if (RowLayoutGuards.hasInvalidSelectedTileContent(context, guardedDraft.boxes, tuning)) {
            return null
        }

        if (RowLayoutGuards.hasMatchstickTile(guardedDraft.boxes, tuning)) {
            return null
        }

        return guardedDraft.rowHeight to guardedDraft.boxes
    }

    /** Returns the row height search bounds used by the planner */
    fun rowHeightBounds(ctx: RowLayoutContext): Pair<Float, Float> {
        val hMin = max(ctx.minItemHeight, 1f)
        val softMax = ctx.minItemHeight * 16f
        return hMin to max(hMin + 1e-3f, softMax)
    }

    /** Build boxes with a fixed height, null if min width can't be achieved */
    fun boxes(ctx: RowLayoutContext, rowHeight: Float): List<RectF>? {
        val len = ctx.length
        if (len <= 0 || rowHeight < ctx.minItemHeight) return null

        val totalGaps = ctx.horizontalGap * max(0, len - 1)
        val availableWidth = ctx.collageWidth - totalGaps
        if (availableWidth <= 0f) return null
        if (availableWidth + 1e-3f < len * ctx.minItemWidth) return null

        val aspects = computePhotoAspectRatios(ctx)

        val ideal = FloatArray(len) { i -> (rowHeight * aspects[i]).coerceAtLeast(ctx.minItemWidth) }
        val floor = FloatArray(len) { ctx.minItemWidth }
        val cap = FloatArray(len) { availableWidth }
        val desire = FloatArray(len) { i -> max(0f, ideal[i] - floor[i]) }

        val widths = WidthDistributor.distributeWithFloorsAndCaps(
            desire = desire,
            floor = floor,
            cap = cap,
            target = availableWidth,
        )

        val out = ArrayList<RectF>(len)
        var x = 0f
        var i = 0
        while (i < len) {
            out += RectF(
                x = x,
                y = 0f,
                w = widths[i],
                h = rowHeight
            )
            x += widths[i]
            if (i < len - 1) x += ctx.horizontalGap
            i++
        }
        return out
    }

    private fun normalizedCoverCropForGuard(
        collageImage: CollageImage,
        box: RectF,
    ): Float {
        val rawCropRatio = TileFitLossModel.coverCropRatio(collageImage, box)
        val imageAspectRatio = MathUtil.aspect(collageImage.width, collageImage.height)
        val boxAspectRatio = MathUtil.aspect(box.w, box.h)

        return MathUtil.normalizeCropRatioAfterFreeCropAllowance(
            rawCropRatio = rawCropRatio,
            imageAspectRatio = imageAspectRatio,
            boxAspectRatio = boxAspectRatio,
            freeCropAspectLimit = freeCropAspectLimit,
        )
    }

    /**
     * Try to plan a row, uses hint if provided, otherwise searches best height
     * Returns null if constraints/guards cannot be satisfied
     */
    fun tryPlan(layoutContext: RowLayoutContext): RowPlan? {
        val (hMin, hMaxRaw) = rowHeightBounds(layoutContext)
        val hHint = layoutContext.rowHeightHint

        if (hHint != null && hHint.isFinite()) {
            val used0 = hHint.coerceIn(hMin, hMaxRaw)
            val boxes0 = boxes(layoutContext, used0) ?: return null
            val result = finalizeRowPlanWithGuardsAndSnapping(
                context = layoutContext,
                initialRowHeight = used0,
                boxesBeforeSnap = boxes0,
                rowHeightCap = hMaxRaw,
            ) ?: return null
            val (usedH, finalBoxes) = result
            val loss = rowLoss(layoutContext, finalBoxes)
            return RowPlan(
                startIndexInclusive = layoutContext.startIndexInclusive,
                endIndexInclusive = layoutContext.endIndexInclusive,
                rowHeight = usedH,
                loss = loss,
                boxes = finalBoxes,
            )
        }

        // Search for the best row height when no explicit hint is provided
        val (bestH, bestLossF) = Search.minimizeOnInterval(hMin, hMaxRaw) { h ->
            val b = boxes(layoutContext, h) ?: return@minimizeOnInterval Float.POSITIVE_INFINITY
            rowLossF(layoutContext, b)
        }
        val used0 = if (bestLossF.isFinite()) bestH else hMin
        val b0 = boxes(layoutContext, used0) ?: return null
        val result = finalizeRowPlanWithGuardsAndSnapping(
            context = layoutContext,
            initialRowHeight = used0,
            boxesBeforeSnap = b0,
            rowHeightCap = hMaxRaw,
        ) ?: return null
        val (usedH, finalBoxes) = result
        val loss = rowLoss(layoutContext, finalBoxes)
        return RowPlan(
            startIndexInclusive = layoutContext.startIndexInclusive,
            endIndexInclusive = layoutContext.endIndexInclusive,
            rowHeight = usedH,
            loss = loss,
            boxes = finalBoxes,
        )
    }

    private fun rowLoss(ctx: RowLayoutContext, boxes: List<RectF>): Double =
        rowLossF(ctx, boxes).toDouble()

    /** Computes row loss using the same narrow container guard as final materialization */
    private fun rowLossF(
        context: RowLayoutContext,
        boxes: List<RectF>,
    ): Float {
        var accumulatedLoss = 0f
        var tileIndex = 0

        while (tileIndex < boxes.size) {
            accumulatedLoss += tileLossForRowScoring(
                context = context,
                boxes = boxes,
                tileIndex = tileIndex,
            )
            tileIndex++
        }

        return if (accumulatedLoss.isFinite()) accumulatedLoss else 1e12f
    }

    private fun tileLossForRowScoring(
        context: RowLayoutContext,
        boxes: List<RectF>,
        tileIndex: Int,
    ): Float {
        val photo = context.photoAt(tileIndex)
        val box = boxes[tileIndex]
        val decision = context.tileFitScorer.decide(photo, box)

        val shouldForceContain = tuning.heuristics
            .shouldForceContainInNarrowContainerForRowScoring(
                layoutWidthPx = context.collageWidth,
                imageAspect = MathUtil.aspect(photo.width, photo.height),
                cropRatio = decision.crop,
                isSmallTile = isNotLargerThanAverageTile(
                    context = context,
                    box = box,
                ),
            )

        val useCover = decision.useCover && !shouldForceContain
        return if (useCover) decision.cover else decision.contain
    }

    private fun isNotLargerThanAverageTile(
        context: RowLayoutContext,
        box: RectF,
    ): Boolean {
        val availableWidth = computeTargetWidthWithoutGaps(context)
        val averageTileWidth = availableWidth / context.length.coerceAtLeast(1)
        return box.w <= averageTileWidth + 1e-3f
    }

    private fun buildFloorsAndCapsForSnap(
        n: Int,
        target: Float,
        minW: Float,
        photoAspects: FloatArray,
        beta: Float,
        absFloor: Float,
        absCap: Float,
    ): Pair<FloatArray, FloatArray> {
        val plan = tuning.planner
        val cell = target / n

        val floor = FloatArray(n) { i ->
            val a = photoAspects[i]
            val perAspect = plan.perAspectFloor(a, tauH) * cell
            max(minW, max(absFloor, max(beta * cell, perAspect)))
        }
        val cap = FloatArray(n) { absCap }
        return floor to cap
    }

    private fun enforceHardMinFracGuard(
        out: FloatArray,
        target: Float,
        minFrac: Float,
    ) {
        if (minFrac <= 0f) return

        val n = out.size
        val minAbs = minFrac * target

        var deficit = 0f
        var i = 0
        while (i < n) {
            if (out[i] < minAbs) deficit += (minAbs - out[i])
            i++
        }
        if (deficit <= 1e-3f) return

        var pool = 0f
        i = 0
        while (i < n) {
            pool += max(0f, out[i] - minAbs)
            i++
        }

        if (pool < 1e-6f) {
            val eq = target / n
            i = 0
            while (i < n) {
                out[i] = eq
                i++
            }
            return
        }

        i = 0
        while (i < n) {
            if (out[i] < minAbs) {
                out[i] = minAbs
            } else {
                val share = (out[i] - minAbs) / pool
                out[i] -= share * deficit
            }
            i++
        }
    }

    private fun rebuildBoxesWithWidths(
        boxes: List<RectF>,
        widths: FloatArray,
        horizontalGap: Float,
    ): List<RectF> {
        val n = boxes.size
        val result = ArrayList<RectF>(n)

        var x = 0f
        var i = 0
        while (i < n) {
            val b = boxes[i]
            result += RectF(
                x = x,
                y = 0f,
                w = widths[i],
                h = b.h
            )
            x += widths[i] + if (i < n - 1) horizontalGap else 0f
            i++
        }
        return result
    }

    /**
     * Adjusts tile widths to exactly fill the row while preserving visual width floors
     */
    fun snapToTargetWidth(
        boxes: List<RectF>,
        width: Float,
        horizontalGap: Float,
        minWidth: Float,
        photoAspects: FloatArray,
    ): List<RectF> {
        if (boxes.isEmpty()) return boxes

        val n = boxes.size
        val gaps = horizontalGap * (n - 1)
        val target = (width - gaps).coerceAtLeast(0f)
        if (target <= 0f) return boxes

        val heuristics = tuning.heuristics
        val beta = heuristics.snapBeta(n)
        val absFloor = heuristics.snapMinAbsFrac(n) * target
        val absCap = heuristics.snapMaxAbsFrac(n) * target

        val (floor, cap) = buildFloorsAndCapsForSnap(
            n = n,
            target = target,
            minW = minWidth,
            photoAspects = photoAspects,
            beta = beta,
            absFloor = absFloor,
            absCap = absCap
        )

        val base = FloatArray(n) { i -> max(boxes[i].w, floor[i]) }
        val desire = FloatArray(n) { i -> max(0f, base[i] - floor[i]) }
        val out = WidthDistributor.distributeWithFloorsAndCaps(
            desire = desire,
            floor = floor,
            cap = cap,
            target = target,
        )

        enforceHardMinFracGuard(out, target, heuristics.hardAbsMinFrac(n))

        return rebuildBoxesWithWidths(boxes, out, horizontalGap)
    }

    /** Increases row height when the current boxes would cause excessive cover crop */
    fun applyExtremeCropGuard(
        context: RowLayoutContext,
        boxesIn: List<RectF>,
        usedHIn: Float,
        hMaxRaw: Float,
        planningAspectRatios: FloatArray,
        extremeCropThreshold: Float = 0.34f,
    ): Pair<Float, List<RectF>> {
        var usedRowHeight = usedHIn
        var boxes = boxesIn

        if (worstCoverCrop(context, boxes) > extremeCropThreshold && usedRowHeight < hMaxRaw) {
            var guardedRowHeight = usedRowHeight

            var tileIndex = 0
            while (tileIndex < boxes.size) {
                val effectiveImageAspectRatio = planningAspectRatios[tileIndex].toDouble()
                val boxAspectRatio = (boxes[tileIndex].w / boxes[tileIndex].h)
                    .toDouble()
                    .coerceAtLeast(1e-6)

                if (boxAspectRatio > effectiveImageAspectRatio) {
                    val maximumBoxAspectRatio =
                        effectiveImageAspectRatio / (1.0 - extremeCropThreshold)

                    val requiredRowHeight = (boxes[tileIndex].w / maximumBoxAspectRatio).toFloat()
                    if (requiredRowHeight > guardedRowHeight) {
                        guardedRowHeight = requiredRowHeight
                    }
                }

                tileIndex++
            }

            guardedRowHeight = guardedRowHeight.coerceAtMost(hMaxRaw)

            if (guardedRowHeight > usedRowHeight + 0.5f) {
                usedRowHeight = guardedRowHeight
                boxes = boxes(context, usedRowHeight) ?: boxes
                boxes = snapToTargetWidth(
                    boxes = boxes,
                    width = context.collageWidth,
                    horizontalGap = context.horizontalGap,
                    minWidth = context.minItemWidth,
                    photoAspects = planningAspectRatios,
                )
            }
        }

        return usedRowHeight to boxes
    }

    private fun worstCoverCrop(ctx: RowLayoutContext, boxes: List<RectF>): Float {
        var worst = 0f
        var i = 0
        while (i < boxes.size) {
            val crop = normalizedCoverCropForGuard(ctx.photoAt(i), boxes[i])
            if (crop > worst) worst = crop
            i++
        }
        return worst
    }
}
