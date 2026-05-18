package com.ki960213.riverpodgraph.activation

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class RiverpodActiveSourceScopePlatformTest : BasePlatformTestCase() {
    fun `test 비활성 프로젝트의 Dart 파일과 Provider 선언은 활성 범위에서 제외된다`() {
        myFixture.addFileToProject(
            "lib/user.dart",
            """
            @riverpod
            String user(Ref ref) => 'Ada';
            """.trimIndent(),
        )

        val activeScope = RiverpodActiveSourceScope.getInstance(project)

        assertTrue(activeScope.activeDartFiles().isEmpty())
        assertTrue(activeScope.providerDeclarations().isEmpty())
    }

    fun `test 활성 프로젝트의 Dart 파일과 Provider 선언은 활성 범위에 포함된다`() {
        myFixture.addFileToProject(
            "pubspec.yaml",
            """
            name: test_app
            dependencies:
              riverpod_annotation: ^3.0.0
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "lib/user.dart",
            """
            @riverpod
            String user(Ref ref) => 'Ada';
            """.trimIndent(),
        )

        val activeScope = RiverpodActiveSourceScope.getInstance(project)

        assertEquals(listOf("user.dart"), activeScope.activeDartFiles().map { it.name })
        assertEquals(listOf("userProvider"), activeScope.providerDeclarations().map { it.providerName })
    }
}
