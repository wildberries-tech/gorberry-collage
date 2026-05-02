package ru.wildberries.collage.strategy

/**
 * weights and flags for plans score
 *
 * Interpretation:
 * - lambdaCrop — weight of the crop penalty (COVER mode)
 * - lambdaGap — weight of the gap/empty-space penalty (CONTAIN mode)
 * - cropCutoff — if crop exceeds this threshold and its penalty >= contain, prefer switching to CONTAIN
 * - gapCutoff — if gap exceeds this threshold and its penalty >= cover, prefer switching to COVER
 * - cropPow — exponent applied to crop, emphasizes large crops
 * - gapPow — exponent applied to gap (typically 1.25)
 * - extremeCropHard — hard threshold for too much crop, above this an extra strong penalty is added
 * - extremeCropAlpha — coefficient for the extra penalty when crop is above extremeCropHard
 * - mismatchAmplify — how much to amplify cropPow when image and box aspect ratios mismatch
 *
 * NOtes:
 * - All default parameters was test by visual control for COVER/CONTAIN
 * - Work with LUT in DefaultTileScorer
 */
data class FitWeights(
    val lambdaCrop: Float = 2.8f,
    val lambdaGap: Float = 0.08f,

    val cropCutoff: Float = 0.30f,
    val gapCutoff: Float = 0.95f,

    val cropPow: Float = 1.7f,
    val gapPow: Float = 1.1f,

    val extremeCropHard: Float = 0.34f,
    val extremeCropAlpha: Float = 6.0f,

    val mismatchAmplify: Float = 0.5f,

    val planningAspectLimit: Float = 1.8f,
    val freeCropAspectLimit: Float = 1.5f,
) {

    init {
        require(lambdaCrop >= 0f) { "lambdaCrop must be >= 0" }
        require(lambdaGap >= 0f) { "lambdaGap must be >= 0" }
        require(cropCutoff in 0f..1f) { "cropCutoff must be in [0,1]" }
        require(gapCutoff in 0f..1f) { "gapCutoff must be in [0,1]" }
        require(cropPow > 0f) { "cropPow must be > 0" }
        require(gapPow > 0f) { "gapPow must be > 0" }
        require(extremeCropHard in 0f..1f) { "extremeCropHard must be in [0,1]" }
        require(extremeCropAlpha >= 0f) { "extremeCropAlpha must be >= 0" }
        require(mismatchAmplify >= 0f) { "mismatchAmplify must be >= 0" }
        require(planningAspectLimit >= 1f) { "planningAspectLimit must be >= 1" }
        require(freeCropAspectLimit >= 1f) { "freeCropAspectLimit must be >= 1" }
    }

    companion object {
        val Default: FitWeights = FitWeights()
    }
}
