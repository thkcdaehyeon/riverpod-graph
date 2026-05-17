# Riverpod Graph v1 Implementation Plan

> **Agent workers:** REQUIRED SUB-SKILL: use superpowers:subagent-driven-development (recommended) / superpowers:executing-plans. Implement task-by-task. Track checkboxes (`- [ ]`).

**Goal:** Build v1 IntelliJ/Android Studio plugin that redirects Riverpod generator navigation to source declarations + shows provider/widget dependency views.

**Architecture:** Keep plugin source-based + opinionated: parse `@riverpod` source files into small index, resolve generated symbols from that index, + layer navigation/search/actions/UI on shared model. Use IntelliJ Platform EPs + Swing only; no Dart PSI synthesis, no generated files.

**Tech Stack:** Kotlin JVM, IntelliJ Platform Gradle Plugin 2.x, IntelliJ Platform OpenAPI, Dart plugin dependency, Swing UI, JUnit 4 platform tests.

---

## Scope Check

Single v1 plan: features share Riverpod declaration index, usage scanner, dependency model. Exclude Dart Analysis Server overlays, ProviderScope override detection, cross-package widget traversal, settings UI, flat provider view, + usage counts, matching `CONTEXT.md` + ADR decisions.

## File Structure

- Build/meta: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `src/main/resources/META-INF/plugin.xml`, `LICENSE`.
- Core: `RiverpodModels.kt`, `RiverpodNaming.kt`, `RiverpodAnnotationParser.kt`, `PubspecDependencyParser.kt`, `RiverpodActivationService.kt`.
- Index/nav/search: `RiverpodProviderIndex.kt`, `RiverpodProviderIndexValue.kt`, `RiverpodProviderResolver.kt`, `RiverpodGotoDeclarationHandler.kt`, `ProviderUsageScanner.kt`, `RiverpodReference.kt`, `RiverpodReferencesSearchExecutor.kt`.
- Graph/UI/actions: `ProviderDependencyAnalyzer.kt`, `RefExtensionScanner.kt`, `WidgetDependencyAnalyzer.kt`, `RiverpodToolWindowFactory.kt`, `ProvidersPanel.kt`, `DependencyGraphPanel.kt`, `WidgetDependencyDialog.kt`, `ShowProviderDependencyGraphAction.kt`, `ShowWidgetDependenciesAction.kt`, `RiverpodProviderLineMarkerProvider.kt`.
- Tests/fixtures: `src/test/kotlin/com/ki960213/riverpodgraph/...`, `src/test/testData/riverpod/...`.
- Docs: `README.md`, `CHANGELOG.md`.

## Task 1: Build Baseline And Plugin Manifest

**Files:**
- Mod: `settings.gradle.kts`
- Mod: `build.gradle.kts`
- Mod: `gradle.properties`
- Mod: `src/main/resources/META-INF/plugin.xml`
- Add: `LICENSE`

- [ ] **1: Write manifest registration smoke test**

Add `src/test/kotlin/com/ki960213/riverpodgraph/PluginManifestTest.kt`:

```kotlin
package com.ki960213.riverpodgraph

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Path

class PluginManifestTest {
    @Test
    fun `plugin manifest depends on Dart and not Compose`() {
        val xml = Path.of("src/main/resources/META-INF/plugin.xml").toFile().readText()

        assertTrue(xml.contains("<depends>Dart</depends>"))
        assertTrue(!xml.contains("com.intellij.modules.compose"))
        assertTrue(xml.contains("Riverpod Graph"))
        assertTrue(xml.contains("com.ki960213.riverpodgraph.navigation.RiverpodGotoDeclarationHandler"))
        assertTrue(xml.contains("com.ki960213.riverpodgraph.search.RiverpodReferencesSearchExecutor"))
    }
}
```

- [ ] **2: Run smoke test; verify fail**

Run `./gradlew test --tests com.ki960213.riverpodgraph.PluginManifestTest`

Expect: FAIL: `plugin.xml` still contains `com.intellij.modules.compose`, `YourCompany`, + no Riverpod EP registrations.

- [ ] **3: Update Gradle + plugin metadata**

Replace `settings.gradle.kts` with:

```kotlin
import org.jetbrains.intellij.platform.gradle.extensions.intellijPlatform

rootProject.name = "riverpod-graph"

pluginManagement {
    plugins {
        id("org.jetbrains.kotlin.jvm") version "2.2.20"
        id("org.jetbrains.changelog") version "2.5.0"
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("org.jetbrains.intellij.platform.settings") version "2.16.0"
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositories {
        mavenCentral()

        intellijPlatform {
            defaultRepositories()
        }
    }
}
```

Replace `build.gradle.kts` with:

```kotlin
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

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
        intellijIdeaCommunity("2025.3.4.1")
        plugins(providers.gradleProperty("platformPlugins").map { value ->
            value.split(',').map(String::trim).filter(String::isNotEmpty)
        })
        testFramework(TestFrameworkType.Platform)
        pluginVerifier()
        instrumentationTools()
    }
}

tasks {
    patchPluginXml {
        sinceBuild.set("253")
        untilBuild.set("253.*")
    }

    test {
        useJUnit()
    }
}
```

Append these properties to `gradle.properties`:

```properties
pluginName=Riverpod Graph
pluginRepositoryUrl=https://github.com/ki960213/riverpod-graph
platformPlugins=Dart
```

Replace `src/main/resources/META-INF/plugin.xml` with:

```xml
<idea-plugin>
    <id>com.ki960213.riverpod-graph</id>
    <name>Riverpod Graph</name>
    <vendor url="https://github.com/ki960213">ki960213</vendor>

    <description><![CDATA[
        Riverpod Graph redirects generated Riverpod symbols to source declarations and visualizes provider and widget dependencies for Riverpod generator projects.
    ]]></description>

    <depends>Dart</depends>

    <extensions defaultExtensionNs="com.intellij">
        <fileBasedIndex implementation="com.ki960213.riverpodgraph.index.RiverpodProviderIndex"/>
        <gotoDeclarationHandler implementation="com.ki960213.riverpodgraph.navigation.RiverpodGotoDeclarationHandler"/>
        <referencesSearch implementation="com.ki960213.riverpodgraph.search.RiverpodReferencesSearchExecutor"/>
        <toolWindow id="Riverpod Graph"
                    anchor="right"
                    icon="/META-INF/pluginIcon.svg"
                    factoryClass="com.ki960213.riverpodgraph.ui.RiverpodToolWindowFactory"/>
        <codeInsight.lineMarkerProvider language="Dart"
                                        implementationClass="com.ki960213.riverpodgraph.gutter.RiverpodProviderLineMarkerProvider"/>
    </extensions>

    <actions>
        <action id="RiverpodGraph.ShowProviderDependencyGraph"
                class="com.ki960213.riverpodgraph.actions.ShowProviderDependencyGraphAction"
                text="Show Riverpod Dependency Graph"
                popup="true">
            <add-to-group group-id="EditorPopupMenu" anchor="last"/>
            <add-to-group group-id="ProjectViewPopupMenu" anchor="last"/>
        </action>
        <action id="RiverpodGraph.ShowWidgetDependencies"
                class="com.ki960213.riverpodgraph.actions.ShowWidgetDependenciesAction"
                text="Show Riverpod Widget Dependencies"
                popup="true">
            <add-to-group group-id="EditorPopupMenu" anchor="last"/>
        </action>
    </actions>
</idea-plugin>
```

Add `LICENSE` from full Apache License 2.0 text, with copyright owner:

```text
Copyright 2026 ki960213
```

- [ ] **4: Create temporary no-op extension classes so plugin descriptor resolves**

Create temp no-op classes; later tasks replace internals:

```kotlin
// src/main/kotlin/com/ki960213/riverpodgraph/navigation/RiverpodGotoDeclarationHandler.kt
package com.ki960213.riverpodgraph.navigation

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement

class RiverpodGotoDeclarationHandler : GotoDeclarationHandler {
    override fun getGotoDeclarationTargets(sourceElement: PsiElement?, offset: Int, editor: Editor): Array<PsiElement>? = null
}
```

```kotlin
// src/main/kotlin/com/ki960213/riverpodgraph/search/RiverpodReferencesSearchExecutor.kt
package com.ki960213.riverpodgraph.search

import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.psi.PsiReference
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.Processor

class RiverpodReferencesSearchExecutor : QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>(true) {
    override fun processQuery(queryParameters: ReferencesSearch.SearchParameters, consumer: Processor<in PsiReference>) = Unit
}
```

```kotlin
// src/main/kotlin/com/ki960213/riverpodgraph/index/RiverpodProviderIndex.kt
package com.ki960213.riverpodgraph.index

import com.intellij.util.indexing.FileBasedIndexExtension
import com.intellij.util.indexing.ID
import com.intellij.util.indexing.DataIndexer
import com.intellij.util.indexing.FileContent
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.KeyDescriptor

class RiverpodProviderIndex : FileBasedIndexExtension<String, String>() {
    override fun getName(): ID<String, String> = NAME
    override fun getIndexer(): DataIndexer<String, String, FileContent> = DataIndexer { emptyMap() }
    override fun getKeyDescriptor(): KeyDescriptor<String> = EnumeratorStringDescriptor.INSTANCE
    override fun getValueExternalizer(): DataExternalizer<String> = EnumeratorStringDescriptor.INSTANCE
    override fun getVersion(): Int = 1
    override fun dependsOnFileContent(): Boolean = true

    companion object {
        val NAME: ID<String, String> = ID.create("com.ki960213.riverpodgraph.provider.index")
    }
}
```

