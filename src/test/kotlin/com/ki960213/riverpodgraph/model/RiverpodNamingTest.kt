package com.ki960213.riverpodgraph.model

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class RiverpodNamingTest : StringSpec({
    "함수 프로바이더는 원래 lowerCamel 이름을 유지한다" {
        (RiverpodNaming.providerForFunction("user")) shouldBe "userProvider"
        (RiverpodNaming.providerForFunction("_privateUser")) shouldBe "_privateUserProvider"
        (RiverpodNaming.providerForFunction("User")) shouldBe "userProvider"
    }

    "함수 프로바이더는 기본 Notifier 접미사를 제거한다" {
        (RiverpodNaming.providerForFunction("fooNotifier")) shouldBe "fooProvider"
        (RiverpodNaming.providerForFunction("_fooNotifier")) shouldBe "_fooProvider"
    }

    "클래스 프로바이더는 generator의 첫 글자 소문자 규칙을 사용한다" {
        (RiverpodNaming.providerForClass("UserController")) shouldBe "userControllerProvider"
        (RiverpodNaming.providerForClass("_PrivateController")) shouldBe "_privateControllerProvider"
        (RiverpodNaming.providerForClass("URLCache")) shouldBe "uRLCacheProvider"
    }

    "클래스 프로바이더는 기본 Notifier 접미사를 제거한다" {
        (RiverpodNaming.providerForClass("CounterNotifier")) shouldBe "counterProvider"
        (RiverpodNaming.providerForClass("_PrivateNotifier")) shouldBe "_privateProvider"
    }

    "생성 상위 클래스 이름은 공개 클래스 이름을 유지한다" {
        (RiverpodNaming.generatedSuperclassForClass("UserController")) shouldBe "_${'$'}" + "UserController"
        (RiverpodNaming.generatedSuperclassForClass("_PrivateController")) shouldBe "_${'$'}" + "PrivateController"
    }
})
