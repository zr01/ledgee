package com.ccs.ledgee.core.services

import com.ccs.ledgee.core.repositories.BalanceType
import com.ccs.ledgee.core.repositories.IsPending
import com.ccs.ledgee.core.repositories.LedgerEntity
import com.ccs.ledgee.core.repositories.VirtualAccountBalanceRepository
import com.ccs.ledgee.core.repositories.VirtualAccountEntity
import com.ccs.ledgee.core.utils.Iso4217Currency
import com.ccs.ledgee.core.utils.amountFor
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.persistence.OptimisticLockException
import jakarta.transaction.Transactional
import org.hibernate.StaleObjectStateException
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service
import java.time.OffsetDateTime

private val log = KotlinLogging.logger { }
private val ZERO = 0.toBigDecimal()

interface VirtualAccountBalanceService {
    @Retryable(
        value = [
            OptimisticLockingFailureException::class,
            OptimisticLockException::class,
            StaleObjectStateException::class
        ],
        backoff = Backoff(10L),
        maxAttempts = 50
    )
    @Transactional
    fun updateAccountBalance(
        account: VirtualAccountEntity,
        balanceType: BalanceType,
        entries: List<LedgerEntity>
    )
}

@Service
class VirtualAccountBalanceServiceImpl(
    private val virtualAccountBalanceRepository: VirtualAccountBalanceRepository
) : VirtualAccountBalanceService {
    override fun updateAccountBalance(
        account: VirtualAccountEntity,
        balanceType: BalanceType,
        entries: List<LedgerEntity>
    ) {
        val accountBalance = virtualAccountBalanceRepository
            .findByVirtualAccountIdAndIsProjected(account.id, balanceType)
            ?: throw IllegalStateException("Virtual Account Balance not found")

        val currency = Iso4217Currency.getCurrency(account.currency)
        val pendingBalanceToUpdate = entries.sumFor(IsPending.Yes, currency)
        val availableBalanceToUpdate = entries.sumFor(IsPending.No, currency)

        accountBalance.pendingBalance += pendingBalanceToUpdate
        accountBalance.availableBalance += availableBalanceToUpdate
        accountBalance.lastUpdated = OffsetDateTime.now()

        virtualAccountBalanceRepository.save(accountBalance)

        log.atInfo {
            message = "Account Balance Updated"
            payload = mapOf(
                "accountPublicId" to account.publicId,
                "pendingBalance" to accountBalance.pendingBalance.toString(),
                "availableBalance" to accountBalance.availableBalance.toString()
            )
        }
    }
}

private fun List<LedgerEntity>.sumFor(
    isPending: IsPending,
    currency: Iso4217Currency
) = filter { it.isPending == isPending }
    .map { entry ->
        entry.amount
            .amountFor(currency)
            .multiply(entry.entryType.sign)
    }
    .let {
        if (it.isEmpty())
            ZERO
        else
            it.reduce { a, b -> a.plus(b) }
    }
