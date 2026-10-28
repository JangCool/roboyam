# 커머스 2b 계약 — 공구(현금 결제) (ERD·패키지 **승인 2026-10-28** — 「지금까지 확정된 상태로 진행」, 추천안 전부 포함)

**의도**: 운영자가 직접 파는 실물 공동구매를 연다 — 공구 등록·모집·참여(선점 → 2a 결제 모듈로 결제)·마감 판정·발주·발송·취소·반품·환불·배송지 보호·Q&A 까지. 성사형(`min_qty > 0`)과 선착순(`min_qty = 0`)을 같은 모델로, 발송 시점(`fulfillment`)은 따로 고른다(D1).
정책 원천은 `docs/domain/commerce.md` 10절(공구)·4·12·13·14·15·17·18·19·20절이고, 결제는 `commerce-payment-contract.md`(`PaymentOrderHandler`·`payment_cancels`·확정 코드), 0단계 장치(본인인증·기능 제한·멱등·속도 제한·메일 대기열·필드 암호화·워커 기록·시험 DB)는 `commerce-foundation-contract.md`. 이 문서는 2b 에서 정할 구조와 결정만 적는다.
**테스트**: commerce.md 와 이 문서의 규칙은 모두 테스트로 막고 통과해야 끝난다(CLAUDE.md 커머스 예외, commerce.md 18절 — P3·P3b·P15·P16·P16b~e·P25·P26·P33~P42·P35 와 불변식 + 아래 「새 규칙 → 테스트」 G1~G12).
**범위 밖(2b)**: 공구 포인트 결제(11절 — 3+ 단계, 표 열만 둔다) · 회원 주최 공구 · 택배사 API · 쿠폰 · 매출·정산 화면과 세금계산서 요청·발행 표시(16-2 — 21-1 「4」) · 리뷰·구매 확정 단추(community 에도 없다) · 저장된 배송지 불러오기 · 1:1 문의(roboyam 에 없다 — 공구 밖 문의는 사업자 정보의 전화·이메일).
**community 대조(2026-10-28)**: `D:\project\community\workspace\community\docs\` 의 `api-commerce.md`(C)·`api-commerce-admin.md`(A)·`api-deal-shop.md`(S)·`features.md` 23절·`decisions.md`(D 450~513)와 맞춰 봤다. 큰 줄기는 같고 roboyam 이 더 엄격하다(늦은 결제는 살리지 않고 자동 취소 — community 는 재고가 있으면 살림). 에스크로·영업일·고객 택배비 보상·계좌 송금은 community 에 없어 roboyam 설계가 정본이다. 가져온 것은 결정 13~22 와 화면 목록에 적었다.
**사용자 결정(2026-10-28)**: 결정 15(반품 처리 방법 선택)·17(추가 입고 = 늘리기 + 추가 차수)·21(공구 Q&A)·23(한 주문에 여러 옵션)·24(차수 사이 1인 한도는 공구별 선택, 기본 독립)·25(다시 판매·추가 입고도 마감 연장 조건)·26(Q&A 개인정보 자동 비밀글 + 안내 · 신고 대상 · 3년 보관 · 답변 수정 이력). 그 뒤 처음 보는 검토자 점검(같은 날)에서 나온 빈 곳은 「수량·상태 규칙」·「Q&A 규칙」에 메웠다. 두 번째 검토(Fable, 같은 날 — 주문 줄 전환·결제 모듈 경계 중심)의 빈 곳과 사용자 결정 42~45 는 「2차 검토 보강」에 메웠다. 최종 검토(Fable 넷 — 트랜잭션·재고·금액·법/실제 공구 관행, 같은 날)와 사용자 결정 52~54(발송 지연은 통지 + 취소권 · 일부 옵션 공급 불가는 답이 없으면 나머지만 받기 · 하자·오배송만 교환)는 「최종 검토 보강」에 메웠다.

## 작업 단위와 순서 (제안)
| 단위 | 내용 | 담당 | 상태 |
|---|---|---|---|
| 2b-1 화면 목업 | 아래 「화면 목록」(Q&A 제외) | frontend | **진행 중**(2026-10-28, 자기 worktree) |
| 2b-2 공구 핵심 서버 | V101, 공구·옵션·배송비 정책·수정 이력, 주문·주문 줄(자격·멱등·선점)·`GroupBuyPaymentHandler`·선점 만료·이탈 제한, 상태 워커(모집 시작·마감·연장·판정·불발 환불), 본인 취소, 추가 입고·줄이기·추가 차수, 불변식, 규칙 테스트 | backend | **구현 완료**(2026-10-28, 브랜치 `claude/commerce-2b-2-groupbuy-core-d751c0` — 아래 「2b-2 구현 결과」, develop 병합 전) |
| 2b-3 발송·요청·환불 서버 | V102, 발주·준비중·발송·배송완료(일괄 부분 성공)·에스크로 송장 등록, 택배사, 배송지 열람·수정·명단 내보내기, 취소·반품 요청(줄·수량 단위)·처리 방법·`GroupBuyRefundCalculator`·보상액·운영 환불, 규칙 테스트 | backend | 제안 |
| 2b-4 설정·알림 + 관리자 API + 연결 | V103, 판매 스위치·체크리스트·사업자 정보·공구 설정, 회원 알림·메일(계약 통지 포함)·관심·수량 풀림·연장·차수 알림, 운영자 알림·기한 경고, 개인정보 파기 pg_cron, 관리자·회원 API 정리, 화면 연결 | backend → frontend | 제안 |
| 2b-5 실제 확인 | 포트원 테스트 채널로 공구 결제·불발 환불·발송 후 반품 환불 사람 확인(공개 주소가 필요한 웹훅·에스크로는 dev 서버) | 사용자 + 세션 | 제안 |
| 2b-6 공구 Q&A | V104, 아래 「Q&A 규칙」 — 화면 목업 → 확정 → 서버 → 연결 | frontend → backend | 사용자 결정 2026-10-28 |
순서: 2b-1 ∥ 2b-2 → 화면 확정 → 2b-3 → 2b-4 → 2b-5, 2b-6 은 2b-4 다음(구매 흐름을 막지 않는다). 2b-2 는 화면과 무관한 서버 핵심이라 2a 와 같이 화면 확정 전에 병행한다(승인 필요). 세션마다 자기 worktree.

## 화면 목록 (2b-1)
- **회원(user-web)**
  - `/group-buys` — 탭 진행·예정·종료, 종료 사유(`SOLD_OUT` 다 팔림 · `TIME_UP` 기간 끝 · `CLOSED` 운영 종료), 예정 공구 가격 미리 공개, 마감 임박·남은 수량 적음 표시(남은 10% 이하 또는 5개 이하), 성사형 진행률, 「N차」 표시. (C 72·78, D 450)
  - `/group-buys/{id}` — 이미지·설명·옵션별 남은 수량, 배송비·반품 비용 금액 고지(10-8), 연장 안내, 「추가 입고 N개」·「N차 공구」 표시, **다 팔림과 「잠시 구매 불가(다른 분이 결제 중)」 구분**(공구·옵션 단위), 수량 풀림·마감 임박 알림 신청, **다 팔려 끝난 공구엔 「다음 차수 알림 받기」**(결정 17), 하단 사업자 정보(공개 info API), 단추 상태는 서버가 정한 `actions`·`purchase.reason`, Q&A(2b-6). (C 92·157, D 450)
  - `/group-buys/{id}/order` — **옵션 여러 줄 × 수량**(결정 23), 배송지(우편번호 찾기, 실패 시 직접 입력), 동의 3종, 본인인증 게이트, 결제대금예치 고지, **견적과 합계가 다르면 결제창 대신 「금액이 바뀌었어요」 확인** → 결제창 → 결과. (D 513)
  - `/me/orders`·`/me/orders/{orderNo}` — 주문 줄·결제·배송 조회, 가린 배송지·전체 보기·수정(기한은 결정 30), 본인 취소, 취소·반품 요청(**줄·수량을 골라**, 환불 예정 금액 미리 보기, 하자·오배송 사진 최대 3장), 요청 철회, 환불 진행 중/완료 표시(결정 16). (C 175·178·181)
- **관리자(admin-web)** — 묶음 「공구」
  - 공구 목록·등록·수정(모집 뒤에는 설명·이미지만)·모집 시작·조기 마감·**중단**(확인 창: 「환불 N명 · 총 N원 / 이미 발송 M명은 그대로」 — 결정 29)·수정 이력·**「추가 차수로 열기」**(결정 17·31)
  - **수량 화면**: 공구·옵션별 총량·확정·선점·남음·반품·다시 판매, 선점 목록(남은 시간), 수량 변동 내역, **추가 입고·줄이기(옵션 지정)**. (A 6절)
  - 주문: 주문 번호·닉네임·회원 번호로 찾기(배송지는 암호문이라 찾지 못한다는 안내), 상태 탭 건수, 상세(주문 줄, 배송지 열람 사유, 운영 메모, 이력), 발주·준비중·발송(화면 안 표로 택배사·송장 일괄 입력, 200건, 행 단위 결과)·송장 정정·배송완료(일괄), **운영 환불**(결정 32), 배송 명단 내보내기. (A 95·120·121·123·140)
  - 취소·반품 요청 대기열: 경과 영업일, 환불 계산 미리 보기, 승인·거절·회수 확인·보상액, **줄마다 처리 방법과 수량**(다시 판매 n · 폐기 · 공급처 반송 · 보관 — 결정 15).
  - 공구 Q&A 대기열(2b-6): 답변 안 한 질문, 답변·수정(이력), 숨김, 신고 처리.
  - 대시보드 커머스 칸(19절): 준비 대기·열린 요청·결제 이상·곧 만료될 선점·남은 수량 적은 공구·**발송 뒤 N일 지난 SHIPPED**(배송완료 처리 누락 — 청약철회 기한이 시작되지 않는다)·답변 안 한 Q&A. (A 211)
  - `/settings/commerce` 구획: 배송비 정책(기간별 줄·왕복 경고) · 택배사 · 사업자 정보 · 판매 스위치·체크리스트 · 공구 설정.

## 마이그레이션 계획 (가번호 — develop 에 합칠 때 다시 확인, 지금 shared 마지막 V100 — 2a 보강 `payment_cancel_one_sending`)
| 가번호 | 스트림 | 내용 | 단위 |
|---|---|---|---|
| V101 `group_buy_core` | shared | `shipping_policies`(기간 배제 제약 + 적용 시작 줄 불변 트리거) · `group_buys` · `group_buy_images` · `group_buy_options` · `group_buy_revisions` · `group_buy_participations`(주문) · **`group_buy_participation_items`(주문 줄)** · `group_buy_qty_movements` · `group_buy_status_history` · `participation_status_history` · `group_buy_watches` · `group_buy_settings`(한 줄 — `point_shop_settings` 모양) | 2b-2 |
| V102 `group_buy_fulfillment` | shared | `commerce_carriers`(6개 시드) · `group_buy_shipments` · `group_buy_requests`(+ `received_at` 반환받은 날 — 3영업일 기한 시작) · **`group_buy_request_items`**(요청 줄 — 주문 줄·수량·**사유**·`refund_amount`) · ~~`group_buy_refunds`~~(결정 36 — 2b-2 가 V101 로 당겼다, 「2b-2 구현 결과」) · **`group_buy_return_dispositions`**(요청 줄·처리 방법·수량) · `group_buy_request_photos` · `commerce_address_views` · `commerce_bank_transfers`(결정 6) | 2b-3 |
| V103 `commerce_sales_switch` | shared | 판매 스위치·체크리스트·사업자 정보(`commerce_sales_settings` 등), `R__batch_32_groupbuy_privacy_cleanup`(배송지 마스킹·수정 이력 6개월·요청 3년·만료 주문 배송지 — 결정 34) | 2b-4 |
| V104 `group_buy_qna` | shared | `group_buy_questions` · `group_buy_answers` · `group_buy_answer_revisions`, 신고 대상 `GROUP_BUY_QUESTION`(기존 `reports` 의 대상 종류 CHECK), 파기 배치에 3년 더하기 | 2b-6 |
- Java 워커(카탈로그 없음, `worker_heartbeats`): `groupbuy-state`(1분 — 선점 만료·모집 시작·마감·연장·판정, 다 팔림 마감의 안전망), 운영자 기한 점검은 19절 점검 워커가 생길 때까지 2b-4 가 1시간 워커로.
- 열은 commerce.md 10-1 표가 바탕이고, 바뀌거나 더하는 것:
  - **주문과 주문 줄**(결정 23): `group_buy_participations` 는 주문 한 건(회원·배송지·배송비·금액·결제·상태·`order_no`·`admin_memo`·`refunded_amount`(상품·배송비 환불 누계)·`compensation_amount`(PG 로 돌려준 고객 택배비 보상 누계 — 결정 39)·`point_refunded`(11절 포인트 결제 대비, 2b 에서는 0)·`reshipping_fee_charged`). 옵션·수량·단가는 `group_buy_participation_items`(주문·옵션·`qty`·`unit_price`·`item_amount`·`canceled_qty`·`returned_qty`·`restocked_qty`, CHECK `0 ≤ restocked_qty ≤ returned_qty`, `canceled_qty + returned_qty ≤ qty`, 유일 `(participation_id, option_id)` — 같은 옵션 두 줄 금지). 옵션이 없는 공구는 줄 하나(옵션 null, 그 주문에 한 줄).
  - 수량 이동 줄에 `participation_item_id`(주문 줄)를 더한다.
  - 주문 CHECK `0 ≤ refunded_amount ≤ amount`(community 는 이 CHECK 로 이중 환불을 막았다 — D 512).
  - `group_buys`: `version`(설명·이미지 편집의 낙관 잠금 — 수량 열은 조건부 UPDATE 로 바꾸고 `version` 을 올리지 않는다), `closing_at`, `summary`·`spec`(평문 ≤ 5,000), `round_root_id`(1차 공구 ID, 1차는 자기 자신)·`round_no`(유일 `(round_root_id, round_no)`)·`round_limit_shared`(결정 24)·`added_qty`, CHECK `min_qty ≤ max_qty`.
  - `group_buy_revisions` 에 수량 사본(`max_qty`·`added_qty`, 옵션별 최대)을 더한다 — 추가 입고도 표시 기록. 주문은 결제 때의 `revision_no` 를 남긴다(낙관 잠금 `version` 과 다른 번호).
  - 수량 이동 종류: RESERVE·RELEASE·CONFIRM·CANCEL·RETURN(반품 기록, 수량 불변)·RESTOCK(다시 판매, 확정 수량 감소)·ADD(추가 입고, 최대 증가)·REDUCE(줄이기, 최대 감소). 이동 줄마다 `option_id` 와 공구·옵션 양쪽의 이후 값.
  - `group_buy_watches.kind` 에 `NEXT_ROUND`(다음 차수 알림 — 1차 공구 기준) 를 더한다.
  - 참여 `hold_expire_count` 는 두지 않고 `participation_status_history` 의 최근 24시간 EXPIRED 중 원인 `HOLD_TIMEOUT` 만 센다(결정 28).
- 요청 사진은 `group_buy_request_photos`(요청 ID 없이 먼저 올리고 붙인다) — 붙지 않은 사진은 24시간 뒤 정리(C 195).
- **최종 검토로 더하는 열·표**(결정 51~86):
  - V101 `group_buys`: `ship_planned_at`·`ship_within_business_days`·`initial_max_qty`·`close_reason`·`normal_price_basis`·`normal_price_source`·`withdrawal_restriction` · 옵션 `initial_max_qty` · 주문 `point_amount`·`cash_amount`·`shipped_shipping_fee` · `group_buy_watches.option_id` · **`group_buy_delay_notices`**(공구·이전/새 예정일·사유·운영자·시각) · 부분 유일 색인·복합 FK(결정 66).
  - V102 `group_buy_shipments`: `kind`(`ORIGINAL`·`EXCHANGE`)·`escrow_logistics_at`·`escrow_completed_at` · **`group_buy_escrow_jobs`** · 요청 `preview_amount`·`resolution`(`REFUND`·`EXCHANGE`)·`reject_reason_code` + 근거 사진 · `group_buy_refunds` 의 `method`·금액 나눔 열(결정 75)·원인 `SUPPLY_FAILURE`·`DELAY` · **`group_buy_supply_failures`**(공구·옵션·사유·기한) · **`group_buy_supply_choices`**(주문·선택 `KEEP_REST`/`CANCEL_ALL`·기한·정한 시각·기본 적용 여부).
  - 수량 이동 종류에 `EXCHANGE_OUT`(교환 출고 — 판매 수량 불변, 기록만).
  - 결제 모듈: `PaymentOrderType.GROUP_BUY_RETURN_FEE`(반품 비용 부족분 추가 결제 — 결정 68) 와 그 처리기(공구 패키지), `payment_orders.order_type` CHECK 에 더한다(V102).

## 패키지 (`groupbuy → payment`, `groupbuy → point` 단방향 — `payment` 는 공구를 모른다)
- shared `com.community.groupbuy`:
  - `entity`·`dto` — `GroupBuyStatus`·`ParticipationStatus`·`Fulfillment`·`ExtensionMode`·`ReturnFeeMode`·`QtyMovementKind`·`RequestType`·`RequestStatus`·`CollectionMethod`·`ReturnDisposition`·`ExpireCause`
  - `repository` — 수량·상태 전이는 JDBC(조건부 UPDATE, `SKIP LOCKED`), 관리자 조회는 JPA + QueryDSL
  - `service` — `GroupBuyAdminService`(등록·수정·모집 시작·조기 마감·중단·추가 차수) · `ShippingPolicyService` · `GroupBuyQuoteService`(견적 — 주문 때 다시 계산) · **`GroupBuyJoinService`**(자격 → 선점 → 금액 → 배송지 암호화 → 주문 PENDING → `PaymentOrderService.create`) · **`GroupBuyQtyService`**(RESERVE·RELEASE·CONFIRM·CANCEL·RETURN·RESTOCK·ADD·REDUCE — 수량을 바꾸는 유일한 길, 공구 행 → 옵션 행 차례로 잠금, 이동 줄 하나씩) · `GroupBuyStateWorker.runOnce` · `GroupBuyJudgeService`(성사·불발 — 불발은 주문 REFUNDED + CANCEL 이동 + `PaymentCancelService` 전액) · `ParticipationCancelService` · `FulfillmentService`(발주·준비중·발송·배송완료) · `GroupBuyRequestService` · **`GroupBuyRefundCalculator`**(한 곳 — 요청·승인 화면이 같은 값, 재부과 배송비 누계로 차액만) · `AddressRevealService` · `GroupBuyInvariantCheck`
  - `handler` — `GroupBuyPaymentHandler implements PaymentOrderHandler`(`GROUP_BUY` — `onPaid`: 8-6 표 판정 → JOINED/IMMEDIATE 면 CONFIRMED + CONFIRM(+ 다 팔리면 같은 트랜잭션에서 CLOSING — 결정 27), 아니면 REJECT(`GROUP_BUY_CLOSED`·`HOLD_EXPIRED`) · `onCanceled`: **표시·알림만**(환불 기록 `group_buy_refunds` 를 완료로, 환불 완료 알림·메일) — 환불 누계는 취소를 넣을 때 이미 올렸다(결정 38), 자동 취소(`anomaly != null`)는 회원 안내만(결정 40). 결제 행을 쥔 채 불리므로 **공구 행을 `FOR UPDATE` 하지 않는다**(결정 37))
  - `qna`(2b-6) — 질문·답변·답변 이력·숨김·개인정보 감지
- api `groupbuy.controller`: 목록·상세·견적·주문(`MoneyRequestGuard` — 멱등 키·속도 제한)·결제 다시 열기·내 주문·본인 취소·요청·배송지 보기/수정·관심 신청·Q&A.
- admin `groupbuy.controller`: 공구·수량·주문·발주/발송·요청·배송지 열람·명단 내보내기·배송비 정책·택배사·설정·Q&A, `batch.service.GroupBuyStateScheduler`(1분, `roboyam.batch.enabled` 서버만 — `PaymentCancelScheduler` 모양), 관리자 이미지 올리기(결정 2).

## 세션 정리 결정 (제안 — 추천안, 사용자 결정 표시한 것은 확정)
1. **화면 경로(회원)**: `MenuBuiltin` 그대로 `/group-buys`(목록) · `/group-buys/{id}`(상세) · `/group-buys/{id}/order`(주문서 → 결제창) · `/me/orders`(내 주문) · `/me/orders/{orderNo}`(상세 — 취소·반품 요청·배송지). 결제창은 `/dev/payment` 의 SDK·창 닫기 판정(`UtilPortone`)을 공용 컴포저블로 옮겨 쓴다.
2. **이미지·설명**: 상세 설명은 공용 편집기 `@roboyam/rich-editor`(admin-web 이미 의존 — 글 본문과 같은 서버 정화를 거친다) + 평문 `summary`·`spec`. community 는 XSS 를 피하려 평문 + 줄바꿈만 받았지만(C 39), roboyam 은 이미 정화된 편집기가 있고 상품 설명에 본문 이미지가 필요하다. 수정 이력은 정화된 HTML 사본. 대표 이미지는 최대 8장 · JPEG·PNG·WebP 정지 이미지 · 5MB · 다시 인코딩해 EXIF 제거 · 긴 변 1600px·썸네일 400px · 순서 바꾸기(community A 70 규칙). 관리자 이미지 올리기 API 를 새로 두고 저장은 기존 미디어 저장소(`roboyam.storage`). 차수끼리 같은 미디어를 쓰므로 미디어는 쓰는 공구가 하나도 없을 때만 지운다.
3. **관리자 메뉴**: 보통 모드 새 묶음 「공구」(결제 묶음 앞) — 공구 · 주문 · 취소·반품 요청 · Q&A. 배송비 정책·택배사·사업자 정보·판매 스위치·공구 설정은 `/settings/commerce` 에 구획으로 더한다(0-5 계약대로). 권한은 2b 에서 쓰는 것만 `featureReady=true`(결정 35).
4. **에스크로**: `PaymentGateway` 에 「배송 정보 등록·수정」을 더하고 발송 처리 때 에스크로 결제 건이면 부른다(가짜 PG 는 기록만). 이니시스 테스트 채널의 에스크로 적용 범위는 2b-5 에서 확인하고, 그 전에는 주문서에 「결제대금예치 선택 가능」 고지만.
5. **현금영수증**: 카드·간편결제만 받으므로(4절) 주문서 입력은 두지 않고 표 열만 둔다. community 도 입력칸은 뒀지만 「SDK 현금영수증 인자는 가상계좌·계좌이체용이라 카드 결제에는 싣지 않는다」로 정했다(D 513). 승인되면 commerce.md 10-6 1번의 「현금영수증 선택」을 고친다.
6. **보상액이 남은 결제액을 넘는 부분**: 9-3 의 계좌 송금을 2b-3 에서 공용 표 `commerce_bank_transfers`(계좌 암호문 — `ROBOYAM_DATA_KEY`, 「송금 완료」 후 계좌 삭제)로 먼저 만들고 3단계 충전 환불이 이어 쓴다.
7. **배송완료**: 택배사 API 가 범위 밖이라 운영자가 처리(일괄 가능). 자동 배송완료는 두지 않는다(청약철회 7일의 시작점이라 추측하지 않는다). 처리 누락은 대시보드 「발송 뒤 N일 지난 SHIPPED」로 잡는다.
8. **영업일**: 주말 + 관리자가 넣는 휴일 목록(`group_buy_settings` 의 휴일 날짜 배열, 설정 화면에서 편집). 공휴일 API 는 쓰지 않는다. 2·3영업일 기한 경고(P35)와 환불 기한 표시가 같이 쓴다.
9. **공구 COMPLETED**: 선착순 즉시 발송은 마감됐고 수량에 들어간 주문(결정 28)이 모두 DELIVERED·CANCELED·REFUNDED 이면. 마감 후 발주는 `ORDERED → SHIPPING`(첫 발송 처리 때) `→ COMPLETED`(같은 조건). **판단은 배송완료 트랜잭션에서 하지 않고 1분 워커의 조건부 sweep**(`… WHERE NOT EXISTS 진행 중 주문`) — 마지막 두 주문이 동시에 배송완료돼 서로 못 보는 경쟁을 피한다(최종 검토). 반품 요청은 COMPLETED 뒤에도 받는다(공구 상태가 아니라 주문·요청 기준).
10. **공구 설정(설정값)**: 선점 시간 5분(3~30) · 성사형 기간 상한 14일 · 선착순 30일 · 연장 최대 30분 · 연장 판단 창 5분 · 판정 여유 2분 · 고객 택배비 보상 상한 10,000원 · 고액 환불 문턱 100,000원 · 배송지 열람 화면 유지 10분 · 휴일 목록 · 만료 주문 배송지 보관 30일. 바꿀 때 감사(전→후).
11. **택배사·local 시드**: 택배사는 **마이그레이션 시드(전 환경)** 로 6개(`CJ`·`HANJIN`·`LOTTE`·`EPOST`·`LOGEN`·`KDEXP`) + 조회 주소 템플릿(`https://`, `{no}` 한 번). 목록은 통째로 저장, 발송 이력이 있는 코드는 지우지 못하고 끄기만, 송장번호는 숫자·영문 6~30자로 정리(community A 118·178). local 시드는 판매 스위치 켜짐·체크리스트 전부 체크·시험 사업자 정보·배송비 정책 `STANDARD`(3,000 · 무료 50,000 · 반품 3,000 · 편도) + 시험 공구 넷(성사형 모집 중 · 선착순 즉시 발송(옵션 셋) · 마감 임박 · 다 팔려 끝난 1차) — `seed/` 와 `PortOneLocalSeeder` 모양(local 만).
12. **운영자 알림**: 예약된 `COMMERCE_ORDER_ANOMALY`(공구 결제 REJECT·불변식 불일치) · `COMMERCE_REFUND_REQUESTED`(취소·반품 요청 — WARNING, 2영업일 경고 · 3영업일 CRITICAL) + 새 `COMMERCE_GROUPBUY_CANCELED`(중단)·`COMMERCE_SALES_TOGGLE`·`COMMERCE_GROUPBUY_QUESTION`(새 질문·신고 — INFO, 묶기). 받는 사람 = `commerce.order.manage`·`commerce.refund.approve` 보유자.
13. **주문 번호**: 주문마다 `G` + KST 날짜 `yyyyMMdd` + 그날 일련 5자리(예 `G2026102800012`, 하루 99,999건 — 넘으면 자리를 늘린다). 회원 주소 `/me/orders/{orderNo}`, 결제창 주문명·메일·고객 응대에 쓴다. 포트원 `redirectUrl` 은 쿼리 없이 주문 상세로 돌아온다(D 513). (community 는 4자리 시퀀스 — C 43)
14. **에스크로 구매 확정**: 에스크로 결제 건은 배송완료 뒤 청약철회 기간(7일)이 열린 요청 없이 지나면 `escrow/complete` 를 부른다(하루 한 번, 실패는 재시도·결제 이상). 송장 정정 때는 `PATCH` 로 배송 정보를 다시 보낸다. 실제 동작은 2b-5 에서 확인.
15. **반품 상품 처리**(사용자 결정): 반품 완료 때 운영자가 **요청 줄마다 처리 방법과 수량**을 고른다(필수, 합 = 그 줄의 반품 수량 — 예 「2개 다시 판매 + 1개 폐기」).
    - **다시 판매**(`RESELL`): 공구가 **모집 중**(`RECRUITING`)이고 **사유가 단순 변심**(`CHANGE_OF_MIND`)일 때만. 하자·오배송은 서버가 거절(`GROUP_BUY_NOT_RESELLABLE`). 그 옵션·공구의 확정 수량을 줄여(이동 `RESTOCK`, 조건 `status='RECRUITING'`) 남은 수량이 늘고, 수량 풀림 알림. 반품은 배송완료 뒤에만 생기므로 실제로는 선착순 즉시 발송 공구에서만 고를 수 있다 — 화면은 다른 공구에서 이 선택지를 숨긴다.
    - **폐기·공급처 반송·보관**(`DISCARD`·`RETURN_TO_SUPPLIER`·`KEEP`): 공구 수량은 그대로, 이동 `RETURN` 으로 기록만.
    - 수량 화면에 판매·반품·다시 판매를 나눠 보인다. 성사 판정은 뒤집지 않는다(10-4). (community 는 전체 반품만 자동 재입고 — A 138)
