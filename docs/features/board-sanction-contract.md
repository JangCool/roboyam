# 게시판 제재 계약 (**정책·화면 확정 2026-11-04 · 서버 구현 끝 · API 연결 끝(미커밋, 브라우저 확인 전)**)

**의도**: 게시판 매니저·스텝이 맡은 게시판에서만 회원에게 경고·쓰기 제한(1·3·7일)을 하루 한도 안에서 준다. 쓰기 제한은 그 게시판의 새 글·댓글만 막고, 사이트 제재(`member-sanction-contract.md`)와 단계·횟수를 따로 센다. 같은 회원이 같은 게시판에서 7일에 3건 이상 받으면 운영자 검토 건이 열리고 운영자가 닫는다.
**근거**: `docs/domain/operator-permissions.md` 「게시판 매니저 · 스텝」 · community `docs/api-board-keeper.md`(지킴이) 4·5·7·8·9절 · `api-sanction.md` 8절.

## 결정 (2026-11-04, 사용자 — 제안 8건 중 2번만 바꿈)
1. **이번 범위는 제재만**. 스텝의 글 숨기기·삭제·댓글 삭제는 다음 작업이다. 다만 처리 기록 표는 제재·글·댓글을 함께 담을 수 있게 둔다(`board_staff_actions.kind`).
2. **단계는 자유 선택**: 경고 · 쓰기 제한 1일 · 3일 · 7일 중 처리자가 고른다. 화면은 **권장 단계**(그 게시판의 유효한 제재로 계산: 경고 → 1일 → 3일 → 7일, 그 뒤 7일)를 설명·배지로 보여 줄 뿐 막지 않는다. 제재는 시간이 지나도 사라지지 않는다(권장 계산에 계속 센다).
3. **반복 검토**: 같은 회원 · 같은 게시판 · 최근 7일(롤링) · `VOIDED` 아닌 제재 3건 이상(처리자가 달라도 합산)이면 열린다. (회원, 게시판)당 열린 건 하나. 마지막으로 닫힌 검토의 `closedAt` 이전 제재는 다시 세지 않는다. 운영자가 셋 중 하나로 닫는다 — `KEPT` 그대로 둠 · `SITE_SANCTIONED` 사이트 제재 부여(기존 제재 대화창, 부여되면 그 제재 번호와 함께 닫힘) · `VOIDED` 게시판 제재 무효(고른 게시판 제재를 무효로 — 스텝 남용 바로잡기). 메모 0~200자 선택. 운영자 알림 `BOARD_SANCTION_REPEAT_REVIEW` 를 켠다.
4. **이의제기는 사이트 제재 이의제기에 합친다**: 같은 표(`sanction_appeals` 에 게시판 제재 칸)·같은 `/appeals` 화면·같은 규칙(제재당 1번, 시작 후 30일, 사유 10~500자, 운영자 인용·기각). 인용 → 게시판 제재 `VOIDED`, 진행 중 쓰기 제한 즉시 풀림, 반복 검토 수에서 빠짐. 매니저·스텝은 이의제기를 보지 않는다.
5. **쓰기 제한 범위**: 그 게시판의 새 글·댓글·답글·임시저장만 막는다. 읽기·추천·수정·삭제·쪽지·다른 게시판은 그대로. 이 게시판에서 진행 중인 쓰기 제한이 있으면 쓰기 제한은 더 못 주고 경고만 된다. 사이트 정지 중인 회원에게도 줄 수 있다(따로 센다).
6. **알림·메일**: 받은 회원에게 사유와 함께 알림(끌 수 없음, 누르면 `/sanction`), 메일은 보내지 않는다. 이의제기 답변 알림은 사이트 제재와 같다(메일은 사이트 제재 답변만).
7. **처리자 비공개**: 처리자 신원은 공개 응답(받은 회원 포함)·알림·오류 문구 어디에도 없다. 운영자(관리자 화면)와 **그 게시판 매니저**(공개 사이트 게시판 관리 화면)만 본다. 매니저는 자기 게시판의 제재를 [해제]할 수 있다(`board.staff-action.restore`). 스텝은 자기가 준 것만 본다(처리자 칸 없음).
8. **화면 위치**: 아래 「화면」. 개인별 한도는 이번에 넣지 않는다(게시판별 → 기본값).
9. (화면 확정 2026-11-04) 무효로 열린 검토의 7일 건수가 3 아래로 떨어져도 **자동으로 닫지 않는다** — 운영자가 닫고, 화면은 지금 건수를 보인다.
10. 대기 중인 이의제기가 있는 제재를 무효로 하면 그 이의제기를 **같은 트랜잭션에서 「인용」으로 닫고** 답변 「운영자가 제재를 무효로 했어요」(답변 알림은 이의 인용과 같다).
11. 범위 「진행 중」 = **진행 중 쓰기 제한만**(경고 제외 — 두 앱 같게). 해제·무효 사유 **1~200자**(매니저·운영자 같게), 검토 메모 0~200자.

