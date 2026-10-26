# 커머스 1단계 계약 — 포인트 확장 + 포인트 상점 (ERD·패키지 제안 승인 2026-10-26 — 구현 전)

**의도**: 포인트를 무상(적립)·유상(충전)으로 나누고, 받은 포인트를 묶음(lot) 단위로 관리해 소멸·회수·사용 취소를 정확히 한다(유상이 아직 없어도 구조를 먼저 바꾼다). 그 위에 **포인트 상점**(포인트로 사는 디지털 아이템)을 만든다.
정책 원천은 `docs/domain/commerce.md` 2-2·2-3·4·5·6·7·12-3·12-4·13-2·14·15·18절이고, 0단계 장치(기능 제한·멱등·속도 제한·배치 기반·시험 DB)는 `commerce-foundation-contract.md` 에 있다. 이 문서는 1단계에서 정한 구조와 결정만 적는다.
**테스트**: 이 문서와 commerce.md 에 적힌 규칙은 모두 테스트로 막고 통과해야 끝난다(CLAUDE.md 커머스 예외, commerce.md 18절). 잠금·동시성·제약·트리거는 `CommerceTestDatabase`(실제 PostgreSQL)로 막는다.

## 작업 단위와 순서
| 단위 | 내용 | 화면 | 상태 |
|---|---|---|---|
| 1-1 포인트 확장 | 아래 A + 배치·불변식 + 회원·관리자 포인트 API | 작음: 회원 포인트 화면(소멸 예정·미회수), 관리자 원장 무상/유상 칸·회원 상세 묶음 — 목업 먼저 | **완료 — develop 병합 fe41910**(2026-10-26, 커밋 1d52436) — 화면 확정·서버·화면 연결·규칙 테스트 통과(shared 157·api 40·admin 84, 알림 간격 추가 뒤 shared 123·api·admin 다시 통과), 아래 「1-1 구현 결과」 |
| 1-2 포인트 상점 | 아래 B + 닉네임 변경권 사용 + 장식 표시 + 0-5 이름 바꾸기 | 큼: 회원 포인트 상점·내 아이템·구매 확인, 관리자 품목·구매 내역·설정 — 목업 먼저 | **완료 — develop 병합 b51cd9b**(2026-10-26, 커밋 1421b3a) — 화면 확정(아바타 포함)·서버(V86)·화면 연결·규칙 테스트 통과(바꾼 범위 shared 195·api 55·admin 97, 두 앱 typecheck — 정지 중 장착 금지·환불 신청(V87) 포함). 합친 뒤 develop 에서도 같은 범위 통과(「1-2 구현 결과」) |
| 1-3 적립 규칙 보강(선택) | commerce.md 6-2 설정 칸 + 가입 포인트(본인인증 완료 때 평생 1회) + 출석체크(community 이식) | 포인트 정책 칸 · 출석 달력 | **완료 — develop 병합 663053d**(2026-10-26, 커밋 e3e9545 — 병합 뒤 커머스 테스트 774개(shared 361·api 133·admin 280) `--rerun` 실패 0, 두 앱 typecheck 통과) — 화면 확정·서버(V89)·화면 연결·규칙 테스트 통과(바꾼 범위 shared 194·api 94·admin 51, 0 실패, 두 앱 typecheck), 아래 「1-3 구현 결과」 |
순서: 1-1 서버 ∥ 1-2 화면 목업 → (1-1 을 develop 에 합친 뒤) 1-2 서버 → 1-3. 1-2 서버는 포인트 사용·사용 취소(1-1)에 기댄다.

## 마이그레이션 계획 (develop 병합 때 확정 — V85·V86·V87, R__batch_29. 그 뒤 소모임 분류가 V88)
| 가번호 | 스트림 | 내용 | 단위 |
|---|---|---|---|
| V85 `point_extension` | shared | A 전부 | 1-1 |
| V86 `point_shop` | shared | B 전부 + 닉네임 변경권 품목 하나(가격 = 지금 `point_settings.nickname_ticket_price`) + 유료 아바타 306 | 1-2 |
| V87 `point_shop_refund_request` | shared | 정지 중 환불 신청 · 환불 종류 · 회원 알림 종류 | 1-2 |
| `R__batch_29_point_invariant_check` | shared | pg_cron 매일, 읽기만 — 어긋나면 FAIL(기존 배치 실패 알림) | 1-1 |
- Java 배치(카탈로그 등록, 마이그레이션 없음): `point-expire`(매일 00:10 KST, 덩어리·체크포인트·resumable), `point-expiry-notice`(매일).
- 회원 알림 종류 CHECK 에 `POINTS_EXPIRING` — 다시 만들 때 **최신 목록(V84)과의 합집합**.

## A. 포인트 확장 (commerce.md 5절)
- **`point_balances`**: `free_balance`·`paid_balance`(bigint ≥ 0) 추가. `balance` 는 `GENERATED ALWAYS AS (free_balance + paid_balance) STORED` — 기존 칸은 계산 칸으로 바로 바꿀 수 없으므로 값을 `free_balance` 로 옮기고 칸을 지운 뒤 다시 만든다(정렬 색인도 다시). `revoke_debt`(bigint ≥ 0, 미회수) 추가.
- **`point_lots`**(새): id, user_id, kind(`FREE`|`PAID`), original_amount(> 0), remaining_amount(0 ≤ · ≤ original), expires_at(FREE 는 반드시 있음, PAID 는 반드시 null — D12), source_reason, source_ref_type, source_ref_id, source_ledger_id(유일, null 허용), restored_from_lot_id, expiry_noticed_at, created_at. 색인 `(user_id, kind, expires_at, id) WHERE remaining_amount > 0`(차감 순서) · `(expires_at) WHERE kind='FREE' AND remaining_amount > 0`(소멸·알림).
- **`point_ledger`**: `free_delta`·`paid_delta`(bigint, 합 = delta — 기존 줄은 `free_delta = delta` 로 채운 뒤 수정 금지 트리거를 새 칸까지 막게 교체). `reason` varchar(30)·`ref_type` varchar(20) 로 넓힘. kind 에 `CANCEL_USE`·`EXPIRE`·`REFUND`, 부호 규칙 「EARN·CANCEL_USE 면 +」. reason·ref_type CHECK 는 **commerce.md 5-3 의 값 전부(2a·3단계 것 포함) + 기존 값**의 합집합으로 한 번에(결정 4). ref_type 에 `POINT_SHOP_PURCHASE`·`POINT_CHARGE`·`GB_PARTICIPATION`·`LOT`·`LEDGER`(미회수 상계의 대상 = 적립 줄)·`USER`(가입 포인트 멱등).
- **`point_ledger_lots`**(새): ledger_id, lot_id, amount(≠ 0), PK `(ledger_id, lot_id)`, 색인 `(lot_id)`. 원장 FK 는 삭제 연쇄(보관 기간 파기 때 함께), 묶음 FK 는 막음. 수정 금지 트리거.
- **`point_revoke_shortfalls`**(새): id, user_id, ref_type, ref_id, requested, applied, created_at, 색인 `(user_id, created_at)`.
- **`point_settings`**: `free_valid_days`(365), `restored_lot_valid_days`(30), `expiry_notice_days`(30), `lot_tracking_started_at`(전환 시각 — 불변식의 「전환 이전 줄 제외」) 추가. `nickname_ticket_price` 는 V86 에서 품목으로 옮긴 뒤 지운다(결정 2).
- **회원 삭제 연쇄 제거**(17-2): `point_balances`·`point_ledger` 의 `users` 외래키를 `ON DELETE CASCADE` 에서 기본(지우지 못하게 막음)으로(결정 5). 새 표는 users 에 외래키를 걸지 않는다(3절).
- **전환(D4)**: 잔액이 있는 회원마다 FREE 묶음 하나(remaining = 잔액, expires_at = 마이그레이션 시각 + 1년, source_reason `MIGRATION`), 원장 줄 없음. 묶음 ID 는 SQL 이 Snowflake 를 만들 수 없어 작은 순번(sequence — DB 배치 로그와 같은 방식, Snowflake 와 겹치지 않음).
- **`PointService` 한 길**(부르는 쪽 메서드 모양 유지): `grant`(FREE 묶음, 미회수가 있으면 같은 트랜잭션에서 EARN 다음 `REVOKE_DEBT_OFFSET` — 충전 보너스는 상계 안 함) · `revoke`(무상만, 적립한 그 묶음 먼저 → 다른 무상(소멸 임박순), 0 바닥, 모자라면 debt + shortfall) · `use`(MANDATORY, `PointDeductionPolicy` — 무상 소멸 임박순 → 만든 순, 그다음 유상 만든 순) · `cancelUse`(원래 묶음으로, 만료됐으면 같은 종류 30일 새 묶음) · `expireLot` · `adjust`(무상만 — +는 1년 묶음, −는 무상에서만, 문턱 ±10,000P 초과는 `point.adjust.large` 필요, 최대 ±100,000P) · `restoreContent`(결정 7). 잠금은 지금처럼 잔액 행 `FOR UPDATE`, 묶음은 그 잠금 아래서만 고친다, 업무의 다른 잠금(재고 등) 뒤에 마지막으로.
- **불변식(5-5)**: 회원별·종류별로 원장 합 = 잔액 = 남은 묶음 합, 원장 줄의 묶음 반영 합 = delta(`lot_tracking_started_at` 이후 줄).
- **회원·관리자 API**: `/api/me/points` 에 무상·유상·합계·소멸 예정(가장 가까운 날·양)·미회수, `ticketCount` 실제 값(1-2). 화면은 D13 — 충전을 열기 전에는 「포인트」 하나 + 소멸 예정 + 「미회수 N P — 다음 적립에서 차감돼요」. 관리자 원장에 무상/유상 칸, 회원 상세에 묶음 목록·소멸 예정·미회수.

## 1-1 화면 확정과 서버 세부 (화면 확정 2026-10-26 — 사용자 「추천대로 확정해」)
**화면**(목업 → 확정): user-web `/me/points` — 충전 전에는 「포인트」 하나, 「{날짜} 소멸 예정 N P」(날짜는 사이트 날짜 형식), 미회수 경고 상자 「미회수 N P — 다음 적립에서 차감돼요」, 내역 줄의 「소멸」(회색)·「사용 취소」(+ 만료 묶음이면 「{날짜}까지 쓸 수 있게 돌려드렸어요」), 거르기 전체·적립·차감·소멸. admin `/points` — 원장 「적립(무상)」·「충전(유상)」 칸, 종류 + 사용 취소·소멸·환불, 조정 대화상자(적립만, 1~100,000, 문턱 초과는 `point.adjust.large` — 없으면 칸 오류, 있으면 주의 안내). admin 회원 상세 「등급·포인트」 — 합계·적립·충전, 소멸 예정, 미회수(+ 최근 기록 5건), 포인트 묶음 목록(남은 것만/전체, 10개씩). 정책 화면의 「포인트에는 유효기간이 없어요」 문구는 1-1 에서 고친다.

