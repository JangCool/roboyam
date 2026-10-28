# 사이트 일반 설정 계약 (**화면 확정 2026-10-21** — 서버는 `roles-contract.md` 의 1·2단계 뒤에 구현)

**의도**: 관리자 앱 설정 > 일반(`/settings/general`)에서 사이트 기본 정보(이름·소개·로고·사이트 아이콘·링크 미리보기 이미지)와 표시 기본값을 다룬다. 이름·소개·공개 주소는 서버 `SiteSettingEntity` 에 이미 있고(설치 마법사 값, `install-contract.md`), 로고·아이콘·링크 미리보기 이미지·표시 기본값은 **서버에 칸이 없는 목업**이다.
**참고(읽기 전용)**: community 의 `docs/api-site-icon.md`(`D:\project\community\workspace\community\docs\api-site-icon.md`) — 아이콘·OG 검사 규칙·저장 방식의 원본.

## 화면
- `/settings/general`. 구획 ① 사이트 기본 정보 — 이름(필수 1~50자)·한 줄 소개(0~100자)는 구획의 [저장]·[되돌리기], **로고 · 사이트 아이콘 · 링크 미리보기 이미지는 [저장] 없이 올리면 즉시 적용**(줄마다 "올리면 바로 적용" 표시, 되돌리기는 확인창). 구획 ② 표시 기본값(언어·시간대·시간 형식·날짜 형식) — 자기 [저장]. 구획마다 따로 읽고 저장한다(가이드 `forms.md` 2-4: 구획별 저장 → 구획 안 저장 줄).
- 권한: 조회 `site.read`, 변경 `site.manage`(없으면 단추 숨김·읽기 전용).
- 기준 코드: `pages/settings/general.vue`, `components/site/SiteImageField.vue`, `utils/util-site-setting*.ts`, `util-site-image*.ts`, `composables/site/api/useSiteSettingsApi(.types).ts`.

## 이미지 규칙 (화면이 먼저 거르고 서버가 최종 판정)
| | 로고 | 사이트 아이콘 | 링크 미리보기 이미지(og:image) |
|---|---|---|---|
| 형식 | PNG·JPG·GIF·WEBP 또는 **SVG 소스 입력**(SVG 파일은 받지 않음) | **PNG**(SVG 는 소스 입력, 서버가 PNG 로 변환) | **PNG** |
| 용량 | 2MB | 2MB | 2MB |
| 크기 | — | 정사각, 한 변 512~4000px | 가로 1200~4000px(세로도 4000 이하), **비율 1200:630 ±2%** — 안 맞으면 자르지 않고 거절 |
| 검사 순서(아이콘·OG) | — | 빈 파일 → 형식(PNG 서명) → 용량 → 읽기 → 너무 큼 → 정사각 아님 → 너무 작음 | …→ 너무 큼 → **비율** → 너무 작음 |
- 문구는 지금 크기·용량을 포함한다. 서버 거절 문구는 가공하지 않고 `fields.file`/`fields.svgSource`/`fields.site` 로 그대로 보인다. 그림은 `<img src>` 하나로만 그린다(`v-html` 금지).

## API 후보 (확정 전)
| | |
|---|---|
| `GET/PUT /api/admin/site/basic` | `{ siteName, tagline, logoUrl }` · `{ siteName, tagline }` |
| `POST /api/admin/files`(multipart) → `PUT/DELETE /api/admin/site/logo`(`{fileId}`) · `PUT …/logo/svg`(`{svgSource}`) | 로고 — 바이트와 지정이 **두 걸음**, 바뀐 `logoUrl` 만 반환 |
| `GET /api/admin/site/icon` · `POST`(multipart `file`) · `PUT …/icon/svg`(`{svgSource}`) · `DELETE` | 아이콘 — **한 걸음**(서버가 여러 크기로 다시 만들어 저장). 응답 `{ custom, previewUrl, defaultPreviewUrl }` |
| `GET /api/admin/site/og-image` · `POST`(multipart `file`) · `DELETE` | 응답 `{ configured, url, width, height, defaultUrl }`. 서버는 1200×630 불투명 PNG 로 맞춘다 |
| `GET/PUT /api/admin/site/display-defaults` | `{ values }` — 이 값이 내 정보 > 개인 설정의 "기본 설정" |