## 규칙
- **줄 수 있는 사람**: 그 게시판의 매니저·스텝이고 역할 권한 `board.sanction.issue` 가 있고, 계정 정상·이메일 인증. 처리할 때마다 다시 판정한다.
- **받을 수 없는 대상**: 본인 · 같은 게시판의 매니저·스텝 · 비회원 글 · 탈퇴(진행 중 포함) 회원 · 소모임 글. 문구는 「제재할 수 없는 회원이에요」 하나로 통일한다(누가 스텝인지 새지 않게).
- **사유**: 코드 필수 — `SPAM` 스팸·광고 · `ABUSE` 비방·인신공격 · `INVESTMENT` 투자 권유 · `OFF_TOPIC` 게시판 주제와 맞지 않음 · `OTHER` 기타. 덧붙임 글 0~200자(`OTHER` 이면 필수 5자 이상). 계기 글·댓글(선택)을 잇는다.
- **하루 한도**: 처리자 1명·게시판 1개당 제재(경고+쓰기 제한) 합산, 값 = 게시판 `staffSanctionDailyLimit` ?? 기본값. 한국 시간 0시 기준, 해제·무효로 돌려받지 않는다. 넘으면 `409 BOARD_STAFF_DAILY_LIMIT`(`limit`·`used`·`resetsAt`). 한도 차감과 제재는 한 트랜잭션(원자적 증가).
- **상태**: `ACTIVE`(경고는 늘 ACTIVE, 쓰기 제한은 끝 전) · `ENDED`(기간 끝) · `LIFTED`(매니저·운영자 해제 — 진행 중 쓰기 제한만, 권장 계산엔 남음) · `VOIDED`(이의 인용·운영자 무효 — 권장·반복 검토 수에서 빠짐). 진행 중 판정은 읽을 때 `ends_at > now()`, DB 크론이 상태만 정리.
- **쓰기 막힘 오류**: `403 BOARD_WRITE_RESTRICTED` + `until`(문구 「이 게시판에서는 {날짜}까지 글·댓글을 쓸 수 없어요.」).
- **권한(관리자)**: 조회 `sanction.read` · 해제·무효·반복 검토 닫기 `sanction.manage` · 이의제기 `appeal.manage`(기존).

