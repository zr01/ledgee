package com.ccs.ledgee.core.utils

import io.github.oshai.kotlinlogging.KotlinLogging
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

private val log = KotlinLogging.logger { }

class IdUtilsTest {

    @Test
    fun `should not clash id sequences`() {
        val idGenerator = IdGenerator(prefix = "te") {
            TimeUnit.MILLISECONDS.sleep(500L)
            val min = System.currentTimeMillis() % 1000000
            val max = min + 1000L
            min to max
        }

        val generated = mutableListOf<String>()
        var inc = 0
        while (inc < 60) {
            val sequences = (0..50)
                .map {
                    CompletableFuture.supplyAsync {
                        idGenerator.nextVal()
                    }
                }

            val gen = sequences.map { it.get() }
            generated.addAll(gen)
            inc++
        }

        log.info { "Generated ${generated.size} sequences" }

        assertThat(generated.size).isGreaterThan(1000)
        assertThat(
            generated.groupBy { it }
        ).allSatisfy { str, list ->
            list.size == 1
        }
    }
}