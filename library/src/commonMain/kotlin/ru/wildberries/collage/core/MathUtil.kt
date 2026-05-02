package ru.wildberries.collage.core

import kotlin.math.pow

object MathUtil {

    fun aspect(width: Float, height: Float): Float {
        val safeW = if (width > 1f) width else 1f
        val safeH = if (height > 1f) height else 1f
        val ratio = safeW / safeH
        return if (ratio >= 1e-6f) ratio else 1e-6f
    }

    /** symmetric round to int */
    fun fastRoundToInt(value: Float): Int =
        if (value >= 0f) (value + 0.5f).toInt() else (value - 0.5f).toInt()

    /**
     * Quantize only W,H into 16 byte size mocponents and boxing into 32 byte size key
     */
    fun quantizeBoxKeyWH(w: Float, h: Float, step: Float): Int {
        val qw = fastRoundToInt(w / step).coerceIn(0, 0xFFFF)
        val qh = fastRoundToInt(h / step).coerceIn(0, 0xFFFF)
        return (qw shl 16) or qh
    }

    fun quantizeAspectKey(width: Float, height: Float, step: Float = 0.01f): Int {
        val a = aspect(width, height)
        return fastRoundToInt(a / step).coerceIn(0, 0x7FFF_FFFF)
    }

    fun mixPhotoKey(imageId: Int, width: Float, height: Float): Int {
        val qa = quantizeAspectKey(width, height)
        return (imageId * 73856093) xor (qa * 19349663)
    }

    fun clampAspectForPlanning(
        aspect: Float,
        maxAspect: Float = 2.0f,
    ): Float {
        val a = aspect.coerceAtLeast(1e-6f)
        val hi = maxAspect.coerceAtLeast(1f)
        return a.coerceIn(1f / hi, hi)
    }

    fun normalizeCropRatioAfterFreeCropAllowance(
        rawCropRatio: Float,
        imageAspectRatio: Float,
        boxAspectRatio: Float,
        freeCropAspectLimit: Float,
    ): Float {
        val safeRawCropRatio = rawCropRatio.coerceIn(0f, 1f)

        val freeCropRatio = directionalFreeCropRatioForAspectLimit(
            rawCropRatio = safeRawCropRatio,
            imageAspectRatio = imageAspectRatio,
            boxAspectRatio = boxAspectRatio,
            freeCropAspectLimit = freeCropAspectLimit,
        )

        if (freeCropRatio <= 1e-6f) {
            return safeRawCropRatio
        }

        if (freeCropRatio >= 0.999f) {
            return 0f
        }

        return ((safeRawCropRatio - freeCropRatio) / (1f - freeCropRatio))
            .coerceIn(0f, 1f)
    }

    private fun directionalFreeCropRatioForAspectLimit(
        rawCropRatio: Float,
        imageAspectRatio: Float,
        boxAspectRatio: Float,
        freeCropAspectLimit: Float,
    ): Float {
        val safeImageAspectRatio = imageAspectRatio.coerceAtLeast(1e-6f)
        val safeBoxAspectRatio = boxAspectRatio.coerceAtLeast(1e-6f)
        val safeAspectLimit = freeCropAspectLimit.coerceAtLeast(1f)
        val inverseAspectLimit = 1f / safeAspectLimit

        val freeCropRatio = when {
            safeImageAspectRatio > safeAspectLimit -> {
                freeCropForWideImage(
                    rawCropRatio = rawCropRatio,
                    imageAspectRatio = safeImageAspectRatio,
                    boxAspectRatio = safeBoxAspectRatio,
                    freeCropAspectLimit = safeAspectLimit,
                )
            }

            safeImageAspectRatio < inverseAspectLimit -> {
                freeCropForTallImage(
                    rawCropRatio = rawCropRatio,
                    imageAspectRatio = safeImageAspectRatio,
                    boxAspectRatio = safeBoxAspectRatio,
                    inverseFreeCropAspectLimit = inverseAspectLimit,
                )
            }

            else -> 0f
        }

        return freeCropRatio.coerceIn(0f, 0.95f)
    }

