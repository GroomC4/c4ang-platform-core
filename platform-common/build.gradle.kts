dependencies {
    // Spring JDBC (AbstractRoutingDataSource)
    api("org.springframework:spring-jdbc:6.1.13")

    // Spring TX (TransactionSynchronizationManager)
    api("org.springframework:spring-tx:6.1.13")
}


publishing {
    publications {
        create<MavenPublication>("gpr") {
            groupId = "io.github.groomc4"
            artifactId = "platform-common"
            version = project.version.toString()

            from(components["java"])

            pom {
                name.set("Platform Common")
                description.set("Common classes for platform-core and testcontainers-starter (DataSourceType, DynamicRoutingDataSource)")
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