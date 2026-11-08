# 운영 게시판(버그 신고 · 기능 요청) · 비밀글 · 처리 상태 · 푸터 고객 지원 계약 (2026-11-08 — 사용자 지시로 목업 확인 없이 바로 구현)

운영자가 사이트 오류 신고와 기능 제안을 받는 공개 게시판 두 개를 두고, 사이트 푸터 「고객 지원」에서 들어간다.
이를 위해 **모든 일반 게시판에서 켤 수 있는 게시판 설정** 두 가지(비밀글 · 처리 상태)를 더했다. 「운영 해명」 게시판은 공지 게시판이 있어 만들지 않는다(사용자 결정).

## 게시판 (로컬 DB 데이터 — 관리자 API·화면으로 만들었다)
| | 버그 신고 `bugs` | 기능 요청 `requests` |
|---|---|---|
| 글·댓글 | 회원(새싹 이상) — 비회원은 읽기만 | 같음 |
| 말머리(고유) | 화면 오류 · 기능 오류 · 모바일 · 결제·주문 · 기타 | 새 기능 · 개선 · 디자인 · 모바일 · 기타 |
| 공통 말머리 | 「공지」만 켬 | 같음 |
| 비밀글 `secretMode` | `OPTIONAL`(글쓴이가 고름) | `OFF` |
| 처리 상태 `statusEnabled` | 켬 | 켬 |
| 글 양식 | 화면 주소 / 재현 순서 1·2·3 / 기대한 결과 / 실제 결과 / 기기·브라우저 / 발생 시각 | 제안 내용 / 필요한 이유 / 생각한 방식 / 참고 |
| 그 밖 | 포인트 적립 끔 · 가젯(인기·최신) 노출 금지 · 검색엔진 색인 끔 · 사이트 검색 켬 · 투표·토론 끔 · 광고 숨김 | 같음 |
- 메인 메뉴(`main`)에는 넣지 않는다(만들 때 자동으로 들어간 「커뮤니티」 폴더 항목은 뺐다). 들어가는 길은 푸터뿐이다.
- 말머리 필수는 없다(기존 기능 없음 — 필요해지면 따로).

## 푸터 「고객 지원」
- 링크의 원천은 공개 메뉴 그룹 **`footer`**(placement `CUSTOM`, 이름 「푸터 고객 지원」) — 관리자 메뉴 관리에서 바꾼다. 게시판 slug 를 코드에 넣지 않는다.
- user-web `AppFooter` 가 `GET /api/menu?set=footer`(`useFooterMenu`, 메인 메뉴 상태와 따로)를 그린다. 폴더는 평평하게 펴고, 못 받거나 비면 묶음을 숨긴다. PC 왼쪽 단 약관 아래 · 모바일 약관 줄 다음 한 줄.

## 비밀글 (shared V166)
- 게시판 `secretMode` `OFF`(기본) · `OPTIONAL` · `ALWAYS` — 게시판마다의 값(기본 설정에서 물려받지 않음), **일반(GENERAL) 게시판만**.
- 글쓰기: ALWAYS → true · OPTIONAL → 요청 `secret`(없으면 false) · OFF → false · **비회원 글은 늘 false**(화면도 비회원에게 체크를 보이지 않는다).
  글 고치기: OPTIONAL 만 글쓴이가 바꾼다 · ALWAYS 는 true · OFF 는 지금 값 그대로. 게시판 설정을 나중에 바꿔도, 관리자가 글을 옮겨도 이미 쓴 글의 값은 그대로.
- 읽는 사람: 글쓴이 · 그 게시판 매니저·스텝 · 운영자 회원 계정(`viewer.staff` 판정과 같다) · 관리자 앱 운영자. 그 밖은 **403 `SECRET_POST`**(+ `boardSlug`) — 상세·고치기·댓글 전부·추천·투표·찬반 토론·의견 고정 요청·신고.
- 같은 게시판 목록: 줄과 제목은 남기고 자물쇠, 읽을 수 없는 사람에게는 썸네일·`hasImage`·`hasPoll`·`deal` 을 비운다.
- 섞인 목록에서 빠진다: `PostRepositoryQDSL.feedVisible`(최신글·홈 가젯·하이라이트/인기 보이기·팁 매거진·공지 띠) + 통합 검색 SQL + 인기글 배치 후보(`R__batch_24`) + 관리자 하이라이트 지정 후보. 프로필 작성글·댓글·수치는 본인이 볼 때만 넣는다. 이전/다음 글은 읽을 수 없는 비밀글을 건너뛴다.
- 한계: 본문 이미지 파일 주소는 주소를 아는 사람이면 열 수 있다(추측하기 어려운 값).