## 서버가 해야 할 일 (이번 화면 범위 밖)
PNG 서명 기준 판별·다시 인코딩, 아이콘 크기별 PNG 생성, OG 1200×630 맞춤, SVG 위험 요소(스크립트·외부 참조·DOCTYPE) 거절과 변환, 저장소·공개 주소·버전 토큰, 공개 사이트 반영 경로(탭 아이콘·manifest·링크 미리보기 메타·상단 로고 자리).

## 미확정 사항
1. SVG 요청 필드명: 화면은 로고와 맞춰 `svgSource`(community 는 `svg`).
2. 아이콘·OG 를 관리자 앱 자체의 탭 아이콘에도 쓸지(community 는 쓰지 않음).
3. 공개 주소(`publicUrl`)는 이 화면 범위가 아니다(소셜 로그인 콜백·메일 링크 기준 — 변경 정책 먼저).
4. 커뮤니티의 미리보기 서랍·접힘 가이드는 만들지 않았다(필요 시 후속).
5. 표시 기본값의 저장 위치와 사용자 층과의 관계(정책 결정 사항).

## 서버 구현 결과 (2026-10-21, `roles-contract.md` 3단계) — 화면 연결은 후속
**API** (`/api/admin`, `ApiResponse`, 조회 `site.read` / 변경 `site.manage`, 오류는 모두 `400 VALIDATION` + `fields`):
| | 요청 → 응답 |
|---|---|
| `GET·PUT /site/basic` | `{siteName, tagline}` → `{siteName, tagline|null, logoUrl|null}` (`fields.siteName`·`tagline`, 둘 다 한꺼번에) |
| `POST /files` multipart `file`+`purpose=SITE_LOGO` | → **201** `{fileId}` (`fields.file`·`purpose`) |
| `PUT /site/logo` `{fileId}` · `PUT /site/logo/svg` `{svgSource}` · `DELETE /site/logo` | → `{logoUrl|null}` (`fields.fileId`·`svgSource`·`site`) |
| `GET·POST·DELETE /site/icon`, `PUT /site/icon/svg` | → `{custom, previewUrl|null, defaultPreviewUrl}` (`fields.file`·`svgSource`) |
| `GET·POST·DELETE /site/og-image` | → `{configured, url|null, width|null, height|null, defaultUrl}` (`fields.file`) |
| `GET·PUT /site/display-defaults` | `{values:{language,zoneId,timeFormat,dateFormat}}` ↔ 같은 모양 (`fields.language|zoneId|timeFormat|dateFormat`) |
- 관리자 `GET /auth/me`·로그인 응답의 `siteName`·`siteLogoUrl` 이 이제 실제 값이다(비로그인에도).
- 기타 오류: `503 STORAGE_UNAVAILABLE`(저장소에 쓰지 못함) · `503 IMAGE_BUSY`(이미지 동시 처리 자리 없음 — 기본 2장, 10초 대기) · `413 FILE_TOO_LARGE`(10MB 초과, multipart 한도 — 2MB 초과는 `fields.file` 문구로 "지금 N.NMB" 를 알린다).
- 검사 문구는 화면 `util-site-setting-messages.ts`·`util-site-image-messages.ts` 와 같은 말이다(ko/en 번들 `siteSetting.*`·`site.image|logo|icon|og|svg.*`).

