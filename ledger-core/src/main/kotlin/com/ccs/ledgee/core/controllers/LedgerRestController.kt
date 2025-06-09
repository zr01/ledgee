package com.ccs.ledgee.core.controllers

import com.ccs.ledgee.core.controllers.models.LedgerApiRequest
import com.ccs.ledgee.core.controllers.models.LedgerApiResponse
import com.ccs.ledgee.core.controllers.models.LedgerDto
import com.ccs.ledgee.core.events.EventPublisherService
import com.ccs.ledgee.core.repositories.IsPending
import com.ccs.ledgee.core.repositories.LedgerEntity
import com.ccs.ledgee.core.repositories.LedgerEntryType
import com.ccs.ledgee.core.services.LedgerService
import com.ccs.ledgee.core.services.toLedgerEntryRecordedEvent
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.oshai.kotlinlogging.withLoggingContext
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

private val log = KotlinLogging.logger { }

@RestController
@RequestMapping("/api/v1/accounts/{accountId}")
class LedgerController(
    private val ledgerService: LedgerService,
    private val eventPublisherService: EventPublisherService,
) {

    @PostMapping("/{entryType}")
    @ResponseStatus(HttpStatus.CREATED)
    fun createLedgerEntry(
        @PathVariable accountId: String,
        @PathVariable entryType: LedgerEntryType,
        @Valid @RequestBody request: LedgerApiRequest
    ): LedgerApiResponse {
        withLoggingContext(
            "accountId" to accountId,
            "entryType" to entryType.name
        ) {
            val savedEntity = ledgerService.postLedgerEntry(
                accountId,
                entryType,
                request.data,
                request.data.createdBy
            )
            eventPublisherService.raiseLedgerEntryEvent(
                savedEntity.account.publicId,
                savedEntity.toLedgerEntryRecordedEvent()
            )
            return LedgerApiResponse(
                id = savedEntity.publicId,
                type = request.type,
                data = savedEntity.toDto()
            )
        }
    }
}

fun LedgerEntity.toDto() = LedgerDto(
    amount = amount,
    productCode = account.productCode,
    isPending = isPending == IsPending.Yes,
    externalReferenceId = externalReferenceId,
    description = description,
    createdBy = createdBy,
    parentPublicId = parentPublicId,
    entryReferenceId = entryReferenceId,
)