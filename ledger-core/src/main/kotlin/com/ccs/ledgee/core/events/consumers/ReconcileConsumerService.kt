package com.ccs.ledgee.core.events.consumers

import com.ccs.ledgee.core.batch.jobs.LedgerError
import com.ccs.ledgee.core.batch.jobs.LedgerException
import com.ccs.ledgee.core.events.EventPublishError
import com.ccs.ledgee.core.events.EventPublisherService
import com.ccs.ledgee.core.repositories.ChangeType
import com.ccs.ledgee.core.repositories.LedgerEntity
import com.ccs.ledgee.core.repositories.LedgerEntryType
import com.ccs.ledgee.core.repositories.LedgerRecordStatus
import com.ccs.ledgee.core.repositories.LedgerRepository
import com.ccs.ledgee.core.services.eventDetail
import com.ccs.ledgee.core.services.toLedgerEvent
import com.ccs.ledgee.events.LedgerAuditEvent
import com.ccs.ledgee.events.LedgerEvent
import com.ccs.ledgee.events.ReconciliationInfo
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.oshai.kotlinlogging.withLoggingContext
import org.apache.kafka.streams.kstream.KStream
import org.springframework.context.annotation.Bean
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.OffsetDateTime
import java.util.function.Consumer

private const val JOB_NAME = "reconcile-consumer-service"
private val log = KotlinLogging.logger { }

@Service
@Transactional
class ReconcileConsumerService(
    private val ledgerRepository: LedgerRepository,
    private val eventPublisherService: EventPublisherService,
    private val transactionTemplate: TransactionTemplate
) {

    @Bean
    fun reconcileConsumer() = Consumer<KStream<String, LedgerEvent>> { stream ->
        stream.filter { _, event ->
            when {
                event.recordStatus == LedgerRecordStatus.Staged.name &&
                        event.entryType == LedgerEntryType.CreditRecord.name -> true

                else -> false
            }
        }.foreach { accountId, event ->
            try {
                transactionTemplate.execute {
                    reconcile(accountId, event)
                }
            } catch (e: Exception) {
                if (e is EventPublishError) {
                    log.error { "Due to ${e.emitResult}" }
                }
                log.error(e) { "Something went wrong" }
                throw e
            }
        }
    }

    private fun reconcile(accountId: String, event: LedgerEvent) {
        val externalReferenceId = event.externalReferenceId
        val entries = ledgerRepository.findAllByExternalReferenceId(externalReferenceId)

        withLoggingContext(
            "externalReferenceId" to externalReferenceId,
            "creditAccountId" to accountId
        ) {
            try {
                validateIfEntriesBalance(entries)
                log.info { "Balanced Entries" }
            } catch (e: LedgerException) {
                handleLedgerReconciliationError(entries, e)
            }
        }
    }

    private fun validateIfEntriesBalance(entries: List<LedgerEntity>) {
        val (debitRecord, creditRecord) = entries.validate()
        debitRecord.setAsBalanced()
        creditRecord.setAsBalanced()
        ledgerRepository.saveAll(entries)
        entries.forEach { entry ->
            eventPublisherService.raiseLedgerEntryEvent(
                entry.account.publicId,
                entry.toLedgerEvent(
                    reconciliationInfo = entry.reconciliationInfo(),
                    eventBy = JOB_NAME
                )
            )
            eventPublisherService.raiseAuditEvent(
                entry.account.publicId,
                entry.toAuditEvent(LedgerRecordStatus.Balanced.name)
            )
        }
    }

    private fun handleLedgerReconciliationError(
        entries: List<LedgerEntity>,
        e: LedgerException
    ) {
        val newRecordStatus = e.recordStatus()
        val changeReason = e.message ?: "Unknown error"
        val toBeUpdated = entries
            .filter { entry -> entry.recordStatus == LedgerRecordStatus.Staged }
        toBeUpdated.forEach { entry -> entry.recordStatus = newRecordStatus }
        ledgerRepository.saveAll(toBeUpdated)
        toBeUpdated.forEach { entry ->
            eventPublisherService.raiseLedgerEntryEvent(
                entry.account.publicId,
                entry.toLedgerEvent(
                    eventBy = JOB_NAME
                )
            )
            eventPublisherService.raiseAuditEvent(
                entry.account.publicId,
                entry.toAuditEvent(changeReason),
            )
        }

        withLoggingContext("ledgerError" to e.ledgerError.name) {
            if (newRecordStatus == LedgerRecordStatus.Error) {
                log.error(e) { "Reconciliation error" }
            } else {
                log.warn { "Reconciliation unbalanced" }
            }
        }
    }
}

fun List<LedgerEntity>.validate(): Pair<LedgerEntity, LedgerEntity> {
    val debitRecords = filter { it.entryType == LedgerEntryType.DebitRecord }
    val creditRecords = filter { it.entryType == LedgerEntryType.CreditRecord }

    if (size == 1) {
        throw LedgerException(
            if (debitRecords.isEmpty()) LedgerError.NO_DEBIT_RECORD else LedgerError.NO_CREDIT_RECORD
        )
    } else if (debitRecords.size > 1) {
        throw LedgerException(LedgerError.EXCESS_DEBIT_RECORDS)
    } else if (creditRecords.size > 1) {
        throw LedgerException(LedgerError.EXCESS_CREDIT_RECORDS)
    }

    val debitRecord = debitRecords.first()
    val creditRecord = creditRecords.first()

    if (debitRecord.amount != creditRecord.amount) {
        throw LedgerException(LedgerError.NOT_ZERO_SUM)
    }

    return debitRecord to creditRecord
}

private fun LedgerEntity.setAsBalanced() = apply {
    recordStatus = LedgerRecordStatus.Balanced
    reconciledBy = JOB_NAME
    reconciledOn = OffsetDateTime.now()
}

private fun LedgerEntity.reconciliationInfo(): ReconciliationInfo = ReconciliationInfo.newBuilder()
    .setReconciledBy(reconciledBy)
    .setReconciledOn(reconciledOn?.toInstant()?.toEpochMilli() ?: OffsetDateTime.now().toInstant().toEpochMilli())
    .build()

private fun LedgerEntity.toAuditEvent(reason: String) = LedgerAuditEvent.newBuilder()
    .setEventDetail(eventDetail(eventBy = JOB_NAME))
    .setLedgerId(id)
    .setPreviousRecordStatus(LedgerRecordStatus.Staged.ordinal)
    .setNewRecordStatus(recordStatus.ordinal)
    .setChangeType(ChangeType.StatusChanged.ordinal)
    .setChangeReason(reason)
    .setChangedFields(
        mapOf(
            "recordStatus" to recordStatus.name
        )
    )
    .setUserAgent(JOB_NAME)
    .build()