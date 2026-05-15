package com.ki960213.riverpodgraph

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class BuildConfigurationTest {
    @Test
    fun `build targets latest IntelliJ IDEA release branch`() {
        val buildFile = Files.readString(Path.of("build.gradle.kts"))

        assertTrue(buildFile.contains("""intellijIdea("2026.1.1")"""))
        assertTrue(buildFile.contains("""sinceBuild.set("261")"""))
        assertTrue(buildFile.contains("""untilBuild.set("261.*")"""))
        assertTrue(buildFile.contains("untilBuild = provider { null }"))
        assertTrue(buildFile.contains("IntelliJPlatformType.IntellijIdea"))
        assertFalse(buildFile.contains("IntelliJPlatformType.IntellijIdeaCommunity"))
        assertFalse(buildFile.contains("2025.3"))
    }
}
