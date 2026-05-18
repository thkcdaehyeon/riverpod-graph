package com.ki960213.riverpodgraph

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Path

class BuildConfigurationTest : StringSpec({
    "최신 IntelliJ IDEA 릴리스 브랜치를 대상으로 빌드한다" {
        val buildFile = Path.of("build.gradle.kts").readUtf8Text()

        (buildFile.contains("""intellijIdea("2026.1.1")""")) shouldBe true
        (buildFile.contains("""sinceBuild.set("261")""")) shouldBe true
        (buildFile.contains("""untilBuild.set("261.*")""")) shouldBe true
        (buildFile.contains("untilBuild = provider { null }")) shouldBe true
        (buildFile.contains("IntelliJPlatformType.IntellijIdea")) shouldBe true
        (buildFile.contains("IntelliJPlatformType.IntellijIdeaCommunity")) shouldBe false
        (buildFile.contains("2025.3")) shouldBe false
    }

    "Dart plugin is installed into the test sandbox, not only marked compatible" {
        val buildFile = Path.of("build.gradle.kts").readUtf8Text()

        (buildFile.contains("plugins(providers.gradleProperty(\"platformPlugins\")")) shouldBe true
        (buildFile.contains("compatiblePlugins(providers.gradleProperty(\"platformPlugins\")")) shouldBe false
        (buildFile.contains("compatiblePlugin(\"com.redhat.devtools.lsp4ij\")")) shouldBe true
    }
})
