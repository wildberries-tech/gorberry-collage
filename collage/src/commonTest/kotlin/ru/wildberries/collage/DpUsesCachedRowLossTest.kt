package ru.wildberries.collage

import ru.wildberries.collage.cache.RowPlanCache
import ru.wildberries.collage.core.CollageTuning
import ru.wildberries.collage.core.DynamicProgrammingRowSplitSolver
import ru.wildberries.collage.core.DynamicProgrammingParams
import ru.wildberries.collage.core.TileLossDecision
import ru.wildberries.collage.core.MathUtil
import ru.wildberries.collage.core.RowPlanner
import ru.wildberries.collage.core.TileFitScorer
import ru.wildberries.collage.model.CollageImage
import ru.wildberries.collage.model.RectF
import ru.wildberries.collage.strategy.RowPenaltyModel
import ru.wildberries.collage.strategy.RowPenaltyContext
import kotlin.test.Test
import kotlin.test.assertEquals

class DpUsesCachedRowLossTest {

    @Test
    fun dp_uses_cached_row_loss_in_minimization() {
        val collageImages = listOf(
            CollageImage(0, 1000f, 1000f),
            CollageImage(1, 1000f, 1000f),
            CollageImage(2, 1000f, 1000f)
        )

        val gap = 6f
        val h = 100f
        val collageW = 3 * h + 2 * gap
        val params = DynamicProgrammingParams(
            maxItemsPerRow = 3,
            maxHorizontalsPerRow = 3,
            minItemWidth = 56f,
            minItemHeight = 56f,
            tauHorizontal = CollageTuning.default.dynamicProgrammingConfig.tauHorizontal
        )

        val rowPlanCache = RowPlanCache()
        val dynamicProgrammingRowSplitSolver = DynamicProgrammingRowSplitSolver(
            params = params,
            tileFitScorer = object : TileFitScorer {
                override fun decide(collageImage: CollageImage, box: RectF) =
                    TileLossDecision(cover = 1f, contain = 1f, crop = 0f, useCover = true)
            },
            rowAugmentor = object : RowPenaltyModel {
                override fun totalPenalty(penaltyContext: RowPenaltyContext): Double = 0.0
            },
            planner = RowPlanner(),
            rowPlanCache = rowPlanCache,
            logger = TestLogger(),
        )

        val hQ = MathUtil.fastRoundToInt(h / CollageTuning.default.dynamicProgrammingConfig.heightQuantStep)
        val boxes = listOf(
            RectF(x = 0f, y = 0f, w = 100f, h = h),
            RectF(x = 106f, y = 0f, w = 100f, h = h),
            RectF(x = 212f, y = 0f, w = 100f, h = h)
        )
        rowPlanCache.put(
            startIndex = 0,
            endIndex = 2,
            heightQuant = hQ,
            rowHeight = h,
            rowLoss = 0f,
            boxes = boxes,
        )

        val solve = dynamicProgrammingRowSplitSolver.solveForTargetRows(
            collageImages = collageImages,
            collageWidth = collageW,
            horizontalGap = gap,
            verticalGap = gap,
            targetRows = 1,
            maxHeightAllowed = Float.POSITIVE_INFINITY,
        )

        assertEquals(solve.ranges.size, 1)
        assertEquals(0..2, solve.ranges.first())
        assertEquals(h, solve.rowHeights.first(), 1e-2f)
    }
}
