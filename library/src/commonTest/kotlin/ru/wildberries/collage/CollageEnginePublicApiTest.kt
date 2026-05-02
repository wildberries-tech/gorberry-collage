package ru.wildberries.collage

import ru.wildberries.collage.api.Clock
import ru.wildberries.collage.api.EngineConfig
import ru.wildberries.collage.api.Logger
import ru.wildberries.collage.api.createCollageEngine
import ru.wildberries.collage.model.Photo
import ru.wildberries.collage.model.SizeAttrs
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CollageEnginePublicApiTest {

    private lateinit var clock: Clock
    private lateinit var logger: Logger

    @BeforeTest
    fun setup() {
        clock = TestClock()
        logger = TestLogger()
    }

    @Test
    fun emptyList_returnsEmptyGeometry() {
        val eng = createCollageEngine(clock, logger)
        val geo = eng.arrangeWithGeometry(emptyList())
        assertEquals(0f, geo.width)
        assertEquals(0f, geo.height)
        assertTrue(geo.rows.isEmpty())
    }

    @Test
    fun singlePhoto_fitsWidth_keepsAspect() {
        val cfg = EngineConfig(
            minCollageSize = SizeAttrs(32f, 32f),
            maxCollageSize = SizeAttrs(64f, 64f),
            minItemHeight = 16f,
            minItemWidth = 16f
        )
        val eng = createCollageEngine(clock, logger, config = cfg)

        val photo = Photo(42, 12f, 8f)
        val geo = eng.arrangeWithGeometry(listOf(photo))

        assertEquals(64f, geo.width)
        assertEquals(1, geo.rows.size)
        val row = geo.rows.first()
        assertEquals(1, row.tiles.size)
        val t = row.tiles.first()
        assertEquals(64f, t.boxW, 1e-3f)
        val contentAR = t.contentW / t.contentH
        assertEquals(12f / 8f, contentAR, 1e-3f)
    }

    @Test
    fun paddings_respected_inEachRow() {
        val cfg = EngineConfig(
            minCollageSize = SizeAttrs(200f, 200f),
            maxCollageSize = SizeAttrs(200f, 200f),
            minItemWidth = 20f,
            minItemHeight = 20f,
            paddings = 2f
        )
        val eng = createCollageEngine(clock, logger, config = cfg)

        val photos = listOf(
            Photo(0, 100f, 100f),
            Photo(1, 100f, 100f),
            Photo(2, 100f, 100f),
            Photo(3, 100f, 100f)
        )
        val geo = eng.arrangeWithGeometry(photos)
        assertTrue(geo.rows.isNotEmpty())
        for (row in geo.rows) {
            val sumW = row.tiles.sumOf { it.boxW.toDouble() }.toFloat()
            val expected = 200f - 2f * (row.tiles.size - 1)
            assertEquals(expected, sumW, 1e-2f)
        }
    }

    @Test
    fun arrangeWithGeometry_returnsTilesMappedBackToPhotos() {
        val cfg = EngineConfig(
            minCollageSize = SizeAttrs(200f, 200f),
            maxCollageSize = SizeAttrs(200f, 200f),
            minItemWidth = 20f,
            minItemHeight = 20f
        )
        val eng = createCollageEngine(clock, logger, config = cfg)

        val photos = TestKit.randomMixed(4)
        val geo = eng.arrangeWithGeometry(photos)
        val tiles = geo.rows.flatMap { it.tiles }
        assertEquals(photos.size, tiles.size)
        val ids = tiles.map { it.imageId }.sorted()
        assertEquals(photos.map { it.imageId }.sorted(), ids)
        assertTrue(tiles.all { it.boxW > 0f && it.boxH > 0f })
    }

    @Test
    fun minItemWidth_isRespected_evenInSnap() {
        val cfg = EngineConfig(
            minCollageSize = SizeAttrs(220f, 200f),
            maxCollageSize = SizeAttrs(220f, 200f),
            minItemWidth = 56f,
            minItemHeight = 56f,
            paddings = 2f
        )
        val eng = createCollageEngine(clock, logger, config = cfg)

        val photos = listOf(
            Photo(0, 1600f, 900f),
            Photo(1, 900f, 1600f),
            Photo(2, 1600f, 900f),
            Photo(3, 900f, 1600f)
        )
        val geo = eng.arrangeWithGeometry(photos)
        geo.rows.forEach { row ->
            row.tiles.forEach { t ->
                assertTrue(t.boxW >= cfg.minItemWidth - 1e-3f)
            }
        }
    }
}
