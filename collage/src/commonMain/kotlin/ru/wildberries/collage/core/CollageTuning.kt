package ru.wildberries.collage.core

import ru.wildberries.collage.core.MathUtil.PowerLookupTable

/**
 * Internal default thresholds and weights used by the layout engine
 *
 * These values are empirical and are not part of the public API
 */
internal object CollageTuning {

    data class HeuristicsConfig(
        val narrowContainerWidthPx: Float = 280f,
        val ultraAspect: Float = 3.2f,
        val cropFailAt: Float = 0.95f,
        val ultraWideSoloAspect: Float = 3.0f,
        val planningAspectLimit: Float = 1.8f,
        val freeCropAspectLimit: Float = 1.5f,
        val smallNStrongHorizontalTau: Float = 1.25f,
        val smallNSingleRowMinHeightFactor2: Float = 0.36f,
        val smallNSingleRowMinHeightFactor3: Float = 0.32f,
        val smallNSingleRowMinHeightFactor4: Float = 0.42f,
    ) {

        /** Relative soft width floor used to reject visually unreadable narrow tiles. */
        fun softRelMinFrac(n: Int): Float = when {
            n >= 5 -> 0.28f
            n == 4 -> 0.26f
            n == 3 -> 0.30f
            n == 2 -> 0.33f
            else -> 0f
        }

        /** Absolute hard floor used in post adjustments */
        fun hardAbsMinFrac(n: Int): Float = when {
            n == 2 -> 0.33f
            n == 3 -> 0.26f
            n == 4 -> 0.22f
            n >= 5 -> 0.18f
            else -> 0f
        }

        /** Ultra narrow guard fractions */
        fun ultraNarrowFrac(n: Int): Float = when {
            n >= 5 -> 0.22f
            n == 4 -> 0.20f
            n == 3 -> 0.24f
            else -> 0f
        }

        /** Snap beta per row length */
        fun snapBeta(n: Int): Float = when {
            n >= 5 -> 0.30f
            n == 4 -> 0.30f
            n == 3 -> 0.34f
            else -> 0f
        }

        fun shouldForceContainInNarrowContainerForRowScoring(
            layoutWidthPx: Float?,
            imageAspect: Float,
            cropRatio: Float,
            isSmallTile: Boolean,
        ): Boolean {
            if (layoutWidthPx == null || !layoutWidthPx.isFinite()) return false
            if (layoutWidthPx > narrowContainerWidthPx + 1e-3f) return false
            val isUltra = imageAspect >= ultraAspect || imageAspect <= 1f / ultraAspect
            val isSevereCrop = cropRatio >= cropFailAt
            return isUltra && isSevereCrop && isSmallTile
        }

        fun shouldForceContainInNarrowContainerForMaterialization(
            layoutWidthPx: Float?,
            imageAspect: Float,
            cropRatio: Float,
        ): Boolean {
            if (layoutWidthPx == null || !layoutWidthPx.isFinite()) return false
            if (layoutWidthPx > narrowContainerWidthPx + 1e-3f) return false
            val isUltra = imageAspect >= ultraAspect || imageAspect <= 1f / ultraAspect
            val isSevereCrop = cropRatio >= cropFailAt
            return isUltra && isSevereCrop
        }

        fun snapMinAbsFrac(n: Int): Float = when {
            n <= 2 -> 0f
            n == 3 -> 0.22f
            n == 4 -> 0.20f
            else -> 0.10f
        }

        fun snapMaxAbsFrac(n: Int): Float = when {
            n <= 2 -> 1f
            n == 3 -> 0.50f
            n == 4 -> 0.40f
            else -> 0.35f
        }
    }

