package com.ccs.ledgee.core.events.processors

import com.ccs.ledgee.core.batch.jobs.LedgerError
import com.ccs.ledgee.core.repositories.LedgerEntryType
import com.ccs.ledgee.core.repositories.LedgerRecordStatus
import com.ccs.ledgee.events.LedgerEntriesReconciledEvent
import com.ccs.ledgee.events.LedgerEntryRecordedEvent
import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.kafka.streams.KeyValue
import org.apache.kafka.streams.kstream.KStream
import org.springframework.context.annotation.Bean
import org.springframework.stereotype.Service
import java.util.function.Function

private val log = KotlinLogging.logger { }

@Service
class LedgerRecordProcessorsService {

    @Bean
    fun ledgerRerouteProcessor() =
        Function<KStream<String, LedgerEntryRecordedEvent>, KStream<String, LedgerEntryRecordedEvent>> { stream ->
            stream.map { key, value ->
                KeyValue(value.externalReferenceId, value)
            }
        }

    @Bean
    fun bookkeepingWaitForReconciliationProcessor() =
        Function<KStream<String, LedgerEntryRecordedEvent>, Array<KStream<String, LedgerEntriesReconciledEvent>>> { stream ->
            stream
                .groupByKey()
                .aggregate({
                    LedgerEntriesReconciledEvent.newBuilder()
                        .setDebitEntry(null)
                        .setCreditEntry(null)
                        .setIncorrectDebits(mutableListOf())
                        .setIncorrectCredits(mutableListOf())
                        .setReconciliationStatus(null)
                        .build()
                }, { externalRefId, entry, record ->
                    try {
                        when {
                            entry.entryType == LedgerEntryType.DebitRecord.name &&
                                    record.debitEntry != null &&
                                    entry.publicId != record.debitEntry.publicId ->
                                throw LedgerReconciliationException(
                                    entry = entry,
                                    errorCode = LedgerError.EXCESS_DEBIT_RECORDS
                                )

                            entry.entryType == LedgerEntryType.DebitRecord.name &&
                                    record.debitEntry == null -> record.apply {
                                debitEntry = entry
                            }

                            entry.entryType == LedgerEntryType.CreditRecord.name &&
                                    record.creditEntry != null &&
                                    entry.publicId != record.creditEntry.publicId ->
                                throw LedgerReconciliationException(
                                    entry = entry,
                                    errorCode = LedgerError.EXCESS_CREDIT_RECORDS
                                )

                            entry.entryType == LedgerEntryType.CreditRecord.name &&
                                    record.creditEntry == null -> record.apply { creditEntry = entry }

                            else -> record
                        }.apply {
                            reconciliationStatus = if (creditEntry != null && debitEntry != null) {
                                if (creditEntry.amount != debitEntry.amount)
                                    throw LedgerReconciliationException(
                                        entry = entry,
                                        errorCode = LedgerError.NOT_ZERO_SUM
                                    )
                                else LedgerRecordStatus.Balanced.name
                            } else {
                                LedgerRecordStatus.WaitingForPair.name
                            }
                        }
                    } catch (e: LedgerReconciliationException) {
                        record.enrichFromError(e)
                    }
                }).toStream()
                .split()
                .branch { _, r ->
                    // Reconciled/Waiting Topic
                    r.reconciliationStatus == LedgerRecordStatus.Balanced.name || r.reconciliationStatus == LedgerRecordStatus.WaitingForPair.name
                }
                .branch { _, r ->
                    // DLQ
                    r.reconciliationStatus != LedgerRecordStatus.Balanced.name && r.reconciliationStatus != LedgerRecordStatus.WaitingForPair.name
                }
                .noDefaultBranch()
                .values
                .toTypedArray()
        }
}

private fun LedgerEntriesReconciledEvent.enrichFromError(e: LedgerReconciliationException) = apply {
    val entry = e.entry
    entry.eventDetail.metadata["error"] = e.errorCode.name

    if (entry.entryType == LedgerEntryType.DebitRecord.name) {
        incorrectDebits.add(entry)
    } else {
        incorrectCredits.add(entry)
    }

    if (e.errorCode == LedgerError.EXCESS_DEBIT_RECORDS || e.errorCode == LedgerError.EXCESS_CREDIT_RECORDS) {
        entry.eventDetail.metadata["recordStatus"] = LedgerRecordStatus.Excess.name
        reconciliationStatus = LedgerRecordStatus.Excess.name
    } else if (e.errorCode == LedgerError.NOT_ZERO_SUM) {
        entry.eventDetail.metadata["recordStatus"] = LedgerRecordStatus.Unbalanced.name
        reconciliationStatus = LedgerRecordStatus.Unbalanced.name
    }
}

class LedgerReconciliationException(
    val entry: LedgerEntryRecordedEvent,
    val errorCode: LedgerError = LedgerError.UNKNOWN,
    msg: String = errorCode.name,
    throwable: Throwable? = null
) : RuntimeException(msg, throwable)
