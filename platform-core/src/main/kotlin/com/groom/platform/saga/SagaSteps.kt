package com.groom.platform.saga

/**
 * Saga Step 네이밍 상수
 * 각 도메인 서비스에서 사용하는 표준 Step 이름 정의
 */
object SagaSteps {
    // ===== Order Service =====
    const val ORDER_CREATED = "ORDER_CREATED"
    const val ORDER_CONFIRMED = "ORDER_CONFIRMED"
    const val ORDER_CANCELLED = "ORDER_CANCELLED"
    const val ORDER_TIMEOUT = "ORDER_TIMEOUT"

    // ===== Product Service =====
    const val STOCK_RESERVATION = "STOCK_RESERVATION"
    const val STOCK_RESERVED = "STOCK_RESERVED"
    const val STOCK_RESERVATION_FAILED = "STOCK_RESERVATION_FAILED"
    const val STOCK_RELEASED = "STOCK_RELEASED"

    // ===== Payment Service =====
    const val PAYMENT_INITIALIZATION = "PAYMENT_INITIALIZATION"
    const val PAYMENT_INITIALIZED = "PAYMENT_INITIALIZED"
    const val PAYMENT_COMPLETED = "PAYMENT_COMPLETED"
    const val PAYMENT_FAILED = "PAYMENT_FAILED"
    const val PAYMENT_CANCELLED = "PAYMENT_CANCELLED"
    const val PAYMENT_REFUNDED = "PAYMENT_REFUNDED"

    // ===== Compensation Steps =====
    const val COMPENSATION_STOCK = "COMPENSATION_STOCK"
    const val COMPENSATION_PAYMENT = "COMPENSATION_PAYMENT"
    const val COMPENSATION_ORDER = "COMPENSATION_ORDER"
}
