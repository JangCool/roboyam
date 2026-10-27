# 커머스 2b 계약 — 공구(현금 결제) (ERD·패키지 **제안 — 승인 전**, 2026-10-28)

**의도**: 운영자가 직접 파는 실물 공동구매를 연다 — 공구 등록·모집·참여(선점 → 2a 결제 모듈로 결제)·마감 판정·발주·발송·취소·반품·환불·배송지 보호까지. 성사형(`min_qty > 0`)과 선착순(`min_qty = 0`)을 같은 모델로, 발송 시점(`fulfillment`)은 따로 고른다(D1).
정책 원천은 `docs/domain/commerce.md` 10절(공구)·4·12·13·14·15·17·18·19·20절이고, 결제는 `commerce-payment-contract.md`(`PaymentOrderHandler`·`payment_cancels`·확정 코드), 0단계 장치(본인인증·기능 제한·멱등·속도 제한·메일 대기열·필드 암호화·워커 기록·시험 DB)는 `commerce-foundation-contract.md`. 이 문서는 2b 에서 정할 구조와 결정만 적는다.
**테스트**: commerce.md 와 이 문서의 규칙은 모두 테스트로 막고 통과해야 끝난다(CLAUDE.md 커머스 예외, commerce.md 18절 — P3·P3b·P15·P16·P16b~e·P25·P26·P33~P42·P35 와 불변식).
**범위 밖(2b)**: 공구 포인트 결제(11절 — 3+ 단계, 표 열만 둔다) · 회원 주최 공구 · 택배사 API · 쿠폰 · 매출·정산 화면과 세금계산서 요청·발행 표시(16-2 — 21-1 「4」) · 상품 Q&A·리뷰·구매 확정 단추(community 에도 없다) · 저장된 배송지 불러오기.
**community 대조(2026-10-28)**: `D:\project\community\workspace\community\docs\` 의 `api-commerce.md`(C)·`api-commerce-admin.md`(A)·`api-deal-shop.md`(S)·`features.md` 23절·`decisions.md`(D 450~513)와 맞춰 봤다. 큰 줄기는 같고 roboyam 이 더 엄격하다(늦은 결제는 살리지 않고 자동 취소 — community 는 재고가 있으면 살림). 에스크로·영업일·고객 택배비 보상·계좌 송금은 community 에 없어 roboyam 설계가 정본이다. 가져온 것은 아래 결정 13~24 와 화면 목록에 적었다.

## 작업 단위와 순서 (제안)
| 단위 | 내용 | 담당 | 상태 |
|---|---|---|---|
| 2b-1 화면 목업 | 아래 「화면 목록」 | frontend | 제안 |
| 2b-2 공구 핵심 서버 | V100, 공구·옵션·배송비 정책·수정 이력, 참여(자격·멱등·선점)·`GroupBuyPaymentHandler`·선점 만료·이탈 제한, 상태 워커(모집 시작·마감·연장·판정·불발 환불), 본인 취소, 불변식, 규칙 테스트 | backend | 제안 |
| 2b-3 발송·요청·환불 서버 | V101, 발주·준비중·발송·배송완료(일괄 부분 성공)·에스크로 송장 등록, 택배사, 배송지 열람·수정·명단 내보내기, 취소·반품 요청·`GroupBuyRefundCalculator`·보상액, 규칙 테스트 | backend | 제안 |
| 2b-4 설정·알림 + 관리자 API + 연결 | V102, 판매 스위치·체크리스트·사업자 정보·공구 설정, 회원 알림·메일(계약 통지 포함)·관심·수량 풀림·연장 알림, 운영자 알림·기한 경고, 개인정보 파기 pg_cron, 관리자·회원 API 정리, 화면 연결 | backend → frontend | 제안 |
| 2b-5 실제 확인 | 포트원 테스트 채널로 공구 결제·불발 환불·발송 후 반품 환불 사람 확인(공개 주소가 필요한 웹훅·에스크로는 dev 서버) | 사용자 + 세션 | 제안 |
순서: 2b-1 ∥ 2b-2 → 화면 확정 → 2b-3 → 2b-4 → 2b-5. 2b-2 는 화면과 무관한 서버 핵심이라 2a 와 같이 화면 확정 전에 병행한다(승인 필요). 세션마다 자기 worktree.

## 화면 목록 (2b-1)
- **회원(user-web)**
  - `/group-buys` — 탭 진행·예정·종료, 종료 사유(`SOLD_OUT` 품절 · `TIME_UP` 기간 끝 · `CLOSED` 운영 종료), 예정 공구 가격 미리 공개, 마감 임박·남은 수량 적음 표시(남은 10% 이하 또는 5개 이하), 성사형 진행률. (C 72·78, D 450)
  - `/group-buys/{id}` — 이미지·설명·옵션·수량, 배송비·반품 비용 금액 고지(10-8), 연장 안내, **품절과 「잠시 구매 불가(다른 분이 결제 중)」 구분**(`reserved_qty`), 수량 풀림·마감 임박 알림 신청, 하단 사업자 정보(공개 info API), 단추 상태는 서버가 정한 `actions`·`purchase.reason`. (C 92·157, D 450)
  - `/group-buys/{id}/order` — 배송지(우편번호 찾기, 실패 시 직접 입력), 동의 3종, 본인인증 게이트, 결제대금예치 고지, **견적과 합계가 다르면 결제창 대신 「금액이 바뀌었어요」 확인** → 결제창 → 결과. (D 513)
  - `/me/orders`·`/me/orders/{orderNo}` — 상태·결제·배송 조회, 가린 배송지·전체 보기·수정(발주 전), 본인 취소, 취소·반품 요청(**환불 예정 금액 미리 보기**, 하자·오배송 사진 최대 3장), **요청 철회**, 환불 진행 중/완료 표시(결정 16). (C 175·178·181)
- **관리자(admin-web)** — 묶음 「공구」
  - 공구 목록·등록·수정(모집 뒤에는 설명·이미지만)·모집 시작·조기 마감·중단(참여 N명·총 환불 N원 확인)·수정 이력
  - **수량 화면**: 총량·확정·선점·남음, 선점 목록(남은 시간), 수량 변동 내역, 수량 줄이기(결정 17). (A 6절)
  - 주문(참여): 주문 번호·닉네임·회원 번호로 찾기(배송지는 암호문이라 찾지 못한다는 안내), 상태 탭 건수, 상세(배송지 열람 사유, **운영 메모**, 이력), 발주·준비중·발송(화면 안 표로 택배사·송장 일괄 입력, 200건, 행 단위 결과)·**송장 정정**·배송완료(일괄), **운영 환불**(요청 없이 — 고액 문턱), 배송 명단 내보내기. (A 95·120·121·123·140)
  - 취소·반품 요청 대기열: 경과 영업일, 환불 계산 미리 보기, 승인·거절·회수 확인·보상액.
  - 대시보드 커머스 칸(19절): 준비 대기·열린 요청·결제 이상·곧 만료될 선점·남은 수량 적은 공구·**발송 뒤 N일 지난 SHIPPED**(배송완료 처리 누락 — 청약철회 기한이 시작되지 않는다). (A 211)
  - `/settings/commerce` 구획: 배송비 정책(기간별 줄·왕복 경고) · 택배사 · 사업자 정보 · 판매 스위치·체크리스트 · 공구 설정.

## 마이그레이션 계획 (가번호 — develop 에 합칠 때 다시 확인, 지금 shared 마지막 V100 — 2a 보강 `payment_cancel_one_sending`)
| 가번호 | 스트림 | 내용 | 단위 |
|---|---|---|---|
| V101 `group_buy_core` | shared | `shipping_policies`(기간 배제 제약 + 적용 시작 줄 불변 트리거) · `group_buys` · `group_buy_images` · `group_buy_options` · `group_buy_revisions` · `group_buy_participations` · `group_buy_qty_movements` · `group_buy_status_history` · `participation_status_history` · `group_buy_watches` · `group_buy_settings`(한 줄 — `point_shop_settings` 모양) | 2b-2 |
| V102 `group_buy_fulfillment` | shared | `commerce_carriers`(6개 시드) · `group_buy_shipments` · `group_buy_requests` · `group_buy_request_photos` · `commerce_address_views` · `commerce_bank_transfers`(결정 6) | 2b-3 |
| V103 `commerce_sales_switch` | shared | 판매 스위치·체크리스트·사업자 정보(`commerce_sales_settings` 등), `R__batch_32_groupbuy_privacy_cleanup`(배송지 마스킹·수정 이력 6개월·요청 3년) | 2b-4 |
- Java 워커(카탈로그 없음, `worker_heartbeats`): `groupbuy-state`(1분 — 선점 만료·모집 시작·마감·연장·판정), 운영자 기한 점검은 19절 점검 워커가 생길 때까지 2b-4 가 1시간 워커로.
- 열은 commerce.md 10-1 표 그대로. 더하는 것: `group_buys.version`(낙관 잠금), `group_buys.closing_at`(CLOSING 에 들어간 시각 — 판정 시각 계산), `group_buys.summary`(목록 카드 한 줄)·`spec`(규격·구성 평문 ≤ 5,000), 참여 `order_no`(유일 — 결정 13)·`admin_memo`, **참여 CHECK `0 ≤ refunded_amount ≤ amount`**(community 는 이 CHECK 로 이중 환불을 막았다 — D 512), `group_buy_images`(공구·순서·미디어 ID). 참여 `hold_expire_count` 는 두지 않고 `participation_status_history` 의 최근 24시간 EXPIRED 수로 센다.
- `group_buy_requests` 사진은 `group_buy_request_photos`(요청 ID 없이 먼저 올리고 붙인다) — 붙지 않은 사진은 24시간 뒤 정리(C 195).

## 패키지 (`groupbuy → payment`, `groupbuy → point` 단방향 — `payment` 는 공구를 모른다)
- shared `com.community.groupbuy`:
  - `entity`·`dto` — `GroupBuyStatus`·`ParticipationStatus`·`Fulfillment`·`ExtensionMode`·`ReturnFeeMode`·`QtyMovementKind`·`RequestType`·`RequestStatus`·`CollectionMethod`
  - `repository` — 수량·상태 전이는 JDBC(조건부 UPDATE, `SKIP LOCKED`), 관리자 조회는 JPA + QueryDSL
  - `service` — `GroupBuyAdminService`(등록·수정·모집 시작·조기 마감·중단) · `ShippingPolicyService` · `GroupBuyQuoteService`(견적 — 주문 때 다시 계산) · **`GroupBuyJoinService`**(자격 → 선점 → 금액 → 배송지 암호화 → 참여 PENDING → `PaymentOrderService.create`) · **`GroupBuyQtyService`**(RESERVE·RELEASE·CONFIRM·CANCEL·ADJUST — 수량을 바꾸는 유일한 길, 이동 줄 하나씩) · `GroupBuyStateWorker.runOnce` · `GroupBuyJudgeService`(성사·불발 — 불발은 참여 REFUNDED + `PaymentCancelService` 전액) · `ParticipationCancelService` · `FulfillmentService`(발주·준비중·발송·배송완료) · `GroupBuyRequestService` · **`GroupBuyRefundCalculator`**(한 곳 — 요청·승인 화면이 같은 값) · `AddressRevealService` · `GroupBuyInvariantCheck`
  - `handler` — `GroupBuyPaymentHandler implements PaymentOrderHandler`(`GROUP_BUY` — `onPaid`: 8-6 표 판정 → JOINED/IMMEDIATE 면 CONFIRMED + CONFIRM, 아니면 REJECT(`GROUP_BUY_CLOSED`·`HOLD_EXPIRED`) · `onCanceled`: 환불 누계·REFUNDED)
- api `groupbuy.controller`: 목록·상세·견적·참여(`MoneyRequestGuard` — 멱등 키·속도 제한)·결제 다시 열기·내 주문·본인 취소·요청·배송지 보기/수정·관심 신청.
- admin `groupbuy.controller`: 공구·참여자·발주/발송·요청·배송지 열람·명단 내보내기·배송비 정책·택배사·설정, `batch.service.GroupBuyStateScheduler`(1분, `roboyam.batch.enabled` 서버만 — `PaymentCancelScheduler` 모양), 관리자 이미지 올리기(결정 2).

## 세션 정리 결정 (제안 — 추천안)
1. **화면 경로(회원)**: `MenuBuiltin` 그대로 `/group-buys`(목록) · `/group-buys/{id}`(상세) · `/group-buys/{id}/order`(주문서 → 결제창) · `/me/orders`(내 주문) · `/me/orders/{id}`(상세 — 취소·반품 요청·배송지). 결제창은 `/dev/payment` 의 SDK·창 닫기 판정(`UtilPortone`)을 공용 컴포저블로 옮겨 쓴다.
2. **이미지·설명**: 상세 설명은 공용 편집기 `@roboyam/rich-editor`(admin-web 이미 의존 — 글 본문과 같은 서버 정화를 거친다) + 평문 `summary`·`spec`. community 는 XSS 를 피하려 평문 + 줄바꿈만 받았지만(C 39), roboyam 은 이미 정화된 편집기가 있고 상품 설명에 본문 이미지가 필요하다. 수정 이력(`group_buy_revisions`)은 정화된 HTML 사본. 대표 이미지는 최대 8장 · JPEG·PNG·WebP 정지 이미지 · 5MB · 다시 인코딩해 EXIF 제거 · 긴 변 1600px·썸네일 400px · 순서 바꾸기(community A 70 규칙). 관리자 이미지 올리기 API 를 새로 두고 저장은 기존 미디어 저장소(`roboyam.storage`).
3. **관리자 메뉴**: 보통 모드 새 묶음 「공구」(결제 묶음 앞) — 공구 · 주문 · 취소·반품 요청. 배송비 정책·택배사·사업자 정보·판매 스위치·공구 설정은 `/settings/commerce` 에 구획으로 더한다(0-5 계약대로). 권한은 2b 에서 쓰는 것만 `featureReady=true`.
4. **에스크로**: `PaymentGateway` 에 「배송 정보 등록·수정」을 더하고 발송 처리 때 에스크로 결제 건이면 부른다(가짜 PG 는 기록만). 이니시스 테스트 채널의 에스크로 적용 범위는 2b-5 에서 확인하고, 그 전에는 주문서에 「결제대금예치 선택 가능」 고지만.
5. **현금영수증**: 카드·간편결제만 받으므로(4절) 주문서 입력은 두지 않고 표 열만 둔다. community 도 입력칸은 뒀지만 「SDK 현금영수증 인자는 가상계좌·계좌이체용이라 카드 결제에는 싣지 않는다」로 정했다(D 513). 승인되면 commerce.md 10-6 1번의 「현금영수증 선택」을 고친다.
6. **보상액이 남은 결제액을 넘는 부분**: 9-3 의 계좌 송금을 2b-3 에서 공용 표 `commerce_bank_transfers`(계좌 암호문 — `ROBOYAM_DATA_KEY`, 「송금 완료」 후 계좌 삭제)로 먼저 만들고 3단계 충전 환불이 이어 쓴다.
7. **배송완료**: 택배사 API 가 범위 밖이라 운영자가 처리(일괄 가능). 자동 배송완료는 두지 않는다(청약철회 7일의 시작점이라 추측하지 않는다).
8. **영업일**: 주말 + 관리자가 넣는 휴일 목록(`group_buy_settings` 의 휴일 날짜 배열, 설정 화면에서 편집). 공휴일 API 는 쓰지 않는다. 2·3영업일 기한 경고(P35)와 환불 기한 표시가 같이 쓴다.
9. **선착순 즉시 발송 공구의 COMPLETED**: 마감됐고 활성 참여가 모두 DELIVERED·CANCELED·REFUNDED·EXPIRED 이면. 반품 요청은 COMPLETED 뒤에도 받는다(공구 상태가 아니라 참여·요청 기준).
10. **공구 설정(설정값)**: 선점 시간 5분(3~30) · 성사형 기간 상한 14일 · 선착순 30일 · 연장 최대 30분 · 연장 판단 창 5분 · 판정 여유 2분 · 고객 택배비 보상 상한 10,000원 · 고액 환불 문턱 100,000원 · 배송지 열람 화면 유지 10분 · 휴일 목록. 바꿀 때 감사(전→후).
11. **택배사·local 시드**: 택배사는 **마이그레이션 시드(전 환경)** 로 6개(`CJ`·`HANJIN`·`LOTTE`·`EPOST`·`LOGEN`·`KDEXP`) + 조회 주소 템플릿(`https://`, `{no}` 한 번). 목록은 통째로 저장, 발송 이력이 있는 코드는 지우지 못하고 끄기만, 송장번호는 숫자·영문 6~30자로 정리(community A 118·178). local 시드는 판매 스위치 켜짐·체크리스트 전부 체크·시험 사업자 정보·배송비 정책 `STANDARD`(3,000 · 무료 50,000 · 반품 3,000 · 편도) + 시험 공구 셋(성사형 모집 중 · 선착순 즉시 발송 · 마감 임박) — `seed/` 와 `PortOneLocalSeeder` 모양(local 만).
12. **운영자 알림**: 예약된 `COMMERCE_ORDER_ANOMALY`(공구 결제 REJECT·불변식 불일치) · `COMMERCE_REFUND_REQUESTED`(취소·반품 요청 — WARNING, 2영업일 경고 · 3영업일 CRITICAL) + 새 `COMMERCE_GROUPBUY_CANCELED`(중단)·`COMMERCE_SALES_TOGGLE`. 받는 사람 = `commerce.order.manage`·`commerce.refund.approve` 보유자.
13. **주문 번호**: 참여마다 `G` + KST 날짜 `yyyyMMdd` + 그날 일련 5자리(예 `G2026102800012`, 하루 99,999건 — 넘으면 자리를 늘린다). 회원 주소 `/me/orders/{orderNo}`, 결제창 주문명·메일·고객 응대에 쓴다. 포트원 `redirectUrl` 은 쿼리 없이 주문 상세로 돌아온다(D 513). (community 는 4자리 시퀀스 — C 43)
14. **에스크로 구매 확정**: 에스크로 결제 건은 배송완료 뒤 청약철회 기간(7일)이 열린 요청 없이 지나면 `escrow/complete` 를 부른다(하루 한 번, 실패는 재시도·결제 이상). 송장 정정 때는 `PATCH` 로 배송 정보를 다시 보낸다. 실제 동작은 2b-5 에서 확인.
15. **반품과 수량**: 반품(전체·부분)은 공구 수량을 되돌리지 않는다 — 대부분 모집이 끝난 뒤라 다시 팔 자리가 없다. 다시 팔려면 새 공구. 발송 전 취소는 commerce.md 10-4 대로 `CANCEL`(모집 중이면 다시 팔린다). (community 는 전체 반품만 재입고 — A 138)
16. **환불 진행 표시**: 참여는 같은 트랜잭션에서 REFUNDED·CANCELED 로 바뀌지만 돈은 취소 워커가 보낸다. 회원·관리자 화면은 연결된 `payment_cancels` 상태로 「환불 진행 중 · 카드사 반영까지 3~7영업일」/「환불 완료(날짜)」/「환불 지연 — 확인 중」(GAVE_UP)을 나눠 보인다. community 의 「취소됐지만 환불 실패」 상태(A 141)를 이 표시로 대신한다.
17. **모집 뒤 수량 조정**(`commerce.stock.adjust`): commerce.md 10-2 「모집 뒤 수량 변경 불가」를 지키되 **최대 수량 줄이기만** 허용(품절·불량·입고 부족). `확정 + 선점` 아래로는 못 줄인다, 사유 필수, 이동 줄 ADJUST·감사(돈 관련). 늘리기는 새 공구.
18. **사업자 정보와 공개 API**: 칸은 상호·대표자·사업자등록번호(체크섬)·통신판매업 신고번호·신고 기관·주소·전화·이메일 + **운영시간·개인정보 관리책임자·호스팅 사업자·배송/환불 정책 문구(≤ 3,000)**(community A 195). 평문 저장. 회원 공개 `GET /api/commerce/info`(값 + `ready`) — 공구 화면 하단. 판매 켜기 자동 조건에 community 의 「공개된 공구 1개 이상」은 넣지 않는다(공구를 열기 전에 판매를 켜 두는 순서를 막지 않게).
19. **설정 읽기**: 선점 시간·배송비·문턱처럼 돈과 시각에 닿는 값은 **그 트랜잭션 안에서 DB 의 지금 값**을 읽는다(캐시 금지 — community A 176·S 290). 화면 표시용만 캐시.
20. **내보내기 안전**: 배송 명단 CSV 는 `= + - @` 로 시작하는 칸 앞에 `'` 를 붙인다(수식 주입 — community A 163).
21. **문의 창구**: 상품 Q&A 는 두지 않는다. 하자·오배송은 반품 요청(하자)으로, 그 밖의 문의는 사업자 정보의 전화·이메일. commerce.md 15-5 의 「문의 3년」 파기는 요청 표에만 걸린다.
22. **관리자 날짜 필터**: 한국 날짜, 양 끝 포함(community D 640).
- **2a 보강(고침, 2026-10-28)**: 포트원 취소 호출을 결제 주문 행 잠금 밖으로 옮기고, 한 결제에 보내는 중인 취소는 하나(V100)로 줄을 세웠다 — 공구 불발로 취소가 몰려도 확정·웹훅이 PG 응답을 기다리지 않는다. 상세는 `commerce-payment-contract.md` 「2a 보강 — 취소 호출을 잠금 밖으로」.
- 감사 행위 후보: `GROUPBUY_CREATE`·`GROUPBUY_UPDATE`·`GROUPBUY_START`·`GROUPBUY_CLOSE_EARLY`(일반) · `GROUPBUY_CANCEL`·`GROUPBUY_REFUND`·`GROUPBUY_REQUEST_APPROVE`·`GROUPBUY_STOCK_ADJUST`·`COMMERCE_SALES_TOGGLE`·`COMMERCE_SHIPPING_POLICY_*`(돈 관련) · `GROUPBUY_ADDRESS_REVEAL`·`GROUPBUY_SHIPPING_EXPORT`·`GROUPBUY_ORDER_VIEW`(개인정보 열람).
- 오류 코드 후보: `GROUP_BUY_NOT_FOUND`(404) · `GROUP_BUY_NOT_RECRUITING`(409) · `GROUP_BUY_SOLD_OUT`(409) · `GROUP_BUY_LIMIT_EXCEEDED`(409) · `GROUP_BUY_ALREADY_JOINED`(409) · `GROUP_BUY_SALES_CLOSED`(403) · `GROUP_BUY_HOLD_EXPIRED`(409) · `GROUP_BUY_LOCKED_FIELD`(409 — 모집 시작 뒤 가격·수량·기간 수정) · `GROUP_BUY_REQUEST_OPEN`(409) · `GROUP_BUY_RETURN_WINDOW_CLOSED`(409) · `DATA_KEY_MISSING`(503).

## 미확정 (승인 때 정할 것)
- 위 결정 1~22 와 작업 단위·화면 목록·마이그레이션 계획.
- 2b-2 를 화면 확정 전에 병행할지(2a 와 같이).
