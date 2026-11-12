# 공지 관리 계약 (관리자) — 화면 확정 2026-11-07

**의도**: 관리자 앱 콘텐츠 관리 묶음에 「공지 관리」를 두고, 공개 사이트 공지 게시판(slug `notice`)의 글을 관리자 앱에서 쓰고·고치고·지우고·고정한다. 새 저장소는 만들지 않는다 — 그 게시판의 보통 글이다.
작성자는 로그인한 운영자에게 이어진 **운영자 회원 계정**(`operator-member-account-contract.md`)이다. 공지 띠(`site-notice-bar-contract.md`)는 이 게시판 최근 글을 그대로 읽으므로 따로 손대지 않는다.

## 화면 (admin-web)
- `/notices` 목록 — 메뉴 콘텐츠 묶음 「게시글 관리」 바로 다음(`notices`, `pi pi-megaphone`, `post.read`). 요약 칸(공지 게시판·고정 n/한도 · 작성 계정 · 공지 띠) · 조회(제목 · 상태 전체/공개/숨김/삭제 · 말머리, 주소 쿼리 `?q=&state=&prefix=&page=`) · 표(고정 먼저) · ⋯ 수정·고정/해제·삭제·게시글 관리에서 보기(`/posts?post=`)·공개 사이트에서 보기.
- `/notices/new`·`/notices/{id}` 작성·수정 — 말머리(선택) · 제목(1~100자) · 본문 RichEditor `ADVANCED`(이미지) · 상단 고정. 지운 공지는 읽기 전용. 수정 화면에 [삭제] 단추는 없다(목록 ⋯ 에만).
- 게시판이 없거나 꺼져 있으면 목록 대신 안내 + [게시판 관리로].
- 코드: `pages/notices/*` · `composables/notice/api/useNoticeApi(.types).ts`(칸 이름 정본) · `components/notice/NoticeWriterWarning.vue` · `utils/util-notice.ts` · i18n `notice`.

## 정책 (사용자 확정 2026-11-07 — 목업 가정 그대로)
1. **작성자 = 내 운영자 회원 계정**. 연결이 없거나 멈춤(정책 6 — 운영자 사용 불가·회원 정지)이면 새 글·수정 모두 `409 NOTICE_WRITER_NOT_LINKED`(화면이 미리 막는다). 고쳐도 작성자는 바뀌지 않는다 — 다른 운영자가 쓴 공지도 고칠 수 있다.
2. **권한**: 보기 `post.read`, 쓰기·고치기·고정·해제·삭제 `post.manage`. 새 권한 없음.
3. **삭제 = 사유 없음 · 작성자 알림 없음 · 포인트 회수 없음**. 게시글 관리 삭제와 같은 「지운 글」 상태가 되어 복구·조치 기록은 게시글 관리에서 본다. 고정은 풀린다.
4. **고정**: 한도 = 게시판 정책 `pinnedMax`(게시글 관리와 같은 셈 — 지우지 않은 고정 글 수), 넘으면 `409 PIN_LIMIT`. 공개 글만 고정(`409 POST_STATE_INVALID`). 순서는 공개 목록과 같은 `pinnedAt` 내림차순(최근 고정이 위).
5. 공지 게시판 = slug `notice`, 소모임 게시판 아님(공지 띠와 같은 판정). 없으면 `404 NOTICE_BOARD_MISSING`, 꺼져 있으면 쓰기 `409 NOTICE_BOARD_INACTIVE`(목록·보기는 됨).
6. 글은 공개 사이트에서 쓴 글과 같은 규칙으로 저장한다(본문 HTML 정화·길이·말머리는 그 게시판의 켜진 말머리). 포인트는 주지 않는다.
7. **기록**: 감사에 운영자로 남긴다 — 행위 `NOTICE_CREATE`·`NOTICE_UPDATE`·`NOTICE_DELETE`·`NOTICE_PIN`·`NOTICE_UNPIN`(대상 `POST`). 삭제·고정·해제는 조치 기록(`post_moderation_events`)에도 이미 있는 종류(`DELETED`·`PINNED`·`UNPINNED`, 덧붙임 `via: NOTICE_ADMIN`)로 한 줄 — 새 종류를 만들지 않아 마이그레이션이 필요 없다.

