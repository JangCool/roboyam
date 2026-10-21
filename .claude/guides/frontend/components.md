# Frontend Components Guide (`components.md`)

## 1. 역할 및 기본 원칙
* **UI 렌더링 전담**: 컴포넌트는 `props`/`emits` 로만 받고 전달한다. 서버 호출·공유 상태는 페이지가 컴포저블로 받아 내려준다(컴포넌트에서 `$fetch`·API 컴포저블 호출 금지).
* **두 화면 이상이 쓰는 조각만 `components/` 에 둔다.** 한 화면 전용 조각은 그 페이지 안에 둔다.
* **모양이 같으면 컴포넌트도 하나다.** 같은 UI 를 화면마다 따로 만들면 같은 것을 고르는데 다르게 보인다. 새 부품을 만들기 전에 `components/` 를 검색한다.

---

## 2. 주요 업무 및 규약

### 2-1. 폴더와 이름
```text
components/
├── common/   # 업무와 무관한 공용 부품: ErrorState, EmptyState, LabeledField, PasswordField, StatusPanel …
├── shell/    # 앱 셸(헤더·메뉴·시트·대화상자): App* 접두어
└── <업무>/   # install/, auth/, board/ … 업무 전용 조각
```
* `nuxt.config.ts` 는 `components: [{ path: '~/components', pathPrefix: false, extensions: ['vue'] }]` 이다. `pathPrefix:false` 라 **폴더 이름이 컴포넌트 이름에 붙지 않으므로 파일 이름은 프로젝트 전체에서 유일**해야 한다(겹치면 자동 임포트가 한쪽을 조용히 버린다). 업무 전용은 업무 접두어(`InstallTokenStep`), 셸 조각은 `App` 접두어(`AppHeader`). 컴포넌트는 파스칼 케이스.
* `extensions:['vue']` 는 `components/` 안의 `.types.ts` 가 컴포넌트로 등록되지 않게 한다.
* 컴포넌트 계약 타입(props·emit 보조 타입)은 작으면 그 `.vue` 안, 다른 파일도 쓰거나 크면 옆의 `<컴포넌트>.types.ts` 에 둔다(`types/` 에 빼지 않는다). 규칙은 `composables.md` 의 타입 절.

### 2-2. 속성: `interface Props` + `withDefaults`
* 속성은 `interface Props` 로 정의하고 `withDefaults(defineProps<Props>(), { … })` 로 받는다. 선택 속성(`?`)은 모두 기본값을 적고, 불리언도 `false` 를 명시한다. 배열·객체 기본값은 함수(`() => []`). 필수 속성만 있어도 `{}` 를 넘긴다.
* 다른 파일이 같은 속성 모양을 쓰면(래퍼 등) `<컴포넌트>.types.ts` 에 `<컴포넌트>Props` 로 내보낸다.
* 기본값에는 지역 변수·컴포저블·`t()` 를 쓸 수 없다(컴파일이 죽는다). 기본값은 비우고 `computed` 로 채운다 → `composables.md` Nuxt 문맥 함정.

### 2-3. HTML ID 와 공통 부품
* HTML `id` 는 `useId()` 로 만든다. 한 화면에 같은 컴포넌트가 둘 이상 놓일 수 있어 고정 문자열이면 라벨 클릭이 엉뚱한 곳을 켠다.
* 필수 공통 부품은 해당 UI 가 처음 필요해질 때 `components/common/` 에 한 번만 만들고 이후 그것만 쓴다.
    * 수치/퍼센트: `UsageBar`(0~100% 구간 색, `null` 은 "—")
    * 다중 선택: `AppMultiSelect`("전체 선택"을 반드시 보인다). PrimeVue `MultiSelect` 를 화면에서 직접 쓰지 않는다
    * 화면 안 좌측 서브메뉴: `AppSideNav`
    * 빈 화면/오류 화면: `EmptyState` · `ErrorState`. "데이터가 없습니다"를 화면마다 적지 않는다
* 이미 있는 공통 부품: `ErrorState` `EmptyState` `StatusPanel` `LabeledField` `PasswordField` `MailFields` `SiteFields` `GradeBadge` `StaffBadge`(`common/`), `PasswordRules` `SocialLoginButtons` `EmailVerifyBanner`(`auth/`), `AppToaster` `AppConfirm` `AppSheet` `AppDrawer`(`shell/`).
* 화면 문구는 템플릿에 박지 않고 i18n 키로 쓴다 → `i18n.md`.

