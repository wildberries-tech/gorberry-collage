package ru.wildberries.collage

import ru.wildberries.collage.model.TileFitPolicy

/**
 * Engine configuration.
 *
 * Values are copied when [CollageEngine] is created.
 * Mutating this object after passing it to [CollageEngine] does not affect
 * the engine instance.
 */
class CollageConfiguration {

    private var defaultLayoutRequest: LayoutRequest? = null

    /**
     * Space between adjacent tiles and rows.
     */
    var spacing: Float = 6f

    /**
     * Minimum tile width. Helps avoid visually unreadable narrow tiles.
     */
    var minTileWidth: Float = 42f

    /**
     * Minimum tile height. Used as the lower bound for row planning.
     */
    var minTileHeight: Float = 42f

    /**
     * Maximum number of tiles in one row.
     */
    var maxTilesPerRow: Int = 4

    /**
     * Maximum number of landscape-like images in one row.
     */
    var maxLandscapeTilesPerRow: Int = 2

    /**
     * Controls how many row-count alternatives are evaluated.
     */
    var searchQuality: SearchQuality = SearchQuality.Balanced

    /**
     * Allows the layout height to exceed maxHeight.
     *
     * When enabled, maxHeight is ignored during planning. The engine still uses
     * minHeight when it tries to expand a layout that is too short.
     */
    var allowHeightOverflow: Boolean = false

    /**
     * Controls how image content is fitted inside each tile.
     */
    var tileFitPolicy: TileFitPolicy = TileFitPolicy.Auto

    /**
     * Sets default layout bounds for [CollageEngine.layout].
     *
     * Use this when one engine is tied to a fixed UI container.
     */
    fun defaultLayout(
        width: Float,
        minHeight: Float = 0f,
        maxHeight: Float = Float.POSITIVE_INFINITY,
    ) {
        val request = LayoutRequest(
            width = width,
            minHeight = minHeight,
            maxHeight = maxHeight,
        )
        request.validate()
        defaultLayoutRequest = request
    }

    internal fun toSnapshot(): ConfigurationSnapshot {
        require(spacing >= 0f) { "spacing must be >= 0" }
        require(minTileWidth > 0f) { "minTileWidth must be > 0" }
        require(minTileHeight > 0f) { "minTileHeight must be > 0" }
        require(maxTilesPerRow >= 1) { "maxTilesPerRow must be >= 1" }
        require(maxLandscapeTilesPerRow >= 0) {
            "maxLandscapeTilesPerRow must be >= 0"
        }
        require(maxLandscapeTilesPerRow <= maxTilesPerRow) {
            "maxLandscapeTilesPerRow must be <= maxTilesPerRow"
        }

        return ConfigurationSnapshot(
            spacing = spacing,
            minTileWidth = minTileWidth,
            minTileHeight = minTileHeight,
            maxTilesPerRow = maxTilesPerRow,
            maxLandscapeTilesPerRow = maxLandscapeTilesPerRow,
            rowSearchRadius = searchQuality.rowSearchRadius,
            allowHeightOverflow = allowHeightOverflow,
            tileFitPolicy = tileFitPolicy,
            defaultLayoutRequest = defaultLayoutRequest,
        )
    }
}

enum class SearchQuality(
    internal val rowSearchRadius: Int,
) {
    /**
     * Faster layout. Evaluates fewer row-count alternatives.
     */
    Fast(rowSearchRadius = 1),

    /**
     * Default quality/speed balance.
     */
    Balanced(rowSearchRadius = 2),

    /**
     * More exhaustive search. Can improve difficult cases at a higher cost.
     */
    High(rowSearchRadius = 3),
}

internal data class ConfigurationSnapshot(
    val spacing: Float,
    val minTileWidth: Float,
    val minTileHeight: Float,
    val maxTilesPerRow: Int,
    val maxLandscapeTilesPerRow: Int,
    val rowSearchRadius: Int,
    val allowHeightOverflow: Boolean,
    val tileFitPolicy: TileFitPolicy,
    val defaultLayoutRequest: LayoutRequest?,
)

internal data class LayoutRequest(
    val width: Float,
    val minHeight: Float,
    val maxHeight: Float,
) {

    fun validate() {
        require(width.isFinite() && width > 0f) {
            "width must be finite and > 0"
        }
        require(minHeight.isFinite() && minHeight >= 0f) {
            "minHeight must be finite and >= 0"
        }
        require((maxHeight.isFinite() && maxHeight > 0f) || maxHeight == Float.POSITIVE_INFINITY) {
            "maxHeight must be finite and > 0 or Float.POSITIVE_INFINITY"
        }
        require(minHeight <= maxHeight) {
            "minHeight must be <= maxHeight"
        }
    }
}
