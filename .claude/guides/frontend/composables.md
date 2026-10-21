# Frontend Composables Guide (`composables.md`)

## 1. 역할 및 기본 원칙
* 컴포저블은 **서버 호출**과 **여러 화면이 공유하는 상태**를 위한 자리다. 한 화면만 쓰는 값은 그 페이지의 `ref` 로 둔다. 화면 마크업은 두지 않는다.
* **업무(도메인) 폴더로 나눈다.** 새 컴포저블을 `composables/` 바로 아래에 평평하게 추가하지 않는다. 한 업무에서만 쓰면 `common/` 에 두지 않고, 쓰는 업무가 둘 이상 되면 `common/` 으로 올린다. 업무 → `common` 방향으로만 의존한다(업무끼리 순환 import 금지).
* 모르겠으면 그 컴포저블을 가장 먼저 쓰는 화면의 업무에 둔다. 기존 평평한 파일은 요청 범위 밖이면 옮기지 않는다.

---

## 2. 주요 업무 및 규약

### 2-1. 폴더
```text
composables/
├── common/        # 여러 업무 공용: useApiClient, useAppToast, useCountdown … (목업 도구는 common/mock/)
├── <업무>/        # auth/, install/, board/ … (소문자 단일 단어)
│   ├── useXxx.ts  # 공유 상태
│   └── api/       # useXxxApi.ts (+ mock/<업무>-mock.ts)
```
* Nuxt 는 `composables/` 바로 아래만 스캔하므로 `nuxt.config.ts` 에 `imports: { dirs: ['composables/**'] }` 를 둔다. **자동 임포트라 이름이 겹치면 한쪽이 조용히 버려진다.** 같은 이름의 컴포저블·타입을 두 곳에 두지 않는다.
* 부정 글롭(`!…`)은 `dirs` 에서 먹지 않는다. `*.types.ts` 와 `mock/` 의 export 는 앱 `nuxt.config.ts` 의 `hooks['imports:dirs']` 훅이 모든 `composables/**` 항목(앱·레이어)에서 뺀다 — 필요한 곳은 명시 import 한다.

### 2-2. API 컴포저블 `useXxxApi` (상태 없음)
* 자원 하나에 파일 하나 — 기준은 화면이 아니라 **API 경로의 주인**이다. 같은 자원의 주소를 두 파일에 적지 않는다.
* 주소는 파일 맨 위 `PATH` 상수로 모은다(경로에 값이 들어가면 함수). 자기 참조는 함수 안에서만 된다(값 자리에서 쓰면 "초기화 전 접근" 오류).
* 서버 호출은 반드시 `useApiClient`(CSRF · 오류 파싱 · `Accept-Language`)를 거친다. 화면·상태 컴포저블이 `$fetch` 를 직접 부르지 않는다.
* 서버 응답은 공통 래퍼 `{success, data, meta}` / `{success:false, error, meta}` 다. `useApiClient` 가 벗겨서 **`data` 만** 돌려주고(`204` 는 `undefined`), 실패는 `error` 로 `ApiError`(`code`·`message`·`fields`·`retryAfterSeconds`·`requestId`)를 던진다. `useXxxApi`·화면은 래퍼를 모른다 — 반환 타입과 목업은 `data` 모양 그대로 둔다. 문의·로그용으로 필요하면 `ApiError.requestId` 만 쓴다.
* API 컴포저블에 `useState` 를 두지 않고, 상태 컴포저블은 요청 세부(경로·헤더)를 모른다.

### 2-3. 상태 컴포저블 `useXxx` (공유 상태)
* 상태는 `useState('고유키', () => 초기값)` 이며 정말 여러 화면이 공유하는 것만 둔다.
* `undefined` = 아직 안 물어봄, `null` = 없음(로그인 안 함 등). 가르지 않으면 "없습니다"가 잠깐 비친다.
* 이미 받았으면 다시 묻지 않는다: `undefined` 일 때만 로드하는 `ensureLoaded()` 를 제공하고 화면이 부른다.
* 로그아웃처럼 안전과 관련된 동작은 서버가 실패해도 화면 상태를 지운다(`try { await api.logout() } finally { me.value = null }`).
* 401 은 오류가 아니라 답일 수 있다(`me` 의 401 = 로그인 안 함). 공통 클라이언트는 "지금 로그인 상태였나"로 세션 끊김을 가른다.

