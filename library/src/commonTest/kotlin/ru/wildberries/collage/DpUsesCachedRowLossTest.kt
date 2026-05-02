package ru.wildberries.collage

import ru.wildberries.collage.cache.PlanCache
import ru.wildberries.collage.core.CollageTuning
import ru.wildberries.collage.core.DynamicProgrammingRowSolver
import ru.wildberries.collage.core.DynamicProgrammingParams
import ru.wildberries.collage.core.LossDecision
import ru.wildberries.collage.core.MathUtil
import ru.wildberries.collage.core.RowPlanner
import ru.wildberries.collage.core.TileScorer
import ru.wildberries.collage.model.Photo
import ru.wildberries.collage.model.RectF
import ru.wildberries.collage.strategy.RowCostAugmentor
import ru.wildberries.collage.strategy.RowPenaltyContext
import kotlin.test.Test
import kotlin.test.assertEquals

class DpUsesCachedRowLossTest {

    @Test
    fun dp_uses_cached_row_loss_in_minimization() {
        val photos = listOf(
            Photo(0, 1000f, 1000f),
            Photo(1, 1000f, 1000f),
            Photo(2, 1000f, 1000f)
        )

        val gap = 6f
        val h = 100f
        val collageW = 3 * h + 2 * gap
        val params = DynamicProgrammingParams(
            maxItemsPerRow = 3,
            maxHorizontalsPerRow = 3,
            minItemWidth = 56f,
            minItemHeight = 56f,
            tauHorizontal = CollageTuning.current.dynamicProgrammingConfig.tauHorizontal
        )

        val planCache = PlanCache()
        val dynamicProgrammingRowSolver = DynamicProgrammingRowSolver(
            params = params,
            tileScorer = object : TileScorer {
                override fun decide(photo: Photo, box: RectF) =
                    LossDecision(cover = 1f, contain = 1f, crop = 0f, useCover = true)
            },
            rowAugmentor = object : RowCostAugmentor {
                override fun totalPenalty(penaltyContext: RowPenaltyContext): Double = 0.0
            },
            planner = RowPlanner(),
            planCache = planCache,
            logger = TestLogger(),
        )

        val hQ = MathUtil.fastRoundToInt(h / CollageTuning.current.dynamicProgrammingConfig.heightQuantStep)
        val boxes = listOf(
            RectF(x = 0f, y = 0f, w = 100f, h = h),
            RectF(x = 106f, y = 0f, w = 100f, h = h),
            RectF(x = 212f, y = 0f, w = 100f, h = h)
        )
        planCache.put(
            startIndex = 0,
            endIndex = 2,
            heightQuant = hQ,
            rowHeight = h,
            rowLoss = 0f,
            boxes = boxes,
        )

        val solve = dynamicProgrammingRowSolver.solveForTargetRows(
            photos = photos,
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
