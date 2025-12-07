package com.groom.platform.saga

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Saga Tracker 설정 프로퍼티
 */
@ConfigurationProperties(prefix = "saga.tracker")
data class SagaTrackerProperties(
    /**
     * Saga Tracker 활성화 여부 (기본값: true)
     */
    val enabled: Boolean = true,

    /**
     * Kafka 토픽명 (기본값: c4ang.saga.tracker)
     */
    val topic: String = "c4ang.saga.tracker"
)
