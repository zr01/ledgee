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
        parentPublicId: String,
        publicAccountId: String,
        correctionEntryType: LedgerEntryType,
        amount: Long,
        createdBy: String
    ): List<LedgerEntity>

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
            accountId = ledgerDto.accountId ?: throw IllegalArgumentException("Account not found"),
            productCode = ledgerDto.productCode ?: throw IllegalArgumentException("Product code not found"),
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
        log.atInfo {
            payload = mapOf(
                "publicAccountId" to account.publicId,
                "publicLedgerId" to entry.publicId
            )
            message = "Created Ledger Entry"
        }
        return saveToDb

//        eventPublisherService
//            .raiseAuditEvent(
//                saveToDb.account.publicId,
//                saveToDb.toCreatedAuditEvent()
//            )

    }

    override fun postLedgerCorrectionEntries(
        parentPublicId: String,
        publicAccountId: String,
        correctionEntryType: LedgerEntryType,
        amount: Long,
        createdBy: String
    ): List<LedgerEntity> {
        // Accepting only the ledger entry types for *Correction
        if (!correctionEntryType.isCorrection) {
            throw IllegalArgumentException("Entry types is not accepted for correction")
        }

        // Validate the account
        val account = virtualAccountService.retrieveAccountByPublicId(publicAccountId)
        // Validate that we are not correcting records that have a minimum of Balanced == 2
        val entryToCorrect = ledgerRepository.findByPublicId(parentPublicId)
            ?: throw IllegalArgumentException("Ledger entry does not exist: $parentPublicId")

        if (entryToCorrect.account.publicId != account.publicId) {
            throw IllegalArgumentException("Ledger entry does not match account")
        }

        if (!correctionEntryType.isCorrectionFor(entryToCorrect.entryType)) {
            throw IllegalArgumentException("Entry type $correctionEntryType does not match to entry's type")
        }

        val entries = ledgerRepository.findAllByExternalReferenceId(entryToCorrect.externalReferenceId)
        if (entries.filter { it.recordStatus == LedgerRecordStatus.Balanced }.size == 2) {
            throw IllegalStateException("Entries are already balanced for ${entryToCorrect.externalReferenceId}")
        }

        // Create the void entry
        val voidType = entryToCorrect.entryType.getVoidType()
        val voidEntry = entryToCorrect.copy(
            id = 0L,
            publicId = voidType.generatePublicId(drIdGenerator, crIdGenerator),
            entryType = voidType,
            recordStatus = LedgerRecordStatus.Staged,
            parentPublicId = parentPublicId,
            description = "Void for $parentPublicId",
            transactionOn = OffsetDateTime.now(),
            createdOn = OffsetDateTime.now(),
            createdBy = createdBy,
            reconciledOn = null,
            reconciledBy = null
        )

        // Create the correction entry
        val correctionEntry = voidEntry.copy(
            publicId = correctionEntryType.generatePublicId(drIdGenerator, crIdGenerator),
            entryType = correctionEntryType,
            amount = amount,
            description = "Correction for $parentPublicId",
        )

        // Update the balance
        val correctionEntries = listOf(voidEntry, correctionEntry)
        virtualAccountBalanceService
            .updateAccountBalance(
                account,
                BalanceType.Actual,
                correctionEntries
            )

        // Save all new entries
        val savedEntriesInDb = ledgerRepository.saveAll(correctionEntries)

        log.atInfo {
            payload = mapOf(
                "parentPublicId" to parentPublicId,
                "voidPublicId" to voidEntry.publicId,
                "correctionPublicId" to correctionEntry.publicId,
                "publicAccountId" to publicAccountId,
            )
            message = "Created correction ledger entries"
        }

        return savedEntriesInDb
    }

    override fun postAuditEntry(entry: LedgerEntity) {
        TODO("Not yet implemented")
    }
}

private fun LedgerEntryType.getVoidType(): LedgerEntryType = when (this) {
    LedgerEntryType.DebitRecord -> LedgerEntryType.DebitRecordVoid
    LedgerEntryType.CreditRecord -> LedgerEntryType.CreditRecordVoid
    else -> throw IllegalStateException("No valid entry types for correction for $name")
}

private fun LedgerEntryType.getCorrectionType(): LedgerEntryType = when (this) {
    LedgerEntryType.DebitRecord -> LedgerEntryType.DebitRecordCorrection
    LedgerEntryType.CreditRecord -> LedgerEntryType.CreditRecordCorrection
    else -> throw IllegalStateException("No valid entry types for correction for $name")
}

private fun LedgerEntryType.isCorrectionFor(entryType: LedgerEntryType) = when (this) {
    LedgerEntryType.DebitRecordCorrection -> entryType == LedgerEntryType.DebitRecord
    LedgerEntryType.CreditRecordCorrection -> entryType == LedgerEntryType.CreditRecord
    else -> false
}

private fun LedgerEntryType.generatePublicId(
    drIdGenerator: IdGenerator,
    crIdGenerator: IdGenerator
) = when (this) {
    LedgerEntryType.DebitRecord,
    LedgerEntryType.DebitRecordCorrection,
    LedgerEntryType.DebitRecordVoid -> drIdGenerator.nextVal()

    LedgerEntryType.CreditRecord,
    LedgerEntryType.CreditRecordCorrection,
    LedgerEntryType.CreditRecordVoid -> crIdGenerator.nextVal()
}

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
