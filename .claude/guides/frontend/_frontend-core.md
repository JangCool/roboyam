# Frontend Core Guide (`_frontend-core.md`)

## 1. 역할 및 기본 원칙
* **기존 구조 우선**: 기술 스택·폴더 구조·구현 패턴을 먼저 확인하고 따른다. 요청된 범위만 수정한다.
* **스택**: Nuxt 4.6 SPA(`ssr:false`) · Vue 3.5 · PrimeVue 5 · Tailwind 4. 설치된 버전을 기준으로 개발하고, 새 라이브러리를 임의로 추가하지 않는다.
* **재사용**: 공통 컴포넌트·컴포저블·유틸을 먼저 검색해 재사용한다. 성급하게 공통화하지 않는다(두 번째 사용처가 생길 때 올린다).
* **확정되지 않은 업무 정책·UI 스펙을 임의로 정하지 않는다.**

---

## 2. 주요 업무 및 규약

### 2-1. 파일 배치 (`apps/<앱>/app/`)
| 어디 | 무엇을 | 두지 않는 것 |
|---|---|---|
| `pages/` | 화면(주소 = 파일 경로). 그 화면에서만 쓰는 조각·값 | 다른 화면도 쓸 로직 |
| `layouts/` | 셸: `default`(앱 셸) · `auth`(로그인 카드) · `install`(설치 마법사) | 업무 로직 |
| `middleware/` | 페이지 진입 전 검사(`install.global` · `guest`) | 화면 마크업 |
| `components/` | 두 화면 이상이 쓰는 조각, 업무 폴더별 → `components.md` | 서버 호출 |
| `composables/` | 서버 호출과 여러 화면이 공유하는 상태, 업무 폴더별 → `composables.md` | 화면 마크업 |
| `utils/` | 입력만 보고 답하는 순수 함수 → `utils.md` | `useState` · `$fetch` |
| `types/` | 주인 없는 업무 횡단 타입만 → `composables.md` | 서버 DTO·컴포넌트 계약 타입 |
| `plugins/` | 앱 시작 때 한 번 하는 일(설치·로그인 상태 조회) | 업무 로직 |

* 두 앱이 같이 쓰는 코드는 `apps-packages/<이름>/` → `layers.md`. 화면 문구는 i18n 키 → `i18n.md`. 입력 폼·오류 표시 → `forms.md`.
* **`.vue` 에 서버 주소(`/api/…`)를 적지 않는다.** 서버와 말하는 곳은 `composables/<업무>/api/use<자원>Api.ts` 한 겹이다.
* 새 파일은 같은 업무의 기존 파일 옆에 둘 수 있는지 먼저 본다. 공유 모듈을 고칠 때는 쓰는 곳을 전부 찾아 함께 확인한다.

### 2-2. 화면 상태와 권한
* 비동기 화면은 로딩 · 빈 결과(`EmptyState`) · 오류(`ErrorState`)를 모두 다룬다.
* **감추는 것은 편의이고 막는 것은 서버다.** 숨길지 비활성으로 둘지는 업무 의미로 정한다. 아직 없는 기능의 메뉴·링크는 보여 주지 않는다.
* 권한이 없는 것(403)은 오류가 아니다 → `forms.md`.

### 2-3. 반응형 상태(`watch`)
* `watch` 남용을 금지하고, 필요한 경우에만 사용한다.
* 파생 상태는 `computed`, 사용자 동작은 이벤트 핸들러로 처리한다.
* `watch` 는 상태 변경에 따른 부수 효과(API 호출, 외부 라이브러리 동기화 등)가 필요한 경우에 사용한다.
* `deep: true`, `immediate: true` 는 필요성이 명확한 경우에만 사용한다.
* 동일한 상태를 여러 `watch` 에서 중복 관리하거나 연쇄적인 상태 변경을 유발하는 구조를 지양한다.
* 새로운 `watch` 추가 시 기존 반응형 흐름, 중복 실행, 불필요한 API 호출 여부를 확인한다.
* 기존 코드를 수정할 때는 불필요한 `watch` 를 무조건 제거하지 말고, 실제 동작과 비동기 처리에 미치는 영향을 검토한다.
* 서버(API)·외부 SDK 에 넘기는 파라미터는 반응형 객체(Proxy)로 넘기지 않고 raw 데이터로 넘긴다 — `toRaw` 후 복사한 평범한 객체(`{ ...toRaw(x) }`, 배열은 `map` 으로 새로 만든다). Proxy 를 그대로 주면 `postMessage`·`structuredClone` 을 쓰는 SDK 가 `DataCloneError` 로 실패하고(포트원 결제창), 요청이 나간 뒤 화면 상태가 바뀌어 보낸 값이 흔들릴 수 있다.

### 2-4. 목업과 검증
* 목업 단계의 API 메서드는 `// MOCK:` 줄과 `// REAL:` 줄을 나란히 둔다. 실제 API 로 바꿀 때 MOCK 줄과 목업 import 만 지운다.
* 검증은 `pnpm --filter <앱> typecheck` 만 실행한다. 타입 검사 통과는 런타임 정상이 아니므로, 새 코드는 dev 서버를 띄워 해당 화면을 한 번 실제로 호출해 확인한다. 시각 리뷰는 개발자가 한다.

---

## 3. 작성 예시
```vue
<script setup lang="ts">
const { t } = useI18n()
const { data, error } = await useBoardDetail(id)   // 서버 호출은 컴포저블 한 겹 (형태만 보인 예)
</script>
<template>
  <ErrorState v-if="error" :error="error" />
  <EmptyState v-else-if="!data" />
  <div v-else>{{ t('board.title') }}</div>
</template>
```

---

## 4. 금지 사항 (Anti-Patterns)
1. ❌ 여러 화면이 쓰지 않는 단발성 기능을 성급하게 공통화
2. ❌ 개발 중 전체 빌드·E2E·단위 테스트 자동 실행
3. ❌ 확정되지 않은 업무 정책이나 UI 스펙의 임의 반영
4. ❌ `.vue` 에 서버 주소·`$fetch` 작성
5. ❌ 서버 정책을 UI 만 보고 추측
