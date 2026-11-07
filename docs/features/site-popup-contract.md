# 팝업 계약 (화면 확정 2026-11-07)

공개 사이트에 들어오면 **화면 가운데 뜨는 이벤트·프로모션 창**(레이어 팝업)이다. 운영자가 관리자 「팝업 관리」에서 여러 개를 만들고 순서·노출·기간·대상을 정한다.
메뉴 줄 아래 한 줄 공지인 「공지 띠」(`site-notice-bar-contract.md`)와 다른 기능이다 — 이름도 「알림 팝업」이 아니라 「팝업」(알림 = 알림 종 기능과 헷갈림).

## 화면
- admin-web `/content/popups`(`pages/content/popups.vue`) — 메뉴 콘텐츠 묶음 맨 끝 「팝업 관리」(키 `sitePopups`, 아이콘 `pi pi-window-maximize`).
  - 목록: 순서 ↑↓(전체 ID 새 차례) · 썸네일 · 상태(노출 중·노출 예정·기간 끝남·노출 끔) · 종류 · 이름 · 보일 곳 · 기기 · 닫기 선택지 · 링크 · 기간 · 바꾼 사람 · 노출 켜기 · 미리보기 · 수정 · 삭제(확인).
  - 노출 중 0개면 경고, 3개 넘으면 「너무 많다」 안내(막지 않음).
  - 만들기·고치기 대화상자 `components/popup/SitePopupFormDialog.vue` — 입력하는 대로 오른쪽 미리보기(PC·모바일). 「지금 뜨는 팝업 보기」는 노출 중(SHOWING)인 것을 홈 기준·기기별로 넘겨 본다.
  - 미리보기 카드 `SitePopupCard.vue` 는 공개 `SitePopupLayer.vue` 와 같은 모양 — 두 곳을 같이 고친다.
- user-web `components/shell/SitePopupLayer.vue` — 기본 레이아웃에 한 번. 상태 `composables/popup/useSitePopups.ts`, 규칙 `utils/util-site-popup.ts`.
  - 여러 개면 **한 창에서 넘기기**(목록 차례, 「1 / N」, ‹ ›, 모바일 밀기). 겹쳐 띄우지 않는다.
  - PC 폭 400px · 모바일 화면 폭 − 2.5rem, 가운데. 그림은 가로 맞춤(세로 최대 min(70dvh,600px)), 텍스트는 제목·내용(줄바꿈 유지, 길면 안에서 스크롤)·단추.
  - 아래 줄 왼쪽 「보지 않기」(그 장의 운영자 선택) → 그 장만 빠지고 다음 장 · 오른쪽 「닫기」·Esc·바깥 → 이번 방문(앱이 열린 동안) 창 전체를 닫음 — 「모든 화면」 팝업도 다른 화면에서 다시 안 뜬다, 새로 고치면 다시 뜬다.
  - 링크를 누르면 창을 닫고 이동(`/…` 같은 창, `http(s)://` 새 창).

## 정책 (사용자 확정 2026-11-07)
- 보일 곳: `HOME`(주소 `/`) · `ALL`(기본 레이아웃의 모든 화면). 기기: `PC`(폭 768px 이상) · `MOBILE` — 화면이 가린다.
- 닫기 줄 선택지는 팝업마다 하나: `TODAY`(그날 자정, 이 기기 시각) · `WEEK`(7일) · `NEVER`(다시 보지 않기) · `NONE`(「닫기」만).
- 「보지 않기」 기록은 **이 브라우저**(localStorage `roboyam:site-popups` = `{ [id]: { version, until|null } }`)에만. 손님도 쓴다. 서버 `version` 이 바뀌면(운영자가 내용 고침) 기록이 있어도 다시 보인다. 목록에 없는 팝업·끝난 기록은 쓸 때 지운다.
- 권한: 보기 `site.read`, 바꾸기 `site.manage`(공지 띠와 같다, 새 권한 없음).
- 그림: 하이라이트 대표 이미지와 같은 검사·저장(PNG·JPG·GIF·WEBP 2MB, 한 변 4000px, GIF 는 첫 장면) — 파일 API 새 쓰임 `SITE_POPUP_IMAGE`.

