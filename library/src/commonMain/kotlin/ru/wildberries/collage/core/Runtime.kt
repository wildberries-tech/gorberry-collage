package ru.wildberries.collage.core

import kotlin.time.TimeSource

internal interface Logger {
    val isEnabled: Boolean
        get() = true

    fun d(tag: String, msg: String)
}

internal object NoopLogger : Logger {
    override val isEnabled: Boolean = false

    override fun d(tag: String, msg: String) = Unit
}

internal object StdoutLogger : Logger {
    override val isEnabled: Boolean = true

    override fun d(tag: String, msg: String) {
        println("[$tag]: $msg")
    }
}

internal interface Clock {
    fun nowNs(): Long
}

internal object MonotonicClock : Clock {

    private val origin = TimeSource.Monotonic.markNow()

    override fun nowNs(): Long {
        return origin.elapsedNow().inWholeNanoseconds
    }
}