```kotlin
// src/main/kotlin/com/ki960213/riverpodgraph/ui/RiverpodToolWindowFactory.kt
package com.ki960213.riverpodgraph.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory

class RiverpodToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) = Unit
}
```

```kotlin
// src/main/kotlin/com/ki960213/riverpodgraph/actions/ShowProviderDependencyGraphAction.kt
package com.ki960213.riverpodgraph.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class ShowProviderDependencyGraphAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) = Unit
}
```

```kotlin
// src/main/kotlin/com/ki960213/riverpodgraph/actions/ShowWidgetDependenciesAction.kt
package com.ki960213.riverpodgraph.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class ShowWidgetDependenciesAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) = Unit
}
```

```kotlin
// src/main/kotlin/com/ki960213/riverpodgraph/gutter/RiverpodProviderLineMarkerProvider.kt
package com.ki960213.riverpodgraph.gutter

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.psi.PsiElement

class RiverpodProviderLineMarkerProvider : LineMarkerProvider {
    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? = null
}
```

- [ ] **5: Run tests + plugin verify**

Run `./gradlew test verifyPlugin`

Expect: PASS: `PluginManifestTest`; `verifyPlugin` completes no Compose dependency errors.

- [ ] **6: Commit**

```bash
git add settings.gradle.kts build.gradle.kts gradle.properties src/main/resources/META-INF/plugin.xml LICENSE src/main/kotlin src/test/kotlin/com/ki960213/riverpodgraph/PluginManifestTest.kt
git commit -m "chore: configure plugin baseline"
```

## Task 2: Core Riverpod Model And Naming

**Files:**
- Add: `src/main/kotlin/com/ki960213/riverpodgraph/model/RiverpodModels.kt`
- Add: `src/main/kotlin/com/ki960213/riverpodgraph/model/RiverpodNaming.kt`
- Test: `src/test/kotlin/com/ki960213/riverpodgraph/model/RiverpodNamingTest.kt`

- [ ] **1: Write failing naming tests**

```kotlin
package com.ki960213.riverpodgraph.model

import org.junit.Assert.assertEquals
import org.junit.Test

class RiverpodNamingTest {
    @Test
    fun `function provider keeps original lower camel name`() {
        assertEquals("userProvider", RiverpodNaming.providerForFunction("user"))
        assertEquals("_privateUserProvider", RiverpodNaming.providerForFunction("_privateUser"))
    }

    @Test
    fun `class provider lowercases first visible class character`() {
        assertEquals("userControllerProvider", RiverpodNaming.providerForClass("UserController"))
        assertEquals("_privateControllerProvider", RiverpodNaming.providerForClass("_PrivateController"))
        assertEquals("urlCacheProvider", RiverpodNaming.providerForClass("URLCache"))
    }

    @Test
    fun `generated superclass keeps class name`() {
        assertEquals("_${'$'}" + "UserController", RiverpodNaming.generatedSuperclassForClass("UserController"))
    }
}
```

- [ ] **2: Run naming tests; verify fail**

Run `./gradlew test --tests com.ki960213.riverpodgraph.model.RiverpodNamingTest`

Expect: FAIL: unresolved `RiverpodNaming`.

- [ ] **3: Implement model + naming**

Add `RiverpodModels.kt`:

```kotlin
package com.ki960213.riverpodgraph.model

enum class RiverpodProviderKind {
    FUNCTION,
    NOTIFIER_CLASS
}

data class RiverpodProviderDeclaration(
    val kind: RiverpodProviderKind,
    val sourceName: String,
    val providerName: String,
    val generatedSuperclassName: String?,
    val returnType: String,
    val familySignature: String,
    val keepAlive: Boolean,
    val isPrivate: Boolean,
    val filePath: String,
    val textOffset: Int,
    val line: Int
)

enum class RiverpodUsageKind {
    WATCH,
    READ,
    LISTEN,
    INVALIDATE,
    REFRESH,
    OVERRIDE,
    NOTIFIER,
    FUTURE,
    SELECT,
    DIRECT_CALL,
    EXTENSION_MEMBER
}

data class RiverpodProviderUsage(
    val providerName: String,
    val kind: RiverpodUsageKind,
    val filePath: String,
    val textOffset: Int,
    val line: Int,
    val marker: RiverpodMarker? = null
)

enum class RiverpodMarker(val label: String) {
    CONDITIONAL("conditional"),
    LOOP("loop"),
    CALLBACK("callback"),
    REF_EXTENSION("ref extension"),
    CYCLE("cycle")
}

data class RiverpodDependencyEdge(
    val fromProvider: String,
    val toProvider: String,
    val usageKind: RiverpodUsageKind,
    val marker: RiverpodMarker? = null
)
```

Add `RiverpodNaming.kt`:

```kotlin
package com.ki960213.riverpodgraph.model

object RiverpodNaming {
    fun providerForFunction(functionName: String): String = functionName + "Provider"

    fun providerForClass(className: String): String {
        val prefix = className.takeWhile { it == '_' }
        val visible = className.drop(prefix.length)
        if (visible.isEmpty()) return className + "Provider"

        val normalized = if (visible.length >= 2 && visible[0].isUpperCase() && visible[1].isUpperCase()) {
            visible[0].lowercaseChar() + visible.drop(1)
        } else {
            visible.replaceFirstChar { it.lowercaseChar() }
        }

        return prefix + normalized + "Provider"
    }

    fun generatedSuperclassForClass(className: String): String = "_${'$'}" + className
}
```

- [ ] **4: Run naming tests**

Run `./gradlew test --tests com.ki960213.riverpodgraph.model.RiverpodNamingTest`

Expect: PASS.

- [ ] **5: Commit**

```bash
git add src/main/kotlin/com/ki960213/riverpodgraph/model src/test/kotlin/com/ki960213/riverpodgraph/model/RiverpodNamingTest.kt
git commit -m "feat: add riverpod provider model"
```

## Task 3: Parse Source Riverpod Declarations

**Files:**
- Add: `src/main/kotlin/com/ki960213/riverpodgraph/parser/RiverpodAnnotationParser.kt`
- Test: `src/test/kotlin/com/ki960213/riverpodgraph/parser/RiverpodAnnotationParserTest.kt`

- [ ] **1: Write failing parser tests**

```kotlin
package com.ki960213.riverpodgraph.parser

import com.ki960213.riverpodgraph.model.RiverpodProviderKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RiverpodAnnotationParserTest {
    @Test
    fun `parses functional provider`() {
        val declarations = RiverpodAnnotationParser.parse(
            filePath = "lib/user.dart",
            content = """
                import 'package:riverpod_annotation/riverpod_annotation.dart';

                part 'user.g.dart';

                @riverpod
                Future<User> user(Ref ref, String id) async => User(id);
            """.trimIndent()
        )

        assertEquals(1, declarations.size)
        assertEquals(RiverpodProviderKind.FUNCTION, declarations.single().kind)
        assertEquals("user", declarations.single().sourceName)
        assertEquals("userProvider", declarations.single().providerName)
        assertEquals("Future<User>", declarations.single().returnType)
        assertEquals("Ref ref, String id", declarations.single().familySignature)
    }

    @Test
    fun `parses notifier class with keep alive`() {
        val declarations = RiverpodAnnotationParser.parse(
            filePath = "lib/session.dart",
            content = """
                @Riverpod(keepAlive: true)
                class SessionController extends _$SessionController {
                  @override
                  Session build(String id) => Session(id);
                }
            """.trimIndent()
        )

        assertEquals(1, declarations.size)
        assertEquals(RiverpodProviderKind.NOTIFIER_CLASS, declarations.single().kind)
        assertEquals("SessionController", declarations.single().sourceName)
        assertEquals("sessionControllerProvider", declarations.single().providerName)
        assertEquals("_${'$'}" + "SessionController", declarations.single().generatedSuperclassName)
        assertEquals("Session", declarations.single().returnType)
        assertEquals("String id", declarations.single().familySignature)
        assertTrue(declarations.single().keepAlive)
    }
}
```

- [ ] **2: Run parser tests; verify fail**

Run `./gradlew test --tests com.ki960213.riverpodgraph.parser.RiverpodAnnotationParserTest`

Expect: FAIL: unresolved `RiverpodAnnotationParser`.

- [ ] **3: Implement parser**

