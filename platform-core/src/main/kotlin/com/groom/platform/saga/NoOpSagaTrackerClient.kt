package com.groom.platform.saga

import org.slf4j.LoggerFactory

/**
 * 테스트/개발용 No-Op SagaTrackerClient 구현체
 *
 * Kafka가 없는 환경에서 사용할 수 있는 더미 구현체입니다.
 * 실제 이벤트 발행 없이 로깅만 수행합니다.
 */
class NoOpSagaTrackerClient : SagaTrackerClient {

    private val logger = LoggerFactory.getLogger(NoOpSagaTrackerClient::class.java)

    override fun recordStep(
        sagaId: String,
        sagaType: SagaType,
        step: String,
        status: SagaStatus,
        orderId: String,
        metadata: Map<String, Any>?
    ) {
        logger.info(
            "[NoOp] Saga tracker event (not sent): sagaId=$sagaId, sagaType=$sagaType, " +
                "step=$step, status=$status, orderId=$orderId"
        )
    }
}
