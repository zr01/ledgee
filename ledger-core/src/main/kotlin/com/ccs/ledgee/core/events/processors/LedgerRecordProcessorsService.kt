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
                        .setLedgerEntries(mutableListOf())
                        .setReconciliationStatus(LedgerRecordStatus.Staged.name)
                        .build()
                }, { externalRefId, entry, record ->
                    processAggregate(externalRefId, entry, record)
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

    private fun processDebitRecordEntry(
        record: LedgerEntriesReconciledEvent,
        entry: LedgerEntryRecordedEvent
    ): LedgerEntriesReconciledEvent {
        // Entry registration to record
        if (record.debitEntry != null) {
            entry.recordStatus = LedgerRecordStatus.Excess.name
            record.ledgerEntries.add(entry)
            throw LedgerReconciliationException(
                entry = entry,
                record = record,
                errorCode = LedgerError.EXCESS_DEBIT_RECORDS
            )
        } else {
            record.debitEntry = entry
        }

        return record.reconcile()
    }

    private fun processCreditRecordEntry(
        record: LedgerEntriesReconciledEvent,
        entry: LedgerEntryRecordedEvent
    ): LedgerEntriesReconciledEvent {
        if (record.creditEntry != null) {
            entry.recordStatus = LedgerRecordStatus.Excess.name
            record.ledgerEntries.add(entry)
            throw LedgerReconciliationException(
                entry = entry,
                record = record,
                errorCode = LedgerError.EXCESS_CREDIT_RECORDS
            )
        } else {
            record.creditEntry = entry
        }

        // Reconciliation
        return record.reconcile()
    }

    // Validation only ever happens to creation of the entry through the API
    private fun processDebitRecordVoid(
        record: LedgerEntriesReconciledEvent,
        entry: LedgerEntryRecordedEvent
    ): LedgerEntriesReconciledEvent {
        if (record.debitEntry == null) {
            entry.recordStatus = LedgerRecordStatus.Excess.name
            record.ledgerEntries.add(entry)
            throw LedgerReconciliationException(
                entry = entry,
                record = record,
                errorCode = LedgerError.EXCESS_DEBIT_RECORDS
            )
        }

        val originalEntry = record.debitEntry
        entry.recordStatus = LedgerRecordStatus.Void.name
        originalEntry.recordStatus = LedgerRecordStatus.Void.name

        record.ledgerEntries.add(originalEntry)
        record.debitEntry = null

        return record.reconcileVoid()
    }

    private fun processCreditRecordVoid(
        record: LedgerEntriesReconciledEvent,
        entry: LedgerEntryRecordedEvent
    ): LedgerEntriesReconciledEvent {
        if (record.creditEntry == null) {
            entry.recordStatus = LedgerRecordStatus.Excess.name
            record.ledgerEntries.add(entry)
            throw LedgerReconciliationException(
                entry = entry,
                record = record,
                errorCode = LedgerError.EXCESS_CREDIT_RECORDS
            )
        }

        val originalEntry = record.creditEntry
        entry.recordStatus = LedgerRecordStatus.Void.name
        originalEntry.recordStatus = LedgerRecordStatus.Void.name

        record.ledgerEntries.add(originalEntry)
        record.creditEntry = null

        return record.reconcileVoid()
    }

    private fun processAggregate(
        externalRefId: String,
        entry: LedgerEntryRecordedEvent,
        record: LedgerEntriesReconciledEvent
    ) = try {
        when (entry.entryType) {
            LedgerEntryType.DebitRecord.name, LedgerEntryType.DebitRecordCorrection.name -> processDebitRecordEntry(record, entry)
            LedgerEntryType.CreditRecord.name, LedgerEntryType.CreditRecordCorrection.name -> processCreditRecordEntry(record, entry)
            LedgerEntryType.DebitRecordVoid.name -> processDebitRecordVoid(record, entry)
            LedgerEntryType.CreditRecordVoid.name -> processCreditRecordVoid(record, entry)
            else -> record
        }
    } catch (e: LedgerReconciliationException) {
        record.enrichFromError(e)
    }
}

private fun LedgerEntriesReconciledEvent.enrichFromError(e: LedgerReconciliationException) = apply {
    e.entry.eventDetail.metadata["error"] = e.errorCode.name
    reconciliationStatus = when (e.errorCode) {
        LedgerError.EXCESS_DEBIT_RECORDS,
        LedgerError.EXCESS_CREDIT_RECORDS -> LedgerRecordStatus.Excess.name
        LedgerError.NOT_ZERO_SUM -> LedgerRecordStatus.Unbalanced.name
        else -> LedgerRecordStatus.Error.name
    }
}

private fun LedgerEntriesReconciledEvent.reconcileVoid() = apply {
    if (reconciliationStatus == LedgerRecordStatus.Unbalanced.name) {
        reconciliationStatus = LedgerRecordStatus.Void.name
    }
}

private fun LedgerEntriesReconciledEvent.reconcile() = apply {
    if (reconciliationStatus == LedgerRecordStatus.Staged.name && isSingleEntryRecorded()) {
        reconciliationStatus = LedgerRecordStatus.WaitingForPair.name
    } else if (reconciliationStatus == LedgerRecordStatus.WaitingForPair.name) {
        if (creditEntry != null && debitEntry != null) {
            if (creditEntry.amount != debitEntry.amount) {
                creditEntry.recordStatus = LedgerRecordStatus.Unbalanced.name
                debitEntry.recordStatus = LedgerRecordStatus.Unbalanced.name
                reconciliationStatus = LedgerRecordStatus.Unbalanced.name
                throw LedgerReconciliationException(
                    entry = debitEntry,
                    record = this,
                    errorCode = LedgerError.NOT_ZERO_SUM,
                    msg = "Debit and credit entries are not balanced"
                )
            } else {
                creditEntry.recordStatus = LedgerRecordStatus.Balanced.name
                debitEntry.recordStatus = LedgerRecordStatus.Balanced.name
                reconciliationStatus = LedgerRecordStatus.Balanced.name
            }
        }
    }
}

private fun LedgerEntriesReconciledEvent.isSingleEntryRecorded() =
    (debitEntry != null).xor(creditEntry != null)

class LedgerReconciliationException(
    val entry: LedgerEntryRecordedEvent,
    val record: LedgerEntriesReconciledEvent,
    val errorCode: LedgerError = LedgerError.UNKNOWN,
    msg: String = errorCode.name,
    throwable: Throwable? = null
) : RuntimeException(msg, throwable)
