package com.groom.platform.datasource.autoconfigure

import com.groom.platform.datasource.DataSourceType
import com.groom.platform.datasource.DynamicRoutingDataSource
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration as SpringDataSourceAutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy
import javax.sql.DataSource

/**
 * Platform DataSource 자동 설정
 *
 * 이 AutoConfiguration은 Primary-Replica 패턴을 위한 DynamicRoutingDataSource를 제공합니다.
 * 실제 master/replica DataSource는 각 서비스의 application.yml에서 설정해야 합니다.
 *
 * **주의사항:**
 * - 이 설정은 프로덕션 환경용입니다
 * - 테스트 환경은 testcontainers-starter를 사용하세요
 * - @Profile("!test") 조건으로 테스트 환경에서는 비활성화됨
 *
 * **서비스에서 해야 할 일:**
 * 1. application.yml에 master/replica datasource 설정
 * 2. 이 Starter 의존성 추가
 * 3. @Transactional(readOnly=true/false) 사용
 *
 * **설정 예시 (application.yml):**
 * ```yaml
 * spring:
 *   datasource:
 *     master:
 *       url: jdbc:postgresql://master-host:5432/mydb
 *       username: user
 *       password: password
 *       hikari:
 *         maximum-pool-size: 10
 *     replica:
 *       url: jdbc:postgresql://replica-host:5432/mydb
 *       username: user
 *       password: password
 *       hikari:
 *         maximum-pool-size: 20
 *
 * platform:
 *   datasource:
 *     replica-enabled: true
 * ```
 */
@AutoConfiguration(
    before = [SpringDataSourceAutoConfiguration::class],
    after = [DataSourceDefaultConfiguration::class]
)
@ConditionalOnClass(DataSource::class)
@EnableConfigurationProperties(PlatformDataSourceProperties::class)
@org.springframework.context.annotation.Profile("!test")  // ⭐ 테스트 환경에서는 비활성화
class DataSourceAutoConfiguration {

    /**
     * Routing DataSource Bean 생성
     *
     * 주의: master/replica DataSource Bean은 각 서비스에서 생성하거나
     * DataSourceDefaultConfiguration에서 자동으로 생성됩니다.
     *
     * @Qualifier를 사용하여 명시적으로 주입받아 순환 참조 문제를 해결합니다.
     */
    @Bean
    @ConditionalOnMissingBean(name = ["routingDataSource"])
    fun routingDataSource(
        @Qualifier("masterDataSource") masterDataSource: DataSource,
        @Qualifier("replicaDataSource") replicaDataSource: DataSource,
    ): DataSource {
        val router = DynamicRoutingDataSource()
        router.setTargetDataSources(
            mapOf(
                DataSourceType.MASTER to masterDataSource,
                DataSourceType.REPLICA to replicaDataSource,
            ),
        )
        router.setDefaultTargetDataSource(masterDataSource)
        router.afterPropertiesSet()

        return router
    }

    /**
     * Primary DataSource Bean 생성 (LazyConnectionDataSourceProxy)
     *
     * LazyConnectionDataSourceProxy를 사용하여 실제 Connection이 필요한 시점까지
     * DataSource 선택을 지연시킵니다.
     */
    @Primary
    @Bean
    @ConditionalOnMissingBean(name = ["dataSource"])
    fun dataSource(routingDataSource: DataSource): DataSource {
        return LazyConnectionDataSourceProxy(routingDataSource)
    }
}
