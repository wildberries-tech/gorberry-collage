package ru.wildberries.collage.core

import kotlin.math.max

internal object SafeRowRanges {

    fun build(
        photoCount: Int,
        effectiveMaximumItemsPerRow: Int,
    ): List<IntRange> {
        val lengths = buildBalancedLengths(
            photoCount = photoCount,
            effectiveMaximumItemsPerRow = effectiveMaximumItemsPerRow,
        )

        val ranges = ArrayList<IntRange>(lengths.size)
        var startIndex = 0

        for (length in lengths) {
            ranges += startIndex until (startIndex + length)
            startIndex += length
        }

        return ranges
    }

    private fun buildBalancedLengths(
        photoCount: Int,
        effectiveMaximumItemsPerRow: Int,
    ): IntArray {
        val rowCount = max(
            1,
            (photoCount + effectiveMaximumItemsPerRow - 1) / effectiveMaximumItemsPerRow,
        )
        val minimumItemsPerRow = if (2 * rowCount <= photoCount) 2 else 1

        val lengths = IntArray(rowCount) { minimumItemsPerRow }
        var remainingItems = photoCount - minimumItemsPerRow * rowCount
        var rowIndex = 0

        while (remainingItems > 0) {
            if (lengths[rowIndex] < effectiveMaximumItemsPerRow) {
                lengths[rowIndex]++
                remainingItems--
            }

            rowIndex = (rowIndex + 1) % rowCount
        }

        return lengths
    }
}
