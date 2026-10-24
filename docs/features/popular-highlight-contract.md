# 인기글 설정·인기글 관리·하이라이트(토론 포함) 계약 (결정 2026-10-24 — 구현 전)

**의도**: 인기글 순위 규칙을 운영자가 화면에서 정하고(지금은 서버 상수), 순위에 오른 부적절한 글을 빼고 다시 넣고, 운영자가 고른 글을 하이라이트로 지정해 그 글에 찬성·반대 의견(토론)을 받는다. community 의 같은 기능을 로보얌 구조·권한·배치 방식에 맞춰 옮긴다.
**정책 원본(읽기 전용)**: community `D:\community\docs\api-popular.md`(8·9절 관리자, 2·3절 규칙), `api-content.md` 1절(하이라이트)·1-2절(인기글 관리), `api-debate.md`(토론 전부). 로보얌의 현재 인기글 구현은 `member-activity-contract.md` 「서버 구현 결과」(DB 배치 `popular-rank`, `R__batch_24_popular_rank.sql`, 표 `post_rank_snapshots`).

## 결정 (2026-10-24, 사용자)
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

## 마이그레이션 (배정)
shared `V74`(인기글 설정·제외) · `V75`(하이라이트) · `V76`(토론). 게시판 옵션(운영자 숨김 공개)이 **V77**. 월요일 커머스 병합 때 커머스 shared 마이그레이션은 **V78부터**로 미룬다.

## 서버 구현 결과 (2026-10-24 — 서버만, 화면은 다음 세션. 실제 DB 에서 아직 돌려 보지 않았다)
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
  status(SHOWING|SCHEDULED|EXPIRED|OFF|BLOCKED), debate, post: AdminPostRef|null, commentCounts: {all, general, pro, con},
  createdAt, updatedAt, updatedBy: {id, name|null}|null }
