package ru.wildberries.collage

import ru.wildberries.collage.core.CollageTuning
import ru.wildberries.collage.model.RectF
import ru.wildberries.collage.strategy.DefaultRowPenaltyModel
import ru.wildberries.collage.strategy.RowLengthBias
import ru.wildberries.collage.strategy.RowPenaltyContext
import kotlin.test.Test
import kotlin.test.assertTrue

class RowPenaltyModelPenaltiesTest {

    @Test
    fun vertical_squash_penalty_triggers_when_height_below_guard() {
        val collageWidth = 360f
        val guardFrac = CollageTuning.default.augmentor.verticalSquashGuardFracOfWidth
        val guardH = guardFrac * collageWidth

        fun ctxWithHeight(h: Float) = RowPenaltyContext(
            length = 3, isLastRow = false,
            plannedHeight = h, heightHint = null,
            rowIndex = 0, rowsLeftAfterThis = 0,
            hCount = 0, vCount = 3,
            currentMostlyV = true,
            planBoxes = listOf(
                RectF(x = 0f, y = 0f, w = 100f, h = 100f),
                RectF(x = 120f, y = 0f, w = 100f, h = 100f),
                RectF(x = 240f, y = 0f, w = 100f, h = 100f),
            ),
            collageWidth = collageWidth, verticalGap = 6f,
            nextRowFirstHeight = null, nextMostlyV = false, tailTotalHeight = 0f,
            contrastTau = CollageTuning.default.dynamicProgrammingConfig.contrastTau,
            rowContrastAlpha = CollageTuning.default.augmentor.rowContrastAlpha,
            rowWidthBalanceAlpha = CollageTuning.default.augmentor.rowWidthBalanceAlpha,
            verticalSquashGuardFracOfWidth = guardFrac,
            verticalSquashAlpha = CollageTuning.default.augmentor.verticalSquashAlpha,
            rowHeightSmoothAlpha = 0.0,
            heightBudgetAlpha = 0.0,
            maxHeightAllowed = Float.POSITIVE_INFINITY,
            areaUnit = (collageWidth * 56f).toDouble(),
            rowLenPrior = RowLengthBias(),
            nextHeightHint = null,
            totalItems = 4,
            itemsLeftAfterThis = 3,
        )

        val augmentor = DefaultRowPenaltyModel(
            widow = CollageTuning.WidowPolicyConfig(
                lastLen1 = 0.0,
                lastLen2 = 0.0,
                loneLen1Mid = 0.0,
                heightDevAlpha = 0.0
            ),
            penaltyPerExtraHorizontal = 0.0,
            penaltyTwoHorizontalsInOneRow = 0.0,
            penaltyThreeHorizontalsInOneRow = 0.0,
            topHeavinessAlpha = 0.0,
            lastRowTallAlpha = 0.0,
            firstRowShortAlpha = 0.0,
            preferThreeVerticalsBonus = 0.0,
            rowContrastAlpha = 0.0,
            rowWidthBalanceAlpha = 0.0,
            verticalSquashGuardFracOfWidth = CollageTuning.default.augmentor.verticalSquashGuardFracOfWidth,
            verticalSquashAlpha = CollageTuning.default.augmentor.verticalSquashAlpha,
            rowHeightSmoothAlpha = 0.0,
            heightBudgetAlpha = 0.0,
            stickGamma4 = 0.0f,
            stickGamma3 = 0.0f,
            stickPenaltyAlpha = 0.0,
            fourMixPenalty = 0.0,
            equalizePerRowAlpha = 0.0,
            kpAlpha = 0.0,
            kpPower = 1.0,
            fitnessJumpAlpha = 0.0,
            fillAlpha = 0.0,
            tightBuckets = CollageTuning.default.augmentor.tightBuckets
        )

        val penAt = augmentor.totalPenalty(ctxWithHeight(guardH))
        val penLow = augmentor.totalPenalty(ctxWithHeight(guardH * 0.7f))

        assertTrue(
            penLow > penAt + 1e-6,
            "Штраф ниже гварда должен быть выше low=$penLow vs at=$penAt"
        )
    }

