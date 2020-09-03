plugins {
    kotlin("jvm") version "1.4.0"
    id("org.jetbrains.dokka") version "1.4.0-rc"
    `java-library`
    jacoco
    `maven-publish`
}

val urlKj = findProperty("PROJECT_URL") as String
val groupKj = findProperty("GROUP") as String
val descriptionKj = findProperty("DESCRIPTION") as String
val artifactKj = findProperty("ARTIFACT_ID") as String

description = descriptionKj
group = groupKj

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.6.2")
    testImplementation("com.jayway.jsonpath:json-path:2.4.0")
    testImplementation("org.mockito:mockito-core:3.3.3")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
    withJavadocJar()
    withSourcesJar()
}

repositories {
    jcenter()
    mavenCentral()
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
    }

    jacocoTestReport {
        reports {
            html.isEnabled = true
            xml.isEnabled = false
            csv.isEnabled = false
            html.destination = file("${buildDir}/reports/jacoco/html")

        }
    }

    test {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
        finalizedBy(jacocoTestReport)
    }

    javadoc {
        options {
            outputLevel = JavadocOutputLevel.QUIET
        }
    }
}

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/barddoo/kson")
            credentials {
                username = findProperty("GITHUB_USER") as String?
                password = findProperty("GITHUB_TOKEN") as String?
            }
        }
        publications {
            create<MavenPublication>("kson") {
                groupId = groupKj
                artifactId = artifactKj
                version = findProperty("VERSION_NAME") as String

                from(components["java"])

                pom {
                    name.set(artifactKj)
                    description.set(descriptionKj)
                    url.set(urlKj)
                    licenses {
                        license {
                            name.set(findProperty("LICENCE_NAME") as String)
                            url.set(findProperty("LICENCE_URL") as String)
                        }
                    }
                    developers {
                        developer {
                            name.set("Charles Fonseca")
                            email.set("charlesjrfonseca@gmail.com")
                        }
                    }
                    scm {
                        connection.set(findProperty("SCM_CONNECTION") as String)
                        url.set(urlKj)
                    }
                }
            }
        }
    }
}
