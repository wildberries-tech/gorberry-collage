package ru.wildberries.collage.cache

import ru.wildberries.collage.model.RectF
import kotlin.math.max

internal class PlanCache {

    private var tableSize = 0
    private var indexMask = 0
    private var size = 0

    private var epoch = 1
    private var stamp = IntArray(0)

    private var keyStart = IntArray(0)
    private var keyEnd = IntArray(0)
    private var keyHQ = IntArray(0)

    var rowHeightBySlot = FloatArray(0)
        private set
    var rowLossBySlot = FloatArray(0)
        private set

    private var boxOffsetBySlot = IntArray(0)
    private var boxCountBySlot = IntArray(0)
    private var boxPoolX = FloatArray(0)
    private var boxPoolY = FloatArray(0)
    private var boxPoolW = FloatArray(0)
    private var boxPoolH = FloatArray(0)
    private var nextFreeInPool = 0

    fun clear() {
        epoch++
        if (epoch == Int.MAX_VALUE) {
            epoch = 1
            if (stamp.isNotEmpty()) stamp.fill(0)
        }
        size = 0
        nextFreeInPool = 0
    }

    fun get(startIndex: Int, endIndex: Int, heightQuant: Int): Int {
        if (tableSize == 0) return INIT_VALUE
        var pos = computeHash(startIndex, endIndex, heightQuant) and indexMask
        var probes = 0
        while (probes < tableSize) {
            if (stamp[pos] != epoch) return INIT_VALUE
            if (keyStart[pos] == startIndex && keyEnd[pos] == endIndex && keyHQ[pos] == heightQuant) {
                return pos
            }
            pos = (pos + 1) and indexMask
            probes++
        }
        return INIT_VALUE
    }

    fun put(
        startIndex: Int,
        endIndex: Int,
        heightQuant: Int,
        rowHeight: Float,
        rowLoss: Float,
        boxes: List<RectF>,
    ): Int {
        ensureForInsert()

        while (true) {
            var pos = computeHash(startIndex, endIndex, heightQuant) and indexMask
            var probes = 0

            while (probes < tableSize) {
                val occupied = stamp[pos] == epoch

                if (!occupied) {
                    // insert new
                    stamp[pos] = epoch
                    keyStart[pos] = startIndex
                    keyEnd[pos] = endIndex
                    keyHQ[pos] = heightQuant

                    rowHeightBySlot[pos] = rowHeight
                    rowLossBySlot[pos] = rowLoss

                    val off = allocateInPool(boxes.size)
                    boxOffsetBySlot[pos] = off
                    boxCountBySlot[pos] = boxes.size
                    writeBoxes(off, boxes)

                    size++
                    return pos
                }

                if (keyStart[pos] == startIndex && keyEnd[pos] == endIndex && keyHQ[pos] == heightQuant) {
                    rowHeightBySlot[pos] = rowHeight
                    rowLossBySlot[pos] = rowLoss

                    val need = boxes.size
                    val have = boxCountBySlot[pos]
                    val off = if (have >= need) {
                        boxCountBySlot[pos] = need
                        boxOffsetBySlot[pos]
                    } else {
                        val newOff = allocateInPool(need)
                        boxOffsetBySlot[pos] = newOff
                        boxCountBySlot[pos] = need
                        newOff
                    }
                    writeBoxes(off, boxes)
                    return pos
                }

                pos = (pos + 1) and indexMask
                probes++
            }

            rehash(tableSize * 2)
        }
    }

    fun boxesAsList(slot: Int): List<RectF> {
        if (slot == INIT_VALUE) return emptyList()
        require(slot in 0 until tableSize) { "slot out of bounds: $slot" }
        if (stamp[slot] != epoch) return emptyList()

        val off = boxOffsetBySlot[slot]
        val cnt = boxCountBySlot[slot]
        if (cnt <= 0) return emptyList()

        val out = ArrayList<RectF>(cnt)
        var i = 0
        while (i < cnt) {
            out += RectF(
                x = boxPoolX[off + i],
                y = boxPoolY[off + i],
                w = boxPoolW[off + i],
                h = boxPoolH[off + i]
            )
            i++
        }
        return out
    }

