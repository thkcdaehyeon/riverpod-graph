package com.ki960213.riverpodgraph.search

import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.ki960213.riverpodgraph.test.RiverpodFixtureBase

class RiverpodFindUsagesFixtureTest : RiverpodFixtureBase() {
    fun `test find usages from source function includes consumer widget watch in another file`() {
        loadFixture("riverpod/basic")
        val sourceFile = myFixture.configureByFile("lib/providers/user_providers.dart")
        val function = sourceFile.findElementAt(sourceFile.text.indexOf("currentUser(Ref").also { check(it >= 0) })!!

        val usages = ReferencesSearch.search(function, GlobalSearchScope.projectScope(project)).findAll()

        assertTrue(
            "Expected home_page.dart usage for currentUser, got: ${usages.map { it.element.containingFile?.name to it.element.text }}",
            usages.any { it.element.containingFile?.virtualFile?.path?.endsWith("lib/features/home/home_page.dart") == true },
        )
    }

    fun `test find usages from generated provider symbol matches source declaration results`() {
        loadFixture("riverpod/basic")
        val sourceFile = myFixture.configureByFile("lib/providers/user_providers.dart")
        val function = sourceFile.findElementAt(sourceFile.text.indexOf("profile(Ref").also { check(it >= 0) })!!
        val sourceUsages = ReferencesSearch.search(function, GlobalSearchScope.projectScope(project))
            .findAll()
            .map { it.element.containingFile?.virtualFile?.path to it.rangeInElement.substring(it.element.text) }
            .toSet()

        val generatedFile = myFixture.configureByFile("lib/providers/user_providers.g.dart")
        val providerOffset = generatedFile.text.indexOf("profileProvider").also { check(it >= 0) }
        val generatedSymbol = generatedFile.findElementAt(providerOffset)!!
        val generatedUsages = ReferencesSearch.search(generatedSymbol, GlobalSearchScope.projectScope(project))
            .findAll()
            .map { it.element.containingFile?.virtualFile?.path to it.rangeInElement.substring(it.element.text) }
            .toSet()

        assertEquals(sourceUsages, generatedUsages)
        assertTrue(
            "Expected profileProvider usage from home_page.dart, got: $generatedUsages",
            generatedUsages.any { (path, text) -> path?.endsWith("lib/features/home/home_page.dart") == true && text == "profileProvider" },
        )
    }

    fun `test find usages from notifier source class includes notifier provider usage`() {
        loadFixture("riverpod/basic")
        val sourceFile = myFixture.configureByFile("lib/providers/session_controller.dart")
        val clazz = sourceFile.findElementAt(sourceFile.text.indexOf("SessionController extends").also { check(it >= 0) })!!

        val usages = ReferencesSearch.search(clazz, GlobalSearchScope.projectScope(project)).findAll()

        assertTrue(
            "Expected sessionControllerProvider.notifier usage, got: ${usages.map { it.element.containingFile?.name to it.element.text }}",
            usages.any { usage ->
                usage.element.containingFile?.virtualFile?.path?.endsWith("lib/features/home/home_page.dart") == true &&
                    usage.rangeInElement.substring(usage.element.text) == "sessionControllerProvider"
            },
        )
    }
}
