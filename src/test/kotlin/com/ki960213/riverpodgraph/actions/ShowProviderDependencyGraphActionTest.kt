package com.ki960213.riverpodgraph.actions

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import com.ki960213.riverpodgraph.model.RiverpodMarker
import com.ki960213.riverpodgraph.model.RiverpodUsageKind
import com.ki960213.riverpodgraph.parser.RiverpodAnnotationParser

class ShowProviderDependencyGraphActionTest : StringSpec({
    "사용 파일에서 실행해도 선언 파일 기준 그래프를 만든다" {
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

        (edges.map { "${it.fromProvider}->${it.toProvider}:${it.usageKind}:${it.marker}" }) shouldBe listOf("profileProvider->userProvider:WATCH:null")
    }

    "프로바이더 파일 간 순환을 표시한다" {
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

        (edges.map { "${it.fromProvider}->${it.toProvider}:${it.usageKind}:${it.marker}" }) shouldBe listOf(
                "aProvider->bProvider:${RiverpodUsageKind.WATCH}:${RiverpodMarker.CYCLE}",
                "bProvider->aProvider:${RiverpodUsageKind.READ}:${RiverpodMarker.CYCLE}",
            )
    }
})
