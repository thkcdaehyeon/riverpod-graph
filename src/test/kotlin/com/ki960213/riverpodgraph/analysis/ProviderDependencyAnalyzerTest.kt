package com.ki960213.riverpodgraph.analysis

import com.ki960213.riverpodgraph.model.*
import com.ki960213.riverpodgraph.parser.RiverpodAnnotationParser
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class ProviderDependencyAnalyzerTest : StringSpec({
    "ref 사용에서 프로바이더 의존성을 분석한다" {
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

        (edges.filter { it.fromProvider == "profileProvider" }.map { it.toProvider }) shouldBe listOf(
            "userProvider",
            "sessionProvider"
        )
    }

    "다른 파일의 선언 오프셋 충돌이면 직접 호출을 유지한다" {
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

        (edges.filter { it.fromProvider == "profileProvider" }.map { it.toProvider }) shouldBe listOf("userProvider")
    }

    "ref 확장 멤버를 통한 의존성을 분석한다" {
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

        (edges.filter { it.fromProvider == "profileProvider" }) shouldBe listOf(
            RiverpodDependencyEdge(
                fromProvider = "profileProvider",
                toProvider = "userProvider",
                usageKind = RiverpodUsageKind.EXTENSION_MEMBER,
                marker = RiverpodMarker.REF_EXTENSION,
            ),
        )
    }

    "순환에 포함된 간선을 표시한다" {
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

        (marked.map { it.marker }) shouldBe listOf(RiverpodMarker.CYCLE, RiverpodMarker.CYCLE, null)
    }

})

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
