package com.ccs.ledgee.core.services

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Service

private val log = KotlinLogging.logger { }

interface SequenceService {
    fun reserveAppAccountIds(range: Int = 5): List<Long>
    fun reserveAppLedgerIds(range: Int = 5): List<Long>
}

@Service
class SequenceServiceImpl(
    private val entityManager: EntityManager
) : SequenceService {

    @Suppress("UNCHECKED_CAST")
    override fun reserveAppAccountIds(range: Int): List<Long> {
        val ids = entityManager
            .createNativeQuery(
                """SELECT
                |setval('app_account_id_seq', (SELECT last_value + :range FROM app_account_id_seq), true) as end_value,
                |(SELECT last_value - :range + 1 FROM app_account_id_seq) as start_value
            """.trimMargin()
            )
            .setParameter("range", range)
            .resultList as List<Array<*>>
        return listOf(ids[0][1] as Long, ids[0][0] as Long)
    }

    @Suppress("UNCHECKED_CAST")
    override fun reserveAppLedgerIds(range: Int): List<Long> {
        val ids = entityManager
            .createNativeQuery(
                """SELECT
                    |setval('app_ledger_id_seq', (SELECT last_value + :range FROM app_ledger_id_seq), true) as end_value,
                    |(SELECT last_value - :range + 1 FROM app_ledger_id_seq) as start_value
                """.trimMargin()
            )
            .setParameter("range", range)
            .resultList as List<Array<*>>
        return listOf(ids[0][1] as Long, ids[0][0] as Long)
    }

}
