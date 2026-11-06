# 사이트 로고 4칸 계약 (화면 확정 2026-11-06 · 서버 미구현)

사이트 로고를 언어(한글·영문)와 놓이는 바탕(밝은·어두운)에 따라 다르게 보이도록 4칸으로 나눈다. 로고 그림에 사이트 이름이 들어 있으면 옆의 이름 글자를 숨긴다.
지금의 단일 로고(`site-settings-contract.md` 의 `logo_file_id`·`logo_svg`)를 넓히는 일이다. 화면은 목업(`// MOCK:`)으로 확정됐고, 서버와 API는 아직 없다.

## 기본 로고(디자인 확정 — 사용자 결정 2026-11-06)
- 심볼: 로보얌 아바타(`robotikon/src/parts.mjs` 좌표) 머리만 그린다. 웃는 눈(^^), 흰 테두리, 남색 귀를 쓰고 안테나는 줄기 절반 길이다.
  - 밝은 바탕용은 머리가 파랑 `#2F6BFF`, 어두운 바탕용은 흰색이다.
- 글자는 개구체(Gaegu 700)를 쓰고 윤곽을 0.5만큼 「살짝 굵게」 한다(더 굵게는 탈락). 글자는 패스로 굳혀 두어서 웹 글꼴이 필요 없다.
  - 한글: 「로보**얌**」 옆에 작은 「Roboyam」을 붙인다(R 대문자).
  - 영문: 「Robo**yam**」만 쓴다.
  - 강조색: 밝은 바탕에서는 파랑, 어두운 바탕에서는 노랑 `#FFC83D` 이다.
- 정렬: 머리 몸통의 세로 중앙을 글자에 맞추고, 안테나는 위로 튀어나오게 둔다.
- 파일: `apps/{admin,user}-web/public/brand/roboyam-logo-{ko,en}-{light,dark}.svg`(viewBox 높이 40, 한글 198 · 영문 176 너비, 루트 width·height 없음, 글자만 영역 `<view id="wordmark">` 포함).
  - 생성 스크립트: `robotikon/logo/build.mjs`(opentype.js + @fontsource/gaegu, `STROKE=0.5 SUB=16 node build.mjs <출력폴더>`).

## 화면
- **admin-web** `/settings/general` → 「기본 정보」의 「로고」
  - 칸은 ① 한글·밝은 ② 한글·어두운 ③ 영문·밝은 ④ 영문·어두운이고 2×2로 놓는다(`sm` 미만에서는 한 열). 부품은 `components/site/SiteLogoVariantTile.vue` 이다.
  - 칸마다 파일 올리기, SVG 소스로 설정, 제거가 있다. 기존 로고처럼 저장 단추 없이 즉시 적용한다. 검사는 기존 `UtilSiteSetting.logoFile`·`logoSvg` 를 쓴다.
  - 빈 칸은 대신 쓰일 그림을 흐리게 보이고 「비어 있음 — ①을 대신 써요」처럼 안내한다.
  - 스위치 「로고에 사이트 이름이 들어 있음(옆 이름 글자 숨김)」이 있다.
  - 칸마다 「모바일」 미리보기(사용자 결정 2026-11-06 — 모바일 로고 칸을 따로 두지 않음): 공개 헤더와 같은 그림 + `#wordmark`, 높이 28px. 안내 한 줄은 SVG 원문에 `<view id="wordmark">` 가 있고 루트 width·height 가 없으면 「모바일에서는 글자 부분만 보여요」, 아니면(PNG 포함) 「모바일에서도 로고 전체가 보여요 + 넣는 방법」(`UtilSiteLogo.wordmarkOf`).
  - 미리보기 바탕: 밝은 바탕용은 흰색, 어두운 바탕용은 진한 회색이다.
