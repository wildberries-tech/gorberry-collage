package ru.wildberries.collage

import ru.wildberries.collage.model.CollageImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CollageEnginePublicApiTest {

    @Test
    fun emptyList_returnsEmptyGeometry() {
        val engine = CollageEngine()

        val geometry = engine.layout(
            images = emptyList(),
            width = 64f,
        )

        assertEquals(0f, geometry.width)
        assertEquals(0f, geometry.height)
        assertTrue(geometry.rows.isEmpty())
    }

    @Test
    fun singlePhoto_fitsWidth_keepsAspect() {
        val engine = CollageEngine {
            minTileWidth = 16f
            minTileHeight = 16f
        }

        val geometry = engine.layout(
            images = listOf(CollageImage(42, 12f, 8f)),
            width = 64f,
            minHeight = 32f,
            maxHeight = 64f,
        )

        assertEquals(64f, geometry.width)
        assertEquals(1, geometry.rows.size)

        val row = geometry.rows.first()
        assertEquals(1, row.tiles.size)

        val tile = row.tiles.first()
        assertEquals(64f, tile.boxW, 1e-3f)

        val contentAspectRatio = tile.contentW / tile.contentH
        assertEquals(12f / 8f, contentAspectRatio, 1e-3f)
    }

    @Test
    fun spacing_isRespectedInEachRow() {
        val engine = CollageEngine {
            spacing = 2f
            minTileWidth = 20f
            minTileHeight = 20f
        }

        val collageImages = listOf(
            CollageImage(0, 100f, 100f),
            CollageImage(1, 100f, 100f),
            CollageImage(2, 100f, 100f),
            CollageImage(3, 100f, 100f),
        )

        val geometry = engine.layout(
            images = collageImages,
            width = 200f,
            minHeight = 200f,
            maxHeight = 200f,
        )

        assertTrue(geometry.rows.isNotEmpty())

        for (row in geometry.rows) {
            val sumTileWidths = row.tiles.sumOf { it.boxW.toDouble() }.toFloat()
            val expectedWidth = 200f - 2f * (row.tiles.size - 1)
            assertEquals(expectedWidth, sumTileWidths, 1e-2f)
        }
    }

    @Test
    fun layout_returnsTilesMappedBackToPhotos() {
        val engine = CollageEngine {
            minTileWidth = 20f
            minTileHeight = 20f
        }

        val photos = TestKit.randomMixed(4)

        val geometry = engine.layout(
            images = photos,
            width = 200f,
            minHeight = 200f,
            maxHeight = 200f,
        )

        val tiles = geometry.rows.flatMap { it.tiles }

        assertEquals(photos.size, tiles.size)
        assertEquals(
            expected = photos.map { it.imageId }.sorted(),
            actual = tiles.map { it.imageId }.sorted(),
        )
        assertTrue(tiles.all { it.boxW > 0f && it.boxH > 0f })
    }

    @Test
    fun minTileWidth_isRespected() {
        val engine = CollageEngine {
            spacing = 2f
            minTileWidth = 56f
            minTileHeight = 56f
        }

        val collageImages = listOf(
            CollageImage(0, 1600f, 900f),
            CollageImage(1, 900f, 1600f),
            CollageImage(2, 1600f, 900f),
            CollageImage(3, 900f, 1600f),
        )

        val geometry = engine.layout(
            images = collageImages,
            width = 220f,
            minHeight = 200f,
            maxHeight = 200f,
        )

        geometry.rows.forEach { row ->
            row.tiles.forEach { tile ->
                assertTrue(tile.boxW >= 56f - 1e-3f)
            }
        }
    }
}