```kotlin
package com.ki960213.riverpodgraph.parser

import com.ki960213.riverpodgraph.model.RiverpodNaming
import com.ki960213.riverpodgraph.model.RiverpodProviderDeclaration
import com.ki960213.riverpodgraph.model.RiverpodProviderKind

object RiverpodAnnotationParser {
    private val annotationRegex = Regex("@(?:r|R)iverpod(?:\\s*\\(([^)]*)\\))?")
    private val classRegex = Regex("""class\s+([A-Za-z_${'$'}][\w${'$'}]*)\s+extends\s+_${'$'}([A-Za-z_${'$'}][\w${'$'}]*)""")
    private val functionRegex = Regex("""([A-Za-z_${'$'}][\w${'$'}<>, ?.]*)\s+([A-Za-z_${'$'}][\w${'$'}]*)\s*\(([^)]*)\)""")
    private val buildRegex = Regex("""([A-Za-z_${'$'}][\w${'$'}<>, ?.]*)\s+build\s*\(([^)]*)\)""")

    fun parse(filePath: String, content: String): List<RiverpodProviderDeclaration> {
        if (filePath.endsWith(".g.dart")) return emptyList()

        return annotationRegex.findAll(content).mapNotNull { annotation ->
            val keepAlive = annotation.groups[1]?.value?.contains("keepAlive: true") == true
            val declarationStart = skipWhitespaceAndComments(content, annotation.range.last + 1)
            val tail = content.substring(declarationStart)

            parseClass(filePath, content, tail, declarationStart, keepAlive)
                ?: parseFunction(filePath, content, tail, declarationStart, keepAlive)
        }.toList()
    }

    private fun parseClass(
        filePath: String,
        content: String,
        tail: String,
        declarationStart: Int,
        keepAlive: Boolean
    ): RiverpodProviderDeclaration? {
        val match = classRegex.find(tail) ?: return null
        if (match.range.first > 80) return null

        val className = match.groupValues[1]
        val body = tail.substring(match.range.last + 1)
        val build = buildRegex.find(body)
        val returnType = build?.groupValues?.get(1)?.trim().orEmpty()
        val signature = build?.groupValues?.get(2)?.trim().orEmpty()
        val offset = declarationStart + match.range.first + match.value.indexOf(className)

        return RiverpodProviderDeclaration(
            kind = RiverpodProviderKind.NOTIFIER_CLASS,
            sourceName = className,
            providerName = RiverpodNaming.providerForClass(className),
            generatedSuperclassName = RiverpodNaming.generatedSuperclassForClass(className),
            returnType = returnType,
            familySignature = signature,
            keepAlive = keepAlive,
            isPrivate = className.startsWith("_"),
            filePath = filePath,
            textOffset = offset,
            line = lineNumber(content, offset)
        )
    }

    private fun parseFunction(
        filePath: String,
        content: String,
        tail: String,
        declarationStart: Int,
        keepAlive: Boolean
    ): RiverpodProviderDeclaration? {
        val header = tail.lineSequence().firstOrNull { it.isNotBlank() } ?: return null
        val match = functionRegex.find(header) ?: return null
        val returnType = match.groupValues[1].trim()
        val functionName = match.groupValues[2].trim()
        val signature = match.groupValues[3].trim()
        val offset = declarationStart + tail.indexOf(header) + match.range.first + match.value.indexOf(functionName)

        return RiverpodProviderDeclaration(
            kind = RiverpodProviderKind.FUNCTION,
            sourceName = functionName,
            providerName = RiverpodNaming.providerForFunction(functionName),
            generatedSuperclassName = null,
            returnType = returnType,
            familySignature = signature,
            keepAlive = keepAlive,
            isPrivate = functionName.startsWith("_"),
            filePath = filePath,
            textOffset = offset,
            line = lineNumber(content, offset)
        )
    }

    private fun skipWhitespaceAndComments(content: String, start: Int): Int {
        var index = start
        while (index < content.length) {
            when {
                content[index].isWhitespace() -> index++
                content.startsWith("//", index) -> index = content.indexOf('\n', index).let { if (it == -1) content.length else it + 1 }
                content.startsWith("/*", index) -> index = content.indexOf("*/", index + 2).let { if (it == -1) content.length else it + 2 }
                else -> return index
            }
        }
        return index
    }

    private fun lineNumber(content: String, offset: Int): Int = content.substring(0, offset).count { it == '\n' } + 1
}
```

- [ ] **4: Run parser tests**

Run `./gradlew test --tests com.ki960213.riverpodgraph.parser.RiverpodAnnotationParserTest`

Expect: PASS.

- [ ] **5: Commit**

```bash
git add src/main/kotlin/com/ki960213/riverpodgraph/parser src/test/kotlin/com/ki960213/riverpodgraph/parser
git commit -m "feat: parse riverpod declarations"
```

## Task 4: Module Activation From pubspec.yaml

**Files:**
- Add: `src/main/kotlin/com/ki960213/riverpodgraph/activation/PubspecDependencyParser.kt`
- Add: `src/main/kotlin/com/ki960213/riverpodgraph/activation/RiverpodActivationService.kt`
- Test: `src/test/kotlin/com/ki960213/riverpodgraph/activation/PubspecDependencyParserTest.kt`

- [ ] **1: Write failing activation parser tests**

```kotlin
package com.ki960213.riverpodgraph.activation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PubspecDependencyParserTest {
    @Test
    fun `detects riverpod annotation dependency`() {
        val pubspec = """
            name: sample_app
            dependencies:
              flutter:
                sdk: flutter
              riverpod_annotation: ^3.0.0
        """.trimIndent()

        assertTrue(PubspecDependencyParser.hasRiverpodAnnotation(pubspec))
    }

    @Test
    fun `does not activate for runtime-only riverpod`() {
        val pubspec = """
            name: sample_app
            dependencies:
              flutter_riverpod: ^3.0.0
        """.trimIndent()

        assertFalse(PubspecDependencyParser.hasRiverpodAnnotation(pubspec))
    }
}
```

- [ ] **2: Run activation parser tests; verify fail**

Run `./gradlew test --tests com.ki960213.riverpodgraph.activation.PubspecDependencyParserTest`

Expect: FAIL: unresolved `PubspecDependencyParser`.

- [ ] **3: Implement pubspec parser + activation service**

Add `PubspecDependencyParser.kt`:

```kotlin
package com.ki960213.riverpodgraph.activation

object PubspecDependencyParser {
    fun hasRiverpodAnnotation(content: String): Boolean {
        var inDependencyBlock = false
        var dependencyIndent = -1

        for (line in content.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue

            val indent = line.indexOfFirst { !it.isWhitespace() }.let { if (it == -1) 0 else it }
            if (trimmed == "dependencies:" || trimmed == "dev_dependencies:") {
                inDependencyBlock = true
                dependencyIndent = indent
                continue
            }

            if (inDependencyBlock && indent <= dependencyIndent && trimmed.endsWith(":")) {
                inDependencyBlock = false
            }

            if (inDependencyBlock && trimmed.startsWith("riverpod_annotation:")) {
                return true
            }
        }

        return false
    }
}
```

Add `RiverpodActivationService.kt`:

```kotlin
package com.ki960213.riverpodgraph.activation

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VfsUtil

@Service(Service.Level.PROJECT)
class RiverpodActivationService(private val project: Project) {
    fun isProjectActive(): Boolean = ModuleManager.getInstance(project).modules.any(::isModuleActive)

    fun isModuleActive(module: Module): Boolean {
        val roots = ModuleRootManager.getInstance(module).contentRoots
        return roots.any { root ->
            val pubspec = root.findChild("pubspec.yaml") ?: return@any false
            val text = VfsUtil.loadText(pubspec)
            PubspecDependencyParser.hasRiverpodAnnotation(text)
        }
    }

    companion object {
        fun getInstance(project: Project): RiverpodActivationService = project.service()
    }
}
```

- [ ] **4: Run activation tests**

Run `./gradlew test --tests com.ki960213.riverpodgraph.activation.PubspecDependencyParserTest`

Expect: PASS.

- [ ] **5: Commit**

```bash
git add src/main/kotlin/com/ki960213/riverpodgraph/activation src/test/kotlin/com/ki960213/riverpodgraph/activation
git commit -m "feat: activate for riverpod annotation modules"
```

## Task 5: Provider Index And Resolver

**Files:**
- Mod: `src/main/kotlin/com/ki960213/riverpodgraph/index/RiverpodProviderIndex.kt`
- Add: `src/main/kotlin/com/ki960213/riverpodgraph/index/RiverpodProviderIndexValue.kt`
- Add: `src/main/kotlin/com/ki960213/riverpodgraph/resolution/RiverpodProviderResolver.kt`
- Test: `src/test/kotlin/com/ki960213/riverpodgraph/index/RiverpodProviderIndexValueTest.kt`

- [ ] **1: Write failing serialization tests**

```kotlin
package com.ki960213.riverpodgraph.index

import com.ki960213.riverpodgraph.model.RiverpodProviderKind
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

class RiverpodProviderIndexValueTest {
    @Test
    fun `round trips provider declaration value`() {
        val value = RiverpodProviderIndexValue(
            kind = RiverpodProviderKind.NOTIFIER_CLASS,
            sourceName = "UserController",
            providerName = "userControllerProvider",
            generatedSuperclassName = "_${'$'}" + "UserController",
            returnType = "User",
            familySignature = "String id",
            keepAlive = true,
            isPrivate = false,
            filePath = "lib/user.dart",
            textOffset = 42,
            line = 7
        )

        val bytes = ByteArrayOutputStream()
        RiverpodProviderIndexValue.Externalizer.save(DataOutputStream(bytes), value)
        val restored = RiverpodProviderIndexValue.Externalizer.read(DataInputStream(ByteArrayInputStream(bytes.toByteArray())))

        assertEquals(value, restored)
    }
}
```

- [ ] **2: Run serialization tests; verify fail**

Run `./gradlew test --tests com.ki960213.riverpodgraph.index.RiverpodProviderIndexValueTest`

Expect: FAIL: unresolved `RiverpodProviderIndexValue`.

- [ ] **3: Implement index value, indexer, + resolver**

Add `RiverpodProviderIndexValue.kt`:

