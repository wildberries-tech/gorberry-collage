package ru.wildberries.collage

import ru.wildberries.collage.core.CollageTuning
import ru.wildberries.collage.core.EngineConfig
import ru.wildberries.collage.model.CollageImage
import ru.wildberries.collage.model.SizeAttrs
import ru.wildberries.collage.strategy.TileFitScoringWeights
import ru.wildberries.collage.strategy.TileFit
import kotlin.test.Test
import kotlin.test.assertTrue

class NarrowContainerGuardTest {

    @Test
    fun narrow_container_forces_contain_on_extreme_cover() {
        val cfg = EngineConfig(
            minCollageSize = SizeAttrs(240f, 360f),
            maxCollageSize = SizeAttrs(240f, 360f),
            spacing = 6f,
            minItemWidth = 56f,
            minItemHeight = 56f,
            maxItemsPerRow = 2
        )
        val coverFriendly = TileFitScoringWeights(
            lambdaCrop = 0.9f, lambdaGap = 0.30f,
            cropCutoff = 0.60f, gapCutoff = 0.92f,
            cropPow = 1.4f, gapPow = 1.25f,
            extremeCropHard = 0.80f, extremeCropAlpha = 4.0f,
            mismatchAmplify = 0.30f
        )
        val eng = TestKit.engine(cfg, coverFriendly)

        val collageImages = listOf(
            CollageImage(0, 5000f, 1000f),
            CollageImage(1, 1000f, 5000f)
        )
        val geom = eng.arrangeWithGeometry(collageImages)
        val heur = CollageTuning.default.heuristics

        geom.rows.forEach { row ->
            row.tiles.forEach { t ->
                if (t.fit == TileFit.COVER) {
                    assertTrue(
                        t.cropRatio < heur.cropFailAt - 1e-4f,
                        "Unexpected COVER with extreme crop=${t.cropRatio}"
                    )
                }
            }
        }
    }
}
