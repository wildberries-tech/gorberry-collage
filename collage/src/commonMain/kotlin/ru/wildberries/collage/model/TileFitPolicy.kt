package ru.wildberries.collage.model

/**
 * Controls how image content is fitted inside each tile.
 */
enum class TileFitPolicy {
    /**
     * Chooses between COVER and CONTAIN for every tile.
     */
    Auto,

    /**
     * Always uses COVER. Tiles have no internal empty areas.
     */
    CoverOnly,
}
