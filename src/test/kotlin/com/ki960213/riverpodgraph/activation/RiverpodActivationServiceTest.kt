package com.ki960213.riverpodgraph.activation

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class RiverpodActivationServiceTest : BasePlatformTestCase() {
    fun `test riverpod_annotation 의존성이 없으면 파일은 비활성이다`() {
        myFixture.configureByText(
            "user.dart",
            """
            @riverpod
            String user(Ref ref) => 'Ada';
            """.trimIndent(),
        )

        assertFalse(RiverpodActivationService.getInstance(project).isFileActive(myFixture.file))
    }
}
