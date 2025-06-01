package com.ccs.ledgee.core.controllers

import com.ccs.ledgee.core.controllers.models.LedgerDto
import com.ccs.ledgee.core.repositories.LedgerEntryType
import com.ccs.ledgee.core.services.LedgerService
import com.ccs.ledgee.core.services.SequenceService
import com.ccs.ledgee.core.utils.uuidStr
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.oshai.kotlinlogging.withLoggingContext
import org.springframework.web.bind.annotation.GetMapping
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
    private val ledgerService: LedgerService,
    private val sequenceService: SequenceService
) {

    @GetMapping("/sequence")
    fun getSequence() = SimulatorResponse(true, 1)
        .also {
            sequenceService.reserveAppAccountIds(5)
        }

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
                    (1..received.upperBound).forEach { _ ->
                        val accountId = Random.nextInt(1, modAcctBy) % modAcctBy
                        val productCode = if (accountId % 2 == 0) "banking" else "creditcard"
                        val merchantId = randomMerchants.random()
                        val merchProductCode = "business"
                        val amount = Random.nextInt(100, 100000)
                        val isPending = Random.nextInt(100) < 15
                        val externalReferenceId = uuidStr()

                        withLoggingContext(
                            "accountId" to accountId.toString(),
                            "merchantId" to merchantId,
                            "externalReferenceId" to externalReferenceId
                        ) {
                            val debitEntry = LedgerDto(
                                amount = amount.toLong(),
                                productCode = productCode,
                                isPending = isPending,
                                externalReferenceId = externalReferenceId,
                                description = uuidStr(),
                                createdBy = "simulator",
                            )

                            ledgerService.postLedgerEntry(
                                accountId.toString(),
                                entryType = LedgerEntryType.DebitRecord,
                                ledgerDto = debitEntry,
                                createdBy = debitEntry.createdBy
                            )

                            if (isPair) {
                                val creditEntry = debitEntry.copy(
                                    productCode = merchProductCode
                                )
                                ledgerService.postLedgerEntry(
                                    merchantId,
                                    entryType = LedgerEntryType.CreditRecord,
                                    ledgerDto = creditEntry,
                                    createdBy = creditEntry.createdBy
                                )
                            }

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