    data class PlannerConfig(
        val alphaV: Float = 0.40f,
        val alphaS: Float = 0.18f,
        val alphaH: Float = 0.12f,

        val perAspectFloorV: Float = 0.60f,
        val perAspectFloorS: Float = 0.30f,
        val perAspectFloorH: Float = 0.18f,

        val gamma3: Float = 0.40f,
        val gamma4: Float = 0.34f,
        val gammaElse: Float = 0.30f,
    ) {

        fun perAspectFloor(a: Float, tauH: Float = 1.05f): Float {
            val tauV = 1f / tauH
            return when {
                a <= tauV -> perAspectFloorV
                a < tauH -> perAspectFloorS
                else -> perAspectFloorH
            }
        }

        fun matchstickGamma(n: Int): Float = when (n) {
            3 -> gamma3
            4 -> gamma4
            else -> gammaElse
        }
    }

    data class DynamicProgrammingConfig(
        val tauHorizontal: Float = 1.05f,
        val contrastTau: Float = 1.35f,
        val rowContrastAlpha: Double = 120_000.0,
        val rowWidthBalanceAlpha: Double = 120_000.0,
        val verticalSquashGuardFracOfWidth: Float = 0.10f,
        val verticalSquashAlpha: Double = 100_000.0,
        val rowHeightSmoothAlpha: Double = 500.0,
        val heightBudgetAlpha: Double = 240_000.0,
        val kLoss: Double = 80.0,
        val kPen: Double = 1.0,
        val anyRowGuardFrac: Float = 0.10f,
        val heightQuantStep: Float = 1f,
    )

    data class WidowPolicyConfig(
        val lastLen1: Double = 500_000.0,
        val lastLen2: Double = 20_000.0,
        val loneLen1Mid: Double = 50_000.0,
        val heightDevAlpha: Double = 0.0,
    )

    private data class AugmentorScalars(
        val widow: WidowPolicyConfig,
        val penaltyPerExtraHorizontal: Double,
        val penaltyTwoHorizontalsInOneRow: Double,
        val penaltyThreeHorizontalsInOneRow: Double,
        val topHeavinessAlpha: Double,
        val lastRowTallAlpha: Double,
        val firstRowShortAlpha: Double,
        val preferThreeVerticalsBonus: Double,
        val antiSplitThreePenalty: Double,
        val rowContrastAlpha: Double,
        val rowWidthBalanceAlpha: Double,
        val verticalSquashGuardFracOfWidth: Float,
        val verticalSquashAlpha: Double,
        val rowHeightSmoothAlpha: Double,
        val heightBudgetAlpha: Double,
        val stickGamma4: Float,
        val stickGamma3: Float,
        val stickPenaltyAlpha: Double,
        val fourMixPenalty: Double,
        val equalizePerRowAlpha: Double,
        val kpAlpha: Double,
        val kpPower: Double,
        val fitnessJumpAlpha: Double,
        val fillAlpha: Double,
        val bonusAlpha: Double,
        val allowNegativeTotalPenalty: Boolean,
        val bonusRowLenUniformAlpha: Double,
        val bonusRowLenTol: Double,
        val bonusEqualHeightsAlpha: Double,
        val bonusEqualHeightsTolFrac: Double,
    )

