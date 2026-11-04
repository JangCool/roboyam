# 게시판 제재 계약 (**정책 확정 2026-11-04 · 화면 목업 중**)

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
1. 운영자 무효로 열린 검토의 7일 건수가 3 아래로 떨어지면 검토를 자동으로 닫을지(제안: 닫지 않음 — 운영자가 닫는다, 화면은 지금 건수를 보인다).
2. 대기 중인 이의제기가 있는 제재를 무효로 하면(제안: 그 이의제기를 같은 트랜잭션에서 「인용」으로 닫고 답변 「운영자가 제재를 무효로 했어요」).
3. 맞출 것(목업끼리 다름): 범위 「진행 중」 = 진행 중 쓰기 제한만(경고 제외, admin 목업 기준 — user-web 내역 화면을 맞춘다) · 해제·무효 사유 길이(admin 1~500, user-web 매니저 해제 1~200 — 제안: 둘 다 1~200).