**규칙 (구현)**
- 이미지 형식은 파일 첫 바이트로 판별, 모두 다시 인코딩해 메타·뒤 붙은 데이터를 뗀다. 로고: PNG→PNG, JPEG→JPEG(품질 0.9), **GIF→첫 프레임 PNG(움직임 버림)**, **WEBP→JDK 에 디코더가 없어 그림은 그대로 두고 컨테이너만 정리(EXIF·XMP·모르는 청크·RIFF 뒤 데이터 제거)**. 한 변 4000px·2MB 상한.
- 아이콘: 검사 순서 빈 파일→PNG 서명→2MB→읽기→한 변 4000 초과→정사각→512 미만, 결과 32·48·180(흰 배경)·192·512 PNG(투명은 180 만 흰 배경). SVG 는 벡터로 크기마다 직접 그린다(JSVG 2.2.0, 외부 자원 읽기 금지, 그리기 5초 제한·스레드 4개 상한).
- OG: 빈→PNG→2MB→읽기→4000 초과→비율(1200:630 ±2%, 자르지 않고 거절)→가로 1200 미만, 결과 1200×630 불투명 PNG(투명은 흰색).
- SVG(로고·아이콘 공통 검사): 100,000자·DOCTYPE/ENTITY·xml-stylesheet·script·foreignObject·`on*`·외부 href/`url()`/`@import` 는 **정화하지 않고 거절**, `xmlns` 없으면 서버가 넣는다. 아이콘은 추가로 정사각(`viewBox` 또는 `width`·`height`)·그릴 수 있는 내용. 로고 SVG 는 소스(검사·정리한 것)를 DB 에 두고 `logoUrl` 은 `data:image/svg+xml;base64,…`.
- 저장 순서: 검사·처리 → 저장소 쓰기 → DB 교체 트랜잭션(설정 행 잠금) → 커밋 뒤 예전 파일 삭제, DB 실패 시 새 파일 삭제. 감사: `SITE_BASIC_UPDATE`·`SITE_LOGO_CHANGE`/`_REMOVE`·`SITE_ICON_CHANGE`/`_RESET`·`SITE_OG_CHANGE`/`_RESET`·`SITE_DISPLAY_UPDATE`(파일 내용·이름·SVG 소스는 남기지 않는다).

**저장소·서빙**
- `ImageStorage` 인터페이스(S3 는 후속) + 로컬 디스크 구현. 폴더 `roboyam.storage.local-path`(`ROBOYAM_STORAGE_PATH`; 기본 `/var/lib/roboyam/media`, local 프로파일 `~/.roboyam/media`). 키에 버전 토큰: `site-logo/{버전}/logo.{png|jpg|webp}` · `site-icon/{버전}/icon-512.png` 등 5장 · `site-og/{버전}/og.png`.
- **`/media/**` 는 공개 서버(api)와 관리자 서버(admin)가 둘 다 같은 폴더를 서빙**한다(1년 immutable, 로그인 불필요). admin 이 쓰고 두 앱이 같은 폴더를 보므로 compose 에서 같은 볼륨을 두 서비스에 마운트한다(`docker/dev`, `docker/prod` 의 `MEDIA_DIR`, 설치기 `<프로젝트>-media`). 응답 주소는 상대 주소 `/media/…` — 보는 쪽 서버가 같은 주소로 서빙하므로 프록시는 `/media` 를 그 서버로 넘기면 된다. 링크 미리보기 메타·manifest 처럼 다른 도메인이 읽는 곳은 `SiteAssets.absolute(url, publicUrl)` 로 공개 주소를 붙인다.
- 기본 아이콘·OG: jar 안의 SVG `/media/default/site-icon.svg`·`site-og.svg`(고정 주소, 하루 캐시). 공개 사이트가 og:image 로 쓸 기본 **PNG** 는 후속(`apps/user-web/public` 에 파일이 아직 없다).
- 서버가 2대 이상이면 로컬 디스크로는 부족하다(공유 디스크 NFS·EFS 또는 S3 구현 필요).

**테이블**: Flyway shared `V12__site_media`(`files`, `site_settings` 에 `logo_file_id`·`logo_svg`·`icon_token`·`og_token`·`display_*`). 파일 장부는 `PENDING`(올리기만 함) → `IN_USE`(로고로 지정). 올리고 지정하지 않은 파일은 배치 `file-orphan-cleanup`(03:45, 24시간 지난 PENDING 삭제)이 지운다.

**공개 사이트가 읽을 경로 (후보였던 것 — 구현됨, 아래 「공개 읽기 경로 구현 결과」)**: ① 공개 API 후보 `GET /api/site/info`(비로그인) → `{siteName, tagline, logoUrl, icon:{version,favicon32,favicon48,appleTouch}, ogImageUrl, displayDefaults}` — `SiteAssets` 가 이미 주소를 만든다(설정 행이 없으면 비어 있다). user-web `useSiteName`·`SiteBrand`·`useSiteTerms`(`siteIcon`·`tagline` 칸)가 채울 자리다. ② 탭 아이콘·링크 미리보기 메타·manifest 는 SPA 정적 `index.html` 로는 크롤러가 못 읽으므로 api 가 `index.html` 의 `<head>` 를 동적으로 채우거나 `/manifest.webmanifest` 를 동적으로 내려야 한다(community 방식).