    private fun freeCropForWideImage(
        rawCropRatio: Float,
        imageAspectRatio: Float,
        boxAspectRatio: Float,
        freeCropAspectLimit: Float,
    ): Float {
        return when {
            // такую обрезку бесплатной не считаем.
            boxAspectRatio >= imageAspectRatio -> 0f

            // box между исходным и 3:2, crop движется в сторону нормализации.
            boxAspectRatio >= freeCropAspectLimit -> rawCropRatio

            // box уже уже 3:2, бесплатна только часть до 3:2
            else -> 1f - freeCropAspectLimit / imageAspectRatio
        }
    }

    private fun freeCropForTallImage(
        rawCropRatio: Float,
        imageAspectRatio: Float,
        boxAspectRatio: Float,
        inverseFreeCropAspectLimit: Float,
    ): Float {
        return when {
            // такую обрезку бесплатной не считаем
            boxAspectRatio <= imageAspectRatio -> 0f

            // box между исходным и 2:3, crop движется в сторону нормализации
            boxAspectRatio <= inverseFreeCropAspectLimit -> rawCropRatio

            // box шире 2:3, бесплатна только часть до 2:3
            else -> 1f - imageAspectRatio / inverseFreeCropAspectLimit
        }
    }

    /**
     * Lookup table for pow(value, exponent) on value ∈ [0, 1]
     * Used for crop and gap penalties to avoid repeated platform pow calls
     */
    class PowerLookupTable(
        private val sampleCount: Int = 512,
        private val exponentMinimum: Float = 1.0f,
        exponentMaximum: Float = 3.5f,
        private val exponentStep: Float = 0.1f,
    ) {

        private val exponentBucketCount: Int =
            ((exponentMaximum - exponentMinimum) / exponentStep).toInt() + 1

        private val powerValuesByExponent: Array<FloatArray> =
            Array(exponentBucketCount) { FloatArray(sampleCount) }

        init {
            var exponent = exponentMinimum
            var exponentIndex = 0

            while (exponentIndex < exponentBucketCount) {
                var sampleIndex = 0

                while (sampleIndex < sampleCount) {
                    val value = sampleIndex.toFloat() / (sampleCount - 1).toFloat()
                    powerValuesByExponent[exponentIndex][sampleIndex] =
                        value.toDouble().pow(exponent.toDouble()).toFloat()
                    sampleIndex++
                }

                exponent += exponentStep
                exponentIndex++
            }
        }

        /** Bilineal interpolation pow(valueInput, exponentInput) by 2D table */
        fun power(
            valueInput: Float,
            exponentInput: Float,
        ): Float {
            val clampedValue = valueInput.coerceIn(0f, 1f)
            val scaledValue = clampedValue * (sampleCount - 1)

            val valueIndex = scaledValue.toInt().coerceIn(0, sampleCount - 2)
            val valueFraction = scaledValue - valueIndex

            val scaledExponent = ((exponentInput - exponentMinimum) / exponentStep)
                .coerceIn(0f, (exponentBucketCount - 1).toFloat())

            val exponentIndex = scaledExponent.toInt().coerceIn(0, exponentBucketCount - 2)
            val exponentFraction = scaledExponent - exponentIndex

            val leftLower = powerValuesByExponent[exponentIndex][valueIndex]
            val rightLower = powerValuesByExponent[exponentIndex][valueIndex + 1]
            val leftUpper = powerValuesByExponent[exponentIndex + 1][valueIndex]
            val rightUpper = powerValuesByExponent[exponentIndex + 1][valueIndex + 1]

            val lower = leftLower + (rightLower - leftLower) * valueFraction
            val upper = leftUpper + (rightUpper - leftUpper) * valueFraction

            return lower + (upper - lower) * exponentFraction
        }
    }
}
