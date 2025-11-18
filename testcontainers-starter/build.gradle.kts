dependencies {
    // Platform DataSource Starter 의존
    // implementation으로 변경하여 전이 의존성 방지
    // (TestDataSourceAutoConfiguration에서만 사용, 서비스에 노출 불필요)
    implementation(project(":datasource-starter"))

    // Spring Boot
    api("org.springframework.boot:spring-boot-starter-data-jpa:3.3.4")
    api("org.springframework.boot:spring-boot-starter-data-redis:3.3.4")
    api("org.springframework.boot:spring-boot-starter-test:3.3.4")

    // Testcontainers
    api("org.testcontainers:testcontainers:1.19.3")
    api("org.testcontainers:postgresql:1.19.3")
    api("org.testcontainers:kafka:1.19.3")
    api("org.testcontainers:junit-jupiter:1.19.3")

    // Kafka
    api("org.springframework.kafka:spring-kafka:3.3.0")

    // Configuration Processor
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor:3.3.4")

    // PostgreSQL Driver
    compileOnly("org.postgresql:postgresql")

    // Redis
    api("org.springframework.data:spring-data-redis:3.3.4")
    api("io.lettuce:lettuce-core:6.3.2.RELEASE")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "com.groom.platform"
            artifactId = "testcontainers-starter"
            version = project.version.toString()

            from(components["java"])

            pom {
                name.set("Platform Testcontainers Spring Boot Starter")
                description.set("Testcontainers auto-configuration for integration tests with Primary-Replica support")
                url.set("https://github.com/GroomC4/c4ang-platform-core")

                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }

                developers {
                    developer {
                        id.set("groom")
                        name.set("Groom Team")
                        email.set("dev@groom.com")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/GroomC4/c4ang-platform-core.git")
                    developerConnection.set("scm:git:ssh://github.com/GroomC4/c4ang-platform-core.git")
                    url.set("https://github.com/GroomC4/c4ang-platform-core")
                }
            }
        }
    }

    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/GroomC4/c4ang-platform-core")
            credentials {
                username = System.getenv("GITHUB_ACTOR") ?: project.findProperty("gpr.user") as String?
                password = System.getenv("GITHUB_TOKEN") ?: project.findProperty("gpr.token") as String?
            }
        }
    }
}
