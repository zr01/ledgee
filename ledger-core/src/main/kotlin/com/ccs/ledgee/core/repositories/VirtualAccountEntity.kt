package com.ccs.ledgee.core.repositories

import com.ccs.ledgee.core.utils.uuidStr
import jakarta.persistence.CascadeType
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.SequenceGenerator
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.PagingAndSortingRepository
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

const val VIRTUAL_ACCOUNTS_SEQ = "virtual_accounts_id_seq"
const val VIRTUAL_ACCOUNTS_DEFAULT_CREATED_BY = "system"

@Entity
@Table(name = "virtual_accounts")
data class VirtualAccountEntity(
    @Id
    @GeneratedValue(generator = VIRTUAL_ACCOUNTS_SEQ)
    @SequenceGenerator(name = VIRTUAL_ACCOUNTS_SEQ, sequenceName = VIRTUAL_ACCOUNTS_SEQ, allocationSize = 1)
    val id: Long = 0L,

    var publicId: String = uuidStr(),
    var accountId: String = uuidStr(),
    var productCode: String = "banking",
    var currency: String = "AUD",
    @JdbcTypeCode(SqlTypes.JSON)
    var metadata: VirtualAccountMetadata? = null, // JSONB field stored as String

    val createdOn: OffsetDateTime = OffsetDateTime.now(),
    val createdBy: String = VIRTUAL_ACCOUNTS_DEFAULT_CREATED_BY,
    var modifiedOn: OffsetDateTime? = null,
    var modifiedBy: String? = null
) {
    @OneToMany(
        mappedBy = "virtualAccount",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
        fetch = FetchType.LAZY
    )
    var balances: MutableSet<VirtualAccountBalanceEntity> = mutableSetOf(
        VirtualAccountBalanceEntity(virtualAccount = this, isProjected = BalanceType.Actual),
        VirtualAccountBalanceEntity(virtualAccount = this, isProjected = BalanceType.Projected),
    )
}

data class VirtualAccountMetadata(
    var bsb: String? = null
)

@Repository
interface VirtualAccountsRepository : JpaRepository<VirtualAccountEntity, Long>,
    PagingAndSortingRepository<VirtualAccountEntity, Long> {

    fun findByPublicId(publicId: String): VirtualAccountEntity?
    fun findByAccountIdAndProductCode(accountId: String, productCode: String): VirtualAccountEntity?
    fun findAllByAccountId(accountId: String): List<VirtualAccountEntity>
    fun findAllByProductCode(productCode: String): List<VirtualAccountEntity>
}