**프론트 `// MOCK:` → `// REAL:` 때**: 로고 올리기 `purpose` 는 `SITE_LOGO`(화면 코드와 같음). `logoUrl`·`previewUrl`·`url` 은 `/media/…` 상대 주소 — 관리자 앱 dev 프록시에 `/media` 를 admin 서버로 넘기는 규칙이 필요하다(prod 는 서버가 직접 서빙). `me` 의 `siteLogoUrl` 은 SVG 일 때 큰 data URI(최대 약 133KB)일 수 있다. 서버는 `fields.site`(설정 행 없음)를 거의 내지 않는다.

**가정/미확정 추가**: 표시 기본값은 `site_settings` 한 행에 둔다(사용자 층·공개 회원 값과의 관계는 미정). GIF 움직임·JPEG EXIF 회전 정보는 다시 만들 때 사라진다. 올리기만 하고 지정 전인 파일의 주소는 응답에 주지 않지만 `/media` 키를 알면 열린다(키에 64비트 무작위 토큰). 로고 SVG 는 그릴 수 있는지는 검사하지 않는다(위험 요소만 거절). 로고 지정 후 이전 파일은 곧바로 지운다(되돌리기 없음).

## 공개 읽기 경로 구현 결과 (2026-10-22, `server/api` — 화면 연결은 후속)
**`GET /api/site/info`**(비로그인, `ApiResponse`) → `{siteName|null, tagline|null, logoUrl|null, icon:{version,favicon32,favicon48,appleTouch,icon192,icon512}|null, ogImageUrl, displayDefaults:{language,zoneId,timeFormat,dateFormat}}`.
- `logoUrl`·`icon.*` 은 관리자 `me` 와 같은 소스(`SiteAssets`)라 같은 주소(`/media/…` 상대, SVG 로고는 data URI). `icon` 은 올린 아이콘이 없으면 null(화면은 기본 `/media/default/site-icon.svg`). `ogImageUrl` 은 올린 이미지, 없으면 기본 PNG(`/media/default/site-og.png`) — 공개 주소가 있으면 절대 주소. 설정 행이 없으면 이름·소개·로고·아이콘이 null, `displayDefaults` 는 처음 값(ko·Asia/Seoul·H24·YMD_DASH).
- 허용 필드만 담는다(이메일·설치 정보·내부 ID·공개 주소 원문 없음). 설치 전에는 기존 가드가 `503 NOT_INSTALLED`(`/api/**` 전체 규칙) — 화면은 설치 상태를 먼저 본다.
- 캐시: 서버 메모리 사본 20초(`PublicSiteService` — 다시 읽다 실패하면 이전 값을 쓰고 5초 뒤 재시도), 응답 `Cache-Control: no-cache` + `ETag`(사본 지문, `If-None-Match` 가 같으면 304 — 브라우저는 매번 확인한다, 2026-10-29 사용자 결정으로 `public, max-age=60` 에서 바꿈). `SecurityConfig` 에 `GET /api/site/info` 허용(api 에만 있는 주소).

**`GET /manifest.webmanifest`**(`application/manifest+json`, `ApiResponse` 로 감싸지 않는다 — 표준 형식) — `name`=사이트 이름(없으면 `Roboyam`), `short_name`(12자까지), `description`=슬로건, `lang`=표시 언어, `start_url`·`scope`=`/`, `display=standalone`, 색 `#ffffff`, `icons` = 올린 192·512 PNG(`purpose: any`) 또는 기본 SVG(`sizes: any`). 아이콘 주소는 상대 주소(같은 서버가 서빙) — 위 「서빙」의 「절대 주소」 안내와 달리 매니페스트는 상대로 뒀다(공개 주소와 접속 도메인이 달라도 깨지지 않게). 캐시·ETag 는 위와 같다.

