package com.ki960213.riverpodgraph

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.ki960213.riverpodgraph.actions.ShowProviderDependencyGraphAction
import com.ki960213.riverpodgraph.actions.ShowWidgetDependenciesAction
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.nio.file.Path

class IntelliJApiUsageTest : StringSpec({
    "actions declare background update thread" {
        ShowProviderDependencyGraphAction().actionUpdateThread shouldBe ActionUpdateThread.BGT
        ShowWidgetDependenciesAction().actionUpdateThread shouldBe ActionUpdateThread.BGT
    }

    "production code does not use deprecated ReadAction compute API" {
        val sourceRoot = Path.of("src", "main", "kotlin")
        val offenders = Files.walk(sourceRoot).use { paths ->
            paths
                .filter { path -> Files.isRegularFile(path) && path.toString().endsWith(".kt") }
                .filter { path -> Files.readString(path).contains("ReadAction.compute") }
                .map { path -> sourceRoot.relativize(path).toString() }
                .toList()
        }

        offenders.shouldBeEmpty()
    }

    "background progress is launched through coroutine progress API" {
        val sourceRoot = Path.of("src", "main", "kotlin")
        val forbiddenSnippets = listOf(
            "ProgressManager.getInstance().run",
            "Task.Backgroundable",
            "com.intellij.openapi.progress.ProgressIndicator",
        )
        val offenders = Files.walk(sourceRoot).use { paths ->
            paths
                .filter { path -> Files.isRegularFile(path) && path.toString().endsWith(".kt") }
                .flatMap { path ->
                    val source = Files.readString(path)
                    forbiddenSnippets
                        .filter { snippet -> source.contains(snippet) }
                        .map { snippet -> "${sourceRoot.relativize(path)} uses $snippet" }
                        .stream()
                }
                .toList()
        }

        offenders.shouldBeEmpty()
    }

    "background entry points do not depend on ProgressManager cancellation checks" {
        val backgroundEntryPoints = listOf(
            Path.of("src", "main", "kotlin", "com", "ki960213", "riverpodgraph", "actions", "ShowProviderDependencyGraphAction.kt"),
            Path.of("src", "main", "kotlin", "com", "ki960213", "riverpodgraph", "actions", "ShowWidgetDependenciesAction.kt"),
            Path.of("src", "main", "kotlin", "com", "ki960213", "riverpodgraph", "ui", "RiverpodToolWindowFactory.kt"),
        )
        val offenders = backgroundEntryPoints
            .filter { path -> Files.readString(path).contains("ProgressManager") }
            .map { path -> path.fileName.toString() }

        offenders.shouldBeEmpty()
    }
})
