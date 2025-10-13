plugins {
    `java-library`
    `maven-publish`

    jacoco
}

group = "dev.emortal.minestom"
version = "1.0-SNAPSHOT"

repositories {
    maven(url = "https://central.sonatype.com/repository/maven-snapshots/") {
        content {
            includeModule("net.minestom", "minestom")
        }
    }
    mavenCentral()

    maven("https://repo.emortal.dev/snapshots")
    maven("https://repo.emortal.dev/releases")

    maven("https://jitpack.io")
    maven("https://packages.confluent.io/maven/")
}

dependencies {
    // Minestom
    api("net.minestom:minestom:2025.10.11-1.21.10")
    api("net.kyori:adventure-text-minimessage:4.25.0")

    implementation("io.pyroscope:agent:0.14.0")
    implementation("org.apache.kafka:kafka-clients:4.1.0")

    // Logger
    implementation("ch.qos.logback:logback-classic:1.5.18")
    implementation("net.logstash.logback:logstash-logback-encoder:8.1")

    // APIs
    api("dev.emortal.api:module-system:1.0.0")
    api("dev.emortal.api:agones-sdk:1.1.0")
    api("dev.emortal.api:common-proto-sdk:c96f109")
    api("dev.emortal.api:live-config-parser:d79a69a")

    api("io.kubernetes:client-java:18.0.1")

    api("io.micrometer:micrometer-registry-prometheus:1.13.6")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.0")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }

    withSourcesJar()
}

tasks {
    test {
        useJUnitPlatform()
    }
    jacocoTestReport {
        dependsOn(test)
    }
}

publishing {
    repositories {
        maven {
            name = "development"
            url = uri("https://repo.emortal.dev/snapshots")
            credentials {
                username = System.getenv("MAVEN_USERNAME")
                password = System.getenv("MAVEN_SECRET")
            }
        }
        maven {
            name = "release"
            url = uri("https://repo.emortal.dev/releases")
            credentials {
                username = System.getenv("MAVEN_USERNAME")
                password = System.getenv("MAVEN_SECRET")
            }
        }
    }

    publications {
        create<MavenPublication>("maven") {
            groupId = "dev.emortal.minestom"
            artifactId = "core"

            val commitHash = System.getenv("COMMIT_HASH_SHORT")
            val releaseVersion = System.getenv("RELEASE_VERSION")
            version = commitHash ?: releaseVersion ?: "local"

            from(components["java"])
        }
    }
}
