package ru.wildberries.collage.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.wildberries.collage.CollageEngine
import ru.wildberries.collage.SearchQuality
import ru.wildberries.collage.model.CollageImage
import ru.wildberries.collage.model.TileFitPolicy

@RunWith(AndroidJUnit4::class)
class CollageLayoutBenchmark {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    @Test
    fun layout_3_images_balanced_coverOnly() {
        benchmarkLayout(
            images = demoImages(3),
            width = 360f,
            maxHeight = 520f,
            searchQuality = SearchQuality.Balanced,
            tileFitPolicy = TileFitPolicy.CoverOnly,
        )
    }

    @Test
    fun layout_6_images_balanced_coverOnly() {
        benchmarkLayout(
            images = demoImages(6),
            width = 360f,
            maxHeight = 520f,
            searchQuality = SearchQuality.Balanced,
            tileFitPolicy = TileFitPolicy.CoverOnly,
        )
    }

    @Test
    fun layout_12_images_balanced_coverOnly() {
        benchmarkLayout(
            images = demoImages(12),
            width = 360f,
            maxHeight = 520f,
            searchQuality = SearchQuality.Balanced,
            tileFitPolicy = TileFitPolicy.CoverOnly,
        )
    }

    @Test
    fun layout_20_images_balanced_coverOnly_overflowAllowed() {
        val engine = CollageEngine {
            spacing = 6f
            minTileWidth = 42f
            minTileHeight = 42f
            maxTilesPerRow = 4
            maxLandscapeTilesPerRow = 2
            searchQuality = SearchQuality.Balanced
            tileFitPolicy = TileFitPolicy.CoverOnly
            allowHeightOverflow = true
        }

        val images = demoImages(20)

        benchmarkRule.measureRepeated {
            val layout = engine.layout(
                images = images,
                width = 360f,
                minHeight = 0f,
                maxHeight = 520f,
            )

            blackhole = layout.height
        }
    }

    @Test
    fun layout_12_images_fast_coverOnly() {
        benchmarkLayout(
            images = demoImages(12),
            width = 360f,
            maxHeight = 520f,
            searchQuality = SearchQuality.Fast,
            tileFitPolicy = TileFitPolicy.CoverOnly,
        )
    }

    @Test
    fun layout_12_images_high_coverOnly() {
        benchmarkLayout(
            images = demoImages(12),
            width = 360f,
            maxHeight = 520f,
            searchQuality = SearchQuality.High,
            tileFitPolicy = TileFitPolicy.CoverOnly,
        )
    }

    @Test
    fun layout_12_images_balanced_auto() {
        benchmarkLayout(
            images = demoImages(12),
            width = 360f,
            maxHeight = 520f,
            searchQuality = SearchQuality.Balanced,
            tileFitPolicy = TileFitPolicy.Auto,
        )
    }

    private fun benchmarkLayout(
        images: List<CollageImage>,
        width: Float,
        maxHeight: Float,
        searchQuality: SearchQuality,
        tileFitPolicy: TileFitPolicy,
    ) {
        val engine = CollageEngine {
            spacing = 6f
            minTileWidth = 42f
            minTileHeight = 42f
            maxTilesPerRow = 4
            maxLandscapeTilesPerRow = 2
            this.searchQuality = searchQuality
            this.tileFitPolicy = tileFitPolicy
            allowHeightOverflow = false
        }

        benchmarkRule.measureRepeated {
            val layout = engine.layout(
                images = images,
                width = width,
                minHeight = 0f,
                maxHeight = maxHeight,
            )

            blackhole = layout.height
        }
    }

    private fun demoImages(count: Int): List<CollageImage> {
        return List(count) { index ->
            demoImagePool[index % demoImagePool.size]
        }
    }

    private companion object {
        @Volatile
        private var blackhole: Float = 0f

        private val demoImagePool = listOf(
            CollageImage(imageId = 1, width = 1122f, height = 1402f), // portrait
            CollageImage(imageId = 2, width = 1536f, height = 1024f), // landscape
            CollageImage(imageId = 3, width = 1254f, height = 1254f), // square
            CollageImage(imageId = 4, width = 1672f, height = 941f),  // wide
            CollageImage(imageId = 5, width = 600f, height = 2400f),  // ultra tall
            CollageImage(imageId = 6, width = 2400f, height = 600f),  // ultra wide
            CollageImage(imageId = 7, width = 2600f, height = 900f),  // panorama
            CollageImage(imageId = 8, width = 700f, height = 2200f),  // vertical
            CollageImage(imageId = 9, width = 1536f, height = 1024f),
            CollageImage(imageId = 10, width = 1122f, height = 1402f),
            CollageImage(imageId = 11, width = 1254f, height = 1254f),
            CollageImage(imageId = 12, width = 1672f, height = 941f),
        )
    }
}
