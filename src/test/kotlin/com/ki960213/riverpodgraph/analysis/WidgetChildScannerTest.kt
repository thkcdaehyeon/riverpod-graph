package com.ki960213.riverpodgraph.analysis

import com.ki960213.riverpodgraph.dart.dartCodeMask
import com.ki960213.riverpodgraph.model.RiverpodMarker
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class WidgetChildScannerTest : StringSpec({
    "위젯 생성자 후보와 문맥 마커를 스캔한다" {
        val content = """
            return Column(children: [
              if (showAdmin) AdminPanel(),
              PlainPanel(),
              ListView.builder(itemBuilder: (context, index) => UserTile()),
              // CommentedPanel(),
              finalText('StringPanel()'),
            ]);
        """.trimIndent()

        val children = WidgetChildScanner.scan(
            content = content,
            codeMask = dartCodeMask(content),
            scanRange = content.indices,
            widgetName = "HomeScreen",
        )

        (children.map { it.name }) shouldBe listOf("AdminPanel", "PlainPanel", "UserTile")
        (children.map { it.marker }) shouldBe listOf(RiverpodMarker.CONDITIONAL, null, RiverpodMarker.CALLBACK)
    }

    "현재 위젯 생성자와 명백한 비위젯 생성자를 제외한다" {
        val content = """
            class HomeScreen extends ConsumerWidget {
              const HomeScreen();
              Widget build(BuildContext context, WidgetRef ref) {
                final now = DateTime.now();
                return Column(children: [HomeScreen(), UserTile()]);
              }
            }
        """.trimIndent()

        val children = WidgetChildScanner.scan(
            content = content,
            codeMask = dartCodeMask(content),
            scanRange = content.indices,
            widgetName = "HomeScreen",
        )

        (children.map { it.name }) shouldBe listOf("UserTile")
    }
})