```kotlin
package com.ki960213.riverpodgraph.index

import com.ki960213.riverpodgraph.model.RiverpodProviderDeclaration
import com.ki960213.riverpodgraph.model.RiverpodProviderKind
import com.intellij.util.io.DataExternalizer
import java.io.DataInput
import java.io.DataOutput

data class RiverpodProviderIndexValue(
    val kind: RiverpodProviderKind,
    val sourceName: String,
    val providerName: String,
    val generatedSuperclassName: String?,
    val returnType: String,
    val familySignature: String,
    val keepAlive: Boolean,
    val isPrivate: Boolean,
    val filePath: String,
    val textOffset: Int,
    val line: Int
) {
    fun toDeclaration(): RiverpodProviderDeclaration = RiverpodProviderDeclaration(
        kind = kind,
        sourceName = sourceName,
        providerName = providerName,
        generatedSuperclassName = generatedSuperclassName,
        returnType = returnType,
        familySignature = familySignature,
        keepAlive = keepAlive,
        isPrivate = isPrivate,
        filePath = filePath,
        textOffset = textOffset,
        line = line
    )

    object Externalizer : DataExternalizer<RiverpodProviderIndexValue> {
        override fun save(out: DataOutput, value: RiverpodProviderIndexValue) {
            out.writeUTF(value.kind.name)
            out.writeUTF(value.sourceName)
            out.writeUTF(value.providerName)
            out.writeBoolean(value.generatedSuperclassName != null)
            if (value.generatedSuperclassName != null) out.writeUTF(value.generatedSuperclassName)
            out.writeUTF(value.returnType)
            out.writeUTF(value.familySignature)
            out.writeBoolean(value.keepAlive)
            out.writeBoolean(value.isPrivate)
            out.writeUTF(value.filePath)
            out.writeInt(value.textOffset)
            out.writeInt(value.line)
        }

        override fun read(input: DataInput): RiverpodProviderIndexValue {
            val kind = RiverpodProviderKind.valueOf(input.readUTF())
            val sourceName = input.readUTF()
            val providerName = input.readUTF()
            val generatedSuperclassName = if (input.readBoolean()) input.readUTF() else null
            val returnType = input.readUTF()
            val familySignature = input.readUTF()
            val keepAlive = input.readBoolean()
            val isPrivate = input.readBoolean()
            val filePath = input.readUTF()
            val textOffset = input.readInt()
            val line = input.readInt()

            return RiverpodProviderIndexValue(
                kind,
                sourceName,
                providerName,
                generatedSuperclassName,
                returnType,
                familySignature,
                keepAlive,
                isPrivate,
                filePath,
                textOffset,
                line
            )
        }
    }
}
```

Replace `RiverpodProviderIndex.kt`:

```kotlin
package com.ki960213.riverpodgraph.index

import com.ki960213.riverpodgraph.parser.RiverpodAnnotationParser
import com.intellij.util.indexing.DataIndexer
import com.intellij.util.indexing.DefaultFileTypeSpecificInputFilter
import com.intellij.util.indexing.FileBasedIndexExtension
import com.intellij.util.indexing.FileContent
import com.intellij.util.indexing.ID
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.KeyDescriptor

class RiverpodProviderIndex : FileBasedIndexExtension<String, RiverpodProviderIndexValue>() {
    override fun getName(): ID<String, RiverpodProviderIndexValue> = NAME

    override fun getIndexer(): DataIndexer<String, RiverpodProviderIndexValue, FileContent> = DataIndexer { input ->
        val path = input.file.path
        if (!path.endsWith(".dart") || path.endsWith(".g.dart")) return@DataIndexer emptyMap()

        RiverpodAnnotationParser.parse(path, input.contentAsText.toString())
            .flatMap { declaration ->
                val value = RiverpodProviderIndexValue(
                    kind = declaration.kind,
                    sourceName = declaration.sourceName,
                    providerName = declaration.providerName,
                    generatedSuperclassName = declaration.generatedSuperclassName,
                    returnType = declaration.returnType,
                    familySignature = declaration.familySignature,
                    keepAlive = declaration.keepAlive,
                    isPrivate = declaration.isPrivate,
                    filePath = declaration.filePath,
                    textOffset = declaration.textOffset,
                    line = declaration.line
                )
                listOfNotNull(
                    declaration.providerName to value,
                    declaration.generatedSuperclassName?.let { it to value },
                    declaration.sourceName to value
                )
            }.toMap()
    }

    override fun getInputFilter() = DefaultFileTypeSpecificInputFilter()
    override fun getKeyDescriptor(): KeyDescriptor<String> = EnumeratorStringDescriptor.INSTANCE
    override fun getValueExternalizer() = RiverpodProviderIndexValue.Externalizer
    override fun getVersion(): Int = 2
    override fun dependsOnFileContent(): Boolean = true

    companion object {
        val NAME: ID<String, RiverpodProviderIndexValue> = ID.create("com.ki960213.riverpodgraph.provider.index")
    }
}
```

Add `RiverpodProviderResolver.kt`:

```kotlin
package com.ki960213.riverpodgraph.resolution

import com.ki960213.riverpodgraph.index.RiverpodProviderIndex
import com.ki960213.riverpodgraph.model.RiverpodProviderDeclaration
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.FileBasedIndex

class RiverpodProviderResolver(private val project: Project) {
    fun findDeclaration(symbol: String, scope: GlobalSearchScope = GlobalSearchScope.projectScope(project)): RiverpodProviderDeclaration? {
        val values = FileBasedIndex.getInstance().getValues(RiverpodProviderIndex.NAME, symbol, scope)
        return values.firstOrNull()?.toDeclaration()
    }

    fun findSourceElement(symbol: String, scope: GlobalSearchScope = GlobalSearchScope.projectScope(project)): PsiElement? {
        val declaration = findDeclaration(symbol, scope) ?: return null
        val file = FileBasedIndex.getInstance().getContainingFiles(RiverpodProviderIndex.NAME, symbol, scope).firstOrNull() ?: return null
        val psiFile = PsiManager.getInstance(project).findFile(file) ?: return null
        return psiFile.findElementAt(declaration.textOffset)
    }
}
```

- [ ] **4: Run index tests**

Run `./gradlew test --tests com.ki960213.riverpodgraph.index.RiverpodProviderIndexValueTest`

Expect: PASS.

- [ ] **5: Commit**

```bash
git add src/main/kotlin/com/ki960213/riverpodgraph/index src/main/kotlin/com/ki960213/riverpodgraph/resolution src/test/kotlin/com/ki960213/riverpodgraph/index
git commit -m "feat: index riverpod declarations"
```

## Task 6: Go To Declaration Redirect

**Files:**
- Mod: `src/main/kotlin/com/ki960213/riverpodgraph/navigation/RiverpodGotoDeclarationHandler.kt`
- Test: `src/test/kotlin/com/ki960213/riverpodgraph/navigation/RiverpodGotoDeclarationHandlerTest.kt`

- [ ] **1: Write failing navigation tests**

```kotlin
package com.ki960213.riverpodgraph.navigation

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class RiverpodGotoDeclarationHandlerTest : BasePlatformTestCase() {
    fun testRedirectsProviderSymbolToSourceFunction() {
        myFixture.configureByText(
            "user.dart",
            """
            import 'package:riverpod_annotation/riverpod_annotation.dart';
            part 'user.g.dart';

            @riverpod
            String user(Ref ref) => 'Ada';

            final value = ref.watch(userProvider<caret>);
            """.trimIndent()
        )

        val handler = RiverpodGotoDeclarationHandler()
        val source = myFixture.file.findElementAt(myFixture.caretOffset - 1)
        val targets = handler.getGotoDeclarationTargets(source, myFixture.caretOffset, myFixture.editor)

        assertNotNull(targets)
        assertEquals("user", targets!!.single().text)
    }
}
```

- [ ] **2: Run navigation test; verify fail**

Run `./gradlew test --tests com.ki960213.riverpodgraph.navigation.RiverpodGotoDeclarationHandlerTest`

Expect: FAIL: handler returns `null`.

- [ ] **3: Implement navigation redirect**

```kotlin
package com.ki960213.riverpodgraph.navigation

import com.ki960213.riverpodgraph.resolution.RiverpodProviderResolver
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement

class RiverpodGotoDeclarationHandler : GotoDeclarationHandler {
    override fun getGotoDeclarationTargets(sourceElement: PsiElement?, offset: Int, editor: Editor): Array<PsiElement>? {
        val element = sourceElement ?: return null
        val project = element.project
        val symbol = symbolAt(element) ?: return null
        if (!symbol.endsWith("Provider") && !symbol.startsWith("_${'$'}")) return null

        val target = RiverpodProviderResolver(project).findSourceElement(symbol) ?: return null
        return arrayOf(target)
    }

    private fun symbolAt(element: PsiElement): String? {
        val text = element.text.trim()
        if (text.matches(Regex("""[A-Za-z_${'$'}][\w${'$'}]*"""))) return text

        val parentText = element.parent?.text ?: return null
        return Regex("""[A-Za-z_${'$'}][\w${'$'}]*""").findAll(parentText)
            .firstOrNull { element.textRange.startOffset >= element.parent.textRange.startOffset + it.range.first && element.textRange.endOffset <= element.parent.textRange.startOffset + it.range.last + 1 }
            ?.value
    }
}
```

- [ ] **4: Run navigation test**

Run `./gradlew test --tests com.ki960213.riverpodgraph.navigation.RiverpodGotoDeclarationHandlerTest`

Expect: PASS.

- [ ] **5: Commit**

```bash
git add src/main/kotlin/com/ki960213/riverpodgraph/navigation src/test/kotlin/com/ki960213/riverpodgraph/navigation
git commit -m "feat: redirect riverpod navigation"
```

