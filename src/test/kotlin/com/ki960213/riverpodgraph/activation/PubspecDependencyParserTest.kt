package com.ki960213.riverpodgraph.activation

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class PubspecDependencyParserTest : StringSpec({
    "dependencies 블록의 riverpod_annotation을 감지한다" {
        val pubspec = """
            dependencies:
              flutter:
                sdk: flutter
              riverpod_annotation: ^3.0.0
        """.trimIndent()

        (PubspecDependencyParser.hasRiverpodAnnotation(pubspec)) shouldBe true
    }

    "런타임 riverpod만 있으면 활성화하지 않는다" {
        val pubspec = """
            dependencies:
              flutter_riverpod: ^3.0.0
        """.trimIndent()

        (PubspecDependencyParser.hasRiverpodAnnotation(pubspec)) shouldBe false
    }

    "dev_dependencies 블록의 riverpod_annotation을 감지한다" {
        val pubspec = """
            dev_dependencies:
              riverpod_annotation: ^3.0.0
        """.trimIndent()

        (PubspecDependencyParser.hasRiverpodAnnotation(pubspec)) shouldBe true
    }

    "따옴표 키의 riverpod_annotation 의존성을 감지한다" {
        val pubspec = """
            "dependencies":
              'riverpod_annotation': ^3.0.0
        """.trimIndent()

        (PubspecDependencyParser.hasRiverpodAnnotation(pubspec)) shouldBe true
    }

    "따옴표 키의 dev riverpod_annotation 의존성을 감지한다" {
        val pubspec = """
            'dev_dependencies':
              "riverpod_annotation": ^3.0.0
        """.trimIndent()

        (PubspecDependencyParser.hasRiverpodAnnotation(pubspec)) shouldBe true
    }

    "중첩된 dependencies 블록은 무시한다" {
        val pubspec = """
            flutter:
              dependencies:
                riverpod_annotation: ^3.0.0
        """.trimIndent()

        (PubspecDependencyParser.hasRiverpodAnnotation(pubspec)) shouldBe false
    }

    "주석이 섞인 dependencies 블록에서도 riverpod_annotation을 감지한다" {
        val pubspec = """
            dependencies: # app packages
              riverpod_annotation: ^3.0.0
        """.trimIndent()

        (PubspecDependencyParser.hasRiverpodAnnotation(pubspec)) shouldBe true
    }

    "다른 의존성 내부의 중첩 riverpod_annotation 키는 무시한다" {
        val pubspec = """
            dependencies:
              local_package:
                path: ../local_package
                riverpod_annotation: false
        """.trimIndent()

        (PubspecDependencyParser.hasRiverpodAnnotation(pubspec)) shouldBe false
    }
})