## 필드·검증 (서버가 최종 판정, 화면 `util-site-popup.ts` 가 미리 알림)
- `name` 필수 1~40자(관리용, 방문자에게 안 보임) · `kind` `IMAGE|TEXT`
- IMAGE: `imageFileId` 필수 · `imageAlt` 선택 100자
- TEXT: `heading` 선택 40자 · `body` 필수 1~1000자(글자만, 줄바꿈 유지) · `buttonLabel` 선택 20자(링크 있을 때 쓰임, 비면 「자세히 보기」)
- `link` 선택 500자 — 공지 띠와 같은 모양(`/…`(`//`·`/\` 아님) 또는 `http(s)://`, 공백 없음)
- `target` `HOME|ALL` · `devices` `PC`·`MOBILE` 하나 이상 · `hideOption` `TODAY|WEEK|NEVER|NONE` · `visible` · `showFrom`/`showUntil` 선택(끝 > 시작)
- 글자 칸은 앞뒤 공백을 떼고 비면 null. 지금 종류가 아닌 칸도 그대로 저장(종류를 되돌리면 다시 쓰인다) — 검사는 지금 종류 칸만.

## API
- 관리자(`site.read` 조회 · `site.manage` 처리) `/api/admin/site-popups`
  - `GET` → `{ items: SitePopupItem[], total, showingCount }`(차례대로). 줄: `id, order, name, kind, imageFileId, imageUrl, imageAlt, link, heading, body, buttonLabel, target, devices, hideOption, visible, showFrom, showUntil, status(SHOWING|SCHEDULED|EXPIRED|OFF), updatedAt, updatedBy{id,name}|null`
  - `POST` → 201 줄 · `PUT /{id}` → 200 줄(모든 칸) · 400 `fields.name|imageFileId|imageAlt|link|heading|body|buttonLabel|devices|showUntil` · 404 `SITE_POPUP_NOT_FOUND`
  - `PUT /order { ids }` → 목록(집합이 다르면 400 `fields.ids`) · `PUT /{id}/visible { visible }` → 줄 · `DELETE /{id}` → 204
- 공개(비로그인) `GET /api/site/popups` → `{ items: [{ id, version, kind, imageUrl, imageAlt, link, heading, body, buttonLabel, target, devices, hideOption }] }` — 노출 켬·기간 안인 것만 차례대로. `version` = 보이는 칸(종류·그림·대체 글·링크·제목·내용·단추·닫기 선택지)의 해시.

## 서버 구현 (2026-11-07)
- shared: `V162__site_popup.sql`(`site_popups` + CHECK 제약, 기기는 `show_pc`·`show_mobile` 두 칸, 파일 용도 `SITE_POPUP_IMAGE`) · `com.community.popup` 엔티티 `SitePopupEntity`·저장소 `SitePopupRepository` · 규칙 `SitePopups`(정리·검사·상태·version — 링크는 `SiteNoticeBars.linkViolation` 그대로) · 공개 목록 `SitePopupReader`.
- admin: `SitePopupController`·`SitePopupAdminService` — 감사 `SITE_POPUP_CREATE|UPDATE|DELETE|ORDER|VISIBLE`(대상 `SITE_POPUP`, 일반 등급, 고치기·차례·노출은 바뀐 때만). 그림은 하이라이트와 같다(지정하면 IN_USE, 바꾸거나 지우면 예전 파일 장부 행 삭제 + 커밋 뒤 저장소 삭제). 키 `site-popup/{버전}/image.{확장자}`.
- api: `PublicSiteController` `GET /popups` + `PublicSitePopupService`(20초 사본, 기간은 요청마다 지금 시각으로 다시 거름, `no-cache` + ETag).
- 계약 밖 칸 오류: `fields.kind|target|hideOption`(모르는 값) · `fields.visible`(`PUT /{id}/visible` 값 없음). 지금 종류가 아닌 칸이 길이를 넘으면 오류 대신 저장하지 않는다(null).
- 공개 응답은 지금 종류가 아닌 칸을 null 로 준다(IMAGE 면 `heading·body·buttonLabel`, TEXT 면 `imageUrl·imageAlt`). 그림 파일이 없어진 IMAGE 는 건너뛴다.
- `version` = SHA-256 앞 16자리 hex(종류 + 그 종류의 보이는 칸 + 링크 + 닫기 선택지) — 이름·대상·기기·기간·노출만 바꾸면 그대로다.

## 미확정
- 대상 회원 구분(손님만/회원만) 없음 — 필요하면 칸 추가.
- 노출·클릭 수 통계 없음.
- 움직이는 GIF 는 첫 장면만(그림 처리 공통 규칙).