## Task 7: Direct Usage Scanner And Find Usages

**Files:**
- Add: `src/main/kotlin/com/ki960213/riverpodgraph/analysis/ProviderUsageScanner.kt`
- Add: `src/main/kotlin/com/ki960213/riverpodgraph/search/RiverpodReference.kt`
- Mod: `src/main/kotlin/com/ki960213/riverpodgraph/search/RiverpodReferencesSearchExecutor.kt`
- Test: `src/test/kotlin/com/ki960213/riverpodgraph/analysis/ProviderUsageScannerTest.kt`

- [ ] **1: Write failing usage scanner tests**

```kotlin
package com.ki960213.riverpodgraph.analysis

import com.ki960213.riverpodgraph.model.RiverpodUsageKind
import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderUsageScannerTest {
    @Test
    fun `scans common ref calls and provider modifiers`() {
        val usages = ProviderUsageScanner.scan(
            filePath = "lib/widget.dart",
            content = """
                final a = ref.watch(userProvider);
                final b = ref.read(sessionProvider.notifier);
                ref.listen(settingsProvider.select((s) => s.theme), (_, __) {});
                ref.invalidate(cacheProvider);
                ref.refresh(feedProvider.future);
                overrides: [userProvider.overrideWith((ref) => user)]
                final direct = user();
            """.trimIndent(),
            providerNames = setOf("userProvider", "sessionProvider", "settingsProvider", "cacheProvider", "feedProvider")
        )

        assertEquals(
            listOf(
                RiverpodUsageKind.WATCH,
                RiverpodUsageKind.NOTIFIER,
                RiverpodUsageKind.SELECT,
                RiverpodUsageKind.INVALIDATE,
                RiverpodUsageKind.FUTURE,
                RiverpodUsageKind.OVERRIDE,
                RiverpodUsageKind.DIRECT_CALL
            ),
            usages.map { it.kind }
        )
    }
}
```

- [ ] **2: Run usage scanner test; verify fail**

Run `./gradlew test --tests com.ki960213.riverpodgraph.analysis.ProviderUsageScannerTest`

Expect: FAIL: unresolved `ProviderUsageScanner`.

- [ ] **3: Implement direct usage scanning + PSI references**

Add `ProviderUsageScanner.kt`:

```kotlin
package com.ki960213.riverpodgraph.analysis

import com.ki960213.riverpodgraph.model.RiverpodProviderUsage
import com.ki960213.riverpodgraph.model.RiverpodUsageKind

object ProviderUsageScanner {
    fun scan(filePath: String, content: String, providerNames: Set<String>): List<RiverpodProviderUsage> {
        val usages = mutableListOf<RiverpodProviderUsage>()

        for (providerName in providerNames) {
            Regex("""ref\.(watch|read|listen|invalidate|refresh)\s*\(\s*${Regex.escape(providerName)}(?:\.(notifier|future|select))?""")
                .findAll(content)
                .forEach { match ->
                    val modifier = match.groupValues.getOrNull(2).orEmpty()
                    val kind = when {
                        modifier == "notifier" -> RiverpodUsageKind.NOTIFIER
                        modifier == "future" -> RiverpodUsageKind.FUTURE
                        modifier == "select" -> RiverpodUsageKind.SELECT
                        else -> when (match.groupValues[1]) {
                            "watch" -> RiverpodUsageKind.WATCH
                            "read" -> RiverpodUsageKind.READ
                            "listen" -> RiverpodUsageKind.LISTEN
                            "invalidate" -> RiverpodUsageKind.INVALIDATE
                            "refresh" -> RiverpodUsageKind.REFRESH
                            else -> RiverpodUsageKind.READ
                        }
                    }
                    usages += usage(providerName, kind, filePath, content, match.range.first + match.value.indexOf(providerName))
                }

            Regex("""${Regex.escape(providerName)}\.overrideWith[A-Za-z]*\s*\(""")
                .findAll(content)
                .forEach { match ->
                    usages += usage(providerName, RiverpodUsageKind.OVERRIDE, filePath, content, match.range.first)
                }

            val sourceFunction = providerName.removeSuffix("Provider")
            Regex("""(?<![.\w${'$'}])${Regex.escape(sourceFunction)}\s*\(""")
                .findAll(content)
                .forEach { match ->
                    usages += usage(providerName, RiverpodUsageKind.DIRECT_CALL, filePath, content, match.range.first)
                }
        }

        return usages.sortedBy { it.textOffset }.distinctBy { it.providerName to it.textOffset to it.kind }
    }

    private fun usage(
        providerName: String,
        kind: RiverpodUsageKind,
        filePath: String,
        content: String,
        offset: Int
    ): RiverpodProviderUsage = RiverpodProviderUsage(
        providerName = providerName,
        kind = kind,
        filePath = filePath,
        textOffset = offset,
        line = content.substring(0, offset).count { it == '\n' } + 1
    )
}
```

Add `RiverpodReference.kt`:

```kotlin
package com.ki960213.riverpodgraph.search

import com.ki960213.riverpodgraph.resolution.RiverpodProviderResolver
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceBase

class RiverpodReference(
    element: PsiElement,
    rangeInElement: TextRange,
    private val providerName: String
) : PsiReferenceBase<PsiElement>(element, rangeInElement, true) {
    override fun resolve(): PsiElement? = RiverpodProviderResolver(element.project).findSourceElement(providerName)
}
```

Replace `RiverpodReferencesSearchExecutor.kt` with impl that:

```kotlin
package com.ki960213.riverpodgraph.search

import com.ki960213.riverpodgraph.index.RiverpodProviderIndex
import com.ki960213.riverpodgraph.analysis.ProviderUsageScanner
import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.Processor
import com.intellij.util.indexing.FileBasedIndex

class RiverpodReferencesSearchExecutor : QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>(true) {
    override fun processQuery(queryParameters: ReferencesSearch.SearchParameters, consumer: Processor<in PsiReference>) {
        val project = queryParameters.elementToSearch.project
        val scope = queryParameters.effectiveSearchScope as? GlobalSearchScope ?: GlobalSearchScope.projectScope(project)
        val searchedName = queryParameters.elementToSearch.text
        val providerValues = FileBasedIndex.getInstance().getValues(RiverpodProviderIndex.NAME, searchedName, scope)
        val providerName = providerValues.firstOrNull()?.providerName ?: searchedName.takeIf { it.endsWith("Provider") } ?: return

        FileBasedIndex.getInstance().getContainingFiles(RiverpodProviderIndex.NAME, providerName, scope)
        val files = FileBasedIndex.getInstance().getAllKeys(RiverpodProviderIndex.NAME, project)
            .flatMap { FileBasedIndex.getInstance().getContainingFiles(RiverpodProviderIndex.NAME, it, scope) }
            .toSet()

        for (file in files) {
            val psiFile = PsiManager.getInstance(project).findFile(file) ?: continue
            val usages = ProviderUsageScanner.scan(file.path, psiFile.text, setOf(providerName))
            for (usage in usages) {
                val element = psiFile.findElementAt(usage.textOffset) ?: continue
                val range = TextRange(0, element.textLength)
                if (!consumer.process(RiverpodReference(element, range, providerName))) return
            }
        }
    }
}
```

- [ ] **4: Run usage scanner tests**

Run `./gradlew test --tests com.ki960213.riverpodgraph.analysis.ProviderUsageScannerTest`

Expect: PASS.

- [ ] **5: Commit**

```bash
git add src/main/kotlin/com/ki960213/riverpodgraph/analysis/ProviderUsageScanner.kt src/main/kotlin/com/ki960213/riverpodgraph/search src/test/kotlin/com/ki960213/riverpodgraph/analysis/ProviderUsageScannerTest.kt
git commit -m "feat: find riverpod provider usages"
```

## Task 8: Provider Dependency Graph

**Files:**
- Add: `src/main/kotlin/com/ki960213/riverpodgraph/analysis/ProviderDependencyAnalyzer.kt`
- Mod: `src/main/kotlin/com/ki960213/riverpodgraph/actions/ShowProviderDependencyGraphAction.kt`
- Test: `src/test/kotlin/com/ki960213/riverpodgraph/analysis/ProviderDependencyAnalyzerTest.kt`

- [ ] **1: Write failing dependency graph tests**

```kotlin
package com.ki960213.riverpodgraph.analysis

import com.ki960213.riverpodgraph.model.RiverpodProviderDeclaration
import com.ki960213.riverpodgraph.model.RiverpodProviderKind
import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderDependencyAnalyzerTest {
    @Test
    fun `finds provider dependencies inside function and notifier method bodies`() {
        val declarations = listOf(
            declaration("user", "userProvider"),
            declaration("session", "sessionProvider"),
            declaration("profile", "profileProvider")
        )

        val source = """
            @riverpod
            Future<Profile> profile(Ref ref) async {
              final user = ref.watch(userProvider);
              final session = ref.read(sessionProvider.notifier);
              return Profile(user, session);
            }
        """.trimIndent()

        val edges = ProviderDependencyAnalyzer.analyzeFile("lib/profile.dart", source, declarations)

        assertEquals(listOf("userProvider", "sessionProvider"), edges.map { it.toProvider })
    }

    private fun declaration(sourceName: String, providerName: String) = RiverpodProviderDeclaration(
        kind = RiverpodProviderKind.FUNCTION,
        sourceName = sourceName,
        providerName = providerName,
        generatedSuperclassName = null,
        returnType = "Object",
        familySignature = "Ref ref",
        keepAlive = false,
        isPrivate = false,
        filePath = "lib/$sourceName.dart",
        textOffset = 0,
        line = 1
    )
}
```

