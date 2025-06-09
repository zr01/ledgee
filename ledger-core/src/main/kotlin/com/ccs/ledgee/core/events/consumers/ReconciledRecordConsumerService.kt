package com.ccs.ledgee.core.events.consumers

import com.ccs.ledgee.core.repositories.LedgerRecordStatus
import com.ccs.ledgee.core.repositories.LedgerRepository
import com.ccs.ledgee.events.LedgerEntriesReconciledEvent
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.oshai.kotlinlogging.withLoggingContext
import org.apache.kafka.streams.kstream.KStream
import org.springframework.context.annotation.Bean
import org.springframework.stereotype.Service
import java.time.OffsetDateTime
import java.util.function.Consumer

private val log = KotlinLogging.logger { }
private const val RECONCILED_BY = "reconciled-records-consumer"

@Service
class ReconciledRecordConsumerService(
    private val ledgerRepository: LedgerRepository
) {

    @Bean
    fun reconciledRecordsConsumer(): Consumer<KStream<String, LedgerEntriesReconciledEvent>> = Consumer { stream ->
        stream.foreach { externalReferenceId, event ->
            withLoggingContext("externalRefId" to externalReferenceId) {
                if (event.reconciliationStatus == LedgerRecordStatus.Balanced.name) {
                    ledgerRepository.findAllByExternalReferenceId(externalReferenceId)
                        .forEach { entry ->
                            ledgerRepository.save(entry.apply {
                                recordStatus = LedgerRecordStatus.Balanced
                                reconciledOn = OffsetDateTime.now()
                                reconciledBy = RECONCILED_BY
                            })

                            // Raise Audit

                            log.info { "Records balanced for $externalReferenceId" }
                        }
                } else if (event.reconciliationStatus == LedgerRecordStatus.WaitingForPair.name) {
                    val entry = event.creditEntry ?: event.debitEntry ?: throw IllegalStateException("No entry found")
                    ledgerRepository
                        .findByPublicId(entry.publicId)
                        ?.also { dbEntry ->
                            ledgerRepository.save(dbEntry.apply {
                                recordStatus = LedgerRecordStatus.WaitingForPair
                            })

                            // Raise Audit
                            log.info { "Record waiting for pair for $externalReferenceId" }
                        }
                }
            }
        }
    }
}