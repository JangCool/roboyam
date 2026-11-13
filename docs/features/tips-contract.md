# 팁·강좌·사용기 계약 (화면 확정 2026-11-07 — 서버 전)

팁과강좌(slug `tip`)·사용기(slug `review`) 두 게시판을 한 화면에 묶어 매거진처럼 보여 준다(사용자가 시안 B 매거진형을 고름).
공개 메뉴의 「팁·강좌·사용기」 폴더(예전 이름 「정보」)를 누르면 이 화면으로 간다. 같은 묶음의 가젯 페이지 `/p/tips`(커뮤니티처럼 가젯 배치)도 만들어 두었다 — 운영자가 메뉴 연결만 바꿔 고를 수 있게 지우지 않는다(사용자 결정).

## 화면 (user-web, 목업)
- `/tips?board=tip|review&page=N` — `pages/tips.vue`. 기본값(전체·1쪽)은 주소에서 뺀다. 모르는 slug 는 전체.
- 카드 배치(홈·가젯과 같은 `gadget-card`·`gadget-stack` 간격):
  1. 매거진 머리 카드 — 제목 「팁·강좌·사용기」 + 설명 한 줄 + 오른쪽 게시판 칩(전체 / 묶음 게시판들), 굵은 밑줄. **제목을 따로 떼지 않는다**(사용자). 그 아래 ☆ 「이번 주 추천」: 큰 글 1(16:9 사진 약 356px 폭·제목·요약 1줄·게시판·추천·댓글) + 작은 글 4(80px 사진·제목·게시판·추천) — 홈 하이라이트 크기에 맞춤(사용자 2026-11-07). 사진 위 꼬리표는 두지 않는다(머리와 겹침).
  2. 「최신 글」 — 사진(없으면 「이미지 없음」) + 제목 + 요약 2줄 + 게시판·작성자·날짜, 쪽 번호(`BoardPager`).
  3. 「많이 본 글」 1~5(조회수) — 넓으면 2 오른쪽 좁은 칸(`@min-[40rem]`), 좁으면 2 아래.
  4. 「태그」 칩 — 3 아래. 누르면 `/search?q=태그`(태그 거르기가 없어 임시).
- 칸 나누기는 카드 폭(`@container`): 헤드라인 `@min-[36rem]`, 아래 두 칸 `@min-[40rem]`. 모바일은 1 → 2 → 3 → 4 한 줄, 작은 추천 글은 가로로 민다(`relative`).
- 차단한 글은 `FoldedRow` 로 접고, 공지·가려진 글·19금 글은 헤드라인·많이 본 글에서 뺀다.

## 목업 가정 (`composables/tips/api/` — `// MOCK:` 은 `mock/tips-mock.ts` 한 곳)
- 실제 값: `GET /api/boards`(묶음 게시판), `useBoardApi().posts(slug, LATEST, page)` 의 글·썸네일·추천·댓글·조회수·작성자·날짜.
- 「전체」는 두 게시판의 같은 쪽을 받아 날짜순으로 합친다(쪽 글 수 최대 2배, 전체 쪽 수 = 큰 쪽).
- 헤드라인 = 각 게시판 첫 쪽에서 최근 7일 추천순(모자라면 오래된 글 추천순), 많이 본 글 = 첫 쪽 조회순(기간 없음).
- 지어낸 값: 본문 요약 `excerpt`(가려진·19금 글은 null), 태그 `tags`(게시판별 묶음에서 1~3개, 칩은 달린 횟수 위 10개).

## API 후보 (서버 단계)
- 글 줄에 `excerpt: string | null` — 본문 앞 글자만 약 120자, 가려진·19금 글 null.
- 묶음 머리 `GET /api/tips?board=` → `{ boards, headline: Post[5], mostViewed: Post[5], tags: {name,count}[] }`.
- 합친 최신 글 `GET /api/tips/posts?board=&page=` → `{ items, page, totalPages, totalCount }`(서버에서 날짜순으로 합쳐 쪽 매김). 글 줄 = `PostFeedRow`(`board` 포함) + `excerpt`·`tags`.

