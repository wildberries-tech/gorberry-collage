package ru.wildberries.collage

import ru.wildberries.collage.core.CollageTuning
import ru.wildberries.collage.core.EngineConfig
import ru.wildberries.collage.core.MathUtil
import ru.wildberries.collage.model.CollageGeometry
import ru.wildberries.collage.model.Photo
import ru.wildberries.collage.model.RowGeometry
import ru.wildberries.collage.model.SizeAttrs
import ru.wildberries.collage.strategy.TileFit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.round
import kotlin.test.DefaultAsserter.assertTrue
import kotlin.test.Test

private fun fmt(value: Number, digits: Int = 1): String {
    require(digits >= 0) { "digits must be >= 0" }

    val factor = 10.0.pow(digits)
    val roundedValue = round(value.toDouble() * factor) / factor
    val normalizedValue = if (abs(roundedValue) < 1e-12) 0.0 else roundedValue
    val text = normalizedValue.toString()

    if (digits == 0) {
        return text.substringBefore('.')
    }

    val parts = text.split('.', limit = 2)
    val integerPart = parts[0]
    val fractionPart = parts.getOrElse(1) { "" }
        .padEnd(digits, '0')
        .take(digits)

    return "$integerPart.$fractionPart"
}

private data class CaseCfg(val tag: String, val photos: List<Photo>)

private fun totalHeightError(geom: CollageGeometry): String? {
    val hExact = if (geom.rows.isEmpty()) 0f else geom.rows.last().y + geom.rows.last().height
    return if (abs(hExact - geom.height) > 1e-2f) {
        "total height mismatch: geom.height=${fmt(geom.height)} vs lastY+H=${fmt(hExact)}"
    } else {
        null
    }
}

private fun rowWidthFillDriftError(
    rowIdx: Int,
    row: RowGeometry,
    width: Float,
    paddings: Float,
): String? {
    val n = row.tiles.size
    if (n == 0) return null
    val sumW = row.tiles.sumOf { it.boxW.toDouble() } + paddings * max(0, n - 1)
    val drift = abs(sumW - width)
    return if (drift > 1.2) {
        "row#$rowIdx: width fill drift=${fmt(drift)} px"
    } else {
        null
    }
}

private fun rowSpikeAndMinWidthErrors(
    rowIdx: Int,
    row: RowGeometry,
    width: Float,
    paddings: Float,
    minItemWidth: Float,
    heur: CollageTuning.HeuristicsConfig,
): List<String> {
    val n = row.tiles.size
    if (n == 0) return emptyList()

    val target = width - paddings * max(0, n - 1)
    val cell = target / n
    val beta = heur.softRelMinFrac(n)
    val ultra = heur.ultraNarrowFrac(n)
    val minAbs = max(minItemWidth, beta * cell)
    val ultraAbs = max(minItemWidth, ultra * cell)

    val cntNearMin = row.tiles.count { it.boxW < minAbs + 1e-3f }
    val cntUltra = row.tiles.count { it.boxW < ultraAbs - 1e-3f }
    val minViol = row.tiles.count { it.boxW < minItemWidth - 1e-3f }

    val out = mutableListOf<String>()
    if ((n >= 4 && cntNearMin >= 3) || (n == 3 && cntNearMin >= 2)) {
        out += "row#$rowIdx: spike cluster (<= ${fmt(minAbs)}) cnt=$cntNearMin of $n"
    }
    if (cntUltra >= 1) {
        out += "row#$rowIdx: ultra-narrow (<= ${fmt(ultraAbs)}) present"
    }
    if (minViol > 0) {
        out += "row#$rowIdx: minWidth violations=$minViol (min=${fmt(minItemWidth)})"
    }
    return out
}

private fun rowVerticalSquashError(
    rowIdx: Int,
    row: RowGeometry,
    width: Float,
    tauV: Float,
    photoById: Map<Int, Photo>,
): String? {
    val n = row.tiles.size
    if (n == 0) return null
    val vCnt = row.tiles.count {
        val p = photoById[it.imageId] ?: error("can't find element by ${it.imageId}")
        MathUtil.aspect(p.width, p.height) <= tauV
    }
    if (vCnt * 2 >= n) {
        val guardH = CollageTuning.default.dynamicProgrammingConfig.verticalSquashGuardFracOfWidth * width
        if (row.height + 1e-3f < guardH) {
            return "row#$rowIdx: vertical-squash h=${fmt(row.height)} < guard=${fmt(guardH)}"
        }
    }
    return null
}

private fun rowWorstCropError(
    rowIdx: Int,
    row: RowGeometry,
): String? {
    var worstCrop = 0.0
    row.tiles.forEach { t ->
        if (t.fit == TileFit.COVER) {
            worstCrop = max(worstCrop, t.cropRatio.toDouble())
        }
    }
    return if (worstCrop > 0.75) {
        "row#$rowIdx: suspicious worst crop=${fmt(worstCrop, 2)}"
    } else {
        null
    }
}

