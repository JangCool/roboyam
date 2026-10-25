# 회원 활동·둘러보기(공개 사이트) 계약 (**화면 확정 · 최신·인기·검색·등급 서버 구현 + 화면 연결 2026-10-24** — 공개 프로필·제재·이의제기는 목업)

**의도**: community 에만 있던 「회원 활동·둘러보기」 화면(공개 회원 프로필·등급 안내·통합 검색·최신글 전체·인기글 전체·게시판 모음·제재 안내·이의제기)을 user-web 으로 옮긴다. UI 는 community 와 같게 두고, 서버에 있는 것(게시판 목록·글 목록·게시판 안 검색·메뉴·로그인 정보 `restriction`·포인트 요약)은 실제로, 없는 것은 community 계약 모양의 `// MOCK:`/`// REAL:` 짝 목업으로 먼저 확정한다.
**참고(읽기 전용)**: community `frontend/app/pages/{users/[id]/index,me/grade,search,latest,popular,boards/index,sanction/index,sanction/appeal}.vue`, `docs/api-{profile,grade,board(검색 2-4),home,popular,sanction}.md`.

## 화면 (user-web)
| 경로 | 내용 | API |
|---|---|---|
| `/users/{id}?tab=posts\|comments` | 비회원도. 이니셜·닉네임+등급 뱃지·한 줄 소개·가입일 · 수치(글·댓글·받은 추천) · 탭(작성글·댓글, 커서 더보기). 내 프로필이면 「프로필 편집」 + 보유 포인트·다음 등급까지 카드 | 목업 `GET /api/users/{id}/profile`·`/posts`·`/comments` · 실제 `GET /api/me/points` · 목업 `GET /api/me/grade` |
| `/me/grade` | 내 등급·다음 등급 진행도(로그인) · 자동 승급 멈춤 안내(정지 → 제재 안내 링크) · 등급 조건표(비회원도) · 계산 안내 | 실제 `GET /api/me/grade` · `GET /api/grade-rules` |
| `/search?q&scope&board&order&page` | 검색 막대(Enter·단추로만) · 범위(전체·제목·본문·작성자)·게시판·정렬 · 최근 검색어(이 기기) · 결과(일치 강조, 데스크톱 쪽 번호 최대 10쪽 / 모바일 더보기) · 결과 없음 제안 · 시간 초과·잘못된 게시판 | 아래 「검색」 |
| `/latest` | 켜진 모든 게시판의 최신글, 「더 보기」(커서), 투자 고지 | 실제 `GET /api/home/latest` |
| `/popular?period=REALTIME\|YESTERDAY\|WEEK&page` | 기간 탭(키보드 이동)·순위 번호·채운 줄 표시(어제·지난주)·이번 주 범위·쪽 번호 | 실제 `GET /api/home/popular` |
| `/boards` | 최근 방문(이 기기 5개, `/boards/{slug}` 가 기록) · 메뉴 그룹별 게시판(말머리 요약·투자 고지) · 메뉴 못 받으면 기본 메뉴 안내 | 실제 `GET /api/menu`·`GET /api/boards` |
| `/sanction?next=` | 로그인 필요. 갈래: 확인하지 않은 경고(누적 N/2 막대·「안내를 확인했어요」→「확인하고 계속」) · 기간 정지 · 영구 정지(로그아웃) · 제한 없음 · 아래 늘 내 제재 이력 | 목업 `GET /api/me/sanctions`·`POST /api/me/sanctions/{id}/acknowledge` |
| `/sanction/appeal?sanctionId=` | 접수 폼(10~500자·글자 수) → 접수 완료 / 검토 중 / 인용·기각(운영자 답변) / 무효·없는 제재 | 목업 `GET /api/me/sanctions`·`POST /api/me/appeals` |
- **들어가는 길**: 글 보기 머리·댓글·데스크톱 목록 표의 회원 작성자 이름 → `/users/{id}`(`PostAuthorLabel link` — 모바일 목록 줄은 줄 전체가 링크라 켜지 않음) · `/me` 바로가기 「내 프로필 보기」「등급 안내」 · 헤더 돋보기 → `/search`(기존) · 로그인 직후 정지·확인 안 한 경고 → `/sanction?next=`(기존 `UtilSanction.afterLoginPath`) · 전역 정지 배너 「자세히 보기」(기존).
- **메뉴**: 공개 메뉴 BUILTIN 레지스트리(서버 `MenuBuiltin`·admin `UtilMenu.BUILTINS`)에 이미 `BOARDS_HUB /boards`·`POPULAR /popular`·`LATEST /latest`·`SEARCH /search`·`MY_GRADE /me/grade` 가 있고 화면 주소를 거기에 맞췄다 — 레지스트리는 바꾸지 않았다. 단 메뉴 **시드**(V43)의 main 그룹에는 이 항목들이 없고, `MY_GRADE` 는 V49 가 숨겼다 → 운영자가 메뉴 관리에서 추가·켜면 된다(서버 마이그레이션은 하지 않았다 — 필요하면 결정).