16. **환불 진행 표시**: 주문은 같은 트랜잭션에서 REFUNDED·CANCELED 로 바뀌지만 돈은 취소 워커가 보낸다. 회원·관리자 화면은 `group_buy_refunds` 에 남긴 `payment_cancel_id`(결정 36)로 결제 취소 상태를 읽어 「환불 진행 중 · 카드사 반영까지 3~7영업일」/「환불 완료(날짜)」/「환불 지연 — 확인 중」(GAVE_UP)/「운영자가 직접 처리함」(MANUAL)을 나눠 보인다. MANUAL 은 지금 `onCanceled` 가 오지 않으므로 결제 모듈의 `PaymentOrderHandler` 에 기본 메서드 `onCancelResolvedManually(…)` 를 더하고 `PaymentAdminActionService` 「수동 처리 완료」가 같은 트랜잭션에서 부른다(결제 모듈은 여전히 도메인을 모른다 — 2b-3 에서 결제 모듈을 조금 고친다). 공구 처리기는 환불 기록을 완료로 두고 회원에게 「환불 처리됨(운영자 확인)」 알림·메일. community 의 「취소됐지만 환불 실패」 상태(A 141)를 이 표시로 대신한다.
17. **모집 뒤 수량 조정 · 추가 입고**(사용자 결정 — commerce.md 10-2 를 고쳤다, 권한 `commerce.stock.adjust`):
    - 「품절」은 둘이다. **잠시 품절**(확정 + 선점 = 최대, 공구는 모집 중 — 선점이 5분 안에 풀릴 수 있다)과 **다 팔림**(확정 = 최대 → 그 자리에서 `CLOSING` → 판정, 결정 27). 옵션도 같다 — 옵션 하나가 다 팔려도 공구는 그 옵션만 「다 팔림」이고, **모든 옵션이 다 팔리거나 공구 최대에 닿으면** 공구가 마감된다.
    - **늘리기(추가 입고)**: 공구가 `RECRUITING` 이고 다 팔리기 전(잠시 품절 포함)에만 — 조건부 UPDATE `status='RECRUITING' AND current_qty < max_qty`. 옵션을 지정하면 옵션 최대와 공구 최대를 한 트랜잭션에서(공구 행 → 옵션 행 차례). 이동 `ADD`, `added_qty` 누계, 상세에 「추가 입고 N개」(줄이기 뒤에는 순증가분만), 수정 이력·감사(돈 관련), 수량 풀림 알림. 사유 필수.
    - **줄이기**: 품절·불량·입고 부족. `RECRUITING` 이고 하한 = max(`확정 + 선점`, 성사형이면 `min_qty`). 이동 `REDUCE`, 사유 필수, 감사. 줄인 결과 확정 = 최대이면 같은 트랜잭션에서 CLOSING.
    - **다 팔린 뒤 추가 입고 = 추가 차수 공구**: 결정 31.
    - 모집 뒤 가격·기간·옵션 구성·배송비는 지금처럼 바꿀 수 없다.
