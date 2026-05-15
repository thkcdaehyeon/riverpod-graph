package com.ki960213.riverpodgraph.analysis

import com.ki960213.riverpodgraph.actions.ShowWidgetDependenciesAction
import com.ki960213.riverpodgraph.model.RiverpodMarker
import com.ki960213.riverpodgraph.model.RiverpodProviderDeclaration
import com.ki960213.riverpodgraph.model.RiverpodProviderKind
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetDependencyAnalyzerTest {
    @Test
    fun `finds provider usage and marked child widget candidates`() {
        val content = """
            class HomeScreen extends ConsumerWidget {
              @override
              Widget build(BuildContext context, WidgetRef ref) {
                final user = ref.watch(userProvider);
                return Column(children: [
                  if (user.isAdmin) AdminPanel(),
                  ListView.builder(itemBuilder: (context, index) => UserTile()),
                ]);
              }
            }
        """.trimIndent()

        val result = WidgetDependencyAnalyzer.analyze(
            filePath = "lib/home.dart",
            content = content,
            providerNames = setOf("userProvider"),
            depthLimit = 5,
        )

        assertEquals(listOf("userProvider"), result.providerNames)
        assertEquals(listOf("AdminPanel", "UserTile"), result.childWidgets.map { it.name })
        assertEquals(listOf(RiverpodMarker.CONDITIONAL, RiverpodMarker.CALLBACK), result.childWidgets.map { it.marker })
    }

    @Test
    fun `uses caret to select enclosing widget class`() {
        val content = """
            class FirstScreen extends ConsumerWidget {
              @override
              Widget build(BuildContext context, WidgetRef ref) {
                final user = ref.watch(firstProvider);
                return FirstPanel();
              }
            }

            class SecondScreen extends ConsumerWidget {
              @override
              Widget build(BuildContext context, WidgetRef ref) {
                final user = ref.watch(secondProvider);
                return SecondPanel();
              }
            }
        """.trimIndent()

        val result = WidgetDependencyAnalyzer.analyze(
            filePath = "lib/home.dart",
            content = content,
            providerNames = setOf("firstProvider", "secondProvider"),
            depthLimit = 5,
            caretOffset = content.indexOf("SecondPanel"),
        )

        assertEquals("SecondScreen", result.widgetName)
        assertEquals(listOf("secondProvider"), result.providerNames)
        assertEquals(listOf("SecondPanel"), result.childWidgets.map { it.name })
    }

    @Test
    fun `uses provider metadata for direct calls and ref extension members`() {
        val content = """
            extension WatchUserX on WidgetRef {
              User get currentUser => watch(userProvider);
            }

            class HomeScreen extends ConsumerWidget {
              @override
              Widget build(BuildContext context, WidgetRef ref) {
                final user = currentUserSource();
                final extensionUser = ref.currentUser;
                return UserPanel();
              }
            }
        """.trimIndent()
        val declaration = declaration(
            sourceName = "currentUserSource",
            providerName = "currentUserProvider",
            filePath = "lib/user.dart",
            textOffset = 0,
        )
        val extensionDependencies = RefExtensionScanner.scan(
            filePath = "lib/home.dart",
            content = content,
            providerNames = setOf("userProvider"),
        )

        val result = WidgetDependencyAnalyzer.analyze(
            filePath = "lib/home.dart",
            content = content,
            providerNames = setOf("currentUserProvider", "userProvider"),
            declarations = listOf(declaration),
            extensionDependencies = extensionDependencies,
            depthLimit = 5,
        )

        assertEquals(listOf("currentUserProvider", "userProvider"), result.providerNames)
    }

    @Test
    fun `uses external ref extension dependencies supplied by caller`() {
        val content = """
            class HomeScreen extends ConsumerWidget {
              @override
              Widget build(BuildContext context, WidgetRef ref) {
                final user = ref.currentUser;
                return UserPanel();
              }
            }
        """.trimIndent()
        val extensionContent = """
            extension WatchUserX on WidgetRef {
              User get currentUser => watch(userProvider);
            }
        """.trimIndent()
        val extensionDependencies = RefExtensionScanner.scan(
            filePath = "lib/ref_x.dart",
            content = extensionContent,
            providerNames = setOf("userProvider"),
        )

        val result = WidgetDependencyAnalyzer.analyze(
            filePath = "lib/home.dart",
            content = content,
            providerNames = setOf("userProvider"),
            extensionDependencies = extensionDependencies,
            depthLimit = 5,
        )

        assertEquals(listOf("userProvider"), result.providerNames)
    }

    @Test
    fun `does not use depth limit as direct child breadth cap`() {
        val content = """
            class HomeScreen extends ConsumerWidget {
              @override
              Widget build(BuildContext context, WidgetRef ref) {
                return Column(children: [
                  OnePanel(),
                  TwoPanel(),
                  ThreePanel(),
                  FourPanel(),
                  FivePanel(),
                  SixPanel(),
                  SevenPanel(),
                ]);
              }
            }
        """.trimIndent()

        val result = WidgetDependencyAnalyzer.analyze(
            filePath = "lib/home.dart",
            content = content,
            providerNames = emptySet(),
            depthLimit = 5,
        )

        assertEquals(
            listOf("OnePanel", "TwoPanel", "ThreePanel", "FourPanel", "FivePanel", "SixPanel", "SevenPanel"),
            result.childWidgets.map { it.name },
        )
    }

    @Test
    fun `does not bleed markers across sibling widgets`() {
        val content = """
            class HomeScreen extends ConsumerWidget {
              @override
              Widget build(BuildContext context, WidgetRef ref) {
                return Column(children: [
                  if (showAdmin) AdminPanel(),
                  PlainPanel(),
                  ListView.builder(itemBuilder: (context, index) => UserTile()),
                  PlainAfterCallback(),
                ]);
              }
            }
        """.trimIndent()

        val result = WidgetDependencyAnalyzer.analyze(
            filePath = "lib/home.dart",
            content = content,
            providerNames = emptySet(),
            depthLimit = 10,
        )

        assertEquals(
            listOf("AdminPanel", "PlainPanel", "UserTile", "PlainAfterCallback"),
            result.childWidgets.map { it.name },
        )
        assertEquals(
            listOf(RiverpodMarker.CONDITIONAL, null, RiverpodMarker.CALLBACK, null),
            result.childWidgets.map { it.marker },
        )
    }

    @Test
    fun `excludes current widget constructor and obvious non widget constructors`() {
        val content = """
            class HomeScreen extends ConsumerWidget {
              @override
              Widget build(BuildContext context, WidgetRef ref) {
                final now = DateTime.now();
                final pattern = RegExp('home');
                return Column(children: [
                  HomeScreen(),
                  UserTile(),
                  Object(),
                ]);
              }
            }
        """.trimIndent()

        val result = WidgetDependencyAnalyzer.analyze(
            filePath = "lib/home.dart",
            content = content,
            providerNames = emptySet(),
            depthLimit = 10,
        )

        assertEquals(listOf("UserTile"), result.childWidgets.map { it.name })
    }

    @Test
    fun `action fallback provider scan ignores comments and strings`() {
        val content = """
            // ref.watch(commentedProvider);
            final text = 'stringProvider';
            final user = ref.watch(realProvider);
        """.trimIndent()

        assertEquals(setOf("realProvider"), ShowWidgetDependenciesAction.fallbackProviderNames(content))
    }

    private fun declaration(
        sourceName: String,
        providerName: String,
        filePath: String,
        textOffset: Int,
    ): RiverpodProviderDeclaration = RiverpodProviderDeclaration(
        kind = RiverpodProviderKind.FUNCTION,
        sourceName = sourceName,
        providerName = providerName,
        generatedSuperclassName = null,
        returnType = "Object",
        familySignature = "Ref ref",
        keepAlive = false,
        isPrivate = false,
        filePath = filePath,
        textOffset = textOffset,
        line = 1,
    )
}
