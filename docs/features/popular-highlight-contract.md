# 인기글 설정·인기글 관리·하이라이트(토론 포함) 계약 (결정 2026-10-24 — 구현 전)

**의도**: 인기글 순위 규칙을 운영자가 화면에서 정하고(지금은 서버 상수), 순위에 오른 부적절한 글을 빼고 다시 넣고, 운영자가 고른 글을 하이라이트로 지정해 그 글에 찬성·반대 의견(토론)을 받는다. community 의 같은 기능을 로보얌 구조·권한·배치 방식에 맞춰 옮긴다.
**정책 원본(읽기 전용)**: community `D:\community\docs\api-popular.md`(8·9절 관리자, 2·3절 규칙), `api-content.md` 1절(하이라이트)·1-2절(인기글 관리), `api-debate.md`(토론 전부). 로보얌의 현재 인기글 구현은 `member-activity-contract.md` 「서버 구현 결과」(DB 배치 `popular-rank`, `R__batch_24_popular_rank.sql`, 표 `post_rank_snapshots`).

## 결정 (2026-10-24, 사용자)

> **토론은 게시글 기능으로 옮김**(2026-10-24 사용자, 정본 = `post-debate-contract.md`): 토론과 그 설정(중립 받기·입장 바꾸기 허용)은 하이라이트가 아니라 **글**(`post_debates`)에 붙는다. 이 문서의 「하이라이트의 debate·neutralEnabled·stanceChangeAllowed」·「노출 중인 하이라이트에서만 토론」·`/api/admin/highlights/{id}/opinions·pin-requests` 는 그 계약이 대신한다. 토론 안의 규칙(투표·의견·소급·고정·고정 요청·비추천)은 이 문서 그대로.
- **인기글 설정·인기글 관리·하이라이트 셋 다 만든다**(앞서 「서버 상수로 둔다」던 `member-activity-contract.md` 결정을 바꾼다).
- **하이라이트는 관리자 화면·서버만 먼저**: 로보얌 홈(`/`)이 아직 자리표시라 공개 홈 표시는 하지 않는다. 홈은 나중에 **테마 형식을 접목해** 구성하고, 그때 하이라이트 표시를 붙인다.
- **토론(찬성·반대 의견 받기)도 함께 가져온다** — 관리자 의견 관리와 공개 사이트의 글 상세·댓글 쪽 토론(입장 탭·배지·입장 바꾸기·글자 수 한도)까지. 공개 홈 하이라이트 가젯의 토론 요약(`api-debate.md` 6절)은 홈과 함께 뒤로 미룬다.

## 로보얌에 맞춰 바꾸는 점 (구현 세션 기준 — 구현하며 확정해 이 문서에 적는다)
- **설정 항목**: community `home-settings` 13개 중 홈 개수(`popularCount`·`latestCount`)는 홈이 없어 뺀다 → 가중치(추천·댓글·조회)·시간 감쇠(`gravity`·`ageOffsetHours`)·최소 추천/댓글/조회·운영진 전용 게시판 제외·한 사람 최대 글 수·이번 주 시작 요일. 기본값은 지금 서버 상수와 같게(3·2·0.1·1.5·2·3·0·0·켬·2·월요일).
- **순위 계산은 지금처럼 DB 배치(`popular-rank`, pg_cron)**: 상수 대신 설정 표를 읽는다. 저장·빼기·다시 넣기 뒤에는 바로 다시 계산한다(배치 「지금 실행」 요청 표 — `batch-contract.md`). community 의 30초 Java 스케줄러는 옮기지 않는다.
- **빼기(제외)**: 세 기간 탭·채우기·게시판 「가젯 노출 금지」와 별개로 그 글만 순위에서 뺀다. 글 자체(게시판·검색·최신)는 그대로.
- **권한**: 운영자 권한 유형 「콘텐츠 > 공지·주요글」(`docs/domain/operator-permissions.md` 66행 — 처리 「하이라이트, 인기글 노출·제외」, 설정 「인기글 점수식」)에 새 권한 코드를 둔다. 후보: `highlight.read`·`highlight.manage`·`popular.read`·`popular.manage`(빼기·다시 넣기)·`popular.settings`(점수식). community 의 「최고 관리자만」은 로보얌 권한 모델(역할·권한 코드, 루트는 모두)로 바꾼다.
- **하이라이트 칸**: community 의 제목·요약·대표 이미지·노출·순서·토론 켜기를 따르되 로보얌 미디어 저장소·서식 규칙에 맞춘다. 소모임·출하 완료글 같은 로보얌에 없는 개념은 뺀다.
- **알림·감사**: community 운영 로그 코드는 로보얌 감사(`AuditActions`)로 옮긴다.

- **운영자가 숨긴 의견의 공개 표시는 게시판 옵션**(2026-10-24 사용자): 게시판 정책 칸 하나(게시판별, 비우면 기본 설정을 따름 — 기존 정책 칸과 같은 구조)로 「운영자가 숨긴 댓글임을 공개 화면에 밝힐지」를 정한다. **켜면** 그 댓글 자리에 「운영자가 숨긴 댓글이에요」, **끄면(기본)** 지금처럼 「신고로 숨겨진 댓글이에요」. 끈 게시판에서는 서버가 숨긴 까닭을 내려 주지 않는다(신고 숨김과 구별되지 않게).

- **입장 바꾸기는 소급한다**(2026-10-24 사용자 — community 와 다름): 입장을 바꾸면 그 글에 내가 쓴 기존 찬성/반대 의견(원댓글·답글)의 입장도 새 입장으로 함께 바꿔 찬성·반대 수가 옮겨 간다. 입장을 정하기 전에 쓴 일반 댓글은 그대로 일반. 숨긴 의견도 함께 바꾼다(수는 보이는 것만 센다).
- **바뀔 입장을 밝힌다**: 단추는 「반대로 바꾸기」/「찬성으로 바꾸기」처럼 바뀔 입장을 쓰고, 확인창은 「찬성 → 반대로 바꿀까요? 지금까지 쓴 찬성 의견 N개도 반대로 바뀌어요」처럼 어디로·몇 개가 바뀌는지 알린다.
- **찬성·반대 색**: 찬성 = 파랑 계열, 반대 = 빨강 계열. 공개(배지·탭·입장 고르개·내 입장)와 관리자(의견 관리 탭·배지, 하이라이트 줄의 찬성·반대 수)가 같은 색을 쓴다. 다크 모드 대비를 지킨다.

- **입장에 중립 추가**(2026-10-24 사용자): 입장은 찬성(`PRO`)·반대(`CON`)·**중립(`NEUTRAL`)** 셋. 「일반」은 입장이 아니라 잡담(입장 없이 쓴 일반 댓글)으로 그대로 둔다. 탭은 전체·일반·찬성·반대·중립, 수도 셋 다 센다. 입장 바꾸기는 지금 입장이 아닌 나머지 둘 중에서 고른다(예: 찬성이면 「반대로」·「중립으로」) — 소급 규칙은 같다. 중립 색 = 초록 계열(라이트·다크 대비 4.5:1 이상). 관리자 의견 관리·하이라이트 수에도 중립을 더한다.

