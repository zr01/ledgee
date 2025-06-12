package com.ccs.ledgee.core.utils

import org.sqids.Sqids
import java.util.*
import java.util.concurrent.atomic.AtomicLong

class IdGenerator(
    private val prefix: String = "dr",
    minChars: Int = 4,
    private val reserveIds: () -> Pair<Long, Long>
) {
    var range: Pair<Long, Long> = reserveIds()
    var counter: AtomicLong = AtomicLong(range.first)
    private val sqids = Sqids(
        alphabet = "ABCDFGHKMNPRSTVWX23456789",
        minLength = minChars
    )

    @Synchronized
    private fun AtomicLong.refreshGetAndIncrement(): Long {
        var seq = getAndIncrement()
        if (seq >= range.second) {
            range = reserveIds()
            counter.set(range.first)
            seq = counter.getAndIncrement()
        }
        return seq
    }

    private fun formPrefix(
        subPrefix: String,
        isPrefixHyphenated: Boolean
    ) = when {
        isPrefixHyphenated && subPrefix.isNotBlank() ->
            "$prefix-$subPrefix-"

        isPrefixHyphenated && subPrefix.isBlank() ->
            "$prefix-"

        !isPrefixHyphenated && subPrefix.isNotBlank() ->
            "$prefix$subPrefix"

        else -> prefix
    }

    fun nextVal(
        compositeKey: Long? = null,
        subPrefix: String = "",
        isPrefixHyphenated: Boolean = true
    ): String = sqids.encode(
        if (compositeKey == null)
            listOf(counter.refreshGetAndIncrement())
        else
            listOf(counter.refreshGetAndIncrement(), compositeKey)
    )
        .let { encodedStr ->
            "${formPrefix(subPrefix, isPrefixHyphenated)}$encodedStr"
        }
}

fun uuid(): UUID = UUID.randomUUID()
fun uuidStr(): String = uuid().toString()