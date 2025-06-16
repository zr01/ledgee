package com.ccs.ledgee.core.aop

import com.ccs.ledgee.core.events.EventPublisherService
import com.ccs.ledgee.core.repositories.LedgerEntity
import com.ccs.ledgee.core.services.toLedgerEntryRecordedEvent
import io.github.oshai.kotlinlogging.KotlinLogging
import org.aspectj.lang.JoinPoint
import org.aspectj.lang.annotation.AfterReturning
import org.aspectj.lang.annotation.Aspect
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

private val log = KotlinLogging.logger { }

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class PublishLedgerEvent

@Aspect
@Component
class LedgerEventAspect(
    private val eventPublisherService: EventPublisherService,
) {
    @AfterReturning(
        pointcut = "@annotation(com.ccs.ledgee.core.aop.PublishLedgerEvent)",
        returning = "result"
    )
    fun publishEvent(joinPoint: JoinPoint, result: Any?) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                object : TransactionSynchronization {
                    override fun afterCommit() {
                        processResult(result)
                    }
                }
            )
        } else {
            // No transaction active, process immediately or log warning
            log.warn { "No active transaction found when executing ${joinPoint.signature}" }
            processResult(result)
        }

    }

    private fun processResult(result: Any?) {
        when (result) {
            is LedgerEntity -> {
                eventPublisherService.raiseLedgerEntryEvent(
                    result.publicId,
                    result.toLedgerEntryRecordedEvent()
                )
            }

            is List<*> -> {
                if (result.isNotEmpty() && result.all { it is LedgerEntity }) {
                    result.forEach { ledgerEntity ->
                        ledgerEntity as LedgerEntity
                        eventPublisherService.raiseLedgerEntryEvent(
                            ledgerEntity.publicId,
                            ledgerEntity.toLedgerEntryRecordedEvent()
                        )
                    }
                }
            }

            else -> {
                log.atWarn {
                    message = "Cannot publish event for unknown result type"
                    payload = mapOf(
                        "resultClass" to "${result?.javaClass ?: "unknown"}"
                    )
                }
            }
        }
    }
}