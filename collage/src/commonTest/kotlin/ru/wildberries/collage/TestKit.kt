package ru.wildberries.collage

import ru.wildberries.collage.cache.TileLossCache
import ru.wildberries.collage.cache.RowPlanCache
import ru.wildberries.collage.core.Clock
import ru.wildberries.collage.core.CollageCore
import ru.wildberries.collage.core.CollageTuning
import ru.wildberries.collage.core.EngineConfig
import ru.wildberries.collage.core.Logger
import ru.wildberries.collage.core.RowPlanner
import ru.wildberries.collage.model.CollageLayout
import ru.wildberries.collage.model.CollageImage
import ru.wildberries.collage.strategy.DefaultRowPenaltyModel
import ru.wildberries.collage.strategy.DefaultTileGeometryMapper
import ru.wildberries.collage.strategy.DefaultTileFitScorer
import ru.wildberries.collage.strategy.TileFitScoringWeights
import ru.wildberries.collage.strategy.RowPenaltyModel
import kotlin.random.Random

class TestClock : Clock {

    override fun nowNs(): Long = 0L
}

class TestLogger : Logger {

    override fun d(tag: String, msg: String) {
        println("[$tag]: $msg")
    }
}

/**
 * Test-only wrapper around the internal algorithm.
 *
 * Public API tests should use [CollageEngine].
 * Internal quality/tuning tests can use this wrapper to keep the old
 * arrangeWithGeometry-style assertions.
 */
class TestLayoutEngine internal constructor(
    private val core: CollageCore,
) {

    fun arrangeWithGeometry(collageImages: List<CollageImage>): CollageLayout {
        return core.arrangeWithGeometry(collageImages)
    }
}

object TestKit {

    internal fun engine(
        cfg: EngineConfig,
        weights: TileFitScoringWeights = TileFitScoringWeights.Default,
        logger: Logger = TestLogger(),
    ): TestLayoutEngine {
        return TestLayoutEngine(
            createTestCore(
                config = cfg,
                weights = weights,
                logger = logger,
                augmentor = null,
            )
        )
    }

    internal fun engineAug(
        cfg: EngineConfig,
        weights: TileFitScoringWeights = TileFitScoringWeights.Default,
        logger: Logger = TestLogger(),
        stickGamma3: Float,
        stickGamma4: Float,
        stickPenaltyAlpha: Double,
        fourMixPenalty: Double,
        equalizePerRowAlpha: Double,
    ): TestLayoutEngine {
        val baseAugmentorConfig = CollageTuning.default.augmentor

        val augmentor = DefaultRowPenaltyModel(
            widow = baseAugmentorConfig.widow,
            penaltyPerExtraHorizontal = baseAugmentorConfig.penaltyPerExtraHorizontal,
            penaltyTwoHorizontalsInOneRow = baseAugmentorConfig.penaltyTwoHorizontalsInOneRow,
            penaltyThreeHorizontalsInOneRow = baseAugmentorConfig.penaltyThreeHorizontalsInOneRow,
            topHeavinessAlpha = baseAugmentorConfig.topHeavinessAlpha,
            lastRowTallAlpha = baseAugmentorConfig.lastRowTallAlpha,
            firstRowShortAlpha = baseAugmentorConfig.firstRowShortAlpha,
            preferThreeVerticalsBonus = baseAugmentorConfig.preferThreeVerticalsBonus,
            rowContrastAlpha = baseAugmentorConfig.rowContrastAlpha,
            rowWidthBalanceAlpha = baseAugmentorConfig.rowWidthBalanceAlpha,
            verticalSquashGuardFracOfWidth = baseAugmentorConfig.verticalSquashGuardFracOfWidth,
            verticalSquashAlpha = baseAugmentorConfig.verticalSquashAlpha,
            rowHeightSmoothAlpha = baseAugmentorConfig.rowHeightSmoothAlpha,
            heightBudgetAlpha = baseAugmentorConfig.heightBudgetAlpha,
            stickGamma4 = stickGamma4,
            stickGamma3 = stickGamma3,
            stickPenaltyAlpha = stickPenaltyAlpha,
            fourMixPenalty = fourMixPenalty,
            equalizePerRowAlpha = equalizePerRowAlpha,
            kpAlpha = baseAugmentorConfig.kpAlpha,
            kpPower = baseAugmentorConfig.kpPower,
            fitnessJumpAlpha = baseAugmentorConfig.fitnessJumpAlpha,
            fillAlpha = baseAugmentorConfig.fillAlpha,
            tightBuckets = baseAugmentorConfig.tightBuckets,
            bonusAlpha = baseAugmentorConfig.bonusAlpha,
            allowNegativeTotalPenalty = baseAugmentorConfig.allowNegativeTotalPenalty,
            bonusRowLenTol = baseAugmentorConfig.bonusRowLenTol,
            bonusEqualHeightsAlpha = baseAugmentorConfig.bonusEqualHeightsAlpha,
            bonusEqualHeightsTolFrac = baseAugmentorConfig.bonusEqualHeightsTolFrac,
        )

        return TestLayoutEngine(
            createTestCore(
                config = cfg,
                weights = weights,
                logger = logger,
                augmentor = augmentor,
            )
        )
    }