**`index.html` 머리 채우기**(`SiteHeadFilter` → `IndexHtmlRenderer`, 보안 필터 뒤)
- 적용: 확장자 없는 GET·HEAD 주소(`/`·`/login` …)와 `/index.html`. `/api`·`/media`·`/actuator`·`/error`·`/_nuxt`·`/.well-known`·확장자 있는 주소는 건드리지 않는다.
- 틀은 `classpath:/static/index.html`(user-web `pnpm generate` 가 `server/api/src/main/resources/static` 으로 내보냄 — gitignore, 별도 복사 단계 없음)이다. 머리에서 `<title>`·`description`·`og:*`·`twitter:*`·`rel=manifest` 를 지우고(`<script>`·`<style>` 안은 그대로) `<meta charset>` 바로 뒤에 넣는다: 아이콘 링크(올렸으면 32·48 `rel=icon` + 180 `apple-touch-icon` 으로 틀의 아이콘 링크를 대체, 안 올렸으면 틀의 링크를 두고 틀에도 없으면 기본 SVG), `rel=manifest`, `<title>`, `description`, `og:type=website`·`og:site_name`·`og:title`·`og:description`·`og:image`(절대 주소, 올린 이미지면 `og:image:width/height` 1200×630), `twitter:card=summary_large_image`·`twitter:title|description|image`. 제목은 「이름」 또는 「이름 — 슬로건」, 설명은 슬로건(없으면 이름). 이름·슬로건·주소는 모두 HTML 이스케이프(속성값 포함). `og:url`·`og:locale`·`canonical` 은 넣지 않는다.
- 설정 행이 없으면(설치 전) 틀 그대로. 틀이 없거나(빌드 전) 머리를 만들다 실패하면 그대로 지나가 기존 처리(404 등)를 따른다. 결과는 설정 사본이 바뀔 때까지 캐시하고 `Cache-Control: no-cache` + `ETag`(304)로 내린다.

**기본 og PNG**: 크롤러가 SVG `og:image` 를 못 읽는 경우가 있어 jar 에 `media-default/site-og.png`(1200×630, 기본 SVG 와 같은 그림)를 동봉했고 `SiteMediaKeys.DEFAULT_OG_PNG_URL` 로 공개 쪽(info·머리 메타)만 PNG 를 쓴다. 관리자 화면의 `defaultUrl` 은 기존 SVG 그대로. 디자인이 정해지면 같은 이름 파일만 바꾸면 된다.

**미확정/후속**
1. 관리자가 바꾼 설정은 이 서버에 최대 20초 뒤 보인다(브라우저 캐시는 매번 확인).
2. 공개 응답에 `Set-Cookie: XSRF-TOKEN` 이 같이 나간다(기존 CSRF 필터) — CDN 이 응답을 캐시하려면 쿠키를 무시하게 설정해야 한다.
3. user-web 의 `SiteTermsResponse` 는 `/api/site/terms` 에서 `siteName`·`siteIcon`(키 이름)·`tagline` 을 받는 모양이다. `/api/site/info` 의 `icon` 과 키 이름이 달라 화면 쪽 타입·로더를 새로 맞춘다.
4. (`/api/site/theme` 은 2026-10-23 구현·연결 — 아래 「테마」) `/api/site/terms` 는 아직 서버에 없다(용어는 `useSiteTerms` 가 서버 값 없이 기본 이름).
5. 서비스 워커·푸시 알림 아이콘은 범위 밖.

> 2026-10-28: 테마 고르기 화면은 「일반」에서 `설정 › 테마`(`/settings/theme`)로 옮겼고, 테마마다 레이아웃·메뉴 설정이 붙었다 — `site-theme-layout-contract.md`. 아래 테마 API 계약(`{ theme, themes }`)은 그대로 정본.

## 테마 (서버 구현 2026-10-23 · 화면 연결 2026-10-23 — 관리자 설정 > 일반 「공개 사이트 테마」 구획(`site-theme`), user-web `useThemeApi` 실제 호출)
**의도**: 운영자가 공개 사이트 테마 하나를 고른다(사용자 개인 테마 없음 — `.claude/guides/frontend/themes.md`). 공개 사이트는 비로그인에도 그 id 를 받아 루트 `data-theme`·셸을 고르고, 모르는 id 면 기본 테마로 그린다.

