package ru.wildberries.collage

import ru.wildberries.collage.cache.LossCache
import ru.wildberries.collage.cache.PlanCache
import ru.wildberries.collage.core.CollageCore
import ru.wildberries.collage.core.CollageTuning
import ru.wildberries.collage.core.EngineConfig
import ru.wildberries.collage.core.MonotonicClock
import ru.wildberries.collage.core.NoopLogger
import ru.wildberries.collage.core.RowPlanner
import ru.wildberries.collage.model.CollageGeometry
import ru.wildberries.collage.model.Photo
import ru.wildberries.collage.model.SizeAttrs
import ru.wildberries.collage.strategy.DefaultRowCostAugmentor
import ru.wildberries.collage.strategy.DefaultTileRenderer
import ru.wildberries.collage.strategy.DefaultTileScorer
import ru.wildberries.collage.strategy.FitWeights

/**
 * Computes collage geometry for a list of photos.
 *
 * The engine instance is safe to reuse. All mutable layout state is created
 * for each [layout] call.
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
     * Kotlin-friendly configuration constructor.
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

    /**
     * Computes collage geometry using default layout bounds configured with
     * [Configuration.defaultLayout].
     *
     * Use this overload when the engine is tied to a fixed UI container.
     */
    fun layout(
        photos: List<Photo>,
    ): CollageGeometry {
        val request = configuration.defaultLayoutRequest
            ?: error(
                "Default layout bounds are not configured. " +
                        "Use layout(photos, width, minHeight, maxHeight) " +
                        "or configure defaultLayout(...)."
            )

        return layout(
            photos = photos,
            request = request,
        )
    }

    /**
     * Computes collage geometry.
     *
     * @param photos photos to arrange. The input order is preserved.
     * @param width target collage width in pixels or logical UI units.
     * @param minHeight optional minimum collage height. Use 0 when there is no minimum.
     * @param maxHeight optional maximum collage height. Use [Float.POSITIVE_INFINITY] when there is no maximum.
     */
    fun layout(
        photos: List<Photo>,
        width: Float,
        minHeight: Float = 0f,
        maxHeight: Float = Float.POSITIVE_INFINITY,
    ): CollageGeometry {
        val request = LayoutRequest(
            width = width,
            minHeight = minHeight,
            maxHeight = maxHeight,
        )
        request.validate()

        return layout(
            photos = photos,
            request = request,
        )
    }

    private fun layout(
        photos: List<Photo>,
        request: LayoutRequest,
    ): CollageGeometry {
        return createOneShotCore(request)
            .arrangeWithGeometry(photos)
    }

    private fun createOneShotCore(request: LayoutRequest): CollageCore {
        val internalConfig = EngineConfig(
            paddings = configuration.spacing,
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
            ignoreHeightCaps = configuration.allowHeightOverflow,
        )

        return CollageCore(
            scorer = DefaultTileScorer(
                weights = FitWeights.Default,
                lut = tuning.resources.powerLookupTable,
            ),
            renderer = DefaultTileRenderer(),
            rowAugmentor = DefaultRowCostAugmentor(),
            clock = MonotonicClock,
            logger = NoopLogger,
            planCache = PlanCache(),
            lossCache = LossCache(),
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
         * Allows the layout to exceed maxHeight when strict height clipping would
         * produce a significantly worse collage.
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
