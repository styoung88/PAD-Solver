package com.example.phoneapp.util

import android.content.Intent
import android.content.res.Resources
import android.os.Build
import android.os.Parcelable

internal inline fun <reified T : Parcelable> Intent.parcelableExtra(key: String): T? =
    if (Build.VERSION.SDK_INT >= 33) getParcelableExtra(key, T::class.java) else {
        @Suppress("DEPRECATION")
        getParcelableExtra(key)
    }

internal fun Resources.dpToPx(value: Int): Int = (value * displayMetrics.density).toInt()
