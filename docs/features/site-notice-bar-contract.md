# 공지 띠 계약 (화면 확정 2026-11-06 · 서버 구현 2026-11-06)

공개 사이트 **모든 화면**의 메뉴 줄(헤더·상단 메뉴·핫 토픽 줄) 아래, 본문 위에 한 줄 공지를 보인다. 홈 맨 아래에 있던 투자 유의 띠는 뺐고 이 띠가 그 자리를 대신한다(투자 고지는 그 게시판 목록 머리·글 아래에 그대로).
운영자가 관리자 설정 > 일반 「공지 띠」에서 켜고 끄고 내용을 정한다. 방문자는 X 로 기간을 골라 숨긴다.

## 화면
- user-web: `components/shell/SiteNoticeBar.vue` — 기본 레이아웃 `after-header` 맨 끝. 본문 폭 안 한 줄 상자(투자 고지 띠와 같은 크기), 위아래 여백 같게 0.5rem(`main.css` `.site-notice-bar` 규칙이 본문 위 여백을 맞춘다).
  스크롤하면 로고 영역처럼 위로 사라진다(붙지 않는다). 모바일 안쪽 화면(글 상세·글쓰기)에서는 숨긴다.
- 여러 줄이면 5초마다 다음 줄로 미끄러진다. 마우스를 올리거나 초점이 있으면 멈추고 ‹ › 로 넘긴다. 몇 번째인지(1/5)는 보이지 않는다(사용자 결정).
- X → 「언제까지 닫을까요?」 하루 · 1주일 · 1달 · 영원히. 「영원히」는 확인창: 「공지를 신속하게 확인하지 못해 생기는 피해는 본인 책임」.
- admin-web: 설정 > 일반 ③ `components/site/SiteNoticeBarSettings.vue`(구획 주소 `notice-bar`) — 켜기 · 내용(공지 게시판 최근 글 / 직접 입력) · 글 수 5·10 · 문구·링크 · 저장된 값의 미리보기.

## 정책 (사용자 결정 2026-11-06)
- 내용 출처는 옵션: 기본 = 공지 게시판(slug `notice`)의 최근 글 N개(5·10)를 차례로, 또는 운영자가 직접 쓴 문구 한 줄(+ 선택 링크).
- 닫음 기록은 **이 브라우저**(localStorage `irobit:site-notice` = `{ version, until|null }`)에만. 손님도 닫을 수 있고 다른 기기에서는 다시 보인다.
- 기간은 누른 때부터: 하루 24시간 · 1주일 7일 · 1달 30일. 「영원히」도 **지금 공지 묶음만** — 서버 `version` 이 바뀌면(새 공지 글·빠진 글·문구/링크 변경) 기간이 남았어도 다시 보인다.

## 필드·검증 (서버가 최종 판정, 화면 `util-site-notice-bar.ts` 가 미리 알림)
- `enabled` · `source` `BOARD|CUSTOM` · `boardCount` 5|10 · `customText` CUSTOM 이면 필수 1~100자, `<` `>` 불가 · `customLink` 선택, `/…`(`//` 아님) 또는 `http(s)://…`, 500자 이하. 출처가 아닌 쪽 칸도 그대로 저장한다.
- 저장: shared `V156`(사이트 설정 행에 `notice_bar_*` 칸).

## API
- 공개 `GET /api/site/notice-bar`(비로그인) → `{ version, items: [{ postId|null, title, link|null, publishedAt|null }] }`
  - 꺼짐 → `{ version: 'off', items: [] }`
  - BOARD → `notice` 게시판에서 손님이 공개 목록으로 볼 수 있는 최근 글 N개, `link` = `/posts/{id}`, `version` = `b:` + 글 id 들
  - CUSTOM → 문구 한 줄, `version` = `c:` + SHA-256(문구 + `\n` + 링크) 앞 16자리
- 관리자 `GET`·`PUT /api/admin/site/notice-bar`(`site.read`·`site.manage`) → 다섯 칸 + `noticeBoard {slug,name}|null` + `preview` 제목 목록. 400 `fields.customText|customLink|boardCount|source`.

## 서버 구현 (2026-11-06)
- shared: `V156__site_notice_bar.sql`(칸 + CHECK 제약 — 문구는 V5 tagline 과 같은 틀) · `SiteSettingEntity.changeNoticeBar` · 규칙 `SiteNoticeBars`(정리·검사·version) · 줄 만들기 `SiteNoticeBarReader`(공개 띠와 관리자 미리보기가 같이 쓴다) · `PostRepositoryQDSL.guestLatest`.
- api: `PublicSiteController` `GET /notice-bar` + `PublicSiteNoticeService`(20초 메모리 사본, `/info` 와 같은 `no-cache` + ETag).
- admin: `SiteNoticeBarController`·`SiteNoticeBarService` — 바뀐 때만 감사 `SITE_NOTICE_BAR_UPDATE`(GENERAL 등급)·관리자 알림 변형 `siteNoticeBar`. 응답 `noticeBoard` 에 `nameEn` 도 있다.
- 공지 게시판 = 게시판 사본(15초)의 slug `notice` 이고 켜져 있고 소모임 게시판이 아님. 글은 최신글 피드와 같은 손님 조건(지운 글·`PUBLISHED` 아님·예약 삭제 지남·신고/운영자 숨김·19금 제외), id 내림차순.
- 링크는 `/` 쪽도 공백·`/\` 시작을 거절한다(화면 `UtilSiteNoticeBar.linkOk` 도 같게 맞춤). 공개 띠 반영은 최대 20초(+ 게시판 사본 15초) 늦다.

## 미확정
- 공지 게시판을 slug `notice` 하나로 고정했다(고르는 칸 없음).
- 가젯 페이지(`/p/[key]`)·최신글(`/latest`) 아래 투자 고지 띠는 그대로 두었다.

- **공지 메뉴 → 띠**(2026-11-12 사용자): 띠 앞 「공지」 글자·확성기를 누르면 공지 게시판(`/boards/notice`). 띠가 켜져 공지가 있으면 상단 메뉴 첫 줄(모바일 `AppMobileMenuRows`·데스크톱 다음 테마 GNB `DaumMenuBar`)에서 공지 게시판 칸을 뺀다(`useNoticeInBar`). 띠가 꺼졌거나 못 받으면 메뉴에 그대로 둔다. 이 브라우저에서 띠를 닫은 것은 메뉴에 반영하지 않는다. /menu 화면·게시판 모음의 공지는 그대로.
- **「영원히」 닫기 뺌**(2026-11-12 사용자): 닫을 기간은 하루·1주일·1달만. 예전에 「영원히」로 닫은 기록(`until` null)은 더 이상 숨기지 않는다.
