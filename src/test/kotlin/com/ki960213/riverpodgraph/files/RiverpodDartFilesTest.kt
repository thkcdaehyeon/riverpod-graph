package com.ki960213.riverpodgraph.files

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class RiverpodDartFilesTest : StringSpec({
    "일반 Dart 소스 파일만 Riverpod 분석 대상으로 본다" {
        (isRiverpodDartSourceFileName("user.dart")) shouldBe true
        (isRiverpodDartSourceFileName("user_provider.dart")) shouldBe true

        (isRiverpodDartSourceFileName("user.g.dart")) shouldBe false
        (isRiverpodDartSourceFileName("user.freezed.dart")) shouldBe false
        (isRiverpodDartSourceFileName("user.mocks.dart")) shouldBe false
        (isRiverpodDartSourceFileName("user.dart.txt")) shouldBe false
    }
})
