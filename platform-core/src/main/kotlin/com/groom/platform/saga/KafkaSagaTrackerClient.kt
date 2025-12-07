package com.groom.platform.saga

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import java.time.Instant
import java.util.UUID

/**
 * Kafka 기반 SagaTrackerClient 구현체
 *
 * c4ang.saga.tracker 토픽으로 SagaTracker Avro 메시지를 발행합니다.
 * OpenTelemetry Trace ID가 있으면 자동으로 metadata에 주입합니다.
 *
 * 이 클래스는 c4ang-contract-hub의 SagaTracker Avro 스키마에 의존합니다.
 * 사용하는 서비스에서 contract-hub 의존성이 있어야 합니다.
 */
class KafkaSagaTrackerClient(
    private val kafkaTemplate: KafkaTemplate<String, Any>,
    private val topic: String,
    private val objectMapper: ObjectMapper,
    private val serviceName: String
) : SagaTrackerClient {

    private val logger = LoggerFactory.getLogger(KafkaSagaTrackerClient::class.java)

    // Avro 클래스를 리플렉션으로 로드
    private val sagaTrackerClass: Class<*>? = try {
        Class.forName("com.groom.ecommerce.saga.event.avro.SagaTracker")
    } catch (e: ClassNotFoundException) {
        logger.warn("SagaTracker Avro class not found. Saga tracking will be disabled.")
        null
    }

    private val sagaTypeClass: Class<*>? = try {
        Class.forName("com.groom.ecommerce.saga.event.avro.SagaType")
    } catch (e: ClassNotFoundException) {
        null
    }

    private val sagaStatusClass: Class<*>? = try {
        Class.forName("com.groom.ecommerce.saga.event.avro.SagaStatus")
    } catch (e: ClassNotFoundException) {
        null
    }

    override fun recordStep(
        sagaId: String,
        sagaType: SagaType,
        step: String,
        status: SagaStatus,
        orderId: String,
        metadata: Map<String, Any>?
    ) {
        if (sagaTrackerClass == null || sagaTypeClass == null || sagaStatusClass == null) {
            logger.debug("SagaTracker Avro classes not available. Skipping event: sagaId=$sagaId, step=$step")
            return
        }

        val eventId = UUID.randomUUID().toString()
        val now = Instant.now()

        val enrichedMetadata = buildMetadata(metadata)

        try {
            val event = buildSagaTrackerEvent(
                eventId = eventId,
                now = now,
                sagaId = sagaId,
                sagaType = sagaType,
                step = step,
                status = status,
                orderId = orderId,
                metadata = enrichedMetadata
            )

            if (event != null) {
                @Suppress("UNCHECKED_CAST")
                (kafkaTemplate as KafkaTemplate<String, Any>).send(topic, sagaId, event)
                    .whenComplete { result, ex ->
                        if (ex != null) {
                            logger.error("Failed to send saga tracker event: eventId=$eventId, sagaId=$sagaId, error=${ex.message}", ex)
                        } else {
                            logger.debug(
                                "Saga tracker event sent: eventId=$eventId, sagaId=$sagaId, step=$step, " +
                                    "offset=${result.recordMetadata.offset()}"
                            )
                        }
                    }
            }
        } catch (e: Exception) {
            logger.error("Failed to build SagaTracker event: ${e.message}", e)
        }
    }

    private fun buildSagaTrackerEvent(
        eventId: String,
        now: Instant,
        sagaId: String,
        sagaType: SagaType,
        step: String,
        status: SagaStatus,
        orderId: String,
        metadata: Map<String, Any>
    ): Any? {
        return try {
            // SagaTracker.newBuilder() 호출
            val newBuilderMethod = sagaTrackerClass!!.getMethod("newBuilder")
            val builder = newBuilderMethod.invoke(null)
            val builderClass = builder.javaClass

            // 필드 설정
            builderClass.getMethod("setEventId", String::class.java).invoke(builder, eventId)
            builderClass.getMethod("setEventTimestamp", Long::class.java).invoke(builder, now.toEpochMilli())
            builderClass.getMethod("setSagaId", String::class.java).invoke(builder, sagaId)

            // SagaType enum 변환
            val avroSagaType = sagaTypeClass!!.getMethod("valueOf", String::class.java)
                .invoke(null, sagaType.name)
            builderClass.getMethod("setSagaType", sagaTypeClass).invoke(builder, avroSagaType)

            builderClass.getMethod("setStep", String::class.java).invoke(builder, step)

            // SagaStatus enum 변환
            val avroSagaStatus = sagaStatusClass!!.getMethod("valueOf", String::class.java)
                .invoke(null, status.name)
            builderClass.getMethod("setStatus", sagaStatusClass).invoke(builder, avroSagaStatus)

            builderClass.getMethod("setOrderId", String::class.java).invoke(builder, orderId)
            builderClass.getMethod("setMetadata", String::class.java)
                .invoke(builder, objectMapper.writeValueAsString(metadata))
            builderClass.getMethod("setRecordedAt", Long::class.java).invoke(builder, now.toEpochMilli())

            // build() 호출
            builderClass.getMethod("build").invoke(builder)
        } catch (e: Exception) {
            logger.error("Failed to build SagaTracker via reflection: ${e.message}", e)
            null
        }
    }

    private fun buildMetadata(metadata: Map<String, Any>?): Map<String, Any> {
        val result = metadata?.toMutableMap() ?: mutableMapOf()

        // Producer 서비스 이름 추가
        result["producerService"] = serviceName
        result["producerTimestamp"] = Instant.now().toString()

        // OpenTelemetry Trace ID 추출 시도 (있는 경우)
        try {
            val traceClass = Class.forName("io.opentelemetry.api.trace.Span")
            val currentMethod = traceClass.getMethod("current")
            val span = currentMethod.invoke(null)
            if (span != null) {
                val spanContextMethod = span.javaClass.getMethod("getSpanContext")
                val spanContext = spanContextMethod.invoke(span)
                if (spanContext != null) {
                    val traceIdMethod = spanContext.javaClass.getMethod("getTraceId")
                    val spanIdMethod = spanContext.javaClass.getMethod("getSpanId")
                    val traceId = traceIdMethod.invoke(spanContext) as? String
                    val spanId = spanIdMethod.invoke(spanContext) as? String
                    if (!traceId.isNullOrBlank() && traceId != "00000000000000000000000000000000") {
                        result["traceId"] = traceId
                    }
                    if (!spanId.isNullOrBlank() && spanId != "0000000000000000") {
                        result["spanId"] = spanId
                    }
                }
            }
        } catch (e: Exception) {
            // OpenTelemetry가 없거나 Trace가 없는 경우 무시
            logger.trace("OpenTelemetry trace context not available: ${e.message}")
        }

        return result
    }
}
