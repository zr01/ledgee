package com.ccs.ledgee.core.repositories

import com.ccs.ledgee.core.repositories.LedgerEntryType.entries
import com.ccs.ledgee.core.utils.uuidStr
import jakarta.persistence.CascadeType
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToOne
import jakarta.persistence.SequenceGenerator
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.PagingAndSortingRepository
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.OffsetDateTime

const val LEDGER_SEQ = "ledger_id_seq"
const val LEDGER_DEFAULT_CREATED_BY = "system"
private val NEGATIVE = (-1).toBigDecimal()
private val POSITIVE = 1.toBigDecimal()

enum class LedgerEntryType(
    val sign: BigDecimal,
    val isCorrection: Boolean
) {
    DebitRecord(NEGATIVE, false),
    CreditRecord(POSITIVE, false),
    DebitRecordVoid(POSITIVE, false),
    CreditRecordVoid(NEGATIVE, false),
    DebitRecordCorrection(NEGATIVE, true),
    CreditRecordCorrection(POSITIVE, true);
}

fun LedgerEntryType.isEquivalent(value: String): Boolean = name.equals(value, ignoreCase = true)

enum class IsPending {
    No,
    Yes
}

enum class LedgerRecordStatus {
    Staged,
    WaitingForPair,
    Balanced,
    Unbalanced,
    Excess,
    Error,
    HotArchive,
    ColdArchive,
    ForDeletion,
    Void
}

@Entity
@Table(name = "ledger")
data class LedgerEntity(
    @Id
    @GeneratedValue(generator = LEDGER_SEQ)
    @SequenceGenerator(name = LEDGER_SEQ, sequenceName = LEDGER_SEQ, allocationSize = 1)
    val id: Long = 0L,

    var parentPublicId: String? = null,
    var publicId: String = uuidStr(),

    @OneToOne(cascade = [CascadeType.MERGE], fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    var account: VirtualAccountEntity = VirtualAccountEntity(),
    var amount: Long = 0L,

    @Enumerated(EnumType.ORDINAL)
    var entryType: LedgerEntryType = LedgerEntryType.DebitRecord,
    @Enumerated(EnumType.ORDINAL)
    var isPending: IsPending = IsPending.No,
    @Enumerated(EnumType.ORDINAL)
    var recordStatus: LedgerRecordStatus = LedgerRecordStatus.Staged,

    var externalReferenceId: String = uuidStr(),
    var entryReferenceId: String? = null,
    var description: String = entryType.name,

    val transactionOn: OffsetDateTime = OffsetDateTime.now(),
    val createdOn: OffsetDateTime = OffsetDateTime.now(),
    val createdBy: String = LEDGER_DEFAULT_CREATED_BY,
    var reconciledOn: OffsetDateTime? = null,
    var reconciledBy: String? = null
)

@Repository
interface LedgerRepository : JpaRepository<LedgerEntity, Long>, PagingAndSortingRepository<LedgerEntity, Long> {

    fun findByPublicId(publicId: String): LedgerEntity?
    fun findAllByExternalReferenceId(externalReferenceId: String): List<LedgerEntity>
}
