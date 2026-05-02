package ru.wildberries.collage.api

import ru.wildberries.collage.cache.LossCache
import ru.wildberries.collage.cache.PlanCache
import ru.wildberries.collage.core.CollageCore
import ru.wildberries.collage.core.CollageTuning
import ru.wildberries.collage.core.RowPlanner
import ru.wildberries.collage.core.TileRenderer
import ru.wildberries.collage.core.TileScorer
import ru.wildberries.collage.model.SizeAttrs
import ru.wildberries.collage.strategy.DefaultRowCostAugmentor
import ru.wildberries.collage.strategy.DefaultTileRenderer
import ru.wildberries.collage.strategy.DefaultTileScorer
import ru.wildberries.collage.strategy.FitWeights
import ru.wildberries.collage.strategy.RowCostAugmentor
import ru.wildberries.collage.strategy.RowLengthPriority

/** builder with overridable components */
fun createCollageEngine(
    clock: Clock,
    logger: Logger = NoopLogger,
    fit: FitWeights = FitWeights(),
    config: EngineConfig = EngineConfig(),
    scorer: TileScorer = DefaultTileScorer(weights = fit, lut = CollageTuning.current.resources.powerLookupTable),
    renderer: TileRenderer = DefaultTileRenderer(),
): CollageEngine {
    return CollageCore(
        scorer = scorer,
        renderer = renderer,
        rowAugmentor = DefaultRowCostAugmentor(),
        clock = clock,
        logger = logger,
        planCache = PlanCache(),
        lossCache = LossCache(),
        planner = RowPlanner(),
        tuning = CollageTuning.current,
        config = config,
    )
}

/** Internal test builder for tests and grid search with optional overrides */
internal fun createGridSearchEngine(
    clock: Clock,
    logger: Logger = NoopLogger,
    config: EngineConfig,
    weights: FitWeights = FitWeights.Default,
    augmentor: RowCostAugmentor? = null,
    scorer: TileScorer? = null,
    renderer: TileRenderer? = null,
    planner: RowPlanner = RowPlanner(),
): CollageEngine {
    val tuning = CollageTuning.current

    val rowAugmentor = augmentor ?: DefaultRowCostAugmentor()
    val tileScorer = scorer ?: DefaultTileScorer(weights, tuning.resources.powerLookupTable)
    val tileRenderer = renderer ?: DefaultTileRenderer()

    return CollageCore(
        scorer = tileScorer,
        renderer = tileRenderer,
        rowAugmentor = rowAugmentor,
        clock = clock,
        logger = logger,
        planCache = PlanCache(),
        lossCache = LossCache(),
        planner = planner,
        tuning = tuning,
        config = config
    )
}

/**
 * Высокоуровневые настройки коллажа
 *
 * Эти параметры используются на этапах:
 * 1 Оценка целевого числа рядов и планирование DP ([rowsSearchSpan], [maxItemsPerRow], [maxHorizontalsPerRow])
 * 2 Ограничения размеров ячеек и рядов ([minItemWidth], [minItemHeight], [ignoreHeightCaps]).
 * 3 Глобальные рамки коллажа ([minCollageSize], [maxCollageSize])
 */
data class EngineConfig(
    /** Горизонтальные и вертикальные отступы между тайлами */
    val paddings: Float = 6f,

    /** Минимальная ширина тайла */
    val minItemWidth: Float = 42f,

    /** Минимальная высота тайла */
    val minItemHeight: Float = 42f,

    /** Максимум элементов в одном ряду */
    val maxItemsPerRow: Int = 4,

    /** Максимум горизонтальных картинок в ряду */
    val maxHorizontalsPerRow: Int = 2,

    /** Минимально допустимый размер итогового коллажа или null без минимума */
    val minCollageSize: SizeAttrs? = null,

    /** Максимально допустимый размер итогового коллажа, или null без ограничения */
    val maxCollageSize: SizeAttrs? = null,

    /**
     * Приоритет на количество тайлов в ряду.
     * Позволяет мягко поощрять или штрафовать количество тайлов,
     * например 1 — штраф, 4 — небольшой бонус
     * По умолчанию выключен
     */
    val rowLengthPriority: RowLengthPriority = RowLengthPriority(),

    /**
     * Радиус перебора по целевому числу рядов вокруг оценки
     *
     * Как используется:
     * 1) Сначала оцениваем разумное число рядов `R`:
     *    - минимально возможное: `Rmin = ceil(n / maxItemsPerRow)` где `n` — количество фото
     *    - эвристическая оценка: `Rest = max(1, (n + 2) / 3)`
     * 2) Формируем диапазон поиска:
     *    `search = max(Rmin, Rest - rowsSearchSpan) .. max(Rmin, Rest + rowsSearchSpan)`
     *
     * Таким образом, `rowsSearchSpan = 0` — пробуем только оценку `Rest`
     * `= 1` пробуем `Rest-1, Rest, Rest+1` и т.д., но нижняя граница никогда не ниже `Rmin`
     *
     * Влияние на скорость:
     *  - время работы растёт примерно линейно по ширине диапазона (каждое значение в диапазоне это отдельный прогон DP)
     *  - типичные значения 1..3 дают хороший баланс качества/времени
     *
     * Примеры (maxItemsPerRow = 4):
     *  - n = 12, Rest = (12+2)/3 = 4, Rmin = ceil(12/4)=3
     *    • rowsSearchSpan = 0 → ищем только 4
     *    • rowsSearchSpan = 2 → ищем  max(3, 4-2)=3 .. max(3, 4+2)=6  → 3..6
     *  - n = 5, Rest = (5+2)/3 = 2, Rmin = ceil(5/4)=2
     *    • rowsSearchSpan = 2 → max(2, 0)=2 .. max(2, 4)=4  → 2..4
     *
     * Замечание:
     *  - если первичный диапазон не дал валидного решения (редкий кейс), движок выполняет
     *    запасной перебор `Rmin..n` — см. реализацию `CollageCore.expandSearchFallback()`.
     *
     * Требования - значение неотрицательное.
     */
    val rowsSearchSpan: Int = 2,

    /**
     * Игнорировать глобальный лимит по высоте коллажа
     * true — разрешить превышать maxCollageSize.height, если это даёт визуально лучшее разбиение.
     * Используется только при падении в DP и постобработке высот.
     */
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
