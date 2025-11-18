dependencies {
    // Spring JDBC (for AbstractRoutingDataSource)
    api("org.springframework:spring-jdbc")

    // Spring Transaction (for TransactionSynchronizationManager)
    api("org.springframework:spring-tx")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "com.groom.platform"
            artifactId = "datasource-core"
            version = project.version.toString()

            from(components["java"])

            pom {
                name.set("Platform DataSource Core")
                description.set("Core classes for dynamic routing datasource (Primary-Replica pattern)")
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
