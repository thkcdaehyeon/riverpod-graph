package com.ki960213.riverpodgraph.ui

import com.ki960213.riverpodgraph.model.RiverpodProviderDeclaration
import com.ki960213.riverpodgraph.model.RiverpodProviderKind
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import javax.swing.SwingUtilities

class ProvidersPanelTest : StringSpec({
    "프로바이더 행에 시그니처, 반환 타입, keepAlive, 경로와 줄을 표시한다" {
        val declaration = RiverpodProviderDeclaration(
            kind = RiverpodProviderKind.FUNCTION,
            sourceName = "user",
            providerName = "userProvider",
            generatedSuperclassName = null,
            returnType = "Future<User>",
            familySignature = "Ref ref, String id",
            keepAlive = true,
            isPrivate = false,
            filePath = "lib/user.dart",
            textOffset = 0,
            line = 5,
        )

        (ProvidersPanel.providerRow(declaration)) shouldBe "userProvider(Ref ref, String id) : Future<User> keepAlive - lib/user.dart:5"
    }

    "private 프로바이더 행은 시그니처와 keepAlive 없이 표시한다" {
        val declaration = declaration(
            providerName = "_secretProvider",
            returnType = "int",
            familySignature = "",
            keepAlive = false,
            isPrivate = true,
            filePath = "lib/secret.dart",
            line = 9,
        )

        (ProvidersPanel.providerRow(declaration)) shouldBe "private _secretProvider : int - lib/secret.dart:9"
    }

    "setProviders는 파일 경로와 줄 순서로 프로바이더를 정렬한다" {
        val panel = ProvidersPanel()

        panel.setProviders(
            listOf(
                declaration(providerName = "lateProvider", filePath = "lib/z.dart", line = 1),
                declaration(providerName = "secondProvider", filePath = "lib/a.dart", line = 20),
                declaration(providerName = "firstProvider", filePath = "lib/a.dart", line = 2),
            ),
        )
        SwingUtilities.invokeAndWait {}

        (panel.providerRows()) shouldBe listOf(
            "firstProvider : String - lib/a.dart:2",
            "secondProvider : String - lib/a.dart:20",
            "lateProvider : String - lib/z.dart:1",
        )
    }

})

/** ProvidersPanel 테스트에 사용할 프로바이더 선언 값을 생성합니다. */
private fun declaration(
    providerName: String = "userProvider",
    returnType: String = "String",
    familySignature: String = "",
    keepAlive: Boolean = false,
    isPrivate: Boolean = false,
    filePath: String = "lib/user.dart",
    line: Int = 1,
    textOffset: Int = 0,
): RiverpodProviderDeclaration = RiverpodProviderDeclaration(
    kind = RiverpodProviderKind.FUNCTION,
    sourceName = providerName.removeSuffix("Provider"),
    providerName = providerName,
    generatedSuperclassName = null,
    returnType = returnType,
    familySignature = familySignature,
    keepAlive = keepAlive,
    isPrivate = isPrivate,
    filePath = filePath,
    textOffset = textOffset,
    line = line,
)