    private fun createTestCore(
        config: EngineConfig,
        weights: TileFitScoringWeights,
        logger: Logger,
        augmentor: RowPenaltyModel?,
    ): CollageCore {
        val tuning = CollageTuning.default

        return CollageCore(
            scorer = DefaultTileFitScorer(
                weights = weights,
                lut = tuning.resources.powerLookupTable,
            ),
            renderer = DefaultTileGeometryMapper(),
            rowAugmentor = augmentor ?: DefaultRowPenaltyModel(),
            clock = TestClock(),
            logger = logger,
            rowPlanCache = RowPlanCache(),
            lossCache = TileLossCache(),
            planner = RowPlanner(tuning = tuning),
            tuning = tuning,
            config = config,
        )
    }

    fun p(id: Int, w: Float, h: Float) = CollageImage(id, w, h)

    fun gridSquare(n: Int, size: Float) =
        (0 until n).map { i -> p(i, size, size) }

    fun mixedSample1(): List<CollageImage> = listOf(
        p(0, 1200f, 800f),
        p(1, 900f, 1600f),
        p(2, 1000f, 1000f),
        p(3, 1600f, 900f),
        p(4, 800f, 1200f)
    )

    fun sample4_HVHV_spikes() = listOf(
        p(0, 1600f, 900f),
        p(1, 900f, 1600f),
        p(2, 1600f, 900f),
        p(3, 900f, 1600f)
    )

    fun allH(n: Int, base: Float = 1000f, ar: Float = 16f / 10f) =
        (0 until n).map { i -> p(i, base, base / ar) }

    fun allV(n: Int, base: Float = 1000f, ar: Float = 10f / 16f) =
        (0 until n).map { i -> p(i, base * ar, base) }

    fun allS(n: Int, size: Float = 1000f) =
        (0 until n).map { i -> p(i, size, size) }

    fun alternatingHV(n: Int, base: Float = 1000f): List<CollageImage> =
        (0 until n).map { i ->
            if (i % 2 == 0) p(i, base, base * 0.625f) else p(i, base * 0.56f, base)
        }

    fun nearMinWidthVerticals(count: Int, base: Float = 1000f, targetAR: Float = 0.5f): List<CollageImage> =
        (0 until count).map { i -> p(i, base * targetAR, base) }

    fun adversarialWideAndThin(nThin: Int = 3, base: Float = 1000f): List<CollageImage> {
        val h1 = p(0, base * 2.0f, base * 1.0f)
        val h2 = p(1, base * 1.8f, base * 1.0f)
        val vs = (0 until nThin).map { i -> p(i + 2, base * 0.5f, base * 1.2f) }
        return listOf(h1, h2) + vs
    }

