# 로고 보관함·보이기 규칙 계약 (화면 확정 2026-11-18)

사이트 로고를 「묶음(한 벌 = 4칸)」으로 보관하고, 기본·기념일·회원 성별에 따라 다른 묶음을 보인다. 사용자 재미를 위한 기능이다.
지금 4칸 로고(`site-logo-variants-contract.md`)를 넓힌 것 — 4칸의 뜻·대체 순서·SVG 검사·공개 SVG 주소는 그 문서가 정본이고, 이 문서는 묶음·규칙·성별만 다룬다.

## 화면
- admin-web `/settings/general` → 「사이트 기본 정보」의 「로고」 줄(오늘 보이는 로고 요약 + 「로고 관리」 단추) → 모달 「로고」.
  - 로고 보관함: 묶음을 줄 형식으로(밝은·어두운 바탕 한글 칸 미리보기, 이름, 배지 「기본 로고」/「규칙 n개」/「보관 중」, 올린 날, 「4칸 중 n칸」, [보기]/[고치기]). 「새 로고 올리기」. **삭제 없음**.
  - 보이기 규칙: 기본 로고 · 기념일 규칙(줄 더하기·고치기·빼기·켬/끔·순서) · 성별 규칙(남성·여성). 구획의 [저장] 한 번.
- user-web: 가입(`/signup` 닉네임 단계, `/signup/social`)과 `/settings/profile` 에 성별 칸. `SiteBrand.vue` 가 규칙에 맞는 묶음을 보인다.

## 정책
- 기본 제공 묶음 2개는 **설치 종류(데모 포함·구조만)와 무관하게 늘 있다**: 「캐릭터 로고」(`CHARACTER`, 마스코트 + 개구체), 「글자 로고」(`WORDMARK`, Gothic A1 + Space Grotesk). 고치지 못한다. 그림 원본은 `robotikon/logo/`.
- 올린 묶음은 이름·칸·「이름 포함」을 고칠 수 있고 지우지 못한다(보관해 나중에 다시 고른다). 한 칸 이상 채워야 저장된다. 이름 1~30자.
- 「로고에 사이트 이름이 들어 있음」은 묶음마다 둔다(기본 제공 둘은 켜짐).
- 보이는 묶음 고르기(용어: 「평소 로고」 → **「기본 로고」**, 2026-11-18): 평소에는 **성별 로고 → 기본 로고**, 기념일에는 **그 회원에게 보일 로고(성별 로고가 있으면 그것, 없으면 기본 로고)의 유형에 맞는 기념일 벌**.
  - 기념일: 켜진 줄 가운데 오늘(서비스 시간대 날짜)에 걸리는 맨 위 줄. 날짜 정하는 법은 ① 날짜 직접(시작~끝, 「매년 반복」이면 월·일만 — 해 넘기는 기간 허용) ② 쉬는 날 표의 이름(그 이름의 날마다 — 해마다 날짜가 바뀌는 날용). 어느 벌을 보일지는 아래 「기념일 로고 유형」.
  - 성별 로고: 로그인한 회원의 성별이 남성·여성이고 그 성별에 묶음이 정해져 있을 때.
  - 기본 로고: 그 밖의 모든 경우(비로그인, 「밝히지 않음」 포함).
- 회원 성별: 선택 항목, `MALE`·`FEMALE`·`NONE`(기본 `NONE` = 밝히지 않음). 가입과 내 정보 수정에서 받는다. **다른 회원·공개 프로필에 내보내지 않는다** — 로고 고르기에만 쓴다. 기존 회원은 `NONE`.
- 권한: 조회 `site.read`, 변경 `site.manage`(새 권한 없음).

## 데이터 (후보)
- 묶음: `id, name, builtInKey(null|CHARACTER|WORDMARK), nameInLogo, logos{koLight,koDark,enLight,enDark}(칸마다 파일 또는 SVG 소스), createdAt`
- 규칙: `defaultBundleId`, `anniversaries[]`(차례 = 우선순위: `id, name, enabled, mode(DATES|HOLIDAY), startDate, endDate, yearly, holidayName, bundleId`), `gender{male, female}`(묶음 id 또는 null)
- 회원: `gender`

## API 후보
- 관리자(응답은 늘 묶음 목록 + 규칙 전체)
  - `GET /api/admin/site/logo-library`
  - `POST /api/admin/site/logo-library/bundles` · `PUT /api/admin/site/logo-library/bundles/{id}`
  - `PUT /api/admin/site/logo-library/rules`
  - 파일은 기존 `POST /api/admin/files`(SITE_LOGO)로 올린 뒤 `fileId`, SVG 는 소스 글자열.
