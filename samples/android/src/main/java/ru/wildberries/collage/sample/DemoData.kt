package ru.wildberries.collage.sample

import androidx.annotation.DrawableRes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.wildberries.collage.model.CollageImage

data class DemoImage(
    @DrawableRes val resId: Int,
    val width: Float,
    val height: Float,
)

data class DemoCase(
    val title: String,
    val description: String,
    val images: List<DemoImage>,
    val minHeight: Dp = 0.dp,
    val maxHeight: Dp? = null,
    val allowHeightOverflow: Boolean = false,
)

data class WidthVariant(
    val title: String,
    val description: String,
    val fraction: Float,
)

fun DemoImage.toCollageImage(): CollageImage {
    return CollageImage(
        imageId = resId,
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

private val dog = DemoImage(R.drawable.demo_dog, width = 1122f, height = 1402f)
private val cats = DemoImage(R.drawable.demo_cats, width = 1536f, height = 1024f)
private val macaw = DemoImage(R.drawable.demo_macaw, width = 1254f, height = 1254f)
private val horses = DemoImage(R.drawable.demo_horses, width = 1672f, height = 941f)
private val sportCar = DemoImage(R.drawable.demo_sport_car, width = 1536f, height = 1024f)
private val van = DemoImage(R.drawable.demo_van, width = 1122f, height = 1402f)
private val suv = DemoImage(R.drawable.demo_suv, width = 1254f, height = 1254f)
private val giraffe = DemoImage(R.drawable.demo_giraffe, width = 1122f, height = 1402f)

private val ultraWide = DemoImage(R.drawable.demo_ultra_wide, width = 2400f, height = 600f)
private val ultraTall = DemoImage(R.drawable.demo_ultra_tall, width = 600f, height = 2400f)
private val panorama = DemoImage(R.drawable.demo_panorama, width = 2600f, height = 900f)
private val vertical = DemoImage(R.drawable.demo_vertical, width = 700f, height = 2200f)

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
        maxHeight = 420.dp,
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
        maxHeight = 520.dp,
        allowHeightOverflow = true,
        images = repeatedDemoImages(20),
    ),
)
