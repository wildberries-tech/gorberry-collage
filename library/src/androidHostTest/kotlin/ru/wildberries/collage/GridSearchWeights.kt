package ru.wildberries.collage

import ru.wildberries.collage.api.CollageEngine
import ru.wildberries.collage.api.EngineConfig
import ru.wildberries.collage.core.MathUtil
import ru.wildberries.collage.model.Photo
import ru.wildberries.collage.model.RectF
import ru.wildberries.collage.model.RowGeometry
import ru.wildberries.collage.model.SizeAttrs
import ru.wildberries.collage.strategy.DefaultTileScorer
import ru.wildberries.collage.strategy.FitWeights
import ru.wildberries.collage.strategy.RowLengthPriority
import ru.wildberries.collage.strategy.TileFit
import java.io.File
import java.util.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private const val PROGRESS_EVERY: Long = 500L

class GridSearchWeights {
    private data class RowEval(
        val spikes: Int,
        val minViol: Int,
        val tinyTiles: Int,
        val vSqueezed: Int,
        val coverCnt: Int,
        val tileCnt: Int,
        val worstCrop: Double,
        val aspectDriftSum: Double,
        val aspectDriftCnt: Int,
        val varPart: Double,
        val rhoAbs: Double,
        val fitnessClass: Int,
        val totalHInc: Float,
        val lossSumSelected: Double,
        val totalBoxArea: Double,
    )

    data class Metrics(
        val spikes: Int,
        val minWViol: Int,
        val rows: Int,
        val widthVar: Double,
        val heightUnder: Double,
        val coverShare: Double,
        val worstCrop: Double,
        val tiles: Int,
        val totalH: Double,
        val timeNs: Long,
        val lossSumSelected: Double,
        val lossPerMPx: Double,
        val vSqueezed: Int,
        val tinyTiles: Int,
        val aspectDriftMean: Double,
        val rhoAbsMean: Double,
        val rhoAbsP95: Double,
        val fitnessJumpsGt1: Int,
        val heightOver: Double,
    )

    private fun hms(ns: Long): String {
        val s = ns / 1_000_000_000.0
        val h = (s / 3600).toInt()
        val m = ((s - h * 3600) / 60).toInt()
        val sec = s - h * 3600 - m * 60
        return "%02dh:%02dm:%04.1fs".format(Locale.US, h, m, sec)
    }

    private fun f2(v: Double) = String.format(Locale.US, "%.2f", v)
    private fun f3(v: Double) = String.format(Locale.US, "%.3f", v)
    private fun f4(v: Double) = String.format(Locale.US, "%.4f", v)
    private fun f5(v: Double) = String.format(Locale.US, "%.5f", v)
    private fun f1(v: Double) = String.format(Locale.US, "%.1f", v)

    private fun datasets(): List<Pair<String, List<Photo>>> = buildList {
        add("V4" to listOf(p(0, 900f, 1600f), p(1, 900f, 1600f), p(2, 900f, 1600f), p(3, 900f, 1600f)))
        add("HVHV" to sample4_HVHV_spikes())
        add("Mixed5" to mixedSample1())
        add("Sq8" to gridSquare(8, 1000f))
        add("Thin10" to randomThinHeavy(10, seed = 7L))
        add("Long24" to longMixed(24, seed = 5L))
        add("AllH12" to allH(12))
        add("AllV12" to allV(12))
        add("AllS12" to allS(12))
        add("AltHV12" to alternatingHV(12))
        add("NearMinV4" to nearMinWidthVerticals(4, base = 1000f, targetAR = 0.5f))
        add("WideThin" to adversarialWideAndThin(nThin = 4))
        add("QuasiSq16" to quasiSquares(16, base = 1000f, delta = 0.08f))
        add("StairBlocks" to staircaseBlocks(repeats = 3, blockH = 3, blockV = 3, blockS = 2, base = 1000f))
        add("RectH8" to (0 until 8).map { i -> p(i, 2000f, 700f) })
        add("RectV8" to (0 until 8).map { i -> p(i, 700f, 2000f) })
        add("MixAR15" to randomMixed(n = 15, seed = 777L, minSide = 700f, maxSide = 1600f))
        add("RandomMixed20" to randomMixed(n = 20, seed = 42L))
        add("RandomThin20" to randomThinHeavy(n = 20, seed = 43L, vShare = 0.8f))
        add("V12Thin" to (0 until 12).map { i -> p(10_000 + i, 800f, 1600f) })
        add("V8Ultra" to (0 until 8).map { i -> p(20_000 + i, 700f, 2100f) })
        add(
            "MixHeavyV12" to buildList {
                repeat(8) { i ->
                    add(
                        p(30_000 + i, 900f, 1600f)
                    )
                }
                add(
                    p(30_100, 1000f, 1000f)
                )
                add(
                    p(30_101, 1000f, 1000f)
                )
                add(
                    p(30_200, 1600f, 900f)
                )
                add(
                    p(30_201, 1600f, 900f)
                )
            },
        )
        add(
            "VxS_14" to buildList {
                var id = 40_000
                repeat(7) {
                    add(
                        p(id++, 800f, 1600f)
                    )
                }
                repeat(7) {
                    add(
                        p(id++, 1000f, 1000f)
                    )
                }
            }
        )
    }