## 실제 API 사용
- **검색**: 모든 검색이 실제 `GET /api/search` 다(2026-10-24 — 게시판 안 검색 우회 `viaBoard` 는 지웠다). 없는·꺼진 게시판은 `400 fields.board` → 「이 게시판에서는 검색할 수 없어요」.
- ~~**목업이 실제 글을 쓴다**~~: 프로필 작성글·최신·인기·검색이 모두 실제 API 로 바뀌어(2026-10-24) 게시판 글을 모으던 목업(`board-feed-mock.ts`)과 가짜 회원 명부(`member-mock.ts`)는 지웠다. 남은 목업(제재·이의제기·성인 확인)은 `composables/common/mock/mock-util.ts` 만 쓴다.

## 목업 API 후보 (서버에 없음 — 화면 확정 뒤 구현, 모양은 `.types.ts`)
### 게시판이 섞인 글 줄 `PostFeedRow = PostRow + board{slug,name,nameEn,color}` · 커서 쪽 `PostFeedPage{items, hasNext, nextCursor}` (`usePostApi.types.ts`)
### 최신·인기 (`composables/home/api/useHomeApi.ts`)
- `GET /api/home/latest?size(1~50, 20)&cursor` → `PostFeedPage` · 틀린 커서 `400 VALIDATION fields.cursor`. 비회원도.
- `GET /api/home/popular?period&page&size` → `PopularListResponse{pinned:[], items, page, totalPages, totalCount, hasNext, nextCursor:null, period, fills{글ID: YESTERDAY|LAST_WEEK}, from, to, weekStart}` · `400 VALIDATION fields.period|page|size`. `from`·`to` 는 이번 주 탭만(서비스 시간대 달력, `to` = 다음 주 시작).
### 통합 검색 `GET /api/search?q&scope(all|title|body|author)&board&order(latest|oldest)&size&page(1~10)|cursor` (`useSearchApi.ts`)
→ `SearchResponse{query, tokens, scope, board, items: SearchRow(PostFeedRow + snippet), page, totalPages(≤10)|null, hasNext, nextCursor}` · `400 VALIDATION fields.q|scope|board|order|page|cursor` · `503 SEARCH_TIMEOUT`. 단어는 공백으로 나눈 **AND**, 단어마다 2자 이상, 전체 2~50자, 5단어까지(화면 검사 `UtilSearchRules` = 서버 후보 `SearchQueryPolicy`).
### 공개 프로필 (`composables/profile/api/useUserProfileApi.ts`)
- `GET /api/users/{id}/profile` → `PublicProfile{id, nickname, grade(gradeKey), gradeLabel, role, bio|null, joinedAt, isMe, stats{posts, comments, likesReceived}}` · `404 PROFILE_NOT_FOUND`(없는·탈퇴·형식 틀림).
- `GET /api/users/{id}/posts?size&cursor` → `PostFeedPage`(최신이 위, 삭제·숨김·꺼진 게시판 제외).
- `GET /api/users/{id}/comments?size&cursor` → `{items: AuthorCommentRow{id, postId, postTitle, board{slug,name,nameEn}, bodyPreview(앞 100자), likeCount, reply, createdAt}, hasNext, nextCursor}` · `400 fields.cursor`.
### 등급 (`composables/grade/api/useGradeApi.ts`)
- `GET /api/grade-rules`(비회원도) → `{rules: [{code(gradeKey), label, minDays, minActivity}]}` 서열 순.
- `GET /api/me/grade`(로그인, 정지 중에도) → `{grade{code,label}, autoPromotion: ACTIVE|PAUSED_SANCTION|PAUSED_ADMIN, next{code,label,requirements[{kind: DAYS|ACTIVITY, current(상한 없음), required, met}]}|null}` — 기준 0 인 조건은 싣지 않는다. 관리자 회원 상세 `gradePoint`(`MemberGradePointAssembler`)와 같은 셈을 본인용으로 내면 된다.
### 제재 (`composables/sanction/api/useSanctionApi.ts`)
- `GET /api/me/sanctions` → `{status, warningCount(0~2), current|null, items: MySanction{id, no, kind: WARNING|SUSPEND_3|SUSPEND_7|SUSPEND_30|BAN, status: ACTIVE|ENDED|LIFTED|VOIDED, reason, warningNo, startsAt, endsAt|null, createdAt, acknowledged, appeal{id,no,status: PENDING|ACCEPTED|REJECTED, reason, answer, createdAt, answeredAt}|null}[]}`(최근 것이 위, 최대 50, 무효 포함).
- `POST /api/me/sanctions/{id}/acknowledge` → 204(멱등) · `404 SANCTION_NOT_FOUND`.
- `POST /api/me/appeals {sanctionId, reason}` → `201 {id, no, status:'PENDING'}` · `400 VALIDATION fields.reason`(10~500) · `404 SANCTION_NOT_FOUND` · `409 APPEAL_ALREADY`(기각 뒤에도) · `409 SANCTION_NOT_APPEALABLE`(무효).
- 모두 로그인만 필요(정지 중·인증 전에도), 비로그인 401. `restriction`(이미 있음)과 `pendingNotices` 는 이 데이터와 맞아야 한다.

