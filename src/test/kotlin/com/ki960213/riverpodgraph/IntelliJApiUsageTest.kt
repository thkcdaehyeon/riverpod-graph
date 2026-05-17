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
})