    private fun fitnessClass(rho: Float) = when {
        rho < -0.5f -> 0
        rho < -0.1f -> 1
        rho <= 0.1f -> 2
        rho <= 0.5f -> 3
        else -> 4
    }

    private fun naturalRowHeight(
        aspects: FloatArray,
        collageWidth: Float,
        gapX: Float,
        minW: Float,
        minH: Float,
    ): Float {
        val n = aspects.size
        val totalGaps = gapX * max(0, n - 1)
        val available = (collageWidth - totalGaps).coerceAtLeast(1f)
        fun sumAt(h: Float): Float {
            var s = 0f
            var i = 0
            while (i < n) {
                val w = h * aspects[i]
                s += if (w >= minW) w else minW
                i++
            }
            return s
        }

        var sumA = 0f
        for (a in aspects) sumA += a
        sumA = sumA.coerceAtLeast(1e-3f)
        var hi = max(minH, available / sumA)
        if (sumAt(hi) < available) {
            var h = hi
            repeat(8) {
                if (sumAt(h) >= available) return@repeat
                h *= 1.8f
            }
            hi = h
        }
        val eps = 0.5f
        var it = 0
        var loV = minH
        var hiV = hi
        while (hiV - loV > eps && it < 24) {
            val mid = 0.5f * (loV + hiV)
            if (sumAt(mid) >= available) hiV = mid else loV = mid
            it++
        }
        return hiV.coerceAtLeast(minH)
    }

    private fun gammaForRowLen(n: Int): Float = when {
        n >= 5 -> 0.26f
        n == 4 -> 0.24f
        n == 3 -> 0.28f
        else -> 0f
    }

    private data class TileAcc(
        val spikes: Int,
        val minViol: Int,
        val tinyTiles: Int,
        val vSqueezed: Int,
        val coverCnt: Int,
        val tileCnt: Int,
        val worstCrop: Double,
        val aspectDriftSum: Double,
        val aspectDriftCnt: Int,
        val sum: Double,
        val sum2: Double,
        val lossSumSel: Double,
        val totalBoxArea: Double,
    )

