# 0002 — IntelliJ + Android Studio 양쪽 지원, Swing UI

날짜: 2026-05-10
상태: 채택

## 맥락

대상 사용자는 Flutter 개발자. 실제 Flutter 개발 환경은 두 갈래:

1. IntelliJ IDEA Ultimate/Community + Dart 플러그인
2. Android Studio + Flutter 플러그인 (Dart 플러그인 번들 포함)

초기 프로젝트는 IntelliJ 2025.3.4.1 의존, `com.intellij.modules.compose` 의존, Compose for Desktop UI 사용 가정. Android Studio는 Compose 모듈을 번들하지 않으므로 호환성 충돌 발생.

## 결정

- 양쪽 IDE 모두 지원
- `com.intellij.modules.compose` 의존 제거
- 플러그인 UI는 **Swing** (IntelliJ Platform 기본 UI 툴킷)
- Dart 플러그인을 명시적 의존으로 선언 (`<depends>Dart</depends>`)
- 플랫폼 베이스라인은 IntelliJ Platform 공통 API에 맞춤
- IntelliJ IDEA 2025.3 이후는 Community/Ultimate 분리 대신 통합 IntelliJ IDEA 배포판을 대상으로 함

## 대안

1. **IntelliJ만 + Compose UI 유지** — 거부. Flutter 개발자 다수가 AS 사용 → 사용자 풀 절반 이상 손실.
2. **Android Studio만** — 거부. IntelliJ Flutter 사용자 배제.
3. **두 빌드 분기 (Compose용 IntelliJ 빌드 + Swing용 AS 빌드)** — 거부. 빌드/배포 복잡도 2배. 가치 대비 비용 과다.

## 결과

- `build.gradle.kts`에서 `composeUI()` 제거, `kotlin.plugin.compose` 플러그인 제거
- `plugin.xml`에서 `com.intellij.modules.compose` 의존 제거, `Dart` 플러그인 의존 추가
- UI 작성 시 Swing 컴포넌트 (`JBPanel`, `JBList`, `Tree`, `ToolWindow` 등) 사용
- 플랫폼 베이스라인은 공통 Platform API로 맞춰 AS 호환성 확보
- IntelliJ IDEA 최신 안정 버전 검증은 2026.1.1 / 261 브랜치부터 `intellijIdea(...)`와 `IntelliJPlatformType.IntellijIdea`를 사용
- 매니페스트의 `until-build`는 제거해 261 이후 IDE 업데이트에서도 설치가 막히지 않게 함
