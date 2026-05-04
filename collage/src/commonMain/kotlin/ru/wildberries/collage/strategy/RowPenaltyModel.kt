package ru.wildberries.collage.strategy

import ru.wildberries.collage.model.RectF

internal data class RowLengthBias(
    val enabled: Boolean = false,
    val w1: Double = +0.03,
    val w2: Double = -0.002,
    val w3: Double = -0.015,
    val w4: Double = +0.006,
)

internal data class RowPenaltyContext(
    val totalItems: Int,
    val length: Int,
    val isLastRow: Boolean,
    val plannedHeight: Float,
    val heightHint: Float?,
    val rowIndex: Int,
    val rowsLeftAfterThis: Int,
    val hCount: Int,
    val vCount: Int,
    val currentMostlyV: Boolean,
    val planBoxes: List<RectF>,
    val collageWidth: Float,
    val verticalGap: Float,
    val nextRowFirstHeight: Float?,
    val nextMostlyV: Boolean,
    val tailTotalHeight: Float,
    val contrastTau: Float,
    val rowContrastAlpha: Double,
    val rowWidthBalanceAlpha: Double,
    val verticalSquashGuardFracOfWidth: Float,
    val verticalSquashAlpha: Double,
    val rowHeightSmoothAlpha: Double,
    val heightBudgetAlpha: Double,
    val maxHeightAllowed: Float,
    val areaUnit: Double,
    val rowLenPrior: RowLengthBias,
    val nextHeightHint: Float? = null,
    val itemsLeftAfterThis: Int = 0,
)

internal interface RowPenaltyModel {

    fun totalPenalty(penaltyContext: RowPenaltyContext): Double
}
