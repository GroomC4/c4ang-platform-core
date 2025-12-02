dependencies {
    // Platform Common (공통 클래스: DataSourceType, DynamicRoutingDataSource)
    api(project(":platform-common"))

    // Spring Boot
    api("org.springframework.boot:spring-boot-starter-data-jpa:3.3.4")
    api("org.springframework.boot:spring-boot-starter-jdbc:3.3.4")

    // Configuration Processor (for yml autocomplete)
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor:3.3.4")

    // HikariCP (already included in spring-boot-starter-jdbc)
    api("com.zaxxer:HikariCP")

    // PostgreSQL Driver (optional - provided by domain service)
    compileOnly("org.postgresql:postgresql")

    // Redis (optional)
    compileOnly("org.springframework.boot:spring-boot-starter-data-redis:3.3.4")

    // Kafka (optional)
    compileOnly("org.springframework.kafka:spring-kafka:3.3.0")

    // Test dependencies
    testImplementation("org.springframework.boot:spring-boot-starter-test:3.3.4")
    testRuntimeOnly("com.h2database:h2")

    // Testcontainers (test scope only)
    testImplementation("org.testcontainers:testcontainers:1.19.7")
    testImplementation("org.testcontainers:postgresql:1.19.7")
    testImplementation("org.testcontainers:junit-jupiter:1.19.7")
}

publishing {
    publications {
        create<MavenPublication>("gpr") {
            groupId = "io.github.groomc4"
            artifactId = "platform-core"
            version = project.version.toString()

            from(components["java"])

            pom {
                name.set("Platform Core")
                description.set("Unified platform core for datasource routing, local dev infrastructure, and test support")
                url.set("https://github.com/GroomC4/c4ang-platform-core")

                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }

                developers {
                    developer {
                        id.set("GroomC4")
                        name.set("GroomC4 Team")
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
        mavenLocal()

        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/GroomC4/c4ang-packages-hub")
            credentials {
                username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_ACTOR")
                password = project.findProperty("gpr.key") as String? ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
