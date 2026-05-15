package com.ki960213.riverpodgraph.activation

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class RiverpodActivationServiceTest : BasePlatformTestCase() {
    fun testFileInactiveWithoutRiverpodAnnotationDependency() {
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
