package com.groom.platform.autoconfigure

import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration as SpringKafkaAutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory

/**
 * Platform Kafka 자동 설정
 *
 * Local 프로필에서 LocalInfraEnvironmentPostProcessor가 주입한 동적 포트를 사용하여
 * Kafka Producer/Consumer를 자동 구성합니다.
 *
 * **주입되는 프로퍼티 (LocalInfraEnvironmentPostProcessor):**
 * - spring.kafka.bootstrap-servers
 *
 * **자동 구성되는 Bean:**
 * - kafkaProducerFactory: Producer 팩토리
 * - kafkaTemplate: 메시지 전송 템플릿
 * - kafkaConsumerFactory: Consumer 팩토리
 * - kafkaListenerContainerFactory: @KafkaListener 컨테이너 팩토리
 */
@AutoConfiguration(before = [SpringKafkaAutoConfiguration::class])
@ConditionalOnClass(name = ["org.springframework.kafka.core.KafkaTemplate"])
@ConditionalOnProperty(prefix = "spring.kafka", name = ["bootstrap-servers"])
class KafkaConfiguration {

    private val logger = KotlinLogging.logger {}

    /**
     * Kafka Producer Factory
     *
     * String Key/Value 기본 설정
     */
    @Bean
    @ConditionalOnMissingBean(ProducerFactory::class)
    fun kafkaProducerFactory(
        @Value("\${spring.kafka.bootstrap-servers}") bootstrapServers: String,
        @Value("\${spring.kafka.producer.acks:all}") acks: String,
        @Value("\${spring.kafka.producer.retries:3}") retries: Int,
        @Value("\${spring.kafka.producer.batch-size:16384}") batchSize: Int,
        @Value("\${spring.kafka.producer.linger-ms:1}") lingerMs: Int,
        @Value("\${spring.kafka.producer.buffer-memory:33554432}") bufferMemory: Long
    ): ProducerFactory<String, String> {
        logger.info { "Creating Kafka Producer Factory: $bootstrapServers" }

        val configs = mapOf(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ProducerConfig.ACKS_CONFIG to acks,
            ProducerConfig.RETRIES_CONFIG to retries,
            ProducerConfig.BATCH_SIZE_CONFIG to batchSize,
            ProducerConfig.LINGER_MS_CONFIG to lingerMs,
            ProducerConfig.BUFFER_MEMORY_CONFIG to bufferMemory
        )

        return DefaultKafkaProducerFactory(configs)
    }

    /**
     * Kafka Template
     *
     * 메시지 전송을 위한 템플릿
     */
    @Bean
    @ConditionalOnMissingBean(KafkaTemplate::class)
    fun kafkaTemplate(producerFactory: ProducerFactory<String, String>): KafkaTemplate<String, String> {
        logger.info { "Creating KafkaTemplate" }
        return KafkaTemplate(producerFactory)
    }

    /**
     * Kafka Consumer Factory
     *
     * String Key/Value 기본 설정
     */
    @Bean
    @ConditionalOnMissingBean(ConsumerFactory::class)
    fun kafkaConsumerFactory(
        @Value("\${spring.kafka.bootstrap-servers}") bootstrapServers: String,
        @Value("\${spring.kafka.consumer.group-id:platform-group}") groupId: String,
        @Value("\${spring.kafka.consumer.auto-offset-reset:earliest}") autoOffsetReset: String,
        @Value("\${spring.kafka.consumer.enable-auto-commit:false}") enableAutoCommit: Boolean,
        @Value("\${spring.kafka.consumer.max-poll-records:500}") maxPollRecords: Int
    ): ConsumerFactory<String, String> {
        logger.info { "Creating Kafka Consumer Factory: $bootstrapServers (group: $groupId)" }

        val configs = mapOf(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ConsumerConfig.GROUP_ID_CONFIG to groupId,
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to autoOffsetReset,
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to enableAutoCommit,
            ConsumerConfig.MAX_POLL_RECORDS_CONFIG to maxPollRecords
        )

        return DefaultKafkaConsumerFactory(configs)
    }

    /**
     * Kafka Listener Container Factory
     *
     * @KafkaListener 어노테이션을 위한 컨테이너 팩토리
     */
    @Bean
    @ConditionalOnMissingBean(name = ["kafkaListenerContainerFactory"])
    fun kafkaListenerContainerFactory(
        consumerFactory: ConsumerFactory<String, String>
    ): ConcurrentKafkaListenerContainerFactory<String, String> {
        logger.info { "Creating Kafka Listener Container Factory" }

        return ConcurrentKafkaListenerContainerFactory<String, String>().apply {
            this.consumerFactory = consumerFactory
            setConcurrency(3)
            containerProperties.pollTimeout = 3000
        }
    }
}
