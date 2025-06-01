package com.ccs.ledgee.core.events

import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.avro.specific.SpecificRecord
import org.springframework.context.annotation.Bean
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.Message
import org.springframework.messaging.support.MessageBuilder
import org.springframework.retry.RetryCallback
import org.springframework.retry.RetryContext
import org.springframework.retry.RetryListener
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import java.util.function.Supplier

private val log = KotlinLogging.logger { }

interface EventPublisherService {
    @Retryable(
        value = [EventPublishError::class],
        backoff = Backoff(delay = 1L),
        maxAttempts = 10000,
        listeners = ["eventRetryListenerSupport"]
    )
    fun raiseLedgerEntryEvent(accountId: String, event: SpecificRecord)

    @Retryable(
        value = [EventPublishError::class],
        backoff = Backoff(delay = 1L),
        maxAttempts = 10000,
        listeners = ["eventRetryListenerSupport"]
    )
    fun raiseAuditEvent(accountId: String, event: SpecificRecord)
}

@Service
class EventPublisherServiceImpl : EventPublisherService {

    private val sinkLedgerEvent = Sinks.many()
        .multicast()
        .directBestEffort<Message<SpecificRecord>>()

    private val sinkAuditEvent = Sinks.many()
        .multicast()
        .directBestEffort<Message<SpecificRecord>>()

    @Bean
    fun supplierLedgerEvents() = Supplier<Flux<Message<SpecificRecord>>> {
        sinkLedgerEvent.asFlux()
    }

    @Bean
    fun supplierAuditEvents() = Supplier<Flux<Message<SpecificRecord>>> {
        sinkAuditEvent.asFlux()
    }

    override fun raiseLedgerEntryEvent(accountId: String, event: SpecificRecord) {
        (accountId to event)
            .toMessage()
            .send { msg ->
                sinkLedgerEvent.tryEmitNext(msg)
            }
    }

    override fun raiseAuditEvent(accountId: String, event: SpecificRecord) {
        (accountId to event)
            .toMessage()
            .send { msg ->
                sinkAuditEvent.tryEmitNext(msg)
            }
    }
}

@Component("eventRetryListenerSupport")
class EventRetryListenerSupport : RetryListener {

    override fun <T : Any?, E : Throwable?> onError(
        context: RetryContext?,
        callback: RetryCallback<T?, E?>?,
        throwable: Throwable?
    ) {
        if (context?.let { it.retryCount % 100L == 0L } == true) {
            log.atWarn {
                message = "Event retry tracker issue"
                payload = mapOf("eventPublishRetryCount" to "${context.retryCount}")
            }
        }
        super.onError(context, callback, throwable)
    }
}

fun Pair<String, SpecificRecord>.toMessage() = MessageBuilder
    .withPayload(second)
    .setHeader(KafkaHeaders.KEY, first)
    .build()

@Synchronized
fun <T : SpecificRecord> Message<T>.send(callback: (Message<T>) -> Sinks.EmitResult) {
    val result = callback(this)
    if (result.isFailure) {
        throw EventPublishError(result, "Error pushing event to sink")
    }
}

class EventPublishError(
    val emitResult: Sinks.EmitResult,
    msg: String? = null,
    cause: Throwable? = null
) : RuntimeException(msg, cause)