package com.groom.platform.saga

/**
 * Saga 상태 정의
 */
enum class SagaStatus {
    STARTED,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    COMPENSATED;

    companion object {
        fun fromString(value: String): SagaStatus =
            entries.find { it.name == value }
                ?: throw IllegalArgumentException("Unknown SagaStatus: $value")
    }
}
