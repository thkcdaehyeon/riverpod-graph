package com.ki960213.riverpodgraph.parser

import com.ki960213.riverpodgraph.model.RiverpodProviderKind
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class RiverpodAnnotationParserTest : StringSpec({
    "함수형 프로바이더 선언을 파싱한다" {
        val content = """
            import 'package:riverpod_annotation/riverpod_annotation.dart';
            part 'user.g.dart';

            @riverpod
            Future<User> user(Ref ref, String id) async => User(id);
        """.trimIndent()

        val declarations = RiverpodAnnotationParser.parse("lib/user.dart", content)

        (declarations.size) shouldBe 1
        val declaration = declarations.single()
        (declaration.kind) shouldBe RiverpodProviderKind.FUNCTION
        (declaration.sourceName) shouldBe "user"
        (declaration.providerName) shouldBe "userProvider"
        (declaration.generatedSuperclassName) shouldBe null
        (declaration.returnType) shouldBe "Future<User>"
        (declaration.familySignature) shouldBe "Ref ref, String id"
        (declaration.keepAlive) shouldBe false
        (declaration.isPrivate) shouldBe false
        (declaration.filePath) shouldBe "lib/user.dart"
        (declaration.textOffset) shouldBe content.indexOf("user(Ref")
        (declaration.line) shouldBe 5
    }

    "keepAlive notifier 클래스 선언을 파싱한다" {
        val content = """
            @Riverpod(keepAlive: true)
            class SessionController extends _${'$'}SessionController {
              @override
              Session build(String id) => Session(id);
            }
        """.trimIndent()

        val declarations = RiverpodAnnotationParser.parse("lib/session.dart", content)

        (declarations.size) shouldBe 1
        val declaration = declarations.single()
        (declaration.kind) shouldBe RiverpodProviderKind.NOTIFIER_CLASS
        (declaration.sourceName) shouldBe "SessionController"
        (declaration.providerName) shouldBe "sessionControllerProvider"
        (declaration.generatedSuperclassName) shouldBe "_${'$'}SessionController"
        (declaration.returnType) shouldBe "Session"
        (declaration.familySignature) shouldBe "String id"
        (declaration.keepAlive) shouldBe true
        (declaration.isPrivate) shouldBe false
        (declaration.filePath) shouldBe "lib/session.dart"
        (declaration.textOffset) shouldBe content.indexOf("SessionController")
        (declaration.line) shouldBe 2
    }

    "주석과 문자열 안의 어노테이션은 무시한다" {
        val content = """
            // @riverpod
            String commented(Ref ref) => 'not a provider';

            final text = '@Riverpod(keepAlive: true) class Fake extends _${'$'}Fake { Fake build() => Fake(); }';

            /*
            @riverpod
            String blocked(Ref ref) => 'not a provider';
            */

            @riverpod
            String real(Ref ref) => 'provider';
        """.trimIndent()

        val declarations = RiverpodAnnotationParser.parse("lib/comments.dart", content)

        (declarations.size) shouldBe 1
        (declarations.single().sourceName) shouldBe "real"
        (declarations.single().textOffset) shouldBe content.indexOf("real(Ref")
    }

    "riverpod 어노테이션과 함수 선언 사이의 메타데이터를 건너뛴다" {
        val content = """
            @Riverpod(keepAlive: true)
            @Deprecated('use newUser(reason: "(kept)")')
            Future<User> user(Ref ref, String id) async => User(id);
        """.trimIndent()

        val declarations = RiverpodAnnotationParser.parse("lib/user.dart", content)

        (declarations.size) shouldBe 1
        val declaration = declarations.single()
        (declaration.kind) shouldBe RiverpodProviderKind.FUNCTION
        (declaration.sourceName) shouldBe "user"
        (declaration.returnType) shouldBe "Future<User>"
        (declaration.familySignature) shouldBe "Ref ref, String id"
        (declaration.keepAlive) shouldBe true
        (declaration.textOffset) shouldBe content.indexOf("user(Ref")
    }

    "문자열과 주석을 무시하며 시그니처 괄호 쌍을 맞춘다" {
        val content = """
            @riverpod
            String tricky(
              Ref ref,
              String value, {
              String fallback = 'not ) end',
              String commentLike = '/* not comment */',
            }) => value;
        """.trimIndent()

        val declarations = RiverpodAnnotationParser.parse("lib/tricky.dart", content)

        (declarations.size) shouldBe 1
        val declaration = declarations.single()
        (declaration.sourceName) shouldBe "tricky"
        (declaration.familySignature) shouldBe "Ref ref, String value, { String fallback = 'not ) end', String commentLike = '/* not comment */', }"
    }

    "함수 반환 타입을 가진 함수 프로바이더를 파싱한다" {
        val content = """
            @riverpod
            String Function() formatter(Ref ref, String prefix) => () => prefix;
        """.trimIndent()

        val declarations = RiverpodAnnotationParser.parse("lib/formatter.dart", content)

        (declarations.size) shouldBe 1
        val declaration = declarations.single()
        (declaration.sourceName) shouldBe "formatter"
        (declaration.providerName) shouldBe "formatterProvider"
        (declaration.returnType) shouldBe "String Function()"
        (declaration.familySignature) shouldBe "Ref ref, String prefix"
        (declaration.textOffset) shouldBe content.indexOf("formatter(Ref")
    }

    "레코드 반환 타입을 가진 함수 프로바이더를 파싱한다" {
        val content = """
            @riverpod
            (String, int) pair(Ref ref) => ('count', 1);
        """.trimIndent()

        val declarations = RiverpodAnnotationParser.parse("lib/pair.dart", content)

        (declarations.size) shouldBe 1
        val declaration = declarations.single()
        (declaration.sourceName) shouldBe "pair"
        (declaration.providerName) shouldBe "pairProvider"
        (declaration.returnType) shouldBe "(String, int)"
        (declaration.familySignature) shouldBe "Ref ref"
        (declaration.textOffset) shouldBe content.indexOf("pair(Ref")
    }

    "주석, 문자열, 중첩 중괄호를 무시하고 클래스 build 메서드를 찾는다" {
        val content = """
            @Riverpod(keepAlive: true)
            class SessionController extends _${'$'}SessionController {
              // Session build() => Session('comment');
              final marker = 'build(ignored)';

              @override
              Session build(String id) {
                final raw = 'not } body end';
                if (id.isEmpty) {
                  return Session('anonymous');
                }
                return Session(id);
              }
            }
        """.trimIndent()

        val declarations = RiverpodAnnotationParser.parse("lib/session.dart", content)

        (declarations.size) shouldBe 1
        val declaration = declarations.single()
        (declaration.kind) shouldBe RiverpodProviderKind.NOTIFIER_CLASS
        (declaration.sourceName) shouldBe "SessionController"
        (declaration.returnType) shouldBe "Session"
        (declaration.familySignature) shouldBe "String id"
        (declaration.textOffset) shouldBe content.indexOf("SessionController")
    }

    "클래스 build 메서드 앞의 멤버 build 호출은 무시한다" {
        val content = """
            @riverpod
            class SessionController extends _${'$'}SessionController {
              final cached = helper.build();

              @override
              String build(String id) => id;
            }
        """.trimIndent()

        val declarations = RiverpodAnnotationParser.parse("lib/session.dart", content)

        (declarations.size) shouldBe 1
        val declaration = declarations.single()
        (declaration.returnType) shouldBe "String"
        (declaration.familySignature) shouldBe "String id"
    }

    "어노테이션 주석 안의 keepAlive 문구는 무시한다" {
        val content = """
            @Riverpod(/* keepAlive: true */)
            String user(Ref ref) => 'user';
        """.trimIndent()

        val declarations = RiverpodAnnotationParser.parse("lib/user.dart", content)

        (declarations.size) shouldBe 1
        (declarations.single().keepAlive) shouldBe false
    }

    "어노테이션 문자열 인자 안의 keepAlive 문구는 무시한다" {
        val content = """
            @Riverpod(name: 'keepAlive: true')
            String user(Ref ref) => 'user';
        """.trimIndent()

        val declarations = RiverpodAnnotationParser.parse("lib/user.dart", content)

        (declarations.size) shouldBe 1
        (declarations.single().keepAlive) shouldBe false
    }

    "중첩 블록 주석 안의 가짜 어노테이션은 무시한다" {
        val content = """
            /*
            outer
            /* nested */
            @riverpod
            String fake(Ref ref) => 'fake';
            */

            @riverpod
            String real(Ref ref) => 'real';
        """.trimIndent()

        val declarations = RiverpodAnnotationParser.parse("lib/nested.dart", content)

        (declarations.size) shouldBe 1
        (declarations.single().sourceName) shouldBe "real"
    }
})
