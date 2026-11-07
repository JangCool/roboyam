# 보상 이벤트 계약 (화면 확정 2026-11-07)

운영자가 회원에게 보상(무상 포인트·포인트 상점 아이템·로보티콘 팩·직접 전달)을 주는 이벤트를 만들고, **어떤 이벤트였고 누가 받았으며 어떻게 골랐고 언제·누가·무엇을 지급(실패·회수)했는지**를 남긴다.
「사이트 팝업」(이벤트·프로모션 알림 창, 다른 세션)과 다른 업무다 — 늘 **보상 이벤트**(`RewardEvent`, 주소 `/reward-events`, 표 `reward_event_*`, 권한 `reward-event.*`)라고 부른다.
포인트·아이템 지급은 커머스 범위다(`docs/domain/commerce.md` 4·5-4·7절) — 아래 규칙은 모두 테스트로 막는다.

## 화면 (admin-web, 확정)
- `/reward-events` 목록(상태 탭·검색·방식) · `/reward-events/new` 만들기 · `/reward-events/{id}` 상세(탭 개요·대상·당첨자 `recipients`·추첨 기록 `draws`·지급 내역 `runs`·기록 `audit`) · `?edit=1` 고치기.
- 파일: `pages/reward-events/*`, `components/rewardevent/*`, `composables/rewardevent/api/useRewardEventAdminApi(.types).ts`(서버 모양의 정본 후보), `utils/util-reward-event*.ts`, i18n `rewardEvent`.
- 메뉴: 로보티콘 묶음 다음 묶음 `rewardEvent`(`pi pi-gift`) — 항목 `rewardEvents` `/reward-events`(`reward-event.read`). 서버 `AdminScreenRegistry` 에 같은 줄.
- 회원 쪽(user-web)은 새 화면 없음 — 알림 + 포인트 내역(사유 「이벤트 보상」 + 이벤트 이름) + 내 아이템(출처 「이벤트 보상」)만. 안내·당첨 발표는 공지 글(이벤트의 `noticeUrl`).

## 사용자 결정 (2026-11-07)
1. 선착순 기준 사건은 **이벤트마다 고른다**: 본인인증 완료(권장 — 1인 1계정) / 이메일 인증(가입 완료 — 부계정 중복 수령 가능 경고).
2. 보상 아이템: 포인트 상점 아이템 + **로보티콘 팩(운영 팩·무료 팩만, 작가 유료 팩은 제외 — 정산 때문)**. 로보티콘 지급은 받기 줄로 남기고 무료 받기 작가 보상 포인트는 주지 않는다.
3. 대상 고르기: 직접 입력·검색 / 글 댓글 작성자 중 추첨 / 조건 일괄 / 전체 회원 일괄 — 모두.
4. 회원 노출: 알림 + 포인트 내역·내 아이템만(공개 이벤트 페이지 없음).
5. 다른 운영자 승인 없음 — 권한으로만.

## 상태·방식
- 상태: 준비 `DRAFT` → 시작 → 진행 중 `ACTIVE` → 마감 `CLOSED`(운영자 마감·끝 시각 지남·선착순 인원 참) → 지급 끝 `DONE`. 지급이 하나도 없을 때만 취소 `CANCELED`(사유 5~200자) — 지급이 있으면 `409 REWARD_EVENT_HAS_GRANTS`(회수를 쓴다).
  - 끝 시각이 지난 `ACTIVE` 는 읽을 때 마감으로 보고(배치 없음) 다음 쓰기 때 `CLOSED` 로 고정한다. 시작은 끝 시각 전만(`409 REWARD_EVENT_PERIOD_PASSED`).
  - 지급 끝 `DONE` = 마감 뒤 대기 대상이 0 이 된 때(실패가 남아도 DONE, 「실패만 다시 지급」은 DONE 에서도 된다).
- 방식 `mode`:
  - `FIRST_COME` 선착순 자동: 기간 안에 기준 사건(`IDENTITY_VERIFIED`·`EMAIL_VERIFIED`)이 일어난 회원에게 그 트랜잭션 안에서 자동 지급, 인원(1~100,000) 차면 자동 마감(감사 `AUTO_CLOSE`). 등수 하나.
  - `SELECTION` 당첨자 선정: 후보 방법 `MANUAL`(붙여 넣기·검색) · `COMMENT_DRAW`(글 댓글 작성자 추첨) 중 하나 이상. 등수 여럿(이름 1~20자·인원). 당첨자 추가·빼기는 지급 전, 추첨·지급은 마감 뒤.
  - `BULK` 일괄: `ALL`(정상 회원 전체) / `CONDITION`(등급 서열 이상·가입일 범위·본인인증만·최근 N일(1~365) 로그인). 대상은 지급 실행 때 조건으로 정한다. 등수 하나.
