package ru.wildberries.collage

import ru.wildberries.collage.cache.TileLossCache
import ru.wildberries.collage.cache.RowPlanCache
import ru.wildberries.collage.core.CollageCore
import ru.wildberries.collage.core.CollageTuning
import ru.wildberries.collage.core.EngineConfig
import ru.wildberries.collage.core.MonotonicClock
import ru.wildberries.collage.core.NoopLogger
import ru.wildberries.collage.core.RowPlanner
import ru.wildberries.collage.model.CollageLayout
import ru.wildberries.collage.model.CollageImage
import ru.wildberries.collage.model.SizeAttrs
import ru.wildberries.collage.strategy.DefaultRowPenaltyModel
import ru.wildberries.collage.strategy.DefaultTileGeometryMapper
import ru.wildberries.collage.strategy.DefaultTileFitScorer
import ru.wildberries.collage.strategy.TileFitScoringWeights

/**
 * Computes platform independent geometry for adaptive image collages
 *
 * The engine preserves input order and does not load, decode, or render images
 * All coordinates and sizes use the same unit as the requested layout width
 */
class CollageEngine {

    private val configuration: ConfigurationSnapshot
    private val tuning: CollageTuning.Snapshot = CollageTuning.default

    constructor() {
        configuration = Configuration().toSnapshot()
    }

    constructor(configuration: Configuration) {
        this.configuration = configuration.toSnapshot()
    }

    /**
     * Kotlin friendly configuration constructor
     *
     * Example:
     * ```
     * val engine = CollageEngine {
     *     spacing = 6f
     *     minTileWidth = 56f
     * }
     * ```
     */
    constructor(configure: Configuration.() -> Unit) {
        configuration = Configuration().apply(configure).toSnapshot()
    }

    fun layoutOrNull(
        images: List<CollageImage>,
        width: Float,
        minHeight: Float = 0f,
        maxHeight: Float = Float.POSITIVE_INFINITY,
    ): CollageLayout? {
        return runCatching {
            layout(
                images = images,
                width = width,
                minHeight = minHeight,
                maxHeight = maxHeight,
            )
        }.getOrNull()
    }

    /**
     * Computes collage geometry using default layout bounds configured with
     * [Configuration.defaultLayout].
     *
     * Use this overload when the engine is tied to a fixed UI container.
     */
    fun layout(
        images: List<CollageImage>,
    ): CollageLayout {
        val request = configuration.defaultLayoutRequest
            ?: error(
                "Default layout bounds are not configured. " +
                        "Use layout(photos, width, minHeight, maxHeight) " +
                        "or configure defaultLayout(...)."
            )

        return layout(
            images = images,
            request = request,
        )
    }

    /**
     * Computes collage geometry for [images].
     *
     * @param images images to arrange. Input order is preserved.
     * @param width target collage width in pixels, points, dp converted pixels, or any other UI unit
     * @param minHeight optional minimum collage height. Use 0 when there is no minimum.
     * @param maxHeight optional maximum collage height. Use [Float.POSITIVE_INFINITY] when there is no maximum.
     */
    fun layout(
        images: List<CollageImage>,
        width: Float,
        minHeight: Float = 0f,
        maxHeight: Float = Float.POSITIVE_INFINITY,
    ): CollageLayout {
        val request = LayoutRequest(
            width = width,
            minHeight = minHeight,
            maxHeight = maxHeight,
        )
        request.validate()

        return layout(
            images = images,
            request = request,
        )
    }

    private fun layout(
        images: List<CollageImage>,
        request: LayoutRequest,
    ): CollageLayout {
        return createOneShotCore(request)
            .arrangeWithGeometry(images)
    }

    private fun createOneShotCore(request: LayoutRequest): CollageCore {
        val internalConfig = EngineConfig(
            spacing = configuration.spacing,
            minItemWidth = configuration.minTileWidth,
            minItemHeight = configuration.minTileHeight,
            maxItemsPerRow = configuration.maxTilesPerRow,
            maxHorizontalsPerRow = configuration.maxLandscapeTilesPerRow,
            minCollageSize = SizeAttrs(
                width = request.width,
                height = request.minHeight,
            ),
            maxCollageSize = SizeAttrs(
                width = request.width,
                height = request.maxHeight,
            ),
            rowsSearchSpan = configuration.rowSearchRadius,
            ignoreMaxHeight = configuration.allowHeightOverflow,
        )

        return CollageCore(
            scorer = DefaultTileFitScorer(
                weights = TileFitScoringWeights.Default,
                lut = tuning.resources.powerLookupTable,
            ),
            renderer = DefaultTileGeometryMapper(),
            rowAugmentor = DefaultRowPenaltyModel(),
            clock = MonotonicClock,
            logger = NoopLogger,
            rowPlanCache = RowPlanCache(),
            lossCache = TileLossCache(),
            planner = RowPlanner(tuning = tuning),
            tuning = tuning,
            config = internalConfig,
        )
    }

    /**
     * Engine configuration. Values are copied when [CollageEngine] is created.
     *
     * Mutating this object after passing it to [CollageEngine] does not affect
     * the engine instance.
     */
    class Configuration {

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
         * Maximum number of landscape-like photos in one row.
         */
        var maxLandscapeTilesPerRow: Int = 2

        /**
         * Controls how many row-count alternatives are evaluated.
         */
        var searchQuality: SearchQuality = SearchQuality.Balanced

        /**
         * Allows the layout height to exceed maxHeight
         *
         * When enabled, maxHeight is not used as a planning cap
         */
        var allowHeightOverflow: Boolean = false

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
}

internal data class ConfigurationSnapshot(
    val spacing: Float,
    val minTileWidth: Float,
    val minTileHeight: Float,
    val maxTilesPerRow: Int,
    val maxLandscapeTilesPerRow: Int,
    val rowSearchRadius: Int,
    val allowHeightOverflow: Boolean,
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
