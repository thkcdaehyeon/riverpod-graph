package com.ki960213.riverpodgraph.analysis

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import com.ki960213.riverpodgraph.model.RiverpodMarker
import com.ki960213.riverpodgraph.model.RiverpodUsageKind

class ProviderUsageScannerTest : StringSpec({
    "일반 ref 호출과 수정자, override, 직접 호출을 스캔한다" {
        val content = """
            final a = ref.watch(userProvider);
            final b = ref.read(sessionProvider.notifier);
            ref.listen(settingsProvider.select((s) => s.theme), (_, __) {});
            ref.invalidate(cacheProvider);
            ref.refresh(feedProvider.future);
            overrides: [userProvider.overrideWith((ref) => user)]
            final direct = user();
        """.trimIndent()

        val usages = ProviderUsageScanner.scan(
            filePath = "lib/widget.dart",
            content = content,
            providerNames = setOf(
                "userProvider",
                "sessionProvider",
                "settingsProvider",
                "cacheProvider",
                "feedProvider",
            ),
        )

        (usages.map { it.kind }) shouldBe listOf(
                RiverpodUsageKind.WATCH,
                RiverpodUsageKind.NOTIFIER,
                RiverpodUsageKind.SELECT,
                RiverpodUsageKind.INVALIDATE,
                RiverpodUsageKind.FUTURE,
                RiverpodUsageKind.OVERRIDE,
                RiverpodUsageKind.DIRECT_CALL,
            )
        (usages.map { it.filePath }) shouldBe List(usages.size) { "lib/widget.dart" }
        (usages.map { it.line }) shouldBe listOf(1, 2, 3, 4, 5, 6, 7)
        (usages.map { it.textOffset }) shouldBe listOf(
                content.indexOf("userProvider"),
                content.indexOf("sessionProvider"),
                content.indexOf("settingsProvider"),
                content.indexOf("cacheProvider"),
                content.indexOf("feedProvider"),
                content.indexOf("userProvider.overrideWith"),
                content.indexOf("user();"),
            )
    }

    "주석, 문자열, 선언, 멤버 직접 호출은 무시한다" {
        val content = """
            // ref.watch(userProvider);
            final text = 'ref.read(userProvider)';

            @riverpod
            Future<User> user(Ref ref) async => User();

            final member = service.user();
            final spacedMember = service . user();
            final direct = user();
        """.trimIndent()

        val usages = ProviderUsageScanner.scan(
            filePath = "lib/user.dart",
            content = content,
            providerNames = setOf("userProvider"),
        )

        (usages.map { it.kind }) shouldBe listOf(RiverpodUsageKind.DIRECT_CALL)
        (usages.map { it.textOffset }) shouldBe listOf(content.lastIndexOf("user();"))
    }

    "직접 호출에 제공된 소스 이름을 사용한다" {
        val content = """
            final wrong = foo();
            final direct = fooNotifier();
        """.trimIndent()

        val usages = ProviderUsageScanner.scan(
            filePath = "lib/foo.dart",
            content = content,
            providerNames = setOf("fooNotifierProvider"),
            directCallSourceNamesByProvider = mapOf("fooNotifierProvider" to setOf("fooNotifier")),
        )

        (usages.map { it.kind }) shouldBe listOf(RiverpodUsageKind.DIRECT_CALL)
        (usages.map { it.textOffset }) shouldBe listOf(content.indexOf("fooNotifier();"))
    }

    "여러 줄 소스 선언 오프셋은 직접 호출에서 제외한다" {
        val content = """
            @riverpod
            Future<User>
            user(Ref ref) async => User();

            final direct = user();
        """.trimIndent()

        val usages = ProviderUsageScanner.scan(
            filePath = "lib/user.dart",
            content = content,
            providerNames = setOf("userProvider"),
            declarationOffsetsByProvider = mapOf("userProvider" to setOf(content.indexOf("user(Ref"))),
        )

        (usages.map { it.kind }) shouldBe listOf(RiverpodUsageKind.DIRECT_CALL)
        (usages.map { it.textOffset }) shouldBe listOf(content.lastIndexOf("user();"))
    }

    "타입 인자가 있는 ref 호출을 스캔한다" {
        val content = """
            final a = ref.watch<User>(userProvider);
            final b = ref.read<SessionNotifier>(sessionProvider.notifier);
            ref.listen<ThemeMode>(settingsProvider.select((s) => s.theme), (_, __) {});
            ref.invalidate<void>(cacheProvider);
            ref.refresh<Future<Feed>>(feedProvider.future);
        """.trimIndent()

        val usages = ProviderUsageScanner.scan(
            filePath = "lib/generic_widget.dart",
            content = content,
            providerNames = setOf(
                "userProvider",
                "sessionProvider",
                "settingsProvider",
                "cacheProvider",
                "feedProvider",
            ),
        )

        (usages.map { it.kind }) shouldBe listOf(
                RiverpodUsageKind.WATCH,
                RiverpodUsageKind.NOTIFIER,
                RiverpodUsageKind.SELECT,
                RiverpodUsageKind.INVALIDATE,
                RiverpodUsageKind.FUTURE,
            )
    }

    "ref 확장 멤버 사용을 스캔한다" {
        val content = """
            final user = ref.currentUser;
            await ref.reload();
            final wrongUser = profile.currentUser;
            await profile.reload();
            final text = 'ref.currentUser';
            // ref.reload();
        """.trimIndent()
        val extensionDependencies = listOf(
            RefExtensionDependency(
                memberId = "WatchUserX.currentUser",
                receiverType = "WidgetRef",
                providerNames = listOf("userProvider"),
                filePath = "lib/ref_x.dart",
                textOffset = 0,
            ),
            RefExtensionDependency(
                memberId = "WatchUserX.reload",
                receiverType = "WidgetRef",
                providerNames = listOf("feedProvider"),
                filePath = "lib/ref_x.dart",
                textOffset = 0,
            ),
        )

        val usages = ProviderUsageScanner.scan(
            filePath = "lib/widget.dart",
            content = content,
            providerNames = setOf("userProvider", "feedProvider"),
            extensionDependencies = extensionDependencies,
        )

        (usages.map { it.providerName }) shouldBe listOf("userProvider", "feedProvider")
        (usages.map { it.kind }) shouldBe listOf(RiverpodUsageKind.EXTENSION_MEMBER, RiverpodUsageKind.EXTENSION_MEMBER)
        (usages.map { it.marker }) shouldBe List(usages.size) { RiverpodMarker.REF_EXTENSION }
        (usages.map { it.textOffset }) shouldBe listOf(content.indexOf("currentUser"), content.indexOf("reload"))
    }
})
