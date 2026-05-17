package com.ki960213.riverpodgraph.navigation

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.FakePsiElement
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull

class RiverpodGotoDeclarationHandlerTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        addRiverpodPubspec()
    }

    fun `test 프로바이더 심볼을 원본 선언으로 이동한다`() {
        myFixture.configureByText(
            "user.dart",
            """
            import riverpod_annotation;

            part 'user.g.dart';

            @riverpod
            String user(Ref ref) => 'Ada';

            final value = ref.watch(userProvider<caret>);
            """.trimIndent(),
        )

        val handler = RiverpodGotoDeclarationHandler()
        val source = myFixture.file.findElementAt(myFixture.caretOffset - 1)
        val targets = handler.getGotoDeclarationTargets(source, myFixture.caretOffset, myFixture.editor)

        assertNotNull(targets)
        assertEquals("user", targets!!.single().text)
    }

    fun `test Future 수정자를 원본 선언으로 이동한다`() {
        myFixture.configureByText(
            "user.dart",
            """
            import riverpod_annotation;

            part 'user.g.dart';

            @riverpod
            Future<String> user(Ref ref) async => 'Ada';

            final value = ref.watch(userProvider.future<caret>);
            """.trimIndent(),
        )

        val targets = targetsAtCaret()

        assertNotNull(targets)
        assertEquals("user", targets!!.single().text)
    }

    fun `test Notifier 수정자를 원본 선언으로 이동한다`() {
        myFixture.configureByText(
            "session.dart",
            $$"""
            import riverpod_annotation;

            part 'session.g.dart';

            @riverpod
            class SessionController extends _$SessionController {
              String build() => 'Ada';
            }

            final value = ref.watch(sessionControllerProvider.notifier<caret>);
            """.trimIndent(),
        )

        val targets = targetsAtCaret()

        assertNotNull(targets)
        assertEquals("SessionController", targets!!.single().text)
    }

    fun `test 생성된 상위 클래스를 원본 클래스로 이동한다`() {
        myFixture.configureByText(
            "session.dart",
            """
            import riverpod_annotation;

            part 'session.g.dart';

            @riverpod
            class SessionController extends _${'$'}SessionController<caret> {
              String build() => 'Ada';
            }
            """.trimIndent(),
        )

        val targets = targetsAtCaret()

        assertNotNull(targets)
        assertEquals("SessionController", targets!!.single().text)
    }

    fun `test Dart 파일 밖에서는 null을 반환한다`() {
        myFixture.configureByText(
            "foo.txt",
            """
            @riverpod
            String user(Ref ref) => 'Ada';

            final value = ref.watch(userProvider<caret>);
            """.trimIndent(),
        )

        val targets = targetsAtCaret()

        assertNull(targets)
    }

    fun `test 중복 프로바이더 대상을 모두 반환한다`() {
        myFixture.configureByText(
            "user.dart",
            """
            import riverpod_annotation;

            part 'user.g.dart';

            @riverpod
            String user(Ref ref) => 'Ada';

            @riverpod
            class User extends _${'$'}User {
              String build() => 'Grace';
            }

            final value = ref.watch(userProvider<caret>);
            """.trimIndent(),
        )

        val targets = targetsAtCaret()

        assertNotNull(targets)
        assertEquals(listOf("user", "User"), targets!!.map { it.text })
    }

    fun `test 인덱스 대상이 하나여도 현재 파일의 중복 대상을 유지한다`() {
        myFixture.configureByText(
            "user.dart",
            """
            @riverpod
            String user(Ref ref) => 'Ada';

            @riverpod
            class User extends _${'$'}User {
              String build() => 'Grace';
            }
            """.trimIndent(),
        )
        val first = target("user", 18, myFixture.file)
        val duplicate = target("User", 62, myFixture.file)

        val merged = mergeCurrentFileTargets(
            currentFileTargets = listOf(first, duplicate),
            indexedTargets = listOf(first),
            containingFile = myFixture.file,
        )

        assertEquals(listOf("user", "User"), merged.map { it.text })
    }

    private fun targetsAtCaret(): Array<PsiElement>? {
        val handler = RiverpodGotoDeclarationHandler()
        val source = myFixture.file.findElementAt(myFixture.caretOffset - 1)
        return handler.getGotoDeclarationTargets(source, myFixture.caretOffset, myFixture.editor)
    }

    private fun target(text: String, offset: Int, file: PsiFile): PsiElement = object : FakePsiElement() {
        override fun getParent(): PsiElement = file
        override fun getContainingFile(): PsiFile = file
        override fun getText(): String = text
        override fun getTextOffset(): Int = offset
        override fun getTextRange(): TextRange = TextRange.from(offset, text.length)
    }

    private fun addRiverpodPubspec() {
        myFixture.addFileToProject(
            "pubspec.yaml",
            """
            name: test_app
            dependencies:
              riverpod_annotation: ^3.0.0
            """.trimIndent(),
        )
    }
}
