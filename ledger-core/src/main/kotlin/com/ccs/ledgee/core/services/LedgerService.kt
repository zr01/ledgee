package com.ccs.ledgee.core.services

import com.ccs.ledgee.commons.EventDetail
import com.ccs.ledgee.core.controllers.models.LedgerDto
import com.ccs.ledgee.core.repositories.BalanceType
import com.ccs.ledgee.core.repositories.ChangeType
import com.ccs.ledgee.core.repositories.IsPending
import com.ccs.ledgee.core.repositories.LedgerEntity
import com.ccs.ledgee.core.repositories.LedgerEntryType
import com.ccs.ledgee.core.repositories.LedgerRecordStatus
import com.ccs.ledgee.core.repositories.LedgerRepository
import com.ccs.ledgee.core.repositories.VirtualAccountEntity
import com.ccs.ledgee.core.utils.IdGenerator
import com.ccs.ledgee.core.utils.uuidStr
import com.ccs.ledgee.events.LedgerAuditEvent
import com.ccs.ledgee.events.LedgerEntryRecordedEvent
import com.ccs.ledgee.events.ReconciliationInfo
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.transaction.Transactional
import org.hibernate.exception.JDBCConnectionException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service
import java.time.OffsetDateTime

private val log = KotlinLogging.logger { }

interface LedgerService {

    @Retryable(
        value = [
            ObjectOptimisticLockingFailureException::class,
            DataIntegrityViolationException::class,
            JDBCConnectionException::class
        ],
        maxAttempts = 1000,
        backoff = Backoff(1L)
    )
    fun postLedgerEntry(
        entryType: LedgerEntryType,
        ledgerDto: LedgerDto,
        createdBy: String
    ): LedgerEntity

    @Retryable(
        value = [
            ObjectOptimisticLockingFailureException::class,
            JDBCConnectionException::class
        ],
        maxAttempts = 1000,
        backoff = Backoff(1L)
    )
    fun postLedgerCorrectionEntries(
        publicAccountId: String,
        parentPublicId: String,
        entryType: LedgerEntryType,
        ledgerDto: LedgerDto,
        createdBy: String
    )

    fun postAuditEntry(entry: LedgerEntity)
}

@Service
@Transactional
class LedgerServiceImpl(
    private val ledgerRepository: LedgerRepository,
    private val virtualAccountService: VirtualAccountService,
    sequenceService: SequenceService,
    private val virtualAccountBalanceService: VirtualAccountBalanceService
) : LedgerService {

    private val drIdGenerator = IdGenerator(
        prefix = "dr"
    ) {
        val range = sequenceService.reserveAppLedgerIds(50)
        range[1] to range[0]
    }

    private val crIdGenerator = IdGenerator(
        prefix = "cr"
    ) {
        val range = sequenceService.reserveAppLedgerIds(50)
        range[1] to range[0]
    }

    override fun postLedgerEntry(
        entryType: LedgerEntryType,
        ledgerDto: LedgerDto,
        createdBy: String
    ): LedgerEntity {
        val publicId = entryType.generatePublicId(drIdGenerator, crIdGenerator)
        val account = virtualAccountService.retrieveOrCreateAccount(
            accountId = ledgerDto.accountId,
            productCode = ledgerDto.productCode,
            createdBy = createdBy
        )
        val entry = ledgerDto.toLedgerEntity(
            publicId,
            account,
            entryType,
            createdBy
        )

        // Update balance
        virtualAccountBalanceService
            .updateAccountBalance(
                account,
                BalanceType.Actual,
                listOf(entry)
            )
        val saveToDb = ledgerRepository.save(entry)
//        eventPublisherService
//            .raiseAuditEvent(
//                saveToDb.account.publicId,
//                saveToDb.toCreatedAuditEvent()
//            )

        log.atInfo {
            payload = mapOf(
                "publicAccountId" to account.publicId,
                "publicLedgerId" to entry.publicId
            )
            message = "Created Ledger Entry"
        }
        return saveToDb
    }

    override fun postLedgerCorrectionEntries(
        publicAccountId: String,
        parentPublicId: String,
        entryType: LedgerEntryType,
        ledgerDto: LedgerDto,
        createdBy: String
    ) {
        // Accepting only the ledger entry types for *Correction
        if (!entryType.isCorrection) {
            throw LedgerCorrectionException("Entry types is not accepted for correction")
        }

        // Validate that we are not correcting records that have a minimum of Balanced == 2
        val entries = ledgerRepository.findAllByExternalReferenceId(ledgerDto.externalReferenceId)
        if (entries.filter { it.recordStatus == LedgerRecordStatus.Balanced }.size == 2) {
            throw LedgerCorrectionException("Entries are already balanced for ${ledgerDto.externalReferenceId}")
        }

        TODO("Not yet implemented")
    }

    override fun postAuditEntry(entry: LedgerEntity) {
        TODO("Not yet implemented")
    }
}

