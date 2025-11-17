plugins {
    kotlin("jvm") version "2.0.21" apply false
    kotlin("plugin.spring") version "2.0.21" apply false
    id("org.springframework.boot") version "3.3.4" apply false
    id("io.spring.dependency-management") version "1.1.6" apply false
    `maven-publish`
}

allprojects {
    group = "com.groom.platform"
    // Tag 이름으로 버전 설정 (예: v1.2.0 → 1.2.0)
    // GitHub Actions에서 -Pversion=1.2.0 형태로 전달
    // 로컬 개발 시에는 기본값 사용
    version = project.findProperty("version") as String? ?: "1.2.1-SNAPSHOT"

    repositories {
        mavenCentral()
        maven {
            url = uri("https://packages.confluent.io/maven/")
        }
    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jetbrains.kotlin.plugin.spring")
    apply(plugin = "org.springframework.boot")
    apply(plugin = "io.spring.dependency-management")
    apply(plugin = "maven-publish")

    dependencies {
        val implementation by configurations

        // Kotlin
        implementation("org.jetbrains.kotlin:kotlin-reflect")
        implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

        // Logging
        implementation("io.github.oshai:kotlin-logging-jvm:7.0.0")
    }

    configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        compilerOptions {
            freeCompilerArgs.add("-Xjsr305=strict")
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    // Disable bootJar and enable plain jar for library modules
    tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
        enabled = false
    }

    tasks.named<Jar>("jar") {
        enabled = true
    }
}
