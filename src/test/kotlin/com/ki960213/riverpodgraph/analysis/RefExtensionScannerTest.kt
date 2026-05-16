package com.ki960213.riverpodgraph.analysis

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class RefExtensionScannerTest : StringSpec({
    "화살표 ref 확장 멤버와 프로바이더 사용을 매핑한다" {
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

        (dependencies.map { it.memberId }) shouldBe listOf("WatchUserX.currentUser", "WatchUserX.reload")
        (dependencies.map { it.receiverType }) shouldBe listOf("WidgetRef", "WidgetRef")
        (dependencies.map { it.providerNames }) shouldBe listOf(listOf("userProvider"), listOf("feedProvider"))
        (dependencies.map { it.filePath }) shouldBe listOf("lib/ref_x.dart", "lib/ref_x.dart")
        (dependencies.map { it.textOffset }) shouldBe listOf(content.indexOf("currentUser"), content.indexOf("reload"))
        (dependencies.map { it.providerOffsetsByProvider }) shouldBe listOf(
                mapOf("userProvider" to listOf(content.indexOf("userProvider"))),
                mapOf("feedProvider" to listOf(content.indexOf("feedProvider"))),
            )
    }

    "블록 본문 ref 확장 멤버와 접두 nullable receiver를 스캔한다" {
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

        (dependencies.map { it.memberId }) shouldBe listOf("WatchUserX.currentUser", "WatchUserX.reload")
        (dependencies.map { it.receiverType }) shouldBe listOf("riverpod.WidgetRef?", "riverpod.WidgetRef?")
        (dependencies.map { it.providerNames }) shouldBe listOf(listOf("userProvider"), listOf("feedProvider", "cacheProvider"))
        (dependencies.map { it.textOffset }) shouldBe listOf(content.indexOf("currentUser"), content.indexOf("reload"))
        (dependencies.map { it.providerOffsetsByProvider }) shouldBe listOf(
                mapOf("userProvider" to listOf(content.indexOf("userProvider"))),
                mapOf(
                    "feedProvider" to listOf(content.indexOf("feedProvider")),
                    "cacheProvider" to listOf(content.indexOf("cacheProvider")),
                ),
            )
    }

    "다른 receiver의 watch 호출은 ref 의존성으로 보지 않는다" {
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

        (dependencies.map { it.memberId }) shouldBe listOf("WatchUserX.currentUser")
        (dependencies.map { it.providerOffsetsByProvider }) shouldBe listOf(mapOf("userProvider" to listOf(content.indexOf("userProvider"))))
    }

    "이름 없는 ref 확장을 receiver 기반 멤버 ID로 스캔한다" {
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

        (dependencies.map { it.memberId }) shouldBe listOf("WidgetRef.currentUser")
        (dependencies.map { it.receiverType }) shouldBe listOf("WidgetRef")
    }
})
