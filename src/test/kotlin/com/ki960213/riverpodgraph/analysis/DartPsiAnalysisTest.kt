package com.ki960213.riverpodgraph.analysis

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.FakePsiElement
import com.intellij.psi.tree.IElementType
import com.jetbrains.lang.dart.DartTokenTypes
import com.jetbrains.lang.dart.psi.DartArgumentList
import com.jetbrains.lang.dart.psi.DartArguments
import com.jetbrains.lang.dart.psi.DartCallExpression
import com.jetbrains.lang.dart.psi.DartExpression
import com.jetbrains.lang.dart.psi.DartNamedArgument
import com.jetbrains.lang.dart.psi.DartReferenceExpression
import com.jetbrains.lang.dart.psi.DartSwitchExpressionWrapper
import com.jetbrains.lang.dart.psi.DartTypeArguments
import com.jetbrains.lang.dart.util.DartClassResolveResult
import com.ki960213.riverpodgraph.model.RiverpodProviderDeclaration
import com.ki960213.riverpodgraph.model.RiverpodProviderKind
import com.ki960213.riverpodgraph.model.RiverpodUsageKind
import java.lang.reflect.Proxy
import junit.framework.TestCase

class DartPsiAnalysisTest : TestCase() {
    fun `test Dart PSI는 ref 변수명이 달라도 프로바이더 사용을 찾는다`() {
        val content = """
            void build(WidgetRef widgetRef) {
              final user = widgetRef.watch(userProvider);
              final session = widgetRef.read(sessionProvider.notifier);
            }
        """.trimIndent()
        val file = psiFile(
            content,
            refCall(content, "widgetRef.watch", "userProvider"),
            refCall(content, "widgetRef.read", "sessionProvider.notifier"),
        )

        val usages = ProviderUsageScanner.scan(
            filePath = "lib/profile.dart",
            file = file,
            usageScope = ProviderUsageScope(providerNames = setOf("userProvider", "sessionProvider")),
        )

        assertEquals(listOf("userProvider", "sessionProvider"), usages.map { it.providerName })
        assertEquals(listOf(RiverpodUsageKind.WATCH, RiverpodUsageKind.NOTIFIER), usages.map { it.kind })
    }

    fun `test Dart PSI 프로바이더 그래프는 Ref 매개변수 이름 변경을 놓치지 않는다`() {
        val content = """
            @riverpod
            String user(Ref dependencyRef) => 'Ada';

            @riverpod
            Profile profile(Ref dependencyRef) {
              final user = dependencyRef.watch(userProvider);
              return Profile(user);
            }
        """.trimIndent()
        val file = psiFile(
            content,
            refCall(content, "dependencyRef.watch", "userProvider"),
        )
        val declarations = listOf(
            declaration(
                sourceName = "user",
                providerName = "userProvider",
                textOffset = content.indexOf("user(Ref"),
                textEndOffset = content.indexOf("@riverpod", startIndex = 1),
            ),
            declaration(
                sourceName = "profile",
                providerName = "profileProvider",
                textOffset = content.indexOf("profile(Ref"),
                textEndOffset = content.length,
            ),
        )

        val edges = ProviderDependencyAnalyzer.analyzeFile(
            filePath = "lib/profile.dart",
            file = file,
            declarations = declarations,
        )

        assertEquals(listOf("profileProvider:userProvider"), edges.map { "${it.fromProvider}:${it.toProvider}" })
    }

    fun `test Dart PSI 위젯 의존성은 WidgetRef 매개변수 이름 변경을 놓치지 않는다`() {
        val content = """
            class HomeScreen extends ConsumerWidget {
              Widget build(BuildContext context, WidgetRef widgetRef) {
                final user = widgetRef.watch(userProvider);
                return UserPanel();
              }
            }
        """.trimIndent()
        val file = psiFile(
            content,
            refCall(content, "widgetRef.watch", "userProvider"),
            constructorCall(content, "UserPanel"),
        )

        val result = WidgetDependencyAnalyzer.analyze(
            filePath = "lib/home.dart",
            file = file,
            providerNames = setOf("userProvider"),
        )

        assertEquals(listOf("userProvider"), result.providerNames)
        assertEquals(listOf("UserPanel"), result.childWidgets.map { it.name })
    }
}

/** PSI 기반 분석 테스트에 사용할 프로바이더 선언을 만듭니다. */
private fun declaration(
    sourceName: String,
    providerName: String,
    textOffset: Int,
    textEndOffset: Int,
): RiverpodProviderDeclaration = RiverpodProviderDeclaration(
    kind = RiverpodProviderKind.FUNCTION,
    sourceName = sourceName,
    providerName = providerName,
    generatedSuperclassName = null,
    returnType = "Object",
    familySignature = "Ref dependencyRef",
    keepAlive = false,
    isPrivate = false,
    filePath = "lib/profile.dart",
    textOffset = textOffset,
    line = 1,
    textEndOffset = textEndOffset,
)

