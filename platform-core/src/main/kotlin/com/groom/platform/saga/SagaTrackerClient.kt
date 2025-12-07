package com.groom.platform.saga

/**
 * Saga Tracker Client 인터페이스
 *
 * 도메인 서비스에서 Saga 진행 상황을 중앙 Saga Tracker Service에 기록하기 위한 클라이언트입니다.
 * c4ang.saga.tracker Kafka 토픽으로 이벤트를 발행합니다.
 *
 * 사용 예시:
 * ```kotlin
 * @Service
 * class OrderService(
 *     private val sagaTrackerClient: SagaTrackerClient
 * ) {
 *     fun createOrder(command: CreateOrderCommand) {
 *         // 주문 생성 로직...
 *
 *         sagaTrackerClient.recordStep(
 *             sagaId = order.id,
 *             sagaType = SagaType.ORDER_CREATION,
 *             step = SagaSteps.ORDER_CREATED,
 *             status = SagaStatus.STARTED,
 *             orderId = order.orderNumber,
 *             metadata = mapOf("totalAmount" to order.totalAmount)
 *         )
 *     }
 * }
 * ```
 */
interface SagaTrackerClient {
    /**
     * Saga 단계를 기록합니다.
     *
     * @param sagaId Saga 고유 ID (일반적으로 주문 ID 또는 별도 Saga UUID)
     * @param sagaType Saga 유형 (ORDER_CREATION, PAYMENT_COMPLETION 등)
     * @param step 현재 단계명 (SagaSteps 상수 사용 권장)
     * @param status 상태 (STARTED, IN_PROGRESS, COMPLETED, FAILED, COMPENSATED)
     * @param orderId 연관 주문 ID
     * @param metadata 추가 메타데이터 (JSON으로 변환됨). traceId, spanId 자동 주입
     */
    fun recordStep(
        sagaId: String,
        sagaType: SagaType,
        step: String,
        status: SagaStatus,
        orderId: String,
        metadata: Map<String, Any>? = null
    )

    /**
     * Saga 시작을 기록합니다. (status=STARTED 헬퍼 메서드)
     */
    fun recordStart(
        sagaId: String,
        sagaType: SagaType,
        step: String,
        orderId: String,
        metadata: Map<String, Any>? = null
    ) {
        recordStep(sagaId, sagaType, step, SagaStatus.STARTED, orderId, metadata)
    }

    /**
     * Saga 진행을 기록합니다. (status=IN_PROGRESS 헬퍼 메서드)
     */
    fun recordProgress(
        sagaId: String,
        sagaType: SagaType,
        step: String,
        orderId: String,
        metadata: Map<String, Any>? = null
    ) {
        recordStep(sagaId, sagaType, step, SagaStatus.IN_PROGRESS, orderId, metadata)
    }

    /**
     * Saga 완료를 기록합니다. (status=COMPLETED 헬퍼 메서드)
     */
    fun recordComplete(
        sagaId: String,
        sagaType: SagaType,
        step: String,
        orderId: String,
        metadata: Map<String, Any>? = null
    ) {
        recordStep(sagaId, sagaType, step, SagaStatus.COMPLETED, orderId, metadata)
    }

    /**
     * Saga 실패를 기록합니다. (status=FAILED 헬퍼 메서드)
     */
    fun recordFailure(
        sagaId: String,
        sagaType: SagaType,
        step: String,
        orderId: String,
        failureReason: String,
        metadata: Map<String, Any>? = null
    ) {
        val enrichedMetadata = (metadata ?: emptyMap()) + ("failureReason" to failureReason)
        recordStep(sagaId, sagaType, step, SagaStatus.FAILED, orderId, enrichedMetadata)
    }

    /**
     * Saga 보상 완료를 기록합니다. (status=COMPENSATED 헬퍼 메서드)
     */
    fun recordCompensation(
        sagaId: String,
        sagaType: SagaType,
        step: String,
        orderId: String,
        metadata: Map<String, Any>? = null
    ) {
        recordStep(sagaId, sagaType, step, SagaStatus.COMPENSATED, orderId, metadata)
    }
}
