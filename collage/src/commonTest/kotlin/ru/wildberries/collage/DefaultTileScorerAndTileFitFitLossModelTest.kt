package ru.wildberries.collage

import ru.wildberries.collage.core.CollageTuning
import ru.wildberries.collage.core.TileFitLossModel
import ru.wildberries.collage.model.CollageImage
import ru.wildberries.collage.model.RectF
import ru.wildberries.collage.strategy.DefaultTileFitScorer
import ru.wildberries.collage.strategy.TileFitScoringWeights
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DefaultTileScorerAndTileFitFitLossModelTest {

    @Test
    fun coverCropRatio_zero_when_aspects_match_and_exact_fit() {
        val p = CollageImage(1, 1200f, 800f)
        val box = RectF(
            x = 0f,
            y = 0f,
            w = 300f,
            h = 200f
        )
        val crop = TileFitLossModel.coverCropRatio(p, box)
        assertEquals(0f, crop, 1e-6f)
    }

    @Test
    fun containGapRatio_zero_when_aspects_match_and_exact_fit() {
        val p = CollageImage(1, 1200f, 800f)
        val box = RectF(
            x = 0f,
            y = 0f,
            w = 300f,
            h = 200f,
        )
        val gap = TileFitLossModel.containGapRatio(p, box)
        assertEquals(0f, gap, 1e-6f)
    }

    @Test
    fun containGapRatio_positive_when_box_wider_than_image() {
        val p = CollageImage(1, 1000f, 1000f)
        val box = RectF(
            x = 0f,
            y = 0f,
            w = 500f,
            h = 200f
        )
        val gap = TileFitLossModel.containGapRatio(p, box)
        assertTrue(gap > 0f)
    }

    @Test
    fun scorer_prefers_cover_when_gap_is_expensive_and_crop_is_moderate() {
        val weights = TileFitScoringWeights(
            lambdaCrop = 1.0f,
            lambdaGap = 3.0f,
            cropCutoff = 0.70f,
            gapCutoff = 0.95f,
            cropPow = 1.4f,
            gapPow = 1.25f,
            extremeCropHard = 0.80f,
            extremeCropAlpha = 4.0f,
            mismatchAmplify = 0.30f
        )
        val scorer = DefaultTileFitScorer(weights, lut = CollageTuning.default.resources.powerLookupTable)

        val p = CollageImage(1, 1000f, 1000f)
        val box = RectF(
            x = 0f,
            y = 0f,
            w = 800f,
            h = 200f
        )
        val d = scorer.decide(p, box)
        assertTrue(d.useCover, "Ожидали COVER при дорогих полях")
    }

    @Test
    fun scorer_prefers_contain_when_crop_crosses_cutoff() {
        val weights = TileFitScoringWeights.Default.copy(cropCutoff = 0.20f)
        val scorer = DefaultTileFitScorer(weights, lut = CollageTuning.default.resources.powerLookupTable)

        val p = CollageImage(1, 1000f, 1000f)
        val box = RectF(
            x = 0f,
            y = 0f,
            w = 1000f,
            h = 200f
        )
        val d = scorer.decide(p, box)
        assertTrue(!d.useCover, "Должны уйти в CONTAIN при crop > cutoff")
    }
}