18. **사업자 정보와 공개 API**: 칸은 상호·대표자·사업자등록번호(체크섬)·통신판매업 신고번호·신고 기관·주소·전화·이메일 + **운영시간·개인정보 관리책임자·호스팅 사업자·배송/환불 정책 문구(≤ 3,000)**(community A 195). 평문 저장. 회원 공개 `GET /api/commerce/info`(값 + `ready`) — 공구 화면 하단. 판매 켜기 자동 조건에 community 의 「공개된 공구 1개 이상」은 넣지 않는다(공구를 열기 전에 판매를 켜 두는 순서를 막지 않게).
19. **설정 읽기**: 선점 시간·배송비·문턱처럼 돈과 시각에 닿는 값은 **그 트랜잭션 안에서 DB 의 지금 값**을 읽는다(캐시 금지 — community A 176·S 290). 화면 표시용만 캐시.
20. **내보내기 안전**: 배송 명단 CSV 는 `= + - @` 로 시작하는 칸 앞에 `'` 를 붙인다(수식 주입 — community A 163).
21. **공구 Q&A**(사용자 결정): 별도 단위 2b-6 — 아래 「Q&A 규칙」.
22. **관리자 날짜 필터**: 한국 날짜, 양 끝 포함(community D 640).
23. **한 주문에 여러 옵션**(사용자 결정): 주문서에서 옵션 여러 줄 × 수량을 한 번에 산다. 결제·배송비·배송지·주문 번호는 주문 단위, 수량·선점·반품·다시 판매는 줄 단위. 선점은 줄마다 옵션 행 + 공구 행을 한 트랜잭션에서(하나라도 모자라면 전체 실패, 잠금은 공구 행 → 옵션 ID 오름차순). 배송비·무료배송 기준은 주문 전체 상품 금액으로. 공구당 **진행 중 주문은 1건**(PENDING_PAYMENT~SHIPPED) — 더 사려면 배송완료 뒤 새 주문이거나 취소 후 다시 주문(결정 28).
24. **차수 사이 1인 한도**(사용자 결정): 공구마다 `round_limit_shared` 를 고른다(기본 꺼짐 = 차수마다 따로). 켜면 같은 `round_root_id` 의 모든 차수에서 산 수량을 합쳐 센다.
25. **연장 조건 넓힘**(사용자 결정 — commerce.md 10-2 를 고친다): `DEMAND` 공구는 마감 전 5분 안에 선점 만료뿐 아니라 **다시 판매(RESTOCK)·추가 입고(ADD)로 수량이 늘었을 때**도 연장한다(수량 풀림 알림을 받은 회원이 들어오기 전에 마감되지 않게).
26. **Q&A 관리**(사용자 결정): 개인정보 자동 비밀글 + 작성자 안내 · 신고 대상 · 단순 문의도 3년 보관 · 답변 수정 이력 — 「Q&A 규칙」.

## 수량·상태 규칙 (검토 보강 2026-10-28)
27. **다 팔림은 그 자리에서 마감**: 결제 확정(`onPaid` 의 CONFIRM)·줄이기(REDUCE)가 확정 = 최대를 만들면 **같은 트랜잭션**에서 `RECRUITING → CLOSING`(`closing_at`, 상태 이력, 종료 사유 `SOLD_OUT`). 1분 워커(10-7 ③)는 안전망으로만 남는다. 추가 입고·다시 판매는 `status='RECRUITING' AND current_qty < max_qty` 조건부라 마감과 경쟁해도 둘 중 하나만 이긴다.
28. **수량에 들어가는 주문과 불변식**: 결제 확정(CONFIRM)된 주문 줄의 `qty − canceled_qty − restocked_qty` 가 「판매 수량」이다. 발송 전 취소·불발·중단 환불은 CANCEL 이동 + `canceled_qty`, 반품은 `returned_qty`(폐기·반송·보관이면 판매 수량 그대로 — 이미 팔려 나간 것), 다시 판매는 RESTOCK + `restocked_qty`. **불변식**: 공구·옵션별 `current_qty` = Σ 판매 수량, `reserved_qty` = Σ PENDING_PAYMENT 줄 수량, 이동 줄 마지막 값 = 현재 값(commerce.md 18절을 이 식으로 고친다). **1인 한도**는 재고 기준과 다르다 — 그 회원의 Σ `qty − canceled_qty − returned_qty`(반품은 처리 방법과 관계없이 뺀다 — 돌려준 것은 그 회원이 가진 것이 아니다, 10-4 「한도는 취소·환불 수량을 뺀다」) + 진행 중 선점(+ 결정 24 합산). 판매 수량(재고 기준)은 폐기·보관한 반품을 빼지 않는다 — 다시 팔 수 없는 물건이라 남은 수량으로 돌아오면 안 된다. **진행 중 주문**(부분 유일) = PENDING_PAYMENT·JOINED·CONFIRMED·PREPARING·SHIPPED. **만료 원인**(`ExpireCause`): `HOLD_TIMEOUT`(선점 시간 초과 — 이탈 횟수에 센다)·`GROUP_BUY_CANCELED`·`PAYMENT_FAILED_CLOSED` 등 — 이탈 제한(`GROUP_BUY_BAN`)은 `HOLD_TIMEOUT` 만 센다.
29. **중단**: 공구 상태 전이 뒤 주문마다 새 트랜잭션에서(결정 37 — 교착 방지) ① PENDING_PAYMENT → EXPIRED(원인 `GROUP_BUY_CANCELED`) + RELEASE + 연결된 `payment_orders` READY → EXPIRED(뒤늦은 결제는 늦은 결제로 자동 취소) ② **발송 전**(JOINED·CONFIRMED·PREPARING) → REFUNDED + CANCEL + 전액 취소 ③ **이미 발송**(SHIPPED·DELIVERED)은 그대로 정상 흐름(반품은 요청으로). 확인 창에 「환불 N명 · 총 N원 / 이미 발송 M명」.
30. **배송지 수정·명단 내보내기 기한은 주문 기준**: 수정은 마감 후 발주 공구면 공구 ORDERED 전, 즉시 발송 공구면 주문 PREPARING 전. 명단 내보내기는 「발송 전 주문(CONFIRMED·PREPARING)」 단위 — 마감 후 발주 공구는 ORDERED 뒤, 즉시 발송 공구는 언제든(commerce.md 10-5·10-9 를 고친다).
31. **추가 차수**: 원본은 모집이 끝난 공구(CLOSING 이후 — 다 팔림·기간 끝·완료, 중단 제외). 같은 루트에 DRAFT·RECRUITING 차수가 이미 있으면 거절(동시 클릭은 유일 `(round_root_id, round_no)` 가 막는다). `round_root_id` = 1차 공구, `round_no` = 그 루트의 최대 + 1. 복사: 설명·이미지·`summary`·`spec`·옵션 이름·추가금·방식(성사형/선착순·발송 시점·연장 방식)·`round_limit_shared`. 새로 넣음: 수량·기간·가격(복사값을 채워 두되 DRAFT 라 고칠 수 있다). 배송비는 모집 시작 때 정책. 복사할 때 「이전 차수의 날짜·수량·배송비 문구가 설명에 남아 있는지 확인하세요」 경고, 모집 시작 때 수정 이력 첫 판. 모집을 시작하면 루트의 모든 차수의 `NEXT_ROUND`·`RESTOCK`·`CLOSING_SOON` 신청자에게 한 번 알림(같은 회원 한 번), 신청은 새 차수로 옮기지 않는다.
32. **운영 환불**(요청 없이, `commerce.refund.approve` · 고액 문턱 `commerce.refund.large`, 사유 필수): 발송 전 주문(JOINED·CONFIRMED·PREPARING)은 「발송 전 취소」와 같다(CANCELED + CANCEL 이동, 줄·수량 지정 가능). 배송완료 주문은 **금액만 돌려주는 환불**(회수 없음 — 보상·가격 조정)로 수량 이동 없이 `refunded_amount` 만, 누계가 결제액이 되면 REFUNDED. SHIPPED 주문은 거절(배송완료 뒤 또는 반품 요청으로).
33. **연속 부분 반품의 배송비**: 주문에 `reshipping_fee_charged`(지금까지 재부과한 배송비) — `GroupBuyRefundCalculator` 는 「남은 수량 기준 배송비 − 이미 재부과한 것」의 차액만 뺀다. 마지막 남은 것을 반품하면 전체 반품 규칙(편도·왕복)으로 다시 맞춘다.
34. **배송지 파기 보강**: 결제하지 못한 주문(EXPIRED)은 만료 30일 뒤(설정값) 배송지 암호문 삭제. 불발·중단 환불(REFUNDED)은 commerce.md 10-9 「취소 후 6개월」.
35. **권한**(commerce.md 13-2 에 더한다): 공구 중단 `commerce.groupbuy.cancel`(이미 있음 — 2b 에서 `featureReady=true`) · 추가 입고·줄이기 `commerce.stock.adjust` · 추가 차수 만들기 `commerce.product.manage` · 반품 처리 방법(다시 판매 포함) `commerce.order.manage`(반품 완료 처리와 같은 사람) · Q&A 답변·숨김·신고 처리 `commerce.order.manage`, Q&A 조회 `commerce.order.read`.

## 2차 검토 보강 (Fable 검토 2026-10-28 — 결제 코드와 대조해 확인)
36. **환불 기록 표 `group_buy_refunds`**: 공구 쪽에서 돈을 돌려주는 모든 일 — 요청 승인·본인 즉시 취소·불발·중단·운영 환불 — 을 한 줄씩: 주문, 원인(`REQUEST`·`USER_CANCEL`·`FAILED`·`CANCELED`·`ADMIN`), 요청 ID(있으면), 상품·배송비 환불액, 보상액, `payment_cancel_id`(`PaymentCancelService.request` 가 돌려준 번호), 요청 키 `GB_REFUND:{id}`, 상태(진행 중·완료·지연·수동). 줄별 금액은 `group_buy_request_items.refund_amount`(요청) 또는 이 표의 줄 내역(jsonb, 요청 없는 환불). 결제 모듈은 바꾸지 않는다(취소 대기열 요청 키·결과로 연결). `CancelKind` 매핑: 본인 취소 `USER_CANCEL` · 불발·중단·반품 `REFUND` · 운영 환불 `ADMIN`.
37. **잠금 차례 — 교착 막기**(코드 확인: 결제 확정·취소 완료는 `payment_orders` 행을 잠근 채 처리기를 부르고, 취소 넣기는 안에서 `payment_orders` 행을 잠근다):
    - `onPaid`·`onCanceled` 안에서는 공구·옵션 행을 `FOR UPDATE` 하지 않고 **조건부 UPDATE 만** 쓴다(수량 확정 `UPDATE … SET current_qty = current_qty + :q, reserved_qty = reserved_qty - :q WHERE … AND status IN (…)`, 다 팔림 마감도 조건부).
    - 공구 행을 잠그는 일(중단·판정·추가 입고·줄이기·추가 차수)은 **결제 행을 잠그는 일과 같은 트랜잭션에 넣지 않는다**: 중단·불발 판정은 ① 공구 상태 전이만 짧은 트랜잭션 ② 주문마다 새 트랜잭션에서 「주문 조건부 전이 → 수량 CANCEL → 환불 기록 → 취소 넣기」(조건부라 다시 돌려도 한 번 — 워커가 이어 받는다). commerce.md 10-7 「활성 참여마다 같은 트랜잭션」을 이렇게 고친다.
    - 주문 하나 안에서는 주문 행 → (조건부) 공구·옵션 수량 → 결제 행 차례. **단, 이 차례는 결제가 PAID 인(취소를 넣는) 흐름에만**. 결제가 아직 READY 인 주문을 닫는 흐름(선점 만료·결제 다시 열기·중단의 결제 중 주문·조회 시 정리·REJECT)은 **결제 행을 먼저**: `UPDATE payment_orders SET status='EXPIRED' WHERE id=:p AND status='READY'`(0 행이어도 계속) → 주문 조건부 전이 → 이긴 쪽만 RELEASE. 결제 확정이 결제 행 → 주문 차례이므로 같은 순서가 된다(최종 검토 — 코드 `PaymentConfirmService.settle`·`PaymentOrderStore.expireOpenReady` 로 확인).
    - 어드바이저리 잠금 `pg_advisory_xact_lock`(키 `gb:{공구}:{회원}`, 트랜잭션 범위)은 **모든 행 잠금보다 먼저**: A → G → O(ID 오름차순, 한 줄씩 — `IN (…)` 한 문장은 스캔 순서로 잠근다) → X.
    - 판정 ①은 `SELECT … FOR UPDATE` 로 공구 행을 잠근 뒤 판단·전이(또는 WHERE 에 `current_qty < min_qty AND …` 를 넣어 재평가되게) — 마지막 결제 확정과 겹쳐 성사 수량인데 불발이 되는 일을 막는다.
