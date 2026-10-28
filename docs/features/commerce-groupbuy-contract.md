# 커머스 2b 계약 — 공구(현금 결제) (ERD·패키지 **제안 — 승인 전**, 2026-10-28)

**의도**: 운영자가 직접 파는 실물 공동구매를 연다 — 공구 등록·모집·참여(선점 → 2a 결제 모듈로 결제)·마감 판정·발주·발송·취소·반품·환불·배송지 보호·Q&A 까지. 성사형(`min_qty > 0`)과 선착순(`min_qty = 0`)을 같은 모델로, 발송 시점(`fulfillment`)은 따로 고른다(D1).
정책 원천은 `docs/domain/commerce.md` 10절(공구)·4·12·13·14·15·17·18·19·20절이고, 결제는 `commerce-payment-contract.md`(`PaymentOrderHandler`·`payment_cancels`·확정 코드), 0단계 장치(본인인증·기능 제한·멱등·속도 제한·메일 대기열·필드 암호화·워커 기록·시험 DB)는 `commerce-foundation-contract.md`. 이 문서는 2b 에서 정할 구조와 결정만 적는다.
**테스트**: commerce.md 와 이 문서의 규칙은 모두 테스트로 막고 통과해야 끝난다(CLAUDE.md 커머스 예외, commerce.md 18절 — P3·P3b·P15·P16·P16b~e·P25·P26·P33~P42·P35 와 불변식 + 아래 「새 규칙 → 테스트」 G1~G12).
**범위 밖(2b)**: 공구 포인트 결제(11절 — 3+ 단계, 표 열만 둔다) · 회원 주최 공구 · 택배사 API · 쿠폰 · 매출·정산 화면과 세금계산서 요청·발행 표시(16-2 — 21-1 「4」) · 리뷰·구매 확정 단추(community 에도 없다) · 저장된 배송지 불러오기 · 1:1 문의(roboyam 에 없다 — 공구 밖 문의는 사업자 정보의 전화·이메일).
**community 대조(2026-10-28)**: `D:\project\community\workspace\community\docs\` 의 `api-commerce.md`(C)·`api-commerce-admin.md`(A)·`api-deal-shop.md`(S)·`features.md` 23절·`decisions.md`(D 450~513)와 맞춰 봤다. 큰 줄기는 같고 roboyam 이 더 엄격하다(늦은 결제는 살리지 않고 자동 취소 — community 는 재고가 있으면 살림). 에스크로·영업일·고객 택배비 보상·계좌 송금은 community 에 없어 roboyam 설계가 정본이다. 가져온 것은 결정 13~22 와 화면 목록에 적었다.
**사용자 결정(2026-10-28)**: 결정 15(반품 처리 방법 선택)·17(추가 입고 = 늘리기 + 추가 차수)·21(공구 Q&A)·23(한 주문에 여러 옵션)·24(차수 사이 1인 한도는 공구별 선택, 기본 독립)·25(다시 판매·추가 입고도 마감 연장 조건)·26(Q&A 개인정보 자동 비밀글 + 안내 · 신고 대상 · 3년 보관 · 답변 수정 이력). 그 뒤 처음 보는 검토자 점검(같은 날)에서 나온 빈 곳은 「수량·상태 규칙」·「Q&A 규칙」에 메웠다.

## 작업 단위와 순서 (제안)
| 단위 | 내용 | 담당 | 상태 |
|---|---|---|---|
| 2b-1 화면 목업 | 아래 「화면 목록」(Q&A 제외) | frontend | 제안 |
| 2b-2 공구 핵심 서버 | V101, 공구·옵션·배송비 정책·수정 이력, 주문·주문 줄(자격·멱등·선점)·`GroupBuyPaymentHandler`·선점 만료·이탈 제한, 상태 워커(모집 시작·마감·연장·판정·불발 환불), 본인 취소, 추가 입고·줄이기·추가 차수, 불변식, 규칙 테스트 | backend | 제안 |
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
| V102 `group_buy_fulfillment` | shared | `commerce_carriers`(6개 시드) · `group_buy_shipments` · `group_buy_requests` · **`group_buy_request_items`**(요청 줄 — 주문 줄·수량) · **`group_buy_return_dispositions`**(요청 줄·처리 방법·수량) · `group_buy_request_photos` · `commerce_address_views` · `commerce_bank_transfers`(결정 6) | 2b-3 |
| V103 `commerce_sales_switch` | shared | 판매 스위치·체크리스트·사업자 정보(`commerce_sales_settings` 등), `R__batch_32_groupbuy_privacy_cleanup`(배송지 마스킹·수정 이력 6개월·요청 3년·만료 주문 배송지 — 결정 34) | 2b-4 |
| V104 `group_buy_qna` | shared | `group_buy_questions` · `group_buy_answers` · `group_buy_answer_revisions`, 신고 대상 `GROUP_BUY_QUESTION`(기존 `reports` 의 대상 종류 CHECK), 파기 배치에 3년 더하기 | 2b-6 |
- Java 워커(카탈로그 없음, `worker_heartbeats`): `groupbuy-state`(1분 — 선점 만료·모집 시작·마감·연장·판정, 다 팔림 마감의 안전망), 운영자 기한 점검은 19절 점검 워커가 생길 때까지 2b-4 가 1시간 워커로.
- 열은 commerce.md 10-1 표가 바탕이고, 바뀌거나 더하는 것:
  - **주문과 주문 줄**(결정 23): `group_buy_participations` 는 주문 한 건(회원·배송지·배송비·금액·결제·상태·`order_no`·`admin_memo`·`refunded_amount`·`reshipping_fee_charged`). 옵션·수량·단가는 `group_buy_participation_items`(주문·옵션·`qty`·`unit_price`·`item_amount`·`canceled_qty`·`returned_qty`·`restocked_qty`, CHECK `0 ≤ restocked_qty ≤ returned_qty`, `canceled_qty + returned_qty ≤ qty`). 옵션이 없는 공구는 줄 하나(옵션 null).
  - 주문 CHECK `0 ≤ refunded_amount ≤ amount`(community 는 이 CHECK 로 이중 환불을 막았다 — D 512).
  - `group_buys`: `version`(설명·이미지 편집의 낙관 잠금 — 수량 열은 조건부 UPDATE 로 바꾸고 `version` 을 올리지 않는다), `closing_at`, `summary`·`spec`(평문 ≤ 5,000), `round_root_id`(1차 공구 ID, 1차는 자기 자신)·`round_no`(유일 `(round_root_id, round_no)`)·`round_limit_shared`(결정 24)·`added_qty`, CHECK `min_qty ≤ max_qty`.
  - `group_buy_revisions` 에 수량 사본(`max_qty`·`added_qty`, 옵션별 최대)을 더한다 — 추가 입고도 표시 기록. 주문은 결제 때의 `revision_no` 를 남긴다(낙관 잠금 `version` 과 다른 번호).
  - 수량 이동 종류: RESERVE·RELEASE·CONFIRM·CANCEL·RETURN(반품 기록, 수량 불변)·RESTOCK(다시 판매, 확정 수량 감소)·ADD(추가 입고, 최대 증가)·REDUCE(줄이기, 최대 감소). 이동 줄마다 `option_id` 와 공구·옵션 양쪽의 이후 값.
  - `group_buy_watches.kind` 에 `NEXT_ROUND`(다음 차수 알림 — 1차 공구 기준) 를 더한다.
  - 참여 `hold_expire_count` 는 두지 않고 `participation_status_history` 의 최근 24시간 EXPIRED 중 원인 `HOLD_TIMEOUT` 만 센다(결정 28).
- 요청 사진은 `group_buy_request_photos`(요청 ID 없이 먼저 올리고 붙인다) — 붙지 않은 사진은 24시간 뒤 정리(C 195).

## 패키지 (`groupbuy → payment`, `groupbuy → point` 단방향 — `payment` 는 공구를 모른다)
- shared `com.community.groupbuy`:
  - `entity`·`dto` — `GroupBuyStatus`·`ParticipationStatus`·`Fulfillment`·`ExtensionMode`·`ReturnFeeMode`·`QtyMovementKind`·`RequestType`·`RequestStatus`·`CollectionMethod`·`ReturnDisposition`·`ExpireCause`
  - `repository` — 수량·상태 전이는 JDBC(조건부 UPDATE, `SKIP LOCKED`), 관리자 조회는 JPA + QueryDSL
  - `service` — `GroupBuyAdminService`(등록·수정·모집 시작·조기 마감·중단·추가 차수) · `ShippingPolicyService` · `GroupBuyQuoteService`(견적 — 주문 때 다시 계산) · **`GroupBuyJoinService`**(자격 → 선점 → 금액 → 배송지 암호화 → 주문 PENDING → `PaymentOrderService.create`) · **`GroupBuyQtyService`**(RESERVE·RELEASE·CONFIRM·CANCEL·RETURN·RESTOCK·ADD·REDUCE — 수량을 바꾸는 유일한 길, 공구 행 → 옵션 행 차례로 잠금, 이동 줄 하나씩) · `GroupBuyStateWorker.runOnce` · `GroupBuyJudgeService`(성사·불발 — 불발은 주문 REFUNDED + CANCEL 이동 + `PaymentCancelService` 전액) · `ParticipationCancelService` · `FulfillmentService`(발주·준비중·발송·배송완료) · `GroupBuyRequestService` · **`GroupBuyRefundCalculator`**(한 곳 — 요청·승인 화면이 같은 값, 재부과 배송비 누계로 차액만) · `AddressRevealService` · `GroupBuyInvariantCheck`
  - `handler` — `GroupBuyPaymentHandler implements PaymentOrderHandler`(`GROUP_BUY` — `onPaid`: 8-6 표 판정 → JOINED/IMMEDIATE 면 CONFIRMED + CONFIRM(+ 다 팔리면 같은 트랜잭션에서 CLOSING — 결정 27), 아니면 REJECT(`GROUP_BUY_CLOSED`·`HOLD_EXPIRED`) · `onCanceled`: 환불 누계·REFUNDED)
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
9. **공구 COMPLETED**: 선착순 즉시 발송은 마감됐고 수량에 들어간 주문(결정 28)이 모두 DELIVERED·CANCELED·REFUNDED 이면. 마감 후 발주는 `ORDERED → SHIPPING`(첫 발송 처리 때) `→ COMPLETED`(같은 조건). 반품 요청은 COMPLETED 뒤에도 받는다(공구 상태가 아니라 주문·요청 기준).
10. **공구 설정(설정값)**: 선점 시간 5분(3~30) · 성사형 기간 상한 14일 · 선착순 30일 · 연장 최대 30분 · 연장 판단 창 5분 · 판정 여유 2분 · 고객 택배비 보상 상한 10,000원 · 고액 환불 문턱 100,000원 · 배송지 열람 화면 유지 10분 · 휴일 목록 · 만료 주문 배송지 보관 30일. 바꿀 때 감사(전→후).
11. **택배사·local 시드**: 택배사는 **마이그레이션 시드(전 환경)** 로 6개(`CJ`·`HANJIN`·`LOTTE`·`EPOST`·`LOGEN`·`KDEXP`) + 조회 주소 템플릿(`https://`, `{no}` 한 번). 목록은 통째로 저장, 발송 이력이 있는 코드는 지우지 못하고 끄기만, 송장번호는 숫자·영문 6~30자로 정리(community A 118·178). local 시드는 판매 스위치 켜짐·체크리스트 전부 체크·시험 사업자 정보·배송비 정책 `STANDARD`(3,000 · 무료 50,000 · 반품 3,000 · 편도) + 시험 공구 넷(성사형 모집 중 · 선착순 즉시 발송(옵션 셋) · 마감 임박 · 다 팔려 끝난 1차) — `seed/` 와 `PortOneLocalSeeder` 모양(local 만).
12. **운영자 알림**: 예약된 `COMMERCE_ORDER_ANOMALY`(공구 결제 REJECT·불변식 불일치) · `COMMERCE_REFUND_REQUESTED`(취소·반품 요청 — WARNING, 2영업일 경고 · 3영업일 CRITICAL) + 새 `COMMERCE_GROUPBUY_CANCELED`(중단)·`COMMERCE_SALES_TOGGLE`·`COMMERCE_GROUPBUY_QUESTION`(새 질문·신고 — INFO, 묶기). 받는 사람 = `commerce.order.manage`·`commerce.refund.approve` 보유자.
13. **주문 번호**: 주문마다 `G` + KST 날짜 `yyyyMMdd` + 그날 일련 5자리(예 `G2026102800012`, 하루 99,999건 — 넘으면 자리를 늘린다). 회원 주소 `/me/orders/{orderNo}`, 결제창 주문명·메일·고객 응대에 쓴다. 포트원 `redirectUrl` 은 쿼리 없이 주문 상세로 돌아온다(D 513). (community 는 4자리 시퀀스 — C 43)
14. **에스크로 구매 확정**: 에스크로 결제 건은 배송완료 뒤 청약철회 기간(7일)이 열린 요청 없이 지나면 `escrow/complete` 를 부른다(하루 한 번, 실패는 재시도·결제 이상). 송장 정정 때는 `PATCH` 로 배송 정보를 다시 보낸다. 실제 동작은 2b-5 에서 확인.
15. **반품 상품 처리**(사용자 결정): 반품 완료 때 운영자가 **요청 줄마다 처리 방법과 수량**을 고른다(필수, 합 = 그 줄의 반품 수량 — 예 「2개 다시 판매 + 1개 폐기」).
    - **다시 판매**(`RESELL`): 공구가 **모집 중**(`RECRUITING`)이고 **사유가 단순 변심**(`CHANGE_OF_MIND`)일 때만. 하자·오배송은 서버가 거절(`GROUP_BUY_NOT_RESELLABLE`). 그 옵션·공구의 확정 수량을 줄여(이동 `RESTOCK`, 조건 `status='RECRUITING'`) 남은 수량이 늘고, 수량 풀림 알림. 반품은 배송완료 뒤에만 생기므로 실제로는 선착순 즉시 발송 공구에서만 고를 수 있다 — 화면은 다른 공구에서 이 선택지를 숨긴다.
    - **폐기·공급처 반송·보관**(`DISCARD`·`RETURN_TO_SUPPLIER`·`KEEP`): 공구 수량은 그대로, 이동 `RETURN` 으로 기록만.
    - 수량 화면에 판매·반품·다시 판매를 나눠 보인다. 성사 판정은 뒤집지 않는다(10-4). (community 는 전체 반품만 자동 재입고 — A 138)
