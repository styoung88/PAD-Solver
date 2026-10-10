package com.example.phoneapp.capture

import com.example.phoneapp.board.OrbColor
import kotlin.math.sqrt

/** Central symbol luminance samples from PAD orb reference artwork; see docs/ORB_REFERENCES.md. */
internal object OrbSymbolTemplates {
    const val SIDE = 13
    const val SAMPLE_PITCH = 0.052f
    private val samples = listOf(
        OrbColor.FIRE to "cc999999999cc998aa7a7aa89978799999997875a9797b7979a545a78bab87a54368679c976863578867876887566588888885663659888889563325588888552342314696413246427837387356b75325442577b",
        OrbColor.WATER to "ddcbbbebbbcddbbbaa9d9aabbb9aa98ccc89aaa8a988bdc989a7769bb8ddbb96765499eee9945654a6998996b454677723288774494c8a2a7c4a338ccb839acc8347696929696846436422246446b76422222677b",
        OrbColor.WOOD to "bdb9abcccabdb9baca8889b9b97bd8797778bb76dba8699ab9c56bbb8dcccbc9c7bb4bededbbc57aa3babb499457b736abb923456b834cbbaa54549a3757ba744555b8b79233375755aa33878867b77569997788b",
        OrbColor.LIGHT to "dedddcecdddedcdedcceccdedcbeecadedaceebbececbdbbeddbb8cdeeeeecc8b79b9eefeeab97eddcdeeedddde68c9ddeddac96a66aeddde966a897daada9d9896bd85bdc58eb675a846d748b67b87793e39988b",
        OrbColor.DARK to "aa7689aa877aa777a9a766677757999457ab6752b99485a9579367a87a5b68b8887738b6986259877116865687287726314462865775577838187287714517827244777118872246338783112246a65347774567a",
        OrbColor.HEART to "dcbabc8cbabcdca999bbb999aca99988988999ab99888788889b8a987777789a8669987778996666555666555667555555555556b55544444555a6954444444596649544444595575486343584569663a838a3668",
        OrbColor.JAMMER to "402246362210756611664126663438411166442221771167712212277777763111b367777755b00ddc9767add6263eec355eeb3625435555435432232555552322211551245411201043101430113112202122211",
        OrbColor.POISON to "562dfffff946316e87efb7af214b3124b5113f1f11123b11126cf11118f01114ff5112f9e011cfefeee326fdffa4effc111effa519fffefefff500dfebefdbff800efe3efd8ff9106662666466400011011001100",
        OrbColor.LETHAL_POISON to "14c3058603c51300bdddddb004a1edeeeeede1b2c136ded632c2390023a3200948c0000e0000c93bb01e7e209b403cef202ffc30019de353ed91021acaefeaca11619b7beb6ba1720221223122031251000001621",
        OrbColor.BOMB to "000073a860000000343a564000004144566140004111227833304111222745534211233434ee4431133333388434114333333333365333333367337bc74347cb8342dbc737cbd435214445445335b44cccbccc64a",
        OrbColor.BOMB to "ffff6777a5fffffff64a57fffffff2449663ffffd311356724bfe41112285434f211143345e633631233334b536632333333344668c6433346c9665bbb939bbb56a34c74446c53bf5239bcb9544ffc633444546af",
    ).map { (color, encoded) -> color to normalize(FloatArray(encoded.length) { encoded[it].digitToInt(16).toFloat() }) }

    fun classify(values: FloatArray): Pair<OrbColor, Float>? {
        val scores = FloatArray(OrbColor.entries.size)
        accumulateScores(values, scores)
        return selectHazard(scores)
    }

    /** Keep the strongest pose for every color, including the ordinary competitors. */
    fun accumulateScores(values: FloatArray, scores: FloatArray) {
        val normalized = normalize(values)
        for ((color, template) in samples) {
            var correlation = 0f
            for (index in normalized.indices) correlation += normalized[index] * template[index]
            scores[color.ordinal] = maxOf(scores[color.ordinal], correlation)
        }
    }

    fun selectHazard(scores: FloatArray, jammerThreshold: Float = 0.60f): Pair<OrbColor, Float>? {
        var bestColor = OrbColor.UNKNOWN
        var best = 0f
        var second = 0f
        for (color in OrbColor.entries) {
            val correlation = scores[color.ordinal]
            if (correlation > best) {
                second = best
                best = correlation
                bestColor = color
            } else if (correlation > second) second = correlation
        }
        val threshold = if (bestColor == OrbColor.JAMMER) jammerThreshold else 0.78f
        return if (bestColor in HAZARDS && best >= threshold && best - second >= 0.15f) bestColor to best else null
    }

    private fun normalize(values: FloatArray): FloatArray {
        val mean = values.average().toFloat()
        var norm = 0f
        for (index in values.indices) {
            values[index] -= mean
            norm += values[index] * values[index]
        }
        norm = sqrt(norm).coerceAtLeast(0.0001f)
        for (index in values.indices) values[index] /= norm
        return values
    }

    private val HAZARDS = setOf(OrbColor.JAMMER, OrbColor.POISON, OrbColor.LETHAL_POISON, OrbColor.BOMB)
}