    fun quasiSquares(n: Int, base: Float = 1000f, delta: Float = 0.08f): List<CollageImage> =
        (0 until n).map { i ->
            val ar = if (i % 2 == 0) 1f + delta else 1f - delta
            p(i, base * ar, base)
        }

    fun randomMixed(
        n: Int,
        seed: Long = 42L,
        minSide: Float = 600f,
        maxSide: Float = 1600f,
    ): List<CollageImage> {
        val rnd = Random(seed)
        fun pickAspect() = when (rnd.nextInt(3)) {
            0 -> 16f / 10f
            1 -> 10f / 16f
            else -> 1f
        }
        return (0 until n).map { i ->
            val base = rnd.nextFloat() * (maxSide - minSide) + minSide
            val ar = pickAspect()
            val w = base * if (ar >= 1f) ar else 1f
            val h = base * if (ar >= 1f) 1f else 1f / ar
            p(i, w, h)
        }
    }

    fun randomThinHeavy(
        n: Int,
        seed: Long = 2L,
        minSide: Float = 700f,
        maxSide: Float = 1500f,
        vShare: Float = 0.7f,
    ): List<CollageImage> {
        val rnd = Random(seed)
        return (0 until n).map { i ->
            val isV = rnd.nextFloat() < vShare
            val base = rnd.nextFloat() * (maxSide - minSide) + minSide
            if (isV) p(i, base * 0.55f, base) else p(i, base, base * 0.6f)
        }
    }

    fun staircaseBlocks(
        repeats: Int = 4,
        blockH: Int = 3,
        blockV: Int = 3,
        blockS: Int = 2,
        base: Float = 1000f,
    ): List<CollageImage> {
        var id = 0
        val out = ArrayList<CollageImage>(repeats * (blockH + blockV + blockS))
        repeat(repeats) {
            repeat(blockH) { out += p(id++, base, base * 0.6f) }
            repeat(blockV) { out += p(id++, base * 0.56f, base) }
            repeat(blockS) { out += p(id++, base, base) }
        }
        return out
    }

    fun longMixed(n: Int = 30, seed: Long = 9L): List<CollageImage> = randomMixed(n, seed)

    private const val ULTRA_WIDE_MIN = 2.4f
    private const val ULTRA_WIDE_MAX = 5.5f

    private fun sampleAspectRich(
        rnd: Random,
        pUltra: Float = 0.30f,
        squareJitter: Float = 0.12f,
        classicJitter: Float = 0.18f,
    ): Float {
        if (rnd.nextFloat() < pUltra) {
            val basePool = floatArrayOf(2.4f, 3.0f, 3.6f, 4.0f, 4.5f, 5.0f, 5.5f)
            val base = basePool[rnd.nextInt(basePool.size)]
            val jitter = 1f + (rnd.nextFloat() - 0.5f) * 0.25f
            val aWide = (base * jitter).coerceIn(ULTRA_WIDE_MIN, ULTRA_WIDE_MAX)
            return if (rnd.nextBoolean()) aWide else 1f / aWide
        }
        return when (rnd.nextInt(4)) {
            0 -> (1f * (1f + (rnd.nextFloat() - 0.5f) * squareJitter)).coerceIn(0.80f, 1.25f)
            1 -> {
                val base = floatArrayOf(1.33f, 1.50f, 1.60f, 1.78f, 2.00f)[rnd.nextInt(5)]
                (base * (1f + (rnd.nextFloat() - 0.5f) * classicJitter)).coerceIn(1.10f, 2.20f)
            }

            2 -> {
                val base = floatArrayOf(1 / 1.33f, 1 / 1.50f, 1 / 1.60f, 1 / 1.78f, 1 / 2.00f)[rnd.nextInt(5)]
                (base * (1f + (rnd.nextFloat() - 0.5f) * classicJitter)).coerceIn(0.45f, 0.90f)
            }

            else -> (1f + (rnd.nextFloat() - 0.5f) * 0.25f).coerceIn(0.75f, 1.35f)
        }
    }

