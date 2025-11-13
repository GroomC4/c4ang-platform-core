package com.groom.platform.testSupport

/**
 * Docker Desktop Kubernetes를 사용한 로컬 E2E 테스트 지원
 *
 * 사전 요구사항:
 * - Docker Desktop Kubernetes 활성화
 * - kubectl 설치
 * - Helm 설치
 */
object LocalK8sTestSupport {
    fun isLocalK8sAvailable(): Boolean =
        try {
            val process =
                ProcessBuilder("kubectl", "cluster-info")
                    .redirectErrorStream(true)
                    .start()
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }

    fun setupTestInfra(namespace: String = "e2e-test") {
        require(isLocalK8sAvailable()) {
            "Docker Desktop Kubernetes is not available. " +
                "Please enable it in Docker Desktop settings."
        }

        // 네임스페이스 생성
        exec("kubectl", "create", "namespace", namespace, "--dry-run=client", "-o", "yaml")
        exec("kubectl", "apply", "-f", "-")

        // Helm 차트 배포
        val helmInstall =
            ProcessBuilder(
                "helm",
                "install",
                "e2e-infra",
                "../c4ang-infra/helm/test-infrastructure",
                "--namespace",
                namespace,
                "--create-namespace",
                "--wait",
                "--timeout",
                "5m",
            ).inheritIO().start()

        require(helmInstall.waitFor() == 0) {
            "Failed to install test infrastructure"
        }

        println("✅ E2E test infrastructure installed successfully")
    }

    fun teardownTestInfra(namespace: String = "e2e-test") {
        ProcessBuilder(
            "helm",
            "uninstall",
            "e2e-infra",
            "--namespace",
            namespace,
        ).inheritIO().start().waitFor()

        ProcessBuilder(
            "kubectl",
            "delete",
            "namespace",
            namespace,
        ).inheritIO().start().waitFor()

        println("🛑 E2E test infrastructure removed")
    }

    private fun exec(vararg command: String): Int =
        ProcessBuilder(*command)
            .inheritIO()
            .start()
            .waitFor()
}
