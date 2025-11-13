package com.groom.platform.testSupport

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag

@Tag("e2e-test")
abstract class E2ETestBase {
    companion object {
        @BeforeAll
        @JvmStatic
        fun setUpE2EInfrastructure() {
            println("🚀 Setting up E2E test infrastructure...")
            LocalK8sTestSupport.setupTestInfra()
        }

        @AfterAll
        @JvmStatic
        fun tearDownE2EInfrastructure() {
            println("🛑 Tearing down E2E test infrastructure...")
            LocalK8sTestSupport.teardownTestInfra()
        }
    }
}
