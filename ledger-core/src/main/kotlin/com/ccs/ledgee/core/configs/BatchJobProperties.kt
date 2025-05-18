package com.ccs.ledgee.core.configs

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "ledger.batch-job")
data class BatchJobProperties(
    var stagedFetchInMinutes: Int = 5,
    var stagedFetchBatchSize: Int = 1000
)