**API**(목업 모양 그대로):
- `GET /api/me/points` + `freeBalance`·`paidBalance`·`chargeAvailable`(충전 기능 — 3단계 전에는 늘 false)·`expiring {date(YYYY-MM-DD, KST), amount} | null`(소멸일이 지나지 않은 무상 묶음 중 가장 가까운 소멸일과 그날 소멸될 양의 합)·`revokeDebt`.
- `GET /api/me/points/ledger?kind=EARN|USE|REVOKE|CANCEL_USE|EXPIRE` — 줄에 `restoredUntil`(사용 취소·글 복구로 돌려준 양 중 30일 새 묶음이 있으면 그 소멸 시각, 없으면 null).
- 관리자 원장 줄에 `freeDelta`·`paidDelta`, `kind` 거르기 + `CANCEL_USE|EXPIRE|REFUND`, 사유 + 5-3 값. `member-candidates` 에 `freeBalance`·`paidBalance`.
- `POST /api/admin/members/{id}/points` — delta 0 아닌 정수 ±100,000, |delta| > 문턱(`point_settings.adjust_large_threshold`, 기본 10,000, 0 = 모든 조정이 고액)이면 `point.adjust.large` 가 없을 때 403 `POINT_ADJUST_LARGE_REQUIRED`. 무상만, 차감은 무상 잔액까지(0 바닥, 관리자 차감은 미회수를 만들지 않는다). 응답 `balance` = 합계. 감사는 지금 `POINT_ADJUST`(이미 돈 관련 5년)에 `large: true` 를 더한다 — 큰 금액 조정을 따로 찾을 수 있게(행위를 나누지 않는다).
- `GET /api/admin/members/{id}/point-lots?active=true|false&page=0&size=10`(`point.read`, size 1~50) → `{ freeBalance, paidBalance, revokeDebt, expiring, lots: { items, total }, shortfalls(최근 5) }`. 묶음 줄 `lotId, kind, originalAmount, remainingAmount, expiresAt|null, sourceReasonCode, sourceReason(요청 언어), restored, createdAt, expired`. 정렬: 남은 것만 = 차감 순서, 전체 = 최근 받은 순. 미회수 기록 `id, refType, refId, requested, applied, createdAt`. 없는 회원·탈퇴 404 `MEMBER_NOT_FOUND`.

**서버 세부 결정(설계 — 계약 A 를 구현하며 정한 것)**:
- 적립 하나 = 무상 묶음 하나(`free_valid_days` 뒤 소멸), `point_ledger_lots` 에 (적립 줄, 묶음, +양).
- **소멸일이 지난 묶음은 쓰지 않는다**: 사용·회수·차감 조정은 잔액 행을 잠근 뒤 먼저 그 회원의 지난 묶음을 소멸(EXPIRE) 처리하고 남은 묶음에서만 뺀다(배치가 늦어도 지난 포인트가 쓰이지 않는다). 소멸 배치는 남은 것을 메꾼다.
- **미회수 상계**(`REVOKE_DEBT_OFFSET`): 적립 직후 같은 트랜잭션에서 min(빚, 무상 잔액)을 방금 만든 묶음부터(그다음 차감 순서) 뺀다. ref = `LEDGER` + 적립 줄 ID. 충전 보너스(3단계)는 상계하지 않는다.
- **회수 멱등**: 반영 0 이라 원장 줄이 없어도 같은 대상을 두 번 빚으로 올리지 않게 `point_revoke_shortfalls` 에 `reason`(회수 사유) 칸을 더하고 유일 `(reason, ref_type, ref_id)`.
- **글 복구 되돌림(결정 7)**: 그 글의 회수 줄이 뺀 묶음으로 되돌리고(만료면 `restored_lot_valid_days` 새 묶음), 그 글의 미회수 기록을 지우며 빚을 min(빚, 그 기록의 모자란 양 합)만큼 줄인다. 그 기록의 모자란 양 중 이미 상계로 갚은 부분(모자란 양 합 − 줄인 빚)도 되돌림 줄에 더해 새 무상 묶음(`free_valid_days`)으로 준다. 되돌림 줄 하나(`POST_RESTORE`, 합이 0 이면 줄 없음).
- 조정 문턱은 설정값 `point_settings.adjust_large_threshold`(0~100,000, 기본 10,000) — 화면에는 아직 칸이 없다(관리자 정책 화면 칸은 1-3 에서).
- 동결(`POINT_FREEZE`): 적립 건너뜀(지금처럼), 사용 거절(`POINT_FROZEN`), 회수·사용 취소·소멸·조정은 한다.

### 1-1 구현 결과 (2026-10-26 — 커밋 1d52436, develop 병합 fe41910)
- **마이그레이션** `V85__point_extension.sql`(가번호): 계약 A 전부 + 구현하며 더한 것 — 원장 사유-종류 짝 CHECK, 몫 CHECK(합 = delta, 부호 일치, REVOKE 는 유상 0), `point_lots` 정체(누구·종류·양·소멸일·출처) 고정 트리거, `point_revoke_shortfalls.reason` + 유일 `(reason, ref_type, ref_id)`, `point_settings.adjust_large_threshold`, 끝에 회원 알림 종류 CHECK(V84 13종 ∪ `POINTS_EXPIRING`). 전환 묶음 ID 는 sequence `point_lot_migration_id_seq`.
- **배치**: Java `point-expire`(매일 00:10, 분류 MEMBER, (소멸일, ID) 키셋·체크포인트·resumable, 파라미터 `userId`) · `point-expiry-notice`(매일 11:00, MEMBER, 회원당 알림 하나 + `expiry_noticed_at`) · DB `point-invariant-check`(`R__batch_29`, 매일 05:30, SYSTEM, 읽기만 — 어긋나면 FAIL 사유에 회원·줄 ID 앞 5개 → 기존 배치 실패 알림).
- **회원 알림** `POINTS_EXPIRING`(system, 회원이 끌 수 없음·운영자는 이벤트 설정에서 끌 수 있음, 링크 `/me/points`): 「{양}P가 {날짜}부터 소멸돼요」 — 양 = 알림 기간 안 무상 묶음의 남은 양 합, 날짜 = 가장 가까운 소멸일(KST). **회원당 최소 간격**(사용자 확정 2026-10-26): `point_settings.expiry_notice_interval_days`(기본 7, 0~30, 0 = 없음) 안에 이미 받은 회원은 건너뛰고 그동안 들어온 묶음은 다음 알림에 담는다. 간격은 한국 날짜로 세고(1일에 받으면 8일에 다시), 알림 기간보다 길면 min(간격, 알림 일수 − 1)을 쓴다. 마지막 알림 시각은 `point_balances.expiry_notice_sent_at`(조건부로 적어 겹친 실행도 한 번만).
- **API**: 위 「API」 그대로(목업과 다른 점 없음). 관리자 정책 조회에 읽기 전용 `adjustLargeThreshold`(조정 대화상자의 미리 안내 — 저장은 1-3). 오류 코드 `POINT_FROZEN` 403 · `POINT_INSUFFICIENT` 409 · `POINT_ADJUST_LARGE_REQUIRED` 403(문구에 문턱 값). 조정 검사 순서 400 → 404 → 403(고액 거절은 `PERMISSION_DENIED` 감사).
- **화면 연결**: 두 앱의 포인트 목업 제거·실제 호출, `point.adjust.large` featureReady=true(서버·화면 카탈로그), 정책 화면 운영 원칙의 유효기간·0P 바닥 문구 고침, user-web 알림 종류 `POINTS_EXPIRING`(모래시계 아이콘).
- **구현 가정**: ① 운영 지급(+조정)도 미회수를 상계한다(5-4 「운영 지급」), 사용 취소·글 복구 되돌림은 상계하지 않는다. ② V85 이전 회수 줄은 묶음을 몰라 글 복구 때 새 무상 묶음(1년)으로 준다. ③ 복구한 글을 다시 지워도 회수하지 않는다(되돌림 줄이 0 이라 없었던 경우만 다시 회수). ④ 소멸 예정 알림은 꺼진 회원·탈퇴 회원의 묶음도 「알림」으로 표시한다(다음 날 다시 고르지 않게). 정지·동결 회원도 받는다. ⑤ 소멸일이 지났지만 아직 소멸 처리 전인 묶음은 잔액에는 남고 소멸 예정·「남은 것만」 묶음 목록에서는 빠진다.

### 1-1 규칙 → 테스트 (모두 실제 PostgreSQL — `CommerceTestDatabase`)
| 규칙 | 테스트 |
|---|---|
| 차감 순서(무상 소멸 임박 → 만든 순 → 유상 만든 순) | shared `PointDeductionOrderTest` |
| 소멸일 지난 묶음은 쓰지 않고 먼저 소멸 | `PointDeductionOrderTest.소멸일이_지난_묶음은_…` |
| 적립 하나 = 무상 묶음 하나(1년) | `PointEarnRevokeTest.적립_하나는_…` |
| 무상만 회수·유상 불변·0 바닥·미회수 + 기록(P21), 적립 묶음 먼저 | `PointEarnRevokeTest`(회수 4개) |
| 같은 대상 두 번 회수해도 빚 한 번 | `PointEarnRevokeTest.같은_대상을_두_번_…`, `PointSchemaGuardTest.같은_대상은_미회수_기록도_…` |
| 미회수 상계(P21b — EARN +20·OFFSET −20·debt 0·하루 한도 1회) | `PointEarnRevokeTest.미회수가_있으면_…_P21b` 외 2 |
| 동결: 적립 건너뜀·사용 거절 | `PointEarnRevokeTest.동결_중이면_…`, `PointAdjustUseCancelTest.동결_중이면_…`, api `PointFreezeEarnTest` |
| 운영 조정 무상만(P20) — 서비스·API | `PointAdjustUseCancelTest`(조정 3개), admin `PointAdjustHttpTest.유상을_지급할_길이_없다_…`·`차감은_무상_잔액까지만_…` |
| 조정 ±100,000 · 문턱 초과는 `point.adjust.large`(없으면 403, 아무것도 안 바뀜) · 문턱 0 · 감사 large·돈 관련 · 정책 조회의 문턱 | admin `PointAdjustHttpTest` |
| 사용·잔액 부족 거절 | `PointAdjustUseCancelTest` |
| 사용 취소: 원래 묶음 / 만료 묶음은 30일 새 묶음 / 멱등 | `PointAdjustUseCancelTest` |
| 소멸(expireLot) 조건부·멱등 | `PointAdjustUseCancelTest.묶음_소멸은_…` |
| 글 복구 되돌림(결정 7) | `PointRestoreTest`(6개) |
| 원장·묶음 반영 수정 금지, 묶음 정체 고정, 몫·부호·사유 CHECK, 합계 계산 칸 | `PointSchemaGuardTest`, `CommerceTestDatabaseTest` |
| 잔액·원장이 있는 회원 행은 지울 수 없음(결정 5) | `PointSchemaGuardTest.잔액이나_원장이_…`, `PointMigrationConversionTest` |
| 동시 적립·사용에서 음수 불가·불변식·하루 상한 | `PointConcurrencyTest`(16·24 스레드) |
| 전환 뒤 불변식(P22) | `PointMigrationConversionTest`(V85 직전까지 이관한 DB 에 옛 데이터 → V85) |
| 불변식(5-5) — 시험마다 | `PointTestKit.assertInvariants`, testFixtures `PointInvariants` |
| 소멸 배치: 지난 무상만·멱등·밀린 것 전부·동결 회원도·덩어리 실패 격리·실패 지점부터/실패 건만 다시 | `PointExpireJobTest` |
| 소멸 예정 알림: 기간 안만·회원당 하나·다시 안 알림·0 이면 끔·이벤트 끄기·탈퇴 제외·알림 종류 CHECK·회원당 최소 간격(7일·한국 날짜·0 이면 없음·알림 기간보다 길면 줄임·겹친 실행 한 번) | `PointExpiryNoticeJobTest`, api `PointsExpiringNotificationTest` |
| 불변식 검사 배치: 정상 OK·어긋나면 FAIL + ID·등록 | `PointInvariantCheckBatchTest` |
| 카탈로그·번들·cron | `PointBatchCatalogTest` |
| 회원 API: 무상·유상·소멸 예정(KST 날짜 합)·미회수·내역 거르기·restoredUntil | api `MyPointHttpTest` |
| 관리자 API: 원장 free/paid·새 종류·후보·회원 상세 종류·묶음 목록(정렬·쪽·미회수 기록·404/400/403) | admin `PointLedgerAdminHttpTest`, `PointMemberLotsHttpTest`, `PointPermissionDeclarationTest` |