38. **환불 누계는 취소를 넣을 때 올린다**: `refunded_amount` 는 취소를 대기열에 넣는 트랜잭션에서 조건부로 올린다(`WHERE refunded_amount + :amt <= amount`) — 공구가 상한의 정본이다. PG 가 GAVE_UP·MANUAL 이어도 같은 금액을 두 번 넣지 못한다. 「남은 결제액」= `cash_amount − refunded_amount − compensation_amount`(최종 검토 — 보상을 빼지 않으면 공구는 통과하고 결제 모듈이 409 로 되돌린다), CHECK `refunded_amount + compensation_amount ≤ cash_amount`, 조건부 UPDATE 도 둘을 합쳐 비교.
39. **보상액은 따로 센다**: PG 취소에 함께 담은 고객 택배비 보상은 `compensation_amount`, 계좌 송금분은 `commerce_bank_transfers`. 불변식: `payment_orders.paid_amount − canceled_amount` = `amount − refunded_amount − compensation_amount`(+ 진행 중 취소분 — 넣은 뒤 완료 전). 매출 집계는 상품 환불과 보상을 가른다.
40. **자동 취소의 `onCanceled`**: 늦은 결제·도메인 거절(`anomaly != null`)의 취소 완료는 주문 금액·상태를 건드리지 않고 회원 안내(commerce.md 8-2 5 문구)만.
41. **선점과 결제 유효 시각을 같게**: 결제 주문 `expires_at = reserved_until`(`PaymentOrderService.create` 의 유효 시간 = `reserved_until − 지금`). 「결제 다시 열기」도 같은 끝 시각 — 선점을 늘리지 않는다, 남은 시간이 없으면 `GROUP_BUY_HOLD_EXPIRED`.
42. **발송 전 일부 줄 취소의 배송비**(사용자 결정 — commerce.md 10-3 D11 을 고친다): 주문 전체 취소면 전액. **일부 줄만 취소해 무료배송 기준 아래로 떨어지면 남은 상품 기준으로 배송비를 다시 계산**해 환불액에서 뺀다(반품비 없음, `reshipping_fee_charged` 에 누계) — 발송 후 부분 반품과 같은 계산(`GroupBuyRefundCalculator` 한 길). 본인 즉시 취소도 줄·수량 단위.
43. **나눠서 모두 반품하면 앞서 뺀 배송비를 돌려준다**(사용자 결정): 마지막 반품으로 주문 전체가 반품되면 **처음 배송비 처리만** 한 번에 전체 반품한 결과와 같게 맞춘다 — 편도면 처음 배송비 0 이 되도록 그동안 재부과한 배송비(`reshipping_fee_charged`)를 그 환불에 더해 돌려주고, 왕복이면 처음 배송비를 한 번만 받은 것으로 맞춘다. 반품비는 실제로 보낸 택배(요청)마다 받는다(나눠 보내면 택배비가 실제로 여러 번 든다). **이 환급은 발송 뒤 반품으로 생긴 재부과에만** — 발송 전 일부 취소(결정 42)로 재부과한 배송비는 그 택배를 실제로 보내는 데 쓴 배송비라 「처음 배송비」로 본다(발송 처리 때 `shipped_shipping_fee = shipping_fee + 그때까지의 재부과` 를 고정하고, 계산기는 이 값으로 「낸 주문 / 무료배송 주문」을 판단한다 — 최종 검토). 그다음은 10-8 기준 표 그대로: **단순 변심 전체 반품이면 처음 배송비를 돌려주지 않고(낸 주문), 하자·오배송 전체 반품이면 돌려준다(업체 부담)**. 처음부터 1개만 배송비를 내고 산 회원과 같은 결과다(재검토 2026-10-28 — 앞 문장이 하자도 돌려주지 않는 것으로 읽혀 고침). G7 기대값: 25,000 × 3 무료배송(편도, 업체회수, 반품비 3,000) — 1개 반품 22,000(남은 50,000 ≥ 기준) → 1개 반품 19,000(재부과 3,000 + 반품비 3,000) → 마지막 1개 25,000 − 3,000(반품비) + 3,000(재부과 돌려줌) = 25,000. 합계 66,000 = 75,000 − 반품비 3,000 × 3.
44. **반품 요청은 줄마다 사유**(사용자 결정): 한 요청(한 택배) 안에 줄마다 사유(단순 변심·하자·오배송·기타)와 수량. 반품 배송비·보상액은 요청당 한 번 — 하자·오배송 줄이 하나라도 있으면 반품 배송비는 운영자 부담. 줄별 환불액은 줄 사유로 계산(하자 줄은 재계산 없이 전액, 변심 줄은 10-8 표). 주문당 열린 요청 1개는 그대로.
45. **모집 끝난 뒤 집계 중(CLOSING) 본인 즉시 취소 허용**(사용자 결정): 성사형 JOINED 주문은 CLOSING 중에도 본인이 바로 취소할 수 있다(청약철회). 판정은 판정 시점의 수량으로 — 이 취소로 불발이 될 수도 있다. 판정과 취소는 주문 조건부 전이로 하나만 이긴다.
46. **주문 상태는 줄 전체로**: 주문 CANCELED 는 Σ(`qty − canceled_qty`) = 0(발송 전 전부 취소), REFUNDED 는 Σ(`qty − canceled_qty − returned_qty`) = 0(받은 뒤 전부 반품 — 반품비가 남아 환불 누계가 결제액에 못 닿아도) 또는 운영 환불 누계가 남은 결제액을 모두 쓴 때(최종 검토: 금액 조건만으로는 반품비 때문에 영원히 REFUNDED 가 되지 않았다). REFUNDED·CANCELED 주문에는 운영 환불을 더 넣지 못한다. 일부 줄만 취소·반품되면 상태는 그대로이고 줄 수량만 바뀐다. 「진행 중 주문 1건」·COMPLETED 조건(결정 9)이 이 정의를 쓴다.
47. **발송은 주문 한 번에**: 줄마다 따로 발송하지 않는다(`group_buy_shipments` 는 주문당). 줄 하나라도 열린 요청이 있으면 주문 전체를 발송하지 못한다(10-3).
48. **옵션 전부 다 팔림도 마감**: 모든 옵션에 한도가 있고 모두 확정 = 한도이면(옵션 한도 합 < 공구 최대여도) 결정 27 과 같이 그 자리에서 CLOSING(종료 사유 `SOLD_OUT`). 옵션 줄이기 하한은 그 옵션의 확정 + 선점.
49. **중단·종료 공구의 남은 일**: 공구 CANCELED 뒤에도 이미 발송된 주문의 배송완료·반품 요청·환불은 계속된다. 마감 후 발주 공구의 `SHIPPING → CANCELED`(발송 일부만 된 뒤 남은 수량을 못 보낼 때)를 허용한다(commerce.md 10-2 전이 목록에 더한다) — 발송 전 주문만 환불(결정 29).
50. **판정 경쟁**: 마감 시각의 연장 판단과 마지막 1개 확정은 둘 다 `status='RECRUITING'` 조건부라 하나만 이긴다(확정이 이기면 SOLD_OUT 마감, 연장이 이기면 연장 중 판매).

## 최종 검토 보강 (2026-10-28 — 트랜잭션·재고·금액·법/관행 네 관점, 코드·법령 사본으로 대조)
### 법·실제 공구 운영
51. **발송 예정일(공급시기 약정)** — 전자상거래법 제15조 ①: 선지급식은 공급시기를 따로 약정하지 않으면 대금 지급일부터 3영업일 안에 공급 조치. 그래서:
    - 성사형·마감 후 발주 공구는 `ship_planned_at`(발송 예정일)이 필수, 선착순 즉시 발송 공구는 `ship_within_business_days`(결제 후 N영업일, 기본 3 — 공구 설정값). 모집 시작 뒤 잠금 필드.
    - 상세·주문서·계약 통지 메일에 「발송 예정일」, 주문서 동의 「구매 조건」에 포함.
    - 예정일·기한을 넘긴 발송 전 주문은 대시보드·운영자 알림(경고, 19절 알람에 더함).
52. **발송 지연 공지**(사용자 결정 — 통지 + 취소권만): 운영자 「발송 지연 공지」(새 예정일·사유 필수) → `group_buy_delay_notices` 에 남기고 발송 전 주문 전원에게 앱 + 메일(제15조 ② 지체 없는 통지). 그때부터 그 주문들은 **승인 없이 즉시 전액 취소**할 수 있다(성사 뒤·준비중이어도 — 사업자 사정이므로 배송비 재부과 없음). 보상(포인트 등)은 두지 않는다 — 나중에 붙일 자리만(지연 공지 행에 보상 칸 없음). 수정 이력에 예정일 변경을 남긴다.
53. **일부 옵션 공급 불가**(사용자 결정 — 답이 없으면 나머지만 받기):
    - 관리자 「옵션 공급 불가 처리」: 옵션 지정 → 그 옵션의 발송 전 줄 전부 CANCEL + 환불(원인 `SUPPLY_FAILURE` — **배송비 재계산 없음**, 사업자 귀책) + 그 옵션 최대를 확정 수량으로 줄임(줄이기 하한 예외, 이동 `REDUCE`) + 모집 중이면 그 옵션 판매 중지. 감사 `GROUPBUY_SUPPLY_FAILURE`.
    - 그 옵션 말고도 줄이 남은 주문의 회원에게 앱 + 메일로 「나머지만 받기 / 전체 취소」를 묻는다(`group_buy_supply_choices`, 기한 3영업일 설정값). 전체 취소를 고르면 남은 줄도 전액(처음 배송비 포함) 환불, **기한까지 답이 없으면 나머지만 받기**. 기한 전에는 그 주문을 발송하지 않는다.
    - 공급 불가로 줄이 모두 빠진 주문은 전액(배송비 포함) 환불.
    - 운영 환불(결정 32)에 사유 「사업자 귀책」 표시를 두고, 귀책이면 계산기가 재부과·반품비를 적용하지 않는다.
54. **교환 — 하자·오배송만**(사용자 결정): 하자·오배송·배송 중 훼손 반품 요청은 회원이 「환불」 또는 「교환」을 고른다(소비자분쟁해결기준 「교환 또는 구입가 환급」). 교환은 회수 확인 뒤 **같은 주문에 재발송**(`group_buy_shipments.kind = EXCHANGE`, 환불 0, 판매 수량 그대로 — 재고 소진은 「교환 출고」 이동으로 기록, 반품비·재발송비 운영자 부담). 공구가 끝났거나 교환할 물건이 없으면 운영자가 환불로 바꾼다(회원 알림). 단순 변심 교환은 받지 않고 「환불 뒤 다시 주문」 안내. 교환 출고분은 에스크로 배송 정보도 다시 등록.
55. **청약철회 제한 표시·반품 거절 근거** — 제17조 ②⑥⑤:
    - 공구별 `withdrawal_restriction`(제한 사유 안내 문구, 선택) — 상세·주문서·계약 메일에 표시(⑥ 「쉽게 알 수 있는 곳에 명확히 표시」하지 않으면 제한할 수 없다).
    - 반품 거절은 사유 코드 필수(`CUSTOMER_DAMAGE`·`USED_VALUE_LOSS`·`TIME_VALUE_LOSS`·`OUT_OF_WINDOW`·`ALREADY_SHIPPED`) + 운영자 근거 사진 최대 3장 — ⑤ 다툼은 사업자가 증명. 계약·청약철회 기록으로 5년 보관.
    - 등록 화면 경고: 「개봉하면 반품 불가」처럼 법보다 넓게 막는 문구, 「재판매 금지·리셀 의심 시 취소」 문구(공정위 2023 시정 사례)는 쓰지 않는다. 주문 제작 공구(제17조 ②6호 — 서면 동의 필요)는 2b 에서 지원하지 않는다.
56. **사업자 정보는 사이트 전체에** — 제10조 ①: 판매 스위치가 켜져 있으면 user-web 공통 푸터에 상호·대표자·주소(불만 처리 장소)·전화·이메일·사업자등록번호·통신판매업 신고번호·호스팅 사업자·약관 링크. 공구 화면 하단에는 결정 18 그대로. [확인 필요 — 공정위 사업자정보공개 페이지 링크 의무]
57. **계약 통지 메일·주문서 항목 보강** — 제13조 ② 6·8호, 시행령 제20조 2호: 환불 지연배상금(연 15%) 조건, 판매조건 제한(모집 기간·총 수량·1인 한도·차수·성사 조건), 발송 예정일(결정 51), 청약철회 제한 안내(결정 55), 분쟁 처리 안내(1372 소비자상담센터·한국소비자원·전자문서·전자거래분쟁조정위원회).
58. **정상가(할인율) 근거** — 표시·광고 고시(종전거래가격 — 판 적 없는 가격을 정가로 쓰면 위반, 공정위 2025~2026 과징금 사례): `normal_price_basis`(공식 판매처가·직전 차수 공구가·시가·종전 판매가) + `normal_price_source`(내부 메모·URL), 상세에 「정상가 기준: ○○」. 「최저가」 문구 금지 안내. 차수 공구는 직전 차수 공구가가 기준이 될 수 있다. 「한정」은 시스템 문구로 쓰지 않고 「총 N개」만(운영자 설명에 「한정」이 있으면 추가 입고 때 경고).
59. **주문서 화면 규칙**(다크패턴 금지 — 제21조의2, 시행령 제9조): 옵션·수량 기본 선택 없음, 동의 체크·결제대금예치 선택 기본 체크 없음, 배송비·반품비는 처음부터 금액으로. 「선불·기프트·지역화폐 카드는 부분 취소가 안 돼 계좌로 환불될 수 있어요」 안내.
60. **Q&A 답변 기한** — 설정값 「답변 기한 2영업일」, 대시보드 「기한 지난 Q&A」(관행, 법정 의무 아님).