    data class AugmentorConfig(
        val widow: WidowPolicyConfig = WidowPolicyConfig(),
        val penaltyPerExtraHorizontal: Double = 40_000.0,
        val penaltyTwoHorizontalsInOneRow: Double = 90_000.0,
        val penaltyThreeHorizontalsInOneRow: Double = 60_000.0,
        val topHeavinessAlpha: Double = 1_200.0,
        val lastRowTallAlpha: Double = 2_600.0,
        val firstRowShortAlpha: Double = 1_300.0,
        val preferThreeVerticalsBonus: Double = 80_000.0,
        val antiSplitThreePenalty: Double = 120_000.0,
        val rowContrastAlpha: Double = 120_000.0,
        val rowWidthBalanceAlpha: Double = 120_000.0,
        val verticalSquashGuardFracOfWidth: Float = 0.12f,
        val verticalSquashAlpha: Double = 100_000.0,
        val horizontalSquashGuardFracOfWidth: Float = 0.18f,
        val horizontalSquashAlpha: Double = 120_000.0,
        val rowHeightSmoothAlpha: Double = 500.0,
        val heightBudgetAlpha: Double = 240_000.0,
        val stickGamma4: Float = 0.30f,
        val stickGamma3: Float = 0.26f,
        val stickPenaltyAlpha: Double = 400_000.0,
        val fourMixPenalty: Double = 0.0,
        val equalizePerRowAlpha: Double = 0.0,
        val kpAlpha: Double = 1_800.0,
        val kpPower: Double = 3.0,
        val fitnessJumpAlpha: Double = 15_000.0,
        val fillAlpha: Double = 1_200.0,
        val tightBuckets: FloatArray = floatArrayOf(-0.5f, -0.1f, 0.1f, 0.5f),
        val bonusAlpha: Double = 0.0,
        val allowNegativeTotalPenalty: Boolean = false,
        val bonusRowLenUniformAlpha: Double = 0.0,
        val bonusRowLenTol: Double = 1.0,
        val bonusEqualHeightsAlpha: Double = 0.0,
        val bonusEqualHeightsTolFrac: Double = 0.10,
    ) {

        private fun toScalars() = AugmentorScalars(
            widow = widow,
            penaltyPerExtraHorizontal = penaltyPerExtraHorizontal,
            penaltyTwoHorizontalsInOneRow = penaltyTwoHorizontalsInOneRow,
            penaltyThreeHorizontalsInOneRow = penaltyThreeHorizontalsInOneRow,
            topHeavinessAlpha = topHeavinessAlpha,
            lastRowTallAlpha = lastRowTallAlpha,
            firstRowShortAlpha = firstRowShortAlpha,
            preferThreeVerticalsBonus = preferThreeVerticalsBonus,
            antiSplitThreePenalty = antiSplitThreePenalty,
            rowContrastAlpha = rowContrastAlpha,
            rowWidthBalanceAlpha = rowWidthBalanceAlpha,
            verticalSquashGuardFracOfWidth = verticalSquashGuardFracOfWidth,
            verticalSquashAlpha = verticalSquashAlpha,
            rowHeightSmoothAlpha = rowHeightSmoothAlpha,
            heightBudgetAlpha = heightBudgetAlpha,
            stickGamma4 = stickGamma4,
            stickGamma3 = stickGamma3,
            stickPenaltyAlpha = stickPenaltyAlpha,
            fourMixPenalty = fourMixPenalty,
            equalizePerRowAlpha = equalizePerRowAlpha,
            kpAlpha = kpAlpha,
            kpPower = kpPower,
            fitnessJumpAlpha = fitnessJumpAlpha,
            fillAlpha = fillAlpha,
            bonusAlpha = bonusAlpha,
            allowNegativeTotalPenalty = allowNegativeTotalPenalty,
            bonusRowLenUniformAlpha = bonusRowLenUniformAlpha,
            bonusRowLenTol = bonusRowLenTol,
            bonusEqualHeightsAlpha = bonusEqualHeightsAlpha,
            bonusEqualHeightsTolFrac = bonusEqualHeightsTolFrac,
        )

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is AugmentorConfig) return false
            return toScalars() == other.toScalars() &&
                tightBuckets.contentEquals(other.tightBuckets)
        }

        override fun hashCode(): Int {
            var result = toScalars().hashCode()
            result = 31 * result + tightBuckets.contentHashCode()
            return result
        }
    }

    data class Resources(val powerLookupTable: PowerLookupTable = PowerLookupTable())

    data class Snapshot(
        val heuristics: HeuristicsConfig = HeuristicsConfig(),
        val planner: PlannerConfig = PlannerConfig(),
        val dynamicProgrammingConfig: DynamicProgrammingConfig = DynamicProgrammingConfig(),
        val augmentor: AugmentorConfig = AugmentorConfig(),
        val resources: Resources = Resources(),
    )

    /** Current tuning used by newly created engine instances */
    val default: Snapshot = Snapshot()
}
