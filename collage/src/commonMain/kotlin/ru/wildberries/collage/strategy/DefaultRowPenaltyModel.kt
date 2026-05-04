package ru.wildberries.collage.strategy

import ru.wildberries.collage.core.CollageTuning
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

private const val DISABLE_PENALTY_FOR_DEBUG = false

/**
 * Default visual penalty model for row-level layout decisions
 *
 * Penalties discourage widows, unreadable narrow tiles, abrupt height jumps,
 * excessive horizontal grouping, and layouts that violate height budgets
 */
internal class DefaultRowPenaltyModel(
    private val widow: CollageTuning.WidowPolicyConfig = CollageTuning.default.augmentor.widow,
    private val penaltyPerExtraHorizontal: Double = CollageTuning.default.augmentor.penaltyPerExtraHorizontal,
    private val penaltyTwoHorizontalsInOneRow: Double = CollageTuning.default.augmentor.penaltyTwoHorizontalsInOneRow,
    private val penaltyThreeHorizontalsInOneRow: Double = CollageTuning.default.augmentor.penaltyThreeHorizontalsInOneRow,
    private val topHeavinessAlpha: Double = CollageTuning.default.augmentor.topHeavinessAlpha,
    private val lastRowTallAlpha: Double = CollageTuning.default.augmentor.lastRowTallAlpha,
    private val firstRowShortAlpha: Double = CollageTuning.default.augmentor.firstRowShortAlpha,
    private val preferThreeVerticalsBonus: Double = CollageTuning.default.augmentor.preferThreeVerticalsBonus,
    private val rowContrastAlpha: Double = CollageTuning.default.augmentor.rowContrastAlpha,
    private val rowWidthBalanceAlpha: Double = CollageTuning.default.augmentor.rowWidthBalanceAlpha,
    private val verticalSquashGuardFracOfWidth: Float = CollageTuning.default.augmentor.verticalSquashGuardFracOfWidth,
    private val verticalSquashAlpha: Double = CollageTuning.default.augmentor.verticalSquashAlpha,
    private val horizontalSquashGuardFracOfWidth: Float = CollageTuning.default.augmentor.horizontalSquashGuardFracOfWidth,
    private val horizontalSquashAlpha: Double = CollageTuning.default.augmentor.horizontalSquashAlpha,
    private val rowHeightSmoothAlpha: Double = CollageTuning.default.augmentor.rowHeightSmoothAlpha,
    private val heightBudgetAlpha: Double = CollageTuning.default.augmentor.heightBudgetAlpha,
    private val stickGamma4: Float = CollageTuning.default.augmentor.stickGamma4,
    private val stickGamma3: Float = CollageTuning.default.augmentor.stickGamma3,
    private val stickPenaltyAlpha: Double = CollageTuning.default.augmentor.stickPenaltyAlpha,
    private val fourMixPenalty: Double = CollageTuning.default.augmentor.fourMixPenalty,
    private val equalizePerRowAlpha: Double = CollageTuning.default.augmentor.equalizePerRowAlpha,
    private val kpAlpha: Double = CollageTuning.default.augmentor.kpAlpha,
    private val kpPower: Double = CollageTuning.default.augmentor.kpPower,
    private val fitnessJumpAlpha: Double = CollageTuning.default.augmentor.fitnessJumpAlpha,
    private val fillAlpha: Double = CollageTuning.default.augmentor.fillAlpha,
    private val tightBuckets: FloatArray = CollageTuning.default.augmentor.tightBuckets,
    private val bonusAlpha: Double = CollageTuning.default.augmentor.bonusAlpha,
    private val allowNegativeTotalPenalty: Boolean = CollageTuning.default.augmentor.allowNegativeTotalPenalty,
    private val bonusRowLenUniformAlpha: Double = CollageTuning.default.augmentor.bonusRowLenUniformAlpha,
    private val bonusRowLenTol: Double = CollageTuning.default.augmentor.bonusRowLenTol,
    private val bonusEqualHeightsAlpha: Double = CollageTuning.default.augmentor.bonusEqualHeightsAlpha,
    private val bonusEqualHeightsTolFrac: Double = CollageTuning.default.augmentor.bonusEqualHeightsTolFrac,
) : RowPenaltyModel {

    private fun fitnessClass(rho: Float): Int = when {
        rho < tightBuckets[0] -> 0
        rho < tightBuckets[1] -> 1
        rho <= tightBuckets[2] -> 2
        rho <= tightBuckets[3] -> 3
        else -> 4
    }

    private fun sqClamped(x: Double, cap: Double = 50.0): Double {
        val y = x.coerceIn(-cap, cap)
        return y * y
    }

    private fun widowPenalty(penaltyContext: RowPenaltyContext): Double {
        val small = penaltyContext.totalItems <= 4
        if (penaltyContext.isLastRow) {
            if (penaltyContext.length == 1) {
                if (!small) return widow.lastLen1

                return when {
                    penaltyContext.hCount == 1 -> widow.lastLen2
                    penaltyContext.vCount == 1 -> widow.lastLen1
                    else -> widow.lastLen2 * 2
                }
            }
            if (penaltyContext.length == 2) return widow.lastLen2
            return 0.0
        }

        if (penaltyContext.length == 1) {
            if (!small) return widow.loneLen1Mid
            return when {
                penaltyContext.vCount == 1 -> widow.loneLen1Mid * 2
                penaltyContext.hCount == 1 -> widow.lastLen2
                else -> widow.lastLen2 * 2
            }
        }
        return 0.0
    }

    private fun kpBadness(penaltyContext: RowPenaltyContext): Double {
        val hHint = penaltyContext.heightHint
        if (hHint == null || hHint <= 1e-3f) return 0.0
        val rho = ((penaltyContext.plannedHeight - hHint) / hHint).toDouble()
        return kpAlpha * abs(rho).pow(kpPower)
    }

    private fun fitnessJumpPenalty(penaltyContext: RowPenaltyContext): Double {
        val nextH = penaltyContext.nextRowFirstHeight ?: return 0.0
        val nextHint = penaltyContext.nextHeightHint ?: return 0.0
        val hHint = penaltyContext.heightHint ?: return 0.0

        val rhoNow = ((penaltyContext.plannedHeight - hHint) / max(1e-3f, hHint))
        val rhoNext = ((nextH - nextHint) / max(1e-3f, nextHint))
        val jump = abs(fitnessClass(rhoNow) - fitnessClass(rhoNext))
        return if (jump > 1) fitnessJumpAlpha * (jump - 1) else 0.0
    }

    private fun orientationPenalty(penaltyContext: RowPenaltyContext): Double {
        val n = penaltyContext.length
        val h = penaltyContext.hCount
        val allH = (h == n)
        var res = 0.0
        if (allH) {
            res += when (n) {
                2 -> when (penaltyContext.totalItems) {
                    2 -> penaltyTwoHorizontalsInOneRow * 5
                    3 -> penaltyTwoHorizontalsInOneRow * 2
                    else -> 0.0
                }
                3 -> penaltyThreeHorizontalsInOneRow
                4 -> penaltyPerExtraHorizontal * 3
                else -> 0.0
            }
        } else if (h > 1) {
            res += (h - 1) * penaltyPerExtraHorizontal
        }
        return res
    }

    private fun topHeavinessPenalty(penaltyContext: RowPenaltyContext): Double =
        (
            -topHeavinessAlpha *
                penaltyContext.plannedHeight.toDouble() *
                (penaltyContext.rowsLeftAfterThis + 1)
            ).coerceAtLeast(-1e6)

    private fun lastRowTooTallPenalty(penaltyContext: RowPenaltyContext): Double {
        val hint = penaltyContext.heightHint ?: return 0.0
        if (!(penaltyContext.isLastRow && penaltyContext.plannedHeight > hint)) return 0.0
        val d = (penaltyContext.plannedHeight - hint).toDouble()
        return lastRowTallAlpha * sqClamped(d)
    }

    private fun firstRowTooShortPenalty(penaltyContext: RowPenaltyContext): Double {
        val hint = penaltyContext.heightHint ?: return 0.0
        if (!(penaltyContext.rowIndex == 0 && penaltyContext.plannedHeight < hint)) return 0.0
        val d = (hint - penaltyContext.plannedHeight).toDouble()
        return firstRowShortAlpha * sqClamped(d)
    }

    private fun preferenceBias(penaltyContext: RowPenaltyContext): Double {
        val allV = (penaltyContext.vCount == penaltyContext.length)
        return if (allV && penaltyContext.length == 3) -preferThreeVerticalsBonus else 0.0
    }

    private fun widthBalancePenalty(penaltyContext: RowPenaltyContext): Double {
        val boxes = penaltyContext.planBoxes
        if (boxes.size < 2) return 0.0

        var sumW = 0f
        for (b in boxes) sumW += b.w
        val meanW = sumW / boxes.size
        if (meanW <= 1e-3f) return 0.0

        var varianceNorm = 0.0
        for (b in boxes) {
            val d = ((b.w - meanW) / meanW).toDouble()
            varianceNorm += d * d
        }
        varianceNorm /= boxes.size
        return rowWidthBalanceAlpha * varianceNorm
    }

    private fun stickPenalty(penaltyContext: RowPenaltyContext): Double {
        val n = penaltyContext.planBoxes.size
        var res = 0.0
        if (n >= 3) {
            var sumW = 0f
            var minW = Float.POSITIVE_INFINITY
            for (b in penaltyContext.planBoxes) {
                sumW += b.w
                if (b.w < minW) minW = b.w
            }
            if (sumW > 1e-3f) {
                val meanW = sumW / n
                val gamma = if (n == 3) stickGamma3 else if (n == 4) stickGamma4 else 0f
                if (gamma > 0f) {
                    val ratio = (minW / meanW).toDouble()
                    if (ratio < gamma - 1e-4) {
                        val narrowCnt = penaltyContext.planBoxes.count { it.w < gamma * meanW }
                        res += narrowCnt * (stickPenaltyAlpha / 8.0)
                    }
                }
            }
            if (n == 4 && penaltyContext.hCount >= 2 && penaltyContext.vCount >= 2) res += fourMixPenalty
        }
        return res
    }

    private fun ultraNarrowPenalty(penaltyContext: RowPenaltyContext): Double {
        val boxes = penaltyContext.planBoxes
        val n = boxes.size
        if (n < 3) return 0.0

        var sumW = 0.0
        for (b in boxes) sumW += b.w.toDouble()
        val meanW = (sumW / n).coerceAtLeast(1e-3)
        val narrowFrac = when {
            n >= 5 -> 0.28
            n == 4 -> 0.26
            else -> 0.30
        }
        val thresh = narrowFrac * meanW

        var acc = 0.0
        for (b in boxes) {
            val w = b.w.toDouble()
            if (w < thresh) {
                val deficit = (thresh - w) / meanW
                acc += deficit * deficit
            }
        }

        val ultraAlpha = stickPenaltyAlpha / 12.0
        return ultraAlpha * acc
    }

    private fun verticalSquashPenalty(penaltyContext: RowPenaltyContext): Double {
        if (penaltyContext.vCount * 2 < penaltyContext.length) return 0.0
        val guardHeight = verticalSquashGuardFracOfWidth * penaltyContext.collageWidth
        if (penaltyContext.plannedHeight >= guardHeight) return 0.0
        val vD = ((guardHeight - penaltyContext.plannedHeight) / guardHeight).toDouble()
        return verticalSquashAlpha * vD * vD
    }

    private fun horizontalSquashPenalty(penaltyContext: RowPenaltyContext): Double {
        if (penaltyContext.hCount * 2 < penaltyContext.length) return 0.0

        val guardHeight = horizontalSquashGuardFracOfWidth * penaltyContext.collageWidth
        if (penaltyContext.plannedHeight >= guardHeight) return 0.0

        val d = ((guardHeight - penaltyContext.plannedHeight) / guardHeight).toDouble()
        return horizontalSquashAlpha * d * d
    }

    private fun contrastPenalty(penaltyContext: RowPenaltyContext): Double {
        val nextH = penaltyContext.nextRowFirstHeight ?: return 0.0
        if (!(penaltyContext.currentMostlyV && penaltyContext.nextMostlyV)) return 0.0
        val ratio = (max(penaltyContext.plannedHeight, nextH) / max(1e-3f, min(penaltyContext.plannedHeight, nextH))).toDouble()
        if (ratio <= penaltyContext.contrastTau) return 0.0
        val cD = ratio - penaltyContext.contrastTau
        return rowContrastAlpha * cD * cD
    }

    private fun smoothPenalty(penaltyContext: RowPenaltyContext): Double {
        val nextH = penaltyContext.nextRowFirstHeight ?: return 0.0
        val d = (penaltyContext.plannedHeight - nextH).toDouble()
        return rowHeightSmoothAlpha * d * d
    }

    private fun heightBudgetPenalty(penaltyContext: RowPenaltyContext): Double {
        if (!penaltyContext.maxHeightAllowed.isFinite()) return 0.0

        val verticalGapBeforeTail = if (penaltyContext.nextRowFirstHeight != null) {
            penaltyContext.verticalGap
        } else {
            0f
        }

        val totalHeightIfTakeThis = penaltyContext.plannedHeight +
            verticalGapBeforeTail +
            penaltyContext.tailTotalHeight

        val overflow = (totalHeightIfTakeThis - penaltyContext.maxHeightAllowed).toDouble()

        return if (overflow > 0.0) {
            heightBudgetAlpha * overflow * overflow
        } else {
            0.0
        }
    }

    private fun fillPenalty(penaltyContext: RowPenaltyContext): Double {
        if (!penaltyContext.maxHeightAllowed.isFinite() || !penaltyContext.isLastRow) return 0.0
        val totalHeightIfTakeThis = penaltyContext.plannedHeight + penaltyContext.tailTotalHeight
        val under = (penaltyContext.maxHeightAllowed - totalHeightIfTakeThis).toDouble().coerceAtLeast(0.0)
        return fillAlpha * under * under
    }

    private fun equalizePenalty(penaltyContext: RowPenaltyContext): Double {
        if (!penaltyContext.maxHeightAllowed.isFinite()) return 0.0
        val rowsRemaining = penaltyContext.rowsLeftAfterThis + 1
        val gapsLeft = if (penaltyContext.rowsLeftAfterThis > 0) penaltyContext.verticalGap * penaltyContext.rowsLeftAfterThis else 0f
        val targetSum = (penaltyContext.maxHeightAllowed - penaltyContext.tailTotalHeight - gapsLeft).toDouble()
        if (rowsRemaining <= 0 || !targetSum.isFinite()) return 0.0
        val targetPerRow = targetSum / rowsRemaining
        val d = penaltyContext.plannedHeight.toDouble() - targetPerRow
        return equalizePerRowAlpha * d * d
    }

    private fun rowLenPriorPenalty(penaltyContext: RowPenaltyContext): Double {
        if (!penaltyContext.rowLenPrior.enabled) return 0.0
        val w = when (penaltyContext.length) {
            1 -> penaltyContext.rowLenPrior.w1
            2 -> penaltyContext.rowLenPrior.w2
            3 -> penaltyContext.rowLenPrior.w3
            4 -> penaltyContext.rowLenPrior.w4
            else -> 0.0
        }
        return w * penaltyContext.areaUnit
    }

    private fun bonusRowLenUniform(penaltyContext: RowPenaltyContext): Double {
        if (bonusRowLenUniformAlpha <= 0.0) return 0.0
        val itemsNow = (penaltyContext.itemsLeftAfterThis + penaltyContext.length).toDouble()
        val rowsNow = (penaltyContext.rowsLeftAfterThis + 1).coerceAtLeast(1)
        val avgNow = itemsNow / rowsNow
        val dist = min(abs(penaltyContext.length - kotlin.math.floor(avgNow)), abs(penaltyContext.length - kotlin.math.ceil(avgNow)))
        val score = (1.0 - (dist / bonusRowLenTol)).coerceIn(0.0, 1.0)
        return bonusRowLenUniformAlpha * (score * score)
    }

    private fun bonusEqualHeights(penaltyContext: RowPenaltyContext): Double {
        if (bonusEqualHeightsAlpha <= 0.0) return 0.0
        val nextH = penaltyContext.nextRowFirstHeight ?: return 0.0
        if (nextH <= 1e-3f || penaltyContext.plannedHeight <= 1e-3f) return 0.0
        val denom = max(penaltyContext.plannedHeight, nextH).toDouble().coerceAtLeast(1e-3)
        val relDiff = abs(penaltyContext.plannedHeight - nextH).toDouble() / denom
        val tol = bonusEqualHeightsTolFrac.coerceAtLeast(1e-6)
        val score = (1.0 - (relDiff / tol)).coerceIn(0.0, 1.0)
        return bonusEqualHeightsAlpha * (score * score)
    }

    override fun totalPenalty(penaltyContext: RowPenaltyContext): Double {
        if (DISABLE_PENALTY_FOR_DEBUG) return 0.0

        val penaltySum =
            widowPenalty(penaltyContext) +
                kpBadness(penaltyContext) +
                fitnessJumpPenalty(penaltyContext) +
                orientationPenalty(penaltyContext) +
                topHeavinessPenalty(penaltyContext) +
                lastRowTooTallPenalty(penaltyContext) +
                firstRowTooShortPenalty(penaltyContext) +
                preferenceBias(penaltyContext) +
                widthBalancePenalty(penaltyContext) +
                stickPenalty(penaltyContext) +
                ultraNarrowPenalty(penaltyContext) +
                verticalSquashPenalty(penaltyContext) +
                horizontalSquashPenalty(penaltyContext) +
                contrastPenalty(penaltyContext) +
                smoothPenalty(penaltyContext) +
                heightBudgetPenalty(penaltyContext) +
                fillPenalty(penaltyContext) +
                equalizePenalty(penaltyContext) +
                rowLenPriorPenalty(penaltyContext)

        val bonusSum = bonusRowLenUniform(penaltyContext) + bonusEqualHeights(penaltyContext)

        var total = penaltySum - bonusAlpha * bonusSum
        if (!total.isFinite()) total = 1e12
        if (!allowNegativeTotalPenalty && total < 0.0) total = 0.0
        return total
    }
}