- 고치기 범위(`actions.editScope`): 준비 `FULL` 전부 · 진행 중 `ACTIVE` 이름·메모·안내 글·끝 시각·인원 · 마감 `CLOSED` 메모·안내 글·등수 인원 · 지급 끝·취소 `MEMO` 메모만. 밖의 칸이 바뀌면 `409 REWARD_EVENT_LOCKED_FIELD`. 인원은 이미 받은(선정된) 수 아래로 못 줄인다(`400 fields.firstCome.limit|tiers[i].quota`).

## 대상 규칙
- 같은 회원은 **이벤트당 한 번**(등수끼리도) — 유일 `(event_id, user_id)`.
- 늘 제외: 정지·탈퇴 회원. 운영자 회원 계정은 `excludeOperators`(기본 켬)일 때 제외.
- 댓글 추첨 후보 = 그 글의 기간 안 댓글(지운 댓글 제외) 작성자를 1인 1회로, 글 작성자 제외(`excludeAuthor` 기본 켬), 위 제외 + 이미 이 이벤트 대상인 회원 제외.
- 추첨은 서버가 한다: 추첨 번호(seed, 64비트 난수)를 정하고, 회원 번호 오름차순 후보 목록을 그 번호의 난수로 섞어 앞에서 N명 — 같은 후보와 번호면 같은 결과. 기록: 시각·운영자·글·등수·후보 수·뽑은 수·번호·뽑힌 회원. 후보가 모자라면 `409 REWARD_DRAW_NOT_ENOUGH`, 등수 자리가 모자라면 `409 REWARD_EVENT_TIER_FULL`.

## 보상 줄 (등수마다 1~10줄) — 지급 규칙 [커머스]
| 종류 | 칸·검사 | 지급 | 회수 |
|---|---|---|---|
| `POINT` 무상 포인트 | 1~100,000P | `PointService` 한 길, **무상 묶음 1년**, 원장 사유 `EVENT_REWARD`(EARN), 대상 = 줄 결과(유일 `(사유, 대상)` → 같은 줄은 한 번만). 하루 활동 한도에 넣지 않는다. 미회수 빚이 있으면 같은 트랜잭션에서 먼저 갚는다(5-4). 유상은 만들 수 없다. 포인트 동결 회원은 그 줄 실패 `POINT_FROZEN` | 사유 `EVENT_REWARD_REVOKE`(REVOKE) — **무상에서만 0P 까지**(빚을 만들지 않는다 — 운영 차감과 같다), 덜 회수되면 `POINT_PARTIAL` + 회수한 양 |
| `SHOP_ITEM` 포인트 상점 아이템 | 품목(아바타 제외, 판매 중단 품목도 가능·삭제 품목 불가), 소모형 수량 1~10(그 밖은 1) | `user_items` 에 **지급 출처**로 보유 줄(구매 없음) — 기간제는 지급 순간부터 품목 기간(같은 품목 기간이 남았으면 앞 줄 끝에서). 이미 가진 영구 아이템은 줄 건너뜀 `ALREADY_OWNED`. **지급 아이템은 환불 대상이 아니다**(셀프·예외 환불 모두) | 아직 안 쓴 줄(`first_used_at` 없음·OWNED)만 `REVOKED`, 쓴 줄은 `ITEM_USED`(회수 못 함) |
| `EMOTICON_PACK` 로보티콘 팩 | **운영 팩·무료 팩만**(작가 유료 팩은 `400`), 판매 중(APPROVED)만 | 받기 줄(출처 이벤트) — 작가 받기 보상 포인트 없음, 구매·결제 줄 없음. 이미 가진 팩은 건너뜀 `PACK_ALREADY_OWNED` | 받기 줄 빼기(이벤트로 받은 줄만) |
| `MANUAL` 직접 전달 | 이름 1~40자 · 전달 방법 메모 0~200자(개인정보 금지) | 사이트는 주지 않는다 — 줄 결과 `DELIVERY_PENDING`, 운영자가 「전달 완료」(메모 0~200자) → `DELIVERED`(시각·운영자) | 기록만 `MANUAL_RECORD_ONLY` |
- 받는 사람 상태: `PENDING` · `GRANTED`(모든 줄 성공·건너뜀·전달 대기/완료) · `PARTIAL`(일부 실패) · `FAILED`(모두 실패) · `SKIPPED`(모두 건너뜀) · `REVOKED`.
- 실패 사유: `POINT_FROZEN`·`WITHDRAWN`·`SUSPENDED`·`ITEM_UNAVAILABLE`·`SERVER_ERROR`. 줄 하나의 실패가 다른 줄을 되돌리지 않는다(줄마다 세이브포인트). 「실패만 다시 지급」은 실패 줄만 다시(성공 줄은 유일 키가 막는다).
- 지급 권한: `reward-event.grant`. **1인 포인트 합이 고액 문턱(`point_settings.adjust_large_threshold`) 초과, 또는 `BULK` 방식에 포인트 보상이 있으면** `point.adjust.large` 도 필요 — 없으면 `403 POINT_ADJUST_LARGE_REQUIRED` + 거부 감사. 선착순 자동 지급은 만들기·시작 때 같은 검사(시작하는 운영자 기준).
- 멱등: 지급 실행은 `idempotencyKey`(이벤트마다 유일) — 같은 키는 처음 실행을 돌려준다. 원장·보유 줄은 줄 결과 유일 키로 한 번만.
- 대량(일괄): 실행 한 번 = `RewardRun`(RUNNING, 진행률). 대상 줄을 만들고 나눠(한 덩어리 = 한 트랜잭션) 처리한다. 서버 여러 대여도 한 실행은 한 곳만 처리(DB 잠금으로 가져가기). 서버가 죽어도 남은 덩어리를 이어 한다.