    @Test
    fun contrast_penalty_triggers_for_two_vertical_rows_with_big_height_ratio() {
        val collageWidth = 360f
        val tau = CollageTuning.default.dynamicProgrammingConfig.contrastTau

        fun ctxWithNext(nextH: Float) = RowPenaltyContext(
            length = 3, isLastRow = false,
            plannedHeight = 200f, heightHint = null,
            rowIndex = 0, rowsLeftAfterThis = 1,
            hCount = 0, vCount = 3, currentMostlyV = true,
            planBoxes = listOf(
                RectF(x = 0f, y = 0f, w = 100f, h = 100f),
                RectF(x = 120f, y = 0f, w = 100f, h = 100f),
                RectF(x = 240f, y = 0f, w = 100f, h = 100f),
            ),
            collageWidth = collageWidth, verticalGap = 6f,
            nextRowFirstHeight = nextH, nextMostlyV = true, tailTotalHeight = 0f,
            contrastTau = tau,
            rowContrastAlpha = CollageTuning.default.augmentor.rowContrastAlpha,
            rowWidthBalanceAlpha = CollageTuning.default.augmentor.rowWidthBalanceAlpha,
            verticalSquashGuardFracOfWidth = CollageTuning.default.augmentor.verticalSquashGuardFracOfWidth,
            verticalSquashAlpha = CollageTuning.default.augmentor.verticalSquashAlpha,
            rowHeightSmoothAlpha = 0.0,
            heightBudgetAlpha = 0.0,
            maxHeightAllowed = Float.POSITIVE_INFINITY,
            areaUnit = (collageWidth * 56f).toDouble(),
            rowLenPrior = RowLengthBias(),
            nextHeightHint = null,
            totalItems = 4,
            itemsLeftAfterThis = 3,
        )

        val augmentor = DefaultRowPenaltyModel(
            widow = CollageTuning.WidowPolicyConfig(
                lastLen1 = 0.0,
                lastLen2 = 0.0,
                loneLen1Mid = 0.0,
                heightDevAlpha = 0.0
            ),
            penaltyPerExtraHorizontal = 0.0,
            penaltyTwoHorizontalsInOneRow = 0.0,
            penaltyThreeHorizontalsInOneRow = 0.0,
            topHeavinessAlpha = 0.0,
            lastRowTallAlpha = 0.0,
            firstRowShortAlpha = 0.0,
            preferThreeVerticalsBonus = 0.0,
            rowContrastAlpha = CollageTuning.default.augmentor.rowContrastAlpha,
            rowWidthBalanceAlpha = 0.0,
            verticalSquashGuardFracOfWidth = CollageTuning.default.augmentor.verticalSquashGuardFracOfWidth,
            verticalSquashAlpha = 0.0,
            rowHeightSmoothAlpha = 0.0,
            heightBudgetAlpha = 0.0,
            stickGamma4 = 0.0f,
            stickGamma3 = 0.0f,
            stickPenaltyAlpha = 0.0,
            fourMixPenalty = 0.0,
            equalizePerRowAlpha = 0.0,
            kpAlpha = 0.0,
            kpPower = 1.0,
            fitnessJumpAlpha = 0.0,
            fillAlpha = 0.0,
            tightBuckets = CollageTuning.default.augmentor.tightBuckets
        )

        val nextOverTau = 200f / (tau + 0.05f)
        val nextUnderTau = 200f / (tau - 0.05f)

        val penOver = augmentor.totalPenalty(ctxWithNext(nextOverTau))
        val penUnder = augmentor.totalPenalty(ctxWithNext(nextUnderTau))

        assertTrue(
            penOver > penUnder + 1e-6,
            "Контрастный штраф должен увеличивать total: over=$penOver vs under=$penUnder"
        )
    }