## API `/api/admin/notices` (`ApiResponse`, ID 문자열, 시각 UTC ISO, 쓰기 CSRF, `no-store`)
- `GET /context` (`post.read`) → `{ board: {id, slug, name, nameEn, active, pinnedMax, pinnedCount, prefixes:[{id,name}], publicUrl|null} | null, writer: {state: LINKED|NOT_LINKED|PAUSED, userId|null, nickname|null, pausedReasons:[OPERATOR_UNAVAILABLE|MEMBER_SUSPENDED]}, noticeBar: {enabled, source: BOARD|CUSTOM, boardCount} }`
- `GET ?q=&state=&prefix=&page=&size=` (`post.read`) → `{ total, page(0부터), size, items: [NoticeItem] }` — 고정 글 먼저, 그다음 최신. `NoticeItem` = `AdminPostItem` 상태 칸 + `author.operatorName` · `pinnedAt` · `editedAt` · `publicUrl`.
- `GET /{id}` (`post.read`) → `{ notice: NoticeItem, content: {format: 'HTML', body} }` · 공지 게시판 글이 아니면 `404 POST_NOT_FOUND`.
- `POST` `{prefixId|null, title, body, pinned}` (`post.manage`) → 201 `{id}` · `PUT /{id}` 같은 몸 → 204. 400 `VALIDATION fields.title|body|prefixId` · 409 `NOTICE_WRITER_NOT_LINKED`·`PIN_LIMIT`·`NOTICE_BOARD_INACTIVE` · 지운 글 수정 `409 POST_STATE_INVALID`.
- `POST /{id}/pin` · `/unpin` (`post.manage`) → 204.
- `DELETE /{id}` (`post.manage`) → 204.
- `POST /images` (multipart `file`, `post.manage`) → `{id, url, width, height}` — 본문 이미지.

## 메뉴
- 서버 `AdminScreenRegistry` 콘텐츠 묶음에 `posts` 다음 `notices` 한 줄(admin-web `util-admin-menu.ts` 와 같은 키·주소·권한·아이콘). 관리자 메뉴를 이미 저장한 사이트는 메뉴 관리 › 「메뉴에 없는 화면」에서 넣는다.

## 미확정
- 예약 발행·다른 게시판에 함께 공지 걸기·알림 보내기는 없다(나중).
- 공지 쓰기에 금지어 검사·운영자 이미지 올리기 한도를 둘지(지금은 없음).

## 서버 구현 결과 (2026-11-07, 마이그레이션 없음)
- **코드**: admin `com.community.notice` — `NoticeAdminController` · `NoticeQueryService`(context·목록·상세) · `NoticeWriteService`(쓰기·고치기·고정·해제·삭제·이미지). `AdminPostRepositoryQDSL.searchPinnedFirst`·`editedAt`, `AdminPostQueryService.publicUrl` 재사용.
- **공통 코드를 api → shared 로 옮김**(같은 패키지 이름, api import 그대로): `ContentRules`·`RichText`·`ContentBody`·`GuestCredentials`·`PostImages`·`ImageUploadNotAllowedException`·`PostImageProcessor`·`PostImageProperties`. 새 shared `PostContentFields`(api `PostWriteService` 칸 검사를 그대로 뺀 것)·`PostImageUploads`(api `PostImageUploadService` 저장 몸통). api 쪽 속도·하루 한도·권한 검사는 api 에 그대로. admin 은 `irobit.post-image.*` 설정이 없어 기본값(10MB·2500만 화소·긴 변 2048).
- **칸 규칙 = 공개 글쓰기와 같음**(정화·제목 1~100자·본문 보이는 글자 10자~·이미지 30·영상 10·말머리 — 운영진 전용 말머리 가능). 서버 칸 `content`·`contentFormat` → `fields.body`, `adult` → `fields.prefixId` 로 바꿔 낸다. 금지어·작성 간격·하루 한도·반복 글·캡차·본인인증·로보티콘 검사는 하지 않는다.
- **검사 차례**: 게시판 404/꺼짐 409 → 작성 계정 409 → (수정) 글 404 → 지운 글 409 → 칸 400 → 이미지 400 / 이미지 금지 게시판 422 `IMAGE_UPLOAD_NOT_ALLOWED` → 고정 한도 409. 꺼진 게시판 409 는 쓰기·고치기·고정만(해제·삭제·이미지 올리기는 된다). 상세도 게시판이 없으면 `NOTICE_BOARD_MISSING` 이 먼저.
- **이미지**: `POST /images` → `files` PENDING(올린 회원 = 내 운영자 회원 계정), 글 저장 때 `PostImages.bind` 로 붙인다(본문 표식 `<img data-image-id>`, `src` 는 서버가 채움). 공개 쪽 1분·하루 한도는 없다. 남의 공지를 고칠 때 새 이미지는 고치는 운영자의 회원 계정 것이어야 한다(이미 붙은 이미지는 그대로).
- **기록**: 조치 기록 `reason` null, detail `via: NOTICE_ADMIN`(+ 고정이 풀렸으면 `unpinned`). 감사 `NOTICE_UPDATE` 는 바뀐 칸만 before/after + `bodyChanged`. 감사 분류 GENERAL.
- **화면 연결**: 목업 걷어 냄. 이미지 올리기는 admin `useApiClient` 에 진행률 올리기가 없어 끝에 한 번만 진행률(공동구매와 같은 방식). 게시글 관리 조치 기록에 `via: NOTICE_ADMIN` 이면 「공지 관리에서」.
- **검증**: shared·api·admin `compileJava`·`compileTestJava`, admin-web `typecheck` 통과. 새 jar(8930)+admin-web(3300)로 실제 호출 확인(2026-11-08 — 로컬 DB 에 루트 이메일 `admin@admin.local` 회원 「운영공지」를 만들어 최고관리자에 연결): 화면에서 새 공지(말머리·고정) 올리기 → 이미지 올리기 201 + 수정 PUT 204(본문 `src`·크기 채워짐) → 해제·재고정·빈 칸 400 `fields.title|body` → 목록 ⋯ 삭제. 공개 api(8910) 글 보기·공지 띠·이미지 200, 게시글 관리 조치 기록 「공지 관리에서」. 테스트 실행 안 함.

