dependencies {
    // Platform DataSource Core
    // api로 노출하여 서비스에서 DynamicRoutingDataSource, DataSourceType 사용 가능
    api(project(":datasource-core"))

    // Spring Boot
    api("org.springframework.boot:spring-boot-starter-data-jpa:3.3.4")
    api("org.springframework.boot:spring-boot-starter-jdbc:3.3.4")

    // Configuration Processor (for yml autocomplete)
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor:3.3.4")

    // HikariCP (already included in spring-boot-starter-jdbc)
    api("com.zaxxer:HikariCP")

    // Optional dependencies
    compileOnly("org.postgresql:postgresql")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "com.groom.platform"
            artifactId = "datasource-starter"
            version = project.version.toString()

            from(components["java"])

            pom {
                name.set("Platform DataSource Spring Boot Starter")
                description.set("Dynamic routing datasource for primary-replica pattern with @Transactional(readOnly) support")
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
