package com.ki960213.riverpodgraph.analysis

import com.ki960213.riverpodgraph.model.RiverpodUsageKind
import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderUsageScannerTest {
    @Test
    fun `scans common ref calls provider modifiers overrides and direct calls`() {
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

        assertEquals(
            listOf(
                RiverpodUsageKind.WATCH,
                RiverpodUsageKind.NOTIFIER,
                RiverpodUsageKind.SELECT,
                RiverpodUsageKind.INVALIDATE,
                RiverpodUsageKind.FUTURE,
                RiverpodUsageKind.OVERRIDE,
                RiverpodUsageKind.DIRECT_CALL,
            ),
            usages.map { it.kind },
        )
        assertEquals(List(usages.size) { "lib/widget.dart" }, usages.map { it.filePath })
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7), usages.map { it.line })
        assertEquals(
            listOf(
                content.indexOf("userProvider"),
                content.indexOf("sessionProvider"),
                content.indexOf("settingsProvider"),
                content.indexOf("cacheProvider"),
                content.indexOf("feedProvider"),
                content.indexOf("userProvider.overrideWith"),
                content.indexOf("user();"),
            ),
            usages.map { it.textOffset },
        )
    }

    @Test
    fun `ignores comments strings declarations and member direct calls`() {
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

        assertEquals(listOf(RiverpodUsageKind.DIRECT_CALL), usages.map { it.kind })
        assertEquals(listOf(content.lastIndexOf("user();")), usages.map { it.textOffset })
    }

    @Test
    fun `uses supplied source names for direct calls`() {
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

        assertEquals(listOf(RiverpodUsageKind.DIRECT_CALL), usages.map { it.kind })
        assertEquals(listOf(content.indexOf("fooNotifier();")), usages.map { it.textOffset })
    }

    @Test
    fun `excludes multiline source declaration offset from direct calls`() {
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

        assertEquals(listOf(RiverpodUsageKind.DIRECT_CALL), usages.map { it.kind })
        assertEquals(listOf(content.lastIndexOf("user();")), usages.map { it.textOffset })
    }

    @Test
    fun `scans ref calls with type arguments`() {
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

        assertEquals(
            listOf(
                RiverpodUsageKind.WATCH,
                RiverpodUsageKind.NOTIFIER,
                RiverpodUsageKind.SELECT,
                RiverpodUsageKind.INVALIDATE,
                RiverpodUsageKind.FUTURE,
            ),
            usages.map { it.kind },
        )
    }
}
