package com.ki960213.riverpodgraph

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.ki960213.riverpodgraph.actions.ShowProviderDependencyGraphAction
import com.ki960213.riverpodgraph.actions.ShowWidgetDependenciesAction
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import java.nio.file.Path

class IntelliJApiUsageTest : StringSpec({
    "actions declare background update thread" {
        ShowProviderDependencyGraphAction().actionUpdateThread shouldBe ActionUpdateThread.BGT
        ShowWidgetDependenciesAction().actionUpdateThread shouldBe ActionUpdateThread.BGT
    }

    "production code does not use deprecated ReadAction compute API" {
        val sourceRoot = Path.of("src", "main", "kotlin")
        val offenders = sourceRoot.toFile().walkTopDown()
            .filter { file -> file.isFile && file.extension == "kt" }
            .filter { file -> file.toPath().containsText("ReadAction.compute") }
            .map { file -> sourceRoot.relativize(file.toPath()).toString() }
            .toList()

        offenders.shouldBeEmpty()
    }

    "background progress is launched through coroutine progress API" {
        val sourceRoot = Path.of("src", "main", "kotlin")
        val forbiddenSnippets = listOf(
            "ProgressManager.getInstance().run",
            "Task.Backgroundable",
            "com.intellij.openapi.progress.ProgressIndicator",
        )
        val offenders = sourceRoot.toFile().walkTopDown()
            .filter { file -> file.isFile && file.extension == "kt" }
            .flatMap { file ->
                val path = file.toPath()
                val source = path.readUtf8Text()
                forbiddenSnippets
                    .filter { snippet -> source.contains(snippet) }
                    .map { snippet -> "${sourceRoot.relativize(path)} uses $snippet" }
            }
            .toList()

        offenders.shouldBeEmpty()
    }

    "background entry points do not depend on ProgressManager cancellation checks" {
        val backgroundEntryPoints = listOf(
            Path.of(
                "src",
                "main",
                "kotlin",
                "com",
                "ki960213",
                "riverpodgraph",
                "actions",
                "ShowProviderDependencyGraphAction.kt"
            ),
            Path.of(
                "src",
                "main",
                "kotlin",
                "com",
                "ki960213",
                "riverpodgraph",
                "actions",
                "ShowWidgetDependenciesAction.kt"
            ),
            Path.of("src", "main", "kotlin", "com", "ki960213", "riverpodgraph", "ui", "RiverpodToolWindowFactory.kt"),
        )
        val offenders = backgroundEntryPoints
            .filter { path -> path.containsText("ProgressManager") }
            .map { path -> path.fileName.toString() }

        offenders.shouldBeEmpty()
    }

    "platform adapters get Riverpod source facts through the active scope module" {
        val platformAdapters = listOf(
            Path.of(
                "src",
                "main",
                "kotlin",
                "com",
                "ki960213",
                "riverpodgraph",
                "actions",
                "ShowProviderDependencyGraphAction.kt"
            ),
            Path.of(
                "src",
                "main",
                "kotlin",
                "com",
                "ki960213",
                "riverpodgraph",
                "actions",
                "ShowWidgetDependenciesAction.kt"
            ),
            Path.of(
                "src",
                "main",
                "kotlin",
                "com",
                "ki960213",
                "riverpodgraph",
                "search",
                "RiverpodReferencesSearchExecutor.kt"
            ),
            Path.of("src", "main", "kotlin", "com", "ki960213", "riverpodgraph", "ui", "RiverpodToolWindowFactory.kt"),
        )
        val forbiddenSnippets = listOf(
            "FileBasedIndex.getInstance()",
            "FilenameIndex.getAllFilesByExt",
        )
        val offenders = platformAdapters.flatMap { path ->
            val source = path.readUtf8Text()
            forbiddenSnippets
                .filter { snippet -> source.contains(snippet) }
                .map { snippet -> "${path.fileName} uses $snippet" }
        }

        offenders.shouldBeEmpty()
    }

    "active scope does not leak provider index values into lookup adapters" {
        val lookupAdapters = listOf(
            Path.of(
                "src",
                "main",
                "kotlin",
                "com",
                "ki960213",
                "riverpodgraph",
                "resolution",
                "RiverpodProviderResolver.kt"
            ),
            Path.of(
                "src",
                "main",
                "kotlin",
                "com",
                "ki960213",
                "riverpodgraph",
                "search",
                "RiverpodReferencesSearchExecutor.kt"
            ),
        )
        val forbiddenSnippets = listOf(
            "RiverpodProviderIndexValue",
            "providerDeclarationsFromIndexValues",
            "providerIndexValues(",
        )
        val offenders = lookupAdapters.flatMap { path ->
            val source = path.readUtf8Text()
            forbiddenSnippets
                .filter { snippet -> source.contains(snippet) }
                .map { snippet -> "${path.fileName} uses $snippet" }
        }

        offenders.shouldBeEmpty()
    }

    "provider usage scope does not expose derived map details" {
        val source = Path.of(
            "src",
            "main",
            "kotlin",
            "com",
            "ki960213",
            "riverpodgraph",
            "analysis",
            "ProviderUsageScanner.kt",
        ).readUtf8Text()
        val offenders = listOf(
            "internal fun directCallSourceNamesByProvider",
            "internal fun declarationOffsetsByProvider",
        ).filter { snippet -> source.contains(snippet) }

        offenders.shouldBeEmpty()
    }
})
