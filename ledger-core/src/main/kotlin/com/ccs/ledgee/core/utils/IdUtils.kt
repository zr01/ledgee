package com.ccs.ledgee.core.utils

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class IdGenerator(
    private val prefix: String = "dr",
    private val digits: Int = 7,
    private val reserveIds: () -> Pair<Long, Long>
) {
    var range: Pair<Long, Long> = reserveIds()
    var counter: AtomicLong = AtomicLong(range.first)
    var isRefreshing = false

    private fun AtomicLong.refreshGetAndIncrement(): Long {
        val seq = getAndIncrement()
        while (isRefreshing && seq >= range.second) {
            TimeUnit.MILLISECONDS.sleep(1)
        }

        if (seq >= range.second) {
            isRefreshing = true
            range = reserveIds()
            isRefreshing = false
        }
        return seq
    }

    fun nextVal(): String =
        "$prefix${currentDate()}${counter.refreshGetAndIncrement().toString().padStart(digits, '0')}"
}

fun currentDate(
    zoneId: ZoneId = ZoneId.systemDefault()
): String = LocalDate.now(zoneId)
    .format(DateTimeFormatter.ofPattern("yyMMdd"))

fun uuid(): UUID = UUID.randomUUID()
fun uuidStr(): String = uuid().toString()