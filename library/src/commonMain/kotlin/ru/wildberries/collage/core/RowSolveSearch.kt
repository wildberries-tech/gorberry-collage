package ru.wildberries.collage.core

import ru.wildberries.collage.api.EngineConfig
import ru.wildberries.collage.model.Photo
import kotlin.math.max
import kotlin.math.min

internal data class RowSearchPolicy(
    val searchRows: IntRange,
    val minRowsPolicy: Int,
)

internal data class RowSolveSearchInput(
    val dynamicProgrammingRowSolver: DynamicProgrammingRowSolver,
    val photos: List<Photo>,
    val collageWidth: Float,
    val horizontalGap: Float,
    val verticalGap: Float,
    val effectiveMaximumItemsPerRow: Int,
    val minimumHeightAllowed: Float,
    val maximumHeightAllowed: Float,
)

internal class RowSolveSearch(
    private val config: EngineConfig,
    private val tuning: CollageTuning.Snapshot,
) {

    fun computeSearchPolicy(
        photos: List<Photo>,
        collageWidth: Float,
        horizontalGap: Float,
        effectiveMaximumItemsPerRow: Int,
        maximumHeightAllowed: Float,
    ): RowSearchPolicy {
        val photoCount = photos.size
        val singleRowAllowed = allowSingleRowSmallInput(
            photos = photos,
            collageWidth = collageWidth,
            horizontalGap = horizontalGap,
            maximumHeightAllowed = maximumHeightAllowed,
        )

        val minimumRowsByPolicy = when (photoCount) {
            in 3..5 -> 2
            else -> if (singleRowAllowed) 1 else 2
        }

        val maximumRowsByPolicy = if (photoCount <= 4) {
            min(photoCount, 3)
        } else {
            photoCount
        }

        return RowSearchPolicy(
            searchRows = computeSearchRows(
                photoCount = photoCount,
                effectiveMaximumItemsPerRow = effectiveMaximumItemsPerRow,
                searchSpan = config.rowsSearchSpan,
                minimumRowsByPolicy = minimumRowsByPolicy,
                maximumRowsByPolicy = maximumRowsByPolicy,
            ),
            minRowsPolicy = minimumRowsByPolicy,
        )
    }

    fun selectBestSolve(
        input: RowSolveSearchInput,
        searchRows: IntRange,
    ): RowSolve? {
        val selection = SolveSelection()

        for (targetRowCount in searchRows) {
            val solve = solveForRowsOrNull(
                input = input,
                targetRowCount = targetRowCount,
            )

            if (solve != null) {
                selection.accept(
                    solve = solve,
                    isWithinRange = isWithinHeightRange(input, solve.totalHeight),
                    isRecoverableBelowMinimum = isRecoverableBelowMinimumSolve(input, solve.totalHeight),
                )
            }
        }

        return selection.best()
    }

    private data class SolveSelection(
        var bestValidWithoutSingleton: RowSolve? = null,
        var bestValidAny: RowSolve? = null,
        var bestRecoverableWithoutSingleton: RowSolve? = null,
        var bestRecoverableAny: RowSolve? = null,
    ) {
        fun accept(
            solve: RowSolve,
            isWithinRange: Boolean,
            isRecoverableBelowMinimum: Boolean,
        ) {
            when {
                isWithinRange -> acceptValid(solve)
                isRecoverableBelowMinimum -> acceptRecoverable(solve)
            }
        }

        fun best(): RowSolve? {
            return bestValidWithoutSingleton
                ?: bestValidAny
                ?: bestRecoverableWithoutSingleton
                ?: bestRecoverableAny
        }

        private fun acceptValid(solve: RowSolve) {
            if (!hasAnySingleton(solve) && betterSolve(solve, bestValidWithoutSingleton)) {
                bestValidWithoutSingleton = solve
            }

            if (betterSolve(solve, bestValidAny)) {
                bestValidAny = solve
            }
        }

        private fun acceptRecoverable(solve: RowSolve) {
            if (!hasAnySingleton(solve) && betterSolve(solve, bestRecoverableWithoutSingleton)) {
                bestRecoverableWithoutSingleton = solve
            }

            if (betterSolve(solve, bestRecoverableAny)) {
                bestRecoverableAny = solve
            }
        }
    }

    private fun isRecoverableBelowMinimumSolve(
        input: RowSolveSearchInput,
        totalHeight: Float,
    ): Boolean {
        val belowMinimum =
            input.minimumHeightAllowed > 0f &&
                totalHeight < input.minimumHeightAllowed - HEIGHT_EPSILON

        val notAboveMaximum =
            !input.maximumHeightAllowed.isFinite() ||
                totalHeight <= input.maximumHeightAllowed + HEIGHT_EPSILON

        return belowMinimum && notAboveMaximum
    }

    fun expandSearchFallback(
        input: RowSolveSearchInput,
        minimumRowsByPolicy: Int,
    ): RowSolve? {
        val photoCount = input.photos.size
        val minimumRowsByCapacity = max(
            1,
            (photoCount + input.effectiveMaximumItemsPerRow - 1) / input.effectiveMaximumItemsPerRow,
        )
        val minimumRows = max(minimumRowsByCapacity, minimumRowsByPolicy)

        var bestValidWithoutSingleton: RowSolve? = null
        var bestValidAny: RowSolve? = null
        var bestAnyWithoutSingleton: RowSolve? = null
        var bestAny: RowSolve? = null

        for (targetRowCount in minimumRows..photoCount) {
            val solve = solveForRowsOrNull(
                input = input,
                targetRowCount = targetRowCount,
            )

            if (solve != null) {
                if (!hasAnySingleton(solve) && betterSolve(solve, bestAnyWithoutSingleton)) {
                    bestAnyWithoutSingleton = solve
                }

                if (betterSolve(solve, bestAny)) {
                    bestAny = solve
                }

                if (isWithinHeightRange(input, solve.totalHeight)) {
                    if (!hasAnySingleton(solve) && betterSolve(solve, bestValidWithoutSingleton)) {
                        bestValidWithoutSingleton = solve
                    }

                    if (betterSolve(solve, bestValidAny)) {
                        bestValidAny = solve
                    }
                }
            }
        }

        return bestValidWithoutSingleton
            ?: bestValidAny
            ?: bestAnyWithoutSingleton
            ?: bestAny
    }

    private fun solveForRowsOrNull(
        input: RowSolveSearchInput,
        targetRowCount: Int,
    ): RowSolve? {
        val solve = solveForRows(
            input = input,
            targetRowCount = targetRowCount,
        )

        return if (solve.ranges.isNotEmpty() && solve.cost.isFinite()) {
            solve
        } else {
            null
        }
    }

    private fun solveForRows(
        input: RowSolveSearchInput,
        targetRowCount: Int,
    ): RowSolve {
        return input.dynamicProgrammingRowSolver.solveForTargetRows(
            photos = input.photos,
            collageWidth = input.collageWidth,
            horizontalGap = input.horizontalGap,
            verticalGap = input.verticalGap,
            targetRows = targetRowCount,
            maxHeightAllowed = input.maximumHeightAllowed,
        )
    }

    private fun isWithinHeightRange(
        input: RowSolveSearchInput,
        totalHeight: Float,
    ): Boolean {
        val satisfiesMinimum =
            input.minimumHeightAllowed <= 0f ||
                totalHeight >= input.minimumHeightAllowed - HEIGHT_EPSILON

        val satisfiesMaximum =
            !input.maximumHeightAllowed.isFinite() ||
                totalHeight <= input.maximumHeightAllowed + HEIGHT_EPSILON

        return satisfiesMinimum && satisfiesMaximum
    }

    private fun computeSearchRows(
        photoCount: Int,
        effectiveMaximumItemsPerRow: Int,
        searchSpan: Int,
        minimumRowsByPolicy: Int,
        maximumRowsByPolicy: Int,
    ): IntRange {
        val minimumRowsByCapacity = max(
            1,
            (photoCount + effectiveMaximumItemsPerRow - 1) / effectiveMaximumItemsPerRow,
        )
        val minimumRows = max(minimumRowsByCapacity, minimumRowsByPolicy)
        val estimatedRows = max(1, (photoCount + 2) / 3)

        val lowerBound = max(minimumRows, estimatedRows - searchSpan)
        val upperBound = max(minimumRows, estimatedRows + searchSpan)
        val cappedUpperBound = min(upperBound, maximumRowsByPolicy)

        return if (cappedUpperBound >= lowerBound) {
            lowerBound..cappedUpperBound
        } else {
            lowerBound..lowerBound
        }
    }

    private fun allowSingleRowSmallInput(
        photos: List<Photo>,
        collageWidth: Float,
        horizontalGap: Float,
        maximumHeightAllowed: Float,
    ): Boolean {
        val photoCount = photos.size
        if (photoCount !in 2..4) return true

        val strongHorizontalAspectThreshold = tuning.heuristics.smallNStrongHorizontalTau
        var aspectSum = 0f
        var strongHorizontalCount = 0

        for (photo in photos) {
            val aspectRatio = MathUtil.aspect(photo.width, photo.height)
            aspectSum += aspectRatio

            if (aspectRatio >= strongHorizontalAspectThreshold) {
                strongHorizontalCount++
            }
        }

        if (photoCount == 2) {
            if (strongHorizontalCount == 2) return false
        } else {
            if (strongHorizontalCount > 0) return false
        }

        val availableWidth = collageWidth - horizontalGap * (photoCount - 1)
        if (availableWidth <= 1f || aspectSum <= 0.0001f) return false

        val predictedRowHeight = availableWidth / aspectSum
        val minimumHeightFactor = when (photoCount) {
            2 -> tuning.heuristics.smallNSingleRowMinHeightFactor2
            3 -> tuning.heuristics.smallNSingleRowMinHeightFactor3
            else -> tuning.heuristics.smallNSingleRowMinHeightFactor4
        }

        val minimumAcceptableHeight = min(
            maximumHeightAllowed,
            collageWidth * minimumHeightFactor,
        )

        return predictedRowHeight >= minimumAcceptableHeight
    }

    private companion object {
        private const val HEIGHT_EPSILON = 1e-3f
    }
}