**아직 테스트로 막지 못한 규칙**: ① 큰 금액 조정 **승인 대기**(결정 11 — 4단계, 지금은 권한 검사만). ② 일일 어뷰징 리포트(결정 12 — 4단계, shortfall 표까지만). ③ 실제 pg_cron 등록·실제 시각 실행(시험 DB 에 pg_cron 없음 — 정의 행과 직접 CALL 만). ④ `@Transactional(MANDATORY)` 강제(서비스 시험이 스프링 프록시 없이 조립). ⑤ 댓글·추천 보너스 회수의 묶음·복구 규칙은 글과 같은 코드 길이라 글로만 시험. ⑥ 충전 환불(REFUND) 줄의 실제 모양(3단계). ⑦ 원장 보관 기간 파기·탈퇴 회원 원장 정리(17-3)가 생기면 불변식 검사를 함께 고쳐야 한다(`R__batch_29` 머리말).

## B. 포인트 상점 (commerce.md 7절)
- **`point_shop_items`**: id, code(유일·불변), kind(`NICKNAME_CHANGE_TICKET`·`NICKNAME_COLOR`·`PROFILE_BORDER`·`PROFILE_AVATAR` — 결정 9·14), usage_type(`PERMANENT`|`CONSUMABLE`|`PERIOD` — 변경권은 CONSUMABLE 만, 색·테두리는 PERMANENT|PERIOD, 아바타는 PERMANENT 만), name·name_en·description·description_en, color(`#RRGGBB`, 닉네임 색만)·border_style(팔레트 키, 테두리만)·avatar_id(아바타 카탈로그 ID, 아바타만·유일) — CHECK, price_point(1~100,000), duration_days(PERIOD 만, 1~365), stock(null = 무제한, ≥ 0), sale_start_at·sale_end_at, active, sort_order, created_by·updated_by·created_at·updated_at.
- **아바타 품목**(결정 14): 유료 306종을 V86 이 시드한다 — code `avatar-<id>`, name = 카탈로그 한글 이름, price 1,000, PERMANENT, 재고·기간·판매 기간 없음, sort_order = 카탈로그 순서. 카탈로그(ID·이름·카테고리·순서)는 배포되는 자산 `avatars/manifest.json`(community 와 같은 328종)이고 그림은 두 앱의 `public/avatars/roboyam/<id>.svg`. 무료 22종(카테고리 `basic`·`hobby`)은 품목 행이 없다. 아바타 품목은 관리자가 새로 만들거나 지울 수 없다(가격·이름·순서·판매 여부만, 묶음 변경 가능).
- **`point_shop_purchases`**: id, user_id, item_id, 사본(code·name·kind·usage_type·price_point·duration_days), status(`COMPLETED`|`REFUNDED`), refund_notice_agreed_at(필수), use_ledger_id, refund_ledger_id, refunded_at, refunded_by_operator_id·refund_reason(예외 환불), created_at. 색인 `(user_id, id DESC)`·`(item_id)`.
- **`user_items`**: id, user_id, item_id, purchase_id(유일), usage_type(사본), status(`OWNED`|`USED`|`REVOKED` — 만료는 저장하지 않고 expires_at 으로 계산, 결정 6), starts_at, expires_at(PERIOD 만), first_used_at(환불 판정, 지우지 않음), used_at, revoked_at, created_at. 유일 `(user_id, item_id) WHERE usage_type='PERMANENT' AND status <> 'REVOKED'`. 기간제를 기간 중에 또 사면 앞 줄 끝에서 시작.
- **`user_equipped`**: PK `(user_id, slot)`, slot(`NICKNAME_COLOR`|`PROFILE_BORDER`), user_item_id(유일), item_id·expires_at(사본 — 결정 8), equipped_at. 장착하면 `first_used_at` 을 채운다. 기간이 지나면 읽을 때 해제로 본다.
- **고른 아바타**: `profiles.profile_image`(V2 에 있는 칸, 지금 안 씀)에 `avatar:<id>`. null 이면 `basic-default` 로 그린다. 장착 칸(`user_equipped`)은 쓰지 않는다. 유료 아바타를 고르면 그 `user_items.first_used_at` 을 채운다(환불 판정 — commerce.md 7-3 「프로필로 설정한 적 없음」). 예외 환불로 보유가 없어진 아바타를 쓰고 있었으면 `basic-default` 로 돌아간다.
- **`point_shop_settings`**(1행): refund_days(7, 1~30).
- **구매**(한 트랜잭션, `MoneyRequestGuard` — 멱등 키 + 요청 제한 `POINT_SHOP_PURCHASE` 1분 10번): 자격(로그인·이메일 인증·정상 회원·회원 권한 `pointshop.use`·`RestrictedFeature.POINT_USE` 아님) → 판매 기간·active·영구 중복 → 재고 조건부 차감 → 포인트 사용(사유 `POINT_SHOP_PURCHASE`, 가격은 서버가 품목에서) → 구매·보유. **적립·충전 포인트 모두 쓸 수 있고 적립부터 차감**(결정 9).
- **환불**(D6, 모든 품목 같은 규칙 — 닉네임 변경권 포함): 안 썼고(first_used_at 없음) 구매 후 refund_days 이내면 회원 셀프 환불(요청 제한 `REFUND_REQUEST`), 아이템 REVOKED → 사용 취소(`POINT_SHOP_REFUND`, 원래 묶음) → 재고 복원 → 구매 REFUNDED. 조건 밖은 `pointshop.refund` 예외 환불(감사 — 돈 관련 5년).
- **닉네임 변경권**: `member-mypage-contract.md` 「후속 작업: 닉네임 변경권」의 사용 규칙(`useTicket`, `409 NICKNAME_TICKET_REQUIRED`, 동시 요청에 한 장이 두 번 쓰이지 않음) 그대로. 단 환불·값은 아래 결정 1·2 가 정본.
- **미리보기 필수**(20-1 L6): 색·테두리는 내 닉네임·프로필에 적용한 모습을, 변경권은 내용 설명을.
- **장식 표시**: 글·댓글 작성자 표시에 `user_equipped` + 품목 한 번 붙이기(색을 고치면 산 사람의 색도 바뀐다 — 관리자 화면 안내).
- **0-5 이름 바꾸기**(결정 「이름」): 운영자 권한 `shop.read`·`shop.manage`·`shop.refund` → `pointshop.read`·`pointshop.manage`·`pointshop.refund`, 회원 권한 `shop.use` → `pointshop.use`, 요청 제한 종류 `SHOP_PURCHASE` → `POINT_SHOP_PURCHASE`(설정 행·화면·`util-permission-catalog.ts`·`operator-permissions.md` 함께). 아직 「준비 중」이고 오픈 전이라 지금 바꾼다.

## 패키지 (의존: pointshop → point)
- shared `com.community.point`(기존 확장 — 엔티티 3개·`PointKind`·`PointDeductionPolicy`·`PointService`·`PointLedgerWriter`, 배치 `point.batch.PointExpireJob`·`PointExpiryNoticeJob`) · `com.community.pointshop`(엔티티 5개, `PointShopCatalogService`·`PointShopPurchaseService`·`PointShopRefundService`·`UserItemService`·`ShopDecorationResolver`·`NicknameTicketService`).
- api: `pointshop.controller`(품목·구매·내 아이템·장착·환불), 포인트 응답, 프로필 변경권, 작성자 장식. admin: `pointshop.controller`(품목·구매 내역·예외 환불·설정), 포인트 원장·회원 상세·큰 금액 조정.
- 감사 행위와 보관 분류: 품목 만들기·고치기·설정 = 일반 운영, 예외 환불·큰 금액 조정 = 돈 관련.

## 세션 정리 결정 (2026-10-26 — 사용자 「추천대로 진행해」)
1. **닉네임 변경권 환불**: commerce D6(안 썼고 7일 안이면 환불)을 따른다. `member-mypage-contract.md` 의 「환불 불가」는 이 결정으로 바뀐다.
2. **변경권 값의 출처**: 포인트 상점 품목 가격 하나. `point_settings.nickname_ticket_price` 는 품목으로 옮기고 지우며, 관리자 포인트 정책 화면의 「상점」 구획을 없앤다.
3. **구매 원장 사유**: 모든 구매가 `POINT_SHOP_PURCHASE`(환불 `POINT_SHOP_REFUND`). 기존 `TICKET_PURCHASE` 는 CHECK 에만 남기고 쓰지 않는다.
4. **원장 CHECK·칸 길이**: commerce.md 5-3 의 값 전부를 지금 한 번에 넣고 `reason` 30자·`ref_type` 20자로 넓힌다(단계마다 CHECK 를 다시 만들면 합칠 때 빠뜨린다 — V80 주석).
5. **회원 삭제 연쇄**: 잔액·원장의 외래키를 「지우지 못하게 막음」으로. 새 표는 users 외래키 없음.
6. **보유 아이템 만료 상태**: 저장하지 않고 expires_at 으로 계산(commerce.md 7-1 의 `EXPIRED` 를 뺀다).
7. **글 복구 되돌림(`POST_RESTORE`)과 묶음**: 회수 때 빼 갔던 묶음으로 되돌리고(만료됐으면 30일 새 묶음), 그 글 때문에 생긴 미회수(debt·shortfall)도 함께 지운다.
8. **장식 표시**: `user_equipped` 에 품목 번호·소멸일 사본을 두어 표시 때 표 하나만 붙인다.
9. **품목 종류와 쓸 수 있는 포인트**: 1단계 품목은 닉네임 변경권·닉네임 색·프로필 테두리·프로필 아바타(결정 14). **포인트 상점 품목은 전부(변경권 포함) 적립·충전 포인트 모두 쓸 수 있고 적립부터 차감**(commerce.md D2 를 「꾸미기 아이템」에서 「포인트 상점 품목 전부」로 넓혔다). 테두리를 어디에 보일지는 화면 목업에서 정한다.
10. **6-2 적립 규칙·가입 포인트**: 1-3 으로 따로(1-1 다음).
11. **큰 금액 조정 승인 대기**(13-2): 1단계는 권한 검사만(문턱을 넘는데 권한이 없으면 거절). 승인 대기는 4단계 — 「아직 테스트로 막지 못한 규칙」으로 적는다.
12. **일일 어뷰징 리포트**: 4단계. 1단계는 shortfall 표까지.
13. **원장 줄 금액 `delta`**: 지금처럼 int(한 줄 금액은 그 범위), 합계 칸만 bigint.
14. **프로필 아바타 추가**(2026-10-26 사용자 지시): community 의 아바타 328종(무료 22 · 유료 306 각 1,000P 영구)을 그대로 가져와 1-2 에 넣는다. 고르기 화면, 프로필·글 보기 머리의 아바타 표시(테두리는 아바타를 감싼다), 포인트 상점 「아바타」 탭, 관리자 묶음 가격·판매 변경. 아바타를 고르지 않은 회원도 이니셜 대신 `basic-default` 아바타로 보인다. 올린 이미지(사진 올리기)는 1단계에 없다.
- **이름**: 「상점」이라고만 쓰지 않는다 — **포인트 상점**(포인트로 사는 디지털 아이템)과 **공동구매**(현금으로 사는 실물)로 부른다. **둘을 한 메뉴로 묶지 않는다**(사용자 2026-10-26). 표·패키지·권한·사유는 `point_shop`·`pointshop`·`POINT_SHOP_*`.

