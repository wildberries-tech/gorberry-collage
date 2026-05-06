package ru.wildberries.collage.core

import ru.wildberries.collage.cache.TileLossCache
import ru.wildberries.collage.cache.RowPlanCache
import ru.wildberries.collage.model.CollageLayout
import ru.wildberries.collage.model.CollageImage
import ru.wildberries.collage.model.RectF
import ru.wildberries.collage.model.CollageRow
import ru.wildberries.collage.model.CollageTile
import ru.wildberries.collage.model.TileFitPolicy
import ru.wildberries.collage.strategy.RowPenaltyModel
import kotlin.math.max
import kotlin.math.min

/**
 * Coordinates the full layout pipeline:
 * row split search, row planning, refinement, and tile geometry materialization
 */
internal class CollageCore(
    private val scorer: TileFitScorer,
    private val renderer: TileGeometryMapper,
    private val rowAugmentor: RowPenaltyModel,
    private val clock: Clock,
    private val logger: Logger = NoopLogger,
    private val rowPlanCache: RowPlanCache = RowPlanCache(),
    private val lossCache: TileLossCache = TileLossCache(),
    private val planner: RowPlanner = RowPlanner(),
    private val tuning: CollageTuning.Snapshot = CollageTuning.default,
    private val config: EngineConfig = EngineConfig(),
) {

    private data class RowBuildResult(
        val rows: List<CollageRow>,
        val nextY: Float,
    )

    private var currentLayoutWidth: Float = Float.NaN

    private val rowLayoutSolutionSearch = RowLayoutSolutionSearch(
        config = config,
        tuning = tuning,
    )

    private val solvedRowPlanRefiner = SolvedRowPlanRefiner(
        config = config,
        tuning = tuning,
        fixedRangePlanBuilder = { photos, range, collageWidth, horizontalGap, verticalGap, heightHint ->
            buildFixedRangePlanAtHeight(
                collageImages = photos,
                range = range,
                collageWidth = collageWidth,
                horizontalGap = horizontalGap,
                verticalGap = verticalGap,
                heightHint = heightHint,
            )
        },
        rowPlanLossCalculator = { photos, range, collageWidth, boxes ->
            rowLossLikePlanner(
                collageImages = photos,
                range = range,
                collageWidth = collageWidth,
                boxes = boxes,
            )
        },
    )

    private fun arrangeSinglePhoto(p: CollageImage, width: Float, minH: Float, maxHRaw: Float): CollageLayout {
        val t0 = clock.nowNs()
        val aspect = if (p.height <= 0f) 1f else p.width / p.height
        val desiredH = width / aspect
        val h = if (config.ignoreMaxHeight) {
            max(max(config.minItemHeight, minH), desiredH)
        } else {
            val hMin = max(config.minItemHeight, minH)
            val hMax = if (maxHRaw.isFinite()) maxHRaw else Float.POSITIVE_INFINITY
            desiredH.coerceIn(hMin, hMax)
        }
        val box = RectF(
            x = 0f,
            y = 0f,
            w = width,
            h = h
        )
        val tile = materializeTile(p, box)
        val dt = clock.nowNs() - t0
        logger.d(
            "Collage",
            "arrangeWithGeometry: n=1 width=$width rows=1 totalH=$h timeNs=$dt"
        )
        return CollageLayout(width, h, listOf(CollageRow(0f, h, listOf(tile))))
    }

    fun arrangeWithGeometry(collageImages: List<CollageImage>): CollageLayout {
        rowPlanCache.clear()
        lossCache.clear()

        val startTimeNs = clock.nowNs()
        val (targetWidth, minHeightAllowed, maxHeightAllowedRaw) = resolveWidthAndHeightLimits()

        if (collageImages.isEmpty()) {
            return CollageLayout(
                width = targetWidth,
                height = 0f,
                rows = emptyList(),
            )
        }

        val maximumHeightForPlanning = if (config.ignoreMaxHeight) {
            Float.POSITIVE_INFINITY
        } else {
            maxHeightAllowedRaw
        }

        currentLayoutWidth = targetWidth

        if (collageImages.size == 1) {
            return arrangeSinglePhoto(
                p = collageImages.first(),
                width = targetWidth,
                minH = minHeightAllowed,
                maxHRaw = maximumHeightForPlanning,
            )
        }

        val geometry = arrangeMultiplePhotos(
            collageImages = collageImages,
            targetWidth = targetWidth,
            minHeightAllowed = minHeightAllowed,
            maximumHeightForPlanning = maximumHeightForPlanning,
        )

        val elapsedTimeNs = clock.nowNs() - startTimeNs
        logger.d(
            "Collage",
            """
                    arrangeWithGeometry: n=${collageImages.size} width=$targetWidth 
                    rows=${geometry.rows.size} totalH=${geometry.height} timeNs=$elapsedTimeNs
                """.trimIndent()
        )

        return geometry
    }

    private fun arrangeMultiplePhotos(
        collageImages: List<CollageImage>,
        targetWidth: Float,
        minHeightAllowed: Float,
        maximumHeightForPlanning: Float,
    ): CollageLayout {
        val horizontalGap = config.spacing
        val verticalGap = config.spacing
        val effectiveMaximumItemsPerRow = effectiveMaxItemsPerRow(targetWidth, horizontalGap)

        val policy = rowLayoutSolutionSearch.computeSearchPolicy(
            collageImages = collageImages,
            collageWidth = targetWidth,
            horizontalGap = horizontalGap,
            effectiveMaximumItemsPerRow = effectiveMaximumItemsPerRow,
            maximumHeightAllowed = maximumHeightForPlanning,
        )

        val dp = buildDynamicProgrammingRowSolver(effectiveMaximumItemsPerRow)

        val rowLayoutSolutionSearchInput = RowLayoutSolutionSearchInput(
            dynamicProgrammingRowSplitSolver = dp,
            collageImages = collageImages,
            collageWidth = targetWidth,
            horizontalGap = horizontalGap,
            verticalGap = verticalGap,
            effectiveMaximumItemsPerRow = effectiveMaximumItemsPerRow,
            minimumHeightAllowed = minHeightAllowed,
            maximumHeightAllowed = maximumHeightForPlanning,
        )

        val best = rowLayoutSolutionSearch.selectBestSolve(
            input = rowLayoutSolutionSearchInput,
            searchRows = policy.searchRows,
        ) ?: rowLayoutSolutionSearch.expandSearchFallback(
            input = rowLayoutSolutionSearchInput,
            minimumRowsByPolicy = policy.minRowsPolicy,
        )

        val chosen = best?.takeIf { it.ranges.isNotEmpty() && it.cost.isFinite() }

        val rows = if (chosen != null) {
            buildRowsFromSolvedPlan(
                collageImages = collageImages,
                targetWidth = targetWidth,
                horizontalGap = horizontalGap,
                verticalGap = verticalGap,
                chosen = chosen,
                minHeightAllowed = minHeightAllowed,
                maximumHeightForPlanning = maximumHeightForPlanning,
            )
        } else {
            buildFallbackRows(
                collageImages = collageImages,
                targetWidth = targetWidth,
                horizontalGap = horizontalGap,
                verticalGap = verticalGap,
                best = best,
                effectiveMaximumItemsPerRow = effectiveMaximumItemsPerRow,
            )
        }

        val totalHeight = rows.lastOrNull()?.let { it.y + it.height } ?: 0f
        return CollageLayout(targetWidth, totalHeight, rows)
    }

    private fun buildRowsFromSolvedPlan(
        collageImages: List<CollageImage>,
        targetWidth: Float,
        horizontalGap: Float,
        verticalGap: Float,
        chosen: RowLayoutSolution,
        minHeightAllowed: Float,
        maximumHeightForPlanning: Float,
    ): List<CollageRow> {
        val finalPlans = solvedRowPlanRefiner.buildFinalPlans(
            SolvedRowPlanRefinementInput(
                collageImages = collageImages,
                collageWidth = targetWidth,
                horizontalGap = horizontalGap,
                verticalGap = verticalGap,
                ranges = chosen.ranges,
                initialHeights = chosen.rowHeights,
                initialBoxes = chosen.rowBoxes,
                minimumHeightAllowed = minHeightAllowed,
                maximumHeightAllowed = maximumHeightForPlanning,
            )
        )

        return buildRowsGeometryFromSolvedPlans(
            collageImages = collageImages,
            verticalGap = verticalGap,
            ranges = chosen.ranges,
            rowBoxes = finalPlans.map { it.boxes },
        )
    }

    private fun buildFallbackRows(
        collageImages: List<CollageImage>,
        targetWidth: Float,
        horizontalGap: Float,
        verticalGap: Float,
        best: RowLayoutSolution?,
        effectiveMaximumItemsPerRow: Int,
    ): List<CollageRow> {
        val (ranges, heights) = resolveRangesAndHeights(
            best = best,
            n = collageImages.size,
            effectiveMaximumItemsPerRow = effectiveMaximumItemsPerRow,
        )

        return buildRowsGeometry(
            collageImages = collageImages,
            collageWidth = targetWidth,
            horizontalGap = horizontalGap,
            verticalGap = verticalGap,
            ranges = ranges,
            rowHeights = heights,
        )
    }
    private fun buildFixedRangePlanAtHeight(
        collageImages: List<CollageImage>,
        range: IntRange,
        collageWidth: Float,
        horizontalGap: Float,
        verticalGap: Float,
        heightHint: Float,
    ): SolvedRowPlan? {
        val heightBucket = MathUtil.fastRoundToInt(heightHint / tuning.dynamicProgrammingConfig.heightQuantStep)

        val cachedSlot = rowPlanCache.get(
            startIndex = range.first,
            endIndex = range.last,
            heightQuant = heightBucket,
        )

        if (cachedSlot >= 0) {
            return solvedRowPlanFromCache(cachedSlot)
        }

        val context = RowLayoutContext(
            collageImages = collageImages,
            startIndexInclusive = range.first,
            endIndexInclusive = range.last,
            collageWidth = collageWidth,
            horizontalGap = horizontalGap,
            verticalGap = verticalGap,
            minItemWidth = config.minItemWidth,
            minItemHeight = config.minItemHeight,
            rowHeightHint = heightHint,
            tileFitScorer = scorer,
            tileFitPolicy = config.tileFitPolicy,
        )

        val rowPlan = planner.tryPlan(context) ?: return null

        val slot = rowPlanCache.put(
            startIndex = range.first,
            endIndex = range.last,
            heightQuant = heightBucket,
            rowHeight = rowPlan.rowHeight,
            rowLoss = rowPlan.loss.toFloat(),
            boxes = rowPlan.boxes,
        )

        return solvedRowPlanFromCache(slot)
    }

    private fun solvedRowPlanFromCache(slot: Int): SolvedRowPlan {
        return SolvedRowPlan(
            height = rowPlanCache.rowHeightBySlot[slot],
            boxes = rowPlanCache.boxesAsList(slot),
            loss = rowPlanCache.rowLossBySlot[slot].toDouble(),
        )
    }

    private fun rowLossLikePlanner(
        collageImages: List<CollageImage>,
        range: IntRange,
        collageWidth: Float,
        boxes: List<RectF>,
    ): Double {
        if (boxes.isEmpty()) return 0.0

        val rowPhotos = collageImages.subList(range.first, range.last + 1)
        var accumulatedLoss = 0f
        var tileIndex = 0

        while (tileIndex < boxes.size) {
            val photo = rowPhotos[tileIndex]
            val box = boxes[tileIndex]
            val rawDecision = scorer.decide(photo, box)

            val shouldForceContain =
                config.tileFitPolicy == TileFitPolicy.Auto &&
                        tuning.heuristics.shouldForceContainInNarrowContainerForMaterialization(
                            layoutWidthPx = collageWidth,
                            imageAspect = MathUtil.aspect(photo.width, photo.height),
                            cropRatio = rawDecision.crop,
                        )

            val useCover = rawDecision.useCover && !shouldForceContain
            accumulatedLoss += if (useCover) rawDecision.cover else rawDecision.contain

            tileIndex++
        }

        return accumulatedLoss.toDouble()
    }

    private fun buildRowsGeometryFromSolvedPlans(
        collageImages: List<CollageImage>,
        verticalGap: Float,
        ranges: List<IntRange>,
        rowBoxes: List<List<RectF>>,
    ): List<CollageRow> {
        var cursorY = 0f
        val out = ArrayList<CollageRow>(ranges.size)

        for (idx in ranges.indices) {
            val range = ranges[idx]
            val segment = collageImages.subList(range.first, range.last + 1)
            val boxes = rowBoxes[idx]

            val tiles = ArrayList<CollageTile>(boxes.size)
            var rowHeight = 0f

            var k = 0
            while (k < boxes.size) {
                val b = boxes[k]
                rowHeight = max(rowHeight, b.h)
                val abs = RectF(
                    x = b.x,
                    y = cursorY + b.y,
                    w = b.w,
                    h = b.h,
                )
                tiles += materializeTile(segment[k], abs)
                k++
            }

            out += CollageRow(cursorY, rowHeight, tiles)
            cursorY += rowHeight + verticalGap
        }

        return out
    }

    private fun buildDynamicProgrammingRowSolver(effectiveMaximumItemsPerRow: Int): DynamicProgrammingRowSplitSolver {
        return DynamicProgrammingRowSplitSolver(
            params = DynamicProgrammingParams(
                maxItemsPerRow = effectiveMaximumItemsPerRow,
                maxHorizontalsPerRow = config.maxHorizontalsPerRow,
                minItemWidth = config.minItemWidth,
                minItemHeight = config.minItemHeight,
                tauHorizontal = tuning.dynamicProgrammingConfig.tauHorizontal,
                contrastTau = tuning.dynamicProgrammingConfig.contrastTau,
                rowContrastAlpha = tuning.dynamicProgrammingConfig.rowContrastAlpha,
                rowWidthBalanceAlpha = tuning.dynamicProgrammingConfig.rowWidthBalanceAlpha,
                verticalSquashGuardFracOfWidth = tuning.dynamicProgrammingConfig.verticalSquashGuardFracOfWidth,
                verticalSquashAlpha = tuning.dynamicProgrammingConfig.verticalSquashAlpha,
                rowHeightSmoothAlpha = tuning.dynamicProgrammingConfig.rowHeightSmoothAlpha,
                heightBudgetAlpha = tuning.dynamicProgrammingConfig.heightBudgetAlpha,
                rowLenPrior = config.rowLengthBias
            ),
            tileFitScorer = scorer,
            rowAugmentor = rowAugmentor,
            planner = planner,
            rowPlanCache = rowPlanCache,
            logger = logger,
            tuning = tuning,
            tileFitPolicy = config.tileFitPolicy,
        )
    }

    private fun resolveRangesAndHeights(
        best: RowLayoutSolution?,
        n: Int,
        effectiveMaximumItemsPerRow: Int,
    ): Pair<List<IntRange>, List<Float>> {
        val okBest = best != null && best.ranges.isNotEmpty() && best.cost.isFinite()
        if (okBest) return best.ranges to best.rowHeights

        val ranges = SafeRowRanges.build(
            photoCount = n,
            effectiveMaximumItemsPerRow = effectiveMaximumItemsPerRow,
        )
        val heights = List(ranges.size) { max(config.minItemHeight, 1f) }
        return ranges to heights
    }

    /**
     * Builds absolute position rows. Falls back to equal width boxes if planner fails
     */
    private fun buildRowsGeometry(
        collageImages: List<CollageImage>,
        collageWidth: Float,
        horizontalGap: Float,
        verticalGap: Float,
        ranges: List<IntRange>,
        rowHeights: List<Float>,
    ): List<CollageRow> {
        var cursorY = 0f
        val rows = ArrayList<CollageRow>()

        for (idx in ranges.indices) {
            val result = materializeRangeWithFallback(
                collageImages = collageImages,
                collageWidth = collageWidth,
                horizontalGap = horizontalGap,
                verticalGap = verticalGap,
                range = ranges[idx],
                height = rowHeights[idx],
                cursorY = cursorY,
            )

            rows += result.rows
            cursorY = result.nextY
        }

        return rows
    }

    private fun materializeRangeWithFallback(
        collageImages: List<CollageImage>,
        collageWidth: Float,
        horizontalGap: Float,
        verticalGap: Float,
        range: IntRange,
        height: Float,
        cursorY: Float,
    ): RowBuildResult {
        val segment = collageImages.subList(range.first, range.last + 1)
        val length = segment.size

        if (length == 1) {
            return buildSingleTileRow(
                collageImage = segment[0],
                collageWidth = collageWidth,
                rowHeight = height,
                cursorY = cursorY,
                verticalGap = verticalGap,
            )
        }

        val context = RowLayoutContext(
            collageImages = collageImages,
            startIndexInclusive = range.first,
            endIndexInclusive = range.last,
            collageWidth = collageWidth,
            horizontalGap = horizontalGap,
            verticalGap = verticalGap,
            minItemWidth = config.minItemWidth,
            minItemHeight = config.minItemHeight,
            rowHeightHint = height,
            tileFitScorer = scorer,
            tileFitPolicy = config.tileFitPolicy,
        )

        val plan = planner.tryPlan(context)

        if (plan != null) {
            val tiles = ArrayList<CollageTile>(plan.boxes.size)
            var tileIndex = 0

            while (tileIndex < plan.boxes.size) {
                val box = plan.boxes[tileIndex]
                val absoluteBox = RectF(
                    x = box.x,
                    y = cursorY + box.y,
                    w = box.w,
                    h = box.h,
                )
                tiles += materializeTile(segment[tileIndex], absoluteBox)
                tileIndex++
            }

            val row = CollageRow(
                y = cursorY,
                height = plan.rowHeight,
                tiles = tiles,
            )

            return RowBuildResult(
                rows = listOf(row),
                nextY = cursorY + plan.rowHeight + verticalGap,
            )
        }

        return buildSingletonFallbackRows(
            collageImages = collageImages,
            collageWidth = collageWidth,
            verticalGap = verticalGap,
            range = range,
            cursorY = cursorY,
        )
    }

    private fun buildSingleTileRow(
        collageImage: CollageImage,
        collageWidth: Float,
        rowHeight: Float,
        cursorY: Float,
        verticalGap: Float,
    ): RowBuildResult {
        val box = RectF(
            x = 0f,
            y = cursorY,
            w = collageWidth,
            h = rowHeight,
        )

        val tile = materializeTile(collageImage, box)
        val row = CollageRow(
            y = cursorY,
            height = rowHeight,
            tiles = listOf(tile),
        )

        return RowBuildResult(
            rows = listOf(row),
            nextY = cursorY + rowHeight + verticalGap,
        )
    }

    private fun buildSingletonFallbackRows(
        collageImages: List<CollageImage>,
        collageWidth: Float,
        verticalGap: Float,
        range: IntRange,
        cursorY: Float,
    ): RowBuildResult {
        var nextY = cursorY
        val rows = ArrayList<CollageRow>(range.last - range.first + 1)

        var photoIndex = range.first
        while (photoIndex <= range.last) {
            val photo = collageImages[photoIndex]
            val aspectRatio = MathUtil.aspect(photo.width, photo.height)

            val desiredRowHeight = collageWidth / aspectRatio
            val clampedRowHeight = desiredRowHeight.coerceIn(
                config.minItemHeight,
                config.minItemHeight * 16f,
            )

            val box = RectF(
                x = 0f,
                y = nextY,
                w = collageWidth,
                h = clampedRowHeight,
            )

            val tile = materializeTile(photo, box)
            rows += CollageRow(
                y = nextY,
                height = clampedRowHeight,
                tiles = listOf(tile),
            )

            nextY += clampedRowHeight + verticalGap
            photoIndex++
        }

        return RowBuildResult(
            rows = rows,
            nextY = nextY,
        )
    }

    private fun effectiveMaxItemsPerRow(collageWidth: Float, gap: Float): Int {
        val capByWidth = ((collageWidth + gap) / (config.minItemWidth + gap)).toInt().coerceAtLeast(1)
        return min(config.maxItemsPerRow, capByWidth)
    }

    private fun materializeTile(collageImage: CollageImage, absBox: RectF): CollageTile {
        val decision = decideWithCache(collageImage, absBox)
        return renderer.materialize(collageImage, absBox, decision)
    }

    /**
     * Resolves the COVER/CONTAIN decision for a tile
     *
     * The cache key uses the image identity and quantized frame size
     * Tile position is intentionally ignored because fit loss depends only on size
     */
    private fun decideWithCache(
        collageImage: CollageImage,
        box: RectF,
    ): TileLossDecision {
        val photoKey = MathUtil.mixPhotoKey(collageImage.imageId, collageImage.width, collageImage.height)
        val boxKey = MathUtil.quantizeBoxKeyWH(box.w, box.h, 1.0f)
        val cached = lossCache.get(photoKey, boxKey)
        if (cached != null) return cached
        val rawDecision = scorer.decide(collageImage, box)
        val decision = applyNarrowContainerContainGuard(
            collageImage = collageImage,
            decision = rawDecision,
        )

        lossCache.put(photoKey, boxKey, decision)
        return decision
    }

    private fun applyNarrowContainerContainGuard(
        collageImage: CollageImage,
        decision: TileLossDecision,
    ): TileLossDecision {
        if (config.tileFitPolicy == TileFitPolicy.CoverOnly) {
            return decision.copy(useCover = true)
        }

        val shouldForceContain = tuning.heuristics
            .shouldForceContainInNarrowContainerForMaterialization(
                layoutWidthPx = currentLayoutWidth,
                imageAspect = MathUtil.aspect(collageImage.width, collageImage.height),
                cropRatio = decision.crop,
            )

        return if (shouldForceContain && decision.useCover) {
            decision.copy(useCover = false)
        } else {
            decision
        }
    }

    /**
     * Resolves the target collage width and height bounds from the current request
     */
    private fun resolveWidthAndHeightLimits(): Triple<Float, Float, Float> {
        with(config) {
            val minWidth = minCollageSize?.width ?: 0f
            val maxWidth = maxCollageSize?.width ?: Float.POSITIVE_INFINITY
            val minHeight = minCollageSize?.height ?: 0f
            val maxHeight = maxCollageSize?.height ?: Float.POSITIVE_INFINITY

            val width = when {
                maxWidth.isFinite() -> maxWidth
                minWidth > 0f -> minWidth
                else -> max(1f, minItemWidth * 2f)
            }.coerceAtLeast(minItemWidth * 1.5f)

            require(width in minWidth..maxWidth) { "width=$width not in [$minWidth..$maxWidth]" }
            return Triple(width, minHeight, maxHeight)
        }
    }
}