- [ ] **2: Run dependency graph tests; verify fail**

Run `./gradlew test --tests com.ki960213.riverpodgraph.analysis.ProviderDependencyAnalyzerTest`

Expect: FAIL: unresolved `ProviderDependencyAnalyzer`.

- [ ] **3: Implement graph analysis + provider graph action**

Add `ProviderDependencyAnalyzer.kt`:

```kotlin
package com.ki960213.riverpodgraph.analysis

import com.ki960213.riverpodgraph.model.RiverpodDependencyEdge
import com.ki960213.riverpodgraph.model.RiverpodMarker
import com.ki960213.riverpodgraph.model.RiverpodProviderDeclaration

object ProviderDependencyAnalyzer {
    fun analyzeFile(
        filePath: String,
        content: String,
        declarations: List<RiverpodProviderDeclaration>
    ): List<RiverpodDependencyEdge> {
        val providerNames = declarations.map { it.providerName }.toSet()
        return declarations.flatMap { declaration ->
            val body = bodyStartingAt(content, declaration.textOffset)
            ProviderUsageScanner.scan(filePath, body, providerNames - declaration.providerName)
                .map { usage ->
                    RiverpodDependencyEdge(
                        fromProvider = declaration.providerName,
                        toProvider = usage.providerName,
                        usageKind = usage.kind,
                        marker = null
                    )
                }
        }.distinct()
    }

    fun markCycles(edges: List<RiverpodDependencyEdge>): List<RiverpodDependencyEdge> {
        val adjacency = edges.groupBy { it.fromProvider }.mapValues { entry -> entry.value.map { it.toProvider } }
        return edges.map { edge ->
            if (hasPath(edge.toProvider, edge.fromProvider, adjacency, mutableSetOf())) edge.copy(marker = RiverpodMarker.CYCLE) else edge
        }
    }

    private fun bodyStartingAt(content: String, offset: Int): String = content.substring(offset.coerceIn(0, content.length))

    private fun hasPath(current: String, target: String, adjacency: Map<String, List<String>>, seen: MutableSet<String>): Boolean {
        if (current == target) return true
        if (!seen.add(current)) return false
        return adjacency[current].orEmpty().any { hasPath(it, target, adjacency, seen) }
    }
}
```

Replace `ShowProviderDependencyGraphAction.kt` with:

```kotlin
package com.ki960213.riverpodgraph.actions

import com.ki960213.riverpodgraph.resolution.RiverpodProviderResolver
import com.ki960213.riverpodgraph.ui.DependencyGraphPanel
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.wm.ToolWindowManager

class ShowProviderDependencyGraphAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val editor = event.getData(CommonDataKeys.EDITOR) ?: return
        val file = event.getData(CommonDataKeys.PSI_FILE) ?: return
        val element = file.findElementAt(editor.caretModel.offset) ?: return
        val declaration = RiverpodProviderResolver(project).findDeclaration(element.text) ?: return

        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Riverpod Graph") ?: return
        toolWindow.show {
            val component = toolWindow.contentManager.contents.firstOrNull()?.component
            DependencyGraphPanel.findIn(component)?.showProvider(declaration.providerName)
        }
    }
    override fun update(event: AnActionEvent) {
        val element = event.getData(CommonDataKeys.PSI_ELEMENT)
        event.presentation.isEnabledAndVisible = event.project != null && element != null
    }
}
```

- [ ] **4: Run dependency graph tests**

Run `./gradlew test --tests com.ki960213.riverpodgraph.analysis.ProviderDependencyAnalyzerTest`

Expect: PASS.

- [ ] **5: Commit**

```bash
git add src/main/kotlin/com/ki960213/riverpodgraph/analysis/ProviderDependencyAnalyzer.kt src/main/kotlin/com/ki960213/riverpodgraph/actions/ShowProviderDependencyGraphAction.kt src/test/kotlin/com/ki960213/riverpodgraph/analysis/ProviderDependencyAnalyzerTest.kt
git commit -m "feat: analyze provider dependencies"
```

## Task 9: Ref Extension Indirect Dependencies

**Files:**
- Add: `src/main/kotlin/com/ki960213/riverpodgraph/analysis/RefExtensionScanner.kt`
- Mod: `src/main/kotlin/com/ki960213/riverpodgraph/analysis/ProviderUsageScanner.kt`
- Mod: `src/main/kotlin/com/ki960213/riverpodgraph/search/RiverpodReferencesSearchExecutor.kt`
- Test: `src/test/kotlin/com/ki960213/riverpodgraph/analysis/RefExtensionScannerTest.kt`

- [ ] **1: Write failing Ref extension tests**

```kotlin
package com.ki960213.riverpodgraph.analysis

import org.junit.Assert.assertEquals
import org.junit.Test

class RefExtensionScannerTest {
    @Test
    fun `maps extension getters and methods to provider usages`() {
        val result = RefExtensionScanner.scan(
            filePath = "lib/ref_x.dart",
            content = """
                extension WatchUserX on WidgetRef {
                  User get currentUser => watch(userProvider).requireValue;
                  Future<void> reload() => refresh(feedProvider.future);
                }
            """.trimIndent(),
            providerNames = setOf("userProvider", "feedProvider")
        )

        assertEquals(listOf("WatchUserX.currentUser", "WatchUserX.reload"), result.map { it.memberId })
        assertEquals(listOf("userProvider"), result[0].providerNames)
        assertEquals(listOf("feedProvider"), result[1].providerNames)
    }
}
```

- [ ] **2: Run Ref extension tests; verify fail**

Run `./gradlew test --tests com.ki960213.riverpodgraph.analysis.RefExtensionScannerTest`

Expect: FAIL: unresolved `RefExtensionScanner`.

- [ ] **3: Implement Ref extension scanner + indirect usage merge**

Add `RefExtensionScanner.kt`:

```kotlin
package com.ki960213.riverpodgraph.analysis

data class RefExtensionDependency(
    val memberId: String,
    val receiverType: String,
    val providerNames: List<String>,
    val filePath: String,
    val textOffset: Int
)

object RefExtensionScanner {
    private val extensionRegex = Regex("""extension\s+([A-Za-z_${'$'}][\w${'$'}]*)\s+on\s+([A-Za-z_${'$'}][\w${'$'}]*)\s*\{""")
    private val getterRegex = Regex("""([A-Za-z_${'$'}][\w${'$'}<>, ?.]*)\s+get\s+([A-Za-z_${'$'}][\w${'$'}]*)\s*=>\s*([^;]+);""")
    private val methodRegex = Regex("""([A-Za-z_${'$'}][\w${'$'}<>, ?.]*)\s+([A-Za-z_${'$'}][\w${'$'}]*)\s*\([^)]*\)\s*=>\s*([^;]+);""")

    fun scan(filePath: String, content: String, providerNames: Set<String>): List<RefExtensionDependency> {
        return extensionRegex.findAll(content).flatMap { extension ->
            val extensionName = extension.groupValues[1]
            val receiverType = extension.groupValues[2]
            val bodyStart = extension.range.last + 1
            val bodyEnd = findMatchingBrace(content, bodyStart - 1)
            val body = content.substring(bodyStart, bodyEnd)

            val getterDependencies = getterRegex.findAll(body).map { member ->
                dependency(extensionName, receiverType, member.groupValues[2], member.groupValues[3], providerNames, filePath, bodyStart + member.range.first)
            }
            val methodDependencies = methodRegex.findAll(body).map { member ->
                dependency(extensionName, receiverType, member.groupValues[2], member.groupValues[3], providerNames, filePath, bodyStart + member.range.first)
            }

            (getterDependencies + methodDependencies).filter { it.providerNames.isNotEmpty() }
        }.toList()
    }

    private fun dependency(
        extensionName: String,
        receiverType: String,
        memberName: String,
        expression: String,
        providerNames: Set<String>,
        filePath: String,
        offset: Int
    ): RefExtensionDependency {
        val used = providerNames.filter { expression.contains(it) }
        return RefExtensionDependency("$extensionName.$memberName", receiverType, used, filePath, offset)
    }

    private fun findMatchingBrace(content: String, openBraceOffset: Int): Int {
        var depth = 0
        for (index in openBraceOffset until content.length) {
            if (content[index] == '{') depth++
            if (content[index] == '}') depth--
            if (depth == 0) return index
        }
        return content.length
    }
}
```

Update `ProviderUsageScanner.scan` to accept optional `extensionDependencies: List<RefExtensionDependency> = emptyList()`; append `RiverpodUsageKind.EXTENSION_MEMBER` usages when source contains `.memberName` / `ref.memberName`.

Update `RiverpodReferencesSearchExecutor` to include extension-member references in same consumer flow, use `RiverpodReference` with provider from extension dependency.

- [ ] **4: Run Ref extension + usage tests**

Run `./gradlew test --tests com.ki960213.riverpodgraph.analysis.RefExtensionScannerTest --tests com.ki960213.riverpodgraph.analysis.ProviderUsageScannerTest`

Expect: PASS.

- [ ] **5: Commit**

```bash
git add src/main/kotlin/com/ki960213/riverpodgraph/analysis src/main/kotlin/com/ki960213/riverpodgraph/search src/test/kotlin/com/ki960213/riverpodgraph/analysis
git commit -m "feat: include ref extension dependencies"
```