## 화면
### user-web (목업)
- 글 상세 ⋯ 메뉴·댓글 ⋯ 메뉴: 그 게시판 매니저·스텝에게만 「작성자 제재」(본인·비회원 글에는 없음). 대화창: 대상 닉네임 · 그 게시판 제재 현황(경고 n · 쓰기 제한 n · 진행 중이면 끝) · 단계 4개(권장 배지 + 「권장: …」 설명, 진행 중 제한이면 제한 3개 비활성) · 사유 코드 · 덧붙임 · 오늘 남은 횟수 → 토스트.
- 게시판 머리: 매니저·스텝 본인에게만 「게시판 운영 중 · 오늘 남은 제재 n건」 + [제재 내역] 링크.
- `/boards/[slug]/sanctions` 「제재 내역」: 매니저 = 그 게시판 전체(처리자 닉네임 포함, 진행 중 쓰기 제한 [해제] + 사유), 스텝 = 내가 준 것만. 매니저·스텝이 아니면 404 화면.
- 쓰기 제한 중인 회원: 글쓰기 버튼·글쓰기 폼·댓글 입력 자리에 「이 게시판에서 {날짜}까지 글·댓글 쓰기가 제한돼요 · 사유 · [자세히]」(→ `/sanction`), 오류 `BOARD_WRITE_RESTRICTED` 도 같은 안내.
- `/sanction`: 「게시판 제재」 구획(게시판 이름·종류·사유·기간·상태·확인하지 않은 경고 [확인했어요]·[이의제기]) · `/sanction/appeal?boardSanctionId=` 「이 게시판에서만 적용된 제재예요」 한 줄. 알림 종류 `BOARD_SANCTION_ISSUED`.
### admin-web (목업)
- `/board-sanctions` 「게시판 제재」(메뉴 커뮤니티 안전, `sanction.read`): 탭 「이력」 · 「반복 검토」(열린 건 배지).
  - 이력: 범위(전체·진행 중·끝남)·게시판·종류·닉네임/번호·기간 → 표(번호·회원·게시판·종류·사유·처리자(닉네임 + 매니저/스텝)·시작·끝·상태·이의) → 서랍 `?boardSanction=`(상세·계기 글 링크·이의 링크·그 회원의 게시판 제재 이력 · [해제](진행 중 제한) · [무효](사유 필수)).
  - 반복 검토: 상태(열림·닫힘) → 표(회원·게시판·7일 건수·여러 게시판 배지·열린 시각) → 서랍 `?review=`(그 게시판 최근 제재 + 처리자 · 다른 게시판 요약 · 사이트 제재 현황 · 닫기 셋 + 메모).
- 회원 상세: 「게시판 제재」 구획(게시판별 건수 + 최근 이력, [게시판 제재 이력 보기] → `/board-sanctions?q=`).
- `/appeals`: 줄·서랍에 종류(사이트/게시판) 배지 · 게시판 이름 · 처리자(닉네임 + 매니저/스텝).

## API 후보 (화면 확정 뒤 다듬는다)
- 공개: `GET /api/boards/{slug}` 의 `viewer.staffTools{ sanction{limit,used,remaining}, resetsAt, manager }`·`viewer.writeRestriction{ boardSanctionId, until, reasonCode }` · `GET /api/board-staff/sanctions/preview?boardId=&userId=` · `POST /api/board-staff/sanctions` · `GET /api/board-staff/boards/{boardId}/sanctions` · `POST /api/board-staff/sanctions/{id}/lift` · `GET /api/me/sanctions` 의 `boardSanctions[]` · `POST /api/me/board-sanctions/{id}/acknowledge` · `POST /api/me/appeals` 에 `boardSanctionId`(`sanctionId` 와 둘 중 하나).
- 관리자: `GET /api/admin/board-sanctions`(+ `/{id}`) · `POST /api/admin/board-sanctions/{id}/lift|void` · `GET /api/admin/board-sanction-reviews`(+ `/summary`·`/{id}`) · `POST /api/admin/board-sanction-reviews/{id}/close` · 회원 상세 `boardSanctions` 요약 · 이의제기 줄·상세의 `kind`·`boardSanction`.
- 서버 번호: shared `V137` · `R__batch_40`(합치기 직전 다시 확인).

## 목업 (2026-11-04, 화면 확정 대기 · 두 앱 `pnpm typecheck` 통과 · 브라우저 확인 안 함)
- **user-web**: 글 상세·댓글에 ⋯ 메뉴를 새로 뒀다(PrimeVue `Menu`, 지금은 「작성자 제재」 하나). `pages/boards/[slug].vue` → `pages/boards/[slug]/index.vue` 로 옮겨 `/boards/[slug]/sanctions` 를 나란히 뒀다. 쓰기 제한은 `viewer.canWrite/canComment=false` + 까닭 `BOARD_WRITE_RESTRICTED` 로 가정. 모양: `composables/board/api/useBoardStaffApi.types.ts`(`BoardStaffTools`·`BoardWriteRestriction`·`BoardSanctionPreview`·`BoardSanctionIssueRequest`·`BoardSanctionIssued`·`BoardStaffSanctionPage`·`BoardStaffSanctionRow`·`BoardSanctionLiftRequest`) · `sanction/api/useSanctionApi.types.ts`(`MyBoardSanction`, `AppealRequest` = `{sanctionId}|{boardSanctionId}` 유니온).
  - `?mock=`: `board-manager`·`board-staff`·`board-limit-used`·`board-restricted`·`board-write-error`·`board-target-protected`·`board-target-restricted`·`board-already-restricted`·`board-preview-fail`·`board-sanction-fail`·`board-sanctions-empty`·`board-sanctions-fail`·`board-lift-fail`·`board-sanctions`(`/sanction` 4건)·`board-ack-fail`·`board-appeal-fail`·`notification-board-sanction`. 예: `/boards/free?mock=board-manager`, `/posts/123?mock=board-staff,board-target-restricted`, `/sanction?mock=board-sanctions`.
