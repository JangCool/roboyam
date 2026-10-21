# Frontend Layers Guide (`layers.md`)

두 앱(`apps/user-web` · `apps/admin-web`)이 **실제로 같이 쓰는** 코드만 `apps-packages/<이름>/` 공유 패키지(Nuxt 레이어)에 둔다. 한 앱만 쓰는 동안은 그 앱 안에 두고, 두 번째 앱이 쓸 때 옮긴다.

## 1. 규약
* **위치·이름**: `apps-packages/<이름>/` 이 Nuxt 레이어이자 pnpm 워크스페이스 패키지 `@roboyam/<이름>` 이다. `package.json`(`name`, `private`, `"type":"module"`, `"main":"./nuxt.config.ts"`, `peerDependencies` + 같은 버전 `devDependencies`)과 `nuxt.config.ts`(`$meta: { name: '<이름>' }`)를 둔다. 코드는 레이어 루트의 `utils/` `composables/` `components/` `plugins/` 에 둔다(`app/` 폴더 금지 — `#layers` 경로가 달라진다).
* **쓰는 법**: 앱 `package.json` 에 `"@roboyam/<이름>": "workspace:*"`, `nuxt.config.ts` 에 `extends: ['@roboyam/<이름>']`(상대 경로 extends 금지). 추가 후 루트에서 `pnpm install`.
* **의존 방향**: 앱 → 레이어. 레이어는 앱을 모르므로 `~/`·`~~/`·앱 코드를 import 하지 않는다. 레이어 코드는 `#layers/<이름>/utils/util-xxx` 로 명시 import 한다. 레이어의 순수 코어(`utils/`)는 `vue`·Nuxt 자동 임포트를 쓰지 않는다.
* **서버 호출 금지**: 레이어에 `$fetch`·`useApiClient`·`/api/` 가 없다. 앱이 서버 값을 받아 레이어 상태(`setXxx`)에 채운다.
* **규약은 앱과 같다**: composables 업무 폴더·타입 `.types.ts`(`composables.md`), 문구 i18n 키(`i18n.md` — 레이어 자신의 문구는 레이어 루트 `i18n/locales/{ko,en}/` 와 `i18n: { vueI18n: 'i18n.config.ts' }`), 컴포넌트(`components.md` — 레이어는 `components/` 바로 아래에 두고 `pathPrefix:false`·`extensions:['vue']` 로 등록).
* **자동 임포트**: 레이어는 `imports.dirs: ['composables/**']` 를 두고, `*.types.ts`·`mock/` 제외 훅은 **앱** `nuxt.config.ts` 에만 둔다(레이어 훅은 앱의 같은 훅에 가려진다). 새 앱에도 같은 훅을 건다.

## 2. 공유 패키지
* **`@roboyam/i18n`** — 언어 설정·공통 문구·`useLanguage`·동적 이름 헬퍼 → `i18n.md`.
* **`@roboyam/display-time`** — 표시 값(언어·시간대·시간 형식·날짜 형식). `@roboyam/i18n` 에 의존한다. 앱이 할 일:
  1. `extends` 에 추가한다.
  2. 서버에서 받은 표시 값을 `useDisplay().setDisplay({ language, zoneId, timeFormat, dateFormat })` 로 채운다(서비스 시간대는 `setServiceZone`). 서버 호출은 앱의 `useDisplayApi` 한 겹이다.
  3. 시각은 UTC ISO 로 저장하고 `const time = useDisplayTime()` 의 `time.listLabel(iso)`·`time.full(iso)` 로 그린다(`UtilTime` 직접 호출·날짜 직접 조립 금지). 시각 입력은 `<AppDateTimePicker v-model="iso" />`(순간 ISO), 시간대와 무관한 날짜는 `<AppDatePicker v-model="ymd" />`(`YYYY-MM-DD`). 표시 값 입력은 `<DisplayValueForm v-model="values" />`(값만 주고받고 저장·서버 호출은 앱 화면이 한다). 표시 값이 바뀌면 형식·시간대가 같이 바뀐다.
