package ru.wildberries.collage.cache

import ru.wildberries.collage.core.LossDecision

internal class LossCache {

    private var tableSize = 0
    private var indexMask = 0
    private var size = 0

    private var epoch = 1
    private var stamp = IntArray(0)

    private var boxKeyTable = IntArray(0)
    private var photoIdTable = IntArray(0)

    private var coverLossTable = FloatArray(0)
    private var containLossTable = FloatArray(0)
    private var cropRatioTable = FloatArray(0)
    private var useCoverTable = BooleanArray(0)

    fun clear() {
        epoch++
        if (epoch == Int.MAX_VALUE) {
            epoch = 1
            if (stamp.isNotEmpty()) stamp.fill(0)
        }
        size = 0
    }

    fun get(photoKey: Int, boxKeyWH: Int): LossDecision? {
        if (tableSize == 0) return null

        var pos = hash(photoKey, boxKeyWH)
        var probes = 0

        while (probes < tableSize) {
            if (stamp[pos] != epoch) return null
            if (photoIdTable[pos] == photoKey && boxKeyTable[pos] == boxKeyWH) {
                return LossDecision(
                    cover = coverLossTable[pos],
                    contain = containLossTable[pos],
                    crop = cropRatioTable[pos],
                    useCover = useCoverTable[pos]
                )
            }
            pos = (pos + 1) and indexMask
            probes++
        }
        return null
    }

    fun put(photoKey: Int, boxKeyWH: Int, lossDecision: LossDecision) {
        ensureForInsert()

        while (true) {
            var pos = hash(photoKey, boxKeyWH)
            var probes = 0

            while (probes < tableSize) {
                val occupied = stamp[pos] == epoch

                if (!occupied) {
                    stamp[pos] = epoch
                    photoIdTable[pos] = photoKey
                    boxKeyTable[pos] = boxKeyWH
                    coverLossTable[pos] = lossDecision.cover
                    containLossTable[pos] = lossDecision.contain
                    cropRatioTable[pos] = lossDecision.crop
                    useCoverTable[pos] = lossDecision.useCover
                    size++
                    return
                }

                if (photoIdTable[pos] == photoKey && boxKeyTable[pos] == boxKeyWH) {
                    coverLossTable[pos] = lossDecision.cover
                    containLossTable[pos] = lossDecision.contain
                    cropRatioTable[pos] = lossDecision.crop
                    useCoverTable[pos] = lossDecision.useCover
                    return
                }

                pos = (pos + 1) and indexMask
                probes++
            }

            rehash(tableSize * 2)
        }
    }

    private fun ensureForInsert() {
        if (tableSize == 0) {
            rehash(16)
            return
        }
        if (size + 1 > (tableSize * MAX_LOAD_NUMERATOR) / MAX_LOAD_DENOMINATOR) {
            rehash(tableSize * 2)
        }
    }

    private fun rehash(minSize: Int) {
        val newSize = nextPow2(kotlin.math.max(16, minSize))

        val oldStamp = stamp
        val oldPhoto = photoIdTable
        val oldBox = boxKeyTable
        val oldCover = coverLossTable
        val oldContain = containLossTable
        val oldCrop = cropRatioTable
        val oldUseCover = useCoverTable
        val oldSize = tableSize
        val curEpoch = epoch

        stamp = IntArray(newSize)
        photoIdTable = IntArray(newSize)
        boxKeyTable = IntArray(newSize)
        coverLossTable = FloatArray(newSize)
        containLossTable = FloatArray(newSize)
        cropRatioTable = FloatArray(newSize)
        useCoverTable = BooleanArray(newSize)

        tableSize = newSize
        indexMask = newSize - 1
        size = 0

        if (oldSize == 0) return

        var i = 0
        while (i < oldSize) {
            if (oldStamp[i] == curEpoch) {
                val p = oldPhoto[i]
                val b = oldBox[i]

                var pos = hash(p, b)
                while (stamp[pos] == curEpoch) {
                    pos = (pos + 1) and indexMask
                }

                stamp[pos] = curEpoch
                photoIdTable[pos] = p
                boxKeyTable[pos] = b
                coverLossTable[pos] = oldCover[i]
                containLossTable[pos] = oldContain[i]
                cropRatioTable[pos] = oldCrop[i]
                useCoverTable[pos] = oldUseCover[i]
                size++
            }
            i++
        }
    }

    private fun nextPow2(x: Int): Int {
        var p = 1
        while (p < x) p = p shl 1
        return p
    }

    private fun hash(photoKey: Int, boxKeyWH: Int): Int {
        val c1 = -0x61C8864F
        val c2 = -0x7A143595
        var h = photoKey * c1 xor (boxKeyWH * c2)
        h = h xor (h ushr 16)
        return h and indexMask
    }

    companion object {
        private const val MAX_LOAD_NUMERATOR = 7
        private const val MAX_LOAD_DENOMINATOR = 10
    }
}
