package com.ccs.ledgee.core.events.consumers

import com.ccs.ledgee.core.repositories.BalanceType
import com.ccs.ledgee.core.repositories.LedgerRepository
import com.ccs.ledgee.core.repositories.VirtualAccountsRepository
import com.ccs.ledgee.core.services.VirtualAccountBalanceService
import com.ccs.ledgee.events.LedgerEntryRecordedEvent
import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.kafka.streams.kstream.KStream
import org.springframework.context.annotation.Bean
import org.springframework.stereotype.Service
import java.util.function.Consumer

private val log = KotlinLogging.logger { }

@Service
class ProjectedAccountBalanceConsumerService(
    private val virtualAccountsRepository: VirtualAccountsRepository,
    private val ledgerRepository: LedgerRepository,
    private val virtualAccountBalanceService: VirtualAccountBalanceService
) {

    @Bean
    fun projectedAccountBalanceConsumer(): Consumer<KStream<String, LedgerEntryRecordedEvent>> = Consumer { stream ->
        stream
            .foreach { _, event ->
                val account = virtualAccountsRepository
                    .findByPublicId(event.publicAccountId)
                    ?: throw IllegalStateException("Virtual Account does not exist [${event.publicAccountId}]")
                val entry = ledgerRepository.findByPublicId(event.publicId)
                    ?: throw IllegalStateException("Ledger entry does not exist [${event.publicId}]")

                virtualAccountBalanceService
                    .updateAccountBalance(
                        account,
                        BalanceType.Projected,
                        listOf(entry)
                    )

                log.atInfo {
                    message = "Projected account balance updated"
                    payload = mapOf(
                        "publicId" to event.publicId,
                        "amount" to event.amount
                    )
                }
            }
    }
}
