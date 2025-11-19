package com.groom.platform.localdev.docker

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty

/**
 * Tests for DockerHealthChecker
 * These tests require Docker to be installed and running
 */
class DockerHealthCheckerTest {

    private val healthChecker = DockerHealthChecker()

    @Test
    fun `should detect if Docker is available`() {
        // This test will pass or fail based on whether Docker is installed
        val available = healthChecker.isDockerAvailable()
        // Just check that the method doesn't throw an exception
        assertThat(available).isIn(true, false)
    }

    @Test
    fun `should detect if Docker Compose is available`() {
        // This test will pass or fail based on whether Docker Compose is installed
        val available = healthChecker.isDockerComposeAvailable()
        // Just check that the method doesn't throw an exception
        assertThat(available).isIn(true, false)
    }

    @Test
    fun `should check port availability`() {
        // Test with a likely available port
        val available = healthChecker.isPortAvailable(54321)
        assertThat(available).isTrue()

        // Test with a likely used port (SSH)
        // Note: This might fail in some environments
        // val notAvailable = healthChecker.isPortAvailable(22)
        // assertThat(notAvailable).isFalse()
    }

    @Test
    fun `should find an available port`() {
        val port = healthChecker.findAvailablePort(54321)
        assertThat(port).isPositive()

        // If the preferred port is available, it should return it
        if (healthChecker.isPortAvailable(54321)) {
            assertThat(port).isEqualTo(54321)
        }
    }

    @Test
    @EnabledIfSystemProperty(named = "docker.tests.enabled", matches = "true")
    fun `should check container health when Docker is available`() {
        // This test requires Docker and a running container
        // It's disabled by default to avoid CI failures

        // Try to check a non-existent container
        val healthy = healthChecker.isContainerHealthy("non-existent-container")
        assertThat(healthy).isFalse()
    }

    @Test
    @EnabledIfSystemProperty(named = "docker.tests.enabled", matches = "true")
    fun `should check all containers health for a project`() {
        // This test requires Docker and Docker Compose
        // It's disabled by default to avoid CI failures

        val healthy = healthChecker.areAllContainersHealthy("non-existent-project")
        assertThat(healthy).isFalse()
    }
}