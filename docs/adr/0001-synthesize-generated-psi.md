# 0001 — `.g.dart` 부재 시 PSI 합성

날짜: 2026-05-10
상태: **Superseded by ADR 0003** (2026-05-10) — 기술 검증 결과 합성 경로 차단

## 맥락

riverpod_generator는 `*.g.dart` 파일에 `_$Foo` 슈퍼클래스와 `fooProvider` 변수를 생성한다. 사용자 정의 코드(예: `class Foo extends _$Foo`, `ref.watch(fooProvider)`)는 이 생성물에 의존한다.

대상 사용자 시나리오: `.g.dart`가 디스크에 **존재하지 않음**. 신규 클론 직후, `build_runner` 미실행, `.g.dart` 커밋 안 하는 팀 워크플로우 등.

이 상태에서는 Dart 플러그인이 `_$Foo`와 `fooProvider`를 미해결로 처리 → 사용자 코드 전체에 빨간 줄, find usage / go to definition 불가.

## 결정

플러그인은 `@riverpod` 어노테이션을 보고 **합성 PSI**를 in-memory로 생성한다. 디스크에 가짜 파일을 쓰지 않는다.

- `@riverpod` 함수 → `fooProvider` 심볼 합성
- `@riverpod` 클래스 → `_$Foo` 슈퍼클래스 심볼 + `fooProvider` 심볼 합성

## 대안

1. **디스크에 가짜 `.g.dart` 작성** — 거부. 사용자가 명시적으로 제외한 파일을 플러그인이 다시 만드는 건 워크플로우 깨뜨림. VCS 오염 위험.
2. **합성 없이 reference contributor만으로 navigation 처리** — 거부. find usage는 가능해도 빨간 줄 자체를 못 없앰. 사용자 경험 반쪽.
3. **`build_runner` 자동 실행 유도** — 거부. 빌드 의존성 강요. 본 플러그인 목표(디스크 부재 환경에서도 동작)와 모순.

## 결과

- 핵심 의존: Dart 플러그인이 외부 PSI 합성을 허용해야 함. 확장 포인트 존재 여부 사전 검증 필수 (별도 작업).
- 합성 PSI는 인덱스에도 노출되어야 find usage 동작. 인덱싱 전략 후속 결정.
- Riverpod 2.x/3.x 버전별 생성 규칙 차이 발생 시 합성 로직 분기 필요.
