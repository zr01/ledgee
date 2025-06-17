package com.ccs.ledgee.core.controllers

import com.ccs.ledgee.core.controllers.models.LedgerApiRequest
import com.ccs.ledgee.core.controllers.models.LedgerApiResponse
import com.ccs.ledgee.core.controllers.models.LedgerCorrectionApiRequest
import com.ccs.ledgee.core.controllers.models.LedgerCorrectionApiResponse
import com.ccs.ledgee.core.controllers.models.LedgerDto
import com.ccs.ledgee.core.repositories.IsPending
import com.ccs.ledgee.core.repositories.LedgerEntity
import com.ccs.ledgee.core.repositories.LedgerEntryType
import com.ccs.ledgee.core.services.LedgerService
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
@RequestMapping("/api/v1/ledger")
class LedgerController(
    private val ledgerService: LedgerService
) {

    @PostMapping("/account/{publicAccountId}/entry/{entryType}")
    @ResponseStatus(HttpStatus.CREATED)
    fun createLedgerEntryUsingPublicAccountId(
        @PathVariable publicAccountId: String,
        @PathVariable entryType: LedgerEntryType,
        @Valid @RequestBody request: LedgerApiRequest
    ): LedgerApiResponse {
        if (request.data.accountId != null || request.data.productCode != null) {
            throw IllegalArgumentException("Account and/or product code must not be provided")
        }

        TODO("Create ledger entry with public account ID being provided")
    }

    @PostMapping("/{entryType}")
    @ResponseStatus(HttpStatus.CREATED)
    fun createLedgerEntry(
        @PathVariable entryType: LedgerEntryType,
        @Valid @RequestBody request: LedgerApiRequest
    ): LedgerApiResponse {
        withLoggingContext(
            "entryType" to entryType.name
        ) {
            if (request.data.accountId == null || request.data.productCode == null) {
                throw IllegalArgumentException("Account and/or product code must be provided")
            }

            val savedEntity = ledgerService.postLedgerEntry(
                entryType,
                request.data,
                request.data.createdBy
            )

            return LedgerApiResponse(
                id = savedEntity.publicId,
                type = request.type,
                data = savedEntity.toDto()
            )
        }
    }

    @PostMapping("/{parentPublicId}/correction")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun createCorrectionEntry(
        @PathVariable parentPublicId: String,
        @RequestBody request: LedgerCorrectionApiRequest
    ): LedgerCorrectionApiResponse {
        withLoggingContext(
            "parentPublicId" to parentPublicId,
        ) {
            val correctionEntries = ledgerService
                .postLedgerCorrectionEntries(
                    parentPublicId = parentPublicId,
                    amount = request.data.amount,
                    createdBy = request.data.createdBy
                )

            return LedgerCorrectionApiResponse(
                id = correctionEntries.last().publicId,
                type = request.type,
                data = request.data,
            )
        }
    }
}

fun LedgerEntity.toDto() = LedgerDto(
    amount = amount,
    accountId = account.accountId,
    productCode = account.productCode,
    isPending = isPending == IsPending.Yes,
    externalReferenceId = externalReferenceId,
    description = description,
    createdBy = createdBy,
    parentPublicId = parentPublicId,
    entryReferenceId = entryReferenceId,
)