package com.groom.platform.autoconfigure

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Import

/**
 * Platform Core 메인 자동 설정
 *
 * 이 AutoConfiguration은 다음 기능을 제공합니다:
 * - DataSource Primary/Replica 라우팅
 * - local 프로필에서 Docker Compose 자동 실행
 * - test 프로필에서 Testcontainers 연동 지원
 *
 * **사용법:**
 * ```kotlin
 * // build.gradle.kts
 * implementation("com.groom.platform:platform-core:2.0.0")
 *
 * // application.yml - 프로필만 지정하면 자동 구성
 * spring:
 *   profiles:
 *     active: local
 * ```
 */
@AutoConfiguration
@EnableConfigurationProperties(PlatformProperties::class)
@Import(DataSourceConfiguration::class)
class PlatformAutoConfiguration {

    private val logger = KotlinLogging.logger {}

    init {
        logger.info { "Platform Core Auto-Configuration initialized" }
    }
}