## 목업 가정
1. ~~최신·인기 목업~~ → 서버 구현(아래 「서버 구현 결과」). 프로필 작성글 목업만 게시판마다 앞 2쪽을 모은다.
2. ~~검색 범위 목업~~ → 서버 구현.
3. 프로필: 닉네임·등급은 모은 글의 작성자 정보(본인은 로그인 정보), 글이 없으면 「회원 {ID 끝 4자리}」·새싹. 가입일·소개·댓글(0~23개, 실제 글에 붙인 가짜 댓글)은 ID 로 정한 가짜 값.
4. ~~등급 목업~~ → 서버 구현.
5. 제재: 스위치가 없으면 로그인 정보 `restriction` 을 따른다(영구 정지 / 정지 7일(끝 = `until`) / `pendingNotices` 만큼 경고 / 없음). 확인·접수는 목업 메모리에서만 바뀐다(새로고침하면 처음으로, 로그인 정보는 그대로).
6. 개발 스위치(`?mock=`): `profile-notfound|profile-view-fail|profile-posts-fail|profile-comments-fail|profile-comments-empty` · `sanction-warning|sanction-warning2|sanction-suspended|sanction-banned|sanction-history|sanction-fail|sanction-ack-fail|appeal-fail|appeal-already`. (최신·인기·검색·등급 스위치는 목업과 함께 지웠다)

## community 대비 뺀 것
- **예약 글(`/scheduled`)**: 로보얌에 예약 발행이 없어 만들지 않았다(BUILTIN `MY_SCHEDULED` 는 레지스트리에 남아 있다).
- 프로필: 프로필 이미지·아바타·닉네임 이미지·꾸미기 테두리, 차단·메모 ⋯ 시트·차단 띠, 쪽지 단추·쪽지함, 소모임·스크랩 탭, 인기글 수치(4칸 → 3칸), 신고(프로필 이미지·닉네임 이미지).
- 등급: 인기글 조건(`minPopular`·`POPULAR`), 닉네임 이미지 신청, 공식 뱃지 안내.
- 제재: 게시판 한정 제재(게시판 지킴이) 구획·`BOARD_SANCTION` 이의제기·알림에서 온 줄 강조, 소셜 로그인 직후 표시(`LOGIN_MARK_KEY`).
- 검색·목록: 하이라이트·소모임 글·글 유형·끌올·예약 삭제 배지, 이모티콘 요약, 차단 접힘(FoldedRow), 홈 가젯 몫. 공통: 모바일 ‹ 머리(`InnerHeader`) — 로보얌 셸에 없어 제목을 그대로 보인다(검색 막대는 헤더 아래에 붙는다).