### 트랜잭션·워커
61. **환불은 별도 워커**: 불발·중단의 주문별 환불(결정 37 ②)은 상태 워커 안에서 하지 않고 워커 키 `groupbuy-refund`(1분, 바퀴당 상한 50건, `SKIP LOCKED`)가 「CLOSED_FAILED·CANCELED 공구의 비종결 주문」을 sweep 한다 — 대량 환불이 마감·만료 처리를 막지 않고, 중간에 멈춘 일도 이 워커가 이어 받는다. 각 주문은 **지금 주문 상태를 다시 잠가 읽고** 분기(그 사이 결제 중 → 확정으로 바뀐 주문도 환불). 즉시 PG 시도는 결제 모듈이 커밋 뒤에 하므로 워커 바퀴당 상한으로 시간 제한.
62. **`onPaid` 는 예외를 던지지 않는다**: 상태 불일치·한도·유일 충돌 같은 도메인 사정은 모두 REJECT 로 바꾼다(예외는 DB 장애 같은 인프라 오류뿐 — 예외면 결제 확정이 영원히 재시도된다). REJECT 경로는 주문 PENDING → EXPIRED(원인 `REJECTED_CLOSED`·`LATE_PAYMENT`) + RELEASE 를 **주문 조건부 전이가 이긴 쪽만**(선점 만료 워커와 이중 RELEASE 방지). 이 만료는 이탈 횟수에 세지 않는다(돈을 낸 회원). 「선점 시간 안」 판단은 지금 시각이 아니라 포트원 `paidAt` 과 비교(결제 계약 결정 4 와 같다).
63. **결제 다시 열기**: 남은 선점이 1분 미만이면 `GROUP_BUY_HOLD_EXPIRED`(결제 모듈의 최소 유효 시간 1분 — `PaymentOrderService.MIN_VALID`). 아직 유효한 READY 결제가 있으면 새로 만들지 않고 그 결제창 정보를 돌려준다(두 번 눌러도 앞 결제가 늦은 결제가 되지 않게).
64. **에스크로 호출 위치**: 배송 정보 등록·구매 확정은 트랜잭션 밖(커밋 뒤)에서 부르고, 결과는 `group_buy_shipments.escrow_logistics_at`·`escrow_completed_at` 에 조건부로 기록, 실패는 재시도 대기열(작은 표 `group_buy_escrow_jobs` — 시도·다음 시도·마지막 오류). 포트원의 「이미 등록·완료」 응답은 성공으로. 구매 확정 전 부분 취소가 PG 에서 막히는지는 2b-5 확인 — 막히면 반품 환불은 계좌 송금(결정 67).
65. **불변식 검사는 한 SQL**(같은 스냅샷): 공구·옵션 수량, 주문 줄 합, 결제 금액(결정 39 — `anomaly IS NULL` 인 받아들인 결제만, 진행 중 취소분을 뺀 식)을 한 문장으로.
66. **부분 유일 색인 명시**(어드바이저리 잠금 뒤 마지막 방어선): 진행 중 주문 `(group_buy_id, user_id) WHERE status IN (PENDING_PAYMENT…SHIPPED)` → `GROUP_BUY_ALREADY_JOINED` · 열린 요청 `(participation_id) WHERE status IN (REQUESTED, APPROVED)` → `GROUP_BUY_REQUEST_OPEN` · 옵션 없는 줄 `UNIQUE NULLS NOT DISTINCT (participation_id, option_id)`(postgres 15+ — 시험 DB 18) · 줄의 옵션이 같은 공구 것인지 복합 FK `(option_id, group_buy_id)`.

### 금액
67. **PG 로 못 돌려주는 상품 환불은 계좌 송금으로 전환**: `group_buy_refunds.method`(`PG_CANCEL`·`BANK_TRANSFER`·`NONE`). 취소가 GAVE_UP 이고 PG 가 영구 거절(취소 기간 지남·부분 취소 불가 카드·에스크로 확정 뒤)이면 운영자 「계좌 송금으로 전환」 — 취소 줄 MANUAL + `commerce_bank_transfers` 행 + 환불 기록 method 변경을 한 트랜잭션, 감사(돈 관련). 결정 6 의 보상 초과분과 같은 표.
68. **반품 환불이 마이너스가 되면 회원이 고른다**(사용자 결정 2026-10-28 — 운영자가 떠안지 않는다): 단순 변심 반품은 반품 비용이 회원 부담이라(제18조 ⑨) 반품비·재부과 배송비가 상품값보다 크면 그 부족분도 회원 몫이다.
    - 반품 신청 화면이 회수 방식별 결과를 미리 보인다 — 예(47,000 + 5,000 무료배송, B 5,000 변심 반품): 업체회수 −1,000 / 직접 발송 +2,000.
    - 결과가 마이너스인 방식을 고르면 **부족분을 카드로 추가 결제해야 접수**된다(결제 모듈 새 주문 종류 `GROUP_BUY_RETURN_FEE` — 대상 = 반품 요청, 금액 = 부족분, 선점 없음, 유효 30분. 결제 확정 때 요청이 접수(REQUESTED)되고, 결제하지 않으면 요청은 접수 전 상태로 남았다가 하루 뒤 닫힌다). 직접 발송으로 바꿔 플러스가 되면 추가 결제 없이 그 금액을 환불한다. 직접 발송도 마이너스면(아주 싼 상품) 직접 발송 + 추가 결제.
    - 요청이 철회·거절되거나 승인 때 다시 계산한 값이 달라지면 추가 결제의 남는 몫을 그 결제에서 부분 취소한다(같은 환불 기록 표, 원인 `RETURN_FEE_REFUND`).
    - 환불액이 정확히 0 이면 취소를 넣지 않고 환불 기록만 완료(method `NONE`).
    - 이렇게 받은 돈까지 실제로 받은 반품비·재부과로 `return_fee`·`reshipping_fee`·`reshipping_fee_charged` 에 남긴다 — 나중에 마지막 반품에서 재부과를 돌려줄 때 이 값을 쓴다. 하자·오배송은 빼는 것이 없어 마이너스가 되지 않는다.
    - [법무 확인] 회수 전에 반품 비용 부족분을 먼저 결제하게 하는 것이 청약철회 방해로 보이지 않는지 — 직접 발송이라는 비용 없는 길을 늘 함께 열어 두는 것으로 대비한다.
69. **불발·중단·지연 취소·공급 불가의 「전액」** = 그 주문의 남은 결제액(`cash_amount − refunded_amount − compensation_amount`) — 앞서 일부 취소가 있었으면 그만큼 뺀다. 이들이 처리되면 그 주문의 열린 반품·취소 요청은 자동 종료(`SUPERSEDED`).
70. **고액 문턱은 운영자가 금액을 정하는 환불에만**(사용자 결정 2026-10-28 — 로봇 공구는 상품 하나가 백만 원대라 금액 기준이면 거의 모든 반품이 고액이 되어 승인이 밀리고 3영업일 환급 기한을 넘길 위험): 고액 기준은 환불을 나눠 돌려주는 장치가 아니라 **누가 승인하느냐**다 — 환불은 금액과 관계없이 늘 PG 취소 한 번에 나간다.
    - **서버가 계산한 환불은 금액과 관계없이 일반 권한**(`commerce.refund.approve`·`commerce.order.manage`)으로 바로: 발송 전 취소 승인, 반품 승인(계산기 값 그대로), 공급 불가, 지연 취소, 불발·중단. 회원 본인 취소·늦은 결제 자동 취소는 승인 자체가 없다.
    - **운영자가 금액을 직접 넣는 환불에만** `commerce.refund.large`: 요청 없는 운영 환불(결정 32), 고객 택배비 보상, 계산값을 고쳐 승인하는 경우, 계좌 송금 전환 중 금액을 바꾸는 경우. 그 주문의 이런 환불 **누계**가 문턱(설정값, 기본 100,000원, **0 = 끔**) 이상이 되는 건부터(쪼개서 피하지 못하게). 권한이 없으면 승인 대기로 올려 권한자에게.
    - 모든 환불은 감사(돈 관련, 5년). commerce.md 13-2 「고액 공구 환불(1회 100,000원 이상)」을 이 뜻으로 고친다.
71. **운영 환불 뒤 반품**: 운영 환불도 줄별 금액으로 나눠 기록(금액 비례, 원 미만 버림·남는 원은 마지막 줄) → 계산기는 「줄 금액 − 줄 기환불」로 계산하고 최종은 `min(계산, 남은 결제액)`. 열린 요청이 있는 주문에는 운영 환불을 막는다.
72. **미리 보기 ↔ 승인 · 견적 ↔ 주문**: 요청에 `preview_amount` 저장 — 승인 때 다시 계산한 값이 다르면 두 값과 사유를 보이고 운영자가 확인 뒤 확정. 주문 API 는 화면이 본 금액(`expectedAmount`)을 **비교용으로만** 받아 다르면 409 `GROUP_BUY_AMOUNT_CHANGED`(청구액은 늘 서버 계산).
73. **콘솔 직접 취소가 있는 결제**: 그 결제에 열린 대사 이슈 `CANCEL_MISMATCH` 가 있으면 공구 환불 승인을 막는다(409 `GROUP_BUY_PAYMENT_UNRECONCILED` — 대사 처리 뒤 승인). 결제 모듈 후속(2a): 재시도 때 포트원 잔액(`amount.total − amount.cancelled`)을 쓰는 보강 [확인 필요].
74. **MANUAL 의 실제 처리 방법**(결제 모듈 소폭 — 2b-3, 결정 16 과 함께): 「수동 처리 완료」에 방법을 받는다 — 콘솔 취소면 그 취소 ID 를 줄에 붙이고 취소 누계까지 올린다(SUCCEEDED 와 같게 — 대사 이슈가 매일 다시 열리지 않게), 계좌 송금이면 결정 67. 불변식·대사는 송금분을 따로 뺀다.
75. **매출을 나눠 셀 수 있게**: `group_buy_refunds` 에 `item_refund`·`shipping_refund`·`return_fee`(−)·`reshipping_fee`(−)·`reshipping_refund`(+)·`compensation_pg`·`compensation_bank`·`completed_at`. 매출 결제 총액은 받아들인 결제만, 자동 취소(AUTO_REJECT)는 환불이 아니다. 불변식에 「주문별 완료 환불의 PG 분 합 = 취소 누계」를 더한다.
76. **포인트 결제 대비 열**(11절, 3+ 단계): 주문에 `point_amount`(0)·`cash_amount`(= amount) 를 지금 두고, 남은 결제액·불변식·고액 문턱을 처음부터 `cash_amount` 로 쓴다. `amount ≥ 1` CHECK.

### 재고
77. **한도 없는 옵션도 옵션 행을 늘 갱신**: 조건만 `max_qty IS NULL OR current_qty + reserved_qty + :q <= max_qty`. 옵션 CHECK `0 ≤ current, 0 ≤ reserved, (max IS NULL OR current + reserved ≤ max)`. 불변식: 옵션 있는 공구는 Σ옵션 current = 공구 current, Σ옵션 reserved = 공구 reserved(옵션 최대 합과 공구 최대는 관계 없음 — 어느 쪽이 커도 된다).
78. **옵션 전부 다 팔림의 동시 확정**: `onPaid` 의 마감 UPDATE 를 한 문장으로 — `UPDATE group_buys SET status='CLOSING', close_reason='SOLD_OUT' … WHERE id=? AND status='RECRUITING' AND (current_qty = max_qty OR NOT EXISTS (옵션 중 max IS NULL 또는 current < max))`. 두 옵션의 마지막 1개가 동시에 확정돼 서로 못 보는 경우는 1분 워커 안전망이 같은 조건으로 잡는다(10-7 ③에 옵션 조건을 더한다).
79. **종료 사유 열** `group_buys.close_reason`(`SOLD_OUT`·`TIME_UP`·`CANCELED`) — CLOSING 전이와 같은 문장에서 기록. 다 팔림 뒤 CLOSING 중 취소로 수량이 줄어도 표시·「다음 차수 알림 받기」·목록 탭은 이 열로.
80. **처음 수량과 추가 입고 표시**: `initial_max_qty`(모집 시작 때 고정, 옵션도) — 「추가 입고 N개」 = max(0, `max_qty − initial_max_qty`), `added_qty` = ADD 합. 불변식 `max_qty = initial_max_qty + ΣADD − ΣREDUCE`(이동 줄 기준, 모집 시작 이후만). 옵션 추가 입고·줄이기는 **같은 수만큼 공구 최대도** 움직인다. 한도 없는 옵션에는 옵션 ADD/REDUCE 를 거절(공구 ADD 로).
81. **모집 중 본인 취소로 풀린 수량도 「수량 풀림」**: 알림(신청자)과 DEMAND 연장 조건(결정 25)에 CANCEL 을 더한다. 알림은 공구·옵션 둘 다 남은 수량 > 0 일 때만(헛알림 방지). 연장 판단은 이동 줄 `kind IN (RELEASE, CANCEL, RESTOCK, ADD) AND created_at ∈ [end_at − 5분, 판단 시각]` — 색인 `(group_buy_id, created_at)`.
82. **이동 줄의 이후 값은 조건부 UPDATE … RETURNING 으로**(따로 SELECT 하면 다른 트랜잭션 값이 섞인다). 「마지막」은 `id` 순. CONFIRM·RELEASE 조건에 `reserved_qty >= :q` 를 넣고, 0 행이면 REJECT + `COMMERCE_ORDER_ANOMALY`(불변식이 이미 깨진 상태).
83. **`returned_qty` 는 반품 완료(회수 확인) 때** 처리 방법과 같은 트랜잭션에서 올린다. 요청 때 줄 수량 ≤ `qty − canceled_qty − returned_qty` 검사, 승인·완료 때 다시 검사.
84. **옵션별 남은 수량·품절 판정**: 옵션 남은 = min(옵션 `max − current − reserved`, 공구 `max − current − reserved`)(옵션 최대가 없으면 공구 값). 옵션 다 팔림 = 옵션 `current = max`(공구가 모집 중이면 추가 입고·다시 판매로 되살아날 수 있다고 안내), 잠시 품절 = 옵션 또는 공구의 `current + reserved = max`. `group_buy_watches` 에 `option_id`(선택)를 더해 옵션 품절 알림도 받는다.
85. **조회 때 정리는 본인 주문만**(10-4 「참여·견적 판정 때 지난 PENDING 정리」): 남의 주문까지 그 자리에서 만료시키면 잠금이 넓어진다 — 나머지는 워커.
86. **1인 한도 세부**: `round_limit_shared` 면 지금 차수의 `max_qty_per_user` 로 루트 전체를 센다(루트당 모집 중 차수는 하나라 어드바이저리 키는 공구 단위로 충분). `max_qty_per_user` null = 제한 없음 허용.