## 알림·감사
- 회원 알림: 지급되면 `REWARD_EVENT_GRANTED`(이벤트 이름 + 보상 요약), 회수되면 `REWARD_EVENT_REVOKED`(사유). 직접 전달 줄만 있는 지급도 알린다(「운영자가 따로 보내요」).
- 감사(대상 `REWARD_EVENT` + 이벤트 ID): 만듦·고침·시작·마감·자동 마감·추첨·당첨자 추가·빼기·지급·재시도·회수·전달 완료·취소. 지급·회수·재시도는 돈 관련(5년). 상세 「기록」 탭은 이 감사를 이벤트로 걸러 보여 준다.

## 권한 (서버 `Permission` 정본)
- `reward-event.read`(READ) · `reward-event.manage`(MANAGE — 만들기·고치기·시작·마감·취소·후보·당첨자·추첨) · `reward-event.grant`(SENSITIVE — 지급·재시도·회수·전달 완료) + 기존 `point.adjust.large`. 회원 이메일은 `member.pii` 가 없으면 가린다.

## API (admin, 모두 `/api/admin/reward-events`)
- `GET ?status&mode&q&page&size` → `RewardEventPage`(번호 내림차순, `counts` 상태별) · `GET /{id}` → `RewardEventDetail`(`stats`·`actions`·`recipientsByTier`·`runningRun`) · 없으면 `404 REWARD_EVENT_NOT_FOUND`
- `POST` `RewardEventSaveRequest` → `201` · `PUT /{id}` · `POST /{id}/start|close|cancel{reason}` — 상태가 안 맞으면 `409 REWARD_EVENT_STATE`
- `GET /catalog`(상점 품목·로보티콘 팩 `selectable`·등급·고액 문턱) · `GET /member-search?q=`(20명)
- `POST /preview/comments` → `RewardCommentPreview` · `POST /preview/targets` → `RewardTargetPreview`(저장 전에도 쓴다 — 번호 없음)
- `POST /{id}/recipients/resolve{lines}` · `POST /{id}/recipients{tierId,memberIds}` · `DELETE /{id}/recipients/{rid}`(지급 전만 — `409 REWARD_RECIPIENT_GRANTED`) · `GET /{id}/recipients?status&tierId&q&page&size`
- `POST /{id}/draws{tierId,count}` → `RewardDraw` · `GET /{id}/draws`
- `GET /{id}/grants/preview` → `RewardGrantPreview`(`largeRequired` 는 서버 판정) · `POST /{id}/grants{idempotencyKey}` → `RewardRun` · `POST /{id}/grants/retry` · `POST /{id}/revocations{recipientIds,reason}`(5~200자) · `POST /{id}/recipients/{rid}/manual-delivered{lineId,memo}`
- `GET /{id}/runs` · `GET /{id}/audit`
- 칸 오류는 `400 VALIDATION fields.<경로>`(예 `tiers[0].rewards[1].points`). 모양은 `useRewardEventAdminApi.types.ts` 와 같게.