- 공개 `GET /api/site/info`: `logos`·`logoNameIncluded`·`logoUrl` 은 **오늘의 성별 무관 묶음**(기념일이 걸리면 그 묶음, 아니면 기본 로고)으로 채운다. `genderLogos{male,female}`(각 `{logos, nameIncluded}` 또는 null)을 더한다 — 평소에는 성별 로고, 기념일에는 그 성별 회원용 기념일 벌이 `logos` 와 다른 묶음일 때만(아래 「기념일 로고 유형」). 화면이 로그인 회원의 성별로 고른다.
- 회원: 가입 요청(이메일·소셜)과 내 프로필 조회·수정에 `gender`.

## 옮기기
- 지금 `site_settings` 4칸 로고: 기본 캐릭터 로고와 같은 그림이거나 모두 비었으면 기본 로고 = 「캐릭터 로고」. 운영자가 올린 그림이 있으면 그 4칸을 올린 묶음 「이전 로고」로 옮기고 기본 로고로 둔다.
- 옛 `/api/admin/site/logos/**`·`/api/admin/site/logo**` 는 화면이 더 쓰지 않는다.

## 서버 구현 2026-11-18 (화면 연결 전 — 컴파일만 확인)
- **저장**: shared **V249** `site_logo_bundles`(칸마다 `*_file_id` → files 또는 `*_svg`, 기본 제공 둘은 id 1·2 고정 + `built_in_key`) · `site_logo_rules`(기념일 줄, `sort_order` = 우선순위, 날짜는 글자 `YYYY-MM-DD`/`MM-DD`) · `site_settings.logo_default_bundle_id`(기본 1)·`logo_male_bundle_id`·`logo_female_bundle_id`. shared **V250** `profiles.gender`(기본 `NONE`).
- **옮기기(V249)**: 4칸이 모두 비었거나 네 칸 모두 기본 캐릭터 SVG(V246 이 넣은 그림·지금 그림의 md5)면 기본 로고 = 1. 아니면 4칸을 묶음 id 3 「이전 로고」로 옮기고 기본 로고 = 3. **옛 칸(`logo_file_id`·`logo_svg`·`logo_{ko_dark,en_light,en_dark}_*`·`logo_name_included`)은 같은 마이그레이션에서 지웠다** — 로고의 원천은 묶음 하나.
- **옛 API 삭제**: `/api/admin/site/logos/**`·`/api/admin/site/logo**`(`SiteLogosController`·`SiteLogoController`·`SiteLogoService`)를 지웠다. `SiteBrandInstaller` 는 로고를 넣지 않는다(아이콘·링크 미리보기만) — 설치 구성과 무관하게 기본 로고의 처음 값이 「캐릭터 로고」다.
- **① 칸을 쓰던 곳**(관리자 `me`·기본 정보 `logoUrl`·메일 로고): 기본 로고 묶음의 한글·밝은 칸(`SiteAssets.logoUrl` — 비었으면 채워진 첫 칸, 메일은 한글·밝은 칸만). 기념일·성별은 타지 않는다. `index.html` 스플래시는 사이트 정보의 `logoUrl`(오늘의 묶음)을 쓴다.
- **관리자 API**(`SiteLogoLibraryController`·`SiteLogoLibraryService`) — 응답은 늘 `{bundles[], rules}`:
  - 묶음 id 는 숫자 글자(기본 제공 `"1"`·`"2"`). 묶음 본문 `{name, nameInLogo, cells:{koLight: {fileId}|{svgSource}|"KEEP"|null, …}}` — 칸을 빼면 `"KEEP"` 과 같다.
  - 오류: 400 `VALIDATION` `fields.name`·`cells`(한 칸 이상)·`cells.koLight`…(파일·SVG 검사)·`defaultBundleId`·`anniversaries[i].name|mode|startDate|endDate|holidayName|bundleId`·`gender.male|female` · 404 `SITE_LOGO_BUNDLE_NOT_FOUND` · 409 `SITE_LOGO_BUNDLE_NOT_EDITABLE`.
  - 규칙 저장은 기념일 줄을 통째로 갈아 끼운다 — 요청의 줄 `id` 는 쓰지 않고 응답에 새 id 가 온다. 줄 수 한도 50.
  - 감사 `SITE_LOGO_BUNDLE_CREATE`·`SITE_LOGO_BUNDLE_UPDATE`·`SITE_LOGO_RULES_UPDATE`(`CacheAutoRefresh` 의 `SITE_SETTINGS_SAVE` → `site.info` 비움).
