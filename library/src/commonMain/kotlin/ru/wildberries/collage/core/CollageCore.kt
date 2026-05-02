package ru.wildberries.collage.core

import ru.wildberries.collage.api.Clock
import ru.wildberries.collage.api.CollageEngine
import ru.wildberries.collage.api.EngineConfig
import ru.wildberries.collage.api.Logger
import ru.wildberries.collage.api.NoopLogger
import ru.wildberries.collage.cache.LossCache
import ru.wildberries.collage.cache.PlanCache
import ru.wildberries.collage.model.CollageGeometry
import ru.wildberries.collage.model.Photo
import ru.wildberries.collage.model.RectF
import ru.wildberries.collage.model.RowGeometry
import ru.wildberries.collage.model.TileGeometry
import ru.wildberries.collage.strategy.RowCostAugmentor
import kotlin.math.max
import kotlin.math.min

/**
 * Collage orchestrator photos -> DP solver -> row layout -> geometry materialization
 */
internal class CollageCore(
    private val scorer: TileScorer,
    private val renderer: TileRenderer,
    private val rowAugmentor: RowCostAugmentor,
    private val clock: Clock,
    private val logger: Logger = NoopLogger,
    private val planCache: PlanCache = PlanCache(),
    private val lossCache: LossCache = LossCache(),
    private val planner: RowPlanner = RowPlanner(),
    private val tuning: CollageTuning.Snapshot = CollageTuning.current,
    private val config: EngineConfig = EngineConfig(),
) : CollageEngine {

    private data class RowBuildResult(
        val rows: List<RowGeometry>,
        val nextY: Float,
    )

    private var _layoutWidth: Float = Float.NaN

    private val rowSolveSearch = RowSolveSearch(
        config = config,
        tuning = tuning,
    )

    private val solvedRowPlanRefiner = SolvedRowPlanRefiner(
        config = config,
        tuning = tuning,
        fixedRangePlanBuilder = { photos, range, collageWidth, horizontalGap, verticalGap, heightHint ->
            buildFixedRangePlanAtHeight(
                photos = photos,
                range = range,
                collageWidth = collageWidth,
                horizontalGap = horizontalGap,
                verticalGap = verticalGap,
                heightHint = heightHint,
            )
        },
        rowPlanLossCalculator = { photos, range, collageWidth, boxes ->
            rowLossLikePlanner(
                photos = photos,
                range = range,
                collageWidth = collageWidth,
                boxes = boxes,
            )
        },
    )

    private fun arrangeSinglePhoto(p: Photo, width: Float, minH: Float, maxHRaw: Float): CollageGeometry {
        val t0 = clock.nowNs()
        val aspect = if (p.height <= 0f) 1f else p.width / p.height
        val desiredH = width / aspect
        val h = if (config.ignoreHeightCaps) {
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
        return CollageGeometry(width, h, listOf(RowGeometry(0f, h, listOf(tile))))
    }

    override fun arrangeWithGeometry(photos: List<Photo>): CollageGeometry {
        planCache.clear()
        lossCache.clear()

        if (photos.isEmpty()) return CollageGeometry(0f, 0f, emptyList())

        val startTimeNs = clock.nowNs()
        val (targetWidth, minHeightAllowed, maxHeightAllowedRaw) = resolveWidthAndHeightLimits()

        val maximumHeightForPlanning = if (config.ignoreHeightCaps) {
            Float.POSITIVE_INFINITY
        } else {
            maxHeightAllowedRaw
        }

        _layoutWidth = targetWidth

        if (photos.size == 1) {
            return arrangeSinglePhoto(
                p = photos.first(),
                width = targetWidth,
                minH = minHeightAllowed,
                maxHRaw = maximumHeightForPlanning,
            )
        }

        val geometry = arrangeMultiplePhotos(
            photos = photos,
            targetWidth = targetWidth,
            minHeightAllowed = minHeightAllowed,
            maximumHeightForPlanning = maximumHeightForPlanning,
        )

        val elapsedTimeNs = clock.nowNs() - startTimeNs
        logger.d(
            "Collage",
            """
                    arrangeWithGeometry: n=${photos.size} width=$targetWidth 
                    rows=${geometry.rows.size} totalH=${geometry.height} timeNs=$elapsedTimeNs
                """.trimIndent()
        )

        return geometry
    }

    private fun arrangeMultiplePhotos(
        photos: List<Photo>,
        targetWidth: Float,
        minHeightAllowed: Float,
        maximumHeightForPlanning: Float,
    ): CollageGeometry {
        val horizontalGap = config.paddings
        val verticalGap = config.paddings
        val effectiveMaximumItemsPerRow = effectiveMaxItemsPerRow(targetWidth, horizontalGap)

        val policy = rowSolveSearch.computeSearchPolicy(
            photos = photos,
            collageWidth = targetWidth,
            horizontalGap = horizontalGap,
            effectiveMaximumItemsPerRow = effectiveMaximumItemsPerRow,
            maximumHeightAllowed = maximumHeightForPlanning,
        )

        val dp = buildDynamicProgrammingRowSolver(effectiveMaximumItemsPerRow)

        val rowSolveSearchInput = RowSolveSearchInput(
            dynamicProgrammingRowSolver = dp,
            photos = photos,
            collageWidth = targetWidth,
            horizontalGap = horizontalGap,
            verticalGap = verticalGap,
            effectiveMaximumItemsPerRow = effectiveMaximumItemsPerRow,
            minimumHeightAllowed = minHeightAllowed,
            maximumHeightAllowed = maximumHeightForPlanning,
        )

        val best = rowSolveSearch.selectBestSolve(
            input = rowSolveSearchInput,
            searchRows = policy.searchRows,
        ) ?: rowSolveSearch.expandSearchFallback(
            input = rowSolveSearchInput,
            minimumRowsByPolicy = policy.minRowsPolicy,
        )

        val chosen = best?.takeIf { it.ranges.isNotEmpty() && it.cost.isFinite() }

        val rows = if (chosen != null) {
            buildRowsFromSolvedPlan(
                photos = photos,
                targetWidth = targetWidth,
                horizontalGap = horizontalGap,
                verticalGap = verticalGap,
                chosen = chosen,
                minHeightAllowed = minHeightAllowed,
                maximumHeightForPlanning = maximumHeightForPlanning,
            )
        } else {
            buildFallbackRows(
                photos = photos,
                targetWidth = targetWidth,
                horizontalGap = horizontalGap,
                verticalGap = verticalGap,
                best = best,
                effectiveMaximumItemsPerRow = effectiveMaximumItemsPerRow,
            )
        }

        val totalHeight = rows.lastOrNull()?.let { it.y + it.height } ?: 0f
        return CollageGeometry(targetWidth, totalHeight, rows)
    }

    private fun buildRowsFromSolvedPlan(
        photos: List<Photo>,
        targetWidth: Float,
        horizontalGap: Float,
        verticalGap: Float,
        chosen: RowSolve,
        minHeightAllowed: Float,
        maximumHeightForPlanning: Float,
    ): List<RowGeometry> {
        val finalPlans = solvedRowPlanRefiner.buildFinalPlans(
            SolvedRowPlanRefinementInput(
                photos = photos,
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
            photos = photos,
            verticalGap = verticalGap,
            ranges = chosen.ranges,
            rowBoxes = finalPlans.map { it.boxes },
        )
    }

    private fun buildFallbackRows(
        photos: List<Photo>,
        targetWidth: Float,
        horizontalGap: Float,
        verticalGap: Float,
        best: RowSolve?,
        effectiveMaximumItemsPerRow: Int,
    ): List<RowGeometry> {
        val (ranges, heights) = resolveRangesAndHeights(
            best = best,
            n = photos.size,
            effectiveMaximumItemsPerRow = effectiveMaximumItemsPerRow,
        )

        return buildRowsGeometry(
            photos = photos,
            collageWidth = targetWidth,
            horizontalGap = horizontalGap,
            verticalGap = verticalGap,
            ranges = ranges,
            rowHeights = heights,
        )
    }
    private fun buildFixedRangePlanAtHeight(
        photos: List<Photo>,
        range: IntRange,
        collageWidth: Float,
        horizontalGap: Float,
        verticalGap: Float,
        heightHint: Float,
    ): SolvedRowPlan? {
        val heightBucket = MathUtil.fastRoundToInt(heightHint / tuning.dynamicProgrammingConfig.heightQuantStep)

        val cachedSlot = planCache.get(
            startIndex = range.first,
            endIndex = range.last,
            heightQuant = heightBucket,
        )

        if (cachedSlot >= 0) {
            return solvedRowPlanFromCache(cachedSlot)
        }

        val context = RowLayoutContext(
            photos = photos,
            startIndexInclusive = range.first,
            endIndexInclusive = range.last,
            collageWidth = collageWidth,
            horizontalGap = horizontalGap,
            verticalGap = verticalGap,
            minItemWidth = config.minItemWidth,
            minItemHeight = config.minItemHeight,
            rowHeightHint = heightHint,
            tileScorer = scorer,
        )

        val rowPlan = planner.tryPlan(context) ?: return null

        val slot = planCache.put(
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
            height = planCache.rowHeightBySlot[slot],
            boxes = planCache.boxesAsList(slot),
            loss = planCache.rowLossBySlot[slot].toDouble(),
        )
    }

    private fun rowLossLikePlanner(
        photos: List<Photo>,
        range: IntRange,
        collageWidth: Float,
        boxes: List<RectF>,
    ): Double {
        if (boxes.isEmpty()) return 0.0

        val rowPhotos = photos.subList(range.first, range.last + 1)
        var accumulatedLoss = 0f
        var tileIndex = 0

        while (tileIndex < boxes.size) {
            val photo = rowPhotos[tileIndex]
            val box = boxes[tileIndex]
            val rawDecision = scorer.decide(photo, box)

            val shouldForceContain = tuning.heuristics
                .shouldForceContainInNarrowContainerForMaterialization(
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
        photos: List<Photo>,
        verticalGap: Float,
        ranges: List<IntRange>,
        rowBoxes: List<List<RectF>>,
    ): List<RowGeometry> {
        var cursorY = 0f
        val out = ArrayList<RowGeometry>(ranges.size)

        for (idx in ranges.indices) {
            val range = ranges[idx]
            val segment = photos.subList(range.first, range.last + 1)
            val boxes = rowBoxes[idx]

            val tiles = ArrayList<TileGeometry>(boxes.size)
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

            out += RowGeometry(cursorY, rowHeight, tiles)
            cursorY += rowHeight + verticalGap
        }

        return out
    }

    private fun buildDynamicProgrammingRowSolver(effectiveMaximumItemsPerRow: Int): DynamicProgrammingRowSolver {
        return DynamicProgrammingRowSolver(
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
                rowLenPrior = config.rowLengthPriority
            ),
            tileScorer = scorer,
            rowAugmentor = rowAugmentor,
            planner = planner,
            planCache = planCache,
            logger = logger,
            tuning = tuning
        )
    }

    private fun resolveRangesAndHeights(
        best: RowSolve?,
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
        photos: List<Photo>,
        collageWidth: Float,
        horizontalGap: Float,
        verticalGap: Float,
        ranges: List<IntRange>,
        rowHeights: List<Float>,
    ): List<RowGeometry> {
        var cursorY = 0f
        val rows = ArrayList<RowGeometry>()

        for (idx in ranges.indices) {
            val result = materializeRangeWithFallback(
                photos = photos,
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
        photos: List<Photo>,
        collageWidth: Float,
        horizontalGap: Float,
        verticalGap: Float,
        range: IntRange,
        height: Float,
        cursorY: Float,
    ): RowBuildResult {
        val segment = photos.subList(range.first, range.last + 1)
        val length = segment.size

        if (length == 1) {
            return buildSingleTileRow(
                photo = segment[0],
                collageWidth = collageWidth,
                rowHeight = height,
                cursorY = cursorY,
                verticalGap = verticalGap,
            )
        }

        val context = RowLayoutContext(
            photos = photos,
            startIndexInclusive = range.first,
            endIndexInclusive = range.last,
            collageWidth = collageWidth,
            horizontalGap = horizontalGap,
            verticalGap = verticalGap,
            minItemWidth = config.minItemWidth,
            minItemHeight = config.minItemHeight,
            rowHeightHint = height,
            tileScorer = scorer,
        )

        val plan = planner.tryPlan(context)

        if (plan != null) {
            val tiles = ArrayList<TileGeometry>(plan.boxes.size)
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

            val row = RowGeometry(
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
            photos = photos,
            collageWidth = collageWidth,
            verticalGap = verticalGap,
            range = range,
            cursorY = cursorY,
        )
    }

    private fun buildSingleTileRow(
        photo: Photo,
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

        val tile = materializeTile(photo, box)
        val row = RowGeometry(
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
        photos: List<Photo>,
        collageWidth: Float,
        verticalGap: Float,
        range: IntRange,
        cursorY: Float,
    ): RowBuildResult {
        var nextY = cursorY
        val rows = ArrayList<RowGeometry>(range.last - range.first + 1)

        var photoIndex = range.first
        while (photoIndex <= range.last) {
            val photo = photos[photoIndex]
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
            rows += RowGeometry(
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

    private fun materializeTile(photo: Photo, absBox: RectF): TileGeometry {
        val decision = decideWithCache(photo, absBox)
        return renderer.materialize(photo, absBox, decision)
    }

    /**
     * COVER/CONTAIN decision with a small cache keyed by quantized (W,H)
     * Position (X,Y) is irrelevant for loss
     */
    private fun decideWithCache(
        photo: Photo,
        box: RectF,
    ): LossDecision {
        val photoKey = MathUtil.mixPhotoKey(photo.imageId, photo.width, photo.height)
        val boxKey = MathUtil.quantizeBoxKeyWH(box.w, box.h, 1.0f)
        val cached = lossCache.get(photoKey, boxKey)
        if (cached != null) return cached
        val rawDecision = scorer.decide(photo, box)
        val decision = applyNarrowContainerContainGuard(
            photo = photo,
            decision = rawDecision,
        )

        lossCache.put(photoKey, boxKey, decision)
        return decision
    }

    private fun applyNarrowContainerContainGuard(
        photo: Photo,
        decision: LossDecision,
    ): LossDecision {
        val shouldForceContain = tuning.heuristics
            .shouldForceContainInNarrowContainerForMaterialization(
                layoutWidthPx = _layoutWidth,
                imageAspect = MathUtil.aspect(photo.width, photo.height),
                cropRatio = decision.crop,
            )

        return if (shouldForceContain && decision.useCover) {
            decision.copy(useCover = false)
        } else {
            decision
        }
    }

    /** Decide target collage width and height limits from optional min/max constraints */
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
