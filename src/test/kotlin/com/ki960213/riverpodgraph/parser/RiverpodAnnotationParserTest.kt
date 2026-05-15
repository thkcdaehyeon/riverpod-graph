package com.ki960213.riverpodgraph.parser

import com.ki960213.riverpodgraph.model.RiverpodProviderKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RiverpodAnnotationParserTest {
    @Test
    fun `parses functional provider declaration`() {
        val content = """
            import 'package:riverpod_annotation/riverpod_annotation.dart';
            part 'user.g.dart';

            @riverpod
            Future<User> user(Ref ref, String id) async => User(id);
        """.trimIndent()

        val declarations = RiverpodAnnotationParser.parse("lib/user.dart", content)

        assertEquals(1, declarations.size)
        val declaration = declarations.single()
        assertEquals(RiverpodProviderKind.FUNCTION, declaration.kind)
        assertEquals("user", declaration.sourceName)
        assertEquals("userProvider", declaration.providerName)
        assertNull(declaration.generatedSuperclassName)
        assertEquals("Future<User>", declaration.returnType)
        assertEquals("Ref ref, String id", declaration.familySignature)
        assertFalse(declaration.keepAlive)
        assertFalse(declaration.isPrivate)
        assertEquals("lib/user.dart", declaration.filePath)
        assertEquals(content.indexOf("user(Ref"), declaration.textOffset)
        assertEquals(5, declaration.line)
    }

    @Test
    fun `parses notifier class declaration with keep alive`() {
        val content = """
            @Riverpod(keepAlive: true)
            class SessionController extends _${'$'}SessionController {
              @override
              Session build(String id) => Session(id);
            }
        """.trimIndent()

        val declarations = RiverpodAnnotationParser.parse("lib/session.dart", content)

        assertEquals(1, declarations.size)
        val declaration = declarations.single()
        assertEquals(RiverpodProviderKind.NOTIFIER_CLASS, declaration.kind)
        assertEquals("SessionController", declaration.sourceName)
        assertEquals("sessionControllerProvider", declaration.providerName)
        assertEquals("_${'$'}SessionController", declaration.generatedSuperclassName)
        assertEquals("Session", declaration.returnType)
        assertEquals("String id", declaration.familySignature)
        assertTrue(declaration.keepAlive)
        assertFalse(declaration.isPrivate)
        assertEquals("lib/session.dart", declaration.filePath)
        assertEquals(content.indexOf("SessionController"), declaration.textOffset)
        assertEquals(2, declaration.line)
    }

    @Test
    fun `ignores annotations in comments and strings`() {
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

        assertEquals(1, declarations.size)
        assertEquals("real", declarations.single().sourceName)
        assertEquals(content.indexOf("real(Ref"), declarations.single().textOffset)
    }

    @Test
    fun `skips metadata between riverpod annotation and function declaration`() {
        val content = """
            @Riverpod(keepAlive: true)
            @Deprecated('use newUser(reason: "(kept)")')
            Future<User> user(Ref ref, String id) async => User(id);
        """.trimIndent()

        val declarations = RiverpodAnnotationParser.parse("lib/user.dart", content)

        assertEquals(1, declarations.size)
        val declaration = declarations.single()
        assertEquals(RiverpodProviderKind.FUNCTION, declaration.kind)
        assertEquals("user", declaration.sourceName)
        assertEquals("Future<User>", declaration.returnType)
        assertEquals("Ref ref, String id", declaration.familySignature)
        assertTrue(declaration.keepAlive)
        assertEquals(content.indexOf("user(Ref"), declaration.textOffset)
    }

    @Test
    fun `balances signature pairs while ignoring strings and comments`() {
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

        assertEquals(1, declarations.size)
        val declaration = declarations.single()
        assertEquals("tricky", declaration.sourceName)
        assertEquals(
            "Ref ref, String value, { String fallback = 'not ) end', String commentLike = '/* not comment */', }",
            declaration.familySignature,
        )
    }

    @Test
    fun `parses function provider with function return type`() {
        val content = """
            @riverpod
            String Function() formatter(Ref ref, String prefix) => () => prefix;
        """.trimIndent()

        val declarations = RiverpodAnnotationParser.parse("lib/formatter.dart", content)

        assertEquals(1, declarations.size)
        val declaration = declarations.single()
        assertEquals("formatter", declaration.sourceName)
        assertEquals("formatterProvider", declaration.providerName)
        assertEquals("String Function()", declaration.returnType)
        assertEquals("Ref ref, String prefix", declaration.familySignature)
        assertEquals(content.indexOf("formatter(Ref"), declaration.textOffset)
    }

    @Test
    fun `parses function provider with record return type`() {
        val content = """
            @riverpod
            (String, int) pair(Ref ref) => ('count', 1);
        """.trimIndent()

        val declarations = RiverpodAnnotationParser.parse("lib/pair.dart", content)

        assertEquals(1, declarations.size)
        val declaration = declarations.single()
        assertEquals("pair", declaration.sourceName)
        assertEquals("pairProvider", declaration.providerName)
        assertEquals("(String, int)", declaration.returnType)
        assertEquals("Ref ref", declaration.familySignature)
        assertEquals(content.indexOf("pair(Ref"), declaration.textOffset)
    }

    @Test
    fun `finds class build method while ignoring comments strings and nested braces`() {
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

        assertEquals(1, declarations.size)
        val declaration = declarations.single()
        assertEquals(RiverpodProviderKind.NOTIFIER_CLASS, declaration.kind)
        assertEquals("SessionController", declaration.sourceName)
        assertEquals("Session", declaration.returnType)
        assertEquals("String id", declaration.familySignature)
        assertEquals(content.indexOf("SessionController"), declaration.textOffset)
    }

    @Test
    fun `ignores member build calls before class build method`() {
        val content = """
            @riverpod
            class SessionController extends _${'$'}SessionController {
              final cached = helper.build();

              @override
              String build(String id) => id;
            }
        """.trimIndent()

        val declarations = RiverpodAnnotationParser.parse("lib/session.dart", content)

        assertEquals(1, declarations.size)
        val declaration = declarations.single()
        assertEquals("String", declaration.returnType)
        assertEquals("String id", declaration.familySignature)
    }

    @Test
    fun `ignores keep alive text inside annotation comment`() {
        val content = """
            @Riverpod(/* keepAlive: true */)
            String user(Ref ref) => 'user';
        """.trimIndent()

        val declarations = RiverpodAnnotationParser.parse("lib/user.dart", content)

        assertEquals(1, declarations.size)
        assertFalse(declarations.single().keepAlive)
    }

    @Test
    fun `ignores keep alive text inside annotation string argument`() {
        val content = """
            @Riverpod(name: 'keepAlive: true')
            String user(Ref ref) => 'user';
        """.trimIndent()

        val declarations = RiverpodAnnotationParser.parse("lib/user.dart", content)

        assertEquals(1, declarations.size)
        assertFalse(declarations.single().keepAlive)
    }

    @Test
    fun `ignores fake annotation inside nested block comment`() {
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

        assertEquals(1, declarations.size)
        assertEquals("real", declarations.single().sourceName)
    }
}