## 1-2 화면 목업 (2026-10-26 — **사용자 확정**(「진행해」): 아래 제안·가정·아바타를 그대로 확정. 서버 없음)
화면은 메모리 목업(`// MOCK:`·`// REAL:` 나란히)으로 만들었다. 아래는 목업이 기대는 가정이고, 확정되면 서버가 이 모양으로 만든다.

**제안한 결정(미확정 3개에 대한 답)**
- **닉네임 색 대비**: 운영자는 `#RRGGBB` 하나만 정한다. 그릴 때 user-web 의 기존 규칙 `UtilColor.cssPair`(홈 가젯 색과 같다 — 라이트 `#ffffff`·다크 `#202125` 바탕에서 4.5:1 이 안 되면 **명도만** 조정, 색상 유지)로 `light-dark()` 두 값을 만든다. 저장값은 원색 그대로. 관리자 미리보기는 라이트·다크 두 바탕을 나란히 보이고, 보정되면 「밝은/어두운 화면에서 조금 바꿔 보여요」 표시(저장은 막지 않는다).
- **프로필 테두리 팔레트**(`border_style` 키, 코드 고정 — 서버 CHECK 와 같은 목록): `GOLD` 금빛 · `SILVER` 은빛 · `ROSE` 장미 · `OCEAN` 바다 · `FOREST` 숲 · `NIGHT` 밤하늘 · `RAINBOW` 무지개. 이니셜 동그라미 둘레의 그라데이션 고리(바탕색 틈 하나) — 두 테마에서 같은 모양.
- **테두리를 보일 자리**: 공개 프로필 머리(`/users/{id}`의 큰 동그라미)와 **글 보기 머리의 작성자**(작은 이니셜 동그라미를 새로 둔다). 목록·댓글·쪽지는 줄이 좁아 **닉네임 색만**. 닉네임 색은 작성자 표시가 있는 모든 곳(`PostAuthorLabel`)과 프로필 닉네임.
- **환불 기한 바꾸기**: 이미 산 구매도 구매 시점이 아니라 **지금 설정값**으로 판정한다.
- **미리보기 없는 품목(변경권)**: 「무엇을 할 수 있나 · 언제 쓰나(가입 뒤 1번 무료 변경을 쓴 다음) · 쓰는 곳(프로필 설정) · 기간 없음 · 환불 조건」 다섯 줄을 상세에 늘 적는다.

**회원 화면(user-web)** — 메뉴 이름 「포인트 상점」(서비스 용어 `POINT_SHOP`, 헤더 프로필 메뉴·햄버거). 공동구매 줄과 따로.
- `/point-shop` 목록(종류 거르기: 전체·닉네임 색·프로필 테두리·변경권) · `/point-shop/{id}` 상세(미리보기·구매) · `/me/items` 내 아이템(장착·해제·남은 기간·환불). 모두 로그인 필요(`auth`).
- 구매 확인 대화상자: 가격 · 「지금 N P → 산 뒤 M P」(충전을 열기 전이라 「포인트」 하나 — D13) · 「적립 포인트부터 빠져요」 한 줄 · 고지 체크(「사용(장착)하면 환불할 수 없어요 · {refundDays}일 안에 쓰지 않으면 환불할 수 있어요」, 안 고르면 [구매] 꺼짐) · 기간제는 「산 순간부터 N일」(보유 중이면 「지금 기간 끝(날짜)에 이어 붙어요」).
- 프로필 설정: 무료 1번을 썼으면 변경권 보유 수 · 「변경권 1장을 써서 바꾸기」 확인 → `useTicket: true` · 0장이면 칸 잠금 + 「포인트 상점에서 변경권 사기(가격)」 링크.
- 아바타(결정 14): 프로필 설정에 「프로필 아바타」 구획(지금 아바타 + [바꾸기] → 고르기 — 카테고리 칩 + 격자, 무료 + 내가 산 것만, 누르면 바로 저장. 환불 기한 안의 유료 아바타는 「고르면 환불할 수 없어요」 한 번 묻는다, 「더 많은 아바타는 포인트 상점에서」 링크) · 포인트 상점 거르기에 「아바타」 탭(유료 306, 카테고리 칩 + 격자, 보유·판매 중단 표시, 누르면 크게 미리보기(내 닉네임·장착 테두리와 함께) + 구매 확인 → 「바로 프로필로 쓰기」) · 내 아이템에 「아바타」 구획(산 것, 사용 중, 프로필로 쓰기, 환불) · 공개 프로필 머리·글 보기 머리의 동그라미가 아바타(테두리는 그 둘레).

**API 후보(회원, api 서버)** — 응답은 공통 래퍼의 `data`.
- `GET /api/point-shop/items?kind=` → `{ refundDays, balance, items: PointShopItemView[] }` · `GET /api/point-shop/items/{id}` → `PointShopItemView`.
  `PointShopItemView` = id, code, kind, usageType, name, nameEn, description, descriptionEn, color, borderStyle, price, durationDays, stock(남은 수, null = 무제한), saleStartAt, saleEndAt, saleState(`ON_SALE`|`UPCOMING`|`ENDED`|`SOLD_OUT`), mine{ owned(영구 보유), count(안 쓴 소모형 장수), periodEndsAt(기간제 지금 끝 — 이어 붙일 자리) }.
- `POST /api/point-shop/purchases` `{ itemId, agreeRefundNotice: true }` + `Idempotency-Key` → `{ purchaseId, userItemId, balance, item }`. 오류: `401` · `403 EMAIL_NOT_VERIFIED`·`ACCOUNT_RESTRICTED`·`MEMBER_RESTRICTED`(POINT_FREEZE)·`PERMISSION_DENIED`(`pointshop.use`) · `409 POINT_SHOP_NOT_ON_SALE`·`POINT_SHOP_SOLD_OUT`·`POINT_SHOP_ALREADY_OWNED`·`POINT_INSUFFICIENT` · `400 VALIDATION fields.agreeRefundNotice` · `429 RATE_LIMITED`(`Retry-After`).
- `GET /api/me/items` → `{ refundDays, items: MyItemView[] }` — 한 줄 = `user_items` 한 줄: id, itemId, purchaseId, kind, usageType, name, nameEn, color, borderStyle, status(`OWNED`|`USED`|`REVOKED`), expired(서버 계산), equipped, startsAt, expiresAt, firstUsedAt, purchasedAt, pricePaid, refundable(서버 판정), refundUntil. 화면은 소모형을 품목별 한 카드(장수)로 묶는다.
- `PUT /api/me/equipped/{slot}` `{ userItemId }` · `DELETE /api/me/equipped/{slot}` → `{ nicknameColor, profileBorder }`(각 `{ userItemId, itemId, color|borderStyle, expiresAt } | null`). 오류 `409 USER_ITEM_EXPIRED`·`USER_ITEM_NOT_EQUIPPABLE`.
- `POST /api/me/items/{id}/refund` + `Idempotency-Key` → `{ refundedPoints, balance }`. 오류 `409 POINT_SHOP_REFUND_NOT_ALLOWED`(썼거나 기한 지남 — 화면은 이유를 다시 읽는다) · `429 RATE_LIMITED`(`REFUND_REQUEST`).
- `PUT /api/me/profile` 에 `useTicket`(선택) · `MyProfile.nicknameChange` 에 `ticketCount`·`ticketPrice` · `409 NICKNAME_TICKET_REQUIRED`(최상위 `ticketCount`·`ticketPrice`) — `member-mypage-contract.md` 「후속 작업」 그대로. `GET /api/me/points` 의 `ticketCount` 실제 값.
- 작성자 장식: `PostAuthor`(글·댓글·목록·쪽지 상대)와 공개 프로필에 `decoration: { nicknameColor: '#RRGGBB'|null, profileBorder: 키|null } | null` — 만료된 장착은 서버가 null 로.
- **아바타**(결정 14 — 모양은 community `docs/api-avatar.md` 를 로보얌에 맞춤):
  - `GET /api/avatars` → `{ balance, selectedId, refundDays, categories[{ id, free, count }], items[{ id, name, category, free, price, itemId, forSale, owned, selected, refundable, url }] }` — 328줄, 카탈로그 순서. 아바타 306줄은 품목 목록(`/api/point-shop/items`)에 섞지 않는다(응답이 커진다). 카테고리 이름은 화면 i18n 키(`avatar.category.<id>`), 아바타 이름은 카탈로그 한글 이름(영문 없음 — 영어 화면도 한글).
  - 사기는 `POST /api/point-shop/purchases { itemId }` 그대로. 사도 프로필은 바뀌지 않는다(화면이 「바로 쓰기」로 이어 부른다).
  - `PUT /api/me/profile/avatar { avatarId }` → `MyProfile`(`avatarUrl` 포함). 무료는 누구나(사업자 전용 계정 포함), 유료는 가진 것만. 오류 `400 VALIDATION fields.avatarId` · `404 AVATAR_NOT_FOUND` · `409 AVATAR_NOT_OWNED` · `403 EMAIL_NOT_VERIFIED`·`ACCOUNT_RESTRICTED`. 같은 것을 다시 골라도 200.
  - 표시: `MyProfile`·공개 프로필·`PostAuthor`(회원)에 `avatarUrl` — 서버가 늘 채운다(고르지 않았거나 카탈로그에 없으면 `basic-default`), 비회원은 null(이니셜). 화면 대체 이미지도 `basic-default`. 그림은 `<img>` 로만 그린다(`v-html`·인라인 SVG 금지), 틀 안에서 `object-fit: contain`(284종이 가로로 조금 넓다).

