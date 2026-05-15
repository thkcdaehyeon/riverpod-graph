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

    fun testRedirectsProviderSymbolToSourceDeclaration() {
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

    fun testRedirectsProviderFutureModifierToSourceDeclaration() {
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

    fun testRedirectsProviderNotifierModifierToSourceDeclaration() {
        myFixture.configureByText(
            "session.dart",
            """
            import riverpod_annotation;

            part 'session.g.dart';

            @riverpod
            class SessionController extends _${'$'}SessionController {
              String build() => 'Ada';
            }

            final value = ref.watch(sessionControllerProvider.notifier<caret>);
            """.trimIndent(),
        )

        val targets = targetsAtCaret()

        assertNotNull(targets)
        assertEquals("SessionController", targets!!.single().text)
    }

    fun testRedirectsGeneratedSuperclassToSourceClassDeclaration() {
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

    fun testReturnsNullOutsideDartFiles() {
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

    fun testReturnsAllDuplicateProviderTargets() {
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

    fun testMergeKeepsCurrentFileDuplicateTargetsWhenIndexHasOne() {
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

    private fun targetsAtCaret(): Array<com.intellij.psi.PsiElement>? {
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
