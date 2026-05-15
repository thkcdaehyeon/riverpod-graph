package com.ki960213.riverpodgraph.analysis

import com.ki960213.riverpodgraph.model.RiverpodDependencyEdge
import com.ki960213.riverpodgraph.model.RiverpodMarker
import com.ki960213.riverpodgraph.model.RiverpodProviderDeclaration
import com.ki960213.riverpodgraph.model.RiverpodProviderKind
import com.ki960213.riverpodgraph.model.RiverpodUsageKind
import com.ki960213.riverpodgraph.parser.RiverpodAnnotationParser
import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderDependencyAnalyzerTest {
    @Test
    fun `analyzes provider dependencies from ref usages`() {
        val source = """
            import 'package:riverpod_annotation/riverpod_annotation.dart';

            @riverpod
            String user(Ref ref) => 'user';

            @riverpod
            Session session(Ref ref) => Session();

            @riverpod
            Profile profile(Ref ref) {
              final user = ref.watch(userProvider);
              final session = ref.read(sessionProvider.notifier);
              return Profile(user, session);
            }
        """.trimIndent()
        val declarations = RiverpodAnnotationParser.parse("lib/profile.dart", source)

        val edges = ProviderDependencyAnalyzer.analyzeFile("lib/profile.dart", source, declarations)

        assertEquals(
            listOf("userProvider", "sessionProvider"),
            edges.filter { it.fromProvider == "profileProvider" }.map { it.toProvider },
        )
    }

    @Test
    fun `keeps direct call when declaration offset collision belongs to another file`() {
        val source = """
            @riverpod
            Profile profile(Ref ref) {
              final user = user();
              return Profile(user);
            }
        """.trimIndent()
        val directCallOffset = source.indexOf("user();")
        val declarations = listOf(
            declaration(
                sourceName = "profile",
                providerName = "profileProvider",
                filePath = "lib/profile.dart",
                textOffset = source.indexOf("profile(Ref"),
            ),
            declaration(
                sourceName = "user",
                providerName = "userProvider",
                filePath = "lib/user.dart",
                textOffset = directCallOffset,
            ),
        )

        val edges = ProviderDependencyAnalyzer.analyzeFile("lib/profile.dart", source, declarations)

        assertEquals(
            listOf("userProvider"),
            edges.filter { it.fromProvider == "profileProvider" }.map { it.toProvider },
        )
    }

    @Test
    fun `analyzes provider dependencies through ref extension members`() {
        val source = """
            import 'package:riverpod_annotation/riverpod_annotation.dart';

            extension WatchUserX on WidgetRef {
              User get currentUser => watch(userProvider).requireValue;
            }

            @riverpod
            User user(Ref ref) => User();

            @riverpod
            Profile profile(Ref ref) {
              final user = ref.currentUser;
              return Profile(user);
            }
        """.trimIndent()
        val declarations = RiverpodAnnotationParser.parse("lib/profile.dart", source)

        val edges = ProviderDependencyAnalyzer.analyzeFile("lib/profile.dart", source, declarations)

        assertEquals(
            listOf(
                RiverpodDependencyEdge(
                    fromProvider = "profileProvider",
                    toProvider = "userProvider",
                    usageKind = RiverpodUsageKind.EXTENSION_MEMBER,
                    marker = RiverpodMarker.REF_EXTENSION,
                ),
            ),
            edges.filter { it.fromProvider == "profileProvider" },
        )
    }

    @Test
    fun `marks edges that participate in cycles`() {
        val edges = listOf(
            RiverpodDependencyEdge(
                fromProvider = "aProvider",
                toProvider = "bProvider",
                usageKind = RiverpodUsageKind.WATCH,
            ),
            RiverpodDependencyEdge(
                fromProvider = "bProvider",
                toProvider = "aProvider",
                usageKind = RiverpodUsageKind.READ,
            ),
            RiverpodDependencyEdge(
                fromProvider = "bProvider",
                toProvider = "cProvider",
                usageKind = RiverpodUsageKind.READ,
            ),
        )

        val marked = ProviderDependencyAnalyzer.markCycles(edges)

        assertEquals(
            listOf(RiverpodMarker.CYCLE, RiverpodMarker.CYCLE, null),
            marked.map { it.marker },
        )
    }

    private fun declaration(
        sourceName: String,
        providerName: String,
        filePath: String,
        textOffset: Int,
    ): RiverpodProviderDeclaration = RiverpodProviderDeclaration(
        kind = RiverpodProviderKind.FUNCTION,
        sourceName = sourceName,
        providerName = providerName,
        generatedSuperclassName = null,
        returnType = "Object",
        familySignature = "Ref ref",
        keepAlive = false,
        isPrivate = false,
        filePath = filePath,
        textOffset = textOffset,
        line = 1,
    )
}