    private fun ensureForInsert() {
        if (tableSize == 0) {
            rehash(8)
            return
        }
        if (size + 1 > (tableSize * MAX_LOAD_NUMERATOR) / MAX_LOAD_DENOMINATOR) {
            rehash(tableSize * 2)
        }
    }

    private fun rehash(minSize: Int) {
        val newSize = nextPow2(maxOf(8, minSize))
        val oldStamp = stamp
        val oldKeyStart = keyStart
        val oldKeyEnd = keyEnd
        val oldKeyHQ = keyHQ
        val oldRowH = rowHeightBySlot
        val oldRowLoss = rowLossBySlot
        val oldBoxOff = boxOffsetBySlot
        val oldBoxCnt = boxCountBySlot
        val oldSize = tableSize
        val curEpoch = epoch

        stamp = IntArray(newSize)
        keyStart = IntArray(newSize)
        keyEnd = IntArray(newSize)
        keyHQ = IntArray(newSize)
        rowHeightBySlot = FloatArray(newSize)
        rowLossBySlot = FloatArray(newSize)
        boxOffsetBySlot = IntArray(newSize)
        boxCountBySlot = IntArray(newSize)

        tableSize = newSize
        indexMask = newSize - 1
        size = 0

        if (oldSize == 0) return

        var i = 0
        while (i < oldSize) {
            if (oldStamp[i] == curEpoch) {
                val s = oldKeyStart[i]
                val e = oldKeyEnd[i]
                val hq = oldKeyHQ[i]

                var pos = computeHash(s, e, hq) and indexMask
                while (stamp[pos] == curEpoch) {
                    pos = (pos + 1) and indexMask
                }

                stamp[pos] = curEpoch
                keyStart[pos] = s
                keyEnd[pos] = e
                keyHQ[pos] = hq
                rowHeightBySlot[pos] = oldRowH[i]
                rowLossBySlot[pos] = oldRowLoss[i]
                boxOffsetBySlot[pos] = oldBoxOff[i]
                boxCountBySlot[pos] = oldBoxCnt[i]
                size++
            }
            i++
        }
    }

    private fun writeBoxes(offset: Int, boxes: List<RectF>) {
        var k = 0
        while (k < boxes.size) {
            val b = boxes[k]
            boxPoolX[offset + k] = b.x
            boxPoolY[offset + k] = b.y
            boxPoolW[offset + k] = b.w
            boxPoolH[offset + k] = b.h
            k++
        }
    }

    private fun allocateInPool(count: Int): Int {
        if (count <= 0) return nextFreeInPool
        val needed = nextFreeInPool + count
        ensurePoolCapacity(needed)
        val offset = nextFreeInPool
        nextFreeInPool = needed
        return offset
    }

    private fun ensurePoolCapacity(minRequired: Int) {
        if (minRequired <= boxPoolX.size) return

        var newCapacity = max(64, boxPoolX.size)
        while (newCapacity < minRequired) {
            newCapacity = if (newCapacity < 1_000_000) newCapacity * 2 else (newCapacity * 3) / 2
        }
        boxPoolX = boxPoolX.copyOf(newCapacity)
        boxPoolY = boxPoolY.copyOf(newCapacity)
        boxPoolW = boxPoolW.copyOf(newCapacity)
        boxPoolH = boxPoolH.copyOf(newCapacity)
    }

    private fun nextPow2(x: Int): Int {
        var p = 1
        while (p < x) p = p shl 1
        return p
    }

    private fun computeHash(startIndex: Int, endIndex: Int, heightQuant: Int): Int {
        var h = startIndex * 73856093
        h = h xor (endIndex * 19349663)
        h = h xor (heightQuant * 83492791)
        h = h xor (h ushr 16)
        return h
    }

    companion object {
        private const val INIT_VALUE = -1
        private const val MAX_LOAD_NUMERATOR = 7
        private const val MAX_LOAD_DENOMINATOR = 10
    }
}