**관리자 화면(admin-web)** — 일반 메뉴 묶음 「포인트 상점」(포인트 묶음 다음, 공동구매와 따로): 품목 관리 `/point-shop/items` · 구매 내역 `/point-shop/purchases` · 포인트 상점 설정 `/point-shop/settings`. 세 화면 모두 조회 `pointshop.read`, 화면 안에서 저장 `pointshop.manage`, 예외 환불 `pointshop.refund`. 포인트 정책 화면의 「상점」 구획은 없애고 안내 한 줄 + 품목 관리 링크(값 `nicknameTicketPrice` 는 서버가 칸을 지울 때까지 그대로 실어 보낸다).
- 품목 편집: 종류(만든 뒤 못 바꿈)·코드(만든 뒤 못 바꿈, 영소문자·숫자·하이픈 3~40)·사용 방식(변경권은 소모형만, 색·테두리는 영구·기간제)·이름·영문명·설명·영문 설명·색(닉네임 색)·테두리 키(테두리)·가격 1~100,000·기간 1~365(기간제)·재고(빈칸 = 무제한, 0 이상)·판매 시작·끝·순서·사용 여부, 오른쪽 미리보기(라이트·다크). 색·테두리를 고치면 「산 사람의 닉네임 색·테두리도 바로 바뀌어요(가격·이름은 산 때 그대로)」 경고.
- 구매 내역: 기간·상태(완료·환불)·종류·회원 거르기, 한 줄 = 구매 시각·회원·품목(산 때 이름·가격)·사용 여부·셀프 환불 가능 여부·상태. [예외 환불](`pointshop.refund`) → 사유 필수(5~200자) 확인 → 감사(돈 관련 5년).
- 아바타 탭(품목 관리 화면 안): 카테고리별 요약(전체·판매 중·중단·가격 범위, 무료 두 카테고리는 「무료」) · 묶음 가격(범위 `ALL`|`CATEGORIES`|`ITEMS`, 1~100,000) · 묶음 판매 켜기/끄기 · 격자에서 골라 묶기, 확인창에 「N개를 바꿔요 · 이미 산 사람의 가격은 그대로」. 아바타 품목은 [품목 추가]의 종류에 없고 지울 수 없다(한 줄 편집은 이름·가격·순서·판매 여부만). 묶음 변경은 감사(일반 운영). API 후보 `GET /api/admin/point-shop/avatars/summary` · `PUT /api/admin/point-shop/avatars/price { scope, categories?, itemIds?, price }` · `POST /api/admin/point-shop/avatars/sale { scope, …, active }` → `{ matched, changed }`, 목록은 `GET /api/admin/point-shop/items?kind=PROFILE_AVATAR`·`excludeKind=PROFILE_AVATAR`.
- API 후보(admin 서버): `GET/POST /api/admin/point-shop/items` · `PUT /api/admin/point-shop/items/{id}` · `GET /api/admin/point-shop/purchases?from&to&status&kind&q&page&size` · `POST /api/admin/point-shop/purchases/{id}/refund { reason }` · `GET/PUT /api/admin/point-shop/settings { refundDays }`.

**목업 가정**: 목업 잔액 12,000P(실제 포인트 요약과 따로 움직인다) · 품목 8개(변경권 1,000P · 영구 색 장미·바다·레몬 · 기간제 색 단풍(30일, 곧 판매 끝) · 영구 테두리 GOLD · 한정 재고 RAINBOW(남은 1개) · 판매 예정 NIGHT — 두 앱 목업이 같은 번호·값) · `?mock=` 스위치로 실패·품절·예정·잔액 부족을 본다(각 목업 파일 머리 주석) · 목업은 장착한 내 장식을 **내 글·댓글·프로필**에 겹쳐 그린다(서버 `decoration` 이 붙으면 「방금 바꾼 것 겹치기」로 남는다) · 변경권 사용 저장(`useTicket`)은 목업이 받는다(실제 서버는 아직 `409 NICKNAME_CHANGE_LIMIT`).

**목업 결과 (2026-10-26 — 두 앱 `typecheck` 통과, 브라우저 확인은 개발자 몫)**
- user-web: `pages/point-shop/{index,[id]}.vue`·`pages/me/items.vue`, `composables/pointshop/`(API 둘 + `mock/pointshop-mock.ts` + 내 장식 겹치기 `useMyDecoration`), `components/pointshop/`(`ProfileBorderAvatar` 등), `utils/util-point-shop*.ts`(옛 `util-shop.ts` 지움), 서비스 용어 `ITEM_SHOP` → `POINT_SHOP`. 고친 곳: `PostAuthorLabel`(닉네임 색)·글 보기 머리(작은 동그라미)·공개 프로필 머리·프로필 설정(변경권 `FREE`/`TICKET`/`NEED_TICKET`)·헤더·햄버거·`/me` 바로가기.
- admin-web: `pages/point-shop/{items,purchases,settings}.vue`, `composables/pointshop/`(API + `mock/pointshop-admin-mock.ts`), `components/pointshop/`(편집 서랍·미리보기·예외 환불 대화상자), `utils/util-point-shop*.ts`·`utils/util-color.ts`(user-web 사본), 메뉴 묶음 `pointShop`, 포인트 정책의 「상점」 구획 없앰.
- 계약 위에서 더 정한 것(서버 때 반영할 후보): `404 POINT_SHOP_ITEM_NOT_FOUND`·`USER_ITEM_NOT_FOUND`·`POINT_SHOP_PURCHASE_NOT_FOUND`, `409 POINT_SHOP_ALREADY_REFUNDED`(예외 환불), 관리자 구매 줄 `selfRefund{allowed, until, blockedBy}`·`refund{kind, refundedAt, operatorName, reason, refundedPoints}`·`firstUsedAt`, 설정 응답 `defaultRefundDays`·`updatedAt`·`updatedBy`, 품목 순서 0 이상·재고 상한 없음. 관리자 판매 상태는 화면 계산(회원 응답만 `saleState`). 회원 상품 상세는 잔액·`refundDays` 때문에 목록도 같이 부른다(서버가 상세에 실어 주면 하나로).
- 서버 단계에 할 일로 남긴 것: `useApiClient` 에 요청 헤더 칸이 없어 `Idempotency-Key` 를 아직 못 보낸다(REAL 줄에 모양만) · 메뉴 내장 주소 `MY_SHOP("/me/shop")`(server `MenuBuiltin`·admin `util-menu.ts`)을 `/point-shop` 으로 · 권한 카탈로그 `shop.*` → `pointshop.*` 와 `AdminScreenRegistry` 세 줄(그 전까지 관리자 메뉴는 최고 관리자에게만 보인다) · 멱등 키 `crypto.randomUUID()` 는 보안 문맥(https·localhost)에서만 된다.
- 아바타(결정 14): 그림 328개를 두 앱 `public/avatars/roboyam/` 에(community 에서 그대로 — 스크립트·외부 참조 없음 확인), 카탈로그 사본을 각 앱 목업 폴더 `avatar-manifest.json` 에 두었다(서버 단계에 `server/shared` 자원으로 옮기고 목업 사본은 지운다). user-web: `composables/avatar/`(API + 목업 — 포인트 상점 목업과 같은 메모리 상태), `components/avatar/`(카테고리 칩·격자), 공용 구매 대화상자 `PointShopPurchaseDialog`·`usePointShopPurchase`, 프로필 설정 「프로필 아바타」 구획(바로 저장 — 닉네임 저장 줄과 따로), 포인트 상점 「아바타」 탭(`?kind=PROFILE_AVATAR`), 내 아이템 아바타 구획, 동그라미가 아바타인 곳: 공개 프로필·글 보기 머리·`/me`. admin-web: 품목 관리 「아바타」 탭(`?tab=avatars` — 카테고리 요약·묶음 가격·묶음 판매·격자 고르기), 아바타 한 줄 편집(이름·영문명·가격·순서·사용 여부만), 구매 내역의 아바타 거르기·썸네일.
- 아바타에서 더 정한 것: `MyItemView` 에 `inUse`·`avatarId`·`avatarUrl`, `PointShopItemView` 에 `avatarId`·`avatarUrl`, 관리자 목록 줄 `category`(이름은 화면 i18n — `categoryName`·`limits.price` 없음, 가격 범위는 화면 상수 1~100,000).
- 목업 한계: 변경권으로 바꾼 닉네임·내 장식·고른 아바타는 목업 안에만 있고 새로고침하면 처음으로 돌아간다.

## 1-2 정지 중 환불 신청 (2026-10-26 — 사용자 「정지 중인데 마음대로 환불되면 안 된다, 운영자 수락」 → 「환불 신청 → 운영자 수락」 선택)
- **정지·영구 정지 중**에는 셀프 환불이 바로 처리되지 않는다. 같은 `POST /api/me/items/{id}/refund`(멱등 키·`REFUND_REQUEST` 제한 그대로)가 조건(안 씀 + 기한 안)을 **신청 순간**에 판정해 맞으면 **환불 신청**(`PENDING`)을 만들고 `202` `{ requestId, status: 'PENDING', requestedAt }` 을 준다(조건 밖이면 지금처럼 409). 판정은 신청 시각으로 고정 — 처리를 기다리는 동안 기한이 지나도 된다.
- 신청이 `PENDING` 인 동안: 그 아이템은 장착·아바타 고르기·셀프 환불 불가 `409 POINT_SHOP_REFUND_PENDING`(써 버리면 조건이 깨진다). 같은 구매에 대기 신청은 하나(DB 유일).
- 운영자(`pointshop.refund`) **수락**: 기존 환불과 같은 처리(아이템 REVOKED → 원래 묶음으로 사용 취소 → 재고 복원 → 구매 REFUNDED, 환불 종류 `REQUEST`, 처리자 기록) · **거절**: 사유 필수(5~200자), 아이템은 다시 쓸 수 있다. 거절된 구매는 정지 중 다시 신청할 수 없다(`409 POINT_SHOP_REFUND_REJECTED`). 정지가 풀리면 기한 안에서 보통 셀프 환불은 된다.
- 결과는 회원 알림(수락·거절 — 거절은 사유 포함), 새 신청은 운영자 알림(닉네임 그림 심사 대기와 같은 방식). 감사: 수락 = `POINT_SHOP_REFUND`(돈 관련 5년), 거절 = 돈 관련.
- **구현(2026-10-26)**: `V87__point_shop_refund_request.sql` — 표 `point_shop_refund_requests`(대기 하나 유일 색인 `ux_point_shop_refund_requests_pending`, 결정 칸 조합 CHECK·결정은 한 번 트리거), 구매에 `refund_kind`(`SELF|EXCEPTION|REQUEST` — 기존 환불 줄은 운영자 있으면 EXCEPTION), 회원 알림 종류 CHECK(V85 ∪ `POINT_SHOP_REFUND_APPROVED|REJECTED`). 운영자 이벤트 `POINT_SHOP_REFUND_REQUESTED`(받는 사람 `pointshop.refund` 운영자, 5분 안 연속 신청은 한 건, 링크 `/point-shop/purchases?refundRequest=PENDING`)는 코드가 원천. API: 회원 `POST /api/me/items/{id}/refund` 202 `{requestId, status, requestedAt}`(정지 중) · `GET /api/me/items` 에 `refundNeedsApproval`·줄 `refundRequest{id,status,requestedAt,decidedAt,rejectReason}` · 관리자 구매 내역 `refundRequest=PENDING|APPROVED|REJECTED` 거르기·줄 `refundRequest` · `POST /api/admin/point-shop/refund-requests/{id}/approve|reject` → `{requestId, purchaseId, status, refundedPoints, decidedAt}`. 오류 `409 POINT_SHOP_REFUND_PENDING|REJECTED|REQUEST_NOT_PENDING`·`404 POINT_SHOP_REFUND_REQUEST_NOT_FOUND`. 감사: 수락 `POINT_SHOP_REFUND`(kind REQUEST)·거절 `POINT_SHOP_REFUND_REJECT`(돈 관련). **구현하며 정한 것**: 대기 중인 구매는 예외 환불도 409(신청은 수락·거절로 끝낸다) · 대기 중인 변경권은 장수에서 빠지고 쓰이지 않는다 · 관리자 줄의 `selfRefund` 는 조건만, 대기 여부는 `refundRequest` 로 · 잠금 순서 구매 → 신청 → 보유 줄 → 프로필 → 재고 → 잔액 · 관리자 화면은 신청 결정을 한 번에 하나씩.
- 마이그레이션은 새 번호 `V87`(가번호 — V86 이 로컬 DB 에 적용됐을 수 있어 고치지 않는다): 표 `point_shop_refund_requests`, 회원 알림 종류 CHECK 를 최신 목록과의 합집합으로 다시 만든다.
- 화면: 내 아이템 — 정지 중이면 [환불] 대신 [환불 신청](확인창 「운영자가 확인한 뒤 환불돼요 · 기다리는 동안 이 아이템은 쓸 수 없어요」), 대기 줄 「환불 신청함 · 운영자 확인 기다리는 중」, 거절 줄 「환불 신청이 거절됐어요: 사유」. 관리자 구매 내역 — 거르기 「환불 신청 대기」, 줄에 신청 시각 · [수락]·[거절(사유)].

