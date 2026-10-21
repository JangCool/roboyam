# 사이트 일반 설정 계약 (화면 목업 단계 — 사용자 화면 확정 전, 2026-10-21 기준)

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
