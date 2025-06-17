package com.ccs.ledgee.core.events.consumers

import com.ccs.ledgee.core.repositories.LedgerRecordStatus
import com.ccs.ledgee.core.repositories.LedgerRepository
import com.ccs.ledgee.events.LedgerEntriesReconciledEvent
import com.ccs.ledgee.events.LedgerEntryRecordedEvent
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
            processRecord(externalReferenceId, event)
        }
    }

    fun processRecord(externalReferenceId: String, event: LedgerEntriesReconciledEvent) {
        withLoggingContext("externalRefId" to externalReferenceId) {
            event
                .ledgerEntries
                .forEach { entry ->
                    val ledgerEntry = entry as LedgerEntryRecordedEvent
                    val dbLedgerEntry = ledgerRepository.findByPublicId(ledgerEntry.publicId)
                        ?: throw IllegalStateException("Ledger entry not found")
                    if (dbLedgerEntry.recordStatus.name != ledgerEntry.recordStatus) {
                        ledgerRepository.save(
                            dbLedgerEntry.apply {
                                recordStatus = LedgerRecordStatus.valueOf(ledgerEntry.recordStatus)
                                if (ledgerEntry.recordStatus == LedgerRecordStatus.Balanced.name) {
                                    reconciledOn = OffsetDateTime.now()
                                    reconciledBy = RECONCILED_BY
                                }
                            }
                        )
                    }
                }
        }
    }
}