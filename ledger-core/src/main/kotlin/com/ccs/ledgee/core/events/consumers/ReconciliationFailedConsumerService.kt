package com.ccs.ledgee.core.events.consumers

import com.ccs.ledgee.core.repositories.LedgerRecordStatus
import com.ccs.ledgee.core.repositories.LedgerRepository
import com.ccs.ledgee.events.LedgerEntriesReconciledEvent
import com.ccs.ledgee.events.LedgerEntryRecordedEvent
import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.kafka.streams.kstream.KStream
import org.springframework.context.annotation.Bean
import org.springframework.stereotype.Service
import java.util.function.Consumer

private val log = KotlinLogging.logger { }

@Service
class ReconciliationFailedConsumerService(
    private val ledgerRepository: LedgerRepository
) {

    @Bean
    fun reconciliationFailedConsumer() = Consumer<KStream<String, LedgerEntriesReconciledEvent>> { stream ->
        stream.foreach { key, record ->
            try {
                if (record.reconciliationStatus == LedgerRecordStatus.Unbalanced.name) {
                    ledgerRepository.findAllByExternalReferenceId(key)
                        .forEach { entry ->
                            ledgerRepository.save(entry.apply {
                                recordStatus = LedgerRecordStatus.Unbalanced
                            })
                        }
                } else {
                    record.incorrectDebits.lastOrNull()?.also { debit ->
                        updateStatusOfEntry(debit as LedgerEntryRecordedEvent)
                    }
                    record.incorrectCredits.lastOrNull()?.also { credit ->
                        updateStatusOfEntry(credit as LedgerEntryRecordedEvent)
                    }
                }
            } catch (e: IllegalStateException) {
                log.error(e) { "Error processing reconciliation failed event due to missing recordStatus value" }
            } catch (e: IllegalArgumentException) {
                log.error(e) { "Error processing reconciliation failed event due to incorrect recordStatus value" }
            }
        }
    }

    private fun updateStatusOfEntry(entry: LedgerEntryRecordedEvent) {
        val eventStatus = (entry.eventDetail.metadata["recordStatus"] as String?)
            ?: throw IllegalStateException("No recordStatus found for ${entry.publicId}")
        val newStatus = LedgerRecordStatus.valueOf(eventStatus)
        ledgerRepository.findByPublicId(entry.publicId)
            ?.also { dbEntry ->
                ledgerRepository.save(dbEntry.apply {
                    recordStatus = newStatus
                })
            }
    }
}