## 1-2 서버 계획 (2026-10-26 — 1-1 커밋 1d52436 위에서 만들고 1-1 다음에 develop 병합)
- 순서: ① shared — V86·엔티티·서비스·아바타 카탈로그·0-5 이름 바꾸기·규칙 시험 → ② api ∥ admin 컨트롤러·HTTP 시험 → ③ 두 앱 목업을 실제 호출로 → ④ 이 문서 「1-2 구현 결과」·「규칙 → 테스트」.
- 잠금 순서(1-1 규칙): 업무 잠금(품목 재고 조건부 UPDATE · 보유 줄 `FOR UPDATE` · 프로필 행 `FOR UPDATE`) 먼저, `PointService.use`/`cancelUse` 가 마지막(잔액 행 잠금).
- 멱등·요청 제한은 api 의 `MoneyRequestGuard`(구매 `POINT_SHOP_PURCHASE` 1분 10번, 환불 `REFUND_REQUEST` 3번). 같은 키 재요청은 처음 응답 그대로.
- 아바타 카탈로그 원본 `server/shared/src/main/resources/avatars/manifest.json`(community 와 같은 파일). 시험이 지킨다: 유료 ID 집합 = 시드한 아바타 품목 · 두 앱 `public/avatars/roboyam/*.svg` = 카탈로그 328.
- 꼭 넣을 시험(사용자 지시): 마지막 재고 둘이 동시에 사기 · 같은 멱등 키로 다시 사기 · 영구 중복 구매 막기 · 장착(아바타는 고르기) 뒤 환불 불가 · 기한 지나면 환불 불가 · 기간제 이어 붙이기 · 변경권 한 장이 두 번 쓰이지 않음. 모두 실제 DB(`CommerceTestDatabase`).

### 1-2 구현 결과 (2026-10-26 — 커밋 1421b3a, develop 병합 b51cd9b)
- **마이그레이션** `V86__point_shop.sql`(가번호 — V85 다음): B절 표 5개 + CHECK(종류별 칸 조합·`#RRGGBB`·팔레트 키 7개·가격 1~100,000·기간 1~365 PERIOD 만·재고 ≥ 0·판매 끝 > 시작·refund_days 1~30·아바타 = PERMANENT·`avatar_id` 필수·유일·`code = 'avatar-'||avatar_id`) · 영구 보유 부분 유일 색인 · 트리거(품목 code·kind·avatar_id 불변·아바타 품목 삭제 불가 / 구매 사본 불변·REFUNDED 되돌림 불가 / 보유 줄 정체·기간 고정·`first_used_at` 지울 수 없음·상태는 앞으로만) · users 외래키 없음. 시드: 변경권 `nickname-change`(그때의 `nickname_ticket_price`) · 유료 아바타 306 · 설정 1행. 시드 ID 는 기존 V30·V53·V54 처럼 SQL 이 만드는 Snowflake(마이그레이션 노드 1023). `point_settings.nickname_ticket_price` 를 지우고 그 칸을 함께 묶던 `ck_point_settings_other` 를 남은 칸으로 다시 만들었다. **0-5 이름 바꾸기**: `request_rate_limits`·`idempotency_requests` 의 `SHOP_PURCHASE` → `POINT_SHOP_PURCHASE`(한도 그대로, 옛 카운터 줄 삭제) · 권한 `shop.*` → `pointshop.*`(역할에 준 것 옮김, featureReady 켬) · 회원 `shop.use` → `pointshop.use` · 메뉴 내장 `MY_SHOP` 주소 `/me/shop` → `/point-shop`(matchPrefix true — 주소는 코드에만 있다).
- **shared** `com.community.pointshop`: 엔티티 5개(읽기 전용 매핑) + JDBC 저장소 4개(조건부 UPDATE·포인트와 같은 트랜잭션 — 1-1 `PointLedgerWriter` 와 같은 이유), 서비스 `PointShopCatalogService`·`PointShopPurchaseService`·`PointShopRefundService`·`UserItemService`·`NicknameTicketService`·`ProfileAvatarService`·`ShopDecorationResolver`·`AvatarCatalog`(원본 `resources/avatars/manifest.json`). 감사 행위: 일반 운영 `POINT_SHOP_ITEM_CREATE|UPDATE`(바뀐 칸만)·`POINT_SHOP_AVATAR_PRICE|SALE`·`POINT_SHOP_SETTINGS_UPDATE`, 돈 관련 5년 `POINT_SHOP_REFUND`. 보안 설정에 `GET /api/avatars` 비로그인 허용.
- **api**: `PointShopController`·`MyItemController`·`AvatarController`·`MemberPointShopService`, 구매·환불은 `MoneyRequestGuard`(같은 키 재요청 = 처음 `data` + `Idempotent-Replayed: true`). 프로필 `useTicket`·`ticketCount`·`ticketPrice`·`avatarUrl`·`PUT /api/me/profile/avatar`(`NICKNAME_CHANGE_LIMIT` 은 없어지고 `NICKNAME_TICKET_REQUIRED`). 작성자 장식: `AuthorViews.members()` 가 프로필 + `ShopDecorationResolver.resolve` 를 목록마다 한 번(N+1 없음) — 글 목록·보기·댓글·토론·고정 요청·검색·홈·공개 프로필·쪽지 상대.
- **admin**: `PointShopAdminController`·`PointShopAdminService`(모든 핸들러 `@RequirePermission pointshop.*`), `AdminScreenRegistry` 세 화면, 회원 상세 `nicknameTicketCount` 실제 값.
- **화면 연결**: 두 앱의 포인트 상점·아바타 목업과 카탈로그 사본을 지우고 실제 호출. user-web `useApiClient` 에 요청 머리글 칸(`headers`) + `UtilIdempotency`(randomUUID 가 없는 문맥의 대체 생성), 「방금 바꾼 장식 겹치기」 `useMyDecoration` + 내 장착·아바타를 한 번 받는 `useMyDecorationSync`, 쪽지에서도 닉네임 색. admin-web 권한 카탈로그·커머스 설정 요청 종류·메뉴 내장 주소·포인트 정책 13칸(변경권 가격 칸 없음)·회원 상세 변경권 수.
- **구현하며 정한 것**: ① 환불 잠금 순서 = 구매 → 보유 줄 → 프로필 → 품목(재고 복원) → 잔액(`cancelUse` 마지막). ② 셀프 환불에서 이미 환불이면 `409 POINT_SHOP_ALREADY_REFUNDED`, `REFUND_NOT_ALLOWED` 는 최상위 `blockedBy`(USED|EXPIRED). ③ 기간제 이어 산 줄은 앞 줄이 끝나도 장착을 저절로 넘겨받지 않는다(다시 장착 — 미리 장착하면 그때 사용). 앞 줄을 환불해도 뒤 줄 시작은 당기지 않는다. ④ **정지·영구 정지 중에는 장착·해제 모두 `403 ACCOUNT_RESTRICTED`**(사용자 2026-10-26 — 정지는 이용 제한. 프로필 수정·아바타 고르기와 같다). 목록은 읽고 지금 장착한 것은 그대로 보인다. 막힌 장착은 사용이 아니라 풀린 뒤 환불 판정에 영향이 없다. 화면(내 아이템)은 안내 + 단추를 끈다. ⑤ 비로그인 아바타 목록은 잔액 0·보유·고름·환불 가능 false. ⑥ 변경권 값 = 파는 변경권 품목 중 순서·ID 가 가장 앞인 것. ⑦ 관리자 품목 검사: 이름 1~40·설명 1~500(필수)·코드 영소문자·숫자·하이픈 3~40, `avatar-` 로 시작 불가. 모르는 `kind`·`usageType` 은 `400 fields.kind|usageType`. 아바타 묶음에 없는 품목이 섞이면 404, 아바타 아닌 품목은 400. ⑧ 형식이 틀린 ID 는 404. ⑨ 변경권으로 바꾼 것도 `nickname_change_count` 에 센다(`allowed` 는 무료 기준).

