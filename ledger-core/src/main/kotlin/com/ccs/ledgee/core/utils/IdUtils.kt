package com.ccs.ledgee.core.utils

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

class IdGenerator(
    private val prefix: String = "dr",
    private val digits: Int = 7,
    private var range: Pair<Long, Long>
) {
    var counter: AtomicLong = AtomicLong(range.first)
    var rangeToExhaust: Pair<Long, Long>? = null

    fun assignNewRange(newMin: Long, newMax: Long) {
        rangeToExhaust = range
        range = newMin to newMax
    }

    fun nextVal(): String =
        "$prefix${currentDate()}${counter.incrementAndGet().toString().padStart(digits, '0')}"
}

fun currentDate(
    zoneId: ZoneId = ZoneId.systemDefault()
): String = LocalDate.now(zoneId)
    .format(DateTimeFormatter.ofPattern("yyMMdd"))

fun uuid(): UUID = UUID.randomUUID()
fun uuidStr(): String = uuid().toString()