- **공개 `GET /api/site/info`**: `logos`·`logoNameIncluded`·`logoUrl` = 오늘(서비스 시간대 `ServiceTime`)의 성별 무관 묶음. `genderLogos{male,female}`(각 `{logos, nameIncluded}` 또는 null)는 평소에는 성별 묶음이 하나라도 정해졌을 때, 기념일에는 성별 회원용 기념일 벌이 `logos` 와 다른 성별이 하나라도 있을 때만 있다(없으면 키 자체가 없다 — 「기념일 로고 유형」 참고). SVG 칸 주소는 `/api/site/logos/{칸}.svg?b=<묶음 id>&v=<지문>`(`b` 없으면 오늘의 묶음).
- **날짜와 캐시**: `PublicSiteService` 가 사본의 로고를 고른 날을 기억하고, 날짜가 바뀐 뒤 첫 요청이 사본을 버리고 다시 읽는다(서버마다 스스로 — 배치 없음). 쉬는 날 표 변경은 `site.info` TTL(기본 20초) 안에 닿는다.
- **회원 성별**: `POST /api/auth/signup`·`POST /api/auth/social/complete` 본문 `gender`(선택, 없으면 `NONE`, 틀리면 `fields.gender`) · `GET/PUT /api/me/profile` 의 `gender`(PUT 은 없으면 그대로) · `GET /api/auth/me` 의 `user.gender`. 공개 프로필·다른 회원 응답·관리자 회원 화면에는 없다. 탈퇴 파기(`WithdrawalEraseStore.anonymizeProfile`)가 `NONE` 으로 되돌린다.
- 기본 제공 묶음의 그림을 바꾸려면 새 마이그레이션이 id 1·2 의 SVG 칸을 고친다(`install-presets/brand/logo-*.svg`·`wordmark-*.svg` 는 그 사본).

## 화면 연결 2026-11-18 (타입 검사만 — 실제 호출 확인 전)
- **admin-web**: `useSiteLogoLibraryApi` 를 실제 API 로 바꾸고 목업(`mock/site-logo-library-mock.ts`)을 지웠다. 묶음 저장은 파일 칸을 먼저 `POST /api/admin/files`(SITE_LOGO)로 올려 `{fileId}` 로 바꾸고, 안 바꾼 칸 `"KEEP"`·비운 칸 null 을 보낸다. 올리기가 거절되면 그 칸 오류(`cells.<칸>`)로 보인다.
  - 서버 칸 오류는 그대로 그 자리에: 묶음 창 `name`·`cells`·`cells.<칸>`(그 타일 아래), 규칙 `defaultBundleId`·`gender.male|female`(그 고르개 아래)·`anniversaries[i].…`(그 줄 아래)·`anniversaries`(목록 아래). 404·409 는 창·구획 아래 한 줄.
  - 규칙 저장 뒤 응답으로 다시 채운다(기념일 줄 id 가 새로 온다). 묶음·규칙을 저장하면 관리자 상단 로고(`setSiteLogoUrl`)를 기본 로고 묶음의 한글·밝은 칸(비었으면 채워진 첫 칸)으로 맞춘다.
  - 기본 제공 묶음은 id 가 아니라 `builtIn` 으로 가린다. 쉬는 날 예시 목록(목업)은 없앴다 — 표를 못 받거나 비면 안내만 보인다.
  - `useSiteSettingsApi` 의 옛 단일 로고·4칸 메서드와 타입, 안 쓰는 `site.logo*` 문구를 지웠다(타일 `SiteLogoVariantTile` 은 묶음 창이 쓴다).
- **user-web**: 가입(이메일·소셜) 본문과 `PUT /api/me/profile` 에 `gender` 를 보내고 `GET` 의 `gender` 를 읽는다(목업 `mock/profile-gender-mock.ts` 삭제). 로그인 정보의 `user.gender` 는 `AuthUser.gender` 로 담긴다.
  - `SiteBrand`: `genderLogos` 에 로그인 회원의 성별(MALE·FEMALE) 쪽 묶음이 있으면 그 `logos`·`nameIncluded`, 아니면 `logos`·`logoNameIncluded`. 4칸 대체 순서는 그대로. 프로필에서 성별을 저장하면 로그인 정보를 다시 받아 새로 고침 없이 로고가 바뀐다.
  - `UtilSiteInfo.cleanGenderLogos` 가 `genderLogos` 를 거른다(쓸 그림이 한 칸도 없는 쪽은 null).
