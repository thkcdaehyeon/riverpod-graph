import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.models.ProductRelease.Channel
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask.FailureLevel

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.changelog")
    id("org.jetbrains.intellij.platform")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.runner.junit5)
    testRuntimeOnly(libs.junit.platform.launcher)
    testRuntimeOnly(libs.junit.vintage.engine)

    intellijPlatform {
        intellijIdea("2026.1.1")
        plugins(providers.gradleProperty("platformPlugins").map { value ->
            value.split(',').map(String::trim).filter(String::isNotEmpty)
        })
        compatiblePlugin("com.redhat.devtools.lsp4ij")
        testFramework(TestFrameworkType.Platform)
        pluginVerifier()
        javaCompiler()
    }
}

intellijPlatform {
    pluginVerification {
        failureLevel.set(
            listOf(
                FailureLevel.COMPATIBILITY_PROBLEMS,
                FailureLevel.INTERNAL_API_USAGES,
                FailureLevel.OVERRIDE_ONLY_API_USAGES,
                FailureLevel.MISSING_DEPENDENCIES,
            )
        )

        ides {
            select {
                sinceBuild.set("261")
                untilBuild.set("261.*")
                types.set(listOf(IntelliJPlatformType.IntellijIdea))
                channels.set(listOf(Channel.RELEASE))
            }
        }
    }
}

tasks {
    patchPluginXml {
        sinceBuild.set("261")
        untilBuild = provider { null }
    }

    test {
        useJUnitPlatform()
        systemProperty("riverpodgraph.testDataPath", "$projectDir/src/test/testData")
    }
}

configurations.testRuntimeClasspath {
    exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core")
    exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core-jvm")
    exclude(group = "net.java.dev.jna", module = "jna")
    exclude(group = "net.java.dev.jna", module = "jna-platform")
}
