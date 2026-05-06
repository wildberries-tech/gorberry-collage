package ru.wildberries.collage.core

import ru.wildberries.collage.cache.RowPlanCache
import ru.wildberries.collage.model.CollageImage
import ru.wildberries.collage.model.RectF
import ru.wildberries.collage.model.TileFitPolicy
import ru.wildberries.collage.strategy.RowPenaltyModel
import ru.wildberries.collage.strategy.RowLengthBias
import ru.wildberries.collage.strategy.RowPenaltyContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

internal data class DynamicProgrammingParams(
    val maxItemsPerRow: Int = 4,
    val maxHorizontalsPerRow: Int = 3,
    val minItemWidth: Float = 56f,
    val minItemHeight: Float = 56f,
    val tauHorizontal: Float = 1.05f,
    val contrastTau: Float = 1.35f,
    val rowContrastAlpha: Double = 120_000.0,
    val rowWidthBalanceAlpha: Double = 120_000.0,
    val verticalSquashGuardFracOfWidth: Float = 0.10f,
    val verticalSquashAlpha: Double = 100_000.0,
    val rowHeightSmoothAlpha: Double = 500.0,
    val heightBudgetAlpha: Double = 240_000.0,
    val rowLenPrior: RowLengthBias = RowLengthBias(),
)

internal data class RowLayoutSolution(
    val cost: Double,
    val lossScore: Double,
    val ranges: List<IntRange>,
    val rowHeights: List<Float>,
    val rowBoxes: List<List<RectF>>,
    val totalHeight: Float,
)

internal fun isBetterSolution(candidate: RowLayoutSolution, current: RowLayoutSolution?): Boolean {
    if (current == null) return true
    if (!candidate.cost.isFinite()) return false
    if (!current.cost.isFinite()) return true

    val base = min(candidate.lossScore, current.lossScore).coerceAtLeast(1.0)
    val eps = 0.02 * base

    return when {
        candidate.lossScore < current.lossScore - eps -> true
        candidate.lossScore > current.lossScore + eps -> false
        else -> candidate.cost < current.cost
    }
}

internal fun countSingletonRows(solve: RowLayoutSolution): Int =
    solve.ranges.count { it.first == it.last }

internal fun hasSingletonRow(solve: RowLayoutSolution): Boolean =
    countSingletonRows(solve) > 0

private data class Plan(
    val boxes: List<RectF>,
    val loss: Double,
    val height: Float,
)

private data class HeightPack(
    val hFeasible: Float,
    val idealH: Float,
    val hEst: Float,
)

private data class NextHints(
    val nextRowHeight: Float?,
    val nextMostlyVertical: Boolean,
    val nextHeightHint: Float?,
)

private data class QuickGuards(
    val itemsLeftAfterThis: Int,
    val rowsLeftAfterThis: Int,
    val hCount: Int,
    val vCount: Int,
)

/**
 * Searches for the best split of ordered images into rows for a fixed row count
 */