    private fun accumulateTiles(
        row: RowGeometry,
        photosById: Map<Int, Photo>,
        minW: Float,
        minAbs: Float,
        cell: Float,
        scorer: DefaultTileScorer,
    ): TileAcc {
        val tauV = 1f / 1.05f
        val vRelMin = 0.34f

        var spikes = 0
        var minViol = 0
        var tinyTiles = 0
        var vSqueezed = 0
        var coverCnt = 0
        var tileCnt = 0
        var worstCrop = 0.0
        var aspectDriftSum = 0.0
        var aspectDriftCnt = 0
        var sum = 0.0
        var sum2 = 0.0
        var lossSumSel = 0.0
        var totalBoxArea = 0.0

        for (t in row.tiles) {
            if (t.boxW < minW - 1e-3f) minViol++
            if (t.boxW <= minW + 1e-3f) tinyTiles++
            if (t.boxW <= minAbs + 1e-3f) spikes++

            val img = photosById[t.imageId] ?: error("not found by ${t.imageId}")
            val aImg = MathUtil.aspect(img.width, img.height)
            val aBox = MathUtil.aspect(t.boxW, t.boxH)
            aspectDriftSum += abs((aBox / aImg).toDouble() - 1.0)
            aspectDriftCnt++

            if (aImg <= tauV) {
                val relW = t.boxW / cell
                if (relW < vRelMin) vSqueezed++
            }

            val rel = (t.boxW / cell).toDouble()
            sum += rel
            sum2 += rel * rel
            tileCnt++

            val d = scorer.decide(img, RectF(x = 0f, y = 0f, w = t.boxW, h = t.boxH))
            lossSumSel += if (d.useCover) d.cover.toDouble() else d.contain.toDouble()
            totalBoxArea += (t.boxW * t.boxH).toDouble()

            if (t.fit == TileFit.COVER) {
                coverCnt++
                if (t.cropRatio > worstCrop) worstCrop = t.cropRatio.toDouble()
            }
        }
        return TileAcc(
            spikes, minViol, tinyTiles, vSqueezed, coverCnt, tileCnt, worstCrop,
            aspectDriftSum, aspectDriftCnt, sum, sum2, lossSumSel, totalBoxArea
        )
    }

    private fun evalRow(
        row: RowGeometry,
        photosById: Map<Int, Photo>,
        targetW: Float,
        paddings: Float,
        minW: Float,
        minH: Float,
        capAvg: Float,
        scorer: DefaultTileScorer,
    ): RowEval {
        val n = row.tiles.size
        if (n == 0) {
            return RowEval(
                spikes = 0,
                minViol = 0,
                tinyTiles = 0,
                vSqueezed = 0,
                coverCnt = 0,
                tileCnt = 0,
                worstCrop = 0.0,
                aspectDriftSum = 0.0,
                aspectDriftCnt = 0,
                varPart = 0.0,
                rhoAbs = 0.0,
                fitnessClass = fitnessClass(0f),
                totalHInc = row.height,
                lossSumSelected = 0.0,
                totalBoxArea = 0.0
            )
        }

        val cell = (targetW - paddings * max(0, n - 1)) / n
        val gamma = gammaForRowLen(n)
        val minAbs = max(minW, gamma * cell)

        val aspectsRow = FloatArray(n) { k ->
            val img = photosById[row.tiles[k].imageId] ?: error("not found by ${row.tiles[k].imageId}")
            MathUtil.aspect(img.width, img.height)
        }
        val hNat = naturalRowHeight(
            aspects = aspectsRow,
            collageWidth = targetW,
            gapX = paddings,
            minW = minW,
            minH = minH
        )
        val hHint = min(hNat, capAvg)

        val rhoAbs = if (hHint > 1e-3f && row.height > 1e-3f) {
            abs(((row.height - hHint) / hHint).toDouble())
        } else {
            0.0
        }
        val fitCls = fitnessClass(rhoAbs.toFloat())

        val acc = accumulateTiles(row, photosById, minW, minAbs, cell, scorer)

        val mean = acc.sum / n
        val vPart = acc.sum2 / n - mean * mean

        return RowEval(
            spikes = acc.spikes,
            minViol = acc.minViol,
            tinyTiles = acc.tinyTiles,
            vSqueezed = acc.vSqueezed,
            coverCnt = acc.coverCnt,
            tileCnt = acc.tileCnt,
            worstCrop = acc.worstCrop,
            aspectDriftSum = acc.aspectDriftSum,
            aspectDriftCnt = acc.aspectDriftCnt,
            varPart = vPart,
            rhoAbs = rhoAbs,
            fitnessClass = fitCls,
            totalHInc = row.height,
            lossSumSelected = acc.lossSumSel,
            totalBoxArea = acc.totalBoxArea
        )
    }

