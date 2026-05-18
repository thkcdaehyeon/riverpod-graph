package com.ki960213.riverpodgraph.search

import com.intellij.psi.PsiReference
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.Processor
import com.ki960213.riverpodgraph.activation.RiverpodActiveSourceScope

class RiverpodReferencesSearchExecutorPlatformTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.addFileToProject(
            "pubspec.yaml",
            """
            name: test_app
            dependencies:
              riverpod_annotation: ^3.0.0
            """.trimIndent(),
        )
    }

    fun `test Find Usages from source provider declaration includes generated provider references`() {
        myFixture.addFileToProject(
            "lib/home.dart",
            """
            class HomeScreen extends ConsumerWidget {
              Widget build(BuildContext context, WidgetRef ref) {
                return Text(ref.watch(userProvider));
              }
            }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "user.dart",
            """
            import riverpod_annotation;

            part 'user.g.dart';

            @riverpod
            String us<caret>er(Ref ref) => 'Ada';
            """.trimIndent(),
        )

        val sourceElement = requireNotNull(myFixture.file.findElementAt(myFixture.caretOffset)) {
            "No PSI element at caret"
        }
        val references = mutableListOf<PsiReference>()
        RiverpodReferencesSearchExecutor().processQuery(
            ReferencesSearch.SearchParameters(
                sourceElement,
                GlobalSearchScope.projectScope(project),
                false,
            ),
            Processor { reference ->
                references += reference
                true
            },
        )
        val providers = RiverpodActiveSourceScope.getInstance(project).providerDeclarations()

        val reference = references.filterIsInstance<RiverpodReference>().singleOrNull {
            it.providerName == "userProvider"
        }
        assertNotNull(
            "element=${sourceElement.text}, providers=${providers.map { it.providerName }}, usages=${references.map { it.element.text }}",
            reference,
        )
        assertEquals("userProvider", reference!!.rangeInElement.substring(reference.element.text))
    }
}