    fun randomMixedRich(
        n: Int,
        seed: Long = 123L,
        minSide: Float = 600f,
        maxSide: Float = 1600f,
        pUltra: Float = 0.30f,
    ): List<CollageImage> {
        val rnd = Random(seed)
        return (0 until n).map { i ->
            val base = rnd.nextFloat() * (maxSide - minSide) + minSide
            val ar = sampleAspectRich(rnd, pUltra = pUltra)
            val w = if (ar >= 1f) base * ar else base
            val h = if (ar >= 1f) base else base / ar
            CollageImage(i, w, h)
        }
    }

    fun allUltraH(n: Int, base: Float = 1000f): List<CollageImage> {
        val pool = floatArrayOf(2.8f, 3.2f, 3.8f, 4.5f, 5.0f, 5.5f)
        return (0 until n).map { i -> CollageImage(i, base * pool[i % pool.size], base) }
    }

    fun allUltraV(n: Int, base: Float = 1000f): List<CollageImage> {
        val pool = floatArrayOf(1 / 2.8f, 1 / 3.2f, 1 / 3.8f, 1 / 4.5f, 1 / 5.0f, 1 / 5.5f)
        return (0 until n).map { i -> CollageImage(i, base * pool[i % pool.size], base) }
    }

    fun ultraSaw(nPairs: Int, base: Float = 1000f): List<CollageImage> {
        val out = ArrayList<CollageImage>(2 * nPairs)
        var id = 0
        val widePool = floatArrayOf(3.0f, 4.0f, 5.0f)
        val tallPool = floatArrayOf(1 / 3.0f, 1 / 4.0f, 1 / 5.0f)
        repeat(nPairs) {
            val w = widePool[it % widePool.size]
            val t = tallPool[it % tallPool.size]
            out += CollageImage(id++, base * w, base)
            out += CollageImage(id++, base * t, base)
        }
        return out
    }
}

fun p(id: Int, w: Float, h: Float) = TestKit.p(id, w, h)
fun gridSquare(n: Int, size: Float) = TestKit.gridSquare(n, size)
fun mixedSample1() = TestKit.mixedSample1()
fun sample4_HVHV_spikes() = TestKit.sample4_HVHV_spikes()
fun allH(n: Int, base: Float = 1000f, ar: Float = 16f / 10f) = TestKit.allH(n, base, ar)
fun allV(n: Int, base: Float = 1000f, ar: Float = 10f / 16f) = TestKit.allV(n, base, ar)
fun allS(n: Int, size: Float = 1000f) = TestKit.allS(n, size)
fun alternatingHV(n: Int, base: Float = 1000f) = TestKit.alternatingHV(n, base)
fun nearMinWidthVerticals(count: Int, base: Float = 1000f, targetAR: Float = 0.5f) = TestKit.nearMinWidthVerticals(count, base, targetAR)
fun adversarialWideAndThin(nThin: Int = 3, base: Float = 1000f) = TestKit.adversarialWideAndThin(nThin, base)
fun quasiSquares(n: Int, base: Float = 1000f, delta: Float = 0.08f) = TestKit.quasiSquares(n, base, delta)
fun randomMixed(n: Int, seed: Long = 42L, minSide: Float = 600f, maxSide: Float = 1600f) = TestKit.randomMixed(n, seed, minSide, maxSide)
fun randomThinHeavy(n: Int, seed: Long = 2L, minSide: Float = 700f, maxSide: Float = 1500f, vShare: Float = 0.7f) =
    TestKit.randomThinHeavy(n, seed, minSide, maxSide, vShare)

fun staircaseBlocks(repeats: Int = 4, blockH: Int = 3, blockV: Int = 3, blockS: Int = 2, base: Float = 1000f) =
    TestKit.staircaseBlocks(repeats, blockH, blockV, blockS, base)

fun longMixed(n: Int = 30, seed: Long = 9L) = TestKit.longMixed(n, seed)