- **admin-web**: 탭을 주소 둘로 나눴다 — `/board-sanctions`(이력, `?boardSanction=`) · `/board-sanctions/reviews`(반복 검토, `?review=`), 공용 탭 머리 `BoardSanctionTabs`(열린 건 배지). 메뉴 `boardSanctions`(커뮤니티 안전, 제재와 이의제기 사이, `sanction.read`). 회원 상세 「게시판 제재」 구획, `/appeals` 종류·게시판·처리자. 「사이트 제재 부여」는 기존 `SanctionIssueDialog` 를 회원 고정으로 열고 성공하면 `SITE_SANCTIONED` 닫기. 모양: `composables/sanction/api/useBoardSanctionApi.types.ts`(`BoardSanctionLine`·`BoardSanctionListView`(+`boards`)·`BoardSanctionDetail{sanction,history}`·`BoardSanctionLiftRequest`·`BoardSanctionVoidRequest`) · `useBoardSanctionReviewApi.types.ts`(`BoardSanctionReviewItem`·`…ListView`·`…Summary`·`…Detail`(+`reviewSanctionIds`)·`…CloseRequest{result,memo,siteSanctionId,voidSanctionIds}`) · `MemberDetail.boardSanctions?`(`MemberBoardSanctionSummary`) · `AppealItem/Detail.kind?`·`boardSanction?`(`AppealDetail.sanction` 은 `| null`).
  - `?mock=`: `board-sanction-empty`·`board-sanction-fail`·`board-sanction-stale`(해제·무효 409)·`board-sanction-review-closed`·`appeal-board`(`/appeals` 에 게시판 제재 이의 2건). 목업 회원(ID `9100xx`)·목업 제재만 목업이 답한다.
- 서버가 같이 할 것: `AdminScreenRegistry` 에 `boardSanctions`(`/board-sanctions`, `sanction.read`, `pi pi-list-check`) · 운영자 알림 링크 `/board-sanctions/reviews?review={id}` · 오류 코드 후보 `403 BOARD_STAFF_FORBIDDEN`·`404 BOARD_STAFF_NOT_FOUND`·`409 BOARD_SANCTION_NOT_LIFTABLE`·`BOARD_SANCTION_ALREADY_VOIDED`·`BOARD_SANCTION_REVIEW_CLOSED`·`404 BOARD_SANCTION_REVIEW_NOT_FOUND`.

## 미확정
- 없음(2026-11-04 화면 확정 때 모두 정함 — 결정 9~11).

