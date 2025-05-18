package com.ccs.ledgee.core.controllers

import com.ccs.ledgee.core.repositories.IsPending
import com.ccs.ledgee.core.repositories.LedgerEntity
import com.ccs.ledgee.core.repositories.LedgerEntryType
import com.ccs.ledgee.core.repositories.LedgerRecordStatus
import com.ccs.ledgee.core.repositories.LedgerRepository
import com.ccs.ledgee.core.utils.uuidStr
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.oshai.kotlinlogging.withLoggingContext
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.concurrent.CompletableFuture
import kotlin.random.Random

private val log = KotlinLogging.logger { }
private val randomMerchants = (1..5).map { uuidStr() }

@RestController
@RequestMapping("/simulator")
class SimulatorController(
    private val ledgerRepository: LedgerRepository
) {

    @PostMapping("/records/create")
    fun postCreateRecords(
        @RequestParam(name = "is_pair", required = false, defaultValue = "true") isPair: Boolean = true,
        @RequestParam(name = "upper_bound", required = false, defaultValue = "100000") upperBound: Int = 100000,
        @RequestParam(name = "mod_acct_by", required = false, defaultValue = "10000") modAcctBy: Int = 10000
    ) = SimulatorResponse(isPair, upperBound)
        .also { received ->
            withLoggingContext(
                "isPair" to received.isPair.toString(),
                "upperBound" to received.upperBound.toString()
            ) {
                log.info { "Simulator Request received" }
                CompletableFuture.runAsync {
                    (1..received.upperBound).forEach { iter ->
                        val accountId = Random.nextInt(1, modAcctBy) % modAcctBy
                        val merchantId = randomMerchants.random()
                        val amount = Random.nextInt(100, 100000)
                        val isPending = Random.nextInt(100) < 15
                        val externalReferenceId = uuidStr()

                        withLoggingContext(
                            "accountId" to accountId.toString(),
                            "merchantId" to merchantId,
                            "externalReferenceId" to externalReferenceId
                        ) {
                            val entries = mutableListOf<LedgerEntity>()
                            val debitRecord = LedgerEntity(
                                accountId = accountId.toString(),
                                amount = amount.toLong(),
                                isPending = if (isPending) IsPending.Yes else IsPending.No,
                                externalReferenceId = externalReferenceId,
                                recordStatus = LedgerRecordStatus.Staged,
                                createdBy = "simulator"
                            )
                            entries.add(debitRecord)
                            if (isPair) {
                                val creditRecord = debitRecord.copy(
                                    accountId = merchantId,
                                    entryType = LedgerEntryType.CreditRecord
                                )
                                entries.add(creditRecord)
                            }

                            ledgerRepository.saveAll(entries)
                            log.info { "Created records" }
                        }
                    }
                }
            }
        }
}

data class SimulatorResponse(
    val isPair: Boolean,
    val upperBound: Int
)