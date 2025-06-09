package com.ccs.ledgee.core.controllers

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice

private val log = KotlinLogging.logger { }

@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(RuntimeException::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    fun handleRuntimeException(e: RuntimeException) = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR)
        .apply {
            title = "unhandled_error"
            this.detail = e.message ?: "Unhandled error"
            log.error(e) { "Unhandled error" }
        }
}