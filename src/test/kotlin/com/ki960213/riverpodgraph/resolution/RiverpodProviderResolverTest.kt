package com.ki960213.riverpodgraph.resolution

import com.ki960213.riverpodgraph.index.RiverpodProviderIndexValue
import com.ki960213.riverpodgraph.index.providerDeclarationsFromIndexValues
import com.ki960213.riverpodgraph.model.RiverpodProviderKind
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class RiverpodProviderResolverTest : StringSpec({
    "중복 선언을 파일 경로와 오프셋 순으로 정렬한다" {
        val sorted = providerDeclarationsFromIndexValues(
            listOf(
                value(sourceName = "ZUser", filePath = "lib/z_user.dart", textOffset = 3),
                value(sourceName = "AUserLate", filePath = "lib/a_user.dart", textOffset = 42),
                value(sourceName = "AUserEarly", filePath = "lib/a_user.dart", textOffset = 7),
            ),
        )

        (sorted.map { it.sourceName }) shouldBe listOf("AUserEarly", "AUserLate", "ZUser")
    }

})

/** 테스트용 인덱스 값을 지정한 파일 경로와 오프셋으로 생성합니다. */
private fun value(
    sourceName: String,
    filePath: String,
    textOffset: Int,
): RiverpodProviderIndexValue = RiverpodProviderIndexValue(
    kind = RiverpodProviderKind.FUNCTION,
    sourceName = sourceName,
    providerName = "userProvider",
    generatedSuperclassName = null,
    returnType = "User",
    familySignature = "",
    keepAlive = false,
    isPrivate = false,
    filePath = filePath,
    textOffset = textOffset,
    line = 1,
)
