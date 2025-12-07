package com.groom.platform.saga

/**
 * Saga 유형 정의
 */
enum class SagaType {
    ORDER_CREATION,
    PAYMENT_COMPLETION;

    companion object {
        fun fromString(value: String): SagaType =
            entries.find { it.name == value }
                ?: throw IllegalArgumentException("Unknown SagaType: $value")
    }
}
