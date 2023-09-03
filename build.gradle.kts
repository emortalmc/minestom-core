plugins {
    `java-library`
    `maven-publish`

    jacoco
}

group = "dev.emortal.minestom"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    mavenLocal()

    maven("https://repo.emortal.dev/snapshots")
    maven("https://repo.emortal.dev/releases")

    maven("https://jitpack.io")
    maven("https://packages.confluent.io/maven/")
}

dependencies {
    // Minestom
    api("dev.hollowcube:minestom-ce:0e8151150a")
    api("net.kyori:adventure-text-minimessage:4.14.0")
    implementation("io.pyroscope:agent:0.11.5")

    // Logger
    implementation("ch.qos.logback:logback-classic:1.4.11")

    // APIs
    api("dev.emortal.api:module-system:f3e496c")
    api("dev.emortal.api:agones-sdk:1.0.7")
    api("dev.emortal.api:common-proto-sdk:4f00807")
    api("dev.emortal.api:live-config-parser:7c88aa1")

    api("io.kubernetes:client-java:18.0.1")

    api("io.micrometer:micrometer-registry-prometheus:1.11.3")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.0")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(20))
    }
}

tasks {
    compileJava {
        options.compilerArgs.addAll(listOf(
                "--release", "20",
                "--enable-preview"
        ))
    }
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