## Q&A 규칙 (2b-6 — 사용자 결정 2026-10-28)
- **표**: `group_buy_questions`(공구·회원·본문 평문 ≤ 1,000자·비밀글·`forced_secret`(개인정보로 자동 전환)·상태 OPEN/ANSWERED·숨김·지운 시각·보관 만료일) · `group_buy_answers`(질문·운영자·본문·시각) · `group_buy_answer_revisions`(답변 이전 내용·고친 운영자·시각).
- **누가·언제**: 질문은 로그인한 정상 회원(본인인증 없이). 공개된 공구(모집 예정·중·종료) 어디서나, 중단(CANCELED)된 공구는 닫는다. 정지 회원은 못 쓴다(글쓰기와 같다), 공구 참여 제한(`GROUP_BUY_BAN`)은 질문을 막지 않는다. 질문 쓰기 속도 제한(기존 글쓰기 제한 모양).
- **보기**: 공개 질문은 누구나, 비밀글은 작성자와 운영자만. Q&A 는 공구마다 따로이고 상세에 「이전 차수 문의 보기」 링크.
- **개인정보 자동 비밀글**(사용자 결정): 본문에 전화번호·이메일·주소 모양이 보이면 서버가 비밀글로 바꿔 저장하고(`forced_secret`) 응답에 그 사실을 싣는다. 작성 화면은 「연락처·주소가 보여 다른 회원에게 보이지 않게 비밀글로 바꿨어요」를 안내하고, 쓰는 동안에도 「연락처·주소는 쓰지 마세요」를 보인다.
- **작성자**: 답변 전에는 고치거나 지울 수 있다. 답변 뒤에는 고칠 수 없고, 공개 → 비밀글 전환과 지우기만. 지워도 **보관 기간 동안 감춘 채 남는다**.
- **운영자**: 답변·답변 수정(이전 내용은 `group_buy_answer_revisions` — 사용자 결정), 숨김(사유 5~200자·감사 `GROUPBUY_QNA_HIDE`), 비밀글 열람·관리자 Q&A 목록 조회는 개인정보 열람 감사(`GROUPBUY_QNA_VIEW`, 2년). 답변이 달리면 작성자에게 앱 알림, 새 질문·신고는 운영자 알림(결정 12).
- **신고**(사용자 결정): 기존 신고 기능(글·댓글)에 대상 종류 `GROUP_BUY_QUESTION` 을 더한다. 처리는 운영자 숨김과 같은 길.
- **보관**(사용자 결정): 단순 문의도 소비자 문의 기록으로 보고 **마지막 답변일(답변이 없으면 작성일)부터 3년** 보관 후 파기(commerce.md 17-3 「문의 3년」). 탈퇴 회원의 질문은 「탈퇴한 회원」으로 표시. 개인정보 처리방침에 항목을 더한다. [법무 확인 — 단순 상품 문의가 「소비자 불만·분쟁 처리 기록」에 드는지. 보수적으로 3년]
- 하자·오배송 환불은 Q&A 가 아니라 반품 요청(하자)으로 안내.

## 새 규칙 → 테스트 (commerce.md 18절에 없는 것 — 구현 단위가 쓴다)
| # | 시나리오 | 기대 결과 | 단위 |
|---|---|---|---|
| G1 | 다시 판매: 모집 중 + 단순 변심 / CLOSING 뒤 / 오배송 / 폐기 | 확정 감소·알림 / `GROUP_BUY_NOT_RESELLABLE` / 거절 / 수량 그대로 | 2b-3 |
| G2 | 마지막 1개 결제 확정 직후(워커 전) 추가 입고·다시 판매 | 거절 — 이미 CLOSING | 2b-2 |
| G3 | 잠시 품절(확정 + 선점 = 최대)에서 추가 입고 → 선점 만료 | 추가분과 풀린 수량 모두 판매 | 2b-2 |
| G4 | 줄이기 하한(확정 + 선점, 성사형 `min_qty`) · 줄여서 확정 = 최대 | 하한 아래 거절 / 즉시 CLOSING | 2b-2 |
| G5 | 옵션 공구의 추가 입고·줄이기·다시 판매·동시 선점(여러 줄) | 공구·옵션 수량 함께 맞음, 교착 없음, 줄 하나 모자라면 주문 전체 실패 | 2b-2 |
| G6 | 부분 반품 다시 판매 뒤 · 전체 반품 폐기 뒤 · 불발 환불 뒤 · 중단 뒤 불변식 | 판매 수량 식과 일치 | 2b-2·2b-3 |
| G7 | 25,000 × 3 무료배송에서 1개씩 세 번 반품 | 재부과 배송비를 두 번 빼지 않고 마지막은 전체 반품 규칙 | 2b-3 |
| G8 | 추가 차수: 복사 범위·새 배송비·동시 두 번 클릭·이전 신청자 한 번 알림·한도 독립/합산 | 표대로, 차수 하나만 생김 | 2b-2·2b-4 |
| G9 | 결제창이 열린 동안 중단 → 그 뒤 결제 도착 | 주문 EXPIRED(`GROUP_BUY_CANCELED`)·선점 해제·늦은 결제 자동 취소·이탈 횟수 안 셈 | 2b-2 |
| G10 | 즉시 발송 공구: 배송 명단 내보내기·배송지 수정 기한 · 발송된 주문이 있는 공구 중단 | 주문 기준 기한 / 발송 전만 환불 | 2b-3 |
| G11 | Q&A: 비밀글 열람 권한·답변 전후 수정·지워도 3년 보관·개인정보 자동 비밀글과 안내·숨김 감사·신고·속도 제한·정지 회원 | 규칙대로 | 2b-6 |
| G12 | 마감 4분 전 다시 판매·추가 입고(`DEMAND`) | 30분 연장 | 2b-2·2b-3 |
| G13 | 25,000 × 3 무료배송 발송 전 2개 취소 | 47,000 환불(배송비 재계산 3,000), `reshipping_fee_charged` 3,000 | 2b-2 |
| G14 | 한 요청에 하자 줄 + 변심 줄 · 하자 A 먼저 → 변심 B 마지막(편도·왕복) | 반품비 운영자 부담 · 줄별 금액 · 결정 43 맞춤 | 2b-3 |
| G15 | 보상액이 든 PG 취소 뒤 · 보상 > 남은 결제액 | 불변식(결정 39) · 넘는 부분 계좌 송금 | 2b-3 |
| G16 | 옵션 한도 합 < 공구 최대에서 옵션 모두 다 팔림 | 즉시 CLOSING(SOLD_OUT) | 2b-2 |
| G17 | 결제 확정 ∥ 중단 · 판정 · 반품 승인 동시 | 교착 없음, 확정이 이기면 중단 단계 ②가 그 주문을 환불 | 2b-2·2b-3 |
| G18 | 결제 다시 열기 · 자동 취소의 `onCanceled` · MANUAL 처리 | 선점 안 늘어남 · `refunded_amount` 그대로 · 공구 완료 표시·메일 | 2b-2·2b-3 |
| G19 | 일부 줄 취소 뒤 주문 상태 · 전부 취소 뒤 CANCELED · 그 뒤 새 주문 | 결정 46 대로 | 2b-2 |
| G20 | 1인 한도: 폐기 반품은 빼고 금액만 운영 환불은 그대로 · `round_limit_shared` · CLOSING 중 본인 취소로 불발 | 결정 28·24·45 대로 | 2b-2·2b-3 |
| G21 | 휴일이 낀 2·3영업일 경고 · CSV 수식 주입 · 같은 금액 두 번 환불 시도(GAVE_UP 중) | 휴일 건너뜀 · `'` 붙음 · 두 번째 거절(결정 38) | 2b-3·2b-4 |
| G22 | 웹훅 확정 ∥ 선점 만료 워커 · ∥ 결제 다시 열기 · ∥ 중단의 결제 중 주문 | 교착 없음(결제 행 먼저 — 결정 37), 둘 중 하나만 이김, RELEASE 한 번 | 2b-2 |
| G23 | 판정 ① ∥ 마지막 결제 확정(확정 = min_qty) | 성사(불발 아님) | 2b-2 |
| G24 | 불발 300건 · 환불 워커 중간에 서버 종료 | 상태 워커 주기 유지, 다음 바퀴가 이어서 끝냄, 중복 환불 없음 | 2b-2 |
| G25 | `onPaid` 도메인 사정(한도·유일 충돌·상태 불일치) | 예외 없이 REJECT, 주문 EXPIRED·RELEASE 한 번, 이탈 안 셈 | 2b-2 |
| G26 | 남은 선점 50초에 결제 다시 열기 · 유효한 READY 가 있는데 다시 열기 | `GROUP_BUY_HOLD_EXPIRED` · 같은 결제 돌려줌 | 2b-2 |
| G27 | 옵션 A·B 마지막 1개 동시 확정 · 한도 없는 옵션 구매 | 1분 안에 CLOSING(SOLD_OUT) · 옵션 행 수량 맞음 | 2b-2 |
| G28 | 모집 중 본인 취소로 풀림(마감 3분 전) | 수량 풀림 알림 · DEMAND 연장 | 2b-2 |
| G29 | 추가 입고·줄이기 이력 · 옵션 추가 입고가 공구 최대도 움직임 · 「추가 입고 N개」 표시 | 결정 80 식과 일치 | 2b-2 |
| G30 | 전체 반품(반품비 남음) 뒤 주문 상태 · 그 뒤 운영 환불 시도 | REFUNDED · 거절 | 2b-3 |
| G31 | 47,000 + 5,000 무료배송에서 B 변심 반품: 업체회수 / 직접 발송 / 추가 결제 안 함 / 접수 뒤 거절 | 1,000 추가 결제해야 접수 · 2,000 환불 · 접수 안 됨(하루 뒤 닫힘) · 추가 결제 1,000 취소 | 2b-3 |
| G32 | 발송 전 2/3 취소(재부과 3,000) 뒤 발송 · 남은 1개 변심 반품(편도·왕복, 업체회수) / 직접 발송 / 하자 반품 | 22,000(처음 배송비 3,000 돌려주지 않음 + 반품비) / 25,000 / 28,000(처음 배송비까지) — 처음부터 1개를 28,000에 산 회원과 같음 | 2b-3 |
| G33 | 하자 부분 반품 + 보상 4,000 뒤 운영 환불 · 남은 결제액 | 보상 뺀 남은 결제액으로 막힘(결정 38) | 2b-3 |
| G34 | 1,200,000원 반품 승인(계산기 값) · 운영 환불 3만원 + 8만원(쪼개기) · 문턱 0 | 일반 권한으로 바로 PG 취소 한 번 · 두 번째부터 고액 권한 · 모두 일반 권한 | 2b-3 |
| G35 | GAVE_UP(취소 기간 지남) → 계좌 송금 전환 · MANUAL(콘솔 취소 ID) | 송금 대기·감사 · 취소 누계 반영, 대사 이슈 다시 안 열림 | 2b-3 |
| G36 | 콘솔 직접 취소(CANCEL_MISMATCH 열림) 결제에 반품 승인 | `GROUP_BUY_PAYMENT_UNRECONCILED` | 2b-3 |
| G37 | 발송 예정일 필수(성사형) · 예정일 지남 경고 · 발송 지연 공지 뒤 준비중 주문 즉시 취소 | 저장 거절 · 경고 · 전액(재부과 없음) | 2b-2·2b-4 |
| G38 | 옵션 공급 불가: 그 옵션만 산 주문 · 섞인 주문(답 없음 / 전체 취소 선택) | 전액(배송비 포함) · 나머지만 발송·재부과 없음 / 전액 | 2b-3 |
| G39 | 하자 교환 · 교환할 물건 없음 | 재발송·환불 0·반품비 운영자 · 환불로 전환 알림 | 2b-3 |
| G40 | 반품 거절 사유 코드·근거 사진 없이 거절 · 청약철회 제한 문구 없는 공구에서 「사용으로 가치 감소」 거절 | 거절 · 거절(⑥ 표시 없으면 제한 못 함) | 2b-3 |
| G41 | 판매 스위치 켜짐 → 푸터 사업자 정보 · 꺼짐 | 표시 · 숨김 | 2b-4 |
| G42 | 주문서 기본 선택 없음 · `expectedAmount` 다름 | 동의·옵션 미선택이면 거절 · 409 `GROUP_BUY_AMOUNT_CHANGED` | 2b-2 |

## 감사·오류 코드 후보
- 감사 행위: `GROUPBUY_CREATE`·`GROUPBUY_UPDATE`·`GROUPBUY_START`·`GROUPBUY_CLOSE_EARLY`(일반) · `GROUPBUY_CANCEL`·`GROUPBUY_REFUND`·`GROUPBUY_REQUEST_APPROVE`·`GROUPBUY_STOCK_ADD`·`GROUPBUY_STOCK_REDUCE`·`GROUPBUY_RETURN_DISPOSE`·`GROUPBUY_ROUND_CREATE`·`COMMERCE_SALES_TOGGLE`·`COMMERCE_SHIPPING_POLICY_*`(돈 관련) · `GROUPBUY_ADDRESS_REVEAL`·`GROUPBUY_SHIPPING_EXPORT`·`GROUPBUY_ORDER_VIEW`·`GROUPBUY_QNA_VIEW`(개인정보 열람) · `GROUPBUY_QNA_HIDE`·`GROUPBUY_QNA_ANSWER_EDIT`(일반).
- 오류 코드: `GROUP_BUY_NOT_FOUND`(404) · `GROUP_BUY_NOT_RECRUITING`(409) · `GROUP_BUY_SOLD_OUT`(409) · `GROUP_BUY_OPTION_SOLD_OUT`(409) · `GROUP_BUY_LIMIT_EXCEEDED`(409) · `GROUP_BUY_ALREADY_JOINED`(409) · `GROUP_BUY_SALES_CLOSED`(403) · `GROUP_BUY_HOLD_EXPIRED`(409) · `GROUP_BUY_LOCKED_FIELD`(409 — 모집 시작 뒤 가격·기간·옵션 구성·배송비 수정) · `GROUP_BUY_QTY_BELOW_FLOOR`(409 — 줄이기 하한) · `GROUP_BUY_NOT_RESELLABLE`(409) · `GROUP_BUY_ROUND_EXISTS`(409) · `GROUP_BUY_ROUND_SOURCE_INVALID`(409) · `GROUP_BUY_REQUEST_OPEN`(409) · `GROUP_BUY_RETURN_WINDOW_CLOSED`(409) · `GROUP_BUY_QUESTION_ANSWERED`(409 — 답변 뒤 수정) · `GROUP_BUY_QUESTION_CLOSED`(409 — 중단된 공구) · `DATA_KEY_MISSING`(503).

## 2a 보강(고침, 2026-10-28)
포트원 취소 호출을 결제 주문 행 잠금 밖으로 옮기고, 한 결제에 보내는 중인 취소는 하나(V100)로 줄을 세웠다 — 공구 불발로 취소가 몰려도 확정·웹훅이 PG 응답을 기다리지 않는다. 상세는 `commerce-payment-contract.md` 「2a 보강 — 취소 호출을 잠금 밖으로」.

