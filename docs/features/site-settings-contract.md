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

**공개 사이트가 읽을 경로 (구현 안 함, 후속)**: ① 공개 API 후보 `GET /api/site/info`(비로그인) → `{siteName, tagline, logoUrl, icon:{version,favicon32,favicon48,appleTouch}, ogImageUrl, displayDefaults}` — `SiteAssets` 가 이미 주소를 만든다(설정 행이 없으면 비어 있다). user-web `useSiteName`·`SiteBrand`·`useSiteTerms`(`siteIcon`·`tagline` 칸)가 채울 자리다. ② 탭 아이콘·링크 미리보기 메타·manifest 는 SPA 정적 `index.html` 로는 크롤러가 못 읽으므로 api 가 `index.html` 의 `<head>` 를 동적으로 채우거나 `/manifest.webmanifest` 를 동적으로 내려야 한다(community 방식).

**프론트 `// MOCK:` → `// REAL:` 때**: 로고 올리기 `purpose` 는 `SITE_LOGO`(화면 코드와 같음). `logoUrl`·`previewUrl`·`url` 은 `/media/…` 상대 주소 — 관리자 앱 dev 프록시에 `/media` 를 admin 서버로 넘기는 규칙이 필요하다(prod 는 서버가 직접 서빙). `me` 의 `siteLogoUrl` 은 SVG 일 때 큰 data URI(최대 약 133KB)일 수 있다. 서버는 `fields.site`(설정 행 없음)를 거의 내지 않는다.

**가정/미확정 추가**: 표시 기본값은 `site_settings` 한 행에 둔다(사용자 층·공개 회원 값과의 관계는 미정). GIF 움직임·JPEG EXIF 회전 정보는 다시 만들 때 사라진다. 올리기만 하고 지정 전인 파일의 주소는 응답에 주지 않지만 `/media` 키를 알면 열린다(키에 64비트 무작위 토큰). 로고 SVG 는 그릴 수 있는지는 검사하지 않는다(위험 요소만 거절). 로고 지정 후 이전 파일은 곧바로 지운다(되돌리기 없음).