## 고정 한도 늘림 (사용자 2026-11-08)
- 게시판 고정 한도 범위 0~5 → 0~10, 사이트 기본 2 → 5, 공지 게시판은 10(shared V173 — 운영자가 바꾼 값은 그대로). 설치 프리셋도 다시 뽑음.

## 전체 공지 (2026-11-08 — 사용자 확정 시안, shared V174)
**의도**: 공지 게시판 고정글을 다른 게시판(일반 · 소모임) 글 목록 맨 위에도 건다. 고정할 때 범위를 고른다 — 기본은 지금처럼 공지 게시판에만.

### 화면
- admin-web `/notices/new`·`/notices/{id}`: 「상단 고정」 아래 **고정 범위** 라디오 — 「공지 게시판에만」(기본) · 「모든 게시판 맨 위에도」 + 칩 「전체 공지」(설명: 일반 · 소모임 게시판 모두 · 최대 3개(지금 n) · 새로 만든 게시판에도 자동). 한도가 찼고 이 글이 살아 있는 전체 공지가 아니면 둘째 칸 꺼짐 + 안내.
  - **뺄 게시판 고르기**: 켜진 일반 게시판 칩(누르면 빠짐 = 빨간 칩 + − 아이콘, 다시 누르면 넣음, 12개 넘으면 접어 「… N개 더」 — 접어도 뺀 칩은 보임) · 「뺄 게시판 N개」 · 소모임 게시판 줄(「소모임 게시판 전체 (N)」 칩 하나 또는 「하나씩 고르기」). 공지 게시판 자신은 목록에 없다.
  - **걸어 둘 기간**: 끝 없음(기본) · 7일 · 30일(저장하는 순간부터) · 날짜 고르기(그날 23:59:59 한국 시각까지, 오늘~365일). 수정 화면은 저장된 끝을 「날짜 고르기」로 보이고, 날짜를 그대로 두면 저장된 시각을 그대로 보낸다.
  - 고정을 끄면 범위 · 제외 · 기간 칸이 사라지고 보낼 때 NOTICE_ONLY(전체 공지에서 내려감). 코드: `components/notice/NoticeGlobalOptions.vue` · `UtilNotice.expiresAt`·`kst*`·`excludedCount`.
- admin-web `/notices` 목록: 요약 상자에 「전체 공지 n / 3」, 제목 아래 「전체 공지」 칩 · 뺀 게시판 수 · 「…까지」/「풀 때까지」/「기간 끝남」. 목록 ⋯ 「고정」은 공지 게시판에만(범위를 고르려면 수정 화면).
- user-web 게시판 글 목록 첫 쪽(고정 공지와 같은 조건 — 첫 쪽 · 말머리/검색어/상태 거르기 없음) 맨 위: 전체 공지 줄 → 그 게시판 고정글 → 일반 글. 줄 = 옅은 주 색상 바탕 + 「공지」 칩(`pi pi-megaphone`) + 제목(한 줄 말줄임) + 날짜, 누르면 `/posts/{id}`. 목록형(모바일 줄 · 데스크톱 표 위) · 카드형 · 갤러리형 · 핫딜 목록 · 핫딜 격자 · daum/naver 테마 목록 · 소모임 상세 글 탭 모두. 빈 게시판(첫 쪽)에도 빈 안내 위에 보인다. 공지 게시판 자신에는 없다. 코드: `components/board/BoardGlobalNoticeList.vue`(`BoardPinnedList` 의 `global`, 목록형 · 핫딜 목록형은 직접), 계약 칸 `BoardPostListProps.globalPinned`.

