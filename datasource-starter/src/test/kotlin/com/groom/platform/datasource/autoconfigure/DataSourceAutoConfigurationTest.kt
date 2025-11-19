package com.groom.platform.datasource.autoconfigure

import com.groom.platform.datasource.DynamicRoutingDataSource
import com.zaxxer.hikari.HikariDataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy
import javax.sql.DataSource

class DataSourceAutoConfigurationTest {

    private val contextRunner = ApplicationContextRunner()
        .withPropertyValues(
            "spring.datasource.master.url=jdbc:h2:mem:testdb-master",
            "spring.datasource.master.driver-class-name=org.h2.Driver",
            "spring.datasource.replica.url=jdbc:h2:mem:testdb-replica",
            "spring.datasource.replica.driver-class-name=org.h2.Driver"
        )
        .withConfiguration(
            AutoConfigurations.of(
                DataSourceDefaultConfiguration::class.java,
                DataSourceAutoConfiguration::class.java
            )
        )

    @Test
    fun `should create all datasource beans without circular reference`() {
        contextRunner.run { context ->
            // 모든 필요한 빈이 생성되었는지 확인
            assertThat(context).hasBean("masterDataSource")
            assertThat(context).hasBean("replicaDataSource")
            assertThat(context).hasBean("routingDataSource")
            assertThat(context).hasBean("dataSource")

            // Primary DataSource가 LazyConnectionDataSourceProxy인지 확인
            val primaryDataSource = context.getBean("dataSource", DataSource::class.java)
            assertThat(primaryDataSource).isInstanceOf(LazyConnectionDataSourceProxy::class.java)

            // Routing DataSource가 DynamicRoutingDataSource인지 확인
            val routingDataSource = context.getBean("routingDataSource", DataSource::class.java)
            assertThat(routingDataSource).isInstanceOf(DynamicRoutingDataSource::class.java)
        }
    }

    @Test
    fun `should use master datasource when replica is not configured`() {
        val runnerWithoutReplica = ApplicationContextRunner()
            .withPropertyValues(
                "spring.datasource.master.url=jdbc:h2:mem:testdb-master",
                "spring.datasource.master.driver-class-name=org.h2.Driver"
            )
            .withConfiguration(
                AutoConfigurations.of(
                    DataSourceDefaultConfiguration::class.java,
                    DataSourceAutoConfiguration::class.java
                )
            )

        runnerWithoutReplica.run { context ->
            assertThat(context).hasBean("masterDataSource")
            assertThat(context).hasBean("replicaDataSource")

            val master = context.getBean("masterDataSource", DataSource::class.java)
            val replica = context.getBean("replicaDataSource", DataSource::class.java)

            // replica가 master와 같은 인스턴스인지 확인
            assertThat(replica).isSameAs(master)
        }
    }

    @Test
    fun `should use custom datasource when defined by user`() {
        contextRunner
            .withUserConfiguration(CustomDataSourceConfiguration::class.java)
            .run { context ->
                assertThat(context).hasBean("masterDataSource")
                assertThat(context).hasBean("replicaDataSource")

                val master = context.getBean("masterDataSource", DataSource::class.java)
                // 커스텀 DataSource가 사용되었는지 확인
                assertThat(master).isInstanceOf(HikariDataSource::class.java)
                assertThat((master as HikariDataSource).maximumPoolSize).isEqualTo(100)
            }
    }

    @Configuration
    class CustomDataSourceConfiguration {
        @Bean
        fun masterDataSource(): DataSource {
            val ds = HikariDataSource()
            ds.jdbcUrl = "jdbc:h2:mem:custom-master"
            ds.driverClassName = "org.h2.Driver"
            ds.maximumPoolSize = 100  // 커스텀 설정
            return ds
        }

        @Bean
        fun replicaDataSource(): DataSource {
            val ds = HikariDataSource()
            ds.jdbcUrl = "jdbc:h2:mem:custom-replica"
            ds.driverClassName = "org.h2.Driver"
            return ds
        }
    }
}