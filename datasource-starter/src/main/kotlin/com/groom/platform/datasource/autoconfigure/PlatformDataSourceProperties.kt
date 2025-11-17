package com.groom.platform.datasource.autoconfigure

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Platform DataSource 설정 프로퍼티
 *
 * **사용 예시 (application.yml):**
 * ```yaml
 * platform:
 *   datasource:
 *     replica-enabled: true
 *     logging-enabled: false
 * ```
 */
@ConfigurationProperties(prefix = "platform.datasource")
data class PlatformDataSourceProperties(
    /**
     * Replica DataSource 활성화 여부
     *
     * true: Replica DataSource Bean 생성
     * false: Master DataSource만 사용 (Replica 비활성화)
     *
     * 기본값: true
     */
    var replicaEnabled: Boolean = true,

    /**
     * DataSource 라우팅 로깅 활성화 여부
     *
     * true: 각 쿼리마다 어느 DataSource(MASTER/REPLICA)를 사용하는지 로그 출력
     * false: 로깅 비활성화
     *
     * 기본값: false
     */
    var loggingEnabled: Boolean = false,
)