## 미확정
1. 메뉴 시드에 최신·인기·검색·게시판 모음 항목을 넣을지, `MY_GRADE` 를 다시 켤지(V49 가 숨김) — 서버 마이그레이션 여부.
2. 공개 프로필의 한 줄 소개·가입일 공개 범위(`member-mypage-contract.md` 미확정 3과 같은 질문), 정지 회원 프로필 표시.
3. 제재 종류: 관리자 회원 상세 자리표시(`MemberSanction.kind = WARNING|SUSPENSION|BAN`, `scope SITE|BOARD`)와 community 단계(`SUSPEND_3/7/30`) 중 무엇으로 갈지 — 화면은 둘 다 그린다(모르는 `SUSPEND*` 는 「정지」). 게시판 단위 제재를 둘지.
4. 인기글 점수·기간·채우기·개수(관리자 인기글 설정이 없다) — 서버 상수로 둘지 설정으로 둘지.
5. 통합 검색에서 게시판별 「검색 포함」 끄기(community `searchable`) 필요 여부, 검색 시간 제한 값.
6. 이의제기 처리(관리자 화면)·답변 알림 이벤트(`APPEAL_ANSWERED` 등) — 회원 알림 카탈로그와 겹치는 개념이라 그 계약과 맞춘다.

## 결정 (2026-10-24, 사용자 "추천대로")
- **공개 메뉴 노출**: 최신·인기·게시판 모음·검색·내 등급 화면은 서버 구현 전(목업 데이터)이라 기본 메뉴에 넣지 않는다 — 서버가 붙은 뒤 운영자가 메뉴 관리에서 켠다(마이그레이션으로 기본 메뉴에 넣지 않는다).
- **제재 종류**: community 단계(경고 → 3일 · 7일 · 30일 정지 → 영구 정지)를 정본으로 한다. 관리자 회원 상세의 자리표시(`SUSPENSION`·`scope`)는 제재 기능을 만들 때 이 단계로 맞춘다.

## 결정 (2026-10-24 2차, 사용자 "추천대로" — community 정책을 로보얌에 맞춤)
- **공개 프로필 공개 범위(미확정 2)**: 한 줄 소개·가입일은 공개 프로필에 보인다(비회원도). 정지된 회원의 프로필도 보인다. 탈퇴했거나 없는 회원은 `404 PROFILE_NOT_FOUND`(「탈퇴했거나 없는 회원이에요」). 이전 닉네임은 어떤 공개 응답에도 없다.
- **인기글(미확정 4)**: 처음에는 서버 상수로 둔다(관리자 인기글 설정 화면은 필요해질 때). 규칙은 community `api-popular.md` 를 따른다 — 기간 탭 실시간·어제·이번 주, 한 글쓴이는 한 탭에 최대 2개, 탭마다 상위 50개 보관, 실시간이 모자라면 어제 글로 채운다(출처 `fill`). 19금 글은 모음에 싣지 않는다.
- **검색(미확정 5)**: 게시판별 「통합 검색에 포함」 끄기는 이번에 두지 않는다(필요해지면 게시판 설정 칸으로 더한다). 검색 한 번의 시간 제한은 3초 서버 상수, 넘으면 `503 SEARCH_TIMEOUT`(화면은 「검색어를 더 구체적으로」).
- **이의제기·제재 알림(미확정 6)**: 이의제기 처리(관리자 화면)는 제재 기능을 만들 때 함께 만든다. 그때 회원 이벤트 `SANCTION_ISSUED`·`SANCTION_LIFTED`·`APPEAL_ANSWERED` 를 회원 이벤트 카탈로그(`MemberEvent`)에 더한다 — 모두 끌 수 없는 계정 알림(묶음 `system`), 이의제기 답변은 알림과 이메일로 간다.

