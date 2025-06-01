package com.ccs.ledgee.core.services

import com.ccs.ledgee.core.repositories.VirtualAccountEntity
import com.ccs.ledgee.core.repositories.VirtualAccountsRepository
import com.ccs.ledgee.core.utils.IdGenerator
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.transaction.Transactional
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service

private val log = KotlinLogging.logger { }

interface VirtualAccountService {
    @Retryable(
        value = [
            DataIntegrityViolationException::class
        ],
        backoff = Backoff(10L)
    )
    @Transactional
    fun retrieveOrCreateAccount(
        accountId: String,
        productCode: String,
        createdBy: String
    ): VirtualAccountEntity
}

@Service
class VirtualAccountServiceImpl(
    private val virtualAccountsRepository: VirtualAccountsRepository,
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
                ).also {
                    log.atInfo {
                        message = "Created Account"
                        payload = mapOf(
                            "publicAccountId" to it.publicId,
                            "accountId" to it.accountId
                        )
                    }
                }
            )
        return virtualAccount
    }
}