### 1-2 규칙 → 테스트 (모두 실제 PostgreSQL — `CommerceTestDatabase`)
| 규칙 | 테스트 |
|---|---|
| **마지막 재고를 둘이 동시에 사기**(하나만·재고 0·불변식) | shared `PointShopPurchaseTest.마지막_재고_하나를_둘이_동시에_…` |
| **같은 멱등 키로 다시 사기**(처음 응답·포인트 한 번·구매 한 줄) · 키 없음·다른 몸·실패 뒤 재시도·429 | api `PointShopPurchaseHttpTest`(5개), 환불 재요청 `MyItemHttpTest.안_쓴_아이템은_환불되고_같은_키로_…` |
| **영구 아이템 중복 구매 막기**(서비스·동시 두 번·부분 유일 색인)·환불하면 다시 살 수 있음 | `PointShopPurchaseTest`(3개), `PointShopSchemaTest.영구_아이템은_…` |
| **장착 뒤 환불 불가**(풀어도) · 아바타 고른 뒤 환불 불가 · 쓴 변경권 | `PointShopRefundTest`(3개), api `MyItemHttpTest.장착하면_…`, `AvatarHttpTest.유료는_가진_것만_…` |
| **기한(7일) 지나면 환불 불가** · 기한을 바꾸면 지금 값으로 판정(늘리기·줄이기) | `PointShopRefundTest`(2개), api `MyItemHttpTest.환불_기한이_지나면_…`, admin `PointShopPurchaseAdminHttpTest.설정_조회_저장은_…` |
| **정지 중 장착·해제 불가**(영구 정지 포함·자리 그대로·막힌 장착은 사용 아님) | shared `UserItemEquipTest.정지_중이면_장착도_해제도_…`, api `MyItemHttpTest.정지_중이면_장착도_해제도_403_…` |
| **정지 중 셀프 환불 = 신청**(202·포인트·아이템 그대로·같은 키 재요청) · 조건 밖이면 신청 없이 409 | shared `PointShopRefundRequestTest.정지_중_셀프_환불은_…`·`조건_밖이면_…`, api `RefundRequestHttpTest.정지_중_환불은_202_…` |
| 신청 순간 판정 고정(기한 지난 뒤 수락해도 원래 묶음으로) | `PointShopRefundRequestTest.판정은_신청_순간으로_…` |
| 대기 중: 재신청·셀프 환불·장착·유료 아바타·변경권·예외 환불 막힘(정지 풀려도) · 대기 하나(동시 두 신청 + 색인) | `PointShopRefundRequestTest`(3개), api `RefundRequestHttpTest.정지가_풀려도_…`, admin `PointShopRefundRequestAdminHttpTest.권한이_없으면_…` |
| 수락 = 환불(재고·REFUNDED·종류 REQUEST·돈 관련 감사·회원 알림) · 신청 = 운영자 알림 | admin `PointShopRefundRequestAdminHttpTest.대기_거르기와_…`, api `RefundRequestHttpTest.정지가_풀려도_…` |
| 거절 = 사유 5~200·아이템 다시 쓸 수 있음·정지 중 재신청 409·풀린 뒤 기한 안 셀프 환불·알림에 사유 | `PointShopRefundRequestTest.거절하면_…`·`거절된_아이템은_…`, admin `…거절은_사유_…`, api `RefundRequestHttpTest.거절하면_…` |
| 이미 결정된 신청 409·없는 신청 404·권한 403 · 신청 표 CHECK·알림 종류·운영자 이벤트·두 언어 문구 | `PointShopRefundRequestTest`(3개), admin `…권한이_없으면_…` |
| **기간제 이어 붙이기** · 기간 끝나면 해제로 보임 · 끝난 것 장착 거절 | `PointShopPurchaseTest.기간제는_…`, `UserItemEquipTest`, api `MyItemHttpTest.기간이_끝난_…`·`AuthorDecorationHttpTest.기간이_끝난_장착은_…` |
| **변경권 한 장이 두 번 쓰이지 않음**(한 장·두 장 동시) · 실패하면 안 씀 · 무료 남으면 안 씀 | `NicknameTicketTest`(4개), api `NicknameTicketProfileHttpTest`(7개) |
| 환불: 원래 묶음으로·재고 복원·REFUNDED·멱등 · 예외 환불(조건 무시·장착 해제·고른 아바타 기본으로·돈 관련 감사·사유) | `PointShopRefundTest`, admin `PointShopPurchaseAdminHttpTest.예외_환불은_…` |
| 판매 기간 밖·사용 안 함·품절·동결·자격(정지·이메일·권한·고지 동의)·잔액 부족은 거절하고 아무것도 안 바뀜 · 자격이 고지보다 먼저 | `PointShopPurchaseTest`(4개), api `PointShopPurchaseHttpTest`(4개) |
| 적립 포인트부터 차감(결정 9) · 같은 구매에 포인트 두 번 사용 불가 | `PointShopPurchaseTest`(2개) |
| 가격·이름은 산 때 사본(품목·묶음 가격을 바꿔도) · 색을 고치면 산 사람의 장식도 바뀜 | `PointShopPurchaseTest.구매_줄은_…`, `PointShopCatalogTest.색을_고치면_…`, admin `PointShopAvatarAdminHttpTest.카테고리_묶음_가격은_…` |
| 품목 CHECK·트리거(종류별 칸·코드 불변·재고 음수 불가·사본 불변·환불 한 번·first_used_at·상태 앞으로만·자리마다 하나) | `PointShopSchemaTest`(6개) |
| 관리자 품목 검사·종류·코드 불변·감사(바뀐 칸만)·아바타 만들기·지우기 불가·고칠 수 있는 칸 제한 | `PointShopCatalogTest`, admin `PointShopItemAdminHttpTest`(6개) |
| 아바타 묶음 가격·판매(범위·무료 카테고리·아바타 아닌 품목 400·없는 품목 404·matched/changed) | `PointShopCatalogTest.아바타_묶음_…`, admin `PointShopAvatarAdminHttpTest`(4개) |
| 아바타: 무료 누구나(사업자 포함)·유료는 보유만·판매 멈춘 것도 보유자는 · 비로그인 목록 · 카탈로그 328 = 시드 306 = 두 앱 svg | `ProfileAvatarTest`(5개), `AvatarCatalogTest`(4개), api `AvatarHttpTest`(5개) |
| 작성자 장식: 글·목록·댓글·공개 프로필(한 번에 풀기·없음 null·아바타 늘·비회원 null·만료 빠짐) | `PointShopCatalogTest.장식은_…`, api `AuthorDecorationHttpTest`(3개) |
| 0-5 이름 바꾸기(요청 종류·권한·역할 옮김) · 변경권 가격 이관·정책 칸 삭제 · 시드 ID | `PointShopMigrationTest`(4개 — V85 까지 이관한 별도 DB 에 옛 행 → V86) |
| 권한 선언(핸들러 11개) · 화면 레지스트리 세 줄 = admin-web 사본 · 권한 카탈로그 = admin-web 사본 · 회원 상세 변경권 수 | admin `PointShopPermissionDeclarationTest`, `PointShopAdminMenuTest`, `CommercePermissionsAndMenuTest`·`Commerce04AdminCatalogTest`, `PointShopPurchaseAdminHttpTest.회원_상세의_…` |

**아직 테스트로 막지 못한 것**: ① 쪽지 상대 장식은 컴파일만(HTTP 시험 없음). ② CSRF·보안 필터 단의 401·CSRF 는 api 시험이 필터 없이 돌려 확인하지 않았다(비로그인 아바타 허용 포함). ③ 로그인한 화면에서의 실제 호출·시각 확인(개발자 몫). ④ 다른 탭·기기에서 바꾼 장착은 이 탭의 겹치기에 새로고침 전까지 반영되지 않는다(화면 한계).

## 1-3 화면 목업 (2026-10-26 — **사용자 확정**(「추천대로 하고 출석체크는 커뮤니티 공개사이트 것 그대로, 로보얌 구성에 맞게」): 아래 화면·확정 6가지 그대로)
새 칸만 메모리 목업이 실제 응답에 덧붙인다(`// MOCK:`·`// REAL:` 나란히, 목업 파일 `composables/{point,board}/api/mock/*`). 마이그레이션 가번호 **V89**(develop 마지막 shared = V88).

**화면**
- admin `/points/policy`: 「글·댓글」에 묶음 「적립 조건」(`postMinLength` 50 · `commentMinLength` 10 · `writeIntervalSeconds` 30 + 스위치 `commentOwnPostExcluded`·`commentOncePerPost`), 새 구획 「하루 총 한도」(`dailyTotalCap` 200 — 0 = 「한도 없음」 딱지), 「출석」에 묶음 「연속 출석 보너스」(`attendanceStreakDays` 7·`attendanceStreakBonus` 20 — 「아직 적용되지 않아요」 유지), 「가입 포인트」 딱지 뗌·본인인증 기준 안내, 새 구획 「운영 조정」(`adjustLargeThreshold` — 이제 저장 가능, 1-1 이 미룬 칸). 운영 원칙 문구 고침.
- admin `/boards/[slug]`(보호·공개 시간·포인트)·`/boards/defaults/general`: `pointMode` 옆에 「적립 최소 글자 수 적용」 `pointMinLengthApplied`(게시판 null = 기본값 따름, 기본값 켬). 유효 `pointMode` = OFF 면 비활성.
- user `/me/points`: 「오늘 총 적립 N / 200P」 막대(한도 0 이면 없음), 「적립 규칙」에 본인인증 보너스 줄(받음 / `/settings/identity` 링크)·「적립 조건」 목록(꺼진 조건 빼고).

**API 후보**
- `GET·PUT /api/admin/settings/points` + 숫자 7칸·불리언 2칸(범위 `postMinLength` 0~10,000 · `commentMinLength` 0~1,000 · `writeIntervalSeconds` 0~3,600 · `dailyTotalCap` 0~100,000 · `attendanceStreakDays` 0~365 · `attendanceStreakBonus` 0~10,000 · `adjustLargeThreshold` 0~100,000), 감사 `POINT_SETTINGS_UPDATE` 바뀐 칸 before/after.
- 게시판 정책·기본값에 `pointMinLengthApplied`(`BoardPolicyValues` nullable · `BoardDefaultsValues` boolean true, `pointMode` 다음).
- `GET /api/me/points` + `today.total{amount, cap}` · `rules.{postMinLength, commentMinLength, commentOwnPostExcluded, commentOncePerPost, writeIntervalSeconds, dailyTotalCap, signup{points, received}}`.

**확정(사용자 「추천대로」 2026-10-26)**
1. 게시판 스위치를 끄면 **글 본문 최소 글자 수만** 빠진다(댓글 최소 글자 수·다른 조건은 그대로).
2. 게시판 `pointMode`(지급 안 함)는 화면·저장에는 있지만 **서버 적립이 보지 않는다**(지금 무시됨) — 같은 적립 길을 고치는 1-3 에서 함께 적용(글·댓글·추천 보너스 0).
3. 가입 포인트 「평생 1회」 = **계정(user_id)마다**(멱등 `SIGNUP` + `USER:{user_id}`, commerce.md 6-2 그대로). 탈퇴 후 30일 재인증 제한이 반복을 늦춘다. 사람마다로 막으려면 인증 해시를 오래 보관해야 해 하지 않는다.
4. 본인인증 때 포인트 동결 중이거나 정상 회원이 아니면 가입 포인트를 **주지 않고 나중에도 주지 않는다**(지금 동결 적립 건너뜀과 같다).
5. `signupPoints` 지금 저장값(기본 0)은 마이그레이션이 바꾸지 않는다 — 운영자가 정한다.
6. 하루 총 한도는 지급한 양(상계 전) 합, 지운 글로 받은 것도 센다. 남은 양이 0 이면 줄이 없어 개별 횟수에 들지 않는다.

## 1-3 출석체크 (2026-10-26 — 사용자 「커뮤니티 공개사이트 것 그대로 적용, 로보얌 구성에 맞게」)
community `docs/api-point.md` 「출석」·`AttendanceService`·user `components/point/AttendancePanel.vue`(+ `usePointAttendance`, 헤더 「포인트·출석체크 · +10P」, `/me` 줄 안내)를 옮긴다. 로보얌에 맞춘 것:
- **표** `point_attendances`(V89): id(Snowflake), user_id, day(KST 날짜), streak(≥ 1 — 어제 줄의 streak + 1, 없으면 1), created_at, 유일 `(user_id, day)`. users 외래키 없음(1단계 새 표 규칙). 넣기는 `insert … on conflict do nothing` 한 문장 — 동시에 눌러도 하나.
- **지급**: `PointService` 한 길. `ATTENDANCE`(+`attendancePoints`)·`ATTENDANCE_STREAK`(streak 이 `attendanceStreakDays` 의 배수일 때 +`attendanceStreakBonus`, 둘 중 0 이면 끔) 모두 대상 `ATTENDANCE` + 출석 줄 ID(원장 ref_type CHECK 에 `ATTENDANCE` 를 더한다 — 기존 목록과의 합집합). 하루 총 한도·미회수 상계·동결(출석은 남고 포인트만 0)·정상 회원 규칙은 다른 활동 적립과 같다. 연속 보너스도 「출석」 활동이라 하루 총 한도에 든다. 적립 실패는 출석을 깨지 않는다(세이브포인트).
- **API**(api 서버, 공통 래퍼·no-store): `GET /api/me/attendance?month=YYYY-MM` → `{ month, days[], monthCount, streak, today{date, attended} }`(month 형식 틀리면 400 `fields.month`, 미래 달은 `days: []`, `streak` = 오늘 또는 어제까지 이어진 연속 일수, 끊겼으면 0) · `POST /api/me/attendance` → `{ date, earned, streakBonus, streak, balance }` · 409 `ATTENDANCE_ALREADY_DONE` · 403 `EMAIL_NOT_VERIFIED`·`ACCOUNT_RESTRICTED`(정지) · 사업자 전용 계정은 포인트 상점·포인트와 같은 규칙. `GET /api/me/points` 는 `attendanceAvailable: true`, `today.attended` 실제 값, `rules.attendanceStreak{days, bonus}`.
- **화면**: user `/me/points` 에 출석 달력 구획(community AttendancePanel — 달 넘기기·출석한 날 표시·[오늘 출석하기]·연속 N일·다음 보너스까지), 헤더 프로필 메뉴·`/me` 줄 「포인트·출석체크」(오늘 출석 전이면 「· +N P」). admin 정책 「출석」 구획의 「아직 적용되지 않아요」 딱지를 뗀다.