### 정책
1. 사이트 전체에서 **동시에 살아 있는** 전체 공지 최대 3개(넘으면 `409 NOTICE_GLOBAL_LIMIT`, 문구 ko·en) — 게시판 고정 한도(`pinnedMax`, `PIN_LIMIT`)와 따로 센다. 셈은 이 글 말고, 공지 게시판 행을 잠그고 한다. 이미 지난 기간 값으로 저장하면 세지 않는다.
2. **살아 있음** = 줄이 있고 · 글이 공지 게시판의 일반 글이며 · 보이는 글(지우지 않음 · 공개 · 예약 삭제 전) · 숨김 중 아님 · 비밀글 · 19금 아님 · 글의 고정 시각 = 줄의 사본(`pinned_at`) · 기간 안(끝 없음 포함). 공지 게시판이 없거나 꺼져 있으면 공개 쪽에 나오지 않는다.
3. 공지 관리 밖(게시글 관리 고정 해제 · 이동 · 삭제, 소모임 해산 등)에서 고정이 풀리거나 다시 고정되면 줄은 저절로 힘을 잃는다(고정 시각이 달라짐) — 다시 걸려면 공지 관리에서 저장. 공지 관리의 고정 해제 · 삭제 · 고정 끄기 · 범위 NOTICE_ONLY 는 줄을 지운다.
4. 기간이 지나면 다른 게시판에서만 내려간다(읽을 때 거른다, 배치 없음) — 공지 게시판 고정은 그대로.
5. 순서는 고정 시각 최근 것이 위. 뺀 게시판 · 「소모임 게시판 전체」(앞으로 만들 소모임 게시판 포함)에는 나오지 않는다. 형식이 맞지만 그사이 지운 게시판 ID 와 공지 게시판 자신은 조용히 뺀다.
6. 권한 그대로(`post.manage`). 감사: `NOTICE_CREATE` 에 `global`(scope · excludedBoardIds · excludeAllGroupBoards · expiresAt), `NOTICE_UPDATE` before/after 에 `global`(바뀌었을 때, 없으면 null), 고정 해제 · 삭제로 내려가면 `globalRemoved: true`.

### 저장 (shared V174 `notice_global_pins`)
`post_id` PK(외래 키 없음 — 글 표 파티션) · `pinned_at`(걸 때의 글 고정 시각 사본) · `excluded_board_ids bigint[]` · `exclude_group_boards` · `expires_at` null · `created_at/by` · `updated_at/by`(운영자 ID). 엔티티 shared `com.community.notice.entity.NoticeGlobalPinEntity`(`MAX = 3`), 질의 `NoticeGlobalPinQueries`(`live` · `liveCount`).

### API
- 관리자 `POST /api/admin/notices` · `PUT /{id}` 몸에 칸 추가: `scope: NOTICE_ONLY|ALL`(null = NOTICE_ONLY) · `excludedBoardIds: string[]` · `excludeAllGroupBoards: boolean` · `expiresAt: ISO|null`. `pinned false` 면 모두 무시. 400 `fields.scope`(모르는 값) · `fields.excludedBoardIds`(깨진 ID · 1000개 넘음) · `fields.expiresAt`(형식 · 지난 시각 — 저장된 값 그대로면 통과 · 366일 넘음). 409 `NOTICE_GLOBAL_LIMIT`. 검사 차례는 기존 뒤에 전체 공지 한도(칸 400 은 기존 칸 오류와 한 번에).
- `NoticeItem.global: { excludedBoardIds: string[], excludeAllGroupBoards, expiresAt|null, expired } | null` — 범위 ALL 인 고정 글만(줄이 힘을 잃었으면 null).
- `GET /context` 에 `global: { count, max: 3, boards: [{id, slug, name, nameEn}], groupBoards: [...] }` — 켜진 게시판만, 공지 게시판 자신 빼고, 정렬 순서대로.
- 공개 `GET /api/boards/{slug}/posts` 응답에 `globalPinned: [{id, title, createdAt}]`(고정 공지와 같은 조건일 때만, 최대 3, 아니면 빈 목록). `GET /api/groups/{id}/posts?page=` 도 같은 칸(쪽 번호 첫 쪽 · 거르기 없음일 때만, 커서 목록은 늘 빈 목록).
- 공개 쪽은 살아 있는 전체 공지를 api 서버 메모리 사본(`GlobalNoticeReader`, 15초 — 게시판 사본과 같은 주기)으로 읽고 기간은 쓸 때마다 다시 거른다. 관리자 앱에서 바꾸면 공개 쪽에는 최대 15초 늦게 닿는다.

### 미확정
- 전체 공지 줄을 말머리 · 검색 · 상태 거르기 · 2쪽 이후에도 보일지(지금은 고정 공지와 같이 첫 쪽 · 거르기 없음만).
- 최신글 · 인기글 · 홈 가젯 같은 섞인 목록에는 걸지 않는다(게시판 · 소모임 글 목록만).
- 「날짜 고르기」는 한국 시각 기준(관리자 앱의 다른 날짜 칸과 같다). 사이트 시간대 설정을 따를지는 나중.
- 화면 확인 · 실제 API 호출은 아직(메인 세션 몫). 테스트 없음.