## 서버 구현 결과 (2026-11-04, 미커밋 · 테스트 없음)
- **DB** shared `V137__board_sanctions.sql`(⚠ develop 기준 V136 다음 — 합칠 때 번호 다시 확인): `board_sanctions`(번호 = `board_sanction_no_seq`, kind·status·reason_code CHECK, 처리자 `issued_by`(회원)+`issued_role`, 계기 `post_id`·`comment_id`(FK 없음)+`source_title`, `acknowledged_at`·`lifted_*`(`lifted_by_type` MANAGER·OPERATOR)·`voided_*`(`void_source` APPEAL·OPERATOR·REVIEW)·`ended_at`) · 색인: **(user_id, board_id) 진행 중 쓰기 제한 하나** 부분 유일 `ux_board_sanctions_running`(회원 앞 — 요청당 쓰기 판정도 이 색인) · 만료 `ix_board_sanctions_expire` · 회원별·게시판별·처리자별·목록(전체/진행 중/끝남) · `board_staff_usage`(PK board·user·한국 날짜, `sanctions` + 다음 작업 자리 `posts`·`comments`) · `board_staff_actions`(kind SANCTION, action ISSUE·LIFT) · `board_sanction_reviews`(번호 시퀀스, (회원, 게시판) 열린 건 하나 부분 유일, `counted_after` = 열 때의 마지막 닫힌 검토 시각, 닫을 때 `closed_count`·`closed_board_count`·`voided_sanction_ids bigint[]`·`site_sanction_id`) · `sanction_appeals` 에 `board_sanction_id`(+`sanction_id` NULL 허용 · 둘 중 하나 CHECK · 게시판 제재당 하나 UNIQUE) · 회원 알림 CHECK ∪ `BOARD_SANCTION_ISSUED`(⚠ 합집합).
- **DB 배치** `R__batch_40_board_sanction_expire.sql`(키 `board-sanction-expire`, 분류 MEMBER, 1분마다): ① 끝난 쓰기 제한 → ENDED(ended_at = ends_at, 조건부 UPDATE, 회원 행은 안 잠금) ② 7일(설정 `retention_days`) 지난 하루 사용량 삭제. 진행 중 판정은 늘 읽을 때 `ends_at > 지금`. Java 쪽 같은 판정 = `BoardSanctionStore#endDue`(부여·해제 전 (회원, 게시판) 지연 정리 — 부분 유일 색인이 끝난 ACTIVE 줄에 막히지 않게).
- **shared** `com.community.sanction`: `BoardSanctionService`(현황 · 부여 · 매니저/운영자 해제 · 운영자 무효 · 경고 확인 · 이의제기 접수·답변 · 반복 검토 닫기 · 쓰기 제한 읽기 · `staffTools`) · `BoardSanctionStore`(JDBC) · `BoardSanctionKind`(권장 단계 `recommended`) · `BoardSanctionReason` · `BoardSanctionException`(코드별 팩터리). `MemberSanctionService#answer` 는 게시판 제재 이의제기면 `BoardSanctionService#answerAppeal` 로 넘긴다(`AppealRow.sanctionId` 는 `Long`, `boardSanctionId` 추가). `BoardActor.writeRestrictions` + `BoardAccessPolicy.withWriteRestriction`·`BOARD_WRITE_RESTRICTED`.
  - 부여 판정 순서: 계정 정상·인증(403) → 게시판(없음 403 · 소모임 게시판 409 대상 보호) → 그 게시판 자리 + 역할 권한 `board.sanction.issue`(403 — 역할 표가 아직 없으면 `MemberRoleQueries` 처럼 막지 않음) → 입력 400 → **대상 회원 행 잠금** → 대상 보호(본인·같은 게시판 매니저·스텝·탈퇴·없음 → 409 하나) → 끝난 제한 정리 → 진행 중 제한이면 409 → 한도 `INSERT … ON CONFLICT … DO UPDATE … WHERE sanctions < 한도`(0행 → 409 + `limit`·`used`·`resetsAt`, 한도 = `boards.staff_sanction_daily_limit` ?? `board_defaults`) → 제재 + 처리 기록 + 반복 검토(7일 · VOIDED 제외 · 마지막 닫힌 검토 이후 · 3건 · 열린 건 없음 → `ON CONFLICT DO NOTHING` 으로 열고 운영자 알림) + 회원 알림, 한 트랜잭션. 잠금 차례: 검토 행 → 이의제기 행 → 회원 행 → 제재 행.
  - 무효: 대기 이의제기 행을 먼저 잠그고 → 회원 행 → VOIDED + 이의 인용(답변 「운영자가 제재를 무효로 했어요」, 번들 `boardSanction.void.appealAnswer` 한국어로 저장) + 답변 알림. 반복 검토 VOIDED 닫기도 같은 길(출처 REVIEW, 무효 사유 = 메모).
