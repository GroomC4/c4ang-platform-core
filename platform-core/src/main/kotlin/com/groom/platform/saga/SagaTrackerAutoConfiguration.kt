package com.groom.platform.saga

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.core.KafkaTemplate

/**
 * Saga Tracker Client Auto Configuration
 *
 * platform.saga.enabled=true (기본값: false)이고 Kafka가 사용 가능한 경우
 * KafkaSagaTrackerClient를 빈으로 등록합니다.
 * 그렇지 않은 경우 NoOpSagaTrackerClient를 등록합니다.
 *
 * @see SagaTrackerProperties
 */
@Configuration
@EnableConfigurationProperties(SagaTrackerProperties::class)
@ConditionalOnProperty(
    prefix = "platform.saga",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = false
)
class SagaTrackerAutoConfiguration {

    private val logger = LoggerFactory.getLogger(SagaTrackerAutoConfiguration::class.java)

    /**
     * Kafka 기반 SagaTrackerClient 빈
     * - KafkaTemplate이 있는 경우 활성화
     */
    @Bean
    @ConditionalOnClass(KafkaTemplate::class)
    @ConditionalOnBean(KafkaTemplate::class)
    fun kafkaSagaTrackerClient(
        kafkaTemplate: KafkaTemplate<String, Any>,
        objectMapper: ObjectMapper,
        properties: SagaTrackerProperties,
        @Value("\${spring.application.name:unknown}") serviceName: String
    ): SagaTrackerClient {
        logger.info("Initializing KafkaSagaTrackerClient with topic: ${properties.topic}, service: $serviceName")
        return KafkaSagaTrackerClient(
            kafkaTemplate = kafkaTemplate,
            topic = properties.topic,
            objectMapper = objectMapper,
            serviceName = serviceName
        )
    }

    /**
     * NoOp SagaTrackerClient 빈
     * - KafkaSagaTrackerClient가 등록되지 않은 경우 Fallback으로 사용
     */
    @Bean
    @ConditionalOnMissingBean(SagaTrackerClient::class)
    fun noOpSagaTrackerClient(): SagaTrackerClient {
        logger.warn("KafkaSagaTrackerClient not available, using NoOpSagaTrackerClient")
        return NoOpSagaTrackerClient()
    }
}