- 로그인·로그아웃으로 성별이 바뀌는 경우도 `SiteBrand` 가 로그인 상태를 읽으므로 바로 따라간다. 단, 사이트 정보(`genderLogos`)는 앱 시작 때 받은 값이라 운영자가 규칙을 바꾼 것은 새로 고침 뒤에 보인다(지금 로고와 같다).

### 기념일 로고 기본 등록 (사용자 결정 2026-11-18 — 설치 직후 보관함·규칙에 전부 있다)
- shared **V251**(약 1.5MB — `scripts/build-holiday-logo-migration.mjs` 가 `design/brand/holiday-logos/` 에서 만든다, 손으로 고치지 않는다): `site_logo_bundles.day_key`(그날의 키) 추가, 기본 제공 묶음 56개(캐릭터 벌 id 101~128 · 글자 벌 201~228, 이름 「삼일절 (캐릭터)」·「삼일절 (글자)」, 이름 포함 켬), 기념일 규칙 28줄(id 101~128, 모두 켬, 달력 순 — 이미 있는 줄 뒤에 붙는다). 설치 구성·데모 여부와 무관하고 이미 설치된 사이트도 받는다(데모 팩은 이 표들을 담지 않는다).
- 벌은 `built_in_key`(`CHARACTER` = 귀여운 버전 · `WORDMARK` = 일반 버전)가 말한다. 유일 조건은 (`built_in_key`, `day_key`) 한 쌍.
- 양력 날은 「날짜 직접 · 매년 반복」 하루, 음력 세 날은 쉬는 날 표의 이름 `설날`·`부처님오신날`·`추석`.
- **두 벌 고르기**(`SiteLogoResolver.matchStyle`): 규칙이 기본 제공 기념일 묶음을 가리키면 기준 로고(그 사람에게 평소 보일 묶음 — 기본 로고 또는 성별 로고)의 벌에 맞는 짝을 보인다 — 기준 로고가 글자 로고 계열(`built_in_key = WORDMARK`)이면 글자 벌, 그 밖(캐릭터 로고·올린 묶음)은 캐릭터 벌. 올린 묶음을 가리키는 규칙은 그대로(성별 무관). 기본 규칙은 캐릭터 벌 묶음(101~128)을 가리킨다.
- 관리자 응답의 묶음에 `style`(`CHARACTER`·`WORDMARK`·null)과 `dayKey`(날 키·null)를 더했다.
- 그림을 고치면: 적용 전에는 스크립트를 다시 돌려 V251 를 다시 쓰고, 적용된 뒤에는 `node scripts/build-holiday-logo-migration.mjs V<번호>__이름.sql --update` 로 SVG 칸만 고치는 새 마이그레이션을 뽑는다.

### 기념일 로고 유형 (서버 2026-11-18 — 컴파일만 확인, 화면 연결 전)
- shared **V252** `site_settings.holiday_logo_style`(varchar(16), NOT NULL, 기본 `AUTO`, CHECK `AUTO`·`CHARACTER`·`WORDMARK`) — 다음 shared 번호는 **V253**.
- 뜻: `AUTO` = 그 회원에게 평소 보일 로고의 유형에 맞춤 — 방문자·성별 로고가 없는 회원은 기본 로고, 남성(여성) 회원이고 남성(여성) 로고가 정해져 있으면 그 묶음의 유형(위 「두 벌 고르기」 판정) · `CHARACTER` = 모두에게 캐릭터 벌 · `WORDMARK` = 모두에게 글자 벌(성별 무관). 규칙이 기본 제공 기념일 묶음(`dayKey` 있음)을 가리킬 때만 그날의 해당 벌 짝으로 바꾼다(짝이 없으면 원래 묶음). 올린 묶음을 가리키는 규칙은 성별과 무관하게 그대로.
- 계산은 `SiteLogoResolver.on` 한 곳: `Today.bundle` = 성별 무관 묶음(기본 로고 기준), 기념일의 `Today.male`·`female` = 그 성별 회원용 벌이 `bundle` 과 **다른 묶음일 때만**(같으면 null). 메일·관리자 상단처럼 로그인 맥락이 없는 곳은 성별을 타지 않는다.
- 관리자 API: 응답 `rules.holidayStyle`(늘 셋 중 하나) · `PUT …/rules` 본문 `holidayStyle`(선택 — 없거나 null 이면 지금 값 그대로, 셋 밖이면 400 `VALIDATION` `fields.holidayStyle`). 감사는 기존 `SITE_LOGO_RULES_UPDATE` 에 `holidayStyle` 값만 더했다.
- 공개 `GET /api/site/info` 는 모양(필드·`SiteLogoSetView`)이 그대로다 — 기념일에도 위 조건이면 `genderLogos.male`/`female` 에 그 성별용 기념일 벌이 실린다(예: 기본 로고 = 캐릭터, 남성 로고 = 글자 로고 → 기념일에 `logos` 는 캐릭터 벌, `genderLogos.male` 은 글자 벌, `female` 은 null). user-web `SiteBrand` 는 `genderLogos` 가 있으면 그것을 쓰므로 화면 수정이 없다.

