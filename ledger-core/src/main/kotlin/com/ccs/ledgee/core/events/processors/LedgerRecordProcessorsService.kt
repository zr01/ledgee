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
                    processEventToRecord(externalRefId, entry, record)
                }).toStream()
                .split()
                .branch { _, r ->
                    log.debug { "SendTo normal: $r" }
                    // Reconciled/Waiting Topic
                    r.reconciliationStatus != LedgerRecordStatus.Error.name
                }
                .branch { _, r ->
                    log.debug { "SendTo dlq: $r" }
                    // DLQ
                    r.reconciliationStatus == LedgerRecordStatus.Error.name
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
            throw LedgerReconciliationException(
                entry = entry,
                record = record,
                errorCode = LedgerError.EXCESS_DEBIT_RECORDS
            )
        }

        val originalEntry = record.debitEntry
        entry.recordStatus = LedgerRecordStatus.Void.name
        originalEntry.recordStatus = LedgerRecordStatus.Void.name

        record.debitEntry = null

        return record.reconcileVoid(originalEntry)
    }

    private fun processCreditRecordVoid(
        record: LedgerEntriesReconciledEvent,
        entry: LedgerEntryRecordedEvent
    ): LedgerEntriesReconciledEvent {
        if (record.creditEntry == null) {
            entry.recordStatus = LedgerRecordStatus.Excess.name
            throw LedgerReconciliationException(
                entry = entry,
                record = record,
                errorCode = LedgerError.EXCESS_CREDIT_RECORDS
            )
        }

        val originalEntry = record.creditEntry
        entry.recordStatus = LedgerRecordStatus.Void.name
        originalEntry.recordStatus = LedgerRecordStatus.Void.name

        record.creditEntry = null

        return record.reconcileVoid(originalEntry)
    }

    fun processEventToRecord(
        externalRefId: String,
        entry: LedgerEntryRecordedEvent,
        record: LedgerEntriesReconciledEvent
    ) = try {
        record.ledgerEntries.add(entry)
        when (entry.entryType) {
            LedgerEntryType.DebitRecord.name, LedgerEntryType.DebitRecordCorrection.name -> processDebitRecordEntry(
                record,
                entry
            )

            LedgerEntryType.CreditRecord.name, LedgerEntryType.CreditRecordCorrection.name -> processCreditRecordEntry(
                record,
                entry
            )

            LedgerEntryType.DebitRecordVoid.name -> processDebitRecordVoid(record, entry)
            LedgerEntryType.CreditRecordVoid.name -> processCreditRecordVoid(record, entry)
            else -> {
                log.atWarn {
                    message = "Unknown entry type encountered"
                    payload = mapOf(
                        "entryType" to entry.entryType,
                        "externalRefId" to externalRefId
                    )
                }
                record
            }
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

private fun LedgerEntriesReconciledEvent.reconcileVoid(
    voidedEntry: LedgerEntryRecordedEvent
) = apply {
    if (reconciliationStatus == LedgerRecordStatus.Unbalanced.name) {
        reconciliationStatus = LedgerRecordStatus.WaitingForPair.name
        ledgerEntries.applyStatusForPublicId(
            LedgerRecordStatus.Void.name,
            voidedEntry.publicId
        )
    }
}

private fun LedgerEntriesReconciledEvent.reconcile() = apply {
    if (reconciliationStatus == LedgerRecordStatus.Staged.name && isSingleEntryRecorded()) {
        reconciliationStatus = LedgerRecordStatus.WaitingForPair.name
        creditEntry?.apply {
            recordStatus = LedgerRecordStatus.WaitingForPair.name
        }
        debitEntry?.apply {
            recordStatus = LedgerRecordStatus.WaitingForPair.name
        }
    } else if (reconciliationStatus == LedgerRecordStatus.WaitingForPair.name) {
        if (creditEntry != null && debitEntry != null) {
            if (creditEntry.amount != debitEntry.amount) {
                creditEntry.recordStatus = LedgerRecordStatus.Unbalanced.name
                debitEntry.recordStatus = LedgerRecordStatus.Unbalanced.name
                ledgerEntries.applyStatusForPublicId(
                    LedgerRecordStatus.Unbalanced.name,
                    creditEntry.publicId, debitEntry.publicId
                )
                reconciliationStatus = LedgerRecordStatus.Unbalanced.name
                throw LedgerReconciliationException(
                    entry = (ledgerEntries.last() as LedgerEntryRecordedEvent),
                    record = this,
                    errorCode = LedgerError.NOT_ZERO_SUM,
                    msg = "Debit and credit entries are not balanced"
                )
            } else {
                creditEntry.recordStatus = LedgerRecordStatus.Balanced.name
                debitEntry.recordStatus = LedgerRecordStatus.Balanced.name
                ledgerEntries.applyStatusForPublicId(
                    LedgerRecordStatus.Balanced.name,
                    creditEntry.publicId, debitEntry.publicId
                )
                reconciliationStatus = LedgerRecordStatus.Balanced.name
            }
        }
    }
}

private fun List<*>.applyStatusForPublicId(
    newRecordStatus: String,
    vararg publicIds: String
) {
    publicIds.forEach { publicId ->
        firstOrNull {
            if (it is LedgerEntryRecordedEvent) {
                it.publicId == publicId
            } else false
        }?.apply {
            this as LedgerEntryRecordedEvent
            recordStatus = newRecordStatus
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
