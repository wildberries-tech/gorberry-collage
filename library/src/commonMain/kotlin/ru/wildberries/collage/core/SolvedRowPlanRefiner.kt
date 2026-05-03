package ru.wildberries.collage.core

import ru.wildberries.collage.model.Photo
import ru.wildberries.collage.model.RectF
import kotlin.math.abs
import kotlin.math.max

internal data class SolvedRowPlanRefinementInput(
    val photos: List<Photo>,
    val collageWidth: Float,
    val horizontalGap: Float,
    val verticalGap: Float,
    val ranges: List<IntRange>,
    val initialHeights: List<Float>,
    val initialBoxes: List<List<RectF>>,
    val minimumHeightAllowed: Float,
    val maximumHeightAllowed: Float,
)

internal fun interface FixedRangePlanBuilder {
    fun build(
        photos: List<Photo>,
        range: IntRange,
        collageWidth: Float,
        horizontalGap: Float,
        verticalGap: Float,
        heightHint: Float,
    ): SolvedRowPlan?
}

internal fun interface RowPlanLossCalculator {
    fun calculate(
        photos: List<Photo>,
        range: IntRange,
        collageWidth: Float,
        boxes: List<RectF>,
    ): Double
}

internal class SolvedRowPlanRefiner(
    private val config: EngineConfig,
    private val tuning: CollageTuning.Snapshot,
    private val fixedRangePlanBuilder: FixedRangePlanBuilder,
    private val rowPlanLossCalculator: RowPlanLossCalculator,
) {

    fun buildFinalPlans(input: SolvedRowPlanRefinementInput): List<SolvedRowPlan> {
        val originalPlans = buildOriginalPlans(input)
        val refinedPlans = refinePlansWithRhythm(input)
        val repairedPlans = expandPlansToMinimumHeight(
            input = input,
            plans = refinedPlans.toMutableList(),
        )

        return choosePlansByHeightLimits(
            input = input,
            repairedPlans = repairedPlans,
            originalPlans = originalPlans,
        )
    }

    private fun choosePlansByHeightLimits(
        input: SolvedRowPlanRefinementInput,
        repairedPlans: List<SolvedRowPlan>,
        originalPlans: List<SolvedRowPlan>,
    ): List<SolvedRowPlan> {
        return when {
            fitHeightLimits(input, repairedPlans) -> repairedPlans
            fitHeightLimits(input, originalPlans) -> originalPlans
            else -> repairedPlans
        }
    }

    private fun refinePlansWithRhythm(input: SolvedRowPlanRefinementInput): List<SolvedRowPlan> {
        if (input.ranges.isEmpty()) return emptyList()

        val plans = buildInitialPlans(input)
        val averageTargetHeight = computeAverageTargetHeight(input)
        val areaUnit = (input.collageWidth * max(config.minItemHeight, 1f)).toDouble()

        repeat(RHYTHM_REFINEMENT_PASSES) {
            refineOnePass(
                input = input,
                plans = plans,
                averageTargetHeight = averageTargetHeight,
                areaUnit = areaUnit,
            )
        }

        return plans
    }

    private fun refineOnePass(
        input: SolvedRowPlanRefinementInput,
        plans: MutableList<SolvedRowPlan>,
        averageTargetHeight: Float,
        areaUnit: Double,
    ) {
        var rowIndex = 0

        while (rowIndex < plans.size) {
            val selectedPlan = selectRhythmPlanForRow(
                input = input,
                plans = plans,
                rowIndex = rowIndex,
                averageTargetHeight = averageTargetHeight,
                areaUnit = areaUnit,
            )

            if (selectedPlan != null) {
                plans[rowIndex] = selectedPlan
            }

            rowIndex++
        }
    }

    private fun selectRhythmPlanForRow(
        input: SolvedRowPlanRefinementInput,
        plans: List<SolvedRowPlan>,
        rowIndex: Int,
        averageTargetHeight: Float,
        areaUnit: Double,
    ): SolvedRowPlan? {
        val range = input.ranges[rowIndex]
        val previousHeight = plans.getOrNull(rowIndex - 1)?.height
        val nextHeight = plans.getOrNull(rowIndex + 1)?.height
        val targetHeight = computeRowTargetHeight(
            photos = input.photos,
            range = range,
            collageWidth = input.collageWidth,
            averageTargetHeight = averageTargetHeight,
        )

        val heightHints = collectRhythmHeightHints(
            current = plans[rowIndex].height,
            previous = previousHeight,
            next = nextHeight,
            target = targetHeight,
            averageTarget = averageTargetHeight,
        )

        val candidates = buildCandidatePlans(
            input = input,
            range = range,
            heightHints = heightHints,
        )

        if (candidates.isEmpty()) return null

        val minimumLoss = candidates.minOf { it.loss / areaUnit }
        val lossTolerance = max(MINIMUM_LOSS_TOLERANCE, minimumLoss * LOSS_TOLERANCE_FRACTION)
        val otherHeights = sumOtherHeights(plans, rowIndex)
        val totalGaps = input.verticalGap * max(0, input.ranges.size - 1)

        val pool = candidates.filter { it.loss / areaUnit <= minimumLoss + lossTolerance }
            .ifEmpty { candidates }

        return pool.minWith(
            compareBy<SolvedRowPlan> {
                rhythmScore(
                    input = input,
                    plan = it,
                    previousHeight = previousHeight,
                    nextHeight = nextHeight,
                    targetHeight = targetHeight,
                    averageTargetHeight = averageTargetHeight,
                    otherHeights = otherHeights,
                    totalGaps = totalGaps,
                )
            }.thenBy { it.loss / areaUnit }
        )
    }

    private fun buildCandidatePlans(
        input: SolvedRowPlanRefinementInput,
        range: IntRange,
        heightHints: List<Float>,
    ): List<SolvedRowPlan> {
        val candidates = ArrayList<SolvedRowPlan>(heightHints.size)

        for (heightHint in heightHints) {
            val plan = fixedRangePlanBuilder.build(
                photos = input.photos,
                range = range,
                collageWidth = input.collageWidth,
                horizontalGap = input.horizontalGap,
                verticalGap = input.verticalGap,
                heightHint = heightHint,
            )

            if (plan != null) {
                candidates += plan
            }
        }

        return candidates
    }

    private fun rhythmScore(
        input: SolvedRowPlanRefinementInput,
        plan: SolvedRowPlan,
        previousHeight: Float?,
        nextHeight: Float?,
        targetHeight: Float,
        averageTargetHeight: Float,
        otherHeights: Float,
        totalGaps: Float,
    ): Double {
        var score = 0.0

        if (previousHeight != null) {
            score += 1.35 * relativeHeightPenalty(plan.height, previousHeight)
        }

        if (nextHeight != null) {
            score += 1.35 * relativeHeightPenalty(plan.height, nextHeight)
        }

        score += 0.80 * relativeHeightPenalty(plan.height, targetHeight)
        score += 0.35 * relativeHeightPenalty(plan.height, averageTargetHeight)

        val totalHeight = otherHeights + plan.height + totalGaps
        score += maximumHeightPenalty(input, totalHeight)
        score += minimumHeightPenalty(input, totalHeight)

        return score
    }

    private fun maximumHeightPenalty(
        input: SolvedRowPlanRefinementInput,
        totalHeight: Float,
    ): Double {
        if (!input.maximumHeightAllowed.isFinite()) return 0.0
        if (totalHeight <= input.maximumHeightAllowed + HEIGHT_EPSILON) return 0.0

        val over = (totalHeight - input.maximumHeightAllowed) / max(input.maximumHeightAllowed, 1f)
        return 6.0 * over * over
    }

    private fun minimumHeightPenalty(
        input: SolvedRowPlanRefinementInput,
        totalHeight: Float,
    ): Double {
        if (!input.minimumHeightAllowed.isFinite()) return 0.0
        if (totalHeight >= input.minimumHeightAllowed - HEIGHT_EPSILON) return 0.0

        val under = (input.minimumHeightAllowed - totalHeight) / max(input.minimumHeightAllowed, 1f)
        return 1.5 * under * under
    }

    private fun buildInitialPlans(input: SolvedRowPlanRefinementInput): MutableList<SolvedRowPlan> {
        return MutableList(input.ranges.size) { rowIndex ->
            val range = input.ranges[rowIndex]

            fixedRangePlanBuilder.build(
                photos = input.photos,
                range = range,
                collageWidth = input.collageWidth,
                horizontalGap = input.horizontalGap,
                verticalGap = input.verticalGap,
                heightHint = input.initialHeights[rowIndex],
            ) ?: SolvedRowPlan(
                height = input.initialHeights[rowIndex],
                boxes = input.initialBoxes[rowIndex],
                loss = rowPlanLossCalculator.calculate(
                    photos = input.photos,
                    range = range,
                    collageWidth = input.collageWidth,
                    boxes = input.initialBoxes[rowIndex],
                ),
            )
        }
    }

    private fun buildOriginalPlans(input: SolvedRowPlanRefinementInput): List<SolvedRowPlan> {
        return input.ranges.indices.map { rowIndex ->
            SolvedRowPlan(
                height = input.initialHeights[rowIndex],
                boxes = input.initialBoxes[rowIndex],
                loss = rowPlanLossCalculator.calculate(
                    photos = input.photos,
                    range = input.ranges[rowIndex],
                    collageWidth = input.collageWidth,
                    boxes = input.initialBoxes[rowIndex],
                ),
            )
        }
    }

    private fun expandPlansToMinimumHeight(
        input: SolvedRowPlanRefinementInput,
        plans: MutableList<SolvedRowPlan>,
    ): List<SolvedRowPlan> {
        if (input.minimumHeightAllowed <= 0f || input.ranges.isEmpty()) return plans
        if (totalHeight(plans, input.verticalGap) >= input.minimumHeightAllowed - HEIGHT_EPSILON) return plans

        var passIndex = 0
        while (
            totalHeight(plans, input.verticalGap) < input.minimumHeightAllowed - HEIGHT_EPSILON &&
            passIndex < MINIMUM_HEIGHT_EXPAND_PASSES
        ) {
            val changed = expandOnePass(
                input = input,
                plans = plans,
            )

            if (!changed) break
            passIndex++
        }

        return plans
    }

    private fun expandOnePass(
        input: SolvedRowPlanRefinementInput,
        plans: MutableList<SolvedRowPlan>,
    ): Boolean {
        var changed = false
        var rowIndex = 0

        while (rowIndex < input.ranges.size) {
            val expanded = buildExpandedPlanForRow(
                input = input,
                plans = plans,
                rowIndex = rowIndex,
            )

            if (expanded != null) {
                plans[rowIndex] = expanded
                changed = true
            }

            rowIndex++
        }

        return changed
    }

    private fun buildExpandedPlanForRow(
        input: SolvedRowPlanRefinementInput,
        plans: List<SolvedRowPlan>,
        rowIndex: Int,
    ): SolvedRowPlan? {
        val currentTotalHeight = totalHeight(plans, input.verticalGap)
        val heightDeficit = input.minimumHeightAllowed - currentTotalHeight

        if (heightDeficit <= HEIGHT_EPSILON) return null

        val remainingRowCount = (input.ranges.size - rowIndex).coerceAtLeast(1)
        val currentRowHeight = plans[rowIndex].height
        val requestedRowHeight = currentRowHeight + heightDeficit / remainingRowCount

        val expandedPlan = fixedRangePlanBuilder.build(
            photos = input.photos,
            range = input.ranges[rowIndex],
            collageWidth = input.collageWidth,
            horizontalGap = input.horizontalGap,
            verticalGap = input.verticalGap,
            heightHint = requestedRowHeight,
        ) ?: return null

        if (expandedPlan.height <= currentRowHeight + 0.5f) return null

        val candidateTotalHeight = currentTotalHeight - currentRowHeight + expandedPlan.height
        val satisfiesMaximum =
            !input.maximumHeightAllowed.isFinite() ||
                candidateTotalHeight <= input.maximumHeightAllowed + HEIGHT_EPSILON

        return if (satisfiesMaximum) expandedPlan else null
    }

    private fun computeAverageTargetHeight(input: SolvedRowPlanRefinementInput): Float {
        val rowCount = input.ranges.size
        val totalGaps = input.verticalGap * max(0, rowCount - 1)
        val initialContentHeight = input.initialHeights.sum()

        return when {
            input.maximumHeightAllowed.isFinite() &&
                initialContentHeight + totalGaps > input.maximumHeightAllowed -> {
                ((input.maximumHeightAllowed - totalGaps) / rowCount).coerceAtLeast(config.minItemHeight)
            }

            input.minimumHeightAllowed.isFinite() &&
                initialContentHeight + totalGaps < input.minimumHeightAllowed -> {
                ((input.minimumHeightAllowed - totalGaps) / rowCount).coerceAtLeast(config.minItemHeight)
            }

            else -> {
                (initialContentHeight / rowCount).coerceAtLeast(config.minItemHeight)
            }
        }
    }

    private fun computeRowTargetHeight(
        photos: List<Photo>,
        range: IntRange,
        collageWidth: Float,
        averageTargetHeight: Float,
    ): Float {
        val counts = countHorizontalAndVerticalPhotos(
            photos = photos,
            range = range,
        )
        val localTarget = rowRhythmTargetHeight(
            collageWidth = collageWidth,
            length = range.last - range.first + 1,
            horizontalCount = counts.first,
            verticalCount = counts.second,
        )

        return 0.5f * (averageTargetHeight + localTarget)
    }

    private fun collectRhythmHeightHints(
        current: Float,
        previous: Float?,
        next: Float?,
        target: Float,
        averageTarget: Float,
    ): List<Float> {
        val hints = ArrayList<Float>(10)

        fun add(value: Float?) {
            if (value == null || !value.isFinite()) return

            val height = max(config.minItemHeight, value)
            if (hints.none { abs(it - height) < 0.5f }) {
                hints += height
            }
        }

        add(current)
        add(target)
        add(averageTarget)
        add(previous)
        add(next)

        if (previous != null && next != null) add(0.5f * (previous + next))
        if (previous != null) add(0.5f * (current + previous))
        if (next != null) add(0.5f * (current + next))

        add(0.5f * (current + target))
        add(0.5f * (target + averageTarget))

        hints.sort()
        return hints
    }

    private fun countHorizontalAndVerticalPhotos(
        photos: List<Photo>,
        range: IntRange,
    ): Pair<Int, Int> {
        val horizontalThreshold = tuning.dynamicProgrammingConfig.tauHorizontal
        val verticalThreshold = 1f / horizontalThreshold

        var horizontalCount = 0
        var verticalCount = 0
        var photoIndex = range.first

        while (photoIndex <= range.last) {
            val aspectRatio = MathUtil.aspect(
                photos[photoIndex].width,
                photos[photoIndex].height,
            )

            if (aspectRatio >= horizontalThreshold) horizontalCount++
            if (aspectRatio <= verticalThreshold) verticalCount++

            photoIndex++
        }

        return horizontalCount to verticalCount
    }

    private fun rowRhythmTargetHeight(
        collageWidth: Float,
        length: Int,
        horizontalCount: Int,
        verticalCount: Int,
    ): Float {
        val byLength = when (length) {
            1 -> 0.44f
            2 -> 0.34f
            3 -> 0.30f
            4 -> 0.27f
            else -> 0.24f
        }

        val byOrientation = when {
            horizontalCount * 2 >= length -> 0.31f
            verticalCount * 2 >= length -> 0.24f
            else -> 0.28f
        }

        return collageWidth * max(byLength, byOrientation)
    }

    private fun fitHeightLimits(
        input: SolvedRowPlanRefinementInput,
        plans: List<SolvedRowPlan>,
    ): Boolean {
        val height = totalHeight(plans, input.verticalGap)
        val satisfiesMinimum =
            input.minimumHeightAllowed <= 0f ||
                height >= input.minimumHeightAllowed - HEIGHT_EPSILON

        val satisfiesMaximum =
            !input.maximumHeightAllowed.isFinite() ||
                height <= input.maximumHeightAllowed + HEIGHT_EPSILON

        return satisfiesMinimum && satisfiesMaximum
    }

    private fun totalHeight(
        plans: List<SolvedRowPlan>,
        verticalGap: Float,
    ): Float {
        val contentHeight = plans.sumOf { it.height.toDouble() }.toFloat()
        val gapsHeight = verticalGap * max(0, plans.size - 1)
        return contentHeight + gapsHeight
    }

    private fun sumOtherHeights(
        plans: List<SolvedRowPlan>,
        excludedRowIndex: Int,
    ): Float {
        var sum = 0f
        var rowIndex = 0

        while (rowIndex < plans.size) {
            if (rowIndex != excludedRowIndex) {
                sum += plans[rowIndex].height
            }

            rowIndex++
        }

        return sum
    }

    private fun relativeHeightPenalty(
        first: Float,
        second: Float,
    ): Double {
        val denominator = max(max(first, second), 1f)
        val delta = (first - second) / denominator
        return (delta * delta).toDouble()
    }

    private companion object {
        private const val RHYTHM_REFINEMENT_PASSES = 2
        private const val MINIMUM_HEIGHT_EXPAND_PASSES = 4
        private const val HEIGHT_EPSILON = 1e-3f
        private const val MINIMUM_LOSS_TOLERANCE = 0.012
        private const val LOSS_TOLERANCE_FRACTION = 0.08
    }
}
