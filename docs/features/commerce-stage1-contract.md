# 커머스 1단계 계약 — 포인트 확장 + 포인트 상점 (ERD·패키지 제안 승인 2026-10-26 — 구현 전)

**의도**: 포인트를 무상(적립)·유상(충전)으로 나누고, 받은 포인트를 묶음(lot) 단위로 관리해 소멸·회수·사용 취소를 정확히 한다(유상이 아직 없어도 구조를 먼저 바꾼다). 그 위에 **포인트 상점**(포인트로 사는 디지털 아이템)을 만든다.
정책 원천은 `docs/domain/commerce.md` 2-2·2-3·4·5·6·7·12-3·12-4·13-2·14·15·18절이고, 0단계 장치(기능 제한·멱등·속도 제한·배치 기반·시험 DB)는 `commerce-foundation-contract.md` 에 있다. 이 문서는 1단계에서 정한 구조와 결정만 적는다.
**테스트**: 이 문서와 commerce.md 에 적힌 규칙은 모두 테스트로 막고 통과해야 끝난다(CLAUDE.md 커머스 예외, commerce.md 18절). 잠금·동시성·제약·트리거는 `CommerceTestDatabase`(실제 PostgreSQL)로 막는다.

## 작업 단위와 순서
| 단위 | 내용 | 화면 | 상태 |
|---|---|---|---|
| 1-1 포인트 확장 | 아래 A + 배치·불변식 + 회원·관리자 포인트 API | 작음: 회원 포인트 화면(소멸 예정·미회수), 관리자 원장 무상/유상 칸·회원 상세 묶음 — 목업 먼저 | 대기 |
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
