package com.example.phoneapp.capture

import com.example.phoneapp.board.OrbColor

/** Artwork descriptors from the supplied PAD 0 captures; see docs/ORB_REFERENCES.md. */
internal object PadZeroOrbReferences {
    val hazards = HazardSymbolMatcher(
        side = 13,
        samplePitch = 0.052f,
        encodedSamples = listOf(
            // 1000013017.jpg, zero-based jammer cells 1 and 16.
            OrbColor.JAMMER to "66245362542764341352531334355424552445413446222544312212762673212114677777641142cb86678bb24554de767dd45523444555445322222453542222110153125101211512121214116651116011466",
            OrbColor.JAMMER to "66346363543664341361531434455424542455413445222544312213762673122114677777641141cb87678ba24556ee768ed45533444555444321222453542222211252135111201512121215116741116011566",
        ),
        thresholds = mapOf(OrbColor.JAMMER to 0.72f),
        refinedThresholds = mapOf(OrbColor.JAMMER to 0.76f),
        minimumMargin = 0.18f,
        // Ignore upper-corner badge artwork when comparing hazard symbols.
        // Combo bonuses are detected separately; locks are not tracked.
        ignoredSamples = buildSet {
            for (row in 0 until 6) for (column in 0 until 13) {
                if (column < 5 || column > 7) add(row * 13 + column)
            }
        },
    )

    val comboBonus = OrbMarkerTemplate(
        side = 13,
        samplePitch = 0.035f,
        encodedSamples = listOf(
            // First capture cell 7 and second capture cells 11 and 23.
            "977777777777aee96788876668eea7addeeb579eeeda7cbdc868eeec88caaca68dee8cbeccc768ed99babda9668e8bedaac87678c8adcdb978978c9a9aa767bc7896ccc6689ad885668898999c98899bbc8889ba8",
            "8777777777777ed87888876667dd97bdeeea667cccb88bcdc866bbba79aaac9669aa6bcebcb766ea79aabca9666e7bec9ab96666baaecdb878766bba9aa767a97677cbb65679a7655678957acc767ab9cb58ced86",
            "8777777777777dc777888766669977bceeea566765878bcdc86654467abaac9673336bcebbb7667549aabda966676bec9ab966677abecdb9677666b99aa766886657cbb6555796656676534568766456652446876",
            // 1000013021.jpg, wood orb in cell 14.
            "877777777778aed86788876778dd97addeeb568cccb87bbdc868bbca79baac968cb97bbecbc768ec99babda9568d8bed9ac97678c9adcdb979978b999aa767dc7887cbc6567ce98566876667be987789765678b99",
        ),
        xOffsets = floatArrayOf(.24f, .27f, .30f, .33f, .36f),
        yOffsets = floatArrayOf(-.32f, -.29f, -.26f, -.23f, -.20f),
        scales = floatArrayOf(0.9f, 1f, 1.1f),
        minimumCorrelation = 0.82f,
        // Compare the diagonal badge and its rim, excluding the surrounding
        // orb body. Heart backgrounds otherwise suppress a correct badge match.
        ignoredSamples = buildSet {
            for (row in 0 until 13) for (column in 0 until 13) {
                if (kotlin.math.abs(row + column - 12) > 4) add(row * 13 + column)
            }
        },
    )
}