    private fun evalOne(
        eng: CollageEngine,
        photos: List<Photo>,
        targetW: Float,
        targetH: Float,
        minW: Float,
        minH: Float,
        paddings: Float,
        weights: FitWeights,
    ): Metrics {
        val t0 = System.nanoTime()
        val geo = eng.arrangeWithGeometry(photos)
        val ns = System.nanoTime() - t0
        var vSqueezed = 0
        var tinyTiles = 0
        var aspectDriftSum = 0.0
        var aspectDriftCnt = 0
        var spikes = 0
        var minViol = 0
        val rows = geo.rows.size
        var varAccum = 0.0
        var varDen = 0
        var totalH = 0f
        var coverCnt = 0
        var tileCnt = 0
        var worstCrop = 0.0
        var totalBoxArea = 0.0
        var lossSumSel = 0.0

        val rhoAbs = ArrayList<Double>(max(1, rows))
        val fitClasses = ArrayList<Int>(max(1, rows))
        val scorer = DefaultTileScorer(weights)
        val photosById = photos.associateBy { it.imageId }

        val capAvg = run {
            val rowsPos = max(1, rows)
            val gapsY = paddings * max(0, rowsPos - 1)
            val remain = (targetH - gapsY).coerceAtLeast(minH)
            (remain / rowsPos).coerceAtLeast(minH)
        }

        for (row in geo.rows) {
            val r = evalRow(row, photosById, targetW, paddings, minW, minH, capAvg, scorer)

            totalH += r.totalHInc
            spikes += r.spikes
            minViol += r.minViol
            tinyTiles += r.tinyTiles
            vSqueezed += r.vSqueezed
            coverCnt += r.coverCnt
            tileCnt += r.tileCnt
            if (r.worstCrop > worstCrop) worstCrop = r.worstCrop
            aspectDriftSum += r.aspectDriftSum
            aspectDriftCnt += r.aspectDriftCnt
            varAccum += r.varPart
            varDen++
            rhoAbs += r.rhoAbs
            fitClasses += r.fitnessClass
            lossSumSel += r.lossSumSelected
            totalBoxArea += r.totalBoxArea
        }

        val widthVar = if (varDen > 0) varAccum / varDen else 0.0
        val heightUnder = max(0.0, (targetH - totalH).toDouble())
        val heightOver = max(0.0, (totalH - targetH).toDouble())
        val coverShare = if (tileCnt > 0) coverCnt.toDouble() / tileCnt else 0.0
        val lossPerMPx = if (totalBoxArea > 0.0) lossSumSel / (totalBoxArea / 1_000_000.0) else 0.0

        rhoAbs.sort()
        val rhoMean = if (rows > 0) rhoAbs.sum() / rows else 0.0
        val rhoP95 = if (rhoAbs.isNotEmpty()) {
            val idx = ((rhoAbs.size - 1) * 0.95).toInt().coerceIn(0, rhoAbs.size - 1)
            rhoAbs[idx]
        } else {
            0.0
        }

        var jumps = 0
        var i = 0
        while (i + 1 < fitClasses.size) {
            if (abs(fitClasses[i] - fitClasses[i + 1]) > 1) jumps++
            i++
        }

        return Metrics(
            spikes = spikes,
            minWViol = minViol,
            rows = rows,
            widthVar = widthVar,
            heightUnder = heightUnder,
            coverShare = coverShare,
            worstCrop = worstCrop,
            tiles = tileCnt,
            totalH = totalH.toDouble(),
            timeNs = ns,
            lossSumSelected = lossSumSel,
            lossPerMPx = lossPerMPx,
            vSqueezed = vSqueezed,
            tinyTiles = tinyTiles,
            aspectDriftMean = if (aspectDriftCnt > 0) aspectDriftSum / aspectDriftCnt else 0.0,
            rhoAbsMean = rhoMean,
            rhoAbsP95 = rhoP95,
            fitnessJumpsGt1 = jumps,
            heightOver = heightOver
        )
    }

    private fun sceneScoreClassic(m: Metrics): Double =
        10000.0 * m.spikes +
            1000.0 * m.minWViol +
            50.0 * m.widthVar +
            10.0 * m.heightUnder +
            10.0 * max(0, m.rows - 6)