## 2b-2 구현 결과 (2026-10-28 — 브랜치 `claude/commerce-2b-2-groupbuy-core-d751c0`, develop 병합 전)
- **마이그레이션** shared `V101__group_buy_core.sql`(develop shared 마지막 V100 다음 — 합칠 때 다시 확인). 표: `group_buy_settings`(1행) · `shipping_policies`(배제 제약 `btree_gist` + 적용 시작 줄 불변 트리거) · `group_buys` · `group_buy_images` · `group_buy_options` · `group_buy_revisions` · `group_buy_status_history` · `group_buy_participations` · `group_buy_participation_items` · `participation_status_history` · `group_buy_qty_movements` · **`group_buy_refunds`** · `group_buy_watches` · `group_buy_delay_notices` · `group_buy_order_counters`. 결정 66 의 부분 유일(진행 중 주문)·`NULLS NOT DISTINCT`·복합 FK, 결정 38·76 의 금액 CHECK, 결정 51 의 발송 시기 CHECK.
- **코드**: shared `com.community.groupbuy` — `entity`(상태·종류 열거) · `dto`(주문·견적·남은 수량·등록 값·이벤트 `GroupBuyEvents`) · `exception`(`GroupBuyException` 한 클래스 + `GroupBuyBlockedException`) · `repository.master`(JDBC: `GroupBuyStore`·`GroupBuyQtyStore`·`ParticipationStore`·`GroupBuyRefundStore`·`ShippingPolicyStore`·`GroupBuyMemberStore`) · `service`(`GroupBuyAdminService`·`ShippingPolicyService`·`GroupBuyQuoteService`·`GroupBuyJoinService`·`GroupBuyQtyService`·`HoldExpiryService`·`ParticipationCancelService`·`GroupBuyRefundService`·`GroupBuyStateWorker`·`GroupBuyRefundWorker`·`GroupBuyInvariantCheck`·`GroupBuyEligibility`·`GroupBuyPricing`·`GroupBuySavepoint`·`GroupBuySalesSwitch`) · `handler.GroupBuyPaymentHandler`. admin `batch.service.GroupBuyStateScheduler`(워커 둘, `roboyam.groupbuy.state-poll-millis`·`refund-poll-millis` 기본 1분, `roboyam.batch.enabled` 서버만). 결제 모듈 소폭: `PaymentOrderService.expireOpen`(READY 닫기 — 결제 행 먼저)·`openCheckout`(유효한 READY 다시 돌려주기), `PaymentOrderStore.findOpenReady`. 감사 행위 10개(`GROUPBUY_*`·`COMMERCE_SHIPPING_POLICY_CREATE` — 돈 관련 5·일반 5) + 대상 `GROUP_BUY`·`SHIPPING_POLICY`, 번들 ko·en.
- **정한 세부**(계약에 없던 것 — 추천안으로 정함):
  1. **환불 기록 표 `group_buy_refunds` 를 V102 에서 V101 로 당겼다** — 2b-2 의 불발·중단 환불·본인 취소가 쓴다. `request_id` 는 FK 없이(요청 표는 V102). 2b-3 은 열만 쓴다.
  2. **「모집 시작」 = 공개(`published_at`)**: 운영자가 누르면 공개되고(시작 전이면 「예정」), 시작 시각에 상태 워커가 DRAFT → RECRUITING(그 시각의 배송비 정책 사본·처음 수량·수정 이력 1). 시작 시각이 이미 지났으면 누른 자리에서. **공개 뒤에는 설명·이미지·요약·사양만** 고칠 수 있다(가격을 미리 공개하므로 공개 시점부터 잠근다).
  3. **결제 다시 열기**: 결정 63 대로 유효한 READY 가 있으면 같은 결제를 돌려주되, 화면이 「결제창 실패 뒤」(`afterFailure`)라고 알리면 새 결제 번호를 만든다(commerce.md 8-1 — 포트원은 실패한 결제 ID 재사용을 보장하지 않는다). 둘 다 선점 끝은 그대로.
  4. **`onPaid` 거절 사유 코드**: `GROUP_BUY_ORDER_NOT_FOUND`·`_ORDER_NOT_PENDING`·`_PAYMENT_MISMATCH`·`_HOLD_EXPIRED`·`_AMOUNT_MISMATCH`·`_CLOSED`(판정·중단 뒤)·`_QTY_ANOMALY`(선점 부족 — 이벤트 `anomaly=true`)·`_CONFLICT`(DB 제약 충돌). 받아들이는 일은 JDBC 세이브포인트 안이라 지면 되돌리고 만료 + RELEASE(조건부, 이긴 쪽만).
  5. **만료 원인** `ExpireCause`: `HOLD_TIMEOUT`(이탈로 셈) · `GROUP_BUY_CANCELED` · `PAYMENT_FAILED_CLOSED`(불발 공구의 결제 중 주문) · `REJECTED_CLOSED` · `LATE_PAYMENT` · `REJECTED_ANOMALY`.
  6. **조기 마감**은 종료 사유 `EARLY_CLOSED`(closing_at = 누른 시각, 상태 이력 사유 `EARLY_CLOSE`) — 2b-1 화면 세션이 알린 사용자 결정(2026-10-28, 결정 79 고침: 종료 사유 `SOLD_OUT`·`TIME_UP`·`EARLY_CLOSED`·`CANCELED` 넷, 2b-1 커밋 f883ae80)을 따랐다. `close_reason` 은 varchar(15). 연장은 상태 이력 `RECRUITING → RECRUITING`(사유 `EXTENDED`).
  7. **발송 지연 공지 뒤 즉시 취소**(결정 52)는 공지 전에 결제한 발송 전 주문의 **주문 전체**(줄을 골라도 전체), 원인 `DELAY`, 재부과 없음, 상태 CANCELED.
  8. 불발·중단 환불의 주문 상태는 REFUNDED, 본인 취소·지연 취소는 CANCELED(결정 46). 금액 나눔: 전액 = 남은 결제액 → 상품(가진 수량) + 나머지(앞서 재부과한 배송비는 `reshipping_refund`, 그 밖은 `shipping_refund`).
  9. 배송지 암호 용도 `groupbuy.address.{recipient,phone,line1,line2,memo}`(DataCipher), 우편번호만 평문.
  10. 판매 스위치는 인터페이스 `GroupBuySalesSwitch` + 기본 구현 `DefaultGroupBuySalesSwitch`(늘 열림) — **2b-4 가 V103 설정을 읽는 구현으로 바꾼다**(회원 주문 API 도 2b-4 라 지금 주문이 들어올 길은 없다).
  11. 회원·운영자 알림·메일은 `GroupBuyEvents`(주문 확정·거절·선점 만료·수량 풀림·차수 시작(받는 사람 중복 없이)·환불 완료·자동 취소 완료·상태 변경)를 업무 트랜잭션 안에서 발행만 한다 — 받는 곳은 2b-4. `COMMERCE_ORDER_ANOMALY` 운영자 알림은 아직 `reserved()` 그대로(이벤트 `OrderRejected.anomaly`).
  12. 관리자 목록 조회용 JPA 엔티티·QueryDSL 은 만들지 않았다(쓰는 화면 API 가 2b-4) — 이번에는 모두 JDBC 행 레코드.
- **규칙 → 테스트**(shared `com.community.groupbuy.*` 80개 + admin `GroupBuyStateSchedulerTest` 3개, 실제 PostgreSQL `CommerceTestDatabase`):

  | 규칙 | 테스트 |
  |---|---|
  | 10-4·결정 23 선점(공구 행 → 옵션 ID 오름차순), 줄 하나 모자라면 주문 전체 실패, 한도 없는 옵션도 수량 열 갱신(77) — G5 | `GroupBuyJoinTest.G5_여러_줄_중_…` · `GroupBuyStockTest.G5_여러_줄_동시_선점과_…` |
  | 결정 41 선점 끝 = 결제 유효 시각, 배송지 암호문, 주문 번호(13) | `GroupBuyJoinTest.주문하면_…` · `GroupBuySchemaTest.주문_번호는_…` |
  | 결정 59·72 동의 3종·줄 필수, 화면 금액 다르면 409 — G42 | `GroupBuyJoinTest.G42_…`(둘) |
  | 10-5 자격(권한·본인인증·19세·정지·참여 제한·판매 스위치) | `GroupBuyJoinTest.자격_권한_…` |
  | 진행 중 주문 1건(어드바이저리 잠금 + 부분 유일 66), 동시 두 번 → 하나 · 전부 취소 뒤 새 주문 — G19 | `GroupBuyJoinTest.진행_중_주문은_…_G19` · `같은_회원이_동시에_…` · `GroupBuySchemaTest.진행_중_주문은_…` |
  | 1인 한도(결정 28 — 취소·반품 뺌, 금액만 환불은 셈)·차수 합산(24·86) — G20·G8 | `GroupBuyJoinTest.한_사람_한도는_…` · `반품은_처리_방법과_…_G20` · `차수_사이_한도는_…_G8_G20` |
  | 잠시 품절 / 다 팔림 구분(17·84), 마지막 하나 동시 → 하나만 | `GroupBuyJoinTest.마지막_하나를_…` · `다_팔리면_temporary_false_…` |
  | 조회 때 정리는 본인 주문만(85) | `GroupBuyJoinTest.본인의_지난_선점은_…` |
  | 결제 확정 → JOINED / 즉시 발송 CONFIRMED(10-3) | `GroupBuyPaymentTest.성사형은_결제되면_…` |
  | 다 팔림 즉시 CLOSING(27)·옵션 전부 다 팔림(48·78)·한도 없는 옵션 — G16 | `GroupBuyPaymentTest.결정27_…` · `G16_…` · `한도_없는_옵션이_…` |
  | P3 결제 중 마감 → 받고 판정 포함 | `GroupBuyPaymentTest.P3_…` |
  | P3b 판정 뒤 결제 → REJECT·자동 취소·만료·RELEASE 한 번·이탈 안 셈, 자동 취소 `onCanceled` 는 금액 불변 — G18 일부 | `GroupBuyPaymentTest.P3b_…` |
  | P15 선점 만료 직후 결제 → 살리지 않음(결제 행 먼저 닫음 — 37) | `GroupBuyPaymentTest.P15_…` |
  | P16 결제창 실패 뒤 재결제 — 같은 주문, 선점 안 늘어남 | `GroupBuyPaymentTest.P16_…` |
  | 결정 63 다시 열기 1분 미만 거절·유효한 READY 그대로 — G26 | `GroupBuyPaymentTest.G26_…` |
  | 결정 62 `onPaid` 도메인 사정(상태 불일치·불변식 깨짐·DB 제약 충돌) 예외 없이 REJECT — G25 | `GroupBuyPaymentTest.G25_…` |
  | 선점 반복 이탈 24시간 3번 → 공구 참여 제한, HOLD_TIMEOUT 만 셈(10-5·12-3·28) | `GroupBuyPaymentTest.선점_반복_이탈_…` |
  | 웹훅 확정 ∥ 선점 만료 · ∥ 다시 열기 — 교착 없음·하나만 이김·RELEASE 한 번 — G22 | `GroupBuyPaymentTest.G22_…`(둘) |
  | 결제창 열린 동안 중단 → 만료·해제·늦은 결제 자동 취소·이탈 안 셈 — G9 | `GroupBuyPaymentTest.G9_…` |
  | 결제 확정 ∥ 중단 — 교착 없음, 확정이 이기면 환불 워커가 환불 — G17(2b-2 몫) | `GroupBuyPaymentTest.G17_…` |
  | 추가 입고·다시 판매는 다 팔림 뒤 거절(이미 CLOSING) — G2 | `GroupBuyStockTest.G2_…` |
  | 잠시 품절에서 추가 입고 → 선점 만료 → 모두 판매 — G3 | `GroupBuyStockTest.G3_…` |
  | 줄이기 하한(확정 + 선점·성사형 최소)·줄여서 다 팔리면 즉시 CLOSING — G4 | `GroupBuyStockTest.G4_…` · `성사형_최소_수량_…` |
  | 옵션 추가 입고·줄이기가 공구 최대도 움직임, 한도 없는 옵션 거절, `max = 처음 + ΣADD − ΣREDUCE`, 「추가 입고 N개」, 수정 이력·감사 — G5·G29 | `GroupBuyStockTest.G5_G29_…` · `사유가_없거나_…` |
  | P16b·P16c·P16d 연장, 결제 중 선점이 남으면 연장, 추가 입고·본인 취소 풀림도 연장(25·81) — G12·G28 | `GroupBuyStateWorkerTest.P16b_…` · `P16c_…` · `P16d_…` · `DEMAND_마감_시각에_…` · `G12_…` · `G28_…` |
  | 예정 공구는 시작 시각에 워커가 모집 시작·그때 배송비 복사 | `GroupBuyStateWorkerTest.예정_공구는_…` |
  | 판정(성사 → CONFIRMED 일괄, 불발 → 환불 워커가 전액·CANCEL·환불 기록 완료) | `GroupBuyStateWorkerTest.판정_성사면_…` |
  | P25 성사 뒤 최소 아래로 내려가도 성사 유지 | `GroupBuyStateWorkerTest.P25_…` |
  | 결정 45 집계 중 본인 즉시 취소 → 불발 가능 — G20 일부 | `GroupBuyStateWorkerTest.결정45_G20_…` |
  | 판정 ∥ 마지막 확정 → 성사(판정 ① 공구 행 잠금 — 37) — G23 | `GroupBuyStateWorkerTest.G23_…` |
  | 두 옵션 마지막 하나 동시 확정 → 워커가 CLOSING(78) — G27 | `GroupBuyStateWorkerTest.G27_…` |
  | COMPLETED sweep(결정 9) | `GroupBuyStateWorkerTest.즉시_발송_성사_공구는_…` |
  | 불발 300건 — 상태 워커는 환불하지 않음, 환불 워커 바퀴당 50·이어 받기·중복 없음(61) — G24 | `GroupBuyStateWorkerTest.G24_…` |
  | 발송 전 2/3 취소 47,000 · 재부과 3,000 · 나머지 취소는 남은 결제액 전부(42·46·69) — G13·G19 | `ParticipationCancelTest.G13_…` |
  | 배송비 낸 주문은 재부과 없음 · 한 줄 취소 · 남의 주문 404 · 가진 수량 초과 400 | `ParticipationCancelTest` 나머지 |
  | 성사형은 모집 중 즉시 취소·성사 뒤 요청으로 · 즉시 발송은 준비중 전까지(10-3 표) | `ParticipationCancelTest.성사형_JOINED_…` · `즉시_발송은_…` |
  | 지연 공지 뒤 준비중 주문 즉시 전액·재부과 없음(52) — G37 일부 | `ParticipationCancelTest.G37_…` |
  | 발송 예정일 필수·즉시 발송은 선착순만·정상가 근거(51·58·D1) — G37 일부 | `GroupBuyAdminTest.G37_…` · `즉시_발송의_발송_영업일은_…` |
  | 모집 시작 = 배송비 사본·처음 수량·이력 1·감사, 기간 상한·정책 없음 거절 | `GroupBuyAdminTest.모집_시작은_…` · `기간이_설정_상한을_…` |
  | 모집 뒤 설명·이미지만(LOCKED_FIELD)·수정 이력·낙관 잠금, DRAFT 는 모두 | `GroupBuyAdminTest.모집_뒤에는_…` · `공개_전_DRAFT_는_…` |
  | 추가 차수: 끝난 공구만·복사 범위·동시 두 번 하나·DRAFT 차수 있으면 거절·중단 공구 거절·시작 때 이전 신청자 한 번 — G8 | `GroupBuyAdminTest.G8_…` · `중단된_공구에서는_…` |
  | 중단 ① 공구만 → ② 환불 워커(발송 전만 환불·결제 중 닫음·발송된 주문 그대로·다시 돌려도 한 번) — 29 | `GroupBuyAdminTest.결정29_…` · `조기_마감은_…` |
  | 종료 사유 넷(`SOLD_OUT`·`TIME_UP`·`EARLY_CLOSED`·`CANCELED`) — 조기 마감은 `EARLY_CLOSED`(결정 79 고침) | `GroupBuyAdminTest.조기_마감은_…_EARLY_CLOSED` · `GroupBuySchemaTest.종료_사유는_…` |
  | P16e 배송비 예약 인상 · 지난·앞선 예약 거절 · 적용 시작 줄 불변·기간 겹침 금지(DB) | `GroupBuyAdminTest.P16e_…` · `지난_시각이나_…` · `적용이_시작된_…_DB` |
  | V101 제약(부분 유일·NULLS NOT DISTINCT·복합 FK·금액·수량·방식 CHECK) | `GroupBuySchemaTest` |
  | 불변식 한 SQL — 판매 수량 식(다시 판매·폐기 반품)·이동 줄 마지막 값·결제 금액(39)·옵션 합(77)·최대 식(80) — G6(2b-2 몫) | `GroupBuySchemaTest.G6_…` + 각 시험 끝의 `assertInvariants` |
  | 워커 「언제 돌릴지」 — 배치를 켠 서버만, 둘 따로, 워커 기록·가린 오류 | admin `GroupBuyStateSchedulerTest` |
