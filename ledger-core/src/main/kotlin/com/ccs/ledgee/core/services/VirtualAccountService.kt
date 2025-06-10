package com.ccs.ledgee.core.services

import com.ccs.ledgee.core.repositories.BalanceType
import com.ccs.ledgee.core.repositories.IsPending
import com.ccs.ledgee.core.repositories.LedgerEntryType
import com.ccs.ledgee.core.repositories.VirtualAccountBalanceRepository
import com.ccs.ledgee.core.repositories.VirtualAccountEntity
import com.ccs.ledgee.core.repositories.VirtualAccountsRepository
import com.ccs.ledgee.core.utils.IdGenerator
import com.ccs.ledgee.core.utils.Iso4217Currency
import com.ccs.ledgee.core.utils.amountFor
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.transaction.Transactional
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service
import java.time.OffsetDateTime

private val log = KotlinLogging.logger { }

interface VirtualAccountService {
    @Retryable(
        value = [
            DataIntegrityViolationException::class
        ],
        backoff = Backoff(10L),
        maxAttempts = 50
    )
    fun retrieveOrCreateAccount(
        accountId: String,
        productCode: String,
        createdBy: String
    ): VirtualAccountEntity

    @Retryable(
        value = [
            IllegalStateException::class,
            OptimisticLockingFailureException::class,
        ],
        backoff = Backoff(10L),
        maxAttempts = 50
    )
    fun updateAccountBalance(
        publicAccountId: String,
        entryType: LedgerEntryType,
        isPending: IsPending,
        amount: Long
    )
}

@Service
@Transactional
class VirtualAccountServiceImpl(
    private val virtualAccountsRepository: VirtualAccountsRepository,
    private val virtualAccountBalanceRepository: VirtualAccountBalanceRepository,
    sequenceService: SequenceService
) : VirtualAccountService {

    private val idGenerator = IdGenerator(
        prefix = "ac"
    ) {
        val range = sequenceService.reserveAppLedgerIds(100)
        range[1] to range[0]
    }

    override fun retrieveOrCreateAccount(
        accountId: String,
        productCode: String,
        createdBy: String
    ): VirtualAccountEntity {
        val virtualAccount = virtualAccountsRepository
            .findByAccountIdAndProductCode(accountId, productCode)
            ?.also {
                log.atInfo {
                    message = "Retrieved Account"
                    payload = mapOf(
                        "publicAccountId" to it.publicId,
                        "accountId" to it.accountId
                    )
                }
            }
            ?: virtualAccountsRepository.save(
                VirtualAccountEntity(
                    publicId = idGenerator.nextVal(),
                    accountId = accountId.lowercase(),
                    productCode = productCode.lowercase(),
                    createdBy = createdBy
                ).also { created ->
                    log.atInfo {
                        message = "Created Account & Balance Records"
                        payload = mapOf(
                            "publicAccountId" to created.publicId,
                            "accountId" to created.accountId
                        )
                    }
                }
            )
        return virtualAccount
    }

    override fun updateAccountBalance(
        publicAccountId: String,
        entryType: LedgerEntryType,
        isPending: IsPending,
        amount: Long
    ) {
        val sign = if (entryType == LedgerEntryType.DebitRecord)
            (-1).toBigDecimal()
        else 1.toBigDecimal()
        val account = virtualAccountsRepository.findByPublicId(publicAccountId)
            ?: throw IllegalStateException("Virtual account not found for public id $publicAccountId")
        val currency = account.currency.let { Iso4217Currency.getCurrency(it) }
        val amount = amount.amountFor(currency) * sign
        val balance = virtualAccountBalanceRepository.findByVirtualAccountIdAndIsProjected(
            account.id,
            BalanceType.Projected
        ) ?: throw IllegalStateException("Virtual account balance not found for account id ${account.publicId}")

        if (isPending == IsPending.Yes) {
            balance.pendingBalance += amount
        } else {
            balance.availableBalance += amount
        }
        balance.lastUpdated = OffsetDateTime.now()
    }
}