    @Test
    fun bonus_row_len_uniform_prefers_balanced_split() {
        val augmentor = DefaultRowPenaltyModel(
            bonusAlpha = 1.0,
            allowNegativeTotalPenalty = true,
            bonusRowLenUniformAlpha = 40_000.0,
            bonusRowLenTol = 1.0,
            rowContrastAlpha = 0.0,
            verticalSquashAlpha = 0.0,
            rowWidthBalanceAlpha = 0.0,
            stickPenaltyAlpha = 0.0,
            topHeavinessAlpha = 0.0,
            preferThreeVerticalsBonus = 0.0,
            rowHeightSmoothAlpha = 0.0,
            heightBudgetAlpha = 0.0,
            fillAlpha = 0.0,
        )

        fun penFor(length: Int) = augmentor.totalPenalty(
            RowPenaltyContext(
                length = length,
                isLastRow = false,
                plannedHeight = 100f,
                heightHint = null,
                rowIndex = 0,
                rowsLeftAfterThis = 1,
                hCount = 0,
                vCount = 0,
                currentMostlyV = false,
                planBoxes = emptyList(),
                collageWidth = 360f,
                verticalGap = 6f,
                nextRowFirstHeight = null,
                nextMostlyV = false,
                tailTotalHeight = 0f,
                contrastTau = CollageTuning.default.dynamicProgrammingConfig.contrastTau,
                rowContrastAlpha = 0.0,
                rowWidthBalanceAlpha = 0.0,
                verticalSquashGuardFracOfWidth =
                    CollageTuning.default.augmentor.verticalSquashGuardFracOfWidth,
                verticalSquashAlpha = 0.0,
                rowHeightSmoothAlpha = 0.0,
                heightBudgetAlpha = 0.0,
                maxHeightAllowed = Float.POSITIVE_INFINITY,
                areaUnit = (360f * 56f).toDouble(),
                rowLenPrior = RowLengthBias(),
                nextHeightHint = null,
                itemsLeftAfterThis = 6 - length,
                totalItems = 6,
            )
        )

        val penaltyForThreePlusThree = penFor(3)
        val penaltyForTwoPlusFour = penFor(2)

        assertTrue(
            penaltyForThreePlusThree < penaltyForTwoPlusFour,
            "3+3 should be cheaper than 2+4 when row-length uniform bonus is enabled; " +
                    "p3=$penaltyForThreePlusThree p2=$penaltyForTwoPlusFour"
        )
    }

    @Test
    fun bonus_equal_heights_prefers_similar_neighbor_rows() {
        val augmentor = DefaultRowPenaltyModel(
            bonusAlpha = 1.0,
            allowNegativeTotalPenalty = true,
            bonusEqualHeightsAlpha = 1_200.0,
            bonusEqualHeightsTolFrac = 0.10,
            rowHeightSmoothAlpha = 0.0,
        )

        fun pen(currentHeight: Float, nextHeight: Float) = augmentor.totalPenalty(
            RowPenaltyContext(
                length = 3,
                isLastRow = false,
                plannedHeight = currentHeight,
                heightHint = null,
                rowIndex = 0,
                rowsLeftAfterThis = 1,
                hCount = 0,
                vCount = 0,
                currentMostlyV = false,
                planBoxes = emptyList(),
                collageWidth = 360f,
                verticalGap = 6f,
                nextRowFirstHeight = nextHeight,
                nextMostlyV = false,
                tailTotalHeight = 0f,
                contrastTau = CollageTuning.default.dynamicProgrammingConfig.contrastTau,
                rowContrastAlpha = 0.0,
                rowWidthBalanceAlpha = 0.0,
                verticalSquashGuardFracOfWidth =
                    CollageTuning.default.augmentor.verticalSquashGuardFracOfWidth,
                verticalSquashAlpha = 0.0,
                rowHeightSmoothAlpha = 0.0,
                heightBudgetAlpha = 0.0,
                maxHeightAllowed = Float.POSITIVE_INFINITY,
                areaUnit = (360f * 56f).toDouble(),
                rowLenPrior = RowLengthBias(),
                nextHeightHint = null,
                itemsLeftAfterThis = 3,
                totalItems = 6,
            )
        )

        val equalPenalty = pen(120f, 120f)
        val differentPenalty = pen(120f, 150f)

        assertTrue(
            equalPenalty < differentPenalty,
            "Similar row heights should be cheaper: equal=$equalPenalty vs diff=$differentPenalty"
        )
    }
}