- **실행**(이 PC 셸에 `JAVA_HOME` 이 없어 지정): `JAVA_HOME=~/.gradle/jdks/eclipse_adoptium-25-amd64-windows.2 ./gradlew :server:shared:test --tests 'com.community.groupbuy.*'` → **80개 통과**(조기 마감 종료 사유를 고친 뒤 다시 돌려 통과, G24 약 70초) · `./gradlew :server:admin:test --tests 'com.community.batch.service.GroupBuyStateSchedulerTest'` → **3개 통과**. 바꾼 곳 회귀 확인: shared `AuditRetentionTest`(6)·`PaymentOrderServiceTest`(7)·`PaymentCompletionTest`(7), admin `Commerce2aCatalogTest`(6)·`Commerce04AdminCatalogTest`(5)·`AuditRetentionAdminTest`(6 — admin 앱 문맥이 공구 빈과 함께 뜸), api `IdentityHttpTest`(6 — api 앱 문맥) 모두 통과. 실패 0. 전체 테스트는 돌리지 않았다.
- **아직 테스트로 막지 못한 규칙과 이유**
  - P26(같은 멱등 키·다른 본문 409)·속도 제한 `GROUPBUY_JOIN` — 장치는 0-5 시험이 막고 있고, 공구 주문 API 에 `MoneyRequestGuard` 를 붙이는 것은 컨트롤러와 함께 2b-4.
  - 권한 코드(`commerce.groupbuy.cancel`·`commerce.stock.adjust`·`commerce.product.manage`)와 설명 HTML 정화 — 관리자 컨트롤러(2b-4). 권한 `featureReady` 도 그때.
  - 수량 풀림 알림(공구·옵션 둘 다 남을 때만·10분에 한 번)·차수 시작 알림 발송·환불 완료 메일·`COMMERCE_ORDER_ANOMALY` 운영자 알림 — 이벤트만 시험했다(발송은 2b-4).
  - G6·G17·G20 중 반품·요청 승인 몫, G10·G21 — 2b-3.
  - 예정일·발송 기한 지난 주문 경고(51·P35) — 2b-4 점검 워커.

### 2b-2 추가 — 상품정보제공고시 서버 (2026-10-28, 결정 87 — develop 계약의 「### 상품정보제공고시」 절이 정본, 이 브랜치에서는 참조만)
- **카탈로그 데이터** `server/shared/src/main/resources/groupbuy/notice-catalog-2023-01-01.json` — 공정거래위원회고시 제2022-15호(2023-01-01 시행) Ⅲ-1 품목별 정보 **40개 품목 · 379개 항목**. 국가법령정보센터 본문 iframe 의 항목 표 이미지 40장을 Claude in Chrome 으로 확대해 2026-10-28 에 옮겼다(파일 머리에 고시 번호·시행일·옮긴 날짜·출처 URL·일반원칙 2~7 원문). **읽기 어려운 글자는 없어 `unverified` 항목은 없다.** 항목마다 `no`(원문 번호, 하위는 `7-1` 등 + `group` 에 묶음 줄)·`key`(영문 — 같은 뜻은 품목이 달라도 같은 키)·`name`·`description`(이름 + 괄호 설명 = 원문 한 줄)·`kind`(`TEXT`·`DATE_LIKE` 원칙 ④·`KC_CERT` 원칙 ⑥·`QUALITY_WARRANTY` 원칙 ⑤)·`required`(모두 true — 원칙 ③)·`conditional`(원문에 「…에 한함」 등 — 해당 없으면 「제공 불가 + 사유」). 품목 `scope`(37·38 의 적용 범위 문구)·`warrantyDisadvantageDisclosure`(원칙 ⑤ 후단 — 10 노트북·11~14). 가운데점은 `·` 로 통일.
- **V101 에 더함**: `group_buy_notices`(공구·차례·품목 코드·제목·카탈로그 버전) · `group_buy_notice_options`(적용 옵션 — **옵션 유일**: 한 옵션은 양식 하나에만, 복합 FK 로 같은 공구 옵션만) · `group_buy_notice_values`(항목 키·값 / 제공 불가 + 사유 — CHECK: 제공 불가면 값 없이 사유 필수) · `group_buy_revisions.notices`(jsonb 사본) + 이력 종류 `NOTICE`.
- **코드**: `GroupBuyNoticeCatalog`(`classpath*:groupbuy/notice-catalog-*.json` 을 모두 읽고 가장 늦은 시행일이 지금 버전) · `GroupBuyNoticeService`(저장·완성 검사·복사·옵션 다시 잇기) · `GroupBuyNoticeStore` · `NoticeDraft` · 오류 409 `GROUP_BUY_NOTICE_INCOMPLETE`(추가 값 `noNotice`·`notices[{index,title,categoryCode,missingKeys}]`·`unassignedOptions`) · 번들 ko·en.
- **정한 세부**
  1. 양식의 적용 옵션은 **옵션 이름**으로 받는다(DRAFT 수정은 옵션을 지우고 다시 넣어 ID 가 바뀐다 — 다시 넣은 뒤 이름으로 다시 잇는다).
  2. 적용 옵션이 빈 양식 = 공구 전체(기본 상품) — 다른 양식에 없는 옵션을 덮는다(부수 상품 옵션은 여기, 원칙 ⑦). 옵션이 있는 공구에서 기본 양식은 하나까지. 기본 양식이 없으면 모든 옵션이 어느 양식에 이름으로 들어 있어야 한다.
  3. 저장은 통째로. 같은 자리·같은 품목의 양식은 카탈로그 버전을 유지하고, 새 양식·품목을 바꾼 양식은 지금 버전. 추가 차수는 버전 그대로 복사.
  4. 완성 검사는 모집 시작(공개) 때 + **공개 뒤 저장 때도**(공개된 고시를 비울 수 없다). 공개 전 DRAFT 는 빈 채 저장할 수 있다. 「값」은 공백만이면 빈 것으로 본다.
  5. 공개 뒤 저장은 모집 중 이후면 수정 이력 번호를 올리고 사본(`NOTICE`), 모집 시작 이력에도 고시 사본이 들어간다.
  6. 「상세페이지 참조」만 적은 값은 저장은 하고 경고 목록을 돌려준다(`DetailPageWarning`).
  7. 판매 화면용 조회(회원 상세 표)·관리자 API 는 2b-4.
- **테스트 G43**(`GroupBuyNoticeTest` 9개): 카탈로그 40개 품목·키 겹침 없음·원칙 ④⑤⑥ 표시 / 양식 없음·빈 항목·공백 값이면 모집 시작 거절(빈 양식·항목 목록), 「제공 불가 + 사유」는 채운 것 / 제공 불가 사유 없으면 저장 거절 + DB CHECK / 어느 양식에도 없는 옵션이면 거절, 기본 양식이 나머지를 덮음 / 한 옵션은 양식 하나에만(저장 검사 + DB 유일)·기본 양식 하나·없는 옵션·품목·항목 거절 / 모집 뒤 수정은 이력 사본, 비우는 수정은 거절 / 추가 차수 복사·카탈로그 버전 고정(시험 전용 옛 버전 `src/test/resources/groupbuy-test/notice-catalog-2022-01-01.json` — 실제 고시가 아니다)·옛 버전 항목으로 검사 / DRAFT 옵션 다시 넣기 뒤 이름으로 다시 잇기 / 「상세페이지 참조」 경고.
- **실행**: `./gradlew :server:shared:test --tests 'com.community.groupbuy.*'` → **89개 통과**(기존 80 + G43 9 — 시험 키트의 `open()` 이 모집 시작 전에 기본 양식을 채우게 바꿈) · admin `AuditRetentionAdminTest`(6)·`GroupBuyStateSchedulerTest`(3), api `IdentityHttpTest`(6) 통과(두 앱 문맥이 카탈로그를 jar 안에서 읽어 뜬다). 실패 0.
- **확인 필요**: 과태료 근거 조문·금액(전자상거래법 제13조 ② 위반 — 계약 결정 87 의 [원문 확인])은 이번에 확인하지 않았다.
- **판매자 추가 항목**(사용자 지시 2026-10-28 — develop 계약 결정 87 「판매자 추가 항목」 줄):
  - V101 `group_buy_notice_values` 를 고쳤다 — `id` PK, `custom`(추가 항목이면 true), `item_key`(고시 항목만), `custom_name`(추가 항목만, 1~40자), `sort_order`. CHECK `ck_gbnv_kind`: 고시 항목은 키 필수·이름 없음, 추가 항목은 키 없음·이름·내용(1~1,000자) 필수·「제공 불가」 없음. 부분 유일: 고시 항목 (양식, 키) · 추가 항목 (양식, 이름) · (양식, 차례).
  - 저장(`NoticeDraft.values[]` 에 `custom=true, name, value, sortOrder` 줄 — `NoticeDraft.Value.custom(...)`): 이름·내용이 모두 빈 줄은 저장하지 않는다, 반쪽 줄·길이 초과는 400, **양식당 20개**, 같은 양식 안 이름 겹침 거절, **고시 항목과 같은 이름은 거절**(공백을 빼고 비교, 「크기, 무게」처럼 쉼표로 나뉜 각 이름도 — 정한 세부), 차례는 `sortOrder`(비면 넣은 차례)로 0부터 다시 매긴다. 평문으로 저장하고 화면이 글자 그대로 보인다.
  - 완성 검사 대상이 아니다(선택). 수정 이력 사본(`notices[].customItems`)·추가 차수 복사 대상.
  - 테스트(G43 보강 3개): 차례대로 저장·빈 줄 제외·완성 검사 밖·이력 사본 / 고시 항목과 같은 이름·21개·이름 겹침·반쪽 줄·41자 거절 + DB CHECK / 차수 복사. `--tests 'com.community.groupbuy.*'` → **92개 통과**, 실패 0.
- **local 시드의 예시 품목 넓히기**(결정 87 「예시(목업·local 시드) 품목을 넓힌다」 — 쌀·과일·생선(20)·김치·소스(21)·티셔츠(1)·운동화(2) + 로봇 키트, 각 공구의 고시 양식을 그 품목으로): **2b-2 범위가 아니라 하지 않았다** — local 시드(결정 11)는 판매 스위치·사업자 정보(V103)와 함께 2b-4 가 만든다. 그때 이 품목들로 공구와 고시 양식을 채운다(신선식품은 `DATE_LIKE` 항목에 일반원칙 ④ 대체 표기 예시).

## 승인 (2026-10-28)
사용자: 「지금까지 확정된 상태로 다음 단계 진행해」 — 결정 1~86·Q&A 규칙·작업 단위·화면 목록·마이그레이션 계획을 추천안 그대로 승인, 2b-2 는 화면 확정 전 병행. 결정 43 은 지금 규칙 유지(나눠 모두 반품해도 한 번에 반품한 것과 같게 — 판매자 손해가 더 커지지 않는다, 처음 배송비까지 받으려면 그 공구의 배송비 정책을 왕복으로).

## 미확정 (승인 때 정할 것 — 승인으로 닫힘, 아래 확인 필요만 남는다)
- 결정 1~14·16·18~20·22·27~36·39~41·46~50·51·55~86 중 정책 판단이 든 것, 작업 단위·화면 목록·마이그레이션 계획(15·17·21·23~26·42~45·52~54 는 사용자 결정 2026-10-28, 37·38·61~66 은 결제 코드로 확인한 필수 규칙, 51·55~59 는 법령 사본으로 확인한 의무).
- [확인 필요 — 2b-5·법무] 이니시스 V2 카드·네이버페이·카카오페이 부분 취소 연속 2회, 에스크로 구매 확정 전·후 부분 취소, PG 가맹점 취소 한도(불발 대량 환불), 공정위 사업자정보공개 링크 의무, 소비자분쟁해결기준·표시광고 고시 현행 원문(최종 검토는 법령 사본·판결 인용으로 확인 — law.go.kr 본문은 못 읽음).
- 2b-2 를 화면 확정 전에 병행할지(2a 와 같이).
- [확인 필요] 「한정 N개」 문구를 쓴 공구에 추가 입고·다음 차수를 열 때 표시·광고 문제 — 등록 화면에 「한정」 표현 안내를 두고 법무 확인 목록(commerce.md 20-2)에 더한다.
