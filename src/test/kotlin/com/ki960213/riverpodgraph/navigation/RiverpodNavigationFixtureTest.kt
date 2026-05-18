package com.ki960213.riverpodgraph.navigation

import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction
import com.intellij.openapi.application.WriteAction
import com.ki960213.riverpodgraph.test.RiverpodFixtureBase

class RiverpodNavigationFixtureTest : RiverpodFixtureBase() {
    fun `test cmd click from consumer widget provider usage lands on source function in another file`() {
        loadFixture("riverpod/basic")
        val homeFile = myFixture.configureByFile("lib/features/home/home_page.dart")
        val offset = homeFile.text.indexOf("currentUserProvider").also { check(it >= 0) } + 5
        myFixture.editor.caretModel.moveToOffset(offset)

        val targets = GotoDeclarationAction.findAllTargetElements(project, myFixture.editor, offset)

        assertNotNull("Expected goto targets for currentUserProvider", targets)
        assertTrue(
            "Expected currentUser source target, got: ${targets!!.map { targetLabel(it) }}",
            targets.any { it.containingFile?.virtualFile?.path?.endsWith("lib/providers/user_providers.dart") == true && it.text == "currentUser" },
        )
    }

    fun `test cmd click still lands on source when generated file is removed`() {
        loadFixture("riverpod/basic")
        val generated = myFixture.findFileInTempDir("lib/providers/user_providers.g.dart")
        assertNotNull("Fixture should include generated user_providers.g.dart", generated)
        WriteAction.run<RuntimeException> { generated!!.delete(this) }

        val homeFile = myFixture.configureByFile("lib/features/home/home_page.dart")
        val offset = homeFile.text.indexOf("profileProvider").also { check(it >= 0) } + 5
        myFixture.editor.caretModel.moveToOffset(offset)

        val targets = GotoDeclarationAction.findAllTargetElements(project, myFixture.editor, offset)

        assertNotNull("Expected goto targets for profileProvider without .g.dart", targets)
        assertTrue(
            "Expected profile source target, got: ${targets!!.map { targetLabel(it) }}",
            targets.any { it.containingFile?.virtualFile?.path?.endsWith("lib/providers/user_providers.dart") == true && it.text == "profile" },
        )
    }

    fun `test cmd click on generated superclass lands on notifier source class`() {
        loadFixture("riverpod/basic")
        val file = myFixture.configureByFile("lib/providers/session_controller.dart")
        val offset = file.text.indexOf("_\$SessionController").also { check(it >= 0) } + 3
        myFixture.editor.caretModel.moveToOffset(offset)

        val targets = GotoDeclarationAction.findAllTargetElements(project, myFixture.editor, offset)

        assertNotNull("Expected goto targets for generated superclass", targets)
        assertTrue(
            "Expected SessionController class target, got: ${targets!!.map { targetLabel(it) }}",
            targets.any { it.containingFile?.virtualFile?.path?.endsWith("lib/providers/session_controller.dart") == true && it.text == "SessionController" },
        )
    }

    private fun targetLabel(element: com.intellij.psi.PsiElement): String =
        "${element.text}@${element.containingFile?.virtualFile?.path}:${element.textOffset}"
}