## 저장 (shared V163 — 합치기 전 develop 번호 다시 확인, V162 는 사이트 팝업 세션)
- 새 표 `reward_events`·`reward_event_tiers`·`reward_event_lines`(품목·팩 이름 사본)·`reward_event_recipients`·`reward_event_line_results`(id = 원장·보유 줄의 대상)·`reward_event_draws`·`reward_event_runs`.
- 넓히는 것: 원장 사유 CHECK에 `EVENT_REWARD`·`EVENT_REWARD_REVOKE`, 대상 CHECK에 `REWARD_EVENT_RESULT`(⚠ 다른 세션도 이 CHECK 를 다시 만든다 — 합집합 확인) · `user_items` 에 지급 출처(구매 또는 줄 결과 중 정확히 하나) · 로보티콘 받기 종류에 이벤트 출처.

## 미확정
- 없음(위 가정은 화면 확정으로 함께 정했다).

## 서버 구현 (2026-11-07)
계약에 없어 서버가 정한 것(목업 동작을 따른 것 포함). 코드: shared `com.community.rewardevent`(서비스·저장소), admin `RewardEventAdminController`·`RewardEventAdminService`·`RewardRunScheduler`.
- **마이그레이션 V163**(V162 = 사이트 팝업 세션). ⚠ 합치기 전 그쪽 V162 가 원장 사유·대상·묶음 출처·회원 알림 종류 CHECK 를 다시 만드는지 보고 합집합을 맞춘다.
- **지급 끝(DONE)** = 마감 + 나눠 처리 중 실행 없음 + (선착순이거나 지급 실행이 있었음) + 대기 받는 사람 0 + **전달 대기 줄 0**(목업 `checkDone`). 일괄을 진행 중에 지급하면 마감으로 바꾼다. 「실패만 다시 지급」은 취소 아닌 상태(지급 끝 포함)에서.
- **무상 1년** = 포인트 정책 `free_valid_days`(기본 365). 원장 `note` = 이벤트 이름(지급·회수 모두 — 회원 포인트 내역에 보인다).
- **고액**: 선착순은 만들기·준비 고치기·시작 때(그 운영자), 당첨자·일괄은 지급 때, 재시도는 실패한 등수 기준. 일괄은 대상 수와 상관없이 포인트 줄이 있으면 고액. 거부 감사 대상 = `REWARD_EVENT` + 이벤트 번호(만들기는 `new`).
- **지급 출처**: `user_items.purchase_id` NULL + `grant_result_id`(줄 결과) + `grant_seq`(소모형 수량 N → 1..N 줄). 셀프 환불은 `409 POINT_SHOP_REFUND_NOT_ALLOWED blockedBy GRANTED`(새 값), 예외 환불·관리자 구매 내역은 구매 기준이라 닿지 않는다. 내 아이템 응답에 `source`(`PURCHASE`|`REWARD_EVENT`) 칸을 더했다(지급 줄은 `purchaseId`·`refundUntil` null, `pricePaid` 0, `purchasedAt` = 받은 시각).
- **로보티콘**: 받기 줄 `kind = EVENT` + `reward_result_id`. 없으면 새 줄(받은 수 +1), 빼거나 환불해 안 가진 줄은 이벤트 줄로 다시 가짐(구매 이음은 지운다, 받은 수 그대로), 가진 팩·작가 자기 팩은 `PACK_ALREADY_OWNED`. 지급 때 판매 중이 아니거나 작가 유료면 `ITEM_UNAVAILABLE`. 회수 = 그 이벤트 줄 `removed_at`. 회원이 뺀·회수된 이벤트 줄을 다시 받으면 무료 받기 줄로 돌아간다. 회원이 이벤트 줄을 목록에서 뺄 수 있다.
- **회수**: 한 요청 500명, 바로 처리(실행 `REVOKE` 끝난 줄). 쓴 아이템(처음 사용 시각·USED·대기 신청에 예약된 올리기 변경권)이 있는 줄은 안 쓴 줄만 회수하고 그 줄은 `SUCCESS` + `ITEM_USED`. 받는 사람은 `REVOKED`.
- **대상**: 정상 회원 = `ACTIVE` 이고 지우지 않음(이메일 인증 여부는 보지 않는다). `WITHDRAWING`·`WITHDRAWN`·지움 = 탈퇴, `SUSPENDED`·`BANNED` = 정지. 운영자 회원 계정 = 활성 연결(`OperatorMemberAccountService`).
- **이메일 인증 기준 사건** = 인증 링크로 처음 인증(이메일 변경은 아님) + 공급자가 확인한 이메일로 소셜 가입. 본인인증은 확정 트랜잭션(가입 포인트 다음). 모두 이벤트마다 세이브포인트.
- **댓글 추첨**: 기간은 [from, to] 포함, 비우면 이벤트 기간(미리 보기는 처음~지금). 추첨 번호 = `SecureRandom` 64비트, 표시 16자리 16진수(목업 8자리), 섞기 = `java.util.Random(seed)` + `Collections.shuffle`(자바 명세가 차례를 정한다).
- **나눠 처리**: 일괄 대상 500명 · 지급 100명이 한 덩어리(한 트랜잭션), 요청 안에서 3덩어리, 나머지는 관리자 서버 워커(`roboyam.reward-event.run-poll-millis` 기본 5초, `roboyam.batch.enabled` 서버만, `worker_heartbeats` 키 `reward-event-runs`). 일괄 실행의 `targetCount` 는 시작 때 미리 센 수 → 대상 만들기가 끝나면 실제 수.
- **고치기(준비)**: 등수는 요청 번호가 그 이벤트의 등수면 남기고, 보상 줄은 모두 새로 만든다(줄 번호가 바뀐다) — 남은 당첨자의 줄 결과도 다시 만든다. 당첨자 선정이 아니게 바꾸면 넣은 당첨자는 지운다. 댓글 기간(`from`·`to`)은 목업처럼 범위 검사에서 보지 않는다.
- **오류 코드 추가**(목업에 있던 것): `404 REWARD_RECIPIENT_NOT_FOUND` · `409 REWARD_EVENT_NO_COMMENT_SOURCE` · `409 REWARD_REVOKE_NOTHING` · `409 REWARD_DELIVERY_STATE` · 미리 보기 `404 POST_NOT_FOUND`. `DELETE …/recipients/{rid}` 는 200 + 상세.
- **기록 탭** 한 줄 = 번들 `rewardEvent.audit.*`(요청 언어). 지급·재시도는 시작·끝 두 줄(돈 관련 5년), 회수 한 줄.
- **회원 알림** `REWARD_EVENT_GRANTED`·`REWARD_EVENT_REVOKED` — 끌 수 없는 처리 알림, 누르면 `/me/points`. 지급 요약은 읽을 때 언어로 「1,000P · 아이템 N개 · 운영자가 따로 보내는 보상 N건」.

