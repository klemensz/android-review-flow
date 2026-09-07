package com.zleptnig.reviewflow.core

import platform.Foundation.NSDate

internal actual fun platformCurrentTimeMillis(): Long {
    val secondsSinceUnixEpoch =
        NSDate().timeIntervalSinceReferenceDate() + UNIX_TO_APPLE_REFERENCE_SECONDS
    return (secondsSinceUnixEpoch * MILLIS_PER_SECOND).toLong()
}

private const val UNIX_TO_APPLE_REFERENCE_SECONDS = 978_307_200.0
private const val MILLIS_PER_SECOND = 1_000.0
