package com.ki960213.riverpodgraph.ui

import com.ki960213.riverpodgraph.index.RiverpodProviderIndexValue
import com.ki960213.riverpodgraph.index.providerDeclarationsFromIndexValues
import com.ki960213.riverpodgraph.model.RiverpodProviderKind
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class RiverpodToolWindowFactoryTest : StringSpec({
    "도구 창 콘텐츠는 프로바이더와 의존성 탭 구성을 소유한다" {
        val content = RiverpodToolWindowContent()

        (content.tabTitles()) shouldBe listOf("Providers", "Dependencies")
    }

    "인덱스 값에서 프로바이더 경로와 오프셋으로 중복 제거 후 정렬한다" {
        val declarations = providerDeclarationsFromIndexValues(
            listOf(
                value(providerName = "zProvider", filePath = "lib/z.dart", textOffset = 4),
                value(providerName = "aProvider", sourceName = "a", filePath = "lib/a.dart", textOffset = 8),
                value(providerName = "aProvider", sourceName = "A", filePath = "lib/a.dart", textOffset = 8),
                value(providerName = "aProvider", filePath = "lib/a.dart", textOffset = 2),
            ),
        )

        (declarations.map { "${it.providerName}:${it.filePath}:${it.textOffset}" }) shouldBe listOf(
            "aProvider:lib/a.dart:2",
            "aProvider:lib/a.dart:8",
            "zProvider:lib/z.dart:4"
        )
    }

})

/** 도구 창 로딩 테스트에 사용할 프로바이더 인덱스 값을 생성합니다. */
private fun value(
    providerName: String,
    sourceName: String = providerName.removeSuffix("Provider"),
    filePath: String,
    textOffset: Int,
): RiverpodProviderIndexValue = RiverpodProviderIndexValue(
    kind = RiverpodProviderKind.FUNCTION,
    sourceName = sourceName,
    providerName = providerName,
    generatedSuperclassName = null,
    returnType = "String",
    familySignature = "",
    keepAlive = false,
    isPrivate = false,
    filePath = filePath,
    textOffset = textOffset,
    line = 1,
)