## 화면 연결 (2026-11-08)
- admin-web 은 모두 실제 API(목업 파일 지움). 지급 확인창 고액 여부 = 서버 `largeRequired`(일괄 + 포인트는 늘 고액 안내). 고치기 저장 뒤 응답으로 폼을 다시 채운다(준비 상태는 보상 줄 번호가 바뀜). 한 번에 넣기·회수 500건을 화면에서 미리 막는다.
- 권한 카탈로그 `util-permission-catalog.ts` 에 `reward-event.read|manage|grant` · 포인트 사유 코드에 `EVENT_REWARD`·`EVENT_REWARD_REVOKE`.
- user-web: 내 아이템 `source`(`PURCHASE`|`REWARD_EVENT`) — 받은 줄은 「이벤트 보상 · ○일 받음」, 환불 단추 없음. 셀프 환불 409 `blockedBy GRANTED` 는 서버 번들 `error.POINT_SHOP_REFUND_NOT_ALLOWED.GRANTED`. 알림 두 종류(아이콘 `pi pi-gift`·`pi pi-undo`, 문구는 서버). 포인트 내역 `note` = 이벤트 이름(원장 그대로).

## 남은 일 처리 (2026-11-08)
- 내 로보티콘: `GET /api/me/emoticons` 의 팩에 `reward`(보상 이벤트로 받은 팩 — 받기 종류 EVENT) — 화면은 「이벤트 보상」 딱지(무료 팩처럼 목록에서 뺄 수 있다). 시험 `RewardItemPackGrantTest`(가진 팩 조회의 출처).
- 내 아이템: 보상 이벤트로 받았다가 회수된 줄은 「환불됨」이 아니라 「회수됨 N개」 묶음.
- 이메일 인증·소셜 가입 지점의 자동 지급: api 시험 `RewardFirstComeEmailApiTest` 4개 — 이메일 첫 인증이면 받음(다시 눌러도 한 번) · 본인인증 기준 이벤트는 안 받음 · 이메일 변경 링크는 기준 사건 아님 · 소셜 가입은 공급자가 확인한 이메일만.

## 남은 일
- 없음.
