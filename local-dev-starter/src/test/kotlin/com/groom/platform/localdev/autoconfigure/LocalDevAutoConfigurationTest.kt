package com.groom.platform.localdev.autoconfigure

import com.groom.platform.localdev.docker.DockerComposeManager
import com.groom.platform.localdev.docker.DockerHealthChecker
import com.groom.platform.localdev.listener.LocalDevApplicationListener
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.DefaultResourceLoader

/**
 * Tests for LocalDevAutoConfiguration
 */
class LocalDevAutoConfigurationTest {

    private val contextRunner = ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                LocalDevAutoConfiguration::class.java
            )
        )

    @Test
    fun `should not create beans when not in local profile`() {
        contextRunner
            .run { context ->
                assertThat(context).doesNotHaveBean(DockerComposeManager::class.java)
                assertThat(context).doesNotHaveBean(DockerHealthChecker::class.java)
                assertThat(context).doesNotHaveBean(LocalDevApplicationListener::class.java)
            }
    }

    @Test
    fun `should create beans when in local profile and enabled`() {
        contextRunner
            .withPropertyValues(
                "spring.profiles.active=local",
                "platform.local-dev.enabled=true",
                "platform.local-dev.auto-start=true"
            )
            .run { context ->
                assertThat(context).hasSingleBean(LocalDevAutoConfiguration::class.java)
                assertThat(context).hasSingleBean(DockerHealthChecker::class.java)
                assertThat(context).hasSingleBean(DockerComposeManager::class.java)
                assertThat(context).hasSingleBean(LocalDevApplicationListener::class.java)
            }
    }

    @Test
    fun `should not create beans when explicitly disabled`() {
        contextRunner
            .withPropertyValues(
                "spring.profiles.active=local",
                "platform.local-dev.enabled=false"
            )
            .run { context ->
                assertThat(context).doesNotHaveBean(LocalDevAutoConfiguration::class.java)
                assertThat(context).doesNotHaveBean(DockerComposeManager::class.java)
                assertThat(context).doesNotHaveBean(LocalDevApplicationListener::class.java)
            }
    }

    @Test
    fun `should not create listener when auto-start is disabled`() {
        contextRunner
            .withPropertyValues(
                "spring.profiles.active=local",
                "platform.local-dev.enabled=true",
                "platform.local-dev.auto-start=false"
            )
            .run { context ->
                assertThat(context).hasSingleBean(DockerHealthChecker::class.java)
                assertThat(context).hasSingleBean(DockerComposeManager::class.java)
                assertThat(context).doesNotHaveBean(LocalDevApplicationListener::class.java)
            }
    }

    @Test
    fun `should load properties correctly`() {
        contextRunner
            .withPropertyValues(
                "spring.profiles.active=local",
                "platform.local-dev.enabled=true",
                "platform.local-dev.project-name=test-project",
                "platform.local-dev.services.postgres.primary-port=25432",
                "platform.local-dev.services.postgres.replica-port=25433",
                "platform.local-dev.services.redis.port=26379",
                "platform.local-dev.services.kafka.port=29092"
            )
            .run { context ->
                assertThat(context).hasSingleBean(LocalDevProperties::class.java)

                val properties = context.getBean(LocalDevProperties::class.java)
                assertThat(properties.projectName).isEqualTo("test-project")
                assertThat(properties.services.postgres.primaryPort).isEqualTo(25432)
                assertThat(properties.services.postgres.replicaPort).isEqualTo(25433)
                assertThat(properties.services.redis.port).isEqualTo(26379)
                assertThat(properties.services.kafka.port).isEqualTo(29092)
            }
    }

    @Configuration
    internal class TestConfiguration {
        @Bean
        fun resourceLoader() = DefaultResourceLoader()
    }
}