    private fun evalAll(
        eng: CollageEngine,
        scenes: List<Pair<String, List<Photo>>>,
        targetW: Float,
        targetH: Float,
        minW: Float,
        minH: Float,
        paddings: Float,
        weights: FitWeights,
    ): Triple<Double, Double, Map<String, Metrics>> {
        var scoreClassicTotal = 0.0
        var lossSumSelTotal = 0.0
        val per = LinkedHashMap<String, Metrics>(scenes.size)
        for ((name, photos) in scenes) {
            val m = evalOne(eng, photos, targetW, targetH, minW, minH, paddings, weights)
            per[name] = m
            scoreClassicTotal += sceneScoreClassic(m)
            lossSumSelTotal += m.lossSumSelected
        }
        return Triple(scoreClassicTotal, lossSumSelTotal, per)
    }

    fun gridAllParamsAndWriteCsv() {
        Locale.setDefault(Locale.US)
        val env = initEnv()
        val grids = buildGrids()
        runGrid(env, grids)
    }

    private data class Env(
        val outDir: File,
        val outFileAll: File,
        val outFileSummary: File,
        val scenes: List<Pair<String, List<Photo>>>,
        val baseCfg: EngineConfig,
        val targetW: Float,
        val targetH: Float,
        val minW: Float,
        val minH: Float,
        val paddings: Float,
    )

    private class Grids(
        val gPairs: Array<Pair<Float, Float>>,
        val stickAGrid: DoubleArray,
        val rowLenGrid: Array<RowLengthPriority>,
        val equalizeAlphaGrid: DoubleArray,
        val fourMixPenaltyGrid: DoubleArray,
        val rowsSearchSpanGrid: IntArray,
        val lambdaCropGrid: FloatArray,
        val lambdaGapGrid: FloatArray,
        val cropPowGrid: FloatArray,
        val gapPowGrid: FloatArray,
        val hardGrid: FloatArray,
        val mmAmpGrid: FloatArray,
    )

    private data class Acc(var ns: Long = 0L, var cnt: Long = 0)

    private fun initEnv(): Env {
        val outDir = File("build/reports/collage-grid").apply { mkdirs() }
        val outFileAll = outDir.resolve("tune_all.csv")
        val outFileSummary = outDir.resolve("tune_summary.csv")

        val scenes = datasets()
        val baseCfg = EngineConfig(
            minCollageSize = SizeAttrs(240f, 360f),
            maxCollageSize = SizeAttrs(240f, 360f),
            minItemWidth = 46f,
            minItemHeight = 46f,
            paddings = 2f,
            rowsSearchSpan = 2
        )
        val targetW = baseCfg.maxCollageSize?.width ?: 240f
        val targetH = baseCfg.maxCollageSize?.height ?: 360f
        val minW = baseCfg.minItemWidth
        val minH = baseCfg.minItemHeight
        val paddings = baseCfg.paddings

        return Env(outDir, outFileAll, outFileSummary, scenes, baseCfg, targetW, targetH, minW, minH, paddings)
    }

    private fun buildGrids(): Grids {
        val gPairs = arrayOf(0.30f to 0.26f, 0.32f to 0.28f)
        val stickAGrid = doubleArrayOf(400_000.0, 800_000.0, 1_200_000.0, 1_600_000.0)
        val rowLenGrid = arrayOf(
            RowLengthPriority(enabled = false),
            RowLengthPriority(enabled = true, w1 = 0.030, w2 = 0.004, w3 = -0.015, w4 = 0.008)
        )

        val equalizeAlphaGrid = doubleArrayOf(0.0, 1500.0, 3000.0)
        val fourMixPenaltyGrid = doubleArrayOf(0.0, 200_000.0)
        val rowsSearchSpanGrid = intArrayOf(1, 2, 3)
        val lambdaCropGrid = floatArrayOf(2.8f, 3.6f, 4.6f)
        val lambdaGapGrid = floatArrayOf(0.08f, 0.14f, 0.22f)
        val cropPowGrid = floatArrayOf(1.3f, 1.7f, 2.2f)
        val gapPowGrid = floatArrayOf(1.10f, 1.30f, 1.50f)
        val hardGrid = floatArrayOf(0.55f, 0.62f)
        val mmAmpGrid = floatArrayOf(0.20f, 0.50f)

        return Grids(
            gPairs, stickAGrid, rowLenGrid, equalizeAlphaGrid, fourMixPenaltyGrid, rowsSearchSpanGrid,
            lambdaCropGrid, lambdaGapGrid, cropPowGrid, gapPowGrid, hardGrid, mmAmpGrid
        )
    }