## 처리 상태 (shared V166)
- 게시판 `statusEnabled`(기본 꺼짐, 일반 게시판만). 값: `RECEIVED` 접수 · `CHECKING` 확인 중 · `IN_PROGRESS` 진행 중 · `DONE` 완료 · `ON_HOLD` 보류 · `REJECTED` 반려 · `DUPLICATE` 중복.
- 켜진 게시판의 새 글은 `RECEIVED`. 값 없는 글(켜기 전 글)은 켜진 게시판에서 `RECEIVED` 로 보이고 꺼진 게시판에서는 null. 목록 `?status=RECEIVED` 는 값 없는 글도 포함, 상태로 거르면 고정 공지를 위에 따로 두지 않는다.
- 바꾸는 사람: 공개 사이트에서 그 게시판 매니저·스텝·운영자 회원 계정(글 보기 Select) · 관리자 앱 `post.manage`(글 서랍). 같은 값이면 아무것도 하지 않는다.
- 글쓴이에게 회원 알림 `POST_STATUS_CHANGED`(묶음 `system`, 끌 수 있음, 「{게시판} 글이 「{상태}」 상태가 됐어요」, 누르면 글) — 바꾼 사람이 본인이면 보내지 않는다. 관리자 쪽 변경은 감사 `POST_PROCESS_STATUS`(from·to), 조치 기록에는 남기지 않는다.

## API
- 공개 게시판 `GET /api/boards`·`/api/boards/{slug}`: `secretMode`, `statusEnabled`. 글 목록 `GET /api/boards/{slug}/posts?status=` 줄 `secret`, `processStatus`. 글 상세 `secret`, `processStatus`, `processStatusAt`. 섞인 목록 줄(`PostFeedRowView`)도 두 칸.
- 글쓰기·고치기 요청 `secret?: boolean|null`.
- `PUT /api/posts/{id}/process-status { status }` → `{ processStatus, processStatusAt }` — 401 · 403 `BOARD_STAFF_FORBIDDEN` · 400 `fields.status` · 409 `POST_STATUS_DISABLED` · 404 `POST_NOT_FOUND` · 403 `POST_HIDDEN`.
- 관리자 `GET/PUT /api/admin/boards/{id}`: `secretMode`, `statusEnabled`(PUT null = 그대로, `board.settings`, 감사 `BOARD_UPDATE`, `diffCount` 에 세지 않음, 일반 게시판이 아니면 400 `board.ops.generalOnly`). 관리자 글 목록·상세 `secret`, `processStatus`, `processStatusAt`, 목록 쿼리 `processStatus`.
- `PUT /api/admin/posts/{id}/process-status` — 위와 같은 모양, `post.manage`, 지운 글 409 `POST_STATE_INVALID`.

## DB
- shared `V166__post_secret_and_status.sql`: `boards.secret_mode`·`status_enabled`, `posts.secret`·`process_status`·`process_status_at`·`process_status_by`, 부분 색인 `ix_posts_board_process_status`, `ck_member_notifications_type` 에 `POST_STATUS_CHANGED` 더함(⚠ 다른 세션이 이 CHECK 를 다시 만들면 합집합 확인). `R__batch_24` 후보에 `NOT p.secret`.

## 운영 서버에서 다시 만들기 (1차 당시 손 절차 — 2차부터 V168·설치 프리셋이 대신한다)
1. 게시판 관리 › 만들기: 「버그 신고」 `bugs`, 「기능 요청」 `requests`(일반·목록형).
2. 각 게시판 설정: 위 표의 말머리·공통 말머리(공지만)·글쓰기·댓글 자격(새싹)·비밀글·처리 상태·포인트 적립 끔·가젯 노출 금지(인기·최신)·검색엔진 색인 끔·광고 숨김·안내문·글 양식.
3. 메뉴 관리 › 메뉴 그룹 만들기: key `footer`, 위치 「사용자 정의」, 항목 = 두 게시판. 메인 메뉴 「커뮤니티」 폴더에 자동으로 들어간 두 항목은 뺀다.

