package com.zleptnig.reviewflow.sampleapp

private val NO_OP_REGEX = Regex("""\bisNoOp\s*=\s*(true|false)\b""")

internal fun parseNoOpFlagFromReviewInfoString(raw: String): Boolean? {
    val match = NO_OP_REGEX.find(raw) ?: return null
    return when (match.groupValues[1]) {
        "true" -> true
        "false" -> false
        else -> null
    }
}
