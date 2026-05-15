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

    intellijPlatform {
        intellijIdea("2026.1.1")
        compatiblePlugins(providers.gradleProperty("platformPlugins").map { value ->
            value.split(',').map(String::trim).filter(String::isNotEmpty)
        })
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
        useJUnit()
    }
}