## 2차 (2026-11-08 — 사용자 지시 「미확정 다 처리해. 마이그레이션으로 넣고」, shared V168)
- **처리 상태 메모·이력**: 바꾸기 요청 `{ status, note? }`(앞뒤 공백 정리, 빈 글자 null, 1~200자, 줄 바꿈·제어 문자 불가 → 400 `fields.note`), 응답 `{ processStatus, processStatusAt, processStatusNote }`. 같은 값이면 메모가 달라도 아무것도 하지 않는다.
  표 `post_process_status_history`(바뀔 때마다 한 줄, 처음 접수는 줄 없음). 글 상세 `processStatusNote`(마지막 메모)·`viewer.canChangeStatus`.
  공개 `GET /api/posts/{id}/process-status/history` → `{ items:[{ fromStatus, toStatus, note, changedAt, actor: OPERATOR|BOARD_STAFF|GROUP_STAFF }] }`(최신 50, 이름 없음, 글을 읽을 수 있는 사람만), 관리자 같은 주소 + `actorName`(`post.read`).
  알림 본문에 「· 메모: …」, 감사 `POST_PROCESS_STATUS` 에 note. 글 원문 파기(R__batch_18) 때 메모만 비우고, 게시판 삭제 때 이력 줄을 지운다.
  화면: 글 보기 [상태 바꾸기] 대화창(상태 + 메모) · 「운영진 메모」 한 줄 · 「처리 이력」 접기, 관리자 글 서랍 폼 + 이력 목록.
- **비회원 비밀글**: OPTIONAL 게시판에서 비회원도 고르고, ALWAYS 게시판의 비회원 글은 비밀. 볼 수 없으면 403 `SECRET_POST` extras `guestPost`(·소모임 글이면 `groupId`).
  `POST /api/posts/{id}/secret-unlock { password }` → 204(비회원 비밀글이 아니면 400 `SECRET_UNLOCK_NOT_GUEST`, 틀리면 403 `PASSWORD_MISMATCH`, `verify-password` 와 시도 횟수를 함께 세어 429 `PASSWORD_ATTEMPTS_EXCEEDED`).
  열람 권한은 서버 세션(Spring Session JDBC)에 글 id → 1시간, 세션당 20개. 잠금 화면에 비밀번호 칸.
- **소모임 게시판 허용**: 비밀글·처리 상태를 GROUP 게시판에서도 켤 수 있다. 소모임 비밀글을 읽는 사람 = 글쓴이 · 소모임장 · 글 관리(`UNPUBLISH`) 매니저 · 운영자 회원 계정(비공개 소모임의 비공개 글은 멤버 전용 판정이 먼저) · 관리자 앱.
  소모임 글 목록 `GET /api/groups/{id}/posts?status=`(줄 가림 같음), `GET /api/groups/{id}/board` 에 `secretMode`·`statusEnabled`. 비밀글은 공개 전환·공개 요청·승인·다시 공개 불가 400 `GROUP_POST_SECRET`. 상태 바꾸기 = 소모임장·글 관리 매니저·운영자 회원 계정.
- **세션 정리 결정 — 핫딜·직접홍보(DEAL·PROMO)는 계속 막는다**: 공개 홍보가 목적이라 비밀글이 맞지 않고, 진행 상태는 딜 마감 상태가 이미 있다(400 `board.ops.generalOnly` — 「일반·소모임 게시판에서만」).
- **시드**: shared `V168` 이 **이미 설치된 DB**(설치 COMPLETED + `board_preset_applied_at`)에만, 같은 주소·이름의 게시판이나 `footer` 그룹이 없을 때 넣는다(로컬 DB 는 이미 있어 그대로). 새 설치는 설치 프리셋(`OpsBoardSeeder`, 값의 정본 `OpsBoardPreset`)이 만든다 — 위 「운영 서버에서 다시 만들기」 손 절차는 더 필요 없다. 쓰기·댓글 등급은 서열이 가장 낮은 등급.

## 미확정
- 관리자 글 상세에 `processStatusNote` 칸은 없다(이력 API 로 대신).