- **api**: `GET /api/boards/{slug}` 의 `viewer.staffTools`(그 게시판에서 **제재를 줄 수 있는** 매니저·스텝에게만)·`viewer.writeRestriction` + 다른 까닭이 없으면 `writeBlockedReason`·`commentBlockedReason` = `BOARD_WRITE_RESTRICTED`, `/api/boards/writable` 에서도 빠짐 · `/api/board-staff/sanctions/preview`·`POST /api/board-staff/sanctions`(201)·`GET /api/board-staff/boards/{boardId}/sanctions?scope=&page=`(1부터, 20개)·`POST /api/board-staff/sanctions/{id}/lift` · `GET /api/me/sanctions` 의 `boardSanctions`(최대 50) · `POST /api/me/board-sanctions/{id}/acknowledge`(204) · `POST /api/me/appeals` 의 `boardSanctionId`. **쓰기 막힘**: 새 글(`POST /api/posts`) · 새 댓글·답글(`POST /api/posts/{id}/comments`, 소모임 글 제외) · 핫딜 새 글 전 링크 확인(`GET /api/deals/link-check`) → `403 BOARD_WRITE_RESTRICTED` + `until`·`boardSanctionId`·`reasonCode`(문구에 한국 시간 날짜). 수정·삭제·추천·토론 입장은 막지 않음. 서버에는 임시저장 API 가 없다(막을 곳 없음). 판정은 `BoardActorResolver` 가 요청마다 한 번 읽는 진행 중 제한(색인 질의 하나).
- **admin**: `/api/admin/board-sanctions`(목록 · `/{id}` — `sanction.read` / `/{id}/lift`·`/{id}/void` — `sanction.manage`, 바뀐 상세) · `/api/admin/board-sanction-reviews`(목록 상태 기본 OPEN · `/summary` · `/{id}` — `sanction.read` / `/{id}/close` — `sanction.manage`) · 회원 상세 `boardSanctions`(탈퇴 null) · 이의제기 목록·상세 `kind`(SITE·BOARD)·`boardSanction`·`boardHistory` + 사이트 제재 요약 `issuedBy` · 대시보드 「제재」 가젯의 대기 이의제기에 게시판 제재 이의도(이름 「게시판 쓰기 제한 n일」) · `AdminScreenRegistry` `boardSanctions`(safety, sanctions 다음·appeals 앞). 운영자 알림 `BOARD_SANCTION_REPEAT_REVIEW` 예약 해제(`sanction.manage` 보유자, 링크 `/board-sanctions/reviews?review={reviewId}`, 부여 트랜잭션 안 발행 · 이메일은 커밋 뒤). 감사 `BOARD_SANCTION_LIFT`·`BOARD_SANCTION_VOID`·`BOARD_SANCTION_REVIEW_CLOSE`(대상 MEMBER, 일반 운영 보관) · 게시판 제재 이의 인용·기각은 기존 `APPEAL_ACCEPT`·`APPEAL_REJECT`(+`boardSanctionId`).
- **알림**: 회원 `BOARD_SANCTION_ISSUED`(SAFETY, 잠금 ACCOUNT, 링크 `/sanction`, **메일 없음** — 발행기가 이메일 값을 보지 않는다, 제목 「[게시판] 쓰기 제한 3일 제재를 받았어요 (… 까지, 한국 시간)」·본문 「사유: 스팸·광고 · 덧붙임」, 처리자 없음). 게시판 제재 이의 답변은 `APPEAL_ANSWERED`(kind `BOARD_*` → 「게시판 쓰기 제한 n일」, 메일 없음).
- **화면 타입과 다른 점·정한 것**
  - 관리자 해제·무효 사유는 **1~200자**(결정 11 — admin 타입 주석의 1~500 과 다름), `fields.reason`.
  - `viewer.staffTools` 는 역할 권한 `board.sanction.issue` 가 없거나 계정이 정상·인증이 아니면 null(자리만 있어도 null). 내역(`/boards/{boardId}/sanctions`)은 자리 + 계정 정상이면 본다.
  - 부여 `postId`·`commentId` 는 그 게시판의 **대상 회원의** 글·댓글이어야 한다(댓글이면 글과 함께) — 아니면 400 `fields.postId|commentId`. 숫자가 아니어도 같은 칸 오류.
  - 매니저·스텝 내역 `source` 는 계기가 있으면 늘 준다(글이 지워져도 — 제목은 줄 때 스냅숏). `lift` 에는 해제한 사람이 없다.
  - 대상 보호 409 는 소모임 게시판 id 로 부를 때도. 없는 게시판 id 는 403.
  - 회원의 경고 확인·이의제기에서 내 것이 아닌 게시판 제재는 404 `BOARD_SANCTION_NOT_FOUND`. `POST /api/me/appeals` 에 둘 다 보내면 400 `fields.boardSanctionId`.
  - 이의제기 상세: 게시판 제재 이의는 `sanction: null` · `history` = 그 회원의 사이트 제재 이력(빈 배열일 수 있음) · `boardHistory`, 사이트 제재 이의는 `boardSanction: null` · `boardHistory: []`. `kind` 는 늘 온다.
  - 반복 검토 `reviewSanctionIds` = 열 때 7일 앞(또는 마지막 닫힌 검토) 뒤 ~ 닫은 때(열려 있으면 지금) 받은 VOIDED 아닌 제재. `recentCount`·`recentBoardCount` 는 열린 건 지금 계산, 닫힌 건 닫을 때 저장한 값.
  - 로그인 정보 `restriction.pendingNotices` 는 사이트 경고만 센다(게시판 경고는 넣지 않았다).