16. **환불 진행 표시**: 주문은 같은 트랜잭션에서 REFUNDED·CANCELED 로 바뀌지만 돈은 취소 워커가 보낸다. 회원·관리자 화면은 연결된 `payment_cancels` 상태로 「환불 진행 중 · 카드사 반영까지 3~7영업일」/「환불 완료(날짜)」/「환불 지연 — 확인 중」(GAVE_UP)을 나눠 보인다. community 의 「취소됐지만 환불 실패」 상태(A 141)를 이 표시로 대신한다.
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
28. **수량에 들어가는 주문과 불변식**: 결제 확정(CONFIRM)된 주문 줄의 `qty − canceled_qty − restocked_qty` 가 「판매 수량」이다. 발송 전 취소·불발·중단 환불은 CANCEL 이동 + `canceled_qty`, 반품은 `returned_qty`(폐기·반송·보관이면 판매 수량 그대로 — 이미 팔려 나간 것), 다시 판매는 RESTOCK + `restocked_qty`. **불변식**: 공구·옵션별 `current_qty` = Σ 판매 수량, `reserved_qty` = Σ PENDING_PAYMENT 줄 수량, 이동 줄 마지막 값 = 현재 값(commerce.md 18절을 이 식으로 고친다). **1인 한도** = 그 회원의 Σ 판매 수량 + 진행 중 선점(+ 결정 24 합산). **진행 중 주문**(부분 유일) = PENDING_PAYMENT·JOINED·CONFIRMED·PREPARING·SHIPPED. **만료 원인**(`ExpireCause`): `HOLD_TIMEOUT`(선점 시간 초과 — 이탈 횟수에 센다)·`GROUP_BUY_CANCELED`·`PAYMENT_FAILED_CLOSED` 등 — 이탈 제한(`GROUP_BUY_BAN`)은 `HOLD_TIMEOUT` 만 센다.
29. **중단**: 한 트랜잭션에서 ① PENDING_PAYMENT → EXPIRED(원인 `GROUP_BUY_CANCELED`) + RELEASE + 연결된 `payment_orders` READY → EXPIRED(뒤늦은 결제는 늦은 결제로 자동 취소) ② **발송 전**(JOINED·CONFIRMED·PREPARING) → REFUNDED + CANCEL + 전액 취소 ③ **이미 발송**(SHIPPED·DELIVERED)은 그대로 정상 흐름(반품은 요청으로). 확인 창에 「환불 N명 · 총 N원 / 이미 발송 M명」.
30. **배송지 수정·명단 내보내기 기한은 주문 기준**: 수정은 마감 후 발주 공구면 공구 ORDERED 전, 즉시 발송 공구면 주문 PREPARING 전. 명단 내보내기는 「발송 전 주문(CONFIRMED·PREPARING)」 단위 — 마감 후 발주 공구는 ORDERED 뒤, 즉시 발송 공구는 언제든(commerce.md 10-5·10-9 를 고친다).
31. **추가 차수**: 원본은 모집이 끝난 공구(CLOSING 이후 — 다 팔림·기간 끝·완료, 중단 제외). 같은 루트에 DRAFT·RECRUITING 차수가 이미 있으면 거절(동시 클릭은 유일 `(round_root_id, round_no)` 가 막는다). `round_root_id` = 1차 공구, `round_no` = 그 루트의 최대 + 1. 복사: 설명·이미지·`summary`·`spec`·옵션 이름·추가금·방식(성사형/선착순·발송 시점·연장 방식)·`round_limit_shared`. 새로 넣음: 수량·기간·가격(복사값을 채워 두되 DRAFT 라 고칠 수 있다). 배송비는 모집 시작 때 정책. 복사할 때 「이전 차수의 날짜·수량·배송비 문구가 설명에 남아 있는지 확인하세요」 경고, 모집 시작 때 수정 이력 첫 판. 모집을 시작하면 루트의 모든 차수의 `NEXT_ROUND`·`RESTOCK`·`CLOSING_SOON` 신청자에게 한 번 알림(같은 회원 한 번), 신청은 새 차수로 옮기지 않는다.
32. **운영 환불**(요청 없이, `commerce.refund.approve` · 고액 문턱 `commerce.refund.large`, 사유 필수): 발송 전 주문(JOINED·CONFIRMED·PREPARING)은 「발송 전 취소」와 같다(CANCELED + CANCEL 이동, 줄·수량 지정 가능). 배송완료 주문은 **금액만 돌려주는 환불**(회수 없음 — 보상·가격 조정)로 수량 이동 없이 `refunded_amount` 만, 누계가 결제액이 되면 REFUNDED. SHIPPED 주문은 거절(배송완료 뒤 또는 반품 요청으로).
33. **연속 부분 반품의 배송비**: 주문에 `reshipping_fee_charged`(지금까지 재부과한 배송비) — `GroupBuyRefundCalculator` 는 「남은 수량 기준 배송비 − 이미 재부과한 것」의 차액만 뺀다. 마지막 남은 것을 반품하면 전체 반품 규칙(편도·왕복)으로 다시 맞춘다.
34. **배송지 파기 보강**: 결제하지 못한 주문(EXPIRED)은 만료 30일 뒤(설정값) 배송지 암호문 삭제. 불발·중단 환불(REFUNDED)은 commerce.md 10-9 「취소 후 6개월」.
35. **권한**(commerce.md 13-2 에 더한다): 추가 입고·줄이기 `commerce.stock.adjust` · 추가 차수 만들기 `commerce.product.manage` · 반품 처리 방법(다시 판매 포함) `commerce.order.manage`(반품 완료 처리와 같은 사람) · Q&A 답변·숨김·신고 처리 `commerce.order.manage`, Q&A 조회 `commerce.order.read`.

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

