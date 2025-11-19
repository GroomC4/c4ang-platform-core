import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

dependencies {
    // Spring Boot
    api("org.springframework.boot:spring-boot-starter:3.3.4")
    api("org.springframework:spring-context:6.1.13")

    // DataSource 호환성을 위해
    compileOnly(project(":datasource-starter"))
    compileOnly(project(":datasource-core"))

    // Configuration Properties
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor:3.3.4")

    // Process 실행을 위한 라이브러리
    implementation("org.apache.commons:commons-exec:1.4.0")

    // YAML 파싱
    implementation("org.yaml:snakeyaml:2.2")

    // 테스트
    testImplementation("org.springframework.boot:spring-boot-starter-test:3.3.4")
    testImplementation("org.testcontainers:testcontainers:1.19.3")
    testImplementation("org.testcontainers:postgresql:1.19.3")
    testRuntimeOnly("org.postgresql:postgresql:42.6.0")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "com.groom.platform"
            artifactId = "local-dev-starter"
            version = project.version.toString()

            from(components["java"])

            pom {
                name.set("Local Dev Starter")
                description.set("Spring Boot starter for automatic local development environment setup")
                url.set("https://github.com/GroomC4/c4ang-platform-core")

                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }

                developers {
                    developer {
                        id.set("c4ang-team")
                        name.set("C4ang Team")
                        email.set("c4ang@groom.com")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/GroomC4/c4ang-platform-core.git")
                    developerConnection.set("scm:git:ssh://github.com:GroomC4/c4ang-platform-core.git")
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
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "21"
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}