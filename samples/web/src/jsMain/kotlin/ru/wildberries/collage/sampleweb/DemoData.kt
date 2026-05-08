package ru.wildberries.collage.sampleweb

import ru.wildberries.collage.model.CollageImage

data class DemoImage(
    val id: Int,
    val url: String,
    val width: Float,
    val height: Float,
)

data class DemoCase(
    val title: String,
    val description: String,
    val images: List<DemoImage>,
    val minHeight: Float = 0f,
    val maxHeight: Float? = null,
    val allowHeightOverflow: Boolean = false,
)

data class WidthVariant(
    val title: String,
    val description: String,
    val fraction: Float,
)

data class DemoFeedItem(
    val key: String,
    val demoCase: DemoCase,
    val widthVariant: WidthVariant,
)

fun DemoImage.toCollageImage(): CollageImage {
    return CollageImage(
        imageId = id,
        width = width,
        height = height,
    )
}

val widthVariants = listOf(
    WidthVariant(
        title = "Full width",
        description = "Uses the whole available message width.",
        fraction = 1.00f,
    ),
    WidthVariant(
        title = "Medium bubble",
        description = "Simulates a regular chat bubble width.",
        fraction = 0.78f,
    ),
    WidthVariant(
        title = "Compact bubble",
        description = "Simulates a narrow message container.",
        fraction = 0.58f,
    ),
)

private val dog = DemoImage(1, "images/demo_dog.png", width = 1122f, height = 1402f)
private val cats = DemoImage(2, "images/demo_cats.png", width = 1536f, height = 1024f)
private val macaw = DemoImage(3, "images/demo_macaw.png", width = 1254f, height = 1254f)
private val horses = DemoImage(4, "images/demo_horses.png", width = 1672f, height = 941f)
private val sportCar = DemoImage(5, "images/demo_sport_car.png", width = 1536f, height = 1024f)
private val van = DemoImage(6, "images/demo_van.png", width = 1122f, height = 1402f)
private val suv = DemoImage(7, "images/demo_suv.png", width = 1254f, height = 1254f)
private val giraffe = DemoImage(8, "images/demo_giraffe.png", width = 1122f, height = 1402f)

private val ultraWide = DemoImage(9, "images/demo_ultra_wide.png", width = 2400f, height = 600f)
private val ultraTall = DemoImage(10, "images/demo_ultra_tall.png", width = 600f, height = 2400f)
private val panorama = DemoImage(11, "images/demo_panorama.png", width = 2600f, height = 900f)
private val vertical = DemoImage(12, "images/demo_vertical.png", width = 700f, height = 2200f)

private val demoImagePool = listOf(
    dog,
    cats,
    macaw,
    horses,
    sportCar,
    van,
    suv,
    giraffe,
    ultraWide,
    ultraTall,
    panorama,
    vertical,
)

private fun repeatedDemoImages(count: Int): List<DemoImage> {
    return List(count) { index ->
        demoImagePool[index % demoImagePool.size]
    }
}

val demoCases = listOf(
    DemoCase(
        title = "Tall image pair",
        description = "Two portrait-shaped images in a compact media group.",
        images = listOf(
            dog,
            giraffe,
        ),
    ),
    DemoCase(
        title = "Mixed orientations",
        description = "Landscape, square, and portrait images in one message.",
        images = listOf(
            cats,
            macaw,
            dog,
        ),
    ),
    DemoCase(
        title = "Four-tile media preview",
        description = "Wide, tall, square, and landscape content in one group.",
        images = listOf(
            horses,
            van,
            macaw,
            sportCar,
        ),
    ),
    DemoCase(
        title = "Chat attachment group",
        description = "Five images arranged as a message-style attachment preview.",
        images = listOf(
            dog,
            cats,
            macaw,
            horses,
            van,
        ),
    ),
    DemoCase(
        title = "Vehicle media group",
        description = "Different vehicle shots with square, portrait, and landscape ratios.",
        images = listOf(
            sportCar,
            van,
            suv,
        ),
    ),
    DemoCase(
        title = "Extreme aspect ratios",
        description = "Ultra-wide and ultra-tall images mixed with regular content.",
        images = listOf(
            ultraWide,
            ultraTall,
            sportCar,
            dog,
        ),
    ),
    DemoCase(
        title = "Ultra stress test",
        description = "Panoramic and vertical images mixed together in one preview.",
        images = listOf(
            panorama,
            vertical,
            ultraWide,
            ultraTall,
            macaw,
        ),
    ),
    DemoCase(
        title = "Height-limited group",
        description = "A larger media group planned under a maximum layout height.",
        maxHeight = 420f,
        images = listOf(
            dog,
            cats,
            macaw,
            horses,
            sportCar,
            giraffe,
        ),
    ),
    DemoCase(
        title = "Long media group with overflow",
        description = "Twenty images with maxHeight set. Overflow is allowed to preserve readable tiles.",
        maxHeight = 520f,
        allowHeightOverflow = true,
        images = repeatedDemoImages(20),
    ),
)

val demoFeedItems: List<DemoFeedItem> = demoCases.flatMap { demoCase ->
    widthVariants.map { widthVariant ->
        DemoFeedItem(
            key = "${demoCase.title}:${widthVariant.title}",
            demoCase = demoCase,
            widthVariant = widthVariant,
        )
    }
}
