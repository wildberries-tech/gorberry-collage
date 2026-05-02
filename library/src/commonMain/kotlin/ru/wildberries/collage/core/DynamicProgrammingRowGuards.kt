package ru.wildberries.collage.core

import kotlin.math.max
import kotlin.math.min

internal object DynamicProgrammingRowGuards {
    private const val PORTRAIT_LIKE_ASPECT_LIMIT = 0.98f
    private const val WIDTH_EPSILON = 1e-3f

    fun allowedRowItemCapacity(
        maximumItemsPerRow: Int,
        verticalPhotoCount: Int,
    ): Int {
        var capacity = maximumItemsPerRow

        if (capacity >= 4 && verticalPhotoCount < 2) {
            capacity = min(capacity, 3)
        }

        return capacity
    }

    fun isRemainderFeasible(
        itemsLeftAfterThis: Int,
        rowsLeftAfterThis: Int,
        minimumItemsPerRow: Int,
        maximumItemsPerRow: Int,
    ): Boolean {
        if (rowsLeftAfterThis !in 0..itemsLeftAfterThis) return false

        return itemsLeftAfterThis >= rowsLeftAfterThis * minimumItemsPerRow &&
            itemsLeftAfterThis <= rowsLeftAfterThis * maximumItemsPerRow
    }

    fun isRowDistributionAllowed(
        rowLength: Int,
        itemsLeftAfterThis: Int,
        rowsLeftAfterThis: Int,
        totalItems: Int,
    ): Boolean {
        if (rowsLeftAfterThis <= 0) return true
        if (rowLength == 4 && itemsLeftAfterThis <= rowsLeftAfterThis * 3) return false

        if (
            rowLength == 1 &&
            itemsLeftAfterThis >= rowsLeftAfterThis * 2 &&
            totalItems > 3
        ) {
            return false
        }

        return true
    }

    fun hasTooManyPortraitLikeItemsInWideRow(
        rowLength: Int,
        portraitLikePhotoCount: Int,
        horizontalPhotoCount: Int,
    ): Boolean {
        if (rowLength < 4) return false

        return portraitLikePhotoCount >= 3 && horizontalPhotoCount == 0
    }

    fun countPortraitLikePhotos(
        aspectRatios: FloatArray,
        startIndex: Int,
        endIndex: Int,
    ): Int {
        var count = 0
        var photoIndex = startIndex

        while (photoIndex <= endIndex) {
            if (aspectRatios[photoIndex] <= PORTRAIT_LIKE_ASPECT_LIMIT) {
                count++
            }

            photoIndex++
        }

        return count
    }

    fun hasEnoughWidthForMinimumItems(
        rowLength: Int,
        collageWidth: Float,
        horizontalGap: Float,
        minimumItemWidth: Float,
    ): Boolean {
        val totalGapsWidth = horizontalGap * max(0, rowLength - 1)
        val availableWidth = collageWidth - totalGapsWidth

        return availableWidth + WIDTH_EPSILON >= rowLength * minimumItemWidth
    }
}
