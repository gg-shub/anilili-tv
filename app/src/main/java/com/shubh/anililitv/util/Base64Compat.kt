package com.shubh.anililitv.util

import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString

internal object Base64Compat {
    fun decode(value: String): ByteArray =
        value.decodeBase64()?.toByteArray() ?: throw IllegalArgumentException("Invalid Base64")

    fun encode(value: ByteArray): String = value.toByteString().base64()
}
