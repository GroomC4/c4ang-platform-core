package com.groom.platform.datasource.autoconfigure

import com.zaxxer.hikari.HikariDataSource
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfigureBefore
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import javax.sql.DataSource

/**
 * DataSource 기본 설정 제공
 *
 * 이 설정은 도메인 서버가 master/replica DataSource를 직접 정의하지 않았을 때
 * application.yml 설정을 기반으로 자동으로 DataSource 빈을 생성합니다.
 *
 * **자동 생성되는 빈:**
 * - masterDataSource: spring.datasource.master 설정 기반
 * - replicaDataSource: spring.datasource.replica 설정 기반
 *
 * **도메인 서버 사용법:**
 * 1. 기본 사용 (yml 설정만):
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
 * ```
 *
 * 2. 커스터마이징:
 * 도메인 서버에서 masterDataSource 또는 replicaDataSource 빈을 직접 정의하면
 * 해당 빈은 이 설정에서 생성되지 않고 도메인의 정의가 우선 적용됩니다.
 */
@Configuration
@Profile("!test")
@AutoConfigureBefore(DataSourceAutoConfiguration::class)
@ConditionalOnClass(DataSource::class)
class DataSourceDefaultConfiguration {

    /**
     * Master DataSource Properties
     */
    @Bean
    @ConditionalOnMissingBean(name = ["masterDataSourceProperties"])
    @ConfigurationProperties("spring.datasource.master")
    fun masterDataSourceProperties(): DataSourceProperties = DataSourceProperties()

    /**
     * Master DataSource
     *
     * 도메인 서버에서 masterDataSource 빈을 정의하지 않았을 때만 생성됩니다.
     */
    @Bean
    @ConditionalOnMissingBean(name = ["masterDataSource"])
    @ConditionalOnProperty(
        prefix = "spring.datasource.master",
        name = ["url"],
        matchIfMissing = false
    )
    fun masterDataSource(
        @Qualifier("masterDataSourceProperties") properties: DataSourceProperties,
    ): DataSource {
        return properties.initializeDataSourceBuilder()
            .type(HikariDataSource::class.java)
            .build()
            .also {
                println("✅ Master DataSource auto-configured from spring.datasource.master")
            }
    }

    /**
     * Replica DataSource Properties
     */
    @Bean
    @ConditionalOnMissingBean(name = ["replicaDataSourceProperties"])
    @ConfigurationProperties("spring.datasource.replica")
    fun replicaDataSourceProperties(): DataSourceProperties = DataSourceProperties()

    /**
     * Replica DataSource
     *
     * 도메인 서버에서 replicaDataSource 빈을 정의하지 않았을 때만 생성됩니다.
     * spring.datasource.replica 설정이 없으면 masterDataSource를 사용합니다.
     */
    @Bean
    @ConditionalOnMissingBean(name = ["replicaDataSource"])
    fun replicaDataSource(
        @Qualifier("replicaDataSourceProperties") replicaProperties: DataSourceProperties,
        @Qualifier("masterDataSource") masterDataSource: DataSource,
    ): DataSource {
        // replica 설정이 있으면 replica DataSource 생성, 없으면 master 사용
        return if (replicaProperties.url != null) {
            replicaProperties.initializeDataSourceBuilder()
                .type(HikariDataSource::class.java)
                .build()
                .also {
                    println("✅ Replica DataSource auto-configured from spring.datasource.replica")
                }
        } else {
            println("ℹ️ Replica DataSource not configured, using Master DataSource")
            masterDataSource
        }
    }
}