## Task 10: Providers Tool Window

**Files:**
- Mod: `src/main/kotlin/com/ki960213/riverpodgraph/ui/RiverpodToolWindowFactory.kt`
- Add: `src/main/kotlin/com/ki960213/riverpodgraph/ui/ProvidersPanel.kt`
- Add: `src/main/kotlin/com/ki960213/riverpodgraph/ui/DependencyGraphPanel.kt`
- Test: `src/test/kotlin/com/ki960213/riverpodgraph/ui/ProvidersPanelTest.kt`

- [ ] **1: Write failing tree label tests**

```kotlin
package com.ki960213.riverpodgraph.ui

import com.ki960213.riverpodgraph.model.RiverpodProviderDeclaration
import com.ki960213.riverpodgraph.model.RiverpodProviderKind
import org.junit.Assert.assertEquals
import org.junit.Test

class ProvidersPanelTest {
    @Test
    fun `provider row includes name family return type keep alive and path`() {
        val row = ProvidersPanel.providerRow(
            RiverpodProviderDeclaration(
                kind = RiverpodProviderKind.FUNCTION,
                sourceName = "user",
                providerName = "userProvider",
                generatedSuperclassName = null,
                returnType = "Future<User>",
                familySignature = "Ref ref, String id",
                keepAlive = true,
                isPrivate = false,
                filePath = "lib/user.dart",
                textOffset = 12,
                line = 5
            )
        )

        assertEquals("userProvider(Ref ref, String id) : Future<User> keepAlive - lib/user.dart:5", row)
    }
}
```

- [ ] **2: Run UI label test; verify fail**

Run `./gradlew test --tests com.ki960213.riverpodgraph.ui.ProvidersPanelTest`

Expect: FAIL: unresolved `ProvidersPanel`.

- [ ] **3: Implement Swing tabs + provider row formatting**

Add `ProvidersPanel.kt`:

```kotlin
package com.ki960213.riverpodgraph.ui

import com.ki960213.riverpodgraph.model.RiverpodProviderDeclaration
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.treeStructure.Tree
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

class ProvidersPanel : JPanel(BorderLayout()) {
    private val root = DefaultMutableTreeNode("Providers")
    private val tree = Tree(DefaultTreeModel(root))

    init {
        add(ScrollPaneFactory.createScrollPane(tree), BorderLayout.CENTER)
    }

    fun setProviders(declarations: List<RiverpodProviderDeclaration>) {
        root.removeAllChildren()
        declarations.sortedWith(compareBy<RiverpodProviderDeclaration> { it.filePath }.thenBy { it.line })
            .groupBy { it.filePath }
            .forEach { (path, providers) ->
                val fileNode = DefaultMutableTreeNode(path)
                providers.forEach { fileNode.add(DefaultMutableTreeNode(providerRow(it))) }
                root.add(fileNode)
            }
        (tree.model as DefaultTreeModel).reload()
    }

    companion object {
        fun providerRow(declaration: RiverpodProviderDeclaration): String {
            val signature = if (declaration.familySignature.isBlank()) "" else "(${declaration.familySignature})"
            val keepAlive = if (declaration.keepAlive) " keepAlive" else ""
            val privatePrefix = if (declaration.isPrivate) "private " else ""
            return "$privatePrefix${declaration.providerName}$signature : ${declaration.returnType}$keepAlive - ${declaration.filePath}:${declaration.line}"
        }
    }
}
```

Add `DependencyGraphPanel.kt`:

```kotlin
package com.ki960213.riverpodgraph.ui

import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.treeStructure.Tree
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.JPanel
import javax.swing.JTabbedPane
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

class DependencyGraphPanel : JPanel(BorderLayout()) {
    private val root = DefaultMutableTreeNode("Dependencies")
    private val tree = Tree(DefaultTreeModel(root))

    init {
        add(ScrollPaneFactory.createScrollPane(tree), BorderLayout.CENTER)
    }

    fun showProvider(providerName: String) {
        root.removeAllChildren()
        root.add(DefaultMutableTreeNode(providerName))
        (tree.model as DefaultTreeModel).reload()
    }

    companion object {
        fun findIn(component: Component?): DependencyGraphPanel? {
            if (component is DependencyGraphPanel) return component
            if (component is JTabbedPane) {
                for (index in 0 until component.tabCount) {
                    val found = findIn(component.getComponentAt(index))
                    if (found != null) return found
                }
            }
            return null
        }
    }
}
```

Replace `RiverpodToolWindowFactory.kt`:

```kotlin
package com.ki960213.riverpodgraph.ui

import com.ki960213.riverpodgraph.activation.RiverpodActivationService
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import javax.swing.JTabbedPane

class RiverpodToolWindowFactory : ToolWindowFactory {
    override fun isApplicable(project: Project): Boolean = RiverpodActivationService.getInstance(project).isProjectActive()

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val tabs = JTabbedPane()
        tabs.addTab("Providers", ProvidersPanel())
        tabs.addTab("Dependencies", DependencyGraphPanel())

        val content = ContentFactory.getInstance().createContent(tabs, "", false)
        toolWindow.contentManager.addContent(content)
    }
}
```

- [ ] **4: Run UI tests**

Run `./gradlew test --tests com.ki960213.riverpodgraph.ui.ProvidersPanelTest`

Expect: PASS.

- [ ] **5: Commit**

```bash
git add src/main/kotlin/com/ki960213/riverpodgraph/ui src/test/kotlin/com/ki960213/riverpodgraph/ui
git commit -m "feat: add riverpod graph tool window"
```

## Task 11: Static Widget Dependency Tree

**Files:**
- Add: `src/main/kotlin/com/ki960213/riverpodgraph/analysis/WidgetDependencyAnalyzer.kt`
- Add: `src/main/kotlin/com/ki960213/riverpodgraph/ui/WidgetDependencyDialog.kt`
- Mod: `src/main/kotlin/com/ki960213/riverpodgraph/actions/ShowWidgetDependenciesAction.kt`
- Test: `src/test/kotlin/com/ki960213/riverpodgraph/analysis/WidgetDependencyAnalyzerTest.kt`

- [ ] **1: Write failing widget analyzer tests**

```kotlin
package com.ki960213.riverpodgraph.analysis

import com.ki960213.riverpodgraph.model.RiverpodMarker
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetDependencyAnalyzerTest {
    @Test
    fun `finds direct provider usage and child widget candidates`() {
        val result = WidgetDependencyAnalyzer.analyze(
            filePath = "lib/home.dart",
            content = """
                class HomeScreen extends ConsumerWidget {
                  @override
                  Widget build(BuildContext context, WidgetRef ref) {
                    final user = ref.watch(userProvider);
                    return Column(children: [
                      if (user.isAdmin) AdminPanel(),
                      ListView.builder(itemBuilder: (context, index) => UserTile()),
                    ]);
                  }
                }
            """.trimIndent(),
            providerNames = setOf("userProvider"),
            depthLimit = 5
        )

        assertEquals(listOf("userProvider"), result.providerNames)
        assertEquals(listOf("AdminPanel", "UserTile"), result.childWidgets.map { it.name })
        assertEquals(RiverpodMarker.CONDITIONAL, result.childWidgets[0].marker)
        assertEquals(RiverpodMarker.CALLBACK, result.childWidgets[1].marker)
    }
}
```

- [ ] **2: Run widget analyzer tests; verify fail**

Run `./gradlew test --tests com.ki960213.riverpodgraph.analysis.WidgetDependencyAnalyzerTest`

Expect: FAIL: unresolved `WidgetDependencyAnalyzer`.

- [ ] **3: Implement static widget analysis + action**

Add `WidgetDependencyAnalyzer.kt`:

```kotlin
package com.ki960213.riverpodgraph.analysis

import com.ki960213.riverpodgraph.model.RiverpodMarker

data class WidgetDependencyResult(
    val widgetName: String,
    val providerNames: List<String>,
    val childWidgets: List<WidgetChildCandidate>
)

data class WidgetChildCandidate(
    val name: String,
    val marker: RiverpodMarker?,
    val textOffset: Int
)

object WidgetDependencyAnalyzer {
    private val widgetClassRegex = Regex("""class\s+([A-Za-z_${'$'}][\w${'$'}]*)\s+extends\s+(ConsumerWidget|ConsumerStatefulWidget|HookConsumerWidget|StatefulHookConsumerWidget|StatelessWidget|StatefulWidget)""")
    private val constructorRegex = Regex("""\b([A-Z][A-Za-z0-9_]*)\s*\(""")

    fun analyze(filePath: String, content: String, providerNames: Set<String>, depthLimit: Int): WidgetDependencyResult {
        val widgetName = widgetClassRegex.find(content)?.groupValues?.get(1) ?: filePath.substringAfterLast('/').removeSuffix(".dart")
        val providers = ProviderUsageScanner.scan(filePath, content, providerNames).map { it.providerName }.distinct()
        val children = constructorRegex.findAll(content)
            .filter { it.groupValues[1] !in setOf("Widget", "BuildContext", "Column", "Row", "ListView", "FutureBuilder", "Builder", "Consumer") }
            .map { match ->
                WidgetChildCandidate(
                    name = match.groupValues[1],
                    marker = markerFor(content, match.range.first),
                    textOffset = match.range.first
                )
            }
            .distinctBy { it.name to it.textOffset }
            .take(if (depthLimit <= 0) Int.MAX_VALUE else depthLimit * 20)
            .toList()

        return WidgetDependencyResult(widgetName, providers, children)
    }

    private fun markerFor(content: String, offset: Int): RiverpodMarker? {
        val prefix = content.substring(0, offset).takeLast(160)
        return when {
            Regex("""\bif\s*\(""").containsMatchIn(prefix) || "?" in prefix.takeLast(40) -> RiverpodMarker.CONDITIONAL
            Regex("""\b(for|forEach|map)\s*\(""").containsMatchIn(prefix) -> RiverpodMarker.LOOP
            Regex("""(builder|itemBuilder)\s*:""").containsMatchIn(prefix) || "=>" in prefix.takeLast(80) -> RiverpodMarker.CALLBACK
            else -> null
        }
    }
}
```