/** 테스트용 PsiFile이 지정한 Dart PSI 요소들을 visitor에 넘기도록 만듭니다. */
private fun psiFile(content: String, vararg elements: PsiElement): PsiFile =
    Proxy.newProxyInstance(
        PsiFile::class.java.classLoader,
        arrayOf(PsiFile::class.java),
    ) { _, method, args ->
        when (method.name) {
            "getText" -> content
            "accept" -> {
                val visitor = args?.firstOrNull() as? PsiElementVisitor ?: return@newProxyInstance null
                elements.forEach { it.accept(visitor) }
                null
            }

            "toString" -> "FakeDartPsiFile"
            "getName" -> "fake.dart"
            else -> defaultValue(method.returnType)
        }
    } as PsiFile

/** Provider ref 호출식 PSI를 만듭니다. */
private fun refCall(content: String, calleeText: String, argumentText: String): DartCallExpression {
    val calleeOffset = content.indexOf(calleeText).takeIf { it >= 0 } ?: error("callee not found: $calleeText")
    val argumentOffset = content.indexOf(argumentText, startIndex = calleeOffset)
        .takeIf { it >= 0 } ?: error("argument not found: $argumentText")
    return FakeDartCallExpression(
        text = "$calleeText($argumentText)",
        offset = calleeOffset,
        callee = FakeDartReferenceExpression(calleeText, calleeOffset),
        arguments = listOf(FakeDartReferenceExpression(argumentText, argumentOffset)),
    )
}

/** 위젯 생성자 호출식 PSI를 만듭니다. */
private fun constructorCall(content: String, calleeText: String): DartCallExpression {
    val calleeOffset = content.indexOf(calleeText).takeIf { it >= 0 } ?: error("callee not found: $calleeText")
    return FakeDartCallExpression(
        text = "$calleeText()",
        offset = calleeOffset,
        callee = FakeDartReferenceExpression(calleeText, calleeOffset),
        arguments = emptyList(),
    )
}

/** 테스트에 필요한 Dart PSI 공통 요소입니다. */
private open class FakeDartElement(
    private val value: String,
    private val offset: Int,
) : FakePsiElement(), DartExpression {
    override fun getParent(): PsiElement? = null
    override fun getText(): String = value
    override fun getTextOffset(): Int = offset
    override fun getTextRange(): TextRange = TextRange.from(offset, value.length)
    override fun getTokenType(): IElementType = DartTokenTypes.REFERENCE_EXPRESSION
    override fun accept(visitor: PsiElementVisitor) {
        visitor.visitElement(this)
    }
}

/** 테스트에 필요한 Dart 참조식 PSI입니다. */
private open class FakeDartReferenceExpression(
    value: String,
    offset: Int,
) : FakeDartElement(value, offset), DartReferenceExpression, PsiReference {
    override fun resolveDartClass(): DartClassResolveResult = DartClassResolveResult.EMPTY
    override fun getElement(): PsiElement = this
    override fun getRangeInElement(): TextRange = TextRange(0, textLength)
    override fun resolve(): PsiElement? = null
    override fun getCanonicalText(): String = text
    override fun handleElementRename(newElementName: String): PsiElement = this
    override fun bindToElement(element: PsiElement): PsiElement = this
    override fun isReferenceTo(element: PsiElement): Boolean = false
    override fun getVariants(): Array<Any> = emptyArray()
    override fun isSoft(): Boolean = false
}

/** 테스트에 필요한 Dart 호출식 PSI입니다. */
private class FakeDartCallExpression(
    text: String,
    offset: Int,
    private val callee: DartExpression,
    arguments: List<DartExpression>,
) : FakeDartReferenceExpression(text, offset), DartCallExpression {
    private val dartArguments = FakeDartArguments(arguments)

    override fun getExpression(): DartExpression = callee
    override fun getSwitchExpressionWrapper(): DartSwitchExpressionWrapper? = null
    override fun getTypeArgumentsList(): List<DartTypeArguments> = emptyList()
    override fun getArguments(): DartArguments = dartArguments
}

/** 테스트에 필요한 Dart arguments PSI입니다. */
private class FakeDartArguments(
    expressions: List<DartExpression>,
) : FakeDartElement("", 0), DartArguments {
    private val argumentList = FakeDartArgumentList(expressions)

    override fun getArgumentList(): DartArgumentList = argumentList
}

/** 테스트에 필요한 Dart argument list PSI입니다. */
private class FakeDartArgumentList(
    private val expressions: List<DartExpression>,
) : FakeDartElement("", 0), DartArgumentList {
    override fun getExpressionList(): List<DartExpression> = expressions
    override fun getNamedArgumentList(): List<DartNamedArgument> = emptyList()
}

/** 동적 프록시가 호출하지 않는 메서드에 반환할 기본값입니다. */
private fun defaultValue(type: Class<*>): Any? = when (type) {
    java.lang.Boolean.TYPE -> false
    java.lang.Integer.TYPE -> 0
    java.lang.Long.TYPE -> 0L
    java.lang.Float.TYPE -> 0f
    java.lang.Double.TYPE -> 0.0
    java.lang.Void.TYPE -> null
    else -> null
}
