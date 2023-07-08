plugins {
    id("com.github.evestera.depsize") version "0.1.0"

    `java-library`
    `maven-publish`
}

group = "dev.emortal.minestom.core"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    mavenLocal()

    maven("https://packages.confluent.io/maven/")
    maven("https://repo.emortal.dev/snapshots")
    maven("https://repo.emortal.dev/releases")
    maven("https://jitpack.io")
}

dependencies {
    // Minestom
//    api("dev.hollowcube:minestom-ce:6f11e42d46")
    api("com.github.hollow-cube:minestom-ce:8a5c610b7b")
    api("net.kyori:adventure-text-minimessage:4.13.0")
    implementation("io.pyroscope:agent:0.11.5")

    // Logger
    implementation("ch.qos.logback:logback-classic:1.4.7")
    implementation("net.logstash.logback:logstash-logback-encoder:7.3")

    // APIs
    api("dev.emortal.api:module-system:0352626")
    api("dev.emortal.api:agones-sdk:1.0.7")
    api("dev.emortal.api:common-proto-sdk:da7a48c")
    api("dev.emortal.api:live-config-parser:6d27d33")
    api("dev.emortal.api:kurushimi-sdk:c5fdffc")

    api("io.kubernetes:client-java:18.0.0")

    api("io.micrometer:micrometer-registry-prometheus:1.11.1")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.3")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.9.3")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
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