- **검증**: `compileJava`·`compileTestJava`(shared·api·admin) 통과. 로컬 DB(irobit-local-postgres, V135 까지 적용)에 V136+V137+R__batch_40 을 `BEGIN … ROLLBACK` 으로 적용(부분 유일 색인 막힘 · CHECK · 한도 증가 1→2→0행 · 열린 검토 `ON CONFLICT` · 이의 하나·둘 중 하나 CHECK · 알림 CHECK · EXPLAIN 부분 색인 확인), 스키마 사본 임시 DB 에서 `CALL batch_board_sanction_expire()` 실행(끝난 2건 ENDED · 지난 사용량 1줄 삭제 · OK, 다시 부르면 SKIP) 후 임시 DB 삭제. 테스트 작성·실행·bootRun·API 실제 호출은 하지 않았다.
- **프론트 후속**: user-web `useBoardStaffApi.ts`(MOCK → REAL, `issue` 의 `nickname` 인자 버림) · `useBoardApi` 의 `mockBoardViewerExtras` 제거(서버가 `staffTools`·`writeRestriction`·`BOARD_WRITE_RESTRICTED` 를 준다) · `usePostApi`·`useCommentApi` 의 `mockBoardWriteGuard` 제거(오류 본문 `until`·`boardSanctionId`·`reasonCode`) · `useSanctionApi`(boardSanctions · `/api/me/board-sanctions/{id}/acknowledge` · appeals `boardSanctionId`) · 알림 목업 `notification-mock.ts` 제거. admin-web `useBoardSanctionApi.ts`·`useBoardSanctionReviewApi.ts` MOCK → REAL · `useMemberApi` 의 `boardSanctions` 목업 제거 · `useAppealApi` 의 `kind`·`boardSanction`·`boardHistory` 를 선택 칸에서 필수로(서버가 늘 준다) · 해제·무효 사유 최대 200자로.