```
  `status` 판정 차례: 글이 PUBLIC 아님(또는 글 없음) BLOCKED → 노출 끔 OFF → 시작 전 SCHEDULED → 끝남 EXPIRED → SHOWING. `visibleCount` = SHOWING 수. `commentCounts.all` = 글의 보이는 댓글 수(답글 포함), 나머지는 지우거나 숨기지 않은 원댓글의 입장별 수.
- `GET /post-search?q=` — `highlight.read` → `AdminPostRef[]`(최대 10). `q` 가 숫자 15~19자리면 글 번호, 아니면 제목 일부(2자 이상, 50자에서 자름, 대소문자 무시 — 최신순). 2자 미만 `400 fields.q`. **지정할 수 있는 글만**: 보이는 글·숨김 아님·19금 아님·켜진 게시판·아직 하이라이트 아님.
- `GET /candidates?days=&minLikes=&boardId=` — `highlight.read` → `{ days, minLikes, boardId|null, items: AdminPostRef[] }`(추천 많은 순, 같으면 최신, 최대 30). `days` 0~365(0 = 전체, 기본 7) · `minLikes` 1~1000(기본 3) · `boardId` 켜진 게시판(선택). 틀리면 `fields.days|minLikes|boardId`. 대상은 post-search 와 같다. 「하이라이트로 보내기」 = 그 `id` 로 `POST /api/admin/highlights { postId }`.
- `POST` `{ postId, title?, summary?, imageFileId?, visible?, showFrom?, showUntil?, debate? }` → **201** `HighlightItem` — `highlight.manage`. 비우면 `visible true`·`debate false`·기간 없음·맨 끝 순서.
- `PUT /{id}` 같은 본문(**모든 칸을 보낸다**, `postId` 는 무시) → 200 `HighlightItem`. `visible`·`debate` 가 null 이면 그대로, `imageFileId` 는 null 이면 **대표 이미지를 지운다**·지금과 같으면 그대로·새 파일이면 바꾼다. 감사 `HIGHLIGHT_UPDATE` 는 바뀐 칸만.
- 칸 오류(모아서): `fields.postId`(만들 때 비었음·숫자 아님) · `fields.title`(40자 초과) · `fields.summary`(100자 초과) · `fields.showUntil`(시작보다 늦지 않음) · `fields.imageFileId`(올려 둔 HIGHLIGHT_IMAGE 파일이 아니거나 이미 다른 데 쓰임). 제목·요약은 앞뒤 공백을 떼고 비면 null.
- 글 오류: `400 HIGHLIGHT_POST_INVALID`(`fields.postId` 문구로 까닭 — 없는 글 · 지웠거나 숨김 · 19금 · 꺼진 게시판) · `409 HIGHLIGHT_DUPLICATE`(같은 글 두 번) · `404 HIGHLIGHT_NOT_FOUND`.
- `PUT /order` `{ ids: [...] }` → 204 — 전체 ID 를 새 차례로(집합이 지금과 다르면 `400 fields.ids`). 감사 `HIGHLIGHT_ORDER`(바뀐 때만).
- `PUT /{id}/visible` `{ visible }` → 200 `HighlightItem`(null 이면 `400 fields.visible`). 감사 `HIGHLIGHT_VISIBLE`(from/to).
- `DELETE /{id}` → 204 — 하이라이트만 지운다(글·이미 쓴 의견·입장은 그대로, 대표 이미지 파일은 지운다). 감사 `HIGHLIGHT_DELETE`.
- **대표 이미지 올리기**: `POST /api/admin/files`(multipart `file` + `purpose=HIGHLIGHT_IMAGE`, 권한 `highlight.manage`) → 201 `{ fileId }` → 만들기·고치기의 `imageFileId`. 로고와 같은 검사(PNG·JPG·GIF·WEBP, 2MB, 한 변 4000px, GIF 는 첫 장면 PNG, 다시 인코딩), 오류 `400 fields.file`. 지정하지 않은 파일은 「올리다 만 파일 정리」가 지운다. 바꾸거나 지우면 예전 파일은 커밋 뒤 저장소에서 지운다.
- community 와 다른 점: 대표 이미지 직접 올리기 추가(community 는 글 대표 이미지만), 소모임·`PRIVATE` 상태 없음, `post.state` 에 `ADULT` 추가, 글 칸이 `AdminPostRef`(`authorNickname` → `authorName`, `boardNameEn`·`viewCount`·`state` 포함), 응답에 `updatedBy` 추가, 권한 `highlight.*`. **공개 홈 표시 API 는 없다**(홈 + 테마 작업 때).

### 하이라이트 의견(토론) 관리 — `/api/admin/highlights/{id}/opinions`
- `GET ?stance=PRO|CON&page=&size=` — `highlight.read` → `{ highlightId, postId, debate, open, pro: Page, con: Page }`, `Page { total, page, size, hasNext, items: [Opinion] }`, `Opinion { commentId, stance, authorKind, authorId|null, authorName, bodyPreview, likeCount, replyCount, createdAt, pinned, hidden, hiddenReason|null }`.
  의견 = 그 글의 입장 배지가 붙은 원댓글(지운 것 제외). 차례 = 고정 먼저 → 추천 많은 순 → 최근. 숨긴 의견도 들어 있다(`hidden`, `hiddenReason` STAFF = 운영자 숨김 · REPORTED·ILLEGAL·RIGHTS = 신고 숨김). `stance` 를 주면 그쪽만 `items`, 다른 쪽은 `total` 만. `page` 1~, `size` 1~100(기본 30). 오류 `fields.stance|page|size` · `404 HIGHLIGHT_NOT_FOUND`. `open` = 지금 토론이 열려 있다.
- `POST|DELETE /{commentId}/pin` · `POST|DELETE /{commentId}/hide` — `highlight.manage` → 200 `Opinion`(바뀐 뒤). 이미 그 상태면 그대로(감사 없음).
  숨김 = 신고 숨김과 같은 칸(`comments.hidden_at`, 까닭 `STAFF`) — 공개 댓글 목록에 자리(`state: HIDDEN`)만 남고 글의 보이는 댓글 수가 1 줄어든다. 숨김 해제는 **STAFF 숨김만** 풀고 수를 되돌린다(신고 숨김은 여기서 풀리지 않고 그대로 돌아온다). 글쓴이에게 알리지 않는다.
  대상이 그 글의 의견이 아니면(일반 댓글·답글·다른 글·지운 댓글·형식 오류) `404 DEBATE_OPINION_NOT_FOUND`. 감사 `HIGHLIGHT_OPINION_PIN|UNPIN|HIDE|UNHIDE`(대상 `COMMENT`, highlightId·postId·stance).
- 고정은 지금 관리자 목록의 차례만 바꾼다(홈 카드는 홈 작업 때).
- community 와 다른 점: 숨김이 따로 칸(`hidden_at`)이 아니라 신고 숨김 칸 + 까닭 STAFF(그래서 답글이 없어도 공개 목록에서 빠지지 않고 HIDDEN 자리가 남는다 — 로보얌 신고 숨김과 같은 모습), `authorNickname` → `authorKind/authorId/authorName`, `hiddenReason`·`open`·`size` 추가.

### 공개 사이트 토론 (server/api — 기존 댓글·차단 동작은 그대로)
- 토론이 열려 있다 = 그 글의 하이라이트가 노출 켬 + 기간 안 + `debate` 켬. 요청마다 DB 를 바로 읽는다(사본 없음).
- **`GET /api/posts/{id}`** `PostDetailView` 끝에 두 칸: `highlighted`(노출 중인 하이라이트 글 — 「찬성·반대 의견 받기」와 무관) · `debate: { enabled, myStance: "PRO"|"CON"|null, opinionMax }`. `myStance` 는 하이라이트가 끝나도 남는다. `PUT /api/posts/{id}` 응답도 같은 칸.
- **`GET /api/posts/{postId}/comments?sort=&kind=`** — `kind` ALL(기본)·GENERAL·PRO·CON(대소문자 무시, 그 밖 `400 fields.kind`). 원댓글의 입장으로 가르고 답글은 원댓글을 따라간다(지운·숨긴 자리도 입장을 간직해 같은 탭). `best` 는 ALL 이 아니면 그 구분의 원댓글 중에서만. 응답 `CommentListResponse` 에 `kind` · `debate: { enabled, myStance, opinionMax, counts: { all, general, pro, con } }` 추가(늘 있다, 하이라이트가 아니면 `enabled false`). `counts.all` = `totalCount`(글 전체, kind 와 무관) · general/pro/con = 지우거나 숨기지 않은 원댓글 수. **`debate.myOpinionCount`**(int) = 내가 이 글에 쓴 지금 입장(`myStance`)의 의견 수(원댓글·답글, 숨긴 것 포함, 지운 것 제외 — 비로그인·입장 없음 0) — 입장 바꾸기 확인창의 「N개도 바뀌어요」(아래 `moved` 와 같은 기준). 화면은 `enabled || pro+con > 0` 일 때 탭을 보인다(끝난 하이라이트는 읽기 전용).
- **`CommentView`·`CommentThreadView` 에 `stance`**(PRO·CON·null — 쓸 때의 배지, 입장 바꾸기로 소급해 바뀐다. 답글·DELETED·HIDDEN 자리에도 남음). 차단 `blocked`·메모·`state` 는 그대로.
- **`CommentView`·`CommentThreadView` 에 `hiddenBy`**(`"STAFF"` | null): `state` 가 HIDDEN 이고 운영자가 숨겼으며(`hidden_reason` STAFF) 그 게시판의 정책 칸 **`staffHiddenNotice`** 유효값이 true 일 때만 `"STAFF"`. 옵션이 꺼진 게시판(기본)·신고 숨김·숨기지 않은 댓글은 늘 null — 꺼진 게시판에서는 운영자 숨김이 신고 숨김과 구별되지 않는다. 화면: `hiddenBy === 'STAFF'` 면 「운영자가 숨긴 댓글이에요」, 그 밖 HIDDEN 은 지금 문구. 게시판 값은 게시판 사본 주기(15초)만큼 늦게 반영될 수 있다.
- **`POST /api/posts/{postId}/comments`** 요청에 `stance`(선택, PRO·CON, 대소문자·공백 무시). 해석(community 표 그대로): 보냈는데 토론이 닫힘 `409 DEBATE_NOT_OPEN` · 내 입장 없음 → 그 입장으로 쓰고 입장이 정해짐 · 같음 → 그대로 · 다름 `409 STANCE_LOCKED`(입장은 PUT 으로만) · 안 보냈고 열림 + 내 입장 있음 → 서버가 배지를 붙인다(답글 포함) · 그 밖 일반 댓글. 처음 정하는 입장은 댓글과 한 트랜잭션(쓰기가 실패하면 입장도 없다, 동시에 다른 입장으로 처음 쓰면 늦은 쪽 409 STANCE_LOCKED). **비회원이 `stance` 를 보내면 `400 fields.stance`**(형식 오류도 `fields.stance`). 응답 201 `CommentView` 에 `stance`.
- **글자 수 한도**: 배지가 붙는 댓글 `opinionMax` = 2,000자, 일반 댓글 2,000자(기존 `COMMENT_MAX`) — community 는 토론 2,000 · 일반 1,000 이었지만 로보얌 일반 댓글이 이미 2,000 이라 같은 값(따로 둔 상수 `DebateReader.OPINION_MAX`). 고칠 때도 배지 여부로 정한다(고치는 요청에는 입장이 없다).
- **`PUT /api/posts/{postId}/stance`** `{ stance }` → 200 `{ stance, moved }` — 로그인 필수(401), 글 읽기 규칙(404 POST_NOT_FOUND · 403 POST_HIDDEN · 403 BOARD_INACTIVE) → 댓글 쓰기 자격(댓글 쓰기와 같은 403) → `400 fields.stance` → `409 DEBATE_NOT_OPEN` → `409 STANCE_NOT_SET`(정한 적 없음). 같은 값이면 아무것도 바꾸지 않는다(`moved 0`, 멱등).
  **소급**(사용자 결정 2026-10-24): 입장이 실제로 바뀌면 같은 트랜잭션에서 그 회원이 그 글에 쓴 입장 있는 댓글(원댓글·답글, 숨긴 것 포함)의 `comments.stance` 를 새 입장으로 바꾼다(`DebateQueries.moveOpinions`). 지운 댓글도 함께 바꾼다(답글이 달린 지운 자리가 옛 탭에 남지 않게) — 다만 `moved` 에는 세지 않는다. 일반 댓글(배지 없음)은 그대로, 고정(`debate_pins`)·숨김 상태도 그대로. `moved` = 옮긴 의견 중 지우지 않은 것의 수(목록 `debate.myOpinionCount` 와 같은 기준). 감사·알림 없음. 이후 쓰는 댓글도 새 배지.
- **`GET /api/posts/{postId}/commenters?q=`** → `{ items: [ { author: PostAuthorView(memo 포함), parentId, replyToId } ] }` — 로그인 필수(서비스가 401). 이 글에 지우거나 숨기지 않은 댓글을 쓴 회원 중 나·내가 차단한 회원·탈퇴 회원·비회원 제외, 닉네임에 `q`(대소문자 무시, 20자에서 자름, 비우면 전체), 가장 최근에 쓴 회원 먼저 10명. 고른 회원에게는 `POST …/comments` 에 `parentId`(그 회원의 최근 댓글의 원댓글)·`replyToId`(그 최근 댓글)를 보내 답글로 쓴다(답글 알림은 기존 그대로, 따로 멘션 알림 없음, `@닉네임` 은 화면이 본문 앞에). community 는 `replyToId` 하나였지만 로보얌 답글은 `parentId` 가 필요해 둘 다 준다.
- 새 오류 코드: `DEBATE_NOT_OPEN`(409) · `STANCE_LOCKED`(409) · `STANCE_NOT_SET`(409) · `DEBATE_OPINION_NOT_FOUND`(404) · `HIGHLIGHT_NOT_FOUND`(404) · `HIGHLIGHT_POST_INVALID`(400) · `HIGHLIGHT_DUPLICATE`(409). 문구는 `messages_{ko,en}`.

### 표 (마이그레이션)
- `V74__popular_settings_exclusions.sql`: `popular_settings`(행 하나 id=1, 11칸 + updated_at·updated_by, 범위 CHECK) · `popular_exclusions(post_id PK, reason CHECK 5종, memo, excluded_by, excluded_at)` — 글 외래 키 없음(파티션 표).
- `V75__highlights.sql`: `highlights(id, post_id UNIQUE, title 40, summary 100, image_file_id → files, visible, show_from, show_until, debate, sort_order, created_*/updated_*)` · `files.purpose` CHECK 에 `HIGHLIGHT_IMAGE`.
- `V76__highlight_debate.sql`: `comments.stance`(PRO·CON CHECK) · `comments.hidden_reason` CHECK 에 `STAFF`(DROP 뒤 다시 만들기) · 색인 `ix_comments_opinion(post_id, stance, id) WHERE parent_id IS NULL AND stance IS NOT NULL`(파티션 부모 — 만드는 동안 댓글 쓰기가 잠긴다) · `post_stances(post_id, user_id PK, stance, …)` · `debate_pins(comment_id PK, post_id, pinned_by, created_at)`. 회원 알림 type CHECK 는 바꾸지 않았다(새 알림 없음).
- `V77__board_staff_hidden_notice.sql`: 게시판 정책 칸 `boards.staff_hidden_notice boolean`(NULL = 기본값 따름) · `board_defaults.staff_hidden_notice boolean NOT NULL DEFAULT false`. 관리자 API 칸 이름 **`staffHiddenNotice`**(기본 false) — 게시판 GET/PUT `policy.staffHiddenNotice`(null = 기본값 따름) + `defaults.staffHiddenNotice`, 기본 설정 GET/PUT `defaults.staffHiddenNotice`(PUT 필수 — 빠지면 `400 VALIDATION fields.staffHiddenNotice`, 다른 불리언 정책 칸과 같은 검사) + `overrides.staffHiddenNotice`. 권한 `board.settings`, 감사 `BOARD_UPDATE`·`BOARD_DEFAULTS_UPDATE`(바뀐 때만 before/after), `diffCount` 에 센다 — 기존 정책 칸과 같은 처리(`BoardFields.POLICY_FIELDS` 차례 `… commentGuestDelayMinutes, staffHiddenNotice, managerMax …`). admin-web `util-board.ts` `SPECS`·`BOARD_POLICY_FIELDS` 에 같은 칸을 더해야 한다(화면 세션 몫).
- 감사: 작업 12개(`POPULAR_SETTINGS_UPDATE` · `POPULAR_EXCLUDE` · `POPULAR_RESTORE` · `HIGHLIGHT_CREATE|UPDATE|DELETE|ORDER|VISIBLE` · `HIGHLIGHT_OPINION_PIN|UNPIN|HIDE|UNHIDE`), 대상 종류 4개(`POPULAR_SETTINGS` · `POST` · `HIGHLIGHT` · `COMMENT`).

## 화면 구현 결과 (2026-10-24 — 목업 없이 실제 API 에 연결, 브라우저 확인 전)
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
- 목록(서버 차례): 순서 번호 · [↑][↓](전체 ID 를 새 차례로 `PUT /order`) · 대표 이미지 · 상태 딱지(노출 중·노출 예정·기간 끝남·노출 끔·글을 볼 수 없음) · 제목(없으면 글 제목)·요약 · 「의견 받는 중」 딱지 · 글 칸 · 노출 기간 한 줄(상시 / ~부터 / ~까지 / 범위) · 댓글·찬성·반대·일반 수 · 마지막 수정자 · 노출 스위치(`PUT /{id}/visible`) · [의견 N](의견 받기 켬이거나 의견이 있을 때) · [수정] · [삭제](확인). 노출 중 0개면 경고 한 줄.
- 만들기·고치기 대화상자 `components/highlight/HighlightFormDialog.vue`: 대상 글(만들 때만 — 글 번호·제목 2자 이상 찾기, 고를 수 있는 글만 / 고칠 때는 고정) · 노출 제목(40, 글자 수) · 요약(100) · 대표 이미지(고르는 즉시 `POST /api/admin/files purpose=HIGHLIGHT_IMAGE`, 화면 검사는 로고와 같은 형식·2MB, [지우기] = 저장 때 `imageFileId: null`) · 노출 켬/끔 · 노출 기간(`AppDateTimePicker` 둘, 비우면 상시, 끝 ≤ 시작은 화면이 막는다) · 찬성·반대 의견 받기. 고칠 때 모든 칸을 보낸다. 서버 `fields.postId|title|summary|showUntil|imageFileId` 는 칸 아래, `HIGHLIGHT_POST_INVALID` 는 `fields.postId` 문구로 글 칸 아래, `HIGHLIGHT_DUPLICATE` 등은 대화상자 아래 한 줄, `HIGHLIGHT_NOT_FOUND` 는 닫고 토스트 + 다시 읽기.
- 후보 구획: 기간(1·3·7·30·90·365일·전체, 기본 7) · 최소 추천(1~1000, 기본 3) · 게시판(켜진 게시판 — **`board.read` 가 있을 때만** 고르개를 보인다) → [후보 찾기] → 글 줄 + [하이라이트로 보내기](`POST { postId }` 만, 기본값). 보낸 뒤 목록·후보를 다시 읽는다.
- 의견 관리 서랍 `components/highlight/HighlightOpinionsDrawer.vue`(오른쪽): 토론 열림/닫힘 딱지 · 찬성/반대 탭(수) · 줄(고정·숨김 사유 딱지, 글쓴이, 시각, 추천·답글 수, 본문 미리보기) · [고정]/[고정 풀기] · [숨기기]/[숨김 풀기](운영자 숨김 STAFF 만 — 신고 숨김은 「신고 처리에서 풀어요」) · 20개씩 쪽 넘기기. 바꾸면 그 쪽을 다시 읽고 목록의 수도 다시 읽는다.
- 권한: `highlight.manage` 없으면 [추가]·순서·노출 스위치·[수정]·[삭제]·[보내기]·의견 처리 단추가 없다(의견은 읽기만).

### 공개 사이트 글 상세 토론 (user-web)
- 글 상세 `pages/posts/[id]/index.vue`: `highlighted` 면 게시판 이름 옆에 「하이라이트」 표시(차단 접기와 무관하게 보인다).
- 댓글 구획 `CommentSection`(목록의 `debate` 가 정본): `enabled || pro+con > 0` 이면 정렬 줄 아래에 [전체 n · 일반 n · 찬성 n · 반대 n] 탭(`kind` 로 다시 받기, 글이 바뀌면 전체로). 끝난 토론은 「이미 쓴 의견만 볼 수 있어요」 한 줄. 구분 탭이 빈 결과면 「이 구분의 댓글이 아직 없어요」.
- 댓글 줄 `CommentItem`: `stance` 가 있으면 작성자 앞에 찬성(파랑)·반대(빨강) 배지(`DebateStanceBadge`, 답글 포함). 지운·숨긴 자리는 예전 문구 그대로. 차단 접기(`FoldedRow`·`blocked`)는 그대로 — 배지도 접힌 줄 안에 있다.
- 열린 토론의 새 댓글 상자 위: 비회원 = 「찬성·반대 의견은 로그인한 회원만」 + [로그인] / 입장 없는 회원 = [일반·찬성·반대] 고르기(고른 것만 `stance` 로 보낸다) / 입장 있는 회원 = 「내 입장 [배지] · 입장 바꾸기」(확인 → `PUT …/stance` → 목록 다시 받기). 답글은 입장을 보내지 않는다(서버가 붙인다).
- 글자 수: 배지가 붙는 새 댓글(고른 입장·내 입장)·내 입장이 있는 답글은 `debate.opinionMax`, 그 밖은 기존 한도(`CommentForm` 새 속성 `maxLength`).
- @ 회원 지정(열린 토론·회원만): [회원 지정] → 닉네임 찾기(250ms 뒤 `GET …/commenters?q=`, 비우면 최근 10명) → 고르면 「@이름님에게 답글로 써요」 + 새 댓글 상자가 그 회원의 최근 댓글에 대한 답글(`parentId`·`replyToId`)이 된다.
- 오류: `DEBATE_NOT_OPEN`·`STANCE_LOCKED`·`STANCE_NOT_SET` 은 서버 문구를 폼 아래(입장 바꾸기는 토스트)에 보이고 목록을 다시 받아 열림·내 입장을 맞춘다(쓰던 글은 남는다). 401 은 로그인 안내.

### 사용자 결정 반영 (2026-10-24 — 운영자 숨김 공개 · 입장 바꾸기 소급 · 찬성·반대 색, 브라우저 확인 전)
- **게시판 옵션 `staffHiddenNotice`**(admin-web): `useBoardApi.types.ts` `BoardDefaults`·`BOARD_POLICY_FIELDS`(`commentGuestDelayMinutes` 다음)·`util-board.ts` `SPECS`(bool, 덮어쓰기 가능, `board.settings`). 기본 설정 › 일반의 「댓글 공개 시간·숨김 표시」 구획(`defaults-guest`, 구획 이름을 바꿈)과 게시판 설정의 「보호·공개 시간·포인트」 구획(`PROTECT_FIELDS`·`detailChanges.protect`)에 칸 「운영자가 숨긴 댓글임을 밝히기」. 문구 `board.field|fieldHelp.staffHiddenNotice`(ko·en).
- **공개 숨김 문구**(user-web `CommentItem`): `hiddenBy === 'STAFF'` 면 「운영자가 숨긴 댓글이에요」(`comment.hiddenByStaff`), 그 밖 HIDDEN 은 「신고로 숨겨진 댓글이에요」 그대로. 타입 `CommentView.hiddenBy?: 'STAFF' | null`.
- **입장 바꾸기**(user-web `CommentSection`): 단추가 바뀔 입장(「반대로 바꾸기」/「찬성으로 바꾸기」, `comment.changeStanceTo.*`). 확인창 제목 「찬성 → 반대로 바꿀까요?」, 본문은 `debate.myOpinionCount` ≥ 1 이면 「지금까지 쓴 찬성 의견 N개도 반대로 바뀌어요.」, 0 이면 「앞으로 쓰는 의견에 반대 배지가 붙어요.」. `PUT …/stance` 응답 `{ stance, moved }`(`StanceChangeResult`)를 받고 목록을 다시 받은 뒤 토스트 「반대로 바꿨어요. 내 의견 N개도 반대로 옮겼어요.」(`moved 0` 이면 앞 문장만).
- **찬성·반대 색**: 두 앱 `assets/css/main.css` 에 같은 토큰 구획 「토론 입장 색」 — `--stance-{pro,con}-{text,soft-bg,soft-text,solid-bg,solid-text}`(PrimeVue `--p-blue-*`·`--p-red-*` + `light-dark()`) + 유틸 `stance-{pro,con}-{text,soft,solid}`. 공개: 배지·「내 입장」 칩 `soft`, 구분 탭·입장 고르개는 고르면 `solid`(전체·일반은 중립 surface 채움), 아니면 그 색 글자, 바꾸기 단추는 바뀔 입장 글자색. 관리자: 의견 관리 서랍 탭 글자 `text`·줄마다 입장 배지 `soft`(`UtilHighlight.stanceTextClass|stanceSoftClass`), 하이라이트 줄의 찬성·반대 수 `text`(`highlight.commentCounts` 를 `{pro}`·`{con}` 자리 채우기로 바꿈). 대비 라이트·다크 모두 4.5:1 이상(가장 낮은 것 흰 글자/red-600 4.8:1), 늘 「찬성」·「반대」 글자와 함께.

### 화면 구현 세션 결정 · 서버와 맞춘 점
- **@ 지정의 `@닉네임` 은 본문에 붙이지 않는다** — 로보얌 `CommentItem` 이 `replyTo` 로 `@이름` 을 이미 그려 둘이 겹친다(서버는 `replyToId` 가 원댓글이어도 `replyTo` 를 채운다). 계약의 「`@닉네임` 은 화면이 본문 앞에」를 이렇게 바꾼다.
- 하이라이트·인기글 화면의 「글 링크」는 글 번호 복사로 대신한다 — admin-web 은 공개 사이트 주소를 모른다(`me` 응답·설정에 없음).
- 후보의 게시판 고르개는 게시판 목록 API(`board.read`)를 빌려 쓴다 — `highlight.read` 만 가진 운영자는 게시판으로 거를 수 없다(전체 게시판으로 찾는다).
- 대표 이미지 화면 검사는 로고 검사(`UtilSiteSetting.logoFile` — 형식·2MB)를 그대로 쓴다. 한 변 4000px·GIF 첫 장면은 서버 판정.

## 미확정
- 공개 홈 하이라이트 표시와 홈의 토론 요약(홈 + 테마 작업 때). 의견 고정은 그때 홈 카드 차례에 쓴다.
- 글 목록 줄의 `highlighted`(community `PostRow.highlighted`)는 만들지 않았다 — 글 상세에만 있다. 필요하면 목록에 붙인다(노출 중 하이라이트 글 ID 모음 사본).
- 다시 계산은 요청 표를 거쳐 배치를 켠 서버가 돌리므로 응답 직후 화면이 바로 읽으면 아직 옛 순위일 수 있다(`recomputePending` 으로 표시).
- 첫 하이라이트를 동시에 같은 글로 두 번 만들면 잠글 행이 없어 유일 제약 위반(500)이 날 수 있다(드묾).
- SQL·마이그레이션은 실제 DB 에서 아직 돌려 보지 않았다(메인 세션이 적용).
- ~~(화면 세션) 공개 댓글에 숨긴 까닭이 없다~~ → 게시판 옵션 `staffHiddenNotice` + `CommentView.hiddenBy` 로 정리(2026-10-24, 위 「공개 사이트 토론」·「표」).
- 입장 바꾸기와 같은 때에 옛 입장을 읽은 댓글 쓰기가 커밋되면 그 댓글 하나는 옛 배지로 남을 수 있다(드묾 — 다시 바꾸면 따라온다).
- (화면 세션) 하이라이트 화면의 게시판 거르기용 가벼운 게시판 목록(`highlight.read` 로 읽는)이 서버에 없다. 공개 사이트 글 링크용 공개 주소도 admin-web 에 없다.
