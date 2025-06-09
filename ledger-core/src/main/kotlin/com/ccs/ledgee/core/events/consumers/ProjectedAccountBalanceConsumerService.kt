package com.ccs.ledgee.core.events.consumers

import com.ccs.ledgee.core.repositories.IsPending
import com.ccs.ledgee.core.repositories.LedgerEntryType
import com.ccs.ledgee.core.services.VirtualAccountService
import com.ccs.ledgee.events.LedgerEntryRecordedEvent
import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.kafka.streams.kstream.KStream
import org.springframework.context.annotation.Bean
import org.springframework.stereotype.Service
import java.util.function.Consumer

private val log = KotlinLogging.logger { }

@Service
class ProjectedAccountBalanceConsumerService(
    private val virtualAccountService: VirtualAccountService
) {

    @Bean
    fun projectedAccountBalanceConsumer(): Consumer<KStream<String, LedgerEntryRecordedEvent>> = Consumer { stream ->
        stream
            .foreach { _, event ->
                virtualAccountService
                    .updateAccountBalance(
                        event.publicAccountId,
                        LedgerEntryType.valueOf(event.entryType),
                        IsPending.valueOf(event.isPending),
                        event.amount
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