**결정**: 공개 응답은 **기존 사이트 정보 사본에 합쳤다** — `PublicSiteService` 사본(20초 TTL)에 `theme` 를 넣어 `GET /api/site/info` 의 `theme` 칸으로 주고, 같은 사본에서 `GET /api/site/theme` 도 같은 값을 준다(user-web `useThemeApi` 후보 주소·모양 그대로라 프론트 변경이 `// REAL:` 교체뿐). 캐시·ETag·전파(관리자 저장 → 공개 서버 최대 20초, 브라우저는 매번 확인)는 `/api/site/info` 와 같다.

**API**
| | 권한 | 요청 → 응답 |
|---|---|---|
| `GET /api/site/theme` (api, 비로그인) | — | → `{ theme, …지금 테마의 메뉴 설정 평평한 칸 }`(2026-10-29 — 칸은 `site-theme-layout-contract.md`) · `Cache-Control: no-cache` + `ETag`(사본 지문, 304) |
| `GET /api/site/info` (api) | — | 기존 응답 끝에 `theme` 칸 추가 |
| `GET /api/admin/site/theme?forTheme=` (admin) | `site.read` | → `{ theme, themes: string[], …forTheme 의 메뉴 설정·테마별 기본값 }`(2026-10-29) |
| `PUT /api/admin/site/theme` (admin, CSRF) | `site.manage` | `{ theme }` → 같은 모양 · 목록 밖·빈 값 `400 VALIDATION` `fields.theme`("알 수 없는 테마예요. 목록에서 골라 주세요.") · 설정 행 없음 `fields.site` |
- 권한 없음 `403 PERMISSION_DENIED`. 표시 기본값 저장 API(`/site/display-defaults`)와 따로 둔 별도 PUT 이다(구획별 저장 — 일반 설정 화면의 한 구획 또는 표시 기본값 구획 안의 한 줄로 붙이면 된다).
- `themes` 는 고를 수 있는 id(보일 순서). 이름(화면 문구)은 화면이 id 로 i18n 키를 찾는다.

**규칙**: 허용 id 의 원천은 서버 코드 `SiteThemes.IDS`(shared `com.community.site.service`) — user-web `utils/util-theme.ts` 의 `THEMES` 와 같은 id 를 둔다(2026-10-29 — `default`·`daum`·`naver`·`roboyam`). 새 테마는 두 곳에 한 줄씩 + 서버 `SiteThemeMenus`(메뉴 설정 기본값)·`HomeLayoutDefaults`(홈 기본 배치)에 그 테마 값. 테마 선택 화면은 `/settings/theme`(`site-theme-layout-contract.md`)로 옮겼다. DB 칸 `site_settings.theme varchar(32) NOT NULL DEFAULT 'default'`(V52, CHECK 없음 — 테마가 늘 때마다 마이그레이션이 필요 없게). 저장된 값이 목록에서 빠졌으면 읽을 때(관리자·공개 모두) `default` 로 본다. 바뀐 때만 감사 `SITE_THEME_UPDATE`(대상 `SITE`/`1`, `before/after.theme` → 「바뀐 내용」 `theme`)와 관리자 알림(사이트 설정 변경, 변형 `notification.variant.siteTheme`). 같은 값 저장은 아무것도 남기지 않는다.

**화면**: `general.vue` ③ 구획 — 라디오 카드(이름·설명은 `site.themeName_<id>`·`site.themeDesc_<id>`, 모르는 id 는 id 그대로), 구획 안 [되돌리기]·[저장], `fields.theme` 는 고르기 아래·`fields.site` 와 그 밖은 구획 안 한 줄. 선택지가 하나면 「지금은 기본 테마 하나만 있어요」. `site.manage` 없으면 라디오 비활성·저장 줄 없음.

**미확정**: 테마가 `default` 하나뿐이라 공개 응답 값이 실제로 바뀌는 것은 두 번째 테마가 생겨야 확인된다(전파 경로는 다른 사이트 설정과 같은 사본). 테마 미리보기·테마별 설정값(색 등)은 범위 밖.
