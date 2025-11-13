package com.groom.platform.testSupport

import io.fabric8.kubernetes.client.Config
import io.fabric8.kubernetes.client.KubernetesClientBuilder
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.testcontainers.k3s.K3sContainer
import org.testcontainers.utility.DockerImageName
import java.time.Duration

/**
 * K8s 통합 테스트를 위한 공유 K3s 컨테이너 Extension
 *
 * Testcontainers K3s Module을 사용하여 경량 Kubernetes 클러스터를 제공합니다.
 *
 * 사용 예시:
 * ```kotlin
 * @K8sIntegrationTest
 * @SpringBootTest
 * class CustomerServiceK8sIntegrationTest {
 *     @Test
 *     fun `K8s 환경에서 통합 테스트`() {
 *         val client = K8sContainerExtension.getKubernetesClient()
 *         // 테스트 로직
 *     }
 * }
 * ```
 */
class K8sContainerExtension : BeforeAllCallback {
    companion object {
        @Volatile
        private var initialized = false

        private lateinit var k3sContainer: K3sContainer
        private lateinit var kubeConfigYaml: String

        /**
         * Kubernetes 클라이언트를 반환합니다.
         */
        @JvmStatic
        fun getKubernetesClient() =
            KubernetesClientBuilder()
                .withConfig(Config.fromKubeconfig(kubeConfigYaml))
                .build()

        /**
         * Kubeconfig YAML을 반환합니다.
         */
        @JvmStatic
        fun getKubeConfig(): String = kubeConfigYaml

        /**
         * K8s API Server URL을 반환합니다.
         */
        @JvmStatic
        fun getApiServerUrl(): String {
            val config = Config.fromKubeconfig(kubeConfigYaml)
            return config.masterUrl
        }

        /**
         * Helm 차트를 배포합니다.
         */
        @JvmStatic
        fun installHelmChart(
            chartPath: String,
            releaseName: String,
            namespace: String = "default",
            values: Map<String, Any> = emptyMap(),
        ) {
            val valuesArgs =
                values.entries.joinToString(" ") { (k, v) ->
                    "--set $k=$v"
                }

            val helmInstallCmd =
                """
                helm install $releaseName $chartPath \
                    --namespace $namespace \
                    --create-namespace \
                    $valuesArgs \
                    --wait
                """.trimIndent()

            k3sContainer.execInContainer(
                "/bin/sh",
                "-c",
                helmInstallCmd,
            )
        }

        /**
         * Helm 차트를 제거합니다.
         */
        @JvmStatic
        fun uninstallHelmChart(
            releaseName: String,
            namespace: String = "default",
        ) {
            k3sContainer.execInContainer(
                "/bin/sh",
                "-c",
                "helm uninstall $releaseName --namespace $namespace",
            )
        }
    }

    override fun beforeAll(context: ExtensionContext) {
        synchronized(K8sContainerExtension::class.java) {
            if (!initialized) {
                println("🚀 Starting shared K3s container for integration tests...")

                k3sContainer =
                    K3sContainer(DockerImageName.parse("rancher/k3s:v1.28.5-k3s1"))
                        .withStartupTimeout(Duration.ofMinutes(2))

                k3sContainer.start()
                kubeConfigYaml = k3sContainer.kubeConfigYaml
                initialized = true

                // JVM 종료 시 컨테이너 정리
                Runtime.getRuntime().addShutdownHook(
                    Thread {
                        println("🛑 Stopping shared K3s container...")
                        k3sContainer.stop()
                    },
                )

                println("✅ K3s container started successfully")
                val config = Config.fromKubeconfig(kubeConfigYaml)
                println("📍 API Server: ${config.masterUrl}")
            }
        }
    }
}
