package com.ki960213.riverpodgraph.dart

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class DartCodeTest : StringSpec({
    "코드 마스크는 주석과 문자열을 공백으로 가린다" {
        val content = listOf(
            "final visible = visibleProvider;",
            "// hiddenLineProvider",
            "/* outer hiddenBlockProvider",
            "   /* nested hiddenNestedProvider */",
            "*/",
            "final quoted = 'hiddenStringProvider';",
            "final raw = r'\\ hiddenRawProvider';",
            "final triple = \"\"\"hiddenTripleProvider\"\"\";",
            "final visibleAgain = otherProvider;",
        ).joinToString("\n")

        val code = codeOnly(content, dartCodeMask(content))

        code.contains("visibleProvider") shouldBe true
        code.contains("otherProvider") shouldBe true
        code.contains("hiddenLineProvider") shouldBe false
        code.contains("hiddenBlockProvider") shouldBe false
        code.contains("hiddenNestedProvider") shouldBe false
        code.contains("hiddenStringProvider") shouldBe false
        code.contains("hiddenRawProvider") shouldBe false
        code.contains("hiddenTripleProvider") shouldBe false
    }

    "블록 주석 끝은 중첩 주석을 따라 계산한다" {
        val content = "/* outer /* nested */ done */ code"

        dartBlockCommentEnd(content, 0) shouldBe content.indexOf(" code")
    }

    "문자열 끝은 이스케이프된 따옴표와 삼중 따옴표를 처리한다" {
        val escaped = """'not \' done' code"""
        val triple = "\"\"\"not ' done\"\"\" code"

        dartStringEnd(escaped, 0) shouldBe escaped.indexOf(" code")
        dartStringEnd(triple, 0) shouldBe triple.indexOf(" code")
    }

    "괄호 매칭은 주석과 문자열 안의 닫는 문자를 무시한다" {
        val content = "call(')', /* ) */ nested(a, b))"
        val codeMask = dartCodeMask(content)

        findMatchingPair(content, codeMask, content.indexOf('('), '(', ')') shouldBe content.lastIndexOf(')')
    }
})