## 결정 (사용자 2026-11-07 — 서버 단계)
- **페이지별 하이라이트 + 자동 채움**: 「이번 주 추천」은 하이라이트 기능을 쓴다. 지금 하이라이트는 표 하나(`highlights`, 글마다 한 번)라 모든 하이라이트 가젯이 같은 목록을 보인다 → 하이라이트에 **놓일 곳**(`place` — `home`·`tips`·가젯 페이지 키)을 더하고, 같은 글을 여러 곳에 넣을 수 있게(곳 + 글 하나), 관리자 하이라이트 화면을 곳별로 나눈다.
  하이라이트 가젯은 어느 곳의 목록을 쓸지 설정으로 고른다(기본 `home` — 지금 동작 그대로). 「이번 주 추천」 5자리 = 운영자가 `tips` 곳에 고른 글 먼저(노출·기간·순서·노출 제목·대표 이미지 그대로), 모자라면 최근 7일 추천순으로 자동 채움(그래도 모자라면 더 오래된 글 추천순).
- 서버 단계에서 정한 것(바꿀 수 있음): 묶음 게시판은 `tip`·`review` 고정(운영자 설정은 나중), 많이 본 글 = 최근 30일 조회순(모자라면 기간 밖으로 채움), 헤드라인 글도 최신 글 목록에 그대로 둔다, 최신 글 한 쪽 20개.

## 미확정
- 태그: 저장·글쓰기 입력 UI·태그 검색/거르기를 새로 만들지(서버에 태그가 없어 화면 목업으로 남긴다).
- 로컬 DB 에만 있는 데이터: 메뉴 폴더 이름·`/tips` 연결·공지/인기글 순서, 가젯 페이지 `tips` — 운영 서버에서는 관리자 화면에서 다시 만든다.

## 서버 구현 결과 (2026-11-07 — 화면 연결 전)
- **마이그레이션** shared **V161** `V161__highlight_place.sql`(가번호 — 합치기 직전 마지막 번호 다시 확인): `highlights.place varchar(30) NOT NULL DEFAULT 'home'`(기존 행 = home, CHECK `^[a-z][a-z0-9-]{1,29}$`) · 유일 `(place, post_id)`(예전 `ux_highlights_post` 지움) · 순서 색인 `(place, sort_order, id)` · 글 색인 `(post_id)`.
- **곳**: `home` · `tips` · 가젯 페이지 키(shared `HighlightPlaces`). 가젯 페이지 키가 home·tips 와 같으면 그 곳 하나로 친다(`/p/tips` 의 하이라이트 가젯이 `place: tips` 면 `/tips` 머리와 같은 목록).
- **관리자** `/api/admin/highlights`: `GET ?place=`(비우면 home → `{ place, visibleCount, total, items }`, 줄에 `place`, `order` 는 곳 안 차례) · `GET /places` → `[{ key, name, kind: HOME|TIPS|PAGE }]`(홈 · 팁·강좌·사용기 · 가젯 페이지 만든 차례, 이름은 요청 언어) · `GET /post-search?q=&place=` · `GET /candidates?…&place=`(응답에 `place`, 그 곳에 없는 글만) · `POST { …, place }`(비우면 home, 없는 곳·틀린 모양 `400 fields.place`, 곳은 만든 뒤 못 바꿈) · `PUT /order { place, ids }`(그 곳의 ID 집합). `409 HIGHLIGHT_DUPLICATE` 는 같은 곳 안에서만. 감사 HIGHLIGHT_CREATE·UPDATE·VISIBLE·DELETE·ORDER 의 detail 에 `place`.
- **가젯**: HIGHLIGHT 설정 `place`(기본 home) — `GadgetConfigs.view` 는 칸이 없거나 모양이 틀리면 home, `GadgetPatches` 는 모양만 검사(`gadgets[i].config.place`), 공개 읽기(`HighlightPicks.resolve`)는 없는 곳(지운 가젯 페이지 등)을 home 으로.
- 글 상세 `highlighted` = **어느 곳이든** 노출 중인 하이라이트가 있으면 참. 관리자 토론 줄의 `highlightId` 는 home 것, 없으면 먼저 만든 것.
- **공개** `GET /api/tips?board=` → `{ boards: PostBoardRef[], headline: TipsPost[≤5], mostViewed: TipsPost[≤5] }` · `GET /api/tips/posts?board=&page=` → `{ items, page, totalPages, totalCount }`(한 쪽 20, page < 1 은 1). 비회원 허용(`SecurityConfig`), no-store.
  - 글 줄 = `PostFeedRow` 칸 그대로 + `excerpt`(본문 평문 앞 120자, 공백 정리, 넘으면 「…」, 가려진·19금·빈 본문 null). `tags` 없음.
  - headline: tips 하이라이트(노출 중·보이는 글·19금·공지 아님, 관리자 순서, 노출 제목→`title`·요약→`excerpt`·대표 이미지→`thumbnailUrl`) → 최근 7일 추천순(추천·조회·최신) → 더 오래된 글 추천순, 같은 글 한 번. **정한 것**: 게시판을 고르지 않으면 운영자가 tips 에 고른 글은 묶음 밖 게시판 글이어도 둔다(고르면 그 게시판 글만).
  - mostViewed: 최근 30일 조회순 → 기간 밖. 헤드라인과 겹쳐도 된다. 공지·숨김·19금·비멤버에게 안 보이는 소모임 글은 둘 다 뺀다.
  - posts: 게시판 글 목록과 같은 거름(공지 빼고, 신고·운영자 숨긴 글은 자리만 남고 요약 null, 19금은 성인에게만), id 최신순.
