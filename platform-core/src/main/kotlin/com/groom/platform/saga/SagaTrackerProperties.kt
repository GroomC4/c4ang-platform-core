package com.groom.platform.saga

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Saga Tracker 설정 프로퍼티
 *
 * 사용 예시:
 * ```yaml
 * platform:
 *   saga:
 *     enabled: true      # Saga Tracker 활성화 (기본값: false)
 *     topic: c4ang.saga.tracker  # Kafka 토픽명
 * ```
 */
@ConfigurationProperties(prefix = "platform.saga")
data class SagaTrackerProperties(
    /**
     * Saga Tracker 활성화 여부 (기본값: false)
     */
    val enabled: Boolean = false,

    /**
     * Kafka 토픽명 (기본값: c4ang.saga.tracker)
     */
    val topic: String = "c4ang.saga.tracker"
)