- **토론 화면 배치**(2026-10-24 사용자 확정, 시안 https://claude.ai/artifact/Q1Y6ac9GrNrMNhxhrtvcWG 3판): 글 상세에서 **토론 구획은 본문 영역 안**(본문 글 바로 다음, 추천·링크 복사·신고 줄 위), **일반 댓글은 맨 아래 원래 댓글 자리**. 둘을 같은 탭·고르개에 나란히 두지 않는다. 토론이 꺼진 글은 지금처럼 댓글만.
- **일반 댓글은 입장과 무관**: 「일반」은 잡담이다. 입장을 정한 회원이 일반 댓글 칸에 써도 배지가 붙지 않는다(지금의 「입장이 있으면 모든 새 댓글에 배지」를 바꾼다). 토론 구획 안에서 쓴 의견과 그 답글만 입장 의견이다(답글도 쓴 사람의 입장 배지).
- **입장 고르기는 투표, 의견은 선택**: 회원은 토론 구획의 [찬성][반대][중립]을 눌러 글 없이 입장만 정할 수 있다(처음 정하기도 `PUT …/stance`). 다시 누르면 바꾸고 기존 의견도 옮겨 간다(소급 규칙 그대로). 비율 막대 = 입장을 고른 **사람 수**(투표 수), 의견 거르기 칩 = **의견 수** — 둘은 따로 센다. 의견 쓰기 칸은 입장을 고른 회원에게 열린다. 입장 취소(투표 철회)는 두지 않는다(필요하면 더한다).

- **중립 사용은 하이라이트마다 관리자가 고른다**(2026-10-24 사용자): 하이라이트 만들기·고치기에 「중립도 받기」(기본 켬). 끄면 공개 토론에 찬성·반대만 보이고 새 중립 선택을 막는다. **서버 정리**: 끄기 전에 고른 중립 표·쓴 중립 의견은 그대로 남고 수(`votes.neutral`·`counts.neutral`)에도 든다 — 화면은 `neutralEnabled || votes.neutral > 0 || counts.neutral > 0` 이면 중립 칸을 보인다. 이미 중립인 회원은 중립 의견을 계속 쓸 수 있고 찬성·반대로 바꿀 수 있지만, 바꾼 뒤 다시 중립으로는 못 간다.

- **고정 의견은 토론 맨 위**(2026-10-24 사용자): 관리자가 의견 관리에서 고정한 의견은 공개 토론 구획의 의견 목록 맨 위에 「고정」 표시와 함께 보인다(숨긴 의견은 고정이어도 올리지 않는다).
- **댓글 추천·비추천과 이름은 게시판 옵션**(2026-10-24 사용자): 모든 댓글(일반 댓글·토론 의견·답글)에 추천과 비추천을 둔다(하나를 누르면 다른 하나는 풀림). 게시판 정책 칸으로 「비추천도 보이기」(끄면 추천만, 기본 끔)와 「추천」·「비추천」 표시 이름(비우면 기본 이름)을 관리자가 정한다 — 게시판별, 비우면 기본 설정을 따름. 토론 의견은 그 글의 게시판 설정을 따른다. 비추천은 포인트·알림에 쓰지 않는다. 글(게시글) 추천은 이번에 바꾸지 않는다.

- **의견 고정 요청**(2026-10-24 사용자): 의견을 쓴 본인이 「고정 요청」을 보내고, 확인하는 사람이 승인하면 고정된다. **누가 확인할지는 게시판 옵션** — 글쓴이·게시판 매니저·운영자 중 여러 개(하나라도 승인하면 고정, 기본 운영자만, 빈 목록 = 요청 끔). 글쓴이·매니저는 공개 글 상세의 토론 구획에서, 운영자는 관리자 의견 관리에서 처리한다. 새 요청은 확인하는 사람에게, 결과는 요청한 사람에게 알린다.

- **입장 바꾸기 허용은 하이라이트마다**(2026-10-24 사용자): 하이라이트 만들기·고치기에 「입장 바꾸기 허용」(기본 켬). 끄면 한 번 고른 입장은 바꿀 수 없고(서버 409), 공개 사이트는 투표 단추를 누를 때 「찬성으로 정할까요? 한 번 정하면 바꿀 수 없어요」 확인을 한 번 더 받은 뒤 정한다. 정한 뒤에는 다른 단추를 막고 안내를 보인다. **서버 정리**: 관리자가 나중에 끄면 이미 고른 입장·쓴 의견은 그대로 두고 그 뒤의 바꾸기만 막는다(`409 STANCE_CHANGE_LOCKED`, V79).

## 마이그레이션 (배정)
shared `V74`(인기글 설정·제외) · `V75`(하이라이트) · `V76`(토론). 게시판 옵션(운영자 숨김 공개)이 **V77**, 중립 입장·중립 받기·댓글 비추천·의견 고정 요청·게시판 정책 칸 4개가 **V78** 하나(`V78__debate_neutral_dislike_pin_request.sql`). **V79 = `post_debates`**(찬반 토론을 하이라이트에서 글로 옮김 · 「입장 바꾸기 허용」 포함 — `post-debate-contract.md`). 월요일 커머스 병합 때 커머스 shared 마이그레이션은 **V82부터**로(V80 = 게시글 관리 `post-admin-contract.md`, V81 = 게시글 투표 `post-poll-contract.md`) 미룬다(V78 은 회원 알림 type CHECK 도 다시 만든다 — 커머스가 같은 CHECK 를 고치면 합집합으로).

## 서버 구현 결과 (2026-10-24 — 서버만, 화면은 다음 세션. 실제 DB 에서 아직 돌려 보지 않았다)
> **V78 갱신**(2026-10-24 — 중립 · 입장 = 투표 · 의견/일반 분리 · 투표 수 · 고정 의견 맨 위 · 댓글 비추천 · 의견 고정 요청): 아래 「공개 사이트 토론」·「댓글 추천·비추천」·「의견 고정 요청」이 새 모양이다. 뒤의 「화면 구현 결과」는 2026-10-24 화면 갱신(V78)으로 이 모양에 맞췄다.
> **토론 옮김**(2026-10-24): 아래의 「하이라이트 토론」(열림 판정·`debate`·`neutralEnabled`·`stanceChangeAllowed`·의견 관리 경로)은 `post-debate-contract.md` 가 정본이다 — 토론은 글의 것(`post_debates`, V79)이고 의견 관리는 `/api/admin/debates/{postId}/…`.
> **V79 갱신**(2026-10-24 — 입장 바꾸기 허용, 서버만 · 지금은 글 토론 칸): 하이라이트 `stanceChangeAllowed`(관리자 만들기·고치기·목록 줄 · 공개 `debate`) · `PUT …/stance` 의 새 `409 STANCE_CHANGE_LOCKED`. 화면은 아직 반영 전.

공통: 응답은 `ApiResponse` 래퍼(`{success,data|error,meta}`), ID 는 문자열, 시각은 ISO-8601 UTC, 관리자 응답은 모두 `Cache-Control: no-store`, 쓰기는 CSRF. 칸 오류는 `400 VALIDATION` + `fields.<칸>`(모아서). 관리자 입구는 `@RequirePermission`(권한 없음 `403 PERMISSION_DENIED`, 루트는 모두).

### 권한 · 메뉴
- `Permission`(유형 `NOTICE` 「공지·주요글」, `NOTICE_SETTINGS` 바로 뒤, 모두 `featureReady: true`) — admin-web `util-permission-catalog.ts` 의 `PERMISSIONS` 에 같은 차례·값으로 더해야 한다(화면 세션 몫):

| 코드 | unit | 하는 일 |
|---|---|---|
| `highlight.read` | READ | 하이라이트 목록·글 찾기·후보·의견 목록 |
| `highlight.manage` | MANAGE | 하이라이트 만들기·고치기·순서·노출·지우기·대표 이미지 올리기, 의견 고정·숨김 |
| `popular.read` | READ | 인기글 관리(순위·뺀 글) 보기, 인기글 설정 보기 |
| `popular.manage` | MANAGE | 인기글에서 빼기·다시 넣기 |
| `popular.settings` | CONFIG | 인기글 설정 저장 |

- 관리자 메뉴 `AdminScreenRegistry`: 새 묶음 `menu.content`(NORMAL, 아이콘 `pi pi-star`, 「게시판」 묶음과 「포인트」 묶음 사이) — 화면 키·주소·권한: `highlights` `/content/highlights` `highlight.read`(`pi pi-bookmark`, `menu.highlights`) · `popularPosts` `/content/popular` `popular.read`(`pi pi-chart-line`, `menu.popularPosts`) · `popularSettings` `/content/popular/settings` `popular.read`(`pi pi-sliders-h`, `menu.popularSettings`). admin-web `UtilAdminMenu.ITEMS` 에 같은 키·주소·권한·차례로 더한다(이름 키 4개는 admin-web i18n).

### 인기글 설정 — `GET·PUT /api/admin/popular-settings`
- GET: `popular.read` 또는 `popular.settings`. PUT: `popular.settings`.
```
PopularSettingsView { settings: Values, defaults: Values, limits: Limits, weekStarts: ["MONDAY","SUNDAY"],
  current: [ { rank, postId, title, boardName, boardNameEn, score, likeCount, commentCount, viewCount, createdAt, fill } ],  // 실시간 상위 10개
  computedAt|null, recomputePending, recomputeRequested, updatedAt|null, updatedBy: {id, name|null}|null }
Values { likeWeight, commentWeight, viewWeight, gravity, ageOffsetHours, minLikes, minComments, minViews, excludeStaffBoards, maxPerAuthor, weekStart }
Limits { likeWeight:{min,max,decimals}, commentWeight, viewWeight, gravity, ageOffsetHours, minLikes, minComments, minViews, maxPerAuthor }
```
- 기본값(= 예전 서버 상수): 3 · 2 · 0.1 · 1.5 · 2 · 3 · 0 · 0 · true · 2 · MONDAY. 범위(소수 자리): 추천·댓글 가중치 0~100(3) · 조회 가중치 0~10(3) · 감쇠 지수 0.5~3(2) · 감쇠 보정 0~48시간(2) · 최소 추천/댓글/조회 0~100000(정수) · 한 사람 최대 1~10(정수).
- PUT 요청 = `Values` 11칸 모두(숫자는 JSON 숫자). 오류 `fields.likeWeight|commentWeight|viewWeight|gravity|ageOffsetHours|minLikes|minComments|minViews|excludeStaffBoards|maxPerAuthor|weekStart`(범위·자릿수·빠짐), **세 가중치가 모두 0 이면 `fields.likeWeight`**. `weekStart` 는 대소문자 무시, 응답은 대문자. 응답은 저장 뒤의 같은 모양.
- 바뀐 칸이 없으면 쓰지 않는다(`recomputeRequested false`). 바뀌면 순위 다시 계산 요청(아래 「바로 다시 계산」) + 감사 `POPULAR_SETTINGS_UPDATE`(대상 `POPULAR_SETTINGS`/`popular`, 바뀐 칸만 before/after).
- `current`·점수는 **계산 시점** 값(저장 직후엔 아직 옛 설정으로 계산한 것 — `recomputePending` 이 true 인 동안 화면이 「다시 계산 중」을 보이고 잠시 뒤 다시 읽으면 된다). 추천·댓글·조회는 지금 값. 지금 볼 수 없는 글·뺀 글은 건너뛰고 번호는 스냅샷 그대로.
- community 와 다른 점: `popularCount`·`latestCount` 없음(홈이 없다), 「최고 관리자만」 → 권한 코드, 오류 `ADMIN_ONLY` 없음, `limits` 가 `[min,max]` 배열이 아니라 `{min,max,decimals}`, `defaults`·`weekStarts`·`recomputePending`·`recomputeRequested` 추가, `updatedBy` 는 `{id,name}`.

### 인기글 관리 — `/api/admin/popular-posts`
- `GET ?period=REALTIME|YESTERDAY|WEEK`(기본 REALTIME, 대소문자 무시, 그 밖 `400 fields.period`) — `popular.read`
```
PopularRankingView { period, from, to, weekStart, computedAt|null, recomputePending,
  items: [ { rank|null, snapshotRank, score, fill|null, excluded, post: AdminPostRef } ],
  excluded: [ { postId, available, post: AdminPostRef|null, reason, memo|null, excludedBy: {id,name|null}|null, excludedAt } ],
  reasons: ["INAPPROPRIATE","PROMOTION","MANIPULATION","DISPUTE","OTHER"] }
AdminPostRef { id, title, boardId, boardName, boardNameEn, prefixName, authorKind(MEMBER|GUEST), authorId|null, authorName,
               likeCount, commentCount, viewCount, createdAt, state(PUBLIC|HIDDEN|DELETED|ADULT|BOARD_OFF) }
```
  `items` = 공개 인기글과 같은 차례·같은 글(지금 볼 수 없는 글·인기글에 못 오르는 게시판 글은 빠지고 `rank` 를 1부터 이어서). **계산 뒤에 뺀 글은 `excluded: true`, `rank: null` 로 자리에 남는다**(다시 계산되면 사라진다). `fill` YESTERDAY(어제 글로 채움)·LAST_WEEK(지난주 글로 채움). `from`·`to` 는 기간 범위(`[from,to)`, 한국 달력·설정의 주 시작 요일). `excluded` 는 최근에 뺀 것부터 200개, 기간과 무관, 글이 지워졌거나 볼 수 없으면 `available false`(글 행이 아예 없으면 `post null`).
- `POST /exclusions` `{ postId, reason, memo? }` — `popular.manage`. 새로 뺐으면 **201**, 이미 빠져 있으면 **200**(그 줄 그대로, 사유를 덮어쓰지 않음). 본문 = `excluded` 한 줄. 오류: `fields.postId`(비었거나 숫자 아님) · `fields.reason`(목록 밖) · `fields.memo`(100자 초과, `OTHER` 인데 비었음) · `404 POST_NOT_FOUND`(없는 글·지운 글 — 숨긴 글·19금 글은 뺄 수 있다).
- `DELETE /exclusions/{postId}` → **204**(멱등 — 빠져 있지 않아도 204). 형식이 틀리면 `400 fields.postId`. 점수·조건이 받쳐 줄 때만 순위로 돌아온다.
- 감사: `POPULAR_EXCLUDE`(대상 `POST`, reason·memo) · `POPULAR_RESTORE`(대상 `POST`, 뺐던 reason) — 실제로 바뀐 때만. 글쓴이에게 알리지 않는다.
- 뺀 글은 세 기간·채우기(DB 배치 후보)에서 빠지고 공개 `GET /api/home/popular` 가 읽을 때도 거른다. 글 자체(게시판·검색·최신)는 그대로.
- community 와 다른 점: 사유가 자유 글이 아니라 **사유 코드 + 메모**(사용자 지시 「사유 선택」), 권한은 하이라이트 메뉴가 아니라 `popular.*`, 줄의 글 칸이 `post` 하나로 묶임(`authorNickname` → `post.authorName`), `rewarded` 없음(인기글 보상 기능 없음), `excluded`·`snapshotRank`·`recomputePending`·`reasons` 추가.

### 바로 다시 계산 · 배치
- 설정 저장·빼기·다시 넣기는 같은 트랜잭션에서 DB 배치 `popular-rank` 의 「지금 실행」 요청을 요청 표(`batch_run_requests`)에 넣는다(`PopularRecompute` → `BatchRequestQueue.enqueueIfIdle` — `ON CONFLICT DO NOTHING`, 끝나지 않은 요청이 이미 있으면 넣지 않는다). 배치를 켠 관리자 서버가 집어 `CALL batch_popular_rank('MANUAL', 운영자ID, NULL)` — pg_cron 이 없어도 돈다. 응답 시점에 순위가 이미 바뀌었다는 보장은 없다(보통 몇 초, 요청 집기 주기). 실행 중이던 요청·정기 실행이 옛 값으로 돌았으면 다음 정기 실행(기본 5분)이 따라온다. 배치 실행 로그에 MANUAL·그 운영자로 남는다.
- `R__batch_24_popular_rank.sql`: 상수 → 설정 표 `popular_settings`(가중치·감쇠·최소값·운영진 게시판 제외·한 사람 최대·주 시작 요일). 실시간 감쇠의 밑은 0.1 아래로 내려가지 않는다(보정 0 일 때 0 으로 나누지 않게). 후보에서 `popular_exclusions` 를 뺀다. `popular_period_range` 가 설정의 주 시작 요일을 따른다(인자·반환형은 그대로). 보관 50개·채우기 기준 5개는 그대로 상수.
- 공개 `GET /api/home/popular`: `weekStart` 가 설정 값(MONDAY·SUNDAY), `from`·`to`(이번 주 탭)도 설정을 따른다. 읽을 때 뺀 글을 거르고, 운영진 전용 게시판 거름은 설정 `excludeStaffBoards` 를 따른다(판정은 shared `PopularRules.rankableBoard` 하나).

### 하이라이트 — `/api/admin/highlights`
- `GET` — `highlight.read` → `{ visibleCount, total, items: [HighlightItem] }`
```
HighlightItem { id, order(1부터), title|null, summary|null, imageFileId|null, imageUrl|null, visible, showFrom|null, showUntil|null,
  status(SHOWING|SCHEDULED|EXPIRED|OFF|BLOCKED), debate, neutralEnabled, stanceChangeAllowed, post: AdminPostRef|null,
  commentCounts: {all, general, pro, con, neutral, votes: {pro, con, neutral, total}},
  createdAt, updatedAt, updatedBy: {id, name|null}|null }
```
  `status` 판정 차례: 글이 PUBLIC 아님(또는 글 없음) BLOCKED → 노출 끔 OFF → 시작 전 SCHEDULED → 끝남 EXPIRED → SHOWING. `visibleCount` = SHOWING 수. `commentCounts.all` = 글의 보이는 댓글 수(답글 포함), general/pro/con/neutral 은 지우거나 숨기지 않은 원댓글의 입장별 수(의견 수), `votes` = 입장을 고른 사람 수(`post_stances`, 공개 `debate.votes` 와 같다). `neutralEnabled` = 「중립도 받기」(V78, 기본 true). `stanceChangeAllowed` = 「입장 바꾸기 허용」(V79, 기본 true).
- `GET /post-search?q=` — `highlight.read` → `AdminPostRef[]`(최대 10). `q` 가 숫자 15~19자리면 글 번호, 아니면 제목 일부(2자 이상, 50자에서 자름, 대소문자 무시 — 최신순). 2자 미만 `400 fields.q`. **지정할 수 있는 글만**: 보이는 글·숨김 아님·19금 아님·켜진 게시판·아직 하이라이트 아님.
- `GET /candidates?days=&minLikes=&boardId=` — `highlight.read` → `{ days, minLikes, boardId|null, items: AdminPostRef[] }`(추천 많은 순, 같으면 최신, 최대 30). `days` 0~365(0 = 전체, 기본 7) · `minLikes` 1~1000(기본 3) · `boardId` 켜진 게시판(선택). 틀리면 `fields.days|minLikes|boardId`. 대상은 post-search 와 같다. 「하이라이트로 보내기」 = 그 `id` 로 `POST /api/admin/highlights { postId }`.
- `POST` `{ postId, title?, summary?, imageFileId?, visible?, showFrom?, showUntil?, debate?, neutralEnabled?, stanceChangeAllowed? }` → **201** `HighlightItem` — `highlight.manage`. 비우면 `visible true`·`debate false`·`neutralEnabled true`·`stanceChangeAllowed true`·기간 없음·맨 끝 순서.
- `PUT /{id}` 같은 본문(**모든 칸을 보낸다**, `postId` 는 무시) → 200 `HighlightItem`. `visible`·`debate`·`neutralEnabled`·`stanceChangeAllowed` 가 null 이면 그대로(감사 `HIGHLIGHT_CREATE` 의 `after`·`HIGHLIGHT_UPDATE` 의 바뀐 칸에 `neutralEnabled`·`stanceChangeAllowed` 포함), `imageFileId` 는 null 이면 **대표 이미지를 지운다**·지금과 같으면 그대로·새 파일이면 바꾼다. 감사 `HIGHLIGHT_UPDATE` 는 바뀐 칸만.
- 칸 오류(모아서): `fields.postId`(만들 때 비었음·숫자 아님) · `fields.title`(40자 초과) · `fields.summary`(100자 초과) · `fields.showUntil`(시작보다 늦지 않음) · `fields.imageFileId`(올려 둔 HIGHLIGHT_IMAGE 파일이 아니거나 이미 다른 데 쓰임). 제목·요약은 앞뒤 공백을 떼고 비면 null.
- 글 오류: `400 HIGHLIGHT_POST_INVALID`(`fields.postId` 문구로 까닭 — 없는 글 · 지웠거나 숨김 · 19금 · 꺼진 게시판) · `409 HIGHLIGHT_DUPLICATE`(같은 글 두 번) · `404 HIGHLIGHT_NOT_FOUND`.
- `PUT /order` `{ ids: [...] }` → 204 — 전체 ID 를 새 차례로(집합이 지금과 다르면 `400 fields.ids`). 감사 `HIGHLIGHT_ORDER`(바뀐 때만).
- `PUT /{id}/visible` `{ visible }` → 200 `HighlightItem`(null 이면 `400 fields.visible`). 감사 `HIGHLIGHT_VISIBLE`(from/to).
- `DELETE /{id}` → 204 — 하이라이트만 지운다(글·이미 쓴 의견·입장은 그대로, 대표 이미지 파일은 지운다). 감사 `HIGHLIGHT_DELETE`.
- **대표 이미지 올리기**: `POST /api/admin/files`(multipart `file` + `purpose=HIGHLIGHT_IMAGE`, 권한 `highlight.manage`) → 201 `{ fileId }` → 만들기·고치기의 `imageFileId`. 로고와 같은 검사(PNG·JPG·GIF·WEBP, 2MB, 한 변 4000px, GIF 는 첫 장면 PNG, 다시 인코딩), 오류 `400 fields.file`. 지정하지 않은 파일은 「올리다 만 파일 정리」가 지운다. 바꾸거나 지우면 예전 파일은 커밋 뒤 저장소에서 지운다.
- community 와 다른 점: 대표 이미지 직접 올리기 추가(community 는 글 대표 이미지만), 소모임·`PRIVATE` 상태 없음, `post.state` 에 `ADULT` 추가, 글 칸이 `AdminPostRef`(`authorNickname` → `authorName`, `boardNameEn`·`viewCount`·`state` 포함), 응답에 `updatedBy` 추가, 권한 `highlight.*`. **공개 홈 표시 API 는 없다**(홈 + 테마 작업 때).

### 하이라이트 의견(토론) 관리 — `/api/admin/highlights/{id}/opinions`
- `GET ?stance=PRO|CON|NEUTRAL&page=&size=` — `highlight.read` → `{ highlightId, postId, debate, open, neutralEnabled, pro: Page, con: Page, neutral: Page }`, `Page { total, page, size, hasNext, items: [Opinion] }`, `Opinion { commentId, stance, authorKind, authorId|null, authorName, bodyPreview, likeCount, replyCount, createdAt, pinned, hidden, hiddenReason|null, dislikeCount, pinRequestId|null }`(`dislikeCount` 는 게시판 비추천이 꺼져 있어도 쌓인 수 그대로, `pinRequestId` = 대기 중인 고정 요청). 화면은 `neutralEnabled || neutral.total > 0` 일 때 중립 탭을 보인다.
  의견 = 그 글의 입장 배지가 붙은 원댓글(지운 것 제외). 차례 = 고정 먼저 → 추천 많은 순 → 최근. 숨긴 의견도 들어 있다(`hidden`, `hiddenReason` STAFF = 운영자 숨김 · REPORTED·ILLEGAL·RIGHTS = 신고 숨김). `stance` 를 주면 그쪽만 `items`, 다른 쪽은 `total` 만. `page` 1~, `size` 1~100(기본 30). 오류 `fields.stance|page|size` · `404 HIGHLIGHT_NOT_FOUND`. `open` = 지금 토론이 열려 있다.
- `POST|DELETE /{commentId}/pin` · `POST|DELETE /{commentId}/hide` — `highlight.manage` → 200 `Opinion`(바뀐 뒤). 이미 그 상태면 그대로(감사 없음).
  숨김 = 신고 숨김과 같은 칸(`comments.hidden_at`, 까닭 `STAFF`) — 공개 댓글 목록에 자리(`state: HIDDEN`)만 남고 글의 보이는 댓글 수가 1 줄어든다. 숨김 해제는 **STAFF 숨김만** 풀고 수를 되돌린다(신고 숨김은 여기서 풀리지 않고 그대로 돌아온다). 글쓴이에게 알리지 않는다.
  대상이 그 글의 의견이 아니면(일반 댓글·답글·다른 글·지운 댓글·형식 오류) `404 DEBATE_OPINION_NOT_FOUND`. 감사 `HIGHLIGHT_OPINION_PIN|UNPIN|HIDE|UNHIDE`(대상 `COMMENT`, highlightId·postId·stance).
- 고정은 관리자 목록의 차례와 **공개 토론 의견 목록의 맨 위**(V78 — 아래 「공개 사이트 토론」)를 정한다(홈 카드는 홈 작업 때). 직접 고정하면 그 의견의 대기 고정 요청은 승인으로 닫힌다(요청한 회원에게 결과 알림).
- **고정 요청(운영자 처리, V78)** — 목록 읽기는 `highlight.read`, 승인·거절은 `highlight.manage`(2026-10-24 — 읽기를 `highlight.read` 로 열었다):
  - `GET /{id}/pin-requests`(`highlight.read`) → `{ highlightId, postId, items: [PinRequest] }` — 대기 중(오래된 것부터, 지우거나 숨긴 의견의 요청 제외). `PinRequest { id, commentId, status(PENDING|APPROVED|REJECTED), opinion: Opinion|null, requesterId, rejectReason|null, createdAt, decidedAt|null }`.
  - `POST /{id}/pin-requests/{requestId}/approve` · `POST …/reject` `{ reason? }`(0~100자) → 200 `PinRequest`(처리 뒤). 오류: `404 HIGHLIGHT_NOT_FOUND` · `404 PIN_REQUEST_NOT_FOUND`(그 하이라이트 글의 요청이 아님·형식 오류) · `409 PIN_REQUEST_DECIDED` · `409 OPINION_UNAVAILABLE`(승인할 의견이 지워졌거나 숨김) · `400 fields.reason`. 감사 `HIGHLIGHT_PIN_REQUEST_APPROVE|REJECT`(대상 `COMMENT`, highlightId·postId·requestId·requesterId·reason). 게시판 정책에 OPERATOR 가 없어도 운영자는 처리할 수 있다(직접 고정과 같은 권한).
- community 와 다른 점: 숨김이 따로 칸(`hidden_at`)이 아니라 신고 숨김 칸 + 까닭 STAFF(그래서 답글이 없어도 공개 목록에서 빠지지 않고 HIDDEN 자리가 남는다 — 로보얌 신고 숨김과 같은 모습), `authorNickname` → `authorKind/authorId/authorName`, `hiddenReason`·`open`·`size` 추가.

### 공개 사이트 토론 (server/api — V78 갱신, 기존 댓글·차단 동작은 그대로)
- 토론이 열려 있다 = 그 글의 하이라이트가 노출 켬 + 기간 안 + `debate` 켬. 요청마다 DB 를 바로 읽는다(사본 없음). 입장 = `PRO`·`CON`·`NEUTRAL`.
- **`GET /api/posts/{id}`** `PostDetailView` 끝 두 칸: `highlighted`(노출 중인 하이라이트 글) · `debate: { enabled, myStance: "PRO"|"CON"|"NEUTRAL"|null, opinionMax, neutralEnabled, stanceChangeAllowed, votes: {pro, con, neutral, total}, pendingPinRequests: number|null, pinRequestEnabled }`. `myStance` 는 하이라이트가 끝나도 남는다. `neutralEnabled` = 하이라이트의 「중립도 받기」(하이라이트가 없으면 false). `stanceChangeAllowed`(V79) = 하이라이트의 「입장 바꾸기 허용」(하이라이트가 없으면 true — 토론이 닫혀 어차피 고를 수 없다) — false 이고 `myStance` 가 있으면 다른 입장 단추를 막는다. `votes` = 입장을 고른 **사람 수**(`post_stances`). `pendingPinRequests` = 내가 확인할 대기 고정 요청 수(아래 「의견 고정 요청」 — 확인 권한자가 아니거나 하이라이트가 아닌 글은 null). `pinRequestEnabled`(2026-10-24) = 이 글의 게시판이 의견 고정 요청을 받는가 — 유효 정책 `pinRequestReviewers` 가 비어 있지 않다(`PIN_REQUEST_OFF` 와 같은 기준, 게시판 값만 — 토론 열림·내 의견 여부는 따로). `PUT /api/posts/{id}` 응답도 같은 칸.
- **`GET /api/posts/{postId}/comments?sort=&kind=`** — `kind`: `ALL`(**기본** — 지금까지처럼 모든 댓글, 토론이 열린 글도 깨지지 않게) · `GENERAL`(입장 없는 원댓글 = **댓글 구획**) · `OPINION`(입장 있는 원댓글 전체 = **토론 구획**) · `PRO`·`CON`·`NEUTRAL`(토론 구획의 거르기). 대소문자 무시, 그 밖 `400 fields.kind`. 원댓글의 입장으로 가르고 답글은 원댓글을 따라간다(지운·숨긴 자리도 입장을 간직해 같은 구분). **화면은 댓글 구획을 늘 `GENERAL`, 토론 구획을 `OPINION`(또는 한 입장)으로 부른다.** 토론 구분(`OPINION`·`PRO`·`CON`·`NEUTRAL`)에서는 **고정한 보이는 의견이 고정한 시각 순으로 맨 위**, 나머지는 `sort` 차례(숨기거나 지운 의견은 고정이어도 올리지 않는다). `best` 는 ALL 이 아니면 그 구분의 원댓글 중에서만.
  응답 `CommentListResponse { threads, best, totalCount, kind, debate, reactions }`:
  ```
  debate: { enabled, myStance, opinionMax, neutralEnabled, stanceChangeAllowed, votes: {pro, con, neutral, total},
            counts: { all, general, generalTotal, opinions, opinionsTotal, pro, con, neutral },
            myOpinionCount, pendingPinRequests: number|null, pinRequestEnabled }   // pinRequestEnabled = 글 상세와 같다
  reactions: { dislikeEnabled, likeLabel|null, dislikeLabel|null }   // 아래 「댓글 추천·비추천」
  ```
  `counts`(모두 지우거나 숨긴 것 제외, `kind` 와 무관하게 글 전체): `all` = `totalCount`(보이는 댓글 전부, 답글 포함) · `general` = 일반 원댓글 수 · `generalTotal` = 댓글 구획 전체(일반 원댓글 + 그 답글 — 「댓글 N」) · `opinions` = 의견 원댓글 수(`pro+con+neutral`) · `opinionsTotal` = 토론 구획 전체(의견 원댓글 + 그 답글) · `pro`/`con`/`neutral` = 입장별 의견 원댓글 수(거르기 칩). **비율 막대는 `votes`, 칩은 `counts`** — 따로 센다. `myOpinionCount` = 내가 쓴 지금 입장의 의견 수(원댓글·답글, 숨긴 것 포함, 지운 것 제외 — 입장 바꾸기 확인창). 화면은 `enabled || votes.total > 0 || counts.opinions > 0` 일 때 토론 구획을 보이고(끝난 하이라이트는 읽기 전용), 중립 칸은 `neutralEnabled || votes.neutral > 0 || counts.neutral > 0` 일 때.
- **댓글 줄**(`CommentView`·`CommentThreadView`) 칸: `stance`(PRO·CON·NEUTRAL·null — 소급해 바뀐다, 답글·DELETED·HIDDEN 자리에도 남음) · `hiddenBy`(`"STAFF"`|null — 게시판 정책 `staffHiddenNotice`, 그대로) · **`pinned`**(고정한 보이는 의견이면 true — 모든 `kind` 에서 같은 값, 위로 올리는 것은 토론 구분만) · **`dislikeCount`·`disliked`**(아래, 비추천이 꺼진 게시판은 null) · **`pinRequest`**(`{ requestId, status: "PENDING"|"REJECTED", rejectReason|null }` — 내가 쓴 의견 원댓글의 가장 최근 고정 요청이 대기·거절일 때만, 그 밖 null). 차단 `blocked`·메모·`state` 는 그대로.
- **`POST /api/posts/{postId}/comments`** 요청에 `opinion`(불리언, 선택)·`stance`(선택). **의견 쓰기**인지:
  - 원댓글: `opinion: true` 또는 `stance` 를 보냈을 때만. 표시가 없으면 **입장을 가진 회원이 써도 일반 댓글**(`stance` null — 「입장이 있으면 모든 새 댓글에 배지」를 없앴다).
  - 답글: 요청 표시와 무관하게 **원댓글을 따른다** — 의견에 단 답글 = 의견(쓴 사람의 지금 입장), 일반 댓글에 단 답글 = 일반(`opinion`·`stance` 를 보내도 무시).
  - 의견 쓰기 판정 차례: 비회원 `400 fields.stance`(형식 오류도 `fields.stance`) → 토론 닫힘 `409 DEBATE_NOT_OPEN`(닫힌 글의 의견에 답글도) → 내 입장 없음 **`409 STANCE_REQUIRED`**(먼저 `PUT …/stance` 로 투표) → `stance` 를 보냈는데 지금 입장과 다름 `409 STANCE_LOCKED`(다른 창에서 바꿨을 때) → 내 **지금 입장**을 배지로. **쓰기는 입장을 정하지 않는다**(투표로만 — 예전의 「처음 의견으로 입장이 정해짐」 없앰). 응답 201 `CommentView`.
- **글자 수 한도**: 의견(배지 있음) `opinionMax` = 2,000자(`DebateReader.OPINION_MAX`), 일반 댓글 2,000자(`COMMENT_MAX`). 고칠 때도 배지 여부로 정한다.
- **`PUT /api/posts/{postId}/stance`** `{ stance }` → 200 `{ stance, moved }` — **처음 고르기(투표)도 이것**. 로그인 필수(401), 글 읽기 규칙(404 POST_NOT_FOUND · 403 POST_HIDDEN · 403 BOARD_INACTIVE) → 댓글 쓰기 자격(댓글 쓰기와 같은 403) → `400 fields.stance`(PRO·CON·NEUTRAL 밖) → `409 DEBATE_NOT_OPEN` → 「입장 바꾸기 허용」이 꺼진 하이라이트에서 이미 고른 입장을 **다른 값으로** 바꾸면 **`409 STANCE_CHANGE_LOCKED`**(V79 — 문구 「이 토론은 한 번 고른 입장을 바꿀 수 없어요.」, 처음 고르기·같은 값은 걸리지 않는다) → 중립이 꺼진 하이라이트에서 **새로** NEUTRAL 을 고르면 `400 fields.stance`(문구 「이 글은 중립 입장을 받지 않아요」 — 이미 중립이면 같은 값이라 그대로 200). 처음이면 입장을 만들고 `moved 0`, 같은 값이면 아무것도 바꾸지 않는다(멱등, `moved 0`). **입장 취소는 없다.** ~~`409 STANCE_NOT_SET`~~ 없앰.
  **소급**(사용자 결정 2026-10-24): 입장이 실제로 바뀌면 같은 트랜잭션에서 그 회원이 그 글에 쓴 입장 있는 댓글(원댓글·답글, 숨긴 것 포함, 세 입장 모두)의 `comments.stance` 를 새 입장으로 바꾼다(`DebateQueries.moveOpinions`). 지운 댓글도 함께 바꾸되 `moved` 에는 세지 않는다. 일반 댓글은 그대로, 고정·숨김 상태도 그대로. 감사·알림 없음. 동시에 처음 고르는 두 요청은 한 행만 남고 늦은 쪽은 바꾸기로 이어 간다(`post_stances` 행 잠금 — 바꾸기가 막힌 하이라이트면 늦은 쪽이 다른 값일 때 409 STANCE_CHANGE_LOCKED).
  **입장 바꾸기 잠금**(V79, 사용자 결정 2026-10-24): 소급은 바꾸기가 허용될 때만 일어난다(막히면 입장·의견 모두 그대로). 관리자가 **나중에** 허용을 끄면 이미 고른 입장·쓴 의견은 그대로 두고 그 뒤의 바꾸기만 막는다(되돌리거나 지우지 않는다). 다시 켜면 바로 바꿀 수 있다.
- **`GET /api/posts/{postId}/commenters?q=`** → `{ items: [ { author: PostAuthorView(memo 포함), parentId, replyToId } ] }` — 로그인 필수(서비스가 401). **@ 지정은 의견에만**: 이 글에 지우거나 숨기지 않은 **의견**(의견 원댓글과 그 아래 답글)을 쓴 회원 중 나·내가 차단한 회원·탈퇴 회원·비회원 제외, 닉네임에 `q`(대소문자 무시, 20자에서 자름, 비우면 전체), 가장 최근에 쓴 회원 먼저 10명. `parentId`(그 회원의 최근 의견의 원댓글)·`replyToId` 로 답글을 쓰면 의견 답글이 된다.
- 오류 코드(토론): `DEBATE_NOT_OPEN`(409) · `STANCE_LOCKED`(409 — 뜻이 「보낸 stance 가 지금 입장과 다름」으로 바뀜) · **`STANCE_CHANGE_LOCKED`(409, V79 새 — 입장 바꾸기가 막힌 하이라이트에서 바꾸기)** · **`STANCE_REQUIRED`(409, 새)** · ~~`STANCE_NOT_SET`~~(없앰) · `DEBATE_OPINION_NOT_FOUND`(404) · `HIGHLIGHT_NOT_FOUND`(404) · `HIGHLIGHT_POST_INVALID`(400) · `HIGHLIGHT_DUPLICATE`(409). 문구는 `messages_{ko,en}`.

### 댓글 추천·비추천 (server/api — V78, 사용자 결정 2026-10-24)
- **모든 댓글**(일반 댓글·의견·답글)에 추천·비추천. 한 회원이 한 댓글에 **둘 중 하나만** — 하나를 누르면 다른 하나는 같은 트랜잭션에서 풀린다. 글(게시글) 추천은 바꾸지 않았다.
- 게시판 정책 칸 3개(`board-contract.md`): `commentDislikeEnabled`(기본 false) · `commentLikeLabel` · `commentDislikeLabel`(1~10자, null = 화면 기본 이름 「추천」·「비추천」). **유효값은 댓글 목록 응답 `reactions: { dislikeEnabled, likeLabel, dislikeLabel }`** 에 싣는다(글 상세·게시판 정보에는 싣지 않았다 — 댓글 구획·토론 구획 모두 댓글 목록을 부르므로). 토론 의견도 그 글의 게시판 설정을 따른다.
- 댓글 줄 `dislikeCount`·`disliked`: 비추천이 꺼진 게시판이면 **null**(쌓인 수는 `comments.dislike_count` 에 보존하되 내리지 않는다). `liked`·`likeCount` 는 그대로.
- `POST /api/comments/{id}/like`(기존 — 응답 칸 추가) · **`POST /api/comments/{id}/dislike`** · **`DELETE /api/comments/{id}/dislike`**(취소) → 200 `CommentLikeResult { likeCount, liked, dislikeCount|null, disliked|null }`(누른 뒤 값 — 비추천이 꺼진 게시판은 비추천 칸 null). 자격·규칙은 추천과 같다: 로그인(401) · 정지·반응 권한 없음 403 · 지운 댓글 404 · 숨긴 댓글 403 `COMMENT_HIDDEN` · 꺼진 게시판 403. 비추천만의 오류: 비추천 꺼진 게시판 **`409 COMMENT_DISLIKE_DISABLED`**(누르기·취소 모두) · 내 댓글 **`400 CANNOT_DISLIKE_OWN`** · 이미 비추천 **`409 ALREADY_DISLIKED`**(누르기). 취소는 비추천하지 않았어도 지금 상태로 200. 추천은 여전히 취소가 없다(`409 ALREADY_LIKED`) — 다만 비추천을 누르면 내 추천이 풀린다. 별도 속도 제한은 없다(추천도 없다).
- 비추천은 **포인트·알림에 쓰지 않는다**. 비추천이 추천을 풀어도 이미 준 추천 보너스(`LIKE_BONUS`)는 회수하지 않는다(보너스는 같은 대상에 한 번 — 다시 추천해도 두 번 주지 않는다).
- 게시판 지우기(관리자)는 `comment_dislikes` 도 함께 지운다.

### 의견 고정 요청 (V78, 사용자 결정 2026-10-24)
- 대상 = **내가 쓴 의견 원댓글**(답글·일반 댓글은 아님). 의견 하나에 대기 요청은 하나(부분 유일 색인). 거절된 뒤에는 다시 보낼 수 있다. 취소하면 행을 지운다.
- **확인하는 사람 = 게시판 정책 `pinRequestReviewers`**(`POST_AUTHOR` 글쓴이 · `BOARD_MANAGER` 그 게시판 매니저(`board_staff` MANAGER, 스텝 제외) · `OPERATOR` 운영자(`highlight.manage`) 중 여러 개, 빈 목록 = 고정 요청 끔, 기본 `["OPERATOR"]`). 고른 사람 중 누구든 승인하면 고정(`debate_pins` — 회원이 승인하면 `pinned_by` null, 누가 승인했는지는 요청 행). **자기 요청은 자기가 확인할 수 없다**(글쓴이가 자기 글에 쓴 의견 등).
- 공개 API(api, 모두 로그인 — 401):
  - `POST /api/comments/{commentId}/pin-request` → **201** `PinRequest { id, commentId, postId, status, rejectReason|null, createdAt, decidedAt|null }`. 오류 차례: `404 COMMENT_NOT_FOUND` → 글 읽기 규칙(404·403) → 내 댓글 아님 `403 FORBIDDEN` → 의견 원댓글 아님 `409 PIN_REQUEST_NOT_OPINION` → 숨긴 의견 `409 OPINION_UNAVAILABLE` → 토론 닫힘 `409 DEBATE_NOT_OPEN` → 게시판이 요청을 받지 않음 `409 PIN_REQUEST_OFF` → 이미 고정 `409 OPINION_ALREADY_PINNED` → 대기 요청 있음 `409 PIN_REQUEST_EXISTS`.
  - `DELETE /api/comments/{commentId}/pin-request` → 204(내 대기 요청을 지운다, 없어도 204).
  - `GET /api/posts/{postId}/pin-requests` → `{ items: [ { id, commentId, stance, author: PostAuthorView(memo 포함), excerpt, likeCount, commentCreatedAt, requestedAt } ] }`(대기 중, 오래된 것부터, 내 요청·지우거나 숨긴 의견 제외) — 확인 권한자(정책의 글쓴이·매니저)만, 아니면 `403 PIN_REQUEST_FORBIDDEN`.
  - `POST /api/pin-requests/{id}/approve` · `POST /api/pin-requests/{id}/reject` `{ reason? }`(0~100자, 앞뒤 공백을 떼고 비면 없음) → 200 `PinRequest`. 오류: `404 PIN_REQUEST_NOT_FOUND` · 확인 권한 없음·자기 요청 `403 PIN_REQUEST_FORBIDDEN` · `409 PIN_REQUEST_DECIDED` · 승인할 의견이 지워졌거나 숨김 `409 OPINION_UNAVAILABLE` · `400 fields.reason`(100자 초과). 토론이 닫혔어도 승인·거절은 된다.
  - 화면 칸: 댓글 줄 `pinRequest`(내 요청 상태) · 글 `debate.pendingPinRequests`(확인 권한자에게 대기 수) · 댓글 목록·글 상세 `debate.pinRequestEnabled`(게시판이 요청을 받는가 — false 면 [고정 요청] 단추를 처음부터 숨김, 2026-10-24).
- 관리자 API: 위 「하이라이트 의견(토론) 관리」의 `…/pin-requests`.
- **알림**: 새 요청 → 회원 이벤트 **`OPINION_PIN_REQUESTED`**(받는 사람 = 정책의 글쓴이·게시판 매니저, 요청한 본인 제외 — 회원이 끌 수 있음, 일으킨 사람·글·댓글을 가리킴, 누르면 그 글) + 정책에 OPERATOR 가 있으면 관리자 이벤트 **`OPINION_PIN_REQUEST_PENDING`**(회원 이벤트와 코드가 겹치면 안 돼 이름을 달리함 — 문구 대상 코드는 회원·운영자를 넘어 하나, `highlight.manage` 보유자, 영역 콘텐츠, 5분 묶음, 링크 `/content/highlights`). 승인·거절 → 요청한 회원에게 회원 이벤트 **`OPINION_PIN_REVIEWED`**(처리 알림 — 끌 수 없음, 거절 사유가 본문). 문구 키 `member.event.*`·`member.notification.*`·`notification.event.*`·알림 문구 변수 `pinRequester`·`opinionExcerpt`·`pinRequestResult`·`pinRejectReason`(ko·en). 회원 알림 `type` CHECK 는 V78 이 다시 만들었다.

### 표 (마이그레이션)
- `V79__post_debates.sql`(옛 `V79__highlight_stance_change_allowed` 는 어느 DB 에도 적용되기 전에 이것으로 대신했다): `post_debates` 표(글의 토론 — 「중립도 받기」·「입장 바꾸기 허용」·연 사람), `highlights.debate=true` 행 옮기기, `highlights.debate`·`neutral_enabled` 칸 지우기, 게시판 정책 `debate_enabled`. 상세는 `post-debate-contract.md` 「서버 구현 결과」.
- `V74__popular_settings_exclusions.sql`: `popular_settings`(행 하나 id=1, 11칸 + updated_at·updated_by, 범위 CHECK) · `popular_exclusions(post_id PK, reason CHECK 5종, memo, excluded_by, excluded_at)` — 글 외래 키 없음(파티션 표).
- `V75__highlights.sql`: `highlights(id, post_id UNIQUE, title 40, summary 100, image_file_id → files, visible, show_from, show_until, debate, sort_order, created_*/updated_*)` · `files.purpose` CHECK 에 `HIGHLIGHT_IMAGE`.
- `V76__highlight_debate.sql`: `comments.stance`(PRO·CON CHECK) · `comments.hidden_reason` CHECK 에 `STAFF`(DROP 뒤 다시 만들기) · 색인 `ix_comments_opinion(post_id, stance, id) WHERE parent_id IS NULL AND stance IS NOT NULL`(파티션 부모 — 만드는 동안 댓글 쓰기가 잠긴다) · `post_stances(post_id, user_id PK, stance, …)` · `debate_pins(comment_id PK, post_id, pinned_by, created_at)`. 회원 알림 type CHECK 는 바꾸지 않았다(새 알림 없음).
- `V78__debate_neutral_dislike_pin_request.sql`(하나로): ① `comments.stance`·`post_stances.stance` varchar(3)→varchar(7), CHECK 를 DROP 뒤 PRO·CON·NEUTRAL 로 다시 · 색인 `ix_post_stances_post_stance(post_id, stance)`(투표 수) · `highlights.neutral_enabled boolean NOT NULL DEFAULT true` ② `comments.dislike_count integer NOT NULL DEFAULT 0` · `comment_dislikes(comment_id, user_id PK, post_id, created_at)`(댓글 ID 해시 16 조각, `ix_comment_dislikes_post_user`) ③ `debate_pin_requests(id, comment_id, post_id, requester_id → users, status PENDING|APPROVED|REJECTED, reject_reason 100, created_at, decided_at, decided_by_kind MEMBER|OPERATOR, decided_by)` + 부분 유일 색인 `ux_debate_pin_requests_pending(comment_id) WHERE status='PENDING'` · `ix_debate_pin_requests_post` ④ 게시판 정책 칸 `boards.comment_dislike_enabled boolean`·`comment_like_label varchar(10)`·`comment_dislike_label varchar(10)`·`pin_request_reviewers varchar(64)`(NULL = 기본값) + `board_defaults` 같은 칸(`comment_dislike_enabled NOT NULL DEFAULT false`, 이름 둘 NULL 허용, `pin_request_reviewers NOT NULL DEFAULT 'OPERATOR'` — 쉼표로 이은 값, 빈 글자 = 끔) ⑤ 회원 알림 `ck_member_notifications_type` 에 `OPINION_PIN_REQUESTED`·`OPINION_PIN_REVIEWED`(DROP 뒤 다시 — 커머스와 합칠 때 합집합).
- `V77__board_staff_hidden_notice.sql`: 게시판 정책 칸 `boards.staff_hidden_notice boolean`(NULL = 기본값 따름) · `board_defaults.staff_hidden_notice boolean NOT NULL DEFAULT false`. 관리자 API 칸 이름 **`staffHiddenNotice`**(기본 false) — 게시판 GET/PUT `policy.staffHiddenNotice`(null = 기본값 따름) + `defaults.staffHiddenNotice`, 기본 설정 GET/PUT `defaults.staffHiddenNotice`(PUT 필수 — 빠지면 `400 VALIDATION fields.staffHiddenNotice`, 다른 불리언 정책 칸과 같은 검사) + `overrides.staffHiddenNotice`. 권한 `board.settings`, 감사 `BOARD_UPDATE`·`BOARD_DEFAULTS_UPDATE`(바뀐 때만 before/after), `diffCount` 에 센다 — 기존 정책 칸과 같은 처리(`BoardFields.POLICY_FIELDS` 차례 `… commentGuestDelayMinutes, staffHiddenNotice, managerMax …`). admin-web `util-board.ts` `SPECS`·`BOARD_POLICY_FIELDS` 에 같은 칸을 더해야 한다(화면 세션 몫).
- 감사: 작업 14개(`POPULAR_SETTINGS_UPDATE` · `POPULAR_EXCLUDE` · `POPULAR_RESTORE` · `HIGHLIGHT_CREATE|UPDATE|DELETE|ORDER|VISIBLE` · `HIGHLIGHT_OPINION_PIN|UNPIN|HIDE|UNHIDE` · `HIGHLIGHT_PIN_REQUEST_APPROVE|REJECT`(V78)), 대상 종류 4개(`POPULAR_SETTINGS` · `POST` · `HIGHLIGHT` · `COMMENT`).

## 화면 구현 결과 (2026-10-24 — 목업 없이 실제 API 에 연결, V78 확정 시안으로 다시 만듦, 브라우저 확인 전)
공통: 서버 호출은 admin-web `composables/content/api/usePopularApi.ts`·`useHighlightApi.ts`, 대표 이미지 바이트는 새 `composables/media/api/useFileApi.ts`(`/api/admin/files` 의 유일한 자리 — 사이트 로고 올리기도 이것을 쓰게 바꿨다). 글 한 칸은 공용 `components/content/ContentPostCell.vue`(제목·말머리·게시판·글쓴이·상태 딱지·글 번호 복사). 문구는 i18n `content`·`popular`·`highlight`(ko·en), user-web 은 `comment`·`post`.

### 권한 · 메뉴 (admin-web)
- `util-permission-catalog.ts` `PERMISSIONS` 에 `notice.settings` 바로 뒤 `highlight.read`·`highlight.manage`·`popular.read`·`popular.manage`·`popular.settings`(유형 `notice`, featureReady true, 서버 `Permission` 과 같은 차례). 이름·설명 키 `permission.item.highlightRead…popularSettingsDesc`.
- `UtilAdminMenu.ITEMS` 에 「게시판」과 「포인트」 사이 `content`(`menu.content`, `pi pi-star`) — `highlights`·`popularPosts`·`popularSettings`(서버 `AdminScreenRegistry` 와 같은 키·주소·권한·아이콘). `/content/popular/settings` 는 `/content/popular` 아래지만 더 구체적인 항목이라 그 항목이 활성이다.
- 감사 화면: 작업·대상 종류 이름은 서버(`AuditLabels` + `messages_*`)가 주므로 화면 변경 없음(새 12개·4개 이름이 번들에 있다).

### 인기글 설정 `/content/popular/settings`
- 구획 4개(점수 가중치 · 시간 감쇠 · 순위 조건(+ 운영진 전용 게시판 빼기 켬/끔) · 기간(이번 주 시작 요일)) + 「지금 실시간 순위」 상위 10개(점수·추천·댓글·조회·채운 줄 딱지). 저장은 한 번에 — 하단 떠 있는 `StickySaveBar`, 우측 구역 내비에 구획별 바뀐 칸·오류 수.
- 숫자 칸은 서버가 준 `limits`(범위·`decimals`)로 미리 검사(`UtilPopular`, 비었음·숫자 아님·정수 아님·소수 자리 초과·범위 밖, 세 가중치 모두 0 → `likeWeight`). 서버 `fields.<칸>` 은 그 칸 아래 그대로, 칸 밖은 저장 줄 안.
- 제목 옆 [기본값으로] = `defaults` 로 칸만 채운다(저장은 저장 줄). 저장 응답 `recomputeRequested` 면 「다시 계산 중」 토스트.
- `recomputePending` 이면 미리보기에 「다시 계산 중」 딱지·안내, 4초마다 GET 을 다시 읽어 **미리보기만** 바꾼다(최대 8번, 고치던 칸은 그대로). [새로고침]으로도 다시 읽는다.
- 권한: `popular.settings` 없으면 칸 `readonly`·고르개 비활성, 저장 줄 대신 읽기 전용 안내, [기본값으로] 없음. 떠날 때 저장하지 않은 변경이 있으면 확인.

### 인기글 관리 `/content/popular` (`?period=` 로 첫 탭)
- 기간 탭(실시간·어제·이번 주) → 범위 한 줄(`from`~`to` 의 1밀리초 앞 날짜, 이번 주는 「월요일/일요일 시작」) · 기간 설명 · 「순위 계산 N분 전」·「점수는 계산 때 값」.
- 순위 표: 순위(계산 뒤 뺀 줄은 「뺌」 딱지·흐리게) · 글(`ContentPostCell` + 채운 줄 「어제 글」·「지난주 글」) · 점수 · 추천·댓글·조회 · 쓴 시각 · [빼기]. 빈 순위는 계산 전/기간마다 안내.
- [빼기] 대화상자: 사유 라디오(서버 `reasons` 차례) + 메모(100자, `OTHER` 는 필수 — 화면 검사 + 서버 `fields.reason|memo` 칸 아래). 404 `POST_NOT_FOUND` 는 토스트 + 다시 읽기.
- 「뺀 글」 구획(기간과 무관): 글(없으면 「글이 없어요 (#ID)」, `available false` 는 「지금 볼 수 없는 글」 딱지) · 사유 딱지 + 메모 · 뺀 사람·시각 · [다시 넣기](확인). 200개면 「최근 200개만」 안내.
- `recomputePending` 이면 「다시 계산 중」 딱지·안내 + 4초 간격 조용히 다시 읽기(최대 8번).
- 권한: `popular.manage` 없으면 [빼기]·[다시 넣기] 칸이 없고 안내 한 줄.

### 하이라이트 `/content/highlights`
- 안내: 공개 홈 표시는 아직 없고 지금은 「하이라이트」 표시·토론만 한다는 한 줄.
- 목록(서버 차례): 순서 번호 · [↑][↓](전체 ID 를 새 차례로 `PUT /order`) · 대표 이미지 · 상태 딱지(노출 중·노출 예정·기간 끝남·노출 끔·글을 볼 수 없음) · 제목(없으면 글 제목)·요약 · 「의견 받는 중」 딱지 · 글 칸 · 노출 기간 한 줄(상시 / ~부터 / ~까지 / 범위) · 수 한 줄 · 마지막 수정자 · 노출 스위치(`PUT /{id}/visible`) · [의견 N](의견 받기 켬이거나 의견이 있을 때, N 에 중립 포함) · [수정] · [삭제](확인). 노출 중 0개면 경고 한 줄.
  - 수 한 줄: 토론이 있는 줄은 「참여 N명(찬성 n · 반대 n · 중립 n) · 의견 N · 일반 댓글 N」 — 입장 숫자는 **투표 수**(`commentCounts.votes`, 입장 색 `stance-*-text`), 「의견」은 의견 원댓글 수(`pro+con+neutral`). 중립은 `neutralEnabled || votes.neutral > 0 || neutral > 0` 일 때만. 토론이 없는 줄은 「댓글 N」(키 `highlight.debateCounts`·`plainCounts`).
- 만들기·고치기 대화상자 `components/highlight/HighlightFormDialog.vue`: 대상 글(만들 때만 — 글 번호·제목 2자 이상 찾기, 고를 수 있는 글만 / 고칠 때는 고정) · 노출 제목(40, 글자 수) · 요약(100) · 대표 이미지(고르는 즉시 `POST /api/admin/files purpose=HIGHLIGHT_IMAGE`, 화면 검사는 로고와 같은 형식·2MB, [지우기] = 저장 때 `imageFileId: null`) · 노출 켬/끔 · 노출 기간(`AppDateTimePicker` 둘, 비우면 상시, 끝 ≤ 시작은 화면이 막는다) · 찬성·반대 의견 받기 · **중립도 받기**(`neutralEnabled`, 기본 켬 — 의견 받기가 꺼져 있으면 비활성으로 보이되 값은 보낸다, 도움말 「끄면 공개 토론에 찬성·반대만 보이고 새 중립 선택을 막아요. 이미 중립을 고른 표와 의견은 남아요.」). 고칠 때 모든 칸을 보낸다. 후보에서 바로 보낼 때는 `neutralEnabled: null`(서버 기본값). 서버 `fields.postId|title|summary|showUntil|imageFileId` 는 칸 아래, `HIGHLIGHT_POST_INVALID` 는 `fields.postId` 문구로 글 칸 아래, `HIGHLIGHT_DUPLICATE` 등은 대화상자 아래 한 줄, `HIGHLIGHT_NOT_FOUND` 는 닫고 토스트 + 다시 읽기.
- 후보 구획: 기간(1·3·7·30·90·365일·전체, 기본 7) · 최소 추천(1~1000, 기본 3) · 게시판(켜진 게시판 — **`board.read` 가 있을 때만** 고르개를 보인다) → [후보 찾기] → 글 줄 + [하이라이트로 보내기](`POST { postId }` 만, 기본값). 보낸 뒤 목록·후보를 다시 읽는다.
- 의견 관리 서랍 `components/highlight/HighlightOpinionsDrawer.vue`(오른쪽): 토론 열림/닫힘 딱지 · 탭 찬성 · 반대 · **중립**(`neutralEnabled || neutral.total > 0` 일 때) · **「고정 요청 N」**(`highlight.read` 면 보인다 — 2026-10-24 갱신, 아래 줄).
  - (2026-10-24 갱신) 「고정 요청」 탭은 `highlight.read`(서랍 prop `canRead`)면 보이고 요청 목록을 읽는다. 줄의 [고정]·[거절] 단추만 `highlight.manage`(`canManage`)일 때 보인다(없으면 줄은 읽기만).
  - 의견 줄: 고정·숨김 사유 딱지, 「고정 요청 대기」 딱지(`pinRequestId`), 입장 배지(`stance-*-soft`), 글쓴이, 시각, 추천·**비추천**(`dislikeCount` — 게시판 비추천이 꺼져 있어도 쌓인 수)·답글 수, 본문 미리보기 · [고정]/[고정 풀기] · [숨기기]/[숨김 풀기](운영자 숨김 STAFF 만 — 신고 숨김은 「신고 처리에서 풀어요」) · 20개씩 쪽 넘기기. 바꾸면 그 쪽·요청 탭을 다시 읽고 목록의 수도 다시 읽는다.
  - 「고정 요청」 탭(`GET …/pin-requests`, 오래된 것부터): 입장 배지·글쓴이·요청 시각·추천/비추천/답글 수·의견 미리보기 + [고정](바로 승인 `POST …/approve`) · [거절](그 자리에 사유 입력 펼침 — 0~100자 선택, 글자 수, `400 fields.reason` 은 그 칸 아래 → `POST …/reject { reason }`). 처리 뒤 요청·의견 목록을 다시 읽고 하이라이트 목록 수도 다시 읽는다. `PIN_REQUEST_NOT_FOUND`·`PIN_REQUEST_DECIDED`·`OPINION_UNAVAILABLE` 는 서버 문구 토스트 + 다시 읽기, `HIGHLIGHT_NOT_FOUND` 는 토스트 + 목록 다시 받기.
- 권한: `highlight.manage` 없으면 [추가]·순서·노출 스위치·[수정]·[삭제]·[보내기]·의견 처리 단추·고정 요청 [고정]·[거절]이 없다(의견·요청은 읽기만).

### 게시판 정책 칸 (admin-web — `board-contract.md` 「정책 칸 4개」)
- `useBoardApi.types.ts` `BoardDefaults`·`BOARD_POLICY_FIELDS`(서버 차례 `… staffHiddenNotice, commentDislikeEnabled, commentLikeLabel, commentDislikeLabel, pinRequestReviewers, managerMax …`) · `util-board.ts` `SPECS`(칸 종류 `label`·`multi` 를 더함 — 이름은 앞뒤 공백을 떼고 비면 null, 1~10자·제어 문자는 화면이 미리 막음, 확인자는 서버 차례로 정리해 보냄, diff·`diffCount`·`overrides` 반영) · `BoardSettingField.vue`(이름 입력·체크 목록).
- 기본 설정 › 일반: 「댓글 공개 시간·숨김 표시」 다음에 새 구획 「댓글 추천·의견 고정 요청」(`defaults-reaction`) — 비추천도 보이기 · 추천 이름(비우면 「추천」) · 비추천 이름(비우면 「비추천」, 비추천이 꺼져 있으면 비활성) · 의견 고정 요청을 확인하는 사람(글쓴이·게시판 매니저·운영자 체크, 모두 끄면 「고정 요청을 받지 않아요」) + 안내 「토론 의견도 그 글의 게시판 설정을 따라요 …」. 「고정 규칙」의 「비추천은 없어요」 줄은 지웠다.
- 게시판 설정 `/boards/[slug]`: 「보호·공개 시간·포인트」 다음에 같은 구획(`board-reaction`, 바뀐 칸 수 `changes.reaction`) — 칸마다 「기본값 따름」. 이름 칸의 빈 값 = 기본 설정 따름, 도움말에 「기본 설정에 이름이 있으면 여기서 「추천」으로 되돌릴 수 없어 같은 글자를 적는다」.

### 공개 사이트 글 상세 토론 (user-web — 확정 시안 3판)
- 글 상세 `pages/posts/[id]/index.vue`: `highlighted` 면 게시판 이름 옆에 「하이라이트」 표시. 댓글 목록을 **둘** 부른다(`usePostComments(id, 'GENERAL')` · `usePostComments(id, 'OPINION')`).
  - **토론 구획**(`CommentSection variant="debate"`)은 본문 글 바로 다음·추천/링크 복사/신고 줄 위(차단 접기 `FoldedRow` 안 — 본문이 접히거나 19금 확인 전이면 함께 가린다). `UtilDebate.visible`(`enabled || votes.total > 0 || counts.opinions > 0` — 목록을 받기 전에는 글 상세 `debate` 의 열림·표 수)일 때만 보이고, 그때 의견 목록을 한 번 부른다.
  - **일반 댓글**(`variant="general"`)은 맨 아래 원래 자리, 늘 `kind=GENERAL`. 「댓글 N」 = `counts.generalTotal` · 정렬 · 베스트 · 새 댓글(입장 고르개·배지 없음, `opinion` 을 보내지 않는다). 토론이 없는 글은 예전처럼 이것만.
- 토론 구획(위에서 아래로):
  - 제목 「찬성·반대 토론」 + 「N명 참여 · 의견 M」(`votes.total` · `counts.opinions`).
  - 투표 단추 [찬성 n명][반대 n명][중립 n명](`votes` — 사람 수). 고른 입장은 채운 색(`stance-*-solid`), 나머지는 입장 글자색. 처음 누르면 바로 `PUT …/stance`(토스트 「찬성으로 투표했어요」), 이미 고른 회원이 다른 단추를 누르면 확인창 「찬성 → 반대로 바꿀까요?」 + 본문(`myOpinionCount` ≥ 1 「지금까지 쓴 찬성 의견 N개도 반대로 바뀌어요.」 / 0 「앞으로 쓰는 의견에 반대 배지가 붙어요.」) → 토스트 「반대로 바꿨어요. 내 의견 N개도 반대로 옮겼어요.」. 같은 단추는 아무것도 하지 않는다(취소 없음). 바꾼 뒤 의견 목록을 다시 받는다. 비회원은 로그인 안내, 댓글 쓰기 자격이 없는 회원·끝난 토론은 비활성.
  - 비율 막대 + 범례 「찬성 12명 (52%)」(보이는 입장끼리 나눈 비율) · 안내 한 줄(투표 전 「…중 하나를 누르면 바로 입장이 정해져요…」 / 투표 뒤 「찬성으로 투표했어요. 의견은 …」 / 끝남 「토론이 끝났어요 …」 / 비회원 「투표와 의견은 로그인한 회원만 …」 + [로그인] / 자격 없음 = 댓글이 막힌 까닭).
  - 중립 칸(단추·칩·막대·범례)은 `UtilDebate.neutralVisible`(`neutralEnabled || votes.neutral > 0 || counts.neutral > 0`)일 때만. 중립이 꺼졌는데 칸이 보이면 중립 단추는 비활성(이미 중립인 회원 제외) + 안내 「이 글은 이제 새 중립 입장을 받지 않아요.」. 새 중립 거절(`400 fields.stance`)은 문구 토스트 + 다시 받기.
  - (확인 권한자 — `debate.pendingPinRequests` > 0) 「고정 요청 N건」 칸 `DebatePinReviews.vue`: `GET /api/posts/{id}/pin-requests` 줄(입장 배지·회원·「본문 앞부분」) + [고정] · [거절](그 자리에 사유 0~100자 선택 → [거절하기]). 처리하면 의견 목록을 다시 받아 수·고정 차례를 맞추고 요청 목록도 다시 받는다. `PIN_REQUEST_DECIDED`·`PIN_REQUEST_NOT_FOUND`·`OPINION_UNAVAILABLE`·`PIN_REQUEST_FORBIDDEN` 은 서버 문구 + 다시 받기, `fields.reason` 은 사유 칸 아래.
  - 의견 거르기 칩 [의견 전체 n][찬성 n][반대 n][중립 n](`counts` — 의견 수) → `kind=OPINION|PRO|CON|NEUTRAL`. 고른 칩은 글자·바탕을 뒤집은 surface, 나머지는 입장 글자색.
  - 의견 목록: 고정 의견(`pinned`)은 서버가 맨 위로 — 테두리 카드 + 「고정」 딱지(`pi-thumbtack`). 정렬 단추·베스트는 두지 않았다(보기 설정 정렬). 빈 결과 「아직 의견이 없어요」/「이 입장의 의견이 아직 없어요」.
  - 의견 쓰기(열린 토론 · 투표한 회원 · 댓글 쓰기 자격): 「내 입장 [배지] · 다른 단추를 누르면 바뀌고 …」 · @ 회원 지정(의견을 쓴 회원 — `commenters`) · 의견 입력(「찬성 의견을 남겨 보세요 (선택)」, 한도 `opinionMax`) → `opinion: true` + 내가 아는 `stance`. @ 지정으로 답글이 되면 표시 없이 `parentId`·`replyToId`(서버가 원댓글을 따름).
  - 의견 답글: 열린 토론에서만 [답글]. 투표하지 않은 회원이 누르면 「먼저 위에서 입장을 골라 주세요.」, 비회원은 로그인 안내. 답글·의견 고치기 한도 `opinionMax`.
  - 오류 `DEBATE_NOT_OPEN`·`STANCE_REQUIRED`·`STANCE_LOCKED` 는 서버 문구를 폼 아래(투표는 토스트)에 보이고 목록을 다시 받는다(쓰던 글은 남는다). `fields.stance` 는 폼 아래 한 줄. 401 은 로그인 안내.
- 댓글 줄 `CommentItem`(두 구획 공통):
  - 입장 배지 `DebateStanceBadge`(찬성 파랑 · 반대 빨강 · **중립 초록**, 답글 포함). 숨김 문구(`hiddenBy` STAFF 「운영자가 숨긴 댓글이에요」 / 그 밖 「신고로 숨겨진 댓글이에요」)·차단 접기(`FoldedRow`·`blocked`)는 그대로.
  - 추천·비추천: `reactions`(목록 응답)의 이름(비면 「추천」·「비추천」 — `comment.likeDefault|dislikeDefault`)으로 [👍 이름 n] [👎 이름 n](아이콘은 `pi-thumbs-*`). 비추천 단추는 `dislikeEnabled` 이고 `dislikeCount` 가 null 이 아닐 때만. 고른 쪽은 옅은 칩(추천 `stance-pro-soft` · 비추천 `stance-con-soft`). 응답 `{ likeCount, liked, dislikeCount, disliked }` 를 그 댓글(원댓글·답글·베스트)에 그대로 넣는다 — 하나를 누르면 다른 하나가 풀린 값. 추천은 취소 없음(눌렀으면 「이미 추천한 댓글이에요」), 비추천은 다시 누르면 취소(`DELETE …/dislike`). 내 댓글은 둘 다 비활성. `COMMENT_DISLIKE_DISABLED`·`ALREADY_DISLIKED` 는 문구 + 목록 다시 받기, `CANNOT_DISLIKE_OWN` 등은 문구만.
  - 내 의견 원댓글(고정 아님)의 고정 요청: [고정 요청] → `POST /api/comments/{id}/pin-request`(응답으로 그 줄을 대기로) → 「고정 요청 보냄 · 확인 기다리는 중 [취소]」(`DELETE`) / 거절(`pinRequest.status REJECTED`)이면 「고정 요청이 거절됐어요 · 사유: …」 + [다시 고정 요청]. 끝난 토론에서는 보내기 단추가 없다(대기 상태·취소는 보인다). `PIN_REQUEST_OFF` 면 문구 + 이 화면에서 단추를 거둔다, `PIN_REQUEST_NOT_OPINION`·`OPINION_UNAVAILABLE`·`DEBATE_NOT_OPEN`·`OPINION_ALREADY_PINNED`·`PIN_REQUEST_EXISTS` 는 문구 + 다시 받기.
  - (2026-10-24 갱신) 댓글 목록 `debate.pinRequestEnabled` 가 false 면 [고정 요청]·[다시 고정 요청] 단추를 처음부터 숨긴다(`CommentSection` `pinRequestsOpen`). 이미 보낸 요청의 대기(「확인 기다리는 중 [취소]」)·거절 사유는 그대로 보이고, `PIN_REQUEST_OFF` 처리도 그대로다. 타입은 `CommentDebate.pinRequestEnabled`·`DebateSummary.pinRequestEnabled?`(글 상세 — 지금 화면은 목록 값만 쓴다).
- 입장 색: 두 앱 `assets/css/main.css` 「토론 입장 색」에 중립 `--stance-neutral-{text,soft-bg,soft-text,solid-bg,solid-text}` + 유틸 `stance-neutral-{text,soft,solid}`(PrimeVue `--p-emerald-*`). 대비: 글자 emerald-700 흰 바탕 5.5:1·surface-100 5.0:1 / 다크 emerald-300 9.6:1 이상 · 옅은 칩 6.8:1 / 다크 11.8:1 · 채운 칩 흰 글자/emerald-700 5.5:1(시안의 emerald-600 은 3.8:1 이라 한 단계 진하게). 판정·색 고르기는 `utils/util-debate.ts`(`UtilDebate`), admin-web 은 `UtilHighlight`.
- 문구: user-web `comment.*`(ko·en — `stance`·`stanceTo`(「찬성으로」)·`debate*`·`vote*`·`changeStance*`(`{from}`·`{to}` 자리 채우기)·`opinion*`·`pin*`·`likeDefault`·`dislikeDefault`). 없어진 키: `kind.ALL|GENERAL`·`stancePick*`·`stanceGeneral`·`likeLabel`.

### 화면 구현 세션 결정 · 서버와 맞춘 점
- **@ 지정의 `@닉네임` 은 본문에 붙이지 않는다** — 로보얌 `CommentItem` 이 `replyTo` 로 `@이름` 을 이미 그려 둘이 겹친다(서버는 `replyToId` 가 원댓글이어도 `replyTo` 를 채운다). 계약의 「`@닉네임` 은 화면이 본문 앞에」를 이렇게 바꾼다.
- 하이라이트·인기글 화면의 「글 링크」는 글 번호 복사로 대신한다 — admin-web 은 공개 사이트 주소를 모른다(`me` 응답·설정에 없음).
- 후보의 게시판 고르개는 게시판 목록 API(`board.read`)를 빌려 쓴다 — `highlight.read` 만 가진 운영자는 게시판으로 거를 수 없다(전체 게시판으로 찾는다).
- 대표 이미지 화면 검사는 로고 검사(`UtilSiteSetting.logoFile` — 형식·2MB)를 그대로 쓴다. 한 변 4000px·GIF 첫 장면은 서버 판정.
- (V78 화면) 의견 쓰기는 `opinion: true` 와 함께 내가 아는 지금 입장(`stance`)도 보낸다 — 다른 창에서 입장을 바꿨으면 `STANCE_LOCKED` 로 막혀 배지가 화면과 어긋나지 않는다.
- (V78 화면) 중립 칸은 지시문의 「`neutralEnabled` false 면 숨김」이 아니라 「서버 구현 결과」의 `neutralEnabled || votes.neutral > 0 || counts.neutral > 0` 를 따랐다(이미 중립인 표·의견이 사라져 보이지 않게).
- (V78 화면) 토론 구획은 차단 접기·19금 가림 안에 둔다(본문과 한 묶음). 토론 구획에는 정렬 단추·베스트를 두지 않았다(시안에 없음 — 고정 의견이 그 자리).
- (V78 화면) 추천·비추천의 고른 색은 시안대로 찬성(파랑)·반대(빨강) 옅은 칩을 빌려 쓴다.

## 미확정
- 공개 홈 하이라이트 표시와 홈의 토론 요약(홈 + 테마 작업 때). 의견 고정은 그때 홈 카드 차례에 쓴다.
- 글 목록 줄의 `highlighted`(community `PostRow.highlighted`)는 만들지 않았다 — 글 상세에만 있다. 필요하면 목록에 붙인다(노출 중 하이라이트 글 ID 모음 사본).
- 다시 계산은 요청 표를 거쳐 배치를 켠 서버가 돌리므로 응답 직후 화면이 바로 읽으면 아직 옛 순위일 수 있다(`recomputePending` 으로 표시).
- 첫 하이라이트를 동시에 같은 글로 두 번 만들면 잠글 행이 없어 유일 제약 위반(500)이 날 수 있다(드묾).
- SQL·마이그레이션은 실제 DB 에서 아직 돌려 보지 않았다(메인 세션이 적용).
- ~~(화면 세션) 공개 댓글에 숨긴 까닭이 없다~~ → 게시판 옵션 `staffHiddenNotice` + `CommentView.hiddenBy` 로 정리(2026-10-24, 위 「공개 사이트 토론」·「표」).
- 입장 바꾸기와 같은 때에 옛 입장을 읽은 댓글 쓰기가 커밋되면 그 댓글 하나는 옛 배지로 남을 수 있다(드묾 — 다시 바꾸면 따라온다).
- (V78) 예전 규칙(「입장이 있으면 모든 새 댓글에 배지」)으로 일반 댓글 아래 달린 배지 답글은 그대로 둔다 — 구획은 원댓글로 가르므로 댓글 구획에 남고, 배지만 붙어 보일 수 있다(데이터 정리는 하지 않았다).
- (V78) 댓글 추천은 여전히 취소가 없지만 비추천을 누르면 추천이 풀린다(사실상 추천을 거둘 길이 생긴다). 이미 준 추천 보너스는 회수하지 않는다 — 바꾸려면 정책을 정해 달라.
- (V78) 운영자 숨김·지운 의견의 대기 고정 요청은 목록·수에서 빠질 뿐 대기로 남는다(숨김을 풀면 다시 보인다). 하이라이트를 지워도 고정·요청 행은 남는다.
- ~~(V78) 확인 권한자(글쓴이·매니저)의 공개 화면(대기 목록·승인·거절)과 관리자 의견 관리의 요청 탭은 화면 세션 몫이다.~~ → 화면 구현(2026-10-24, 위 「화면 구현 결과」).
- ~~(화면 세션, V78) 공개 응답에 「이 게시판이 의견 고정 요청을 받는가」(`pinRequestReviewers` 가 빈 목록인지)가 없어, 내 의견의 [고정 요청] 단추는 늘 보이고 누른 뒤 `409 PIN_REQUEST_OFF` 로 알게 된다(그 뒤 이 화면에서 단추를 거둔다). 댓글 목록 `reactions` 나 `debate` 에 불리언 하나(예: `pinRequestEnabled`)를 실으면 처음부터 숨길 수 있다 — 서버 확인 필요.~~ → 해결(2026-10-24): 댓글 목록·글 상세 `debate.pinRequestEnabled`(위 「공개 사이트 토론」). 화면 연결 끝(2026-10-24, 「화면 구현 결과」 댓글 줄).
- ~~(화면 세션, V78) 관리자 「고정 요청」 목록은 읽기에도 `highlight.manage` 가 필요해 `highlight.read` 만 가진 운영자는 요청 탭을 볼 수 없다(의견 줄의 「고정 요청 대기」 딱지만 보인다).~~ → 해결(2026-10-24): `GET …/pin-requests` 는 `highlight.read`(승인·거절은 `highlight.manage` 그대로). 화면의 탭 조건도 `highlight.read` 로 바꿨다(2026-10-24, 「화면 구현 결과」 의견 관리 서랍).
- (화면 세션) 하이라이트 화면의 게시판 거르기용 가벼운 게시판 목록(`highlight.read` 로 읽는)이 서버에 없다. 공개 사이트 글 링크용 공개 주소도 admin-web 에 없다.
