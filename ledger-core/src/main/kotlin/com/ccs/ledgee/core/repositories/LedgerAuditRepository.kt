package com.ccs.ledgee.core.repositories

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.SequenceGenerator
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.PagingAndSortingRepository
import org.springframework.stereotype.Repository
import java.net.InetAddress
import java.time.OffsetDateTime

const val AUDIT_SEQ = "ledger_audit_id_seq"
const val AUDIT_DEFAULT_CREATED_BY = "system"

enum class ChangeType {
    Created,
    Updated,
    StatusChanged,
    Archived,
    MarkedDeletion
}

@Entity
@Table(name = "ledger_audit")
data class LedgerAuditEntity(
    @Id
    @GeneratedValue(generator = AUDIT_SEQ)
    @SequenceGenerator(name = AUDIT_SEQ, sequenceName = AUDIT_SEQ, allocationSize = 1)
    val id: Long = 0L,

    val ledgerId: Long,

    @Enumerated(EnumType.ORDINAL)
    val previousRecordStatus: LedgerRecordStatus? = null,
    @Enumerated(EnumType.ORDINAL)
    val newRecordStatus: LedgerRecordStatus? = null,

    @Enumerated(EnumType.ORDINAL)
    val changeType: ChangeType = ChangeType.Created,
    val changeReason: String = changeType.name,

    @JdbcTypeCode(SqlTypes.JSON)
    val changedFields: ChangedFieldsEntity? = null, // JSONB field stored as String
    val ipAddress: InetAddress? = null,
    val userAgent: String? = null,

    val createdOn: OffsetDateTime = OffsetDateTime.now(),
    val createdBy: String = AUDIT_DEFAULT_CREATED_BY
)

data class ChangedFieldsEntity(
    val fieldsChanged: List<String>
)

@Repository
interface LedgerAuditRepository : JpaRepository<LedgerAuditEntity, Long>,
    PagingAndSortingRepository<LedgerAuditEntity, Long> {
    fun findAllByLedgerId(ledgerId: Long): List<LedgerAuditEntity>
    fun findAllByChangeType(changeType: ChangeType): List<LedgerAuditEntity>
    fun findAllByCreatedBy(createdBy: String): List<LedgerAuditEntity>
}
