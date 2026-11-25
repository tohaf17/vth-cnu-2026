plugins {
    id("io.micronaut.application") apply false
    id("io.micronaut.test-resources") apply false
    id("com.google.cloud.tools.jib") apply false
}

allprojects {
    group = "ua.cdu.edu.fotius.fspm.valorant"
    version = "1.0.0"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "io.micronaut.application")
    apply(plugin = "io.micronaut.test-resources")
    apply(plugin = "com.google.cloud.tools.jib")

    dependencies {
        "implementation"("io.micronaut:micronaut-http-server-netty")
        "runtimeOnly"("org.yaml:snakeyaml")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
        "testResourcesService"("io.micronaut.testresources:micronaut-test-resources-jdbc-postgresql")
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        maxHeapSize = "384m"
    }

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(25))
        }
    }

    extensions.configure<JavaApplication> {
        applicationDefaultJvmArgs = listOf(
            "-Xms64m",
            "-Xmx256m",
            "-XX:MaxMetaspaceSize=192m",
            "-XX:+UseSerialGC"
        )
    }

    extensions.configure<com.google.cloud.tools.jib.gradle.JibExtension> {
        from {
            image = "eclipse-temurin:25-jre-alpine"
        }
        to {
            val gcpProject = System.getenv("GCP_PROJECT_ID") ?: "YOUR_PROJECT_ID"
            val gcpRegion = System.getenv("GCP_REGION") ?: "us-central1"
            image = "$gcpRegion-docker.pkg.dev/$gcpProject/valorant-repo/${project.name}:latest"
        }
        container {
            ports = listOf("8080")
            jvmFlags = listOf(
                "-Xms64m",
                "-Xmx256m",
                "-XX:+UseSerialGC"
            )
        }
    }
}
