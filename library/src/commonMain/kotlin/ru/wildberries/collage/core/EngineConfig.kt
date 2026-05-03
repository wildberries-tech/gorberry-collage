package ru.wildberries.collage.core

import ru.wildberries.collage.model.SizeAttrs
import ru.wildberries.collage.strategy.RowLengthPriority

/**
 * Internal layout configuration used by the algorithm.
 *
 * Public users should configure [ru.wildberries.collage.CollageEngine] instead.
 */
internal data class EngineConfig(
    val paddings: Float = 6f,
    val minItemWidth: Float = 42f,
    val minItemHeight: Float = 42f,
    val maxItemsPerRow: Int = 4,
    val maxHorizontalsPerRow: Int = 2,
    val minCollageSize: SizeAttrs? = null,
    val maxCollageSize: SizeAttrs? = null,
    val rowLengthPriority: RowLengthPriority = RowLengthPriority(),
    val rowsSearchSpan: Int = 2,
    val ignoreHeightCaps: Boolean = false,
) {
    init {
        require(paddings >= 0f) { "paddings must be >= 0" }
        require(minItemWidth > 0f) { "minItemWidth must be > 0" }
        require(minItemHeight > 0f) { "minItemHeight must be > 0" }
        require(maxItemsPerRow >= 1) { "maxItemsPerRow must be >= 1" }
        require(maxHorizontalsPerRow >= 0) { "maxHorizontalsPerRow must be >= 0" }
        require(maxHorizontalsPerRow <= maxItemsPerRow) {
            "maxHorizontalsPerRow must be <= maxItemsPerRow"
        }
        require(rowsSearchSpan >= 0) { "rowsSearchSpan must be >= 0" }

        minCollageSize?.let { minimumSize ->
            require(minimumSize.width >= 0f) { "minCollageSize.width must be >= 0" }
            require(minimumSize.height >= 0f) { "minCollageSize.height must be >= 0" }
        }

        maxCollageSize?.let { maximumSize ->
            require(maximumSize.width > 0f) { "maxCollageSize.width must be > 0" }
            require(maximumSize.height > 0f) { "maxCollageSize.height must be > 0" }
        }

        val minimumSize = minCollageSize
        val maximumSize = maxCollageSize
        if (minimumSize != null && maximumSize != null) {
            require(minimumSize.width <= maximumSize.width) {
                "minCollageSize.width must be <= maxCollageSize.width"
            }
            require(minimumSize.height <= maximumSize.height) {
                "minCollageSize.height must be <= maxCollageSize.height"
            }
        }
    }
}