- **프론트 타입과 차이**(`useTipsApi.types.ts`): `TipsPost.tags`·`TipsOverview.tags` 없음(화면 목업 유지) · `boards[]` 에 `id` 가 더 있음 · headline 은 최대 5(타입 주석은 4).
- **시험**: admin `HighlightPlaceAdminTest`(5) · `GadgetPatchesTest`(+1, 10) · `HomeLayoutGroupBuyConfigTest`(7, HIGHLIGHT 칸에 place) · api `TipsHttpTest`(6) · `GadgetAdTest`·`GadgetGroupBuyTest`(9) 통과.
- 관리자 하이라이트 화면(곳 고르개)·user-web `/tips` 실제 연결은 아직(MOCK 그대로).

- **모바일 최신 글 줄**(2026-11-12 사용자 시안 확정): 윗줄 = 게시판 이름(맨 왼쪽) · 말머리, 썸네일 64×48(그림 표시만 — `GadgetThumb :label=false`) + 제목, 본문 미리보기는 썸네일 아래 줄 폭 전체, 아랫줄(썸네일 밑까지 줄 폭 전체) = 아바타·닉네임(회원 메뉴) … 오른쪽 끝 추천 · 시간. 데스크톱은 그대로.

## /tips 레이아웃 편집 (사용자 2026-11-13 — 「tips 도 편집 당연히 되어야지, 두 개 다 되게 하고 메뉴는 /tips」)
`/tips` 매거진을 관리자 `설정 › 테마 › 레이아웃 › 페이지` 에서 홈·가젯 페이지와 같은 편집기(실제 화면 iframe 편집)로 꾸민다. `/p/tips`(가젯 페이지)도 지금처럼 따로 편집된다. 메뉴는 `/tips` 그대로.
- **시스템 페이지**: `gadget_pages.system_page`(`'TIPS'` 또는 null, 하나뿐) 인 가젯 페이지 한 행이 `/tips` 의 배치를 갖는다(키 `tips-magazine` — 공개 주소는 `/p/…` 가 아니라 `/tips`). 배치·가젯은 다른 가젯 페이지처럼 테마마다 따로(`gadget_page_layouts`·`home_gadgets.page_id`), 저장 API·감사·캐시도 같다.
  - 지울 수 없고(409 `GADGET_PAGE_SYSTEM`) 영문 주소를 바꿀 수 없다(400 `fields.key`). 이름·설명·기준 폴더·글쓰기 단추는 고친다(이름·설명 = `/tips` 제목 카드 글자).
  - 관리자 목록 응답 `GadgetPage` 에 `systemPage: 'TIPS' | null`. 공개 `GET /api/pages/tips-magazine` 는 다른 페이지와 같은 모양.
  - 메뉴 「누르면 가는 곳」 가젯 페이지 목록에서 이 페이지의 경로는 `/tips`. 하이라이트 곳 목록에는 따로 두지 않는다(이 페이지의 하이라이트 가젯 곳은 `tips`).
  - 설치 구조 파일(`irobit.structure.json`)에도 이 행이 있다. 없는 채 설치되면 공개 `/tips` 는 화면에 박힌 처음 배치로 그린다.
