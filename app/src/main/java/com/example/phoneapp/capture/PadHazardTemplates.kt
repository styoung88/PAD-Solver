package com.example.phoneapp.capture

import com.example.phoneapp.board.OrbColor

/** PAD hazard reference artwork; see docs/ORB_REFERENCES.md. */
internal object PadHazardTemplates {
    val matcher = HazardSymbolMatcher(
        side = 13,
        samplePitch = 0.052f,
        encodedSamples = listOf(
            OrbColor.JAMMER to "402246362210756611664126663438411166442221771167712212277777763111b367777755b00ddc9767add6263eec355eeb3625435555435432232555552322211551245411201043101430113112202122211",
            OrbColor.POISON to "562dfffff946316e87efb7af214b3124b5113f1f11123b11126cf11118f01114ff5112f9e011cfefeee326fdffa4effc111effa519fffefefff500dfebefdbff800efe3efd8ff9106662666466400011011001100",
            OrbColor.LETHAL_POISON to "14c3058603c51300bdddddb004a1edeeeeede1b2c136ded632c2390023a3200948c0000e0000c93bb01e7e209b403cef202ffc30019de353ed91021acaefeaca11619b7beb6ba1720221223122031251000001621",
            OrbColor.BOMB to "000073a860000000343a564000004144566140004111227833304111222745534211233434ee4431133333388434114333333333365333333367337bc74347cb8342dbc737cbd435214445445335b44cccbccc64a",
            OrbColor.BOMB to "ffff6777a5fffffff64a57fffffff2449663ffffd311356724bfe41112285434f211143345e633631233334b536632333333344668c6433346c9665bbb939bbb56a34c74446c53bf5239bcb9544ffc633444546af",
        ),
        thresholds = mapOf(OrbColor.JAMMER to 0.60f, OrbColor.POISON to 0.78f,
            OrbColor.LETHAL_POISON to 0.78f, OrbColor.BOMB to 0.78f),
        refinedThresholds = mapOf(OrbColor.JAMMER to 0.65f),
        minimumMargin = 0.15f,
    )
}
