package com.ki960213.riverpodgraph.activation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PubspecDependencyParserTest {
    @Test
    fun `detects riverpod annotation dependency in dependencies block`() {
        val pubspec = """
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
            dependencies:
              flutter_riverpod: ^3.0.0
        """.trimIndent()

        assertFalse(PubspecDependencyParser.hasRiverpodAnnotation(pubspec))
    }

    @Test
    fun `detects riverpod annotation dependency in dev dependencies block`() {
        val pubspec = """
            dev_dependencies:
              riverpod_annotation: ^3.0.0
        """.trimIndent()

        assertTrue(PubspecDependencyParser.hasRiverpodAnnotation(pubspec))
    }

    @Test
    fun `detects riverpod annotation dependency with quoted keys`() {
        val pubspec = """
            "dependencies":
              'riverpod_annotation': ^3.0.0
        """.trimIndent()

        assertTrue(PubspecDependencyParser.hasRiverpodAnnotation(pubspec))
    }

    @Test
    fun `detects riverpod annotation dev dependency with quoted keys`() {
        val pubspec = """
            'dev_dependencies':
              "riverpod_annotation": ^3.0.0
        """.trimIndent()

        assertTrue(PubspecDependencyParser.hasRiverpodAnnotation(pubspec))
    }

    @Test
    fun `ignores nested dependencies block`() {
        val pubspec = """
            flutter:
              dependencies:
                riverpod_annotation: ^3.0.0
        """.trimIndent()

        assertFalse(PubspecDependencyParser.hasRiverpodAnnotation(pubspec))
    }

    @Test
    fun `detects riverpod annotation dependency in commented dependencies block`() {
        val pubspec = """
            dependencies: # app packages
              riverpod_annotation: ^3.0.0
        """.trimIndent()

        assertTrue(PubspecDependencyParser.hasRiverpodAnnotation(pubspec))
    }

    @Test
    fun `ignores nested riverpod annotation key inside another dependency`() {
        val pubspec = """
            dependencies:
              local_package:
                path: ../local_package
                riverpod_annotation: false
        """.trimIndent()

        assertFalse(PubspecDependencyParser.hasRiverpodAnnotation(pubspec))
    }
}