## 연결 (2026-11-04)
- **실제 API 로 바뀐 것**: user-web `useBoardStaffApi`(preview·부여·내역·해제 — `issue` 의 `nickname` 인자 없앰) · `useBoardApi.board`(서버 `viewer.staffTools`·`viewer.writeRestriction` 그대로) · `usePostApi`/`useCommentApi.create`(쓰기 막힘 흉내 없앰 — `403 BOARD_WRITE_RESTRICTED` 본문 `until`·`boardSanctionId`·`reasonCode`) · `useSanctionApi`(`boardSanctions` · `/api/me/board-sanctions/{id}/acknowledge` · appeals `boardSanctionId` 같은 줄) · `useNotificationApi`(목업 알림 없앰). admin-web `useBoardSanctionApi`·`useBoardSanctionReviewApi` · `useAppealApi`(보강 없앰) · `useSanctionApi`(목업 회원 분기 없앰) · `useMemberApi`(`boardSanctions` 목업 없앰).
- **지운 목업**: user-web `composables/board/api/mock/board-staff-mock.ts`·`composables/sanction/api/mock/sanction-mock.ts`·`composables/notification/api/mock/notification-mock.ts`(`common/mock/mock-util.ts` 는 성인 확인 목업이 써서 남김) · admin-web `composables/sanction/api/mock/board-sanction-mock.ts`·`composables/common/mock/mock-util.ts`(쓰는 곳 없음). 「목업」 절의 `?mock=` 스위치는 모두 없어졌다.
- **서버에 맞춘 것**: 화면 타입 = 서버 DTO 칸 이름·null 여부(`BoardViewer.staffTools`·`writeRestriction` · `MySanctions.boardSanctions` · `MyBoardSanction.board.nameEn` · `MemberDetail.boardSanctions` · `AppealItem/Detail.kind`·`boardSanction`·`boardHistory` · `AppealSanctionBrief.issuedBy` 를 선택 → 필수). 부여 `400 fields.kind|postId|commentId` 는 대화창 폼 아래 한 줄(`reasonCode`·`reasonText` 는 칸 아래). 회원 쪽 게시판 경고 확인·이의제기의 404 는 `BOARD_SANCTION_NOT_FOUND` 로 가른다. `/boards/{slug}/sanctions` 는 `viewer.staffTools` 대신 목록 `404 BOARD_STAFF_NOT_FOUND` 로 운영진을 가른다(역할 권한 없이 자리만 있는 매니저·스텝도 내역을 본다 — 남은 한도 줄은 `staffTools` 가 있을 때만). 해제·무효 사유 최대 200자(관리자 `UtilBoardSanction.REASON_MAX`). 「진행 중」 = 진행 중 쓰기 제한만(유효한 경고는 「전체」에만 나온다 — 서버 그대로).
- **남은 차이**: 로그인 정보 `restriction.pendingNotices` 는 게시판 경고를 세지 않아 로그인 직후 안내(`/sanction` 착륙)에는 게시판 경고가 없다 — 대신 그 게시판 머리에 한 줄(아래 「확인하지 않은 경고 안내」). 핫딜 새 글의 링크 확인(`GET /api/deals/link-check`)이 `403 BOARD_WRITE_RESTRICTED` 로 막히면 화면은 링크 확인 오류 한 줄로 보인다(보통은 게시판 응답의 `writeRestriction` 으로 폼 대신 안내가 먼저 뜬다). 두 앱 `pnpm typecheck` 통과 — 브라우저 확인·테스트는 아직 하지 않았다.

## 확인하지 않은 경고 안내 (2026-11-05 · 테스트 없음)
- 경고는 쓰기를 막지 않아 알림을 놓치면 `/sanction` 에 가야만 보였다 → 경고를 준 **그 게시판 머리**에 「이 게시판에서 경고를 받았어요 · 사유 · [자세히] · [확인했어요]」 + 「글·댓글은 그대로 쓸 수 있어요…」(`BoardWarningNotice`, 쓰기 제한 안내 바로 아래). 로그인 직후 안내(사이트 경고 길)에는 넣지 않는다(사용자 결정 — 시안 B).
- `GET /api/boards/{slug}` `viewer.pendingWarnings`: `[{boardSanctionId, reasonCode, issuedAt}]` — 보는 나의 그 게시판 경고 중 무효 아님·`acknowledged_at` 없음, 최근 것이 위, 최대 10(`BoardSanctionService.PENDING_WARNING_MAX`). 비로그인·소모임 게시판은 빈 목록. 처리자 없음. 마이그레이션 없음(`ix_board_sanctions_user`).
- 둘 이상이면 가장 최근 사유 + 「외 n건」. [확인했어요]는 실린 경고를 모두 `POST /api/me/board-sanctions/{id}/acknowledge`(404 는 넘김) → 안내를 뺀다. `/sanction` 에서도 확인한 것으로 바뀐다.
