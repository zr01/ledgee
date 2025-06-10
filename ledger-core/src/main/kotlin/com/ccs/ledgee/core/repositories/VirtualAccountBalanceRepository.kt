package com.ccs.ledgee.core.repositories

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.SequenceGenerator
import jakarta.persistence.Table
import jakarta.persistence.Version
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.OffsetDateTime

private const val VIRTUAL_ACCOUNT_BALANCES_SEQ = "virtual_account_balance_id_seq"

enum class BalanceType {
    Actual,
    Projected
}

@Entity
@Table(name = "virtual_account_balance")
data class VirtualAccountBalanceEntity(
    @Id
    @GeneratedValue(generator = VIRTUAL_ACCOUNT_BALANCES_SEQ)
    @SequenceGenerator(name = VIRTUAL_ACCOUNT_BALANCES_SEQ, sequenceName = VIRTUAL_ACCOUNTS_SEQ, allocationSize = 1)
    val id: Long = 0L,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    val virtualAccount: VirtualAccountEntity = VirtualAccountEntity(),

    @Column
    @Enumerated(value = EnumType.ORDINAL)
    val isProjected: BalanceType = BalanceType.Actual,

    @Column
    var availableBalance: BigDecimal = BigDecimal.ZERO,

    @Column
    var pendingBalance: BigDecimal = BigDecimal.ZERO,

    @Column
    var lastUpdated: OffsetDateTime = OffsetDateTime.now(),

    @Version
    @Column
    var version: Long = 0L
) {
    fun getTotalBalance(): BigDecimal = availableBalance + pendingBalance
}

@Repository
interface VirtualAccountBalanceRepository : JpaRepository<VirtualAccountBalanceEntity, Long> {
    fun findByVirtualAccountIdAndIsProjected(
        virtualAccountId: Long,
        isProjected: BalanceType
    ): VirtualAccountBalanceEntity?
}
