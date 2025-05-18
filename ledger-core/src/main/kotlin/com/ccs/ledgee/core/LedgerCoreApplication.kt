package com.ccs.ledgee.core

import com.ccs.ledgee.core.configs.BatchJobProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling

@EnableAsync
@EnableScheduling
@SpringBootApplication
@EnableConfigurationProperties(
    value = [
        BatchJobProperties::class
    ]
)
class LedgerCoreApplication

fun main(args: Array<String>) {
    runApplication<LedgerCoreApplication>(*args)
}
