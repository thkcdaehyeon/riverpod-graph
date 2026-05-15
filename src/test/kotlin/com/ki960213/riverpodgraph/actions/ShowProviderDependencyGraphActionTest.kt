package com.ki960213.riverpodgraph.actions

import com.ki960213.riverpodgraph.model.RiverpodMarker
import com.ki960213.riverpodgraph.model.RiverpodUsageKind
import com.ki960213.riverpodgraph.parser.RiverpodAnnotationParser
import org.junit.Assert.assertEquals
import org.junit.Test

class ShowProviderDependencyGraphActionTest {
    @Test
    fun `builds provider graph from declaration source file when invoked from usage file`() {
        val providerSource = """
            @riverpod
            String user(Ref ref) => 'Ada';

            @riverpod
            Profile profile(Ref ref) {
              final user = ref.watch(userProvider);
              return Profile(user);
            }
        """.trimIndent()
        val usageSource = """
            class HomeScreen extends ConsumerWidget {
              Widget build(BuildContext context, WidgetRef ref) {
                final profile = ref.watch(profileProvider);
                return Text('${'$'}profile');
              }
            }
        """.trimIndent()
        val declarations = RiverpodAnnotationParser.parse("lib/providers.dart", providerSource)

        val edges = providerGraphEdgesForSourceFiles(
            sourceFiles = listOf(
                ProviderGraphSourceFile("lib/home.dart", usageSource),
                ProviderGraphSourceFile("lib/providers.dart", providerSource),
            ),
            declarations = declarations,
        )

        assertEquals(
            listOf("profileProvider->userProvider:WATCH:null"),
            edges.map { "${it.fromProvider}->${it.toProvider}:${it.usageKind}:${it.marker}" },
        )
    }

    @Test
    fun `marks cycles across provider source files`() {
        val aSource = """
            @riverpod
            String a(Ref ref) {
              return ref.watch(bProvider);
            }
        """.trimIndent()
        val bSource = """
            @riverpod
            String b(Ref ref) {
              return ref.read(aProvider);
            }
        """.trimIndent()
        val declarations = RiverpodAnnotationParser.parse("lib/a.dart", aSource) +
            RiverpodAnnotationParser.parse("lib/b.dart", bSource)

        val edges = providerGraphEdgesForSourceFiles(
            sourceFiles = listOf(
                ProviderGraphSourceFile("lib/a.dart", aSource),
                ProviderGraphSourceFile("lib/b.dart", bSource),
            ),
            declarations = declarations,
        )

        assertEquals(
            listOf(
                "aProvider->bProvider:${RiverpodUsageKind.WATCH}:${RiverpodMarker.CYCLE}",
                "bProvider->aProvider:${RiverpodUsageKind.READ}:${RiverpodMarker.CYCLE}",
            ),
            edges.map { "${it.fromProvider}->${it.toProvider}:${it.usageKind}:${it.marker}" },
        )
    }
}