### 1-3 구현 결과 (2026-10-26 — 미커밋)
- **마이그레이션** `V89__point_earn_rules.sql`(가번호 — develop 마지막 shared V88): `point_settings` 8칸 + `ck_point_settings_earn_rules` · `board_defaults.point_min_length_applied`(not null, 기본 true)·`boards.point_min_length_applied`(null = 기본값 따름) · 표 `point_attendances`(유일 `(user_id, day)`, streak ≥ 1, users FK 없음) · `ck_point_ledger_ref`·`ck_point_lots_source_ref` 다시 만듦(V85 목록 ∪ `ATTENDANCE` — 출석 지급이 만드는 묶음의 출처 대상도 `ATTENDANCE` 라 묶음 CHECK 도 넓혔다).
- **shared**: `PointService` — `earn` 을 `earnPost(PostEarn)`·`earnComment(CommentEarn)` 로 나눔, `likeBonus(…, boardPoints)`, 새 `attendance`·`signup`·`signupReceived`·`earnedToday`. 판정 순서: 잠금 전(게시판 포인트 끔 → 정책 0 → 최소 글자 수 → 자기 글 댓글) → 잔액 행 잠금 아래(이미 지급 → 개별 하루 상한(횟수) → 같은 글 댓글 1회 → 작성 간격 → 하루 총 한도 남은 만큼). `AttendanceService`·`PointAttendanceStore`. 게시판 `EffectiveBoard` 에 `pointsEnabled`(유효 `pointMode` ON)·`pointMinLengthApplied`. 본인인증 `IdentityVerificationService.settle` 이 시도 확정 뒤 `PointService.signup`(세이브포인트).
- **api**: `MyAttendanceController`(`GET/POST /api/me/attendance`, no-store), `/api/me/points` 새 칸, 글·댓글·추천의 적립 호출에 게시판 유효값·본문 글자·글 작성자 전달.
- **admin**: 정책 `GET·PUT` 22칸 모두 필수(불리언 비면 400 `fields.<칸>` 「켬 또는 끔을 골라 주세요.」), 감사 바뀐 칸 before/after(문턱 포함). 게시판 정책·기본값 `pointMinLengthApplied`(기본값 null 저장은 400, `overrides` 에 실림).
- **화면 연결**: 세 목업 지움·실제 호출. user `/me/points` 출석 구획(달력·연속·다음 보너스·[오늘 출석하기])·오늘 적립·규칙의 출석 줄, `/me` 줄 「포인트·출석체크」(헤더 메뉴는 기존 분기 그대로). admin 정책 「출석」 딱지 뗌.
- **구현하며 정한 것**: ① 글자 수 = `plain()` 에서 유니코드 공백·NBSP·U+3000·폭 없는 공백(U+200B~200D·2060·FEFF·180E)을 뺀 코드 포인트. ② 작성 간격 기준 = 직전 `POST`·`COMMENT` 적립 줄의 `created_at`(포인트를 못 받은 글은 줄이 없어 기준이 아님, 정확히 30초면 지급). ③ 같은 글 댓글 1회 = 같은 `post_id` 의 댓글 + 원장 `COMMENT` 줄(지운 댓글 행도 남아 센다). ④ 하루 총 한도 대상 = `ATTENDANCE`·`ATTENDANCE_STREAK`·`POST`·`COMMENT`·`LIKE_BONUS` EARN 금액 합(상계 전), 「오늘」 경계는 개별 상한과 같은 Snowflake ID(실제 시계). ⑤ 출석 날짜는 앱 시계의 한국 날짜(community 는 DB 시각), 출석 포인트·연속 보너스는 따로 세이브포인트, 대상 둘 다 `ATTENDANCE:{출석 ID}`, `earned` = 출석분만·`streakBonus` 따로, 동결·한도로 0 이어도 200. ⑥ 사업자 전용 계정은 출석을 따로 막지 않는다(기존 포인트 적립과 같다). ⑦ 출석 403 은 `ACCOUNT_RESTRICTED`·`EMAIL_NOT_VERIFIED` 만.

### 1-3 규칙 → 테스트 (모두 실제 PostgreSQL — `CommerceTestDatabase`)
| 규칙 | 테스트 |
|---|---|
| 글자 수(공백·NBSP·폭 없는 공백 제외, 코드 포인트) | shared `PointEarnRulesTest.글자_수는_…` |
| 글 최소 글자 수(49자 0 · 50자 지급) | `PointEarnRulesTest.본문_실제_글자가_…`, api `PointEarnWiringTest.게시판_칸이_비면_…` |
| 게시판 스위치 끔 → 글만 빠지고 댓글은 그대로 · 기본값 끔 | `PointEarnRulesTest.게시판_스위치를_끄면_…`, api `PointEarnWiringTest.스위치를_끈_…`·`기본값의_스위치를_끄면_…` |
| 정책 0 = 조건 없음(최소 글자·간격·총 한도) | `PointEarnRulesTest`(3개) |
| 게시판 `pointMode` OFF → 글·댓글·추천 보너스 0, 글은 올라감 | `PointEarnRulesTest.게시판_pointMode_OFF면_…`, api `PointEarnWiringTest.게시판_pointMode_OFF면_…` |
| 자기 글 댓글 0(끄면 지급) | `PointEarnRulesTest.자기_글에_단_댓글은_…` |
| 같은 글 두 번째 댓글 0(첫 댓글을 지우고 회수해도) · 끄면 지급 | `PointEarnRulesTest.같은_글의_두_번째_댓글은_…`·`같은_글_댓글_1회를_끄면_…` |
| 작성 간격(글·댓글 합침, 30초 경계) · 못 받은 글은 기준 아님 | `PointEarnRulesTest.포인트를_받은_…`·`포인트를_못_받은_…` |
| 하루 총 한도 걸침(190P 에서 글 20P → 10P) · 남은 0 이면 줄·개별 횟수 없음 | `PointEarnRulesTest.하루_총_한도에_걸치면_…` |
| 총 한도는 상계 전 양·지운 글로 받은 것도 셈 | `PointEarnRulesTest.하루_총_한도는_상계_전_…` |
| 한도 제외 사유(운영 조정·가입 포인트) | `PointEarnRulesTest.운영_조정과_가입_포인트는_…`, `PointSignupTest.미회수가_있으면_…` |
| 동시 적립이 총 한도·간격을 넘지 않음(12·8 스레드) | `PointEarnRulesTest.동시에_적립해도_…` |
| 가입 포인트 한 번만(대상 USER·유일 색인·재인증) · 동시 요청 | shared `PointSignupTest`(3개), api `SignupPointIdentityTest.본인인증을_확정하면_…`·`같은_회원이_동시에_…` |
| 동결 중 가입 포인트 없음(풀려도 안 줌)·정지 회원 없음·정책 0 이면 없음(인증은 성공) | api `SignupPointIdentityTest`(3개), `PointSignupTest.동결_중이거나_…`·`정책이_0이면_…` |
| 가입 포인트 미회수 상계 | `PointSignupTest.미회수가_있으면_…` |
| 출석 하루 한 번(409) · 동시 4번 → 하나 | shared `PointAttendanceTest.하루에_한_번이고_…`·`동시에_두_번_…`, api `AttendanceHttpTest.출석하면_200_…` |
| 연속 보너스(배수 날만·끊기면 1부터·0 이면 끔) | `PointAttendanceTest.연속_보너스는_…`·`연속_보너스_일수나_…` |
| 출석 동결이면 기록만 · 출석 포인트 0 이면 기록만 | `PointAttendanceTest.동결_중이면_…`·`출석_포인트가_0이면_…`, api `AttendanceHttpTest.동결_중이어도_…` |
| 출석·연속 보너스도 하루 총 한도에 듦 | `PointAttendanceTest.출석과_연속_보너스는_…` |
| 출석 자격(정지·영구 정지·이메일 인증 전·탈퇴) | `PointAttendanceTest.정지_회원은_403_…`, api `AttendanceHttpTest.이메일_인증_전은_…` |
| 출석 달력(출석일·연속·끊기면 0·미래 달·today·월 형식 400) | `PointAttendanceTest.달력은_…`·`달_형식이_틀리면_…`, api `AttendanceHttpTest.달력은_…` |
| 출석 표 유일·streak CHECK | `PointAttendanceTest.출석_줄은_…` |
| 관리자 정책 새 칸 조회·저장·감사 before/after(같은 값이면 없음)·범위·빠진 칸 400·경계값·권한 | admin `PointSettingsHttpTest`(5개) |
| 저장한 조정 문턱으로 고액 판정 | admin `PointSettingsHttpTest.저장한_문턱으로_…` |
| 게시판 칸 저장·null(따름)·감사·기본값 boolean·null 400·overrides·유효값 | admin `BoardPointMinLengthHttpTest`(3개) |
| 회원 포인트 응답 새 칸(출석 가능·오늘 출석·총 적립·규칙) | api `AttendanceHttpTest.내_포인트에_…` |
| 불변식(5-5) — 시험마다 | `PointTestKit.assertInvariants`, `PointInvariants` |

**아직 테스트로 막지 못한 것**: ① 추천 보너스의 api 연결(`like` 가 `board.pointsEnabled()` 를 넘기는지)은 컴파일로만 — 인정된 추천 문턱을 채울 등급 데이터가 커서 shared 에서 인자 효과만 시험. ② 자정을 걸치는 한도 초기화(「오늘」 경계가 실제 시계라 시험이 옮길 수 없음). ③ 보관 기간 파기로 댓글 행 자체가 지워지면 「같은 글 댓글 1회」가 그 댓글을 세지 못한다(지금 파기는 표시뿐 — 실제 삭제 경로가 생기면 다시 본다). ④ 본인인증 쪽 세이브포인트와 JPA 트랜잭션 관리자의 조합은 api 시험(실제 앱 문맥)으로만. ⑤ 화면(타입 검사만 — 브라우저 확인은 개발자).

## 미확정
- 두 앱이 같이 쓰는 장식 규칙(대비 보정 `UtilColor`·테두리 팔레트)을 지금은 앱마다 사본으로 둔다 — `apps-packages` 로 옮길지.
