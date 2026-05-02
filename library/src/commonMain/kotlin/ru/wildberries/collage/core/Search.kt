package ru.wildberries.collage.core

internal object Search {

    /**
     * Finding minimum on segment using golden section
     *
     * @param left left border (if left > right — borders would be swapped)
     * @param right  right border
     * @param iterations iterations num (20-30 is usually enough)
     * @param f function for looking for a minimum on [left, right]
     * @return pair (x*, f(x*)) — the minimum argument and the value of the fucntion in it
     */
    fun minimizeOnInterval(
        left: Float,
        right: Float,
        iterations: Int = 22,
        f: (Float) -> Float,
    ): Pair<Float, Float> {
        var a = minOf(left, right)
        var b = maxOf(left, right)
        if (a == b) {
            val fa = f(a)
            return a to fa
        }

        // golden section constant (phi = 0.618...)
        val golden = 0.618034f

        var x1 = b - golden * (b - a)
        var x2 = a + golden * (b - a)
        var f1 = f(x1)
        var f2 = f(x2)

        var i = 0
        while (i < iterations) {
            if (f1 <= f2) {
                b = x2
                x2 = x1
                f2 = f1
                x1 = b - golden * (b - a)
                f1 = f(x1)
            } else {
                a = x1
                x1 = x2
                f1 = f2
                x2 = a + golden * (b - a)
                f2 = f(x2)
            }
            i++
        }

        val xBest = 0.5f * (a + b)
        val fBest = f(xBest)
        return xBest to fBest
    }
}