### 2-4. 뱃지·태그 글자 정렬
* **뱃지(PrimeVue `Tag`·`Badge`, 직접 만든 알약 모양 포함) 안의 글자는 상하좌우 중앙에 놓는다.** 글자가 뱃지 아래쪽으로 붙어 어긋나 보이면 안 된다(폰트 줄 높이가 위아래로 비대칭이라 기본 줄 높이에서 자주 생긴다).
    * 뱃지 자체를 `inline-flex items-center justify-center` 로 두고 `leading-none`(줄 높이 1)을 준다. 위아래 여백은 `py-*` 로 **대칭**으로만 주고, 글자에 `pt-*`·`mt-*` 같은 한쪽 여백 보정을 넣지 않는다.
    * **한글 글꼴은 글자가 줄 상자 중앙보다 약 0.09em 아래에 앉는다**(실측: 11px 에서 약 1px, 알약 안에서 글자가 아래로 붙어 보이는 주원인). 줄 상자만 가운데에 맞춰서는 해결되지 않으므로 **글자 요소(`GradeBadge` 의 `__label`, PrimeVue `.p-tag-label`)에만** `position: relative; top: -0.09em` 을 준다. 뱃지 전체나 여백을 옮기지 않고, 이 보정은 이 방법 하나로만 한다(`GradeBadge.vue` 참고).
    * 뱃지 안에 아이콘·개수·글자를 함께 넣을 때도 안쪽 래퍼를 `inline-flex gap-*` 로 묶고 안쪽 글자는 기본 `line-height` 를 따로 키우지 않는다. **아이콘과 글자가 함께 있으면 `items-center` 가 아니라 `items-baseline`(같은 기준선)** 으로 세운다 — 아이콘 상자와 글자 상자는 소수점 위치가 달라, 뱃지가 화면 소수점 위치(.25~.5)에 놓이면 가운데 맞춤은 한쪽만 1px 높게 찍힌다(실측). 같은 기준선이면 둘이 항상 같이 반올림된다. 이때 한글 글자가 내려앉는 보정은 글자가 아니라 **위 여백을 줄이고 아래 여백을 같은 만큼 늘려** 아이콘과 함께 올린다(`GradeBadge.vue` 참고).
    * **텍스트 옆·뒤에 뱃지를 붙이는 줄**은 부모를 `inline-flex`/`flex` + `items-center` 로 둔다. 베이스라인 정렬(`items-baseline`, 기본 inline 배치)에 맡기지 않는다. 뱃지가 글자 줄 아래로 처지는 원인이다.
    * 같은 모양의 뱃지는 화면마다 클래스를 새로 쓰지 않고 공통 부품(`GradeBadge` `StaffBadge` 등)이나 같은 클래스 조합을 쓴다 → 위 "모양이 같으면 컴포넌트도 하나다".
    * 새 뱃지를 만들거나 고친 뒤에는 브라우저에서 글자가 위아래 중앙인지 직접 본다(다크 모드·글자 크기 배율 포함). 타입 검사로는 안 잡힌다.

---

## 3. 작성 예시
```vue
<script setup lang="ts">
interface Props {
  label: string
  modelValue?: string
  items?: string[]
}
const props = withDefaults(defineProps<Props>(), { modelValue: '', items: () => [] })
defineEmits<{ (e: 'update:modelValue', value: string): void }>()
const inputId = useId()
</script>
<template>
  <label :for="inputId">{{ label }}</label>
  <input :id="inputId" :value="props.modelValue" @input="$emit('update:modelValue', ($event.target as HTMLInputElement).value)" />
</template>
```

---

## 4. 금지 사항 (Anti-Patterns)
1. ❌ 컴포넌트에서 `$fetch`·API 컴포저블 직접 호출
2. ❌ HTML ID 하드코딩
3. ❌ 폴더만 다르고 파일 이름이 같은 컴포넌트
4. ❌ 같은 모양의 UI 를 화면마다 따로 구현
5. ❌ 컴포넌트 계약 타입을 `types/` 로 이동
6. ❌ `defineProps<{ … }>()` 인라인 나열, `withDefaults` 밖의 기본값
7. ❌ 템플릿에 서버 주소(`/api/…`)나 긴 문구 직접 작성
8. ❌ 뱃지 글자를 중앙정렬하지 않고 기본 줄 높이·베이스라인 정렬에 맡기기(글자가 아래로 붙는다), 한쪽 여백(`pt-*`)으로 보정하기
