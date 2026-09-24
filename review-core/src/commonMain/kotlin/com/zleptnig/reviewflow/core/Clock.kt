package com.zleptnig.reviewflow.core

interface Clock {
    fun nowEpochMs(): Long
}

object SystemClock : Clock {
    override fun nowEpochMs(): Long = platformCurrentTimeMillis()
}

internal expect fun platformCurrentTimeMillis(): Long