Add `WidgetDependencyDialog.kt`:

```kotlin
package com.ki960213.riverpodgraph.ui

import com.ki960213.riverpodgraph.analysis.WidgetDependencyResult
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.treeStructure.Tree
import javax.swing.JComponent
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

class WidgetDependencyDialog(private val result: WidgetDependencyResult) : DialogWrapper(false) {
    init {
        title = "Riverpod Widget Dependencies"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val root = DefaultMutableTreeNode(result.widgetName)
        val providers = DefaultMutableTreeNode("Providers")
        result.providerNames.forEach { providers.add(DefaultMutableTreeNode(it)) }
        val children = DefaultMutableTreeNode("Child Widgets")
        result.childWidgets.forEach { child ->
            val suffix = child.marker?.let { " [${it.label}]" }.orEmpty()
            children.add(DefaultMutableTreeNode(child.name + suffix))
        }
        root.add(providers)
        root.add(children)
        return ScrollPaneFactory.createScrollPane(Tree(DefaultTreeModel(root)))
    }
}
```

Replace `ShowWidgetDependenciesAction.kt`:

```kotlin
package com.ki960213.riverpodgraph.actions

import com.ki960213.riverpodgraph.analysis.WidgetDependencyAnalyzer
import com.ki960213.riverpodgraph.ui.WidgetDependencyDialog
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys

class ShowWidgetDependenciesAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val file = event.getData(CommonDataKeys.PSI_FILE) ?: return
        val providerNames = Regex("""[A-Za-z_${'$'}][\w${'$'}]*Provider""").findAll(file.text).map { it.value }.toSet()
        val result = WidgetDependencyAnalyzer.analyze(file.virtualFile.path, file.text, providerNames, depthLimit = 5)
        WidgetDependencyDialog(result).show()
    }

    override fun update(event: AnActionEvent) {
        val file = event.getData(CommonDataKeys.PSI_FILE)
        event.presentation.isEnabledAndVisible = file?.virtualFile?.extension == "dart"
    }
}
```

- [ ] **4: Run widget analyzer tests**

Run `./gradlew test --tests com.ki960213.riverpodgraph.analysis.WidgetDependencyAnalyzerTest`

Expect: PASS.

- [ ] **5: Commit**

```bash
git add src/main/kotlin/com/ki960213/riverpodgraph/analysis/WidgetDependencyAnalyzer.kt src/main/kotlin/com/ki960213/riverpodgraph/ui/WidgetDependencyDialog.kt src/main/kotlin/com/ki960213/riverpodgraph/actions/ShowWidgetDependenciesAction.kt src/test/kotlin/com/ki960213/riverpodgraph/analysis/WidgetDependencyAnalyzerTest.kt
git commit -m "feat: show widget provider dependencies"
```

## Task 12: Gutter Icon, Docs, And Release Readiness

**Files:**
- Mod: `src/main/kotlin/com/ki960213/riverpodgraph/gutter/RiverpodProviderLineMarkerProvider.kt`
- Mod: `README.md`
- Mod: `CHANGELOG.md`
- Test: `src/test/kotlin/com/ki960213/riverpodgraph/gutter/RiverpodProviderLineMarkerProviderTest.kt`

- [ ] **1: Write failing line marker test**

```kotlin
package com.ki960213.riverpodgraph.gutter

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class RiverpodProviderLineMarkerProviderTest : BasePlatformTestCase() {
    fun testAddsMarkerToRiverpodAnnotationElement() {
        myFixture.configureByText(
            "counter.dart",
            """
            @riverpod
            int counter(Ref ref) => 0;
            """.trimIndent()
        )

        val markers = myFixture.doHighlighting().filter { it.gutterIconRenderer != null }

        assertTrue(markers.any { it.description?.contains("Riverpod provider") == true })
    }
}
```

- [ ] **2: Run gutter test; verify fail**

Run `./gradlew test --tests com.ki960213.riverpodgraph.gutter.RiverpodProviderLineMarkerProviderTest`

Expect: FAIL: no marker is created.

- [ ] **3: Implement gutter marker + docs**

Replace `RiverpodProviderLineMarkerProvider.kt` with:

```kotlin
package com.ki960213.riverpodgraph.gutter

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement

class RiverpodProviderLineMarkerProvider : LineMarkerProvider {
    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        if (element.text != "@riverpod" && !element.text.startsWith("@Riverpod")) return null

        return LineMarkerInfo(
            element,
            element.textRange,
            AllIcons.Nodes.Property,
            { "Riverpod provider" },
            null,
            GutterIconRenderer.Alignment.CENTER,
            { "Riverpod provider" }
        )
    }
}
```

Replace `README.md` with concise product docs:

```markdown
# Riverpod Graph

Riverpod Graph is a JetBrains IDE plugin for Flutter projects that use `riverpod_generator` 3.x.

## v1 Features

- Redirects Go to Declaration for generated `fooProvider` and `_$Foo` symbols back to the source `@riverpod` function or class.
- Integrates Find Usages for source declarations and generated provider symbols.
- Shows a Riverpod Graph tool window with Providers and Dependencies tabs.
- Shows static widget dependency trees from widget context actions.
- Adds gutter markers beside `@riverpod` declarations.

## Scope

The plugin activates only for modules whose `pubspec.yaml` contains `riverpod_annotation`.

Legacy manual providers such as `Provider((ref) => ...)`, `StateNotifierProvider`, and `ChangeNotifierProvider` are outside v1 scope because the Dart plugin already handles them.

Widget dependency analysis is static. Conditional, loop, callback, Ref extension, and cycle cases are marked so the result stays honest about analysis limits.

## License

Apache License 2.0.
```

Update `CHANGELOG.md`:

```markdown
<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Riverpod Graph Changelog

## [Unreleased]

### Added

- Riverpod generator declaration index.
- Go to Declaration redirect for generated provider symbols.
- Find Usages integration for generated provider usage.
- Providers and Dependencies tool window tabs.
- Static widget dependency view.
- Gutter marker for `@riverpod` declarations.
```

- [ ] **4: Final verify**

Run `./gradlew test verifyPlugin`

Expect: PASS.

Run `./gradlew runIde`

Expect: IDE launches, plugin loads, Dart project with `riverpod_annotation` shows Riverpod Graph tool window.

- [ ] **5: Commit**

```bash
git add src/main/kotlin/com/ki960213/riverpodgraph/gutter src/test/kotlin/com/ki960213/riverpodgraph/gutter README.md CHANGELOG.md
git commit -m "feat: prepare riverpod graph v1"
```

## Manual Acceptance Checklist

- Open sample Riverpod generator project where `pubspec.yaml` includes `riverpod_annotation`.
- Exclude generated `*.g.dart` files from IntelliJ indexing.
- Go to Declaration on `ref.watch(userProvider)` + verify it opens `@riverpod user(...)`.
- Go to Declaration on `class User extends _$User` + verify it opens `User` class declaration when caret is on `_$User`.
- Find Usages on `user()` + `userProvider` + verify both include `watch`, `read`, `listen`, `invalidate`, `refresh`, `.notifier`, `.future`, `.select`, overrides, direct source calls, + Ref extension indirect usages.
- Open Riverpod Graph tool window; verify Providers are grouped by source file with name, family signature, return type, keepAlive, + path line.
- Right-click provider; run Show Riverpod Dependency Graph; verify cycles are marked + traversal stops at cycle.
- Right-click `ConsumerWidget`, `ConsumerStatefulWidget`, `HookConsumerWidget`, `StatefulHookConsumerWidget`, `StatelessWidget`, + `StatefulWidget`; verify direct providers + child widget candidates appear.
- Confirm conditional, loop, callback, Ref extension, + cycle markers render as text labels.
- Confirm plugin inactive in Dart module no `riverpod_annotation`.

## Self Review

- Spec coverage: navigation redirect maps to Tasks 5-7; Find Usages maps to Tasks 7 + 9; Providers tab maps to Task 10; widget dependency tree maps to Task 11; provider dependency graph maps to Task 8; gutter icon maps to Task 12; Android Studio + Swing constraints map to Task 1; distribution + Apache 2.0 map to Tasks 1 + 12.
- Red-flag scan: plan contains concrete file paths, commands, expected results, code snippets, + commit points.
- Type consistency: shared model names are `RiverpodProviderDeclaration`, `RiverpodProviderUsage`, `RiverpodDependencyEdge`, `RiverpodMarker`, + those names are used consistently across parser, index, search, analysis, + UI tasks.
- Out of scope accepted: Dart Analysis Server red-line overlays, ProviderScope overrides, cross-package widget traversal, settings panel, usage counts, + flat provider view remain outside v1.