## 감사·오류 코드 후보
- 감사 행위: `GROUPBUY_CREATE`·`GROUPBUY_UPDATE`·`GROUPBUY_START`·`GROUPBUY_CLOSE_EARLY`(일반) · `GROUPBUY_CANCEL`·`GROUPBUY_REFUND`·`GROUPBUY_REQUEST_APPROVE`·`GROUPBUY_STOCK_ADD`·`GROUPBUY_STOCK_REDUCE`·`GROUPBUY_RETURN_DISPOSE`·`GROUPBUY_ROUND_CREATE`·`COMMERCE_SALES_TOGGLE`·`COMMERCE_SHIPPING_POLICY_*`(돈 관련) · `GROUPBUY_ADDRESS_REVEAL`·`GROUPBUY_SHIPPING_EXPORT`·`GROUPBUY_ORDER_VIEW`·`GROUPBUY_QNA_VIEW`(개인정보 열람) · `GROUPBUY_QNA_HIDE`·`GROUPBUY_QNA_ANSWER_EDIT`(일반).
- 오류 코드: `GROUP_BUY_NOT_FOUND`(404) · `GROUP_BUY_NOT_RECRUITING`(409) · `GROUP_BUY_SOLD_OUT`(409) · `GROUP_BUY_OPTION_SOLD_OUT`(409) · `GROUP_BUY_LIMIT_EXCEEDED`(409) · `GROUP_BUY_ALREADY_JOINED`(409) · `GROUP_BUY_SALES_CLOSED`(403) · `GROUP_BUY_HOLD_EXPIRED`(409) · `GROUP_BUY_LOCKED_FIELD`(409 — 모집 시작 뒤 가격·기간·옵션 구성·배송비 수정) · `GROUP_BUY_QTY_BELOW_FLOOR`(409 — 줄이기 하한) · `GROUP_BUY_NOT_RESELLABLE`(409) · `GROUP_BUY_ROUND_EXISTS`(409) · `GROUP_BUY_ROUND_SOURCE_INVALID`(409) · `GROUP_BUY_REQUEST_OPEN`(409) · `GROUP_BUY_RETURN_WINDOW_CLOSED`(409) · `GROUP_BUY_QUESTION_ANSWERED`(409 — 답변 뒤 수정) · `GROUP_BUY_QUESTION_CLOSED`(409 — 중단된 공구) · `DATA_KEY_MISSING`(503).

## 2a 보강(고침, 2026-10-28)
포트원 취소 호출을 결제 주문 행 잠금 밖으로 옮기고, 한 결제에 보내는 중인 취소는 하나(V100)로 줄을 세웠다 — 공구 불발로 취소가 몰려도 확정·웹훅이 PG 응답을 기다리지 않는다. 상세는 `commerce-payment-contract.md` 「2a 보강 — 취소 호출을 잠금 밖으로」.

## 미확정 (승인 때 정할 것)
- 결정 1~14·16·18~20·22·27~35, 작업 단위·화면 목록·마이그레이션 계획(15·17·21·23~26 은 사용자 결정 2026-10-28).
- 2b-2 를 화면 확정 전에 병행할지(2a 와 같이).
- [확인 필요] 「한정 N개」 문구를 쓴 공구에 추가 입고·다음 차수를 열 때 표시·광고 문제 — 등록 화면에 「한정」 표현 안내를 두고 법무 확인 목록(commerce.md 20-2)에 더한다.
