package com.groom.platform.autoconfigure

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration as SpringRedisAutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.StringRedisSerializer

/**
 * Platform Redis 자동 설정
 *
 * Local 프로필에서 LocalInfraEnvironmentPostProcessor가 주입한 동적 포트를 사용하여
 * Redis 연결을 자동 구성합니다.
 *
 * **주입되는 프로퍼티 (LocalInfraEnvironmentPostProcessor):**
 * - spring.data.redis.host
 * - spring.data.redis.port
 *
 * **자동 구성되는 Bean:**
 * - redisConnectionFactory: Lettuce 기반 연결 팩토리
 * - redisTemplate: Object 직렬화 지원 템플릿
 * - stringRedisTemplate: String 전용 템플릿
 */
@AutoConfiguration(before = [SpringRedisAutoConfiguration::class])
@ConditionalOnClass(name = ["org.springframework.data.redis.core.RedisTemplate"])
@ConditionalOnProperty(prefix = "spring.data.redis", name = ["host"])
class RedisConfiguration {

    private val logger = KotlinLogging.logger {}

    /**
     * Redis Connection Factory
     *
     * Lettuce 클라이언트를 사용하여 Redis 연결을 생성합니다.
     */
    @Bean
    @ConditionalOnMissingBean(RedisConnectionFactory::class)
    fun redisConnectionFactory(
        @org.springframework.beans.factory.annotation.Value("\${spring.data.redis.host:localhost}") host: String,
        @org.springframework.beans.factory.annotation.Value("\${spring.data.redis.port:6379}") port: Int,
        @org.springframework.beans.factory.annotation.Value("\${spring.data.redis.password:}") password: String
    ): RedisConnectionFactory {
        logger.info { "Creating Redis Connection Factory: $host:$port" }

        val config = RedisStandaloneConfiguration(host, port)
        if (password.isNotBlank()) {
            config.setPassword(password)
        }

        return LettuceConnectionFactory(config).apply {
            afterPropertiesSet()
        }
    }

    /**
     * RedisTemplate for Object serialization
     *
     * Key: String, Value: JSON 직렬화
     */
    @Bean
    @ConditionalOnMissingBean(name = ["redisTemplate"])
    fun redisTemplate(redisConnectionFactory: RedisConnectionFactory): RedisTemplate<String, Any> {
        logger.info { "Creating RedisTemplate with JSON serialization" }

        return RedisTemplate<String, Any>().apply {
            connectionFactory = redisConnectionFactory
            keySerializer = StringRedisSerializer()
            valueSerializer = GenericJackson2JsonRedisSerializer()
            hashKeySerializer = StringRedisSerializer()
            hashValueSerializer = GenericJackson2JsonRedisSerializer()
            afterPropertiesSet()
        }
    }

    /**
     * StringRedisTemplate for String operations
     */
    @Bean
    @ConditionalOnMissingBean(StringRedisTemplate::class)
    fun stringRedisTemplate(redisConnectionFactory: RedisConnectionFactory): StringRedisTemplate {
        logger.info { "Creating StringRedisTemplate" }

        return StringRedisTemplate(redisConnectionFactory)
    }
}
