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
import ru.wildberries.collage.model.TileFitPolicy
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
        configuration = CollageConfiguration().toSnapshot()
    }

    constructor(configuration: CollageConfiguration) {
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
    constructor(configure: CollageConfiguration.() -> Unit) {
        configuration = CollageConfiguration().apply(configure).toSnapshot()
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
            tileFitPolicy = configuration.tileFitPolicy,
        )

        return CollageCore(
            scorer = DefaultTileFitScorer(
                weights = TileFitScoringWeights.Default,
                lut = tuning.resources.powerLookupTable,
                fitPolicy = configuration.tileFitPolicy,
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
}
