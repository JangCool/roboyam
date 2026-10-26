# 커머스 1단계 계약 — 포인트 확장 + 포인트 상점 (ERD·패키지 제안 승인 2026-10-26 — 구현 전)

**의도**: 포인트를 무상(적립)·유상(충전)으로 나누고, 받은 포인트를 묶음(lot) 단위로 관리해 소멸·회수·사용 취소를 정확히 한다(유상이 아직 없어도 구조를 먼저 바꾼다). 그 위에 **포인트 상점**(포인트로 사는 디지털 아이템)을 만든다.
정책 원천은 `docs/domain/commerce.md` 2-2·2-3·4·5·6·7·12-3·12-4·13-2·14·15·18절이고, 0단계 장치(기능 제한·멱등·속도 제한·배치 기반·시험 DB)는 `commerce-foundation-contract.md` 에 있다. 이 문서는 1단계에서 정한 구조와 결정만 적는다.
**테스트**: 이 문서와 commerce.md 에 적힌 규칙은 모두 테스트로 막고 통과해야 끝난다(CLAUDE.md 커머스 예외, commerce.md 18절). 잠금·동시성·제약·트리거는 `CommerceTestDatabase`(실제 PostgreSQL)로 막는다.

## 작업 단위와 순서
| 단위 | 내용 | 화면 | 상태 |
|---|---|---|---|
| 1-1 포인트 확장 | 아래 A + 배치·불변식 + 회원·관리자 포인트 API | 작음: 회원 포인트 화면(소멸 예정·미회수), 관리자 원장 무상/유상 칸·회원 상세 묶음 — 목업 먼저 | 완료(미커밋) 2026-10-26 — 화면 확정·서버·화면 연결·규칙 테스트 통과(shared 157·api 40·admin 84, 알림 간격 추가 뒤 shared 123·api·admin 다시 통과), 아래 「1-1 구현 결과」 |
| 1-2 포인트 상점 | 아래 B + 닉네임 변경권 사용 + 장식 표시 + 0-5 이름 바꾸기 | 큼: 회원 포인트 상점·내 아이템·구매 확인, 관리자 품목·구매 내역·설정 — 목업 먼저 | 대기 |
| 1-3 적립 규칙 보강(선택) | commerce.md 6-2 설정 칸 + 가입 포인트(본인인증 완료 때 평생 1회) | 포인트 정책 칸 | 대기 |
순서: 1-1 서버 ∥ 1-2 화면 목업 → (1-1 을 develop 에 합친 뒤) 1-2 서버 → 1-3. 1-2 서버는 포인트 사용·사용 취소(1-1)에 기댄다.

## 마이그레이션 계획 (가번호 — develop 에 합칠 때 마지막 번호를 다시 보고 확정)
| 가번호 | 스트림 | 내용 | 단위 |
|---|---|---|---|
| V85 `point_extension` | shared | A 전부 | 1-1 |
| V86 `point_shop` | shared | B 전부 + 닉네임 변경권 품목 하나(가격 = 지금 `point_settings.nickname_ticket_price`) | 1-2 |
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

### 1-1 구현 결과 (2026-10-26 — 미커밋, 브랜치 `claude/objective-bardeen-1fa60a`)
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
- **`point_shop_items`**: id, code(유일·불변), kind(`NICKNAME_CHANGE_TICKET`·`NICKNAME_COLOR`·`PROFILE_BORDER` — 결정 9), usage_type(`PERMANENT`|`CONSUMABLE`|`PERIOD` — 변경권은 CONSUMABLE 만, 색·테두리는 PERMANENT|PERIOD), name·name_en·description·description_en, color(`#RRGGBB`, 닉네임 색만)·border_style(팔레트 키, 테두리만) — CHECK, price_point(1~100,000), duration_days(PERIOD 만, 1~365), stock(null = 무제한, ≥ 0), sale_start_at·sale_end_at, active, sort_order, created_by·updated_by·created_at·updated_at.
- **`point_shop_purchases`**: id, user_id, item_id, 사본(code·name·kind·usage_type·price_point·duration_days), status(`COMPLETED`|`REFUNDED`), refund_notice_agreed_at(필수), use_ledger_id, refund_ledger_id, refunded_at, refunded_by_operator_id·refund_reason(예외 환불), created_at. 색인 `(user_id, id DESC)`·`(item_id)`.
- **`user_items`**: id, user_id, item_id, purchase_id(유일), usage_type(사본), status(`OWNED`|`USED`|`REVOKED` — 만료는 저장하지 않고 expires_at 으로 계산, 결정 6), starts_at, expires_at(PERIOD 만), first_used_at(환불 판정, 지우지 않음), used_at, revoked_at, created_at. 유일 `(user_id, item_id) WHERE usage_type='PERMANENT' AND status <> 'REVOKED'`. 기간제를 기간 중에 또 사면 앞 줄 끝에서 시작.
- **`user_equipped`**: PK `(user_id, slot)`, slot(`NICKNAME_COLOR`|`PROFILE_BORDER`), user_item_id(유일), item_id·expires_at(사본 — 결정 8), equipped_at. 장착하면 `first_used_at` 을 채운다. 기간이 지나면 읽을 때 해제로 본다.
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
9. **품목 종류와 쓸 수 있는 포인트**: 1단계 품목은 닉네임 변경권·닉네임 색·프로필 테두리. **포인트 상점 품목은 전부(변경권 포함) 적립·충전 포인트 모두 쓸 수 있고 적립부터 차감**(commerce.md D2 를 「꾸미기 아이템」에서 「포인트 상점 품목 전부」로 넓혔다). 테두리를 어디에 보일지는 화면 목업에서 정한다.
10. **6-2 적립 규칙·가입 포인트**: 1-3 으로 따로(1-1 다음).
11. **큰 금액 조정 승인 대기**(13-2): 1단계는 권한 검사만(문턱을 넘는데 권한이 없으면 거절). 승인 대기는 4단계 — 「아직 테스트로 막지 못한 규칙」으로 적는다.
12. **일일 어뷰징 리포트**: 4단계. 1단계는 shortfall 표까지.
13. **원장 줄 금액 `delta`**: 지금처럼 int(한 줄 금액은 그 범위), 합계 칸만 bigint.
- **이름**: 「상점」이라고만 쓰지 않는다 — **포인트 상점**(포인트로 사는 디지털 아이템)과 **공동구매**(현금으로 사는 실물)로 부른다. **둘을 한 메뉴로 묶지 않는다**(사용자 2026-10-26). 표·패키지·권한·사유는 `point_shop`·`pointshop`·`POINT_SHOP_*`.

## 미확정
- 프로필 테두리를 보일 자리(작성자 표시·프로필 화면)와 팔레트 — 1-2 화면 목업에서.
- 닉네임 색의 대비(어두운·밝은 테마에서 읽히는지) 검사 규칙 — 1-2 화면 목업에서.
- 미리보기를 줄 수 없는 품목의 설명 문구 기준.