- **가젯 종류 4개**(이 시스템 페이지 배치에서만 받는다 — 홈·게시판 레이아웃·일반 가젯 페이지는 400 `fields.gadgets[i].type`): 공개 응답 `data: null`·`empty: false`(화면이 `/api/tips`·`/api/tips/posts` 로 채운다), 내용 설정(`config`) 없음 — 제목·노출·꾸미기만.
  | 종류 | 뜻 | 최소(12칸 기준 w×h) | 처음 배치(24칸) |
  |---|---|---|---|
  | `TIPS_HEADLINE` | 이번 주 추천(큰 글 1 + 작은 글 4) — 보이는 첫 것은 제목 카드 안 굵은 밑줄 아래로 들어간다(편집 중 제외) | 6×10 | x0 y0 w24 h20 |
  | `TIPS_LATEST` | 최신 글(쪽 번호, `?board=&page=`) | 4×10 | x0 y20 w17 h40 |
  | `TIPS_POPULAR` | 많이 본 글 1~5 | 3×6 | x17 y20 w7 h14 |
  | `TIPS_TAGS` | 태그(목업) | 3×3 | x17 y34 w7 h8 |
- 제목 카드(페이지 이름·설명 + 오른쪽 게시판 칩)는 가젯이 아니라 `/tips` 화면이 그린다 — 칩(`?board=`)은 네 가젯 모두에 걸린다.
- **구현 결과(2026-11-13)**: shared **V209** `V209__gadget_page_system_tips.sql` — `gadget_pages.system_page`(CHECK `TIPS`, 부분 유일) + 시스템 행 **id 1**·키 `tips-magazine`·설명 = 지금 부제·글쓰기 단추 끔·기준 폴더 없음(이미 있으면 넘어감). 설치 구조 파일에도 같은 행.
  - 서버: 매거진 4종은 `GadgetKinds.isTips`(기존 `isKnown` 밖) — `GadgetLayoutWriter` 가 TIPS 자리에서만 받고, 공개 `data: null`. 지우기 409 `GADGET_PAGE_SYSTEM` · 키 바꾸기 400 `fields.key`(`gadgetPage.key.system`). 관리자 목록에서 시스템 페이지가 맨 앞. 페이지 한도 50 에 시스템 페이지도 센다(운영자가 만들 수 있는 건 49).
  - 메뉴 대상 선택지에 `path`·`systemPage`(시스템은 `/tips`). 하이라이트 곳 목록에서 시스템 페이지는 빠지고 `tips-magazine` 은 `tips` 로 친다(`HighlightPlaces.canonical`).
  - 화면: user-web `pages/tips.vue`(시스템 페이지 배치 + 제목 카드·칩, 없으면 `UtilTips.fallbackPage`) · `useTipsShared`(데이터 한 번 받기) · `GadgetTips{Headline,Latest,Popular,Tags}.vue`. admin 넣기 목록은 TIPS 페이지에서만 4종(`UtilGadgetLayoutEditor.addable`), 페이지 정보 창은 시스템 페이지면 공개 주소만 보이고 지우기 없음.
  - 가장자리: V209 전에 키 `tips-magazine` 인 일반 페이지가 있으면 시스템 행은 들어가지 않는다. 매거진 가젯은 ⚙ 설정(제목·꾸미기 창)이 아직 없다.