internal class DynamicProgrammingRowSplitSolver(
    private val params: DynamicProgrammingParams,
    private val tileFitScorer: TileFitScorer,
    private val rowAugmentor: RowPenaltyModel,
    private val planner: RowPlanner,
    private val rowPlanCache: RowPlanCache,
    private val logger: Logger,
    private val tuning: CollageTuning.Snapshot = CollageTuning.default,
    private val tileFitPolicy: TileFitPolicy = TileFitPolicy.CoverOnly,
) {

    private val tauVertical: Float = 1f / params.tauHorizontal
    private val heightQuantStep: Float = tuning.dynamicProgrammingConfig.heightQuantStep

    private fun heightQuant(h: Float): Int =
        MathUtil.fastRoundToInt(h / heightQuantStep)

    private fun perRowBudget(rowsLeft: Int, maxHeightAllowed: Float, verticalGap: Float): Float {
        if (!maxHeightAllowed.isFinite()) return Float.POSITIVE_INFINITY
        val remaining = maxHeightAllowed - verticalGap * (rowsLeft - 1)
        return max(params.minItemHeight, remaining / rowsLeft)
    }

    private fun minHFromMinWidth(
        start: Int,
        end: Int,
        aspect: FloatArray,
    ): Float {
        var h = params.minItemHeight
        var i = start
        while (i <= end) {
            val a = aspect[i].coerceAtLeast(1e-6f)
            val need = params.minItemWidth / a
            if (need > h) h = need
            i++
        }
        return h
    }

    private fun applyAbsMinWidthGuard(
        current: Float,
        start: Int,
        end: Int,
        nInRow: Int,
        collageWidth: Float,
        horizontalGap: Float,
        aspect: FloatArray,
    ): Float {
        val target = collageWidth - horizontalGap * max(0, nInRow - 1)
        val absMinFrac = when {
            nInRow == 4 -> 0.20f
            nInRow == 3 -> 0.20f
            nInRow >= 5 -> 0.10f
            else -> 0f
        }
        if (absMinFrac == 0f) return current

        val minAbsW = absMinFrac * target
        var needHAbs = params.minItemHeight
        var t = start
        while (t <= end) {
            val a = aspect[t].coerceAtLeast(1e-6f)
            val needH = minAbsW / a
            if (needH > needHAbs) needHAbs = needH
            t++
        }
        return max(current, needHAbs)
    }

    private fun applyAnyRowGuard(current: Float, collageWidth: Float): Float {
        val anyRowGuard = tuning.dynamicProgrammingConfig.anyRowGuardFrac
        return max(current, anyRowGuard * collageWidth)
    }

    private fun betaFor(nInRow: Int): Float =
        tuning.heuristics.softRelMinFrac(nInRow)

    private fun applyBetaGuard(
        current: Float,
        start: Int,
        end: Int,
        nInRow: Int,
        collageWidth: Float,
        horizontalGap: Float,
        aspect: FloatArray,
    ): Float {
        val beta = betaFor(nInRow)
        val target = collageWidth - horizontalGap * max(0, nInRow - 1)
        val cell = if (nInRow > 0) target / nInRow else 1f
        if (beta <= 0f || beta * cell <= params.minItemWidth + 1e-3f) return current

        var hBeta = params.minItemHeight
        var t = start
        while (t <= end) {
            val a = aspect[t].coerceAtLeast(1e-6f)
            val need = (beta * cell) / a
            if (need > hBeta) hBeta = need
            t++
        }
        return max(current, hBeta)
    }

    private fun hardGamma(a: Float): Float {
        val tauH = params.tauHorizontal
        val tauV = 1f / tauH
        val pc = tuning.planner
        return when {
            a <= tauV -> pc.perAspectFloorV
            a < tauH -> pc.perAspectFloorS
            else -> pc.perAspectFloorH
        }
    }

    private fun applyHardGammaGuard(
        current: Float,
        start: Int,
        end: Int,
        nInRow: Int,
        collageWidth: Float,
        horizontalGap: Float,
        aspect: FloatArray,
    ): Float {
        val beta = betaFor(nInRow)
        val target = collageWidth - horizontalGap * max(0, nInRow - 1)
        val cell = if (nInRow > 0) target / nInRow else 1f

        var hNeed = params.minItemHeight
        var i = start
        while (i <= end) {
            val a = aspect[i].coerceAtLeast(1e-6f)
            val floorW = max(params.minItemWidth, max(beta * cell, hardGamma(a) * cell))
            hNeed = max(hNeed, floorW / a)
            i++
        }
        return max(current, hNeed)
    }

    private fun rhythmTargetHeight(
        collageWidth: Float,
        length: Int,
        hCount: Int,
        vCount: Int,
    ): Float {
        val byLen = when (length) {
            1 -> 0.44f
            2 -> 0.34f
            3 -> 0.30f
            4 -> 0.27f
            else -> 0.24f
        }
        val byOrientation = when {
            hCount * 2 >= length -> 0.31f
            vCount * 2 >= length -> 0.24f
            else -> 0.28f
        }
        return collageWidth * max(byLen, byOrientation)
    }

    private fun collectCandidateHeights(
        hFeasible: Float,
        idealH: Float,
        hEst: Float,
        capH: Float,
        collageWidth: Float,
        length: Int,
        hCount: Int,
        vCount: Int,
        nextRowHeight: Float?,
        nextHeightHint: Float?,
    ): List<Float> {
        val out = ArrayList<Float>(10)

        fun add(raw: Float?) {
            if (raw == null || !raw.isFinite()) return
            val h = if (capH.isFinite()) raw.coerceIn(hFeasible, capH) else max(hFeasible, raw)
            if (out.none { abs(it - h) < 0.5f }) out += h
        }

        val rhythmH = rhythmTargetHeight(
            collageWidth = collageWidth,
            length = length,
            hCount = hCount,
            vCount = vCount,
        )

        add(hFeasible)
        add(idealH)
        add(hEst)
        add(rhythmH)
        add(0.5f * (hEst + rhythmH))
        add(nextRowHeight)
        add(nextHeightHint)
        if (nextRowHeight != null) add(0.5f * (hEst + nextRowHeight))
        if (capH.isFinite()) add(capH)

        out.sort()
        return out
    }

    private fun computeHeightPack(
        startIndex: Int,
        endIndex: Int,
        aspect: FloatArray,
        collageWidth: Float,
        horizontalGap: Float,
        perRowHCap: Float,
        hNatCur: Float,
    ): HeightPack {
        val n = endIndex - startIndex + 1

        var hFeasible = minHFromMinWidth(startIndex, endIndex, aspect)
        hFeasible = applyAbsMinWidthGuard(
            current = hFeasible,
            start = startIndex,
            end = endIndex,
            nInRow = n,
            collageWidth = collageWidth,
            horizontalGap = horizontalGap,
            aspect = aspect,
        )
        hFeasible = applyAnyRowGuard(hFeasible, collageWidth)
        hFeasible = applyBetaGuard(
            current = hFeasible,
            start = startIndex,
            end = endIndex,
            nInRow = n,
            collageWidth = collageWidth,
            horizontalGap = horizontalGap,
            aspect = aspect,
        )
        hFeasible = applyHardGammaGuard(
            current = hFeasible,
            start = startIndex,
            end = endIndex,
            nInRow = n,
            collageWidth = collageWidth,
            horizontalGap = horizontalGap,
            aspect = aspect,
        )

        val idealH = if (perRowHCap.isFinite()) min(hNatCur, perRowHCap) else hNatCur
        var capH = if (perRowHCap.isFinite()) max(perRowHCap, params.minItemHeight) else Float.POSITIVE_INFINITY
        if (hFeasible > capH) capH = hFeasible
        val hEst = min(max(hNatCur, hFeasible), capH)

        return HeightPack(hFeasible = hFeasible, idealH = idealH, hEst = hEst)
    }

    private fun computeNextHints(
        tail: RowLayoutSolution,
        countV: (Int, Int) -> Int,
        naturalHeight: (Int, Int) -> Float,
        rowsLeftAfterThis: Int,
        maxHeightAllowed: Float,
        verticalGap: Float,
    ): NextHints {
        if (tail.ranges.isEmpty()) {
            return NextHints(nextRowHeight = null, nextMostlyVertical = false, nextHeightHint = null)
        }
        val nr = tail.ranges.first()
        val lenNext = nr.last - nr.first + 1
        val vNext = countV(nr.first, nr.last)
        val nextMostlyVertical = (vNext * 2 >= lenNext)

        val perNextCap = perRowBudget(rowsLeftAfterThis, maxHeightAllowed, verticalGap)
        val hNatNext = naturalHeight(nr.first, nr.last)
        val nextHeightHint = if (perNextCap.isFinite()) min(hNatNext, perNextCap) else hNatNext

        return NextHints(
            nextRowHeight = tail.rowHeights.first(),
            nextMostlyVertical = nextMostlyVertical,
            nextHeightHint = nextHeightHint
        )
    }

    private fun buildPlanAtHeight(
        i: Int,
        j: Int,
        heightHint: Float,
        collageImages: List<CollageImage>,
        collageWidth: Float,
        horizontalGap: Float,
        verticalGap: Float,
    ): Plan? {
        val q = heightQuant(heightHint)
        val cached = rowPlanCache.get(i, j, q)
        if (cached >= 0) {
            return Plan(
                boxes = rowPlanCache.boxesAsList(cached),
                loss = rowPlanCache.rowLossBySlot[cached].toDouble(),
                height = rowPlanCache.rowHeightBySlot[cached]
            )
        }
        val ctx = RowLayoutContext(
            collageImages = collageImages,
            startIndexInclusive = i,
            endIndexInclusive = j,
            collageWidth = collageWidth,
            horizontalGap = horizontalGap,
            verticalGap = verticalGap,
            minItemWidth = params.minItemWidth,
            minItemHeight = params.minItemHeight,
            rowHeightHint = heightHint,
            tileFitScorer = tileFitScorer,
            tileFitPolicy = tileFitPolicy,
        )
        val rp = planner.tryPlan(ctx) ?: return null
        val slot = rowPlanCache.put(
            startIndex = i,
            endIndex = j,
            heightQuant = q,
            rowHeight = rp.rowHeight,
            rowLoss = rp.loss.toFloat(),
            boxes = rp.boxes,
        )
        return Plan(rowPlanCache.boxesAsList(slot), rowPlanCache.rowLossBySlot[slot].toDouble(), rowPlanCache.rowHeightBySlot[slot])
    }

    private fun computeTotalCostOrNull(
        thisRowLoss: Double,
        penaltyRaw: Double,
        tailCost: Double,
        areaUnit: Double,
        kLoss: Double,
        kPen: Double,
    ): Double? {
        var penalty = (penaltyRaw / areaUnit)
        if (!penalty.isFinite()) penalty = 1e6
        if (penalty < 0.0) penalty = 0.0

        val lossScaled = kLoss * (thisRowLoss / areaUnit)
        val penaltySafe = if (penalty.isFinite()) penalty else 1e12
        val tailCostSafe = if (tailCost.isFinite()) tailCost else Double.POSITIVE_INFINITY
        if (!tailCostSafe.isFinite()) return null

        val total = lossScaled + kPen * penaltySafe + tailCostSafe
        return if (total.isFinite()) total else null
    }

    private inner class Session(
        val collageImages: List<CollageImage>,
        val collageWidth: Float,
        val horizontalGap: Float,
        val verticalGap: Float,
        val targetRows: Int,
        val maxHeightAllowed: Float,
    ) {

        val n: Int = collageImages.size
        val forbidSingletonRows: Boolean = n > 1 && 2 * targetRows <= n
        val aspectReal: FloatArray = FloatArray(n) { i -> MathUtil.aspect(collageImages[i].width, collageImages[i].height) }
        val aspectPlan: FloatArray = FloatArray(n) { i ->
            MathUtil.clampAspectForPlanning(
                aspectReal[i],
                tuning.heuristics.planningAspectLimit,
            )
        }

        private val prefixHoriz = IntArray(n + 1)
        private val prefixVert = IntArray(n + 1)
        private val hNat = Array(n) { FloatArray(n) }

        private val areaUnit: Double = (collageWidth * max(params.minItemHeight, 1f)).toDouble()
        private val kLoss = tuning.dynamicProgrammingConfig.kLoss
        private val kPen = tuning.dynamicProgrammingConfig.kPen
        private val memo: Array<Array<RowLayoutSolution?>> = Array(n + 1) { arrayOfNulls(targetRows + n + 2) }

        init {
            var i = 0
            while (i < n) {
                prefixHoriz[i + 1] = prefixHoriz[i] + if (aspectReal[i] >= params.tauHorizontal) 1 else 0
                prefixVert[i + 1] = prefixVert[i] + if (aspectReal[i] <= tauVertical) 1 else 0
                i++
            }
        }

        fun normalizedLoss(loss: Double): Double = loss / areaUnit

        fun totalCostOrNull(
            thisRowLoss: Double,
            penaltyRaw: Double,
            tailCost: Double,
        ): Double? = this@DynamicProgrammingRowSplitSolver.computeTotalCostOrNull(
            thisRowLoss = thisRowLoss,
            penaltyRaw = penaltyRaw,
            tailCost = tailCost,
            areaUnit = areaUnit,
            kLoss = kLoss,
            kPen = kPen
        )

        fun buildPenaltyContext(
            length: Int,
            endIndex: Int,
            rowIndex: Int,
            guards: QuickGuards,
            plan: Plan,
            hp: HeightPack,
            tail: RowLayoutSolution,
            hints: NextHints,
        ): RowPenaltyContext {
            return RowPenaltyContext(
                totalItems = n,
                length = length,
                isLastRow = (endIndex == n - 1),
                plannedHeight = plan.height,
                rowIndex = rowIndex,
                rowsLeftAfterThis = guards.rowsLeftAfterThis,
                hCount = guards.hCount,
                vCount = guards.vCount,
                currentMostlyV = (guards.vCount * 2 >= length),
                planBoxes = plan.boxes,
                collageWidth = collageWidth,
                verticalGap = verticalGap,
                nextRowFirstHeight = hints.nextRowHeight,
                heightHint = hp.idealH,
                nextMostlyV = hints.nextMostlyVertical,
                tailTotalHeight = tail.totalHeight,
                contrastTau = params.contrastTau,
                rowContrastAlpha = params.rowContrastAlpha,
                rowWidthBalanceAlpha = params.rowWidthBalanceAlpha,
                verticalSquashGuardFracOfWidth = params.verticalSquashGuardFracOfWidth,
                verticalSquashAlpha = params.verticalSquashAlpha,
                rowHeightSmoothAlpha = params.rowHeightSmoothAlpha,
                heightBudgetAlpha = params.heightBudgetAlpha,
                maxHeightAllowed = maxHeightAllowed,
                areaUnit = areaUnit,
                rowLenPrior = params.rowLenPrior,
                nextHeightHint = hints.nextHeightHint,
                itemsLeftAfterThis = guards.itemsLeftAfterThis
            )
        }

        fun countH(i: Int, j: Int): Int = prefixHoriz[j + 1] - prefixHoriz[i]
        fun countV(i: Int, j: Int): Int = prefixVert[j + 1] - prefixVert[i]

        fun naturalHeight(i: Int, j: Int): Float {
            var h = hNat[i][j]
            if (h > 0f) return h
            val length = j - i + 1
            h = if (length > params.maxItemsPerRow) {
                params.minItemHeight
            } else {
                naturalRowHeightFromArray(
                    aspects = aspectPlan, start = i, end = j,
                    collageWidth = collageWidth, horizontalGap = horizontalGap,
                    minW = params.minItemWidth, minH = params.minItemHeight
                )
            }
            hNat[i][j] = h
            return h
        }

        fun perRowBudgetLocal(rowsLeft: Int): Float =
            perRowBudget(rowsLeft, maxHeightAllowed, verticalGap)

        fun solve(): RowLayoutSolution = bestFrom(0, 0)

        fun bestFrom(startIndex: Int, rowIndex: Int): RowLayoutSolution {
            if (startIndex == n) {
                return if (rowIndex == targetRows) {
                    RowLayoutSolution(
                        cost = 0.0,
                        lossScore = 0.0,
                        ranges = emptyList(),
                        rowHeights = emptyList(),
                        rowBoxes = emptyList(),
                        totalHeight = 0f,
                    )
                } else {
                    RowLayoutSolution(
                        cost = Double.POSITIVE_INFINITY,
                        lossScore = Double.POSITIVE_INFINITY,
                        ranges = emptyList(),
                        rowHeights = emptyList(),
                        rowBoxes = emptyList(),
                        totalHeight = Float.POSITIVE_INFINITY,
                    )
                }
            }
            memo[startIndex][rowIndex]?.let { return it }

            val remainingPhotos = n - startIndex
            val rowsLeft = targetRows - rowIndex
            if (rowsLeft !in 1..remainingPhotos ||
                remainingPhotos > rowsLeft * params.maxItemsPerRow
            ) {
                val res = RowLayoutSolution(
                    cost = Double.POSITIVE_INFINITY,
                    lossScore = Double.POSITIVE_INFINITY,
                    ranges = emptyList(),
                    rowHeights = emptyList(),
                    rowBoxes = emptyList(),
                    totalHeight = Float.POSITIVE_INFINITY,
                )
                memo[startIndex][rowIndex] = res
                return res
            }

            var best: RowLayoutSolution? = null
            val maxLenHere = min(params.maxItemsPerRow, remainingPhotos)
            val perRowHCap = perRowBudgetLocal(rowsLeft)

            var length = 1
            while (length <= maxLenHere) {
                val candidate = evaluateLengthCandidate(
                    session = this,
                    length = length,
                    startIndex = startIndex,
                    rowIndex = rowIndex,
                    perRowHCap = perRowHCap
                ) { nextStart, nextRow -> bestFrom(nextStart, nextRow) }

                if (candidate != null && isBetterSolution(candidate, best)) {
                    best = candidate
                }
                length++
            }

            val res = best ?: RowLayoutSolution(
                cost = Double.POSITIVE_INFINITY,
                lossScore = Double.POSITIVE_INFINITY,
                ranges = emptyList(),
                rowHeights = emptyList(),
                rowBoxes = emptyList(),
                totalHeight = Float.POSITIVE_INFINITY,
            )
            memo[startIndex][rowIndex] = res
            return res
        }
    }

    /**
     * Solves the row split problem for exactly [targetRows] rows.
     */
    fun solveForTargetRows(
        collageImages: List<CollageImage>,
        collageWidth: Float,
        horizontalGap: Float,
        verticalGap: Float,
        targetRows: Int,
        maxHeightAllowed: Float,
    ): RowLayoutSolution {
        val session = Session(
            collageImages = collageImages,
            collageWidth = collageWidth,
            horizontalGap = horizontalGap,
            verticalGap = verticalGap,
            targetRows = targetRows,
            maxHeightAllowed = maxHeightAllowed
        )
        val result = session.solve()
        if (logger.isEnabled) {
            val rangesText = result.ranges.joinToString { range ->
                "${range.first}..${range.last}"
            }

            val message = buildString {
                append("PICKED rows=")
                append(result.ranges.size)
                append(" ranges=[")
                append(rangesText)
                append("] totalHeight=")
                append(result.totalHeight)
                append(" cost=")
                append(result.cost)
            }

            logger.d("DP", message)
        }
        return result
    }

    private fun evaluateLengthCandidate(
        session: Session,
        length: Int,
        startIndex: Int,
        rowIndex: Int,
        perRowHCap: Float,
        bestFromNext: (Int, Int) -> RowLayoutSolution,
    ): RowLayoutSolution? {
        val endIndex = startIndex + length - 1
        val guards = quickGuards(
            session = session,
            length = length,
            startIndex = startIndex,
            endIndex = endIndex,
            rowIndex = rowIndex,
        ) ?: return null

        val hNatCur = session.naturalHeight(startIndex, endIndex)
        val hp = computeHeightPack(
            startIndex = startIndex,
            endIndex = endIndex,
            aspect = session.aspectPlan,
            collageWidth = session.collageWidth,
            horizontalGap = session.horizontalGap,
            perRowHCap = perRowHCap,
            hNatCur = hNatCur,
        )
        val capH = if (perRowHCap.isFinite()) max(perRowHCap, hp.hFeasible) else Float.POSITIVE_INFINITY

        val tail = bestFromNext(endIndex + 1, rowIndex + 1)
        if (!tail.cost.isFinite()) return null

        val hints = computeNextHints(
            tail = tail,
            countV = session::countV,
            naturalHeight = session::naturalHeight,
            rowsLeftAfterThis = guards.rowsLeftAfterThis,
            maxHeightAllowed = session.maxHeightAllowed,
            verticalGap = session.verticalGap
        )

        var best: RowLayoutSolution? = null

        val candidateHeights = collectCandidateHeights(
            hFeasible = hp.hFeasible,
            idealH = hp.idealH,
            hEst = hp.hEst,
            capH = capH,
            collageWidth = session.collageWidth,
            length = length,
            hCount = guards.hCount,
            vCount = guards.vCount,
            nextRowHeight = hints.nextRowHeight,
            nextHeightHint = hints.nextHeightHint,
        )

        for (candidateHeight in candidateHeights) {
            val solve = buildSolveForCandidateHeightOrNull(
                session = session,
                candidateHeight = candidateHeight,
                startIndex = startIndex,
                endIndex = endIndex,
                rowLength = length,
                rowIndex = rowIndex,
                maximumRowHeight = capH,
                guards = guards,
                heightPack = hp,
                tail = tail,
                hints = hints,
            )

            if (solve != null && isBetterSolution(solve, best)) {
                best = solve
            }
        }

        return best
    }

    private fun buildSolveForCandidateHeightOrNull(
        session: Session,
        candidateHeight: Float,
        startIndex: Int,
        endIndex: Int,
        rowLength: Int,
        rowIndex: Int,
        maximumRowHeight: Float,
        guards: QuickGuards,
        heightPack: HeightPack,
        tail: RowLayoutSolution,
        hints: NextHints,
    ): RowLayoutSolution? {
        val plan = buildPlanAtHeight(
            i = startIndex,
            j = endIndex,
            heightHint = candidateHeight,
            collageImages = session.collageImages,
            collageWidth = session.collageWidth,
            horizontalGap = session.horizontalGap,
            verticalGap = session.verticalGap,
        ) ?: return null

        if (maximumRowHeight.isFinite() && plan.height > maximumRowHeight + 1e-3f) {
            return null
        }

        val penaltyRaw = rowAugmentor.totalPenalty(
            session.buildPenaltyContext(
                length = rowLength,
                endIndex = endIndex,
                rowIndex = rowIndex,
                guards = guards,
                plan = plan,
                hp = heightPack,
                tail = tail,
                hints = hints,
            )
        )

        val totalCost = session.totalCostOrNull(
            thisRowLoss = plan.loss,
            penaltyRaw = penaltyRaw,
            tailCost = tail.cost,
        ) ?: return null

        return RowLayoutSolution(
            cost = totalCost,
            lossScore = session.normalizedLoss(plan.loss) + tail.lossScore,
            ranges = listOf(startIndex..endIndex) + tail.ranges,
            rowHeights = listOf(plan.height) + tail.rowHeights,
            rowBoxes = listOf(plan.boxes) + tail.rowBoxes,
            totalHeight = plan.height +
                (if (tail.ranges.isNotEmpty()) session.verticalGap else 0f) +
                tail.totalHeight,
        )
    }

    private fun quickGuards(
        session: Session,
        length: Int,
        startIndex: Int,
        endIndex: Int,
        rowIndex: Int,
    ): QuickGuards? {
        val itemsLeftAfterThis = session.n - (endIndex + 1)
        val rowsLeftAfterThis = session.targetRows - (rowIndex + 1)
        val minimumItemsPerRow = if (session.forbidSingletonRows) 2 else 1

        val horizontalPhotoCount = session.countH(startIndex, endIndex)
        val verticalPhotoCount = session.countV(startIndex, endIndex)

        val portraitLikePhotoCount = DynamicProgrammingRowGuards.countPortraitLikePhotos(
            aspectRatios = session.aspectReal,
            startIndex = startIndex,
            endIndex = endIndex,
        )

        val allowedItemCapacity = DynamicProgrammingRowGuards.allowedRowItemCapacity(
            maximumItemsPerRow = params.maxItemsPerRow,
            verticalPhotoCount = verticalPhotoCount,
        )

        val tooManyPortraitLikeItems = DynamicProgrammingRowGuards.hasTooManyPortraitLikeItemsInWideRow(
            rowLength = length,
            portraitLikePhotoCount = portraitLikePhotoCount,
            horizontalPhotoCount = horizontalPhotoCount,
        )

        val isValid =
            length <= allowedItemCapacity &&
                !tooManyPortraitLikeItems &&
                (!session.forbidSingletonRows || length != 1) &&
                DynamicProgrammingRowGuards.isRemainderFeasible(
                    itemsLeftAfterThis = itemsLeftAfterThis,
                    rowsLeftAfterThis = rowsLeftAfterThis,
                    minimumItemsPerRow = minimumItemsPerRow,
                    maximumItemsPerRow = params.maxItemsPerRow,
                ) &&
                DynamicProgrammingRowGuards.isRowDistributionAllowed(
                    rowLength = length,
                    itemsLeftAfterThis = itemsLeftAfterThis,
                    rowsLeftAfterThis = rowsLeftAfterThis,
                    totalItems = session.n,
                ) &&
                DynamicProgrammingRowGuards.hasEnoughWidthForMinimumItems(
                    rowLength = length,
                    collageWidth = session.collageWidth,
                    horizontalGap = session.horizontalGap,
                    minimumItemWidth = params.minItemWidth,
                ) &&
                horizontalPhotoCount <= params.maxHorizontalsPerRow

        return if (isValid) {
            QuickGuards(
                itemsLeftAfterThis = itemsLeftAfterThis,
                rowsLeftAfterThis = rowsLeftAfterThis,
                hCount = horizontalPhotoCount,
                vCount = verticalPhotoCount,
            )
        } else {
            null
        }
    }

    /**
     * Natural row height for [start]..[end] height such that sum of widths at this height fills
     * collageWidth - (length - 1) * horizontalGap, while respecting minimum widths
     */
    private fun naturalRowHeightFromArray(
        aspects: FloatArray,
        start: Int,
        end: Int,
        collageWidth: Float,
        horizontalGap: Float,
        minW: Float,
        minH: Float,
    ): Float {
        val length = end - start + 1
        val totalGaps = horizontalGap * max(0, length - 1)
        val available = (collageWidth - totalGaps).coerceAtLeast(1f)

        fun sumWidthAt(height: Float): Float {
            var s = 0f
            var i = start
            while (i <= end) {
                val w = height * aspects[i]
                s += if (w >= minW) w else minW
                i++
            }
            return s
        }

        var lo = minH
        var sumA = 0f
        var i = start
        while (i <= end) {
            sumA += aspects[i]
            i++
        }
        sumA = sumA.coerceAtLeast(1e-3f)
        var hi = max(lo, available / sumA)

        if (sumWidthAt(hi) < available) {
            var h = hi
            repeat(8) {
                if (sumWidthAt(h) >= available) return@repeat
                h *= 1.8f
            }
            hi = h
        }

        val eps = 0.5f
        var iterations = 0
        while (hi - lo > eps && iterations < 24) {
            val mid = 0.5f * (lo + hi)
            if (sumWidthAt(mid) >= available) hi = mid else lo = mid
            iterations++
        }
        return hi.coerceAtLeast(minH)
    }
}
