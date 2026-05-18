package com.ki960213.riverpodgraph.analysis

import com.ki960213.riverpodgraph.actions.fallbackProviderNames
import com.ki960213.riverpodgraph.model.RiverpodMarker
import com.ki960213.riverpodgraph.model.RiverpodProviderDeclaration
import com.ki960213.riverpodgraph.model.RiverpodProviderKind
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class WidgetDependencyAnalyzerTest : StringSpec({
    "프로바이더 사용과 표시된 자식 위젯 후보를 찾는다" {
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
        )

        (result.providerNames) shouldBe listOf("userProvider")
        (result.childWidgets.map { it.name }) shouldBe listOf("AdminPanel", "UserTile")
        (result.childWidgets.map { it.marker }) shouldBe listOf(RiverpodMarker.CONDITIONAL, RiverpodMarker.CALLBACK)
    }

    "캐럿으로 감싼 위젯 클래스를 선택한다" {
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
            caretOffset = content.indexOf("SecondPanel"),
        )

        (result.widgetName) shouldBe "SecondScreen"
        (result.providerNames) shouldBe listOf("secondProvider")
        (result.childWidgets.map { it.name }) shouldBe listOf("SecondPanel")
    }

    "직접 호출과 ref 확장 멤버에 프로바이더 메타데이터를 사용한다" {
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
        )

        (result.providerNames) shouldBe listOf("currentUserProvider", "userProvider")
    }

    "호출자가 제공한 외부 ref 확장 의존성을 사용한다" {
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
        )

        (result.providerNames) shouldBe listOf("userProvider")
    }

    "깊이 제한을 직접 자식 너비 제한으로 쓰지 않는다" {
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
        )

        (result.childWidgets.map { it.name }) shouldBe listOf(
            "OnePanel",
            "TwoPanel",
            "ThreePanel",
            "FourPanel",
            "FivePanel",
            "SixPanel",
            "SevenPanel"
        )
    }

    "형제 위젯 사이로 마커가 번지지 않는다" {
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
        )

        (result.childWidgets.map { it.name }) shouldBe listOf(
            "AdminPanel",
            "PlainPanel",
            "UserTile",
            "PlainAfterCallback"
        )
        (result.childWidgets.map { it.marker }) shouldBe listOf(
            RiverpodMarker.CONDITIONAL,
            null,
            RiverpodMarker.CALLBACK,
            null
        )
    }

    "현재 위젯 생성자와 명백한 비위젯 생성자를 제외한다" {
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
        )

        (result.childWidgets.map { it.name }) shouldBe listOf("UserTile")
    }

    "액션 fallback 프로바이더 스캔은 주석과 문자열을 무시한다" {
        val content = """
            // ref.watch(commentedProvider);
            final text = 'stringProvider';
            final user = ref.watch(realProvider);
        """.trimIndent()

        (fallbackProviderNames(content)) shouldBe setOf("realProvider")
    }

})

/** 테스트용 프로바이더 선언 값을 지정한 위치 정보로 생성합니다. */
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
