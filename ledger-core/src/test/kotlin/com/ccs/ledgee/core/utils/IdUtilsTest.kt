package com.ccs.ledgee.core.utils

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

private val log = KotlinLogging.logger { }

class IdUtilsTest {

    @Test
    fun `should not clash id sequences`() {
        val batch = 50L
        var sequence = 0L
        val idGenerator = IdGenerator(prefix = "te", minChars = 4) {
            val min = sequence
            val max = sequence + batch
            sequence += batch
            min to max
        }

        val generated = runBlocking {
            generateSequencesByAccount() {
                idGenerator.nextVal()
            }
        }

        log.info { "Generated ${generated.size} sequences" }
        log.info { "First: ${generated.first()}, Last: ${generated.last()}" }

        assertThat(generated.size).isGreaterThan(1000)
        assertThat(
            generated.groupBy { it }
        ).allSatisfy { _, list ->
            list.size == 1
        }
    }

    private suspend fun generateSequencesByAccount(
        numOfAccounts: Int = 500,
        parallelWorkers: Int = 50,
        work: () -> String
    ) = coroutineScope {
        (0..numOfAccounts).map { acct ->
            async {
                (0..parallelWorkers).asFlow()
                    .map {
                        async {
                            work()
                        }
                    }
                    .map { it.await() }
                    .toList()
            }
        }
    }.awaitAll()
        .reduce { acc, list -> acc + list }
}