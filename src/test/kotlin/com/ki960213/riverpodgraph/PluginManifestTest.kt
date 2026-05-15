package com.ki960213.riverpodgraph

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class PluginManifestTest {
    @Test
    fun `plugin manifest depends on Dart and not Compose`() {
        val xml = Files.readString(Path.of("src/main/resources/META-INF/plugin.xml"))

        assertTrue(xml.contains("<depends>Dart</depends>"))
        assertTrue(!xml.contains("com.intellij.modules.compose"))
        assertTrue(xml.contains("Riverpod Graph"))
        assertTrue(xml.contains("com.ki960213.riverpodgraph.navigation.RiverpodGotoDeclarationHandler"))
        assertTrue(xml.contains("com.ki960213.riverpodgraph.search.RiverpodReferencesSearchExecutor"))
    }
}