- **user-web** `components/shell/SiteBrand.vue` 가 쓰이는 자리: 헤더(PC·모바일), 모바일 메뉴 서랍, 로그인, 설치 화면.
  - 언어: `useLanguage()` 가 `en` 이면 영문 칸을 쓴다.
  - 바탕: 밝은 바탕용과 어두운 바탕용 그림을 함께 두고, CSS 역할 변수 `--brand-logo-light`·`--brand-logo-dark`(`main.css`·`palettes.css`)가 하나만 보인다.
    - 어두운 바탕용: 기본 테마의 파란 바, 진한 색상 테마, 야간 모드 전체
    - 밝은 바탕용: 흰 모바일 바, 옅은 색상 테마, 네이버형·다음형 흰 줄, 서랍·로그인·설치
  - 크기: 높이 28px(md 이상 32px), 너비 최대 220px, 모바일은 화면 폭의 45%까지.
  - 모바일(md 미만, 사용자 결정 2026-11-06): 아이콘을 빼고 글자만 보인다. `<picture><source media="(max-width: 767.98px)" srcset="…#wordmark">` 로 SVG 안의 `<view id="wordmark" viewBox="…">` 영역만 그린다. 기본 로고의 이 영역은 심볼만 뺀 글자다(한글은 「로보얌 Roboyam」, 영문은 「Roboyam」). 글자는 아이콘이 있던 자리(메뉴 단추 바로 옆)부터 시작한다.
    - `wordmark` 영역이 없는 SVG 나 PNG 는 조각 주소가 무시되어 로고 전체가 보인다.
    - ⚠ SVG 루트에 `width`·`height` 가 있으면 Chrome 이 영역을 무시한다. 기본 로고는 `viewBox` 만 둔다. 운영자가 올린 SVG 에 대해서는 안내만 할지, 서버가 루트 크기를 지워 줄지 정해야 한다(미확정).
  - 「이름 포함」이 켜져 있으면 이름 글자를 숨기고 `<img alt>` 에 사이트 이름을 넣는다. 꺼져 있거나 쓸 그림이 없으면 이름 글자를 보이고 `alt=""` 로 둔다.

## 정책
- 대체 순서(`util-site-logo.ts` `UtilSiteLogo.resolve`, 두 앱 같음): 바탕을 언어보다 먼저 맞춘다.
  - ① 자기 칸만
  - ② → ①
  - ③ → ①
  - ④ → ② → ③ → ①
  - 모두 없으면 이름 글자만 보인다. 못 불러온 그림은 건너뛰고 다음 칸으로 간다.
- 서버는 원본 4칸만 준다. 대체 순서는 화면 규칙이다.
- 지금 서버에 있는 단일 로고는 ① 한글·밝은 칸으로 옮긴다(마이그레이션).

## 필드·검증 (후보)
- 칸 `koLight`·`koDark`·`enLight`·`enDark`: 각각 null 을 허용하고, 값은 파일 주소(`/media/…`) 또는 SVG data URI 다. 파일·SVG 검사는 지금 로고와 같다(형식·2MB·한 변 4000px, SVG 위험 요소는 거절).
- `nameInLogo` boolean: 기본값 후보는 false(기존 사이트는 이름 글자가 계속 보이게). 데모와 로보얌 설치 기본값은 true.

## API 후보 (`useSiteSettingsApi.ts` 의 `// REAL:` 줄)
- 관리자(`site.read`·`site.manage`). 응답은 매번 4칸 전체와 `nameInLogo` 다.
  - `GET /api/admin/site/logos`
  - `PUT /api/admin/site/logos/{variant}` `{fileId}` · `PUT /api/admin/site/logos/{variant}/svg` `{svgSource}` · `DELETE /api/admin/site/logos/{variant}`
  - `PUT /api/admin/site/logos/name-in-logo` `{nameInLogo}`
- 공개: `GET /api/site/info` 에 `logos{koLight,koDark,enLight,enDark}`, `logoNameIncluded` 를 더한다. 하위 호환을 위해 `logoUrl` 은 ① 값으로 남긴다.
- 저장 후보:
  - (a) `site_settings` 에 칸별 `logo_*_file_id`·`logo_*_svg` 와 불리언을 더한다. 지금 `logo_*` 는 ①로 본다.
  - (b) 별도 표 `site_logos(variant, file_id, svg)`

## 미확정
- 저장 방식 (a)·(b).
- `nameInLogo` 기본값.
- 관리자 앱 상단 `SiteBrand` 는 20×20 정사각이라, 가로로 긴 로고가 아주 작게 보인다. 관리자 상단도 4칸 중 하나를 쓸지, 아이콘을 쓸지 정해야 한다(이번에는 그대로 둠).
- `util-site-logo.ts` 가 두 앱에 복사되어 있다. 공용 레이어(`apps-packages`)로 옮길지 정해야 한다.
- 쓰지 않는 옛 i18n 키(`site.logoHint`·`logoAdd`·`logoChange`·`logoRemoveMessage`)를 정리할지.
- 기본 로고 4장을 설치 때 기본값으로 넣을지(`boardPreset ROBOYAM` 처럼), 데모 데이터에만 넣을지.
