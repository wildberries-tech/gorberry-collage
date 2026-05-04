package ru.wildberries.collage

import ru.wildberries.collage.core.CollageTuning
import ru.wildberries.collage.core.EngineConfig
import ru.wildberries.collage.core.MathUtil
import ru.wildberries.collage.model.CollageImage
import ru.wildberries.collage.model.SizeAttrs
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertTrue

class RowPlannerHeuristicsTest {

    @Test
    fun snap_respects_perAspectFloor_and_beta() {
        val cfg = EngineConfig(
            minCollageSize = SizeAttrs(360f, 360f),
            maxCollageSize = SizeAttrs(360f, 360f),
            spacing = 6f,
            minItemWidth = 56f,
            minItemHeight = 56f,
            maxItemsPerRow = 3
        )
        val eng = TestKit.engine(cfg)

        val collageImages = listOf(
            CollageImage(0, 1600f, 900f),
            CollageImage(1, 900f, 1600f),
            CollageImage(2, 1600f, 900f)
        )
        val geom = eng.arrangeWithGeometry(collageImages)
        val row = geom.rows.first()
        val n = row.tiles.size
        val target = geom.width - cfg.spacing * max(0, n - 1)
        val cell = if (n > 0) target / n else 1f

        val heur = CollageTuning.default.heuristics
        val plan = CollageTuning.default.planner
        val tauH = CollageTuning.default.dynamicProgrammingConfig.tauHorizontal
        val beta = heur.snapBeta(n)

        row.tiles.forEachIndexed { i, t ->
            val a = MathUtil.aspect(collageImages[i].width, collageImages[i].height)
            val perAspect = plan.perAspectFloor(a, tauH)
            val floorW = max(cfg.minItemWidth, max(beta * cell, perAspect * cell))
            assertTrue(
                t.boxW + 1e-3f >= floorW,
                "tile#$i boxW=${t.boxW} < floor=$floorW"
            )
        }
    }

    @Test
    fun matchstick_gamma_guard_for_3_tiles() {
        val cfg = EngineConfig(
            minCollageSize = SizeAttrs(320f, 360f),
            maxCollageSize = SizeAttrs(320f, 360f),
            spacing = 6f,
            minItemWidth = 56f,
            minItemHeight = 56f,
            maxItemsPerRow = 3
        )
        val eng = TestKit.engine(cfg)

        val collageImages = listOf(
            CollageImage(0, 900f, 1600f),
            CollageImage(1, 1600f, 900f),
            CollageImage(2, 1600f, 900f)
        )
        val geom = eng.arrangeWithGeometry(collageImages)
        val row = geom.rows.first()
        if (row.tiles.size == 3) {
            val ws = row.tiles.map { it.boxW }
            val mean = ws.average().toFloat().coerceAtLeast(1e-3f)
            val minW = ws.minOrNull() ?: mean
            val gamma = CollageTuning.default.planner.matchstickGamma(3)
            val ratio = minW / mean
            assertTrue(ratio + 1e-4f >= gamma, "min/mean=$ratio < gamma=$gamma")
        }
    }

    @Test
    fun hardAbsMinFrac_for_3_and_4_tiles() {
        // n=3
        run {
            val cfg = EngineConfig(
                minCollageSize = SizeAttrs(300f, 360f),
                maxCollageSize = SizeAttrs(300f, 360f),
                spacing = 6f,
                minItemWidth = 56f,
                minItemHeight = 56f,
                maxItemsPerRow = 3
            )
            val eng = TestKit.engine(cfg)
            val collageImages = listOf(
                CollageImage(0, 1600f, 900f),
                CollageImage(1, 900f, 1600f),
                CollageImage(2, 1600f, 900f)
            )
            val g = eng.arrangeWithGeometry(collageImages)
            val row = g.rows.first()
            if (row.tiles.size == 3) {
                val target = g.width - cfg.spacing * 2
                val thr = CollageTuning.default.heuristics.hardAbsMinFrac(3) * target
                val minW = row.tiles.minOf { it.boxW }
                assertTrue(minW + 1e-3f >= thr, "n=3 minW=$minW < thr=$thr")
            }
        }

        val cfg = EngineConfig(
            minCollageSize = SizeAttrs(320f, 360f),
            maxCollageSize = SizeAttrs(320f, 360f),
            spacing = 6f,
            minItemWidth = 56f,
            minItemHeight = 56f,
            maxItemsPerRow = 4
        )
        val eng = TestKit.engine(cfg)
        val collageImages = listOf(
            CollageImage(0, 1600f, 900f),
            CollageImage(1, 900f, 1600f),
            CollageImage(2, 1600f, 900f),
            CollageImage(3, 900f, 1600f)
        )
        val g = eng.arrangeWithGeometry(collageImages)
        val row = g.rows.firstOrNull { it.tiles.size == 4 } ?: return
        val target = g.width - cfg.spacing * 3
        val thr = CollageTuning.default.heuristics.hardAbsMinFrac(4) * target
        val minW = row.tiles.minOf { it.boxW }
        assertTrue(minW + 1e-3f >= thr, "n=4 minW=$minW < thr=$thr")
    }
}