## 미확정
- 설치 구성이 아이로빗이 아니어도(구조만·다른 구성) 기본 로고가 「캐릭터 로고」(아이로빗 그림)로 시작한다 — 예전에는 그런 사이트는 이름 글자만 보였다(V246 은 아이로빗 구성에만 넣었다).
- 성별 수집에 따른 약관·개인정보 처리방침 문구(수집 항목 추가) — 법무 확인.
- 기념일 규칙이 대체공휴일에도 걸리게 할지(지금은 쉬는 날 표에서 그 이름인 날만).
- 관리자 왼쪽 메뉴에 「로고」 항목을 따로 둘지(지금은 일반 설정 안 모달).
- 기본 제공 묶음 이름의 영어 표기.
- 공공데이터에서 받은 쉬는 날 표는 설날·추석 연휴 사흘이 모두 「설날」·「추석」 이름일 수 있다(기본값 시드는 「설날 전날」·「설날 다음날」로 나뉜다) — 그러면 기념일 로고가 연휴 사흘 내내 걸린다. 당일만 걸지 정해야 한다.
- 관리자 보관함 응답이 기념일 묶음 56개의 SVG(data URI)까지 실어 약 2MB 다 — 화면이 느리면 묶음 그림을 주소로 따로 받게 바꾼다.

## 기념일 로고 (그림 확정 2026-11-18)
- 28일 × 두 벌(귀여운 버전 = 캐릭터 로고의 개구체 글자, 일반 버전 = 글자 로고) × 4칸 = 224장. 확정본은 `design/brand/holiday-logos/<날>/`, 만드는 법은 `robotikon/logo/`(`node build-all.mjs <폴더>`, 그림의 원천은 `mascot.mjs`).
- 공통 규칙: 글자와 글자 색은 평소 그대로. 한글판은 작은 글자 자리에 이름(위)과 날짜(아래), 영문판은 날짜만. 이름은 줄이지도 끊지도 않는다(긴 날은 로고가 길어진다).
- **해에서 사방으로 뻗는 빛살 무늬는 쓰지 않는다**(전범기 연상). 붉은 동그라미만 두는 표현도 피한다(방패연 꼭지는 태극).
- 태극기는 건·감·리·곤을 제대로 그리고 괘가 깃면 밖으로 나가지 않게 한다.
- 캐릭터 없이 상징만 쓰는 날(11): 새해 첫날 · 설날 · 삼일절 · 어버이날 · 스승의 날 · 현충일 · 6·10 민주항쟁 · 6·25 전쟁일 · 광복절 · 일본군 '위안부' 피해자 기림의 날 · 추석.
- 캐릭터에 그날의 특징과 표정을 얹는 날(17): 제헌절 · 개천절 · 한글날 · 국군의 날 · 광복군 창설일 · 4·3 추념일 · 임시정부 수립일 · 4·19 혁명 · 5·18 민주화운동 · 학생독립운동 · 순국선열의 날 · 식목일 · 어린이날 · 부처님오신날 · 성탄절 · 과학의 날 · 정보통신의 날.
- 고증: 광복군 군모·국군 철모·학생모는 사용자가 준 사진 기준, 그 밖의 상징은 일반적으로 알려진 것(자료 대조 없음). 근거는 `mascot.mjs` 주석.
- 글꼴(패스로 굳혀 씀, 모두 OFL): Gaegu · Gothic A1 · Space Grotesk · 나눔손글씨 붓(광복절 붓글씨) — 오픈소스 고지에 넣어야 한다.