### 2-4. 반응형 인자는 `MaybeRef` 로 받고 읽는 자리에서 푼다
```ts
export function useSomething(id: MaybeRef<string>) {
    const url = computed(() => `/api/x/${toValue(id)}`);   // 읽는 순간에 푼다
    return { url, load: () => api.load(toValue(id)) };
}
// ✗ const v = toValue(id) 를 바깥에서 한 번만 풀면 처음 값에 고정된다
```
화면 값에 반응해야 할 때만 쓴다(인자를 호출 때 받는 `useXxxApi` 메서드에는 불필요).

### 2-5. Nuxt 문맥 함정
* `useNuxtApp()` · `useState()` · `useRoute()` · `useI18n()` · `usePrimeVue()` 는 setup 시점의 문맥을 본다. **`await` 뒤나 콜백(`onResponseError` 등) 안에서 부르면 던지지 않고 조용히 아무 일도 안 한다.** 부르기 전에 붙잡아 두거나 `nuxtApp.runWithContext(...)` 로 감싼다.
* `defineProps` 기본값에는 지역 변수·컴포저블을 쓸 수 없다(타입 검사는 통과하는데 컴파일이 죽는다). 기본값을 비우고 `computed` 로 채운다.

### 2-6. 타입은 주인 옆 `.types.ts` 에
타입은 쓰는 코드와 함께 바뀌므로 `types/` 에 몰아넣지 않는다.

| 타입 | 위치 |
|---|---|
| 서버와 주고받는 모양(요청·응답) | API 컴포저블 옆 `use<자원>Api.types.ts` (예: `MeResponse`, `LoginRequest`) |
| 상태 컴포저블이 따로 정의하는 타입 | 그 옆 `use<자원>.types.ts` |
| 컴포넌트 계약 | `components.md` |
| 유틸 하나가 쓰는 타입 / 한 화면만 쓰는 타입 | 그 `utils/util-*.ts` / 그 `.vue` 안 |
| 주인 없는 업무 횡단 타입(예: `MenuNode`) | `types/` (예외적으로만) |

* `.types.ts` 에는 `interface`/`type` 과 타입에 딸린 상수만 둔다(로직·`use` 호출·Nuxt 의존 금지).
* 서버 DTO 이름을 그대로 쓴다. 같은 이름의 타입을 두 곳에 만들지 않고, 다른 업무의 타입은 그 `.types.ts` 를 import 한다(복사 금지).
* import 는 명시적으로 쓴다: `import type { MeResponse } from '~/composables/auth/api/useAuthApi.types'`.
* 서버 규칙을 미리 검사하는 화면 코드(`utils/util-<업무>.ts`)에는 서버의 해당 클래스·규칙 이름을 주석으로 적는다.

### 2-7. 이름
상태 `use<자원>` · API `use<자원>Api` · 타입 파일 `<주인>.types.ts` · 목업 `api/mock/<업무>-mock.ts`.

---

## 3. 금지 사항 (Anti-Patterns)
1. ❌ 새 컴포저블을 `composables/` 바로 아래에 평평하게 추가
2. ❌ 한 업무에서만 쓰는 컴포저블을 `common/` 에 배치
3. ❌ `.vue`·상태 컴포저블에서 `$fetch` 직접 호출, `/api/` 문자열 작성
4. ❌ 같은 자원의 주소를 두 파일에 분산
5. ❌ API 컴포저블에 `useState`, 상태 컴포저블에 요청 세부
6. ❌ `await` 뒤·콜백 안에서 Nuxt 컴포저블을 문맥 없이 호출
7. ❌ 업무 폴더 순환 import
8. ❌ 새 타입을 `types/` 에 몰아넣기, 같은 타입을 업무마다 복사
