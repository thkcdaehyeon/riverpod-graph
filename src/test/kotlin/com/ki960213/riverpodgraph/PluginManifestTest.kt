package com.ki960213.riverpodgraph

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.nio.file.Path

class PluginManifestTest : StringSpec({
    "플러그인 매니페스트는 Dart에 의존하고 Compose에는 의존하지 않는다" {
        val xml = Files.readString(Path.of("src/main/resources/META-INF/plugin.xml"))

        (xml.contains("<depends>Dart</depends>")) shouldBe true
        (!xml.contains("com.intellij.modules.compose")) shouldBe true
        (xml.contains("Riverpod Graph")) shouldBe true
        (xml.contains("com.ki960213.riverpodgraph.navigation.RiverpodGotoDeclarationHandler")) shouldBe true
        (xml.contains("com.ki960213.riverpodgraph.search.RiverpodReferencesSearchExecutor")) shouldBe true
    }
})