private fun checkGeometry(
    cfg: EngineConfig,
    case: CaseCfg,
    geom: CollageGeometry,
): List<String> {
    val errs = mutableListOf<String>()
    totalHeightError(geom)?.let { errs += it }

    val width = geom.width
    val paddings = cfg.paddings
    val minItemWidth = cfg.minItemWidth
    val photoById = case.photos.associateBy { it.imageId }

    val tauH = CollageTuning.default.dynamicProgrammingConfig.tauHorizontal
    val tauV = 1f / tauH
    val heur = CollageTuning.default.heuristics

    geom.rows.forEachIndexed { rowIdx, row ->
        rowWidthFillDriftError(rowIdx, row, width, paddings)?.let { errs += it }
        errs += rowSpikeAndMinWidthErrors(rowIdx, row, width, paddings, minItemWidth, heur)
        rowVerticalSquashError(rowIdx, row, width, tauV, photoById)?.let { errs += it }
        rowWorstCropError(rowIdx, row)?.let { errs += it }
    }

    return errs
}

private fun runSuite(
    suiteName: String,
    cfg: EngineConfig,
    cases: List<CaseCfg>,
) {
    val eng = TestKit.engine(cfg)

    val failures = mutableListOf<String>()
    for (c in cases) {
        val geom = eng.arrangeWithGeometry(c.photos)
        val errs = checkGeometry(cfg, c, geom)
        if (errs.isNotEmpty()) {
            failures += buildString {
                append("Bad ").append(suiteName).append(" / ").append(c.tag).append('\n')
                append(" photos=").append(c.photos.size)
                    .append(" width=").append(fmt(geom.width))
                    .append(" rows=").append(geom.rows.size)
                    .append(" H=").append(fmt(geom.height)).append('\n')
                errs.forEach { e -> append("   • ").append(e).append('\n') }
            }
        } else {
            println("OK $suiteName / ${c.tag}  photos=${c.photos.size}  rows=${geom.rows.size}  H=${fmt(geom.height)}")
        }
    }

    if (failures.isNotEmpty()) {
        println("\n===== FAILURES (${failures.size}) =====")
        failures.forEach { println(it) }
    }
    assertTrue("Есть неуспешные кейсы: ${failures.size}", failures.isEmpty())
}

class CollageQualityTest {

    @Test
    fun fixed_datasets_smoke() {
        val cfg = EngineConfig(
            minCollageSize = SizeAttrs(240f, 360f),
            maxCollageSize = SizeAttrs(240f, 360f),
            paddings = 6f,
            minItemWidth = 56f,
            minItemHeight = 56f,
            rowsSearchSpan = 2
        )

        val cases = buildList {
            add(CaseCfg("1_sq", TestKit.gridSquare(1, 1000f)))
            add(CaseCfg("2_sq", TestKit.gridSquare(2, 1000f)))
            add(CaseCfg("3_sq", TestKit.gridSquare(3, 1000f)))
            add(CaseCfg("4_sq", TestKit.gridSquare(4, 1000f)))
            add(CaseCfg("12_sq", TestKit.gridSquare(12, 1000f)))

            add(CaseCfg("allH_12", TestKit.allH(12)))
            add(CaseCfg("allV_12", TestKit.allV(12)))
            add(CaseCfg("allS_12", TestKit.allS(12)))
            add(CaseCfg("altHV_12", TestKit.alternatingHV(12)))

            add(CaseCfg("mix_6_s1", TestKit.randomMixed(6, seed = 1L)))
            add(CaseCfg("mix_9_s2", TestKit.randomMixed(9, seed = 2L)))
            add(CaseCfg("mix_12_s3", TestKit.randomMixed(12, seed = 3L)))

            add(CaseCfg("ultra_allH_12", TestKit.allUltraH(12)))
            add(CaseCfg("ultra_allV_12", TestKit.allUltraV(12)))
            add(CaseCfg("ultra_saw_10", TestKit.ultraSaw(10)))
            add(CaseCfg("ultra_mix12_s100", TestKit.randomMixedRich(12, seed = 100L, pUltra = 0.5f)))
        }

        runSuite("SMOKE", cfg, cases)
    }

    @Test
    fun property_randomized_1_to_12() {
        val cfg = EngineConfig(
            minCollageSize = SizeAttrs(240f, 360f),
            maxCollageSize = SizeAttrs(240f, 360f),
            paddings = 6f,
            minItemWidth = 56f,
            minItemHeight = 56f,
            rowsSearchSpan = 2
        )

        val cases = ArrayList<CaseCfg>()
        var idBase = 100_000
        for (n in 1..12) {
            for (seed in 0L..7L) {
                val photos = TestKit.randomMixed(n, seed = seed + n)
                val renum = photos.mapIndexed { i, p -> Photo(idBase + i, p.width, p.height) }
                idBase += n + 1
                cases += CaseCfg("rnd_n${n}_s$seed", renum)
            }
        }
        runSuite("RAND", cfg, cases)
    }

    @Test
    fun property_randomized_ultra_1_to_12() {
        val cfg = EngineConfig(
            minCollageSize = SizeAttrs(240f, 360f),
            maxCollageSize = SizeAttrs(240f, 360f),
            paddings = 6f,
            minItemWidth = 56f,
            minItemHeight = 56f,
            rowsSearchSpan = 2
        )

        val cases = ArrayList<CaseCfg>()
        var idBase = 200_000
        for (n in 1..12) {
            for (seed in 0L..7L) {
                val photos = TestKit.randomMixedRich(n, seed = seed + n * 13L, pUltra = 0.55f)
                val renum = photos.mapIndexed { i, p -> Photo(idBase + i, p.width, p.height) }
                idBase += n + 7
                cases += CaseCfg("ultra_n${n}_s$seed", renum)
            }
        }
        runSuite("RAND_ULTRA", cfg, cases)
    }
}
