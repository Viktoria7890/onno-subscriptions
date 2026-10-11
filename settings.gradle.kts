plugins {
    // Если на машине нет JDK 21, Gradle скачает его сам (toolchain auto-provisioning).
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

rootProject.name = "onno-subscriptions"