    private inline fun iterateCombos(
        grids: Grids,
        crossinline body: (
            g3: Float,
            g4: Float,
            sa: Double,
            rowLen: RowLengthPriority,
            eqA: Double,
            fourMix: Double,
            rss: Int,
            lc: Float,
            lg: Float,
            cp: Float,
            gp: Float,
            hc: Float,
            mma: Float,
        ) -> Unit,
    ) {
        val dims = intArrayOf(
            grids.gPairs.size,
            grids.stickAGrid.size,
            grids.rowLenGrid.size,
            grids.equalizeAlphaGrid.size,
            grids.fourMixPenaltyGrid.size,
            grids.rowsSearchSpanGrid.size,
            grids.lambdaCropGrid.size,
            grids.lambdaGapGrid.size,
            grids.cropPowGrid.size,
            grids.gapPowGrid.size,
            grids.hardGrid.size,
            grids.mmAmpGrid.size
        )
        val idx = IntArray(dims.size) { 0 }
        while (true) {
            val (g3, g4) = grids.gPairs[idx[0]]
            body(
                g3, g4,
                grids.stickAGrid[idx[1]],
                grids.rowLenGrid[idx[2]],
                grids.equalizeAlphaGrid[idx[3]],
                grids.fourMixPenaltyGrid[idx[4]],
                grids.rowsSearchSpanGrid[idx[5]],
                grids.lambdaCropGrid[idx[6]],
                grids.lambdaGapGrid[idx[7]],
                grids.cropPowGrid[idx[8]],
                grids.gapPowGrid[idx[9]],
                grids.hardGrid[idx[10]],
                grids.mmAmpGrid[idx[11]],
            )
            var k = dims.lastIndex
            while (k >= 0) {
                idx[k]++
                if (idx[k] < dims[k]) break
                idx[k] = 0
                k--
            }
            if (k < 0) break
        }
    }

    private fun printHeaders(outAll: java.io.PrintWriter, outSum: java.io.PrintWriter) {
        outAll.println(
            "g3,g4,stickA,rowLenOn,rowW1,rowW2,rowW3,rowW4,eqAlpha,fourMix,lambdaCrop,lambdaGap,cropPow," +
                "gapPow,extremeCropHard,mismatchAmplify,rowsSearchSpan," +
                "scoreTotalClassic,scoreSceneClassic,lossSumSelected,lossPerMPx,vSqueezed," +
                "tinyTiles,aspectDriftMean,rhoAbsMean,rhoAbsP95,fitnessJumpsGt1,heightUnder,heightOver," +
                "scene,rows,tiles,widthVar,coverShare,worstCrop,totalH,timeNs"
        )
        outSum.println(
            "g3,g4,stickA,rowLenOn,rowW1,rowW2,rowW3,rowW4,eqAlpha,fourMix,lambdaCrop," +
                "lambdaGap,cropPow,gapPow,extremeCropHard,mismatchAmplify,rowsSearchSpan," +
                "scoreTotalClassic,lossSumSelectedTotal,avgRows,emptyScenes," +
                "avgWidthVar,avgCoverShare,avgWorstCrop,avgLossPerMPx,avgVSqueezed," +
                "avgTinyTiles,avgAspectDrift,avgRhoAbsMean,avgRhoAbsP95,totalFitnessJumps,avgHeightUnder,avgHeightOver"
        )
    }

