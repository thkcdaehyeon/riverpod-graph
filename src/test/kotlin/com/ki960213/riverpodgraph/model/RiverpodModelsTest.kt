package com.ki960213.riverpodgraph.model

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class RiverpodModelsTest : StringSpec({
    "프로바이더 선언은 모델 필드를 저장한다" {
        val declaration = RiverpodProviderDeclaration(
            kind = RiverpodProviderKind.FUNCTION,
            sourceName = "user",
            providerName = "userProvider",
            generatedSuperclassName = null,
            returnType = "String",
            familySignature = "",
            keepAlive = false,
            isPrivate = false,
            filePath = "lib/user.dart",
            textOffset = 0,
            line = 1,
        )

        (declaration.kind) shouldBe RiverpodProviderKind.FUNCTION
        (declaration.returnType) shouldBe "String"
        (declaration.familySignature) shouldBe ""
        (declaration.generatedSuperclassName) shouldBe null
    }

    "사용과 의존성 간선 마커 기본값은 null이다" {
        val usage = RiverpodProviderUsage(
            providerName = "userProvider",
            kind = RiverpodUsageKind.WATCH,
            filePath = "lib/user.dart",
            textOffset = 10,
            line = 2,
        )
        val edge = RiverpodDependencyEdge(
            fromProvider = "profileProvider",
            toProvider = "userProvider",
            usageKind = RiverpodUsageKind.WATCH,
        )

        (usage.marker) shouldBe null
        (edge.marker) shouldBe null
    }
})
