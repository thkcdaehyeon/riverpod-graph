package com.ki960213.riverpodgraph.analysis

import org.junit.Assert.assertEquals
import org.junit.Test

class RefExtensionScannerTest {
    @Test
    fun `scans ref extension arrow members and maps provider usages`() {
        val content = """
            extension WatchUserX on WidgetRef {
              User get currentUser => watch(userProvider).requireValue;
              Future<void> reload() => refresh(feedProvider.future);
            }
        """.trimIndent()

        val dependencies = RefExtensionScanner.scan(
            filePath = "lib/ref_x.dart",
            content = content,
            providerNames = setOf("userProvider", "feedProvider"),
        )

        assertEquals(listOf("WatchUserX.currentUser", "WatchUserX.reload"), dependencies.map { it.memberId })
        assertEquals(listOf("WidgetRef", "WidgetRef"), dependencies.map { it.receiverType })
        assertEquals(listOf(listOf("userProvider"), listOf("feedProvider")), dependencies.map { it.providerNames })
        assertEquals(listOf("lib/ref_x.dart", "lib/ref_x.dart"), dependencies.map { it.filePath })
        assertEquals(listOf(content.indexOf("currentUser"), content.indexOf("reload")), dependencies.map { it.textOffset })
        assertEquals(
            listOf(
                mapOf("userProvider" to listOf(content.indexOf("userProvider"))),
                mapOf("feedProvider" to listOf(content.indexOf("feedProvider"))),
            ),
            dependencies.map { it.providerOffsetsByProvider },
        )
    }

    @Test
    fun `scans block-bodied ref extension members and prefixed nullable receiver types`() {
        val content = """
            extension WatchUserX on riverpod.WidgetRef? {
              User get currentUser {
                return watch(userProvider).requireValue;
              }

              Future<void> reload() async {
                await refresh(feedProvider.future);
                ref.invalidate(cacheProvider);
              }
            }
        """.trimIndent()

        val dependencies = RefExtensionScanner.scan(
            filePath = "lib/ref_x.dart",
            content = content,
            providerNames = setOf("userProvider", "feedProvider", "cacheProvider"),
        )

        assertEquals(listOf("WatchUserX.currentUser", "WatchUserX.reload"), dependencies.map { it.memberId })
        assertEquals(listOf("riverpod.WidgetRef?", "riverpod.WidgetRef?"), dependencies.map { it.receiverType })
        assertEquals(
            listOf(listOf("userProvider"), listOf("feedProvider", "cacheProvider")),
            dependencies.map { it.providerNames },
        )
        assertEquals(listOf(content.indexOf("currentUser"), content.indexOf("reload")), dependencies.map { it.textOffset })
        assertEquals(
            listOf(
                mapOf("userProvider" to listOf(content.indexOf("userProvider"))),
                mapOf(
                    "feedProvider" to listOf(content.indexOf("feedProvider")),
                    "cacheProvider" to listOf(content.indexOf("cacheProvider")),
                ),
            ),
            dependencies.map { it.providerOffsetsByProvider },
        )
    }

    @Test
    fun `does not treat other receiver watch calls as ref dependencies`() {
        val content = """
            extension WatchUserX on WidgetRef {
              User get currentUser => watch(userProvider).requireValue;
              User get otherUser => foo.watch(userProvider).requireValue;
              User get spacedOtherUser => foo . watch(userProvider).requireValue;
            }
        """.trimIndent()

        val dependencies = RefExtensionScanner.scan(
            filePath = "lib/ref_x.dart",
            content = content,
            providerNames = setOf("userProvider"),
        )

        assertEquals(listOf("WatchUserX.currentUser"), dependencies.map { it.memberId })
        assertEquals(
            listOf(mapOf("userProvider" to listOf(content.indexOf("userProvider")))),
            dependencies.map { it.providerOffsetsByProvider },
        )
    }

    @Test
    fun `scans unnamed ref extensions with receiver-based member ids`() {
        val content = """
            extension on WidgetRef {
              User get currentUser => watch(userProvider).requireValue;
            }
        """.trimIndent()

        val dependencies = RefExtensionScanner.scan(
            filePath = "lib/ref_x.dart",
            content = content,
            providerNames = setOf("userProvider"),
        )

        assertEquals(listOf("WidgetRef.currentUser"), dependencies.map { it.memberId })
        assertEquals(listOf("WidgetRef"), dependencies.map { it.receiverType })
    }
}