    private fun reportProgressIfNeeded(
        done: Long,
        total: Long,
        accNs: Long,
        accCnt: Long,
        tStart: Long,
    ): Pair<Long, Long> {
        val tick = (done % PROGRESS_EVERY == 0L) || (done == total)
        if (!tick) return accNs to accCnt
        val elapsed = System.nanoTime() - tStart
        val avgPer = if (accCnt > 0) accNs / accCnt else 0L
        val remain = total - done
        val eta = if (avgPer > 0) avgPer * remain else 0L
        val pct = 100.0 * done / total
        println(
            "Grid progress: $done / $total (${f2(pct)}%), avg=${f2(avgPer / 1e6)} ms, " +
                "elapsed=${hms(elapsed)}, ETA=${hms(eta)}"
        )
        return 0L to 0L
    }

    private fun runGrid(env: Env, grids: Grids) {
        val totalCombos =
            grids.gPairs.size.toLong() *
                grids.stickAGrid.size * grids.rowLenGrid.size *
                grids.equalizeAlphaGrid.size * grids.fourMixPenaltyGrid.size *
                grids.lambdaCropGrid.size * grids.lambdaGapGrid.size * grids.cropPowGrid.size *
                grids.gapPowGrid.size * grids.hardGrid.size * grids.mmAmpGrid.size *
                grids.rowsSearchSpanGrid.size

        env.outFileAll.printWriter().use { outAll ->
            env.outFileSummary.printWriter().use { outSum ->
                printHeaders(outAll, outSum)

                var done: Long = 0
                val tStart = System.nanoTime()
                val acc = Acc()

                iterateCombos(grids) { g3, g4, sa, rowLen, eqA, fourMix, rss, lc, lg, cp, gp, hc, mma ->
                    val weights = FitWeights(
                        lambdaCrop = lc,
                        lambdaGap = lg,
                        cropPow = cp,
                        gapPow = gp,
                        extremeCropHard = hc,
                        mismatchAmplify = mma
                    )
                    val cfg = env.baseCfg.copy(rowLengthPriority = rowLen, rowsSearchSpan = rss)
                    val eng = TestKit.engineAug(
                        cfg = cfg,
                        weights = weights,
                        logger = TestLogger(),
                        stickGamma3 = g3,
                        stickGamma4 = g4,
                        stickPenaltyAlpha = sa,
                        fourMixPenalty = fourMix,
                        equalizePerRowAlpha = eqA
                    )

                    val t0 = System.nanoTime()
                    val (scoreTotalClassic, lossSumSelTotal, map) =
                        evalAll(
                            eng = eng,
                            scenes = env.scenes,
                            targetW = env.targetW,
                            targetH = env.targetH,
                            minW = env.minW,
                            minH = env.minH,
                            paddings = env.paddings,
                            weights = weights
                        )
                    val dt = System.nanoTime() - t0
                    done++
                    acc.ns += dt
                    acc.cnt++

                    val sumScenesClassic = map.values.sumOf { sceneScoreClassic(it) }
                    check(abs(sumScenesClassic - scoreTotalClassic) < 1e-6) {
                        "scoreTotalClassic != Σ(scene): ${f2(scoreTotalClassic)} vs ${f2(sumScenesClassic)}"
                    }

                    var vSqueezedAccum = 0.0
                    var tinyTilesAccum = 0.0
                    var aspectDriftAccum = 0.0
                    var rowsAccum = 0.0
                    var emptyScenes = 0
                    var widthVarAccum = 0.0
                    var coverShareAccum = 0.0
                    var worstCropAccum = 0.0
                    var lossPerMPxAccum = 0.0
                    var rhoAbsMeanAccum = 0.0
                    var rhoAbsP95Accum = 0.0
                    var fitnessJumpsTotal = 0
                    var heightUnderAccum = 0.0
                    var heightOverAccum = 0.0

                    for ((sceneName, m) in map) {
                        val scoreSceneClassic = sceneScoreClassic(m)
                        outAll.println(
                            "$g3,$g4,${f2(sa)}," +
                                "${rowLen.enabled},${f3(rowLen.w1)},${f3(rowLen.w2)},${f3(rowLen.w3)},${f3(rowLen.w4)}," +
                                "${f2(eqA)},${f2(fourMix)}," +
                                "$lc,$lg,$cp,$gp,$hc,$mma,$rss," +
                                "${f2(scoreTotalClassic)},${f2(scoreSceneClassic)},${f2(m.lossSumSelected)},${f2(m.lossPerMPx)}," +
                                "${m.vSqueezed},${m.tinyTiles},${f3(m.aspectDriftMean)}," +
                                "${f4(m.rhoAbsMean)},${f4(m.rhoAbsP95)},${m.fitnessJumpsGt1}," +
                                "${f2(m.heightUnder)},${f2(m.heightOver)}," +
                                "$sceneName,${m.rows},${m.tiles},${f5(m.widthVar)},${f3(m.coverShare)},${f3(m.worstCrop)}," +
                                "${f1(m.totalH)},$dt"
                        )

                        rowsAccum += m.rows
                        if (m.rows == 0) emptyScenes++
                        widthVarAccum += m.widthVar
                        coverShareAccum += m.coverShare
                        worstCropAccum += m.worstCrop
                        lossPerMPxAccum += m.lossPerMPx
                        vSqueezedAccum += m.vSqueezed
                        tinyTilesAccum += m.tinyTiles
                        aspectDriftAccum += m.aspectDriftMean
                        rhoAbsMeanAccum += m.rhoAbsMean
                        rhoAbsP95Accum += m.rhoAbsP95
                        fitnessJumpsTotal += m.fitnessJumpsGt1
                        heightUnderAccum += m.heightUnder
                        heightOverAccum += m.heightOver
                    }
                    outAll.flush()

                    val avgRows = rowsAccum / map.size
                    val avgWidthVar = widthVarAccum / map.size
                    val avgCoverShare = coverShareAccum / map.size
                    val avgWorstCrop = worstCropAccum / map.size
                    val avgLossPerMPx = lossPerMPxAccum / map.size
                    val avgVSqueezed = vSqueezedAccum / map.size
                    val avgTinyTiles = tinyTilesAccum / map.size
                    val avgAspectDrift = aspectDriftAccum / map.size
                    val avgRhoAbsMean = rhoAbsMeanAccum / map.size
                    val avgRhoAbsP95 = rhoAbsP95Accum / map.size
                    val avgHeightUnder = heightUnderAccum / map.size
                    val avgHeightOver = heightOverAccum / map.size

                    outSum.println(
                        "$g3,$g4,${f2(sa)}," +
                            "${rowLen.enabled},${f3(rowLen.w1)},${f3(rowLen.w2)},${f3(rowLen.w3)},${f3(rowLen.w4)}," +
                            "${f2(eqA)},${f2(fourMix)}," +
                            "$lc,$lg,$cp,$gp,$hc,$mma,$rss," +
                            "${f2(scoreTotalClassic)},${f2(lossSumSelTotal)}," +
                            "${f2(avgRows)},$emptyScenes," +
                            "${f5(avgWidthVar)}," +
                            "${f3(avgCoverShare)}," +
                            "${f3(avgWorstCrop)}," +
                            "${f2(avgLossPerMPx)}," +
                            "${f2(avgVSqueezed)}," +
                            "${f2(avgTinyTiles)}," +
                            "${f3(avgAspectDrift)}," +
                            "${f4(avgRhoAbsMean)}," +
                            "${f4(avgRhoAbsP95)}," +
                            "$fitnessJumpsTotal," +
                            "${f2(avgHeightUnder)}," +
                            f2(avgHeightOver)
                    )
                    outSum.flush()

                    val (accNsNew, accCntNew) = reportProgressIfNeeded(
                        done = done,
                        total = totalCombos,
                        accNs = acc.ns,
                        accCnt = acc.cnt,
                        tStart = tStart
                    )
                    acc.ns = accNsNew
                    acc.cnt = accCntNew
                }
            }
        }
    }
}

object GridSearchWeightsRunner {

    @JvmStatic
    fun main(args: Array<String>) {
        GridSearchWeights().gridAllParamsAndWriteCsv()
    }
}