## 공개 프로필 서버 구현·연결 (2026-10-24, member-mypage 세션)
- `GET /api/users/{id}/profile|posts|comments` 를 api 서버에 만들고(비회원도 — 보안 설정 `permitAll` 한 줄) `useUserProfileApi` 를 실제 호출로 바꿨다(`user-profile-mock.ts` 삭제, 위 목업 가정 3·스위치 `profile-*` 는 없어짐. 쓰는 곳이 없어진 `board-feed-mock.ts`·`member-mock.ts` 도 뒤에 지웠다).
- 수치는 바로 센다(지연 없음): 글 = 켜진 게시판의 지우지·숨기지 않은 글, 댓글 = 지우지·숨기지 않은 댓글 중 볼 수 있는 글의 것, 받은 추천 = 그 글·댓글의 추천 합(19금 글도 센다). `gradeLabel` 은 등급 이름(영어 이름이 없어 요청 언어와 무관).
- 작성글 줄 = `PostFeedRowView`(`PostRowView` 칸 + `board`), 쪽 = `PostFeedPageView` — 최신·인기·검색과 같은 이름·위치(`com.community.post.dto.response`). `PostBoardRefView` 에 `color` 를 더했다(글 상세 `board` 에도 나간다). 숨긴 글은 목록에서 아예 빠진다(게시판 목록처럼 자리를 남기지 않음). `size` 1~50 밖은 `400 fields.size`, 틀린 커서 `400 fields.cursor` — 회원 404 보다 먼저.
- ~~`blockedByMe`·`memo` 는 아직 내려가지 않는다~~ → 합친 뒤 붙였다(2026-10-24): 프로필 `blockedByMe`(회원 차단만)·`memo`, 작성글 줄 `blocked`·작성자 `memo`, 작성 댓글 줄 `blocked`(새 칸, 단어는 댓글 본문) — 소통 세션의 `ViewerBlocks` 로 게시판 목록·댓글과 같은 판정. 비로그인·본인은 비어 있다.

## 서버 구현 결과 (2026-10-24 — 최신·인기·통합 검색·등급. 공개 프로필·제재·이의제기는 아직 목업)
**API** (`server/api` — `home`·`search`·`grade` 패키지, 모양은 위 「목업 API 후보」 그대로. 비회원도: latest·popular·search·grade-rules — `SecurityConfig` GET permitAll)
- `GET /api/home/latest?size&cursor` → `PostFeedPageView`(`PostFeedRowView` = `PostRowView` + `PostBoardRefView board`). 켜진 게시판(「가젯 노출 금지」에 `LATEST` 를 고른 게시판 제외 — `board-contract.md`)의 보이는 글, 최신순, 19금은 표시와 함께 포함. 커서 = 마지막 글 ID. `400 VALIDATION fields.size|cursor`.
- `GET /api/home/popular?period&page&size` → `PopularListResponse`. 스냅샷 순서대로 읽을 때 다시 거른다(삭제·숨김·19금·꺼진 게시판·「가젯 노출 금지」에 `POPULAR` 를 고른 게시판·운영진 전용 게시판). `from`·`to` 는 WEEK 만(그 밖 null), `weekStart` 는 늘 `MONDAY`. 순위가 없으면 `page 1·totalPages 1`, 범위를 넘는 쪽은 빈 `items`. `400 fields.period|page|size`.
- `GET /api/search` → `SearchResponse`(`SearchRowView` = 피드 줄 + `snippet`). 쪽 모드면 `page`·`totalPages`(최대 10, 결과 없으면 0), 커서 모드면 둘 다 null. `snippet` = 일치 자리 앞 40자부터 120자, 잘리면 「…」. 없는·꺼진 게시판 `400 fields.board`, `page`+`cursor` 동시 `fields.page`, 3초 예산(문장마다 남은 시간을 `statement_timeout`) 넘으면 `503 SEARCH_TIMEOUT`. 19금 글 포함, `author` 범위는 비회원 이름도 찾는다. 단어 규칙 `SearchQueryPolicy` = 화면 `UtilSearchRules`.
- `GET /api/grade-rules`(`no-cache`) · `GET /api/me/grade`(`no-store`, 비로그인·탈퇴 401). 셈은 shared `GradeProgress` 하나 — 관리자 회원 상세(`MemberGradePointAssembler`)도 이것을 쓴다. `label` 은 등급 이름 그대로(등급 표에 영어 이름 칸이 없다).
- `PostBoardRefView` 에 `color` 칸(마지막)을 더했다 — 마이페이지 세션과 같은 줄(세션 정리 결정). JSON 에 `id` 도 있다(화면 타입에는 없음, 무해).

