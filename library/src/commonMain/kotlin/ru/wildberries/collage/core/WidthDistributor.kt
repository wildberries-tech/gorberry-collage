package ru.wildberries.collage.core

import kotlin.math.max
import kotlin.math.pow

internal object WidthDistributor {

    private const val DISTRIBUTION_POWER = 1.6f
    private const val RESIDUE_EPSILON = 1e-3f
    private const val SHARE_EPSILON = 1e-6f
    private const val MAX_ITERATIONS = 16

    fun distributeWithFloorsAndCaps(
        desire: FloatArray,
        floor: FloatArray,
        cap: FloatArray,
        target: Float,
    ): FloatArray {
        val output = floor.copyOf()
        var residue = target - output.sum()

        if (residue <= RESIDUE_EPSILON) {
            return fastEqualizeWhenNoResidue(
                itemCount = desire.size,
                target = target,
                floor = floor,
                cap = cap,
            )
        }

        residue = spreadResidueProportionallyWithClamp(
            output = output,
            desire = desire,
            cap = cap,
            target = target,
        )

        if (residue < -RESIDUE_EPSILON) {
            shaveExcessDownToTarget(
                output = output,
                floor = floor,
                target = target,
            )
        }

        return output
    }

    private fun fastEqualizeWhenNoResidue(
        itemCount: Int,
        target: Float,
        floor: FloatArray,
        cap: FloatArray,
    ): FloatArray {
        val output = floor.copyOf()
        val equalWidth = (target / itemCount).coerceAtLeast(1e-6f)

        var sumNow = 0f
        var itemIndex = 0

        while (itemIndex < itemCount) {
            output[itemIndex] = equalWidth.coerceIn(floor[itemIndex], cap[itemIndex])
            sumNow += output[itemIndex]
            itemIndex++
        }

        val scale = if (sumNow > 1e-6f) target / sumNow else 1f

        itemIndex = 0
        while (itemIndex < itemCount) {
            output[itemIndex] *= scale
            itemIndex++
        }

        return output
    }

    private fun spreadResidueProportionallyWithClamp(
        output: FloatArray,
        desire: FloatArray,
        cap: FloatArray,
        target: Float,
    ): Float {
        val free = BooleanArray(desire.size) { true }
        var residue = target - output.sum()
        var iteration = 0

        while (residue > RESIDUE_EPSILON && iteration < MAX_ITERATIONS) {
            val share = shareForFreeItems(
                desire = desire,
                free = free,
            )

            residue = if (share < SHARE_EPSILON) {
                allocateEvenlyToFreeItems(
                    output = output,
                    free = free,
                    residue = residue,
                    target = target,
                )
            } else {
                addProportionallyWithClamp(
                    output = output,
                    desire = desire,
                    free = free,
                    cap = cap,
                    residue = residue,
                    share = share,
                    target = target,
                )
            }

            iteration++
        }

        return residue
    }

    private fun shareForFreeItems(
        desire: FloatArray,
        free: BooleanArray,
    ): Float {
        var share = 0f
        var itemIndex = 0

        while (itemIndex < desire.size) {
            if (free[itemIndex]) {
                share += desire[itemIndex].pow(DISTRIBUTION_POWER)
            }

            itemIndex++
        }

        return share
    }

    private fun allocateEvenlyToFreeItems(
        output: FloatArray,
        free: BooleanArray,
        residue: Float,
        target: Float,
    ): Float {
        val freeCount = free.count { it }
        val allocation = residue / max(1, freeCount)

        var itemIndex = 0
        while (itemIndex < output.size) {
            if (free[itemIndex]) {
                output[itemIndex] += allocation
            }

            itemIndex++
        }

        return target - output.sum()
    }

    private fun addProportionallyWithClamp(
        output: FloatArray,
        desire: FloatArray,
        free: BooleanArray,
        cap: FloatArray,
        residue: Float,
        share: Float,
        target: Float,
    ): Float {
        var itemIndex = 0

        while (itemIndex < output.size) {
            if (free[itemIndex]) {
                val addition = residue * (desire[itemIndex].pow(DISTRIBUTION_POWER) / share)
                val value = (output[itemIndex] + addition).coerceAtMost(cap[itemIndex])

                if (value >= cap[itemIndex] - RESIDUE_EPSILON) {
                    free[itemIndex] = false
                }

                output[itemIndex] = value
            }

            itemIndex++
        }

        return target - output.sum()
    }

    private fun shaveExcessDownToTarget(
        output: FloatArray,
        floor: FloatArray,
        target: Float,
    ) {
        var excess = output.sum() - target
        var iteration = 0

        while (excess > RESIDUE_EPSILON && iteration < MAX_ITERATIONS) {
            val shrinkPool = computeShrinkPool(
                output = output,
                floor = floor,
            )

            if (shrinkPool < SHARE_EPSILON) break

            shrinkProportionally(
                output = output,
                floor = floor,
                excess = excess,
                shrinkPool = shrinkPool,
            )

            excess = output.sum() - target
            iteration++
        }
    }

    private fun computeShrinkPool(
        output: FloatArray,
        floor: FloatArray,
    ): Float {
        var pool = 0f
        var itemIndex = 0

        while (itemIndex < output.size) {
            pool += max(0f, output[itemIndex] - floor[itemIndex])
            itemIndex++
        }

        return pool
    }

    private fun shrinkProportionally(
        output: FloatArray,
        floor: FloatArray,
        excess: Float,
        shrinkPool: Float,
    ) {
        var itemIndex = 0

        while (itemIndex < output.size) {
            val available = max(0f, output[itemIndex] - floor[itemIndex])
            val shrink = excess * (available / shrinkPool)
            output[itemIndex] = (output[itemIndex] - shrink).coerceAtLeast(floor[itemIndex])
            itemIndex++
        }
    }
}
