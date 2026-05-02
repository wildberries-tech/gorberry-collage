package ru.wildberries.collage.api

import ru.wildberries.collage.model.CollageGeometry
import ru.wildberries.collage.model.Photo

interface Logger {
    val isEnabled: Boolean get() = true
    fun d(tag: String, msg: String)
}

object NoopLogger : Logger {
    override val isEnabled: Boolean = false
    override fun d(tag: String, msg: String) = Unit
}

object StdoutLogger : Logger {
    override val isEnabled: Boolean = true

    override fun d(tag: String, msg: String) {
        println("[$tag]: $msg")
    }
}

interface Clock {
    fun nowNs(): Long
}

interface CollageEngine {
    fun arrangeWithGeometry(photos: List<Photo>): CollageGeometry
}