**인기글 순위 = DB 배치 `popular-rank`**(저장 프로시저 + pg_cron, 분류 MEMBER, 기본 `*/5 * * * *` — 가정, 배치 화면에서 바꿀 수 있다). `R__batch_24_popular_rank.sql`: 기간 함수 `popular_period_range`(Java 응답의 `from`·`to` 도 이것을 부른다) · 후보 함수 · 프로시저 `batch_popular_rank`(기간마다 DELETE+INSERT 후 COMMIT — 실패한 기간은 예전 스냅샷 유지, 처리 건수 0 이면 SKIP). 표 `post_rank_snapshots(period, rank, post_id, score, fill, computed_at)`.
- → popular-highlight-contract 로 설정화(2026-10-24 — 아래 상수는 설정 표 `popular_settings` 의 기본값이 됐다. 관리자 「인기글 설정」·「인기글 관리(빼기)」, `weekStart` 는 설정 값).
- 서버 상수(결정 — 설정 화면 없음): 점수 추천×3 + 댓글×2 + 조회×0.1, 실시간만 `÷ (경과 시간 + 2)^1.5`, 최소 추천 3, 점수 0 제외, 한 글쓴이 한 탭 2개(채운 글 포함), 탭마다 50개, 실시간·이번 주가 5개 미만이면 어제(`YESTERDAY`)·지난주(`LAST_WEEK`) 글로 채움, 어제는 채우지 않음, 같은 점수면 ID 큰 글 먼저, 한국 달력·주 시작 월요일. 제외: 19금·삭제·숨김·꺼진 게시판·**게시판 설정 「가젯 노출 금지」에 인기글(`POPULAR`)을 고른 게시판(`boards.gadget_exclusions`, shared V69 — 핫딜·직접홍보 DEAL·PROMO 는 이 값으로 시작, 사용자 결정 2026-10-24)**·운영진 전용 게시판(유효 글쓰기 자격 `STAFF`).
- 비회원 글의 「글쓴이」는 작성 IP(`author_ip`, 보유 기간이 지나 지워졌으면 이름)로 가른다.
- pg_cron 이 없으면 순위가 계산되지 않아 인기글이 늘 비어 있다(`grade-promotion` 과 같음 — 관리자 서버 기동 WARN·배치 목록 「사용 불가」). 실행 로그가 하루 288줄 쌓인다(`batch-log-cleanup` 90일 보관).

**마이그레이션** shared `V68__popular_rank_and_search.sql`: `batch_db_jobs.cadence` 에 `MINUTES`(간격 분 = `kst_minute` 1~59) · `post_rank_snapshots` · pg_trgm GIN 색인 4개(`lower(title)`·`lower(coalesce(content_text, content))`·비회원 `lower(guest_name)`·`profiles lower(nickname)` — 파티션 부모라 CONCURRENTLY 불가, 만드는 동안 글쓰기가 잠긴다). `V69` 는 쓰지 않았다. `R__batch_00_helpers.sql` 에 `batch_define_every_minutes` + `batch_default_cron` 의 `MINUTES` 분기.

**미확정(서버)**
1. 인기 주기 5분·최소 추천 3 은 가정(소규모 사이트에서는 인기글이 자주 빌 수 있다 — 채우기도 같은 최소 추천을 지킨다).
2. ~~핫딜·직접홍보 게시판 제외 여부~~ → 게시판 설정 「가젯 노출 금지」로 정한다(DEAL·PROMO 는 `{POPULAR}` 로 시작, 사용자 결정 2026-10-24, 배치·읽기 둘 다).
3. ~~차단한 회원의 글 제외는 합칠 때 붙인다~~ → 빼지 않고 표시만 한다(2026-10-24): 최신·인기·검색 줄에 `blocked`(USER·WORD)·작성자 `memo` 를 `ViewerBlocks` 로 싣고(게시판 목록과 같음, 개수·쪽 수 그대로), 접는 것은 화면 몫. 인기글 스냅샷은 회원 공통 그대로, 표시는 줄을 만들 때 붙인다.
4. SQL·마이그레이션·검색 시간 제한(57014 → 503)은 실제 DB 에서 아직 돌려 보지 않았다.