private fun LedgerEntryType.generatePublicId(
    drIdGenerator: IdGenerator,
    crIdGenerator: IdGenerator
) = when (this) {
    LedgerEntryType.DebitRecord,
    LedgerEntryType.DebitRecordCorrection,
    LedgerEntryType.VoidDebitRecord -> drIdGenerator.nextVal()

    LedgerEntryType.CreditRecord,
    LedgerEntryType.CreditRecordCorrection,
    LedgerEntryType.VoidCreditRecord -> crIdGenerator.nextVal()
}

class LedgerCorrectionException(msg: String = "Incorrect Ledger Correction Entries", cause: Throwable? = null) :
    RuntimeException(msg, cause)

fun LedgerEntity.toLedgerEntryRecordedEvent(
    reconciliationInfo: ReconciliationInfo? = null,
    eventBy: String = createdBy
): LedgerEntryRecordedEvent = LedgerEntryRecordedEvent.newBuilder()
    .setParentPublicId(parentPublicId)
    .setPublicId(publicId)
    .setPublicAccountId(account.publicId)
    .setAmount(amount)
    .setEntryType(entryType.name)
    .setIsPending(isPending.name)
    .setRecordStatus(recordStatus.name)
    .setExternalReferenceId(externalReferenceId)
    .setEntryReferenceId(entryReferenceId)
    .setDescription(description)
    .setTransactionOn(transactionOn.toInstant().toEpochMilli())
    .setReconciliation(reconciliationInfo)
    .setEventDetail(eventDetail(eventBy))
    .build()

fun LedgerEntity.eventDetail(
    eventBy: String = createdBy
): EventDetail = EventDetail.newBuilder()
    .setEventId(uuidStr())
    .setEventBy(eventBy)
    .setEventOn(OffsetDateTime.now().toEpochSecond())
    .setMetadata(emptyMap())
    .build()

private fun LedgerEntity.toCreatedAuditEvent(): LedgerAuditEvent = LedgerAuditEvent.newBuilder()
    .setEventDetail(eventDetail())
    .setLedgerId(id)
    .setNewRecordStatus(recordStatus.ordinal)
    .setChangeType(ChangeType.Created.ordinal)
    .setChangeReason("Post Entry")
    .setChangedFields(changedFields())
    .setUserAgent(createdBy)
    .build()

private fun LedgerEntity.changedFields() = mutableMapOf(
    "publicId" to publicId,
    "accountId" to account.publicId,
    "amount" to amount,
    "entryType" to entryType.name,
    "isPending" to isPending.name,
    "recordStatus" to recordStatus.name,
    "externalReferenceId" to externalReferenceId,
    "description" to description,
    "transactionOn" to transactionOn.toString(),
    "createdOn" to createdOn.toString(),
    "createdBy" to createdBy
)

private fun LedgerDto.toLedgerEntity(
    publicId: String,
    account: VirtualAccountEntity,
    entryType: LedgerEntryType,
    createdBy: String
) = LedgerEntity(
    parentPublicId = parentPublicId,
    publicId = publicId,
    account = account,
    amount = amount,
    entryType = entryType,
    isPending = if (isPending) IsPending.Yes else IsPending.No,
    externalReferenceId = externalReferenceId,
    entryReferenceId = entryReferenceId,
    description = description,
    createdBy = createdBy
)
