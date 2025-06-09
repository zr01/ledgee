package com.ccs.ledgee.core.utils

import java.math.BigDecimal
import java.math.RoundingMode

enum class Iso4217Currency(
    val alphaCode: String,
    val numericCode: String,
    val d: Int
) {
    AUD("AUD", "036", 2);

    companion object {
        @JvmStatic
        fun getCurrency(currency: String) = entries.first {
            it.alphaCode.equals(currency, true) ||
                    it.numericCode.equals(currency)
        }
    }
}

fun Long.amountFor(currency: Iso4217Currency): BigDecimal = toBigDecimal()
    .setScale(2, RoundingMode.HALF_EVEN)
    .movePointLeft(currency.d)

fun BigDecimal.amountFor(currency: Iso4217Currency): Long = setScale(currency.d, RoundingMode.HALF_EVEN)
    .movePointRight(currency.d)
    .toLong()