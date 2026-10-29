# 커머스 2b 계약 — 공구(현금 결제)

**의도**: 운영자가 직접 파는 실물 공동구매 — 상품·공구 등록, 모집, 참여(선점 → 2a 결제 모듈로 결제), 마감 판정, 발주·발송, 취소·반품·교환·환불, 배송지 보호, Q&A·리뷰. 성사형(`min_qty > 0`)과 선착순(`min_qty = 0`)을 같은 모델로 두고, 발송 시점(`fulfillment` — 마감 후 발주 / 즉시 발송)은 따로 고른다(D1).
**정본 관계**: 정책 원천은 `docs/domain/commerce.md` 10절(공구)·4·12·13·14·15·17·18·19·20절이다. **이 계약과 commerce.md 가 다르면 이 계약이 정본**이다(결정에 「commerce.md ○○ 를 고친다」로 적은 곳, commerce.md 10-1 표는 처음 설계 기록). 결제는 `commerce-payment-contract.md`(`PaymentOrderHandler`·`payment_cancels`·확정 코드), 0단계 장치(본인인증·기능 제한·멱등·속도 제한·메일 대기열·필드 암호화·워커 기록·시험 DB)는 `commerce-foundation-contract.md`.
**테스트**: commerce.md 와 이 문서의 규칙은 모두 테스트로 막고 통과해야 끝난다(CLAUDE.md 커머스 예외, commerce.md 18절 P 시나리오 + 이 문서 G1~G45). 모두 실제 PostgreSQL(`CommerceTestDatabase`), 시험 끝마다 불변식(`assertInvariants`). 대응은 「규칙 → 테스트」.
**community 와의 관계**: 큰 줄기는 community 커머스와 같고 roboyam 이 더 엄격하다(늦은 결제는 살리지 않고 자동 취소). 에스크로·영업일·고객 택배비 보상·계좌 송금은 roboyam 설계가 정본.

## 범위 밖
- **뒤 단계로 미룸**: 공구 포인트 결제(commerce.md 11절 — 3+ 단계, 열만 둔다: 결정 76) · 회원 주최 공구 · 택배사 API(배송완료는 운영자 — 결정 7) · 매출·정산 화면과 세금계산서 요청·발행(16-2) · 구매 확정 단추 · 저장된 배송지 불러오기 · 1:1 문의(공구 밖 문의는 사업자 정보의 전화·이메일) · 장바구니(결정 88) · 입력형 옵션(각인 문구 등 — 주문 줄에 적는 글)·주문 제작 공구(결정 55).
- **하지 않기로 함**:
  - **쿠폰·프로모션(장바구니·등급 할인·정기 결제 포함)은 하지 않는다** — 공구가 자체가 할인이고, 부분 환불 계산이 복잡해지며 1인 운영으로 악용 관리가 어렵다. 이벤트성 혜택은 회원 포인트 지급으로 대신한다(사용자 결정 2026-10-29). 실제 운영에서 요청이 생기면 그때 검토.
  - 발송 지연 보상(결정 52 — 자리도 두지 않는다).

## 작업 단위
| 단위 | 내용 | 상태 |
|---|---|---|
| 2b-1 화면 | 아래 「화면 목록」 | 확정 |
| 2b-2 공구 핵심 서버 | V101 — 상품·공구·단품·주문·선점·결제 처리기·상태/환불 워커·본인 취소·수량 조정·추가 차수·고시 | 완료 |
| 2b-3 발송·요청·환불 서버 | V102·V103 — 발주·발송·에스크로·배송지 열람·요청·계산기·운영 환불·공급 불가·계좌 송금 | 완료 |
| 2b-4 설정·알림·API·연결 | V104·`R__batch_32` — 판매 스위치·사업자 정보·설정·알림·메일·기한 점검·개인정보 파기·컨트롤러·화면 연결·local 시드 | 완료 |
| 2b-5 실제 확인 | 포트원 테스트 채널로 결제·취소·환불 | 로컬 완료 · **dev 서버 확인 남음**(「남은 일」) |
| 2b-6 Q&A·리뷰 | V106 — 「Q&A 규칙」·「리뷰 규칙」 | 완료 |
| 홈 가젯 | 「홈 `GROUP_BUY` 가젯 연결」 | 완료 |

## 화면 목록
모든 서버 호출은 `composables/groupbuy/api/use…Api.ts`·`composables/commerce/api/use…Api.ts` 한 겹(요청·응답 모양은 각 `*.types.ts`). 금액·남은 수량·환불 미리 보기는 서버가 계산한 값을 보여 주기만 하고, 단추는 서버 `actions`·`purchase.reason` 만 본다.
- **회원(user-web)**
  - `/group-buys` — 탭 진행·예정·종료(`?phase=`, 서버 `phase` UPCOMING·ONGOING·ENDED), 카테고리 칩(`?category=` — 위 단 → 아래 단 칩), 종료 사유 딱지(`SOLD_OUT` 다 팔림·`TIME_UP` 기간 끝·`EARLY_CLOSED` 조기 마감·`CANCELED` 운영 종료), 예정 공구 가격 미리 공개, 마감 임박(서버 `closingSoon` — 24시간)·남은 수량 적음(≤ 5개 또는 ≤ 10%, 화면 판정), 성사형 진행률, 「N차」, 리뷰 평균·수.
  - `/group-buys/{id}` — 탭(상품상세·상품평·상품문의·배송/교환/반품 안내 — 붙는 탭·scrollspy·해시). 단품 고르기: 옵션 종류 차례로(앞 축 전엔 뒤 축 꺼짐, 판매 안 함 비활성·품절 흐리게 + 「다 팔림 / 잠시 구매 불가」, 옵션 없이 이름으로 가리는 단품은 한 목록) → 「선택한 상품」(`GroupBuyPickedLines` — 같은 단품 +1, 수량 ±·빼기, 상한 = 단품 남은 수량과 1인 한도(MAIN 합)) → 「추가 구성상품」(본 상품 없으면 담기 꺼짐, 마지막 MAIN 을 빼면 「추가 구성상품도 뺄까요?」) → 서버 견적(`GroupBuyQuoteSummary`) → 「구매하기」. 고른 목록은 주소 쿼리 `?items=skuId:qty,…`(최대 50줄, ID 19자리까지 — sessionStorage 안 씀). 예정·종료·막힘은 「상품별 남은 수량」만. 그 밖: 다 팔림 ↔ 잠시 구매 불가(공구·단품), 알림 신청 `RESTOCK`(단품)·`CLOSING_SOON`·`NEXT_ROUND`(다 팔려 끝난 공구), 연장 안내, 「추가 입고 N개」·「N차」, 발송 지연 공지, 배송비·반품비 금액, 정상가 기준, 상품정보제공고시(`GroupBuyProductNotices` — 양식마다 품목·항목 표, 제공 불가는 「제공 불가 — 사유」, `KC_CERT` 딱지, 아래 「판매자 추가 정보」, 양식이 둘 이상이면 접기·옵션별 칩, 출처 `noticeNo`, 모바일은 `AppSheet`), 하단 사업자 정보(`CommerceBusinessInfo`), 리뷰(머리 = 평균·수·「리뷰 제공 기준 ⓘ」, 별점 분포 없음)·Q&A.
  - `/group-buys/{id}/order` — 고른 목록 확인만(「상품 다시 고르기」 → 상세, 없거나 깨진 목록은 안내 후 상세), 배송지(다음 우편번호 `postcode.v2.js` 를 `AppSheet` 안에 `embed()` — `usePostcodeSearch`, 실패하면 직접 입력), 동의 3종, 판매조건·배송비·반품비·발송 예정일·청약철회 제한·정상가 기준·선불카드 안내·에스크로 고지(결정 59), 고시 시트(고른 단품 양식 먼저, 「주문하는 상품」 딱지), 본인인증 게이트 → 주문(`expectedAmount` + 멱등 키) → 409 `GROUP_BUY_AMOUNT_CHANGED` 면 결제창 대신 「금액이 바뀌었어요」 → 결제창 → 선점 남은 시간·결제 다시 열기. 결제창은 공용 `usePaymentCheckout`(구매자 정보는 반응형 Proxy 가 아닌 평범한 객체로 넘긴다 — 포트원 `DataCloneError`), 공구용 `useGroupBuyCheckout`(주문 결제·다시 열기·반품비 부족분, 리디렉션 `/me/orders/{orderNo}`).
  - `/me/orders`·`/me/orders/{orderNo}` — 주문 줄(단품 이름·「추가 구성」 딱지)·결제·배송, 가린 배송지 → 전체 보기 → 수정, 본인 즉시 취소·취소 요청·반품 요청(줄·수량 — 줄을 고르면 수량 1로 채움, 줄마다 사유, 하자·오배송 사진 3장, 환불/교환 — 고른 줄이 모두 업체 부담일 때만 교환, 「하자를 안 날」), 본 상품을 모두 고르면 「추가 구성상품도 함께 고르기」, 회수 방식별 환불 미리 보기 나란히·마이너스면 「직접 발송으로 바꾸기」/「부족분 결제하고 접수」(결정 68), 요청 철회·상태·거절 사유, 환불 진행 표시(결정 16), 발송 지연 「바로 전액 취소」(52), 공급 불가 「나머지만 받기 / 전체 취소」 + 기한(53), 배송완료 줄 「리뷰 쓰기」(기한)·「리뷰 보기」.
  - `/me/reviews`(상단 메뉴 서랍 링크) · 사이트 푸터 `AppFooter.vue`(결정 56) · 홈 `GROUP_BUY` 가젯.
- **관리자(admin-web)** — 보통 모드 묶음 「공구」(포인트 상점과 결제 사이, 아이콘 `pi pi-truck`), `AdminScreenRegistry` 와 화면 카탈로그가 같은 키·주소·권한·아이콘·차례.
  - 상품 `/group-buy-products`(카테고리·검색) · `/group-buy-products/{id}`(이름·카테고리(아래 단 필수)·요약·사양·설명·이미지·정상가·근거·청약철회 제한, 「단품(옵션)」 `GroupBuyProductSkusEditor` — 옵션 종류 칩·단품 표(값 열·이름(비우면 「블랙 / M」)·기본 공급가·판매)·줄 더하기·복사·차례·빼기·상품/단품 ID 읽기 전용(새 줄 「저장하면 자동」), 고시 양식 `GroupBuyNoticeEditor`(앵커 `gb-notice` — 품목 고르기·항목 칸·`conditional` 안내·일반원칙 원문 접어 보기·날짜 대체 표기/KC 는 저장 안 하는 입력 도우미·「상세페이지 참조」 경고·판매자 추가 항목·빈 항목 표시), 쓰는 공구 목록·구성 잠금 표시, 「이 상품으로 공구 만들기」 → `/group-buys/new?productId=`) · 옵션 종류 `/group-buy-option-types` · 카테고리 `/group-buy-categories`(2단·차례·숨김·통째 저장).
  - 공구 `/group-buys`·`/group-buys/new`·`/group-buys/{id}` — ⓪ 상품 고르기(상품 칸 읽기 전용 + 「상품에서 고치기」), 단품별 설정 표(추가금·한도·판매·공급가 — 공급가 칸은 권한자만, 판매가 < 공급가면 「역마진」 + 저장·모집 시작 전 확인 창), 추가 구성상품(다른 상품의 단품 고르기), DRAFT 전체 저장(`PUT`)·모집 뒤 잠김 표시, 「최저가」·「한정」·「개봉하면 반품 불가」·「재판매」 문구 경고, 모집 시작(409 → 빈 항목 표시·첫 문제로 이동)·조기 마감·발주·추가 차수(복사 범위·경고·막힘 이유)·발송 지연 공지·단품 공급 불가(서버 미리 보기)·중단(서버 미리 보기)·수정 이력 서랍.
  - 수량 `/group-buy-stock?id=` — 공구·단품별 최대·처음·추가 입고·확정·선점·남음·반품·다시 판매·하한(MAIN 만 공구 합계, 추가상품 따로 줄), 선점 목록(남은 초), 이동 내역, 추가 입고·줄이기(단품·사유).
  - 주문 `/group-buy-orders` — 주문 번호·닉네임·회원 번호로 찾기(배송지는 암호문이라 못 찾는다는 안내), 상태 탭 건수, 준비중·발송(표 안 택배사·송장, 200건)·배송완료 일괄 + 줄 결과, 발주·배송 명단 내보내기(사유), 상세 서랍(배송지 열람 사유 → 10분 뒤 가림, 열람 기록, 환불 기록, 메모, 이력, 송장 정정, 운영 환불 — 발송 전 줄·수량 / 배송완료 금액만 / SHIPPED 막힘, 사업자 귀책, 고액 문턱 안내·승인 대기).
  - 요청 `/group-buy-requests` — 경과 영업일 경고(2)·심각(3), 줄마다 사유·수량·사진, `preview_amount` ↔ 다시 계산한 값 비교, 승인·거절(사유 코드 + 근거 사진, 제한 문구 없는 공구의 `USED_VALUE_LOSS` 경고)·회수 송장·회수 확인·완료(줄마다 처리 방법별 수량, `RESELL` 은 RECRUITING + 단순 변심 줄만)·보상액·교환 재발송·환불로 바꾸기·반품비 추가 결제 상태·「하자를 안 날」.
  - Q&A `/group-buy-qna` · 리뷰 `/group-buy-reviews`(공개·신고 있음·숨김·전체 탭, 별점·사진·답글 없음 필터, 상세 서랍 — 답글·작성자 수정 이력·신고·숨김).
  - 대시보드 `/` 커머스 일곱 칸: 준비 대기(CONFIRMED)·열린 요청·결제 이상·곧 만료될 선점(2분 안)·남은 수량 적은 공구(≤ 5 또는 ≤ 10%)·발송 뒤 7일 지난 SHIPPED·답변 안 한 Q&A. 칸마다 볼 권한(주문 `commerce.order.read`·수량 `commerce.product.read`·결제 `commerce.payment.read`)이 없으면 null.
  - `/settings/commerce` 구획 다섯(구획마다 `SaveBar`): 판매 스위치·체크리스트 / 사업자 정보(결정 18, 등록번호 체크섬) / 배송비 정책(기간별 줄·시작된 줄 잠금·새 줄·왕복 경고) / 택배사(시드 6·템플릿 검사·이력 있는 코드 삭제 불가) / 공구 설정(결정 10).
  - 결제 이상 「수동 처리」에 방법 고르기(결정 74).

## API (JSON 카멜, 공통 래퍼, ID 는 글자 — 스노플레이크, 쪽 번호 0부터)
오류 코드의 `GROUP_BUY_` 접두는 줄여 적은 곳이 있다. 회원 응답에는 공급가가 없다.
| 쪽 | 주소 | 요청 → 응답·오류 |
|---|---|---|
| 회원(공개) | `GET /api/group-buys?phase&category&page` · `GET /api/group-buys/categories` · `GET /api/group-buys/{id}` | 목록 줄(`stockState` AVAILABLE·TEMP_SOLD_OUT·SOLD_OUT, `remainingQty`, `closingSoon`, `roundNo`, `closeReason`, `categoryId`, `reviews`) / `[{id, name, children[{id, name}]}]` / 상세(+ `images`·`descriptionHtml`·`normalPriceBasis`·`shipPlannedAt`·`shipWithinBusinessDays`·`shipping{fee, freeShippingOver, returnFee, returnFeeMode}`·`withdrawalRestriction`·`availability{remaining, soldOut, temporarilyUnavailable, addedQty, axes[{axisId, name, values[{valueId, value}]}], skus[{skuId, kind, name, valueIds, unitPrice, remaining(null = 제한 없음), soldOut, temporarilyUnavailable, onSale}]}`·`notices[{categoryCode, categoryNo, categoryName, title, skuNames, rows[{itemKey, no, group, name, description, kind, value, unavailable, unavailableReason}], customItems, catalogVersion, noticeNo}]`·`delayNotice`·`purchase{reason}`·`actions`·`watches`·`skuWatches{watchable, watching}`·`myOrderNo`) · 404 `GROUP_BUY_NOT_FOUND` |
| 회원 | `POST /api/group-buys/{id}/quote`(공개) · `POST …/orders`(멱등 키, `MoneyRequestGuard` `GROUPBUY_JOIN`) | `{items[{skuId, qty}]}` → `items[{skuId, kind, name, qty, unitPrice, itemAmount}]`·`itemsAmount`·`shippingFee`·`totalAmount` / `{items, address, agreements, expectedAmount}` → `{orderNo, amount, reservedUntil, payment{orderType, orderRefId, expiresAt, checkout, customer{fullName, phoneNumber, email}}}` · 400 `items`(MAIN 없음 `groupbuy.order.main.required`·같은 단품 두 줄·수량 0) · 409 `AMOUNT_CHANGED`(현재 금액)·`NOT_RECRUITING`·`SOLD_OUT`·`SKU_NOT_ON_SALE`·`SKU_SOLD_OUT{skuId, temporary}`·`LIMIT_EXCEEDED`·`ALREADY_JOINED` · 403 `SALES_CLOSED` |
| 회원 | `PUT`·`DELETE /api/group-buys/{id}/watches` · `GET /api/commerce/info`(공개) | `{kind, skuId}` → 204(서버가 허락한 때만) / `{salesOpen, ready, business{결정 18 칸}}` |
| 회원 | `GET /api/me/orders` · `GET /api/me/orders/{orderNo}` | 목록(`attention` PAY·SUPPLY_CHOICE·DELAY·RETURN_FEE) / 상세(줄 `skuId`·`skuName`·`kind`·`canceledQty`·`returnedQty`·`selectableQty`, 가린 배송지, `shipment`, `delayNotice`, `supplyChoice`, `requests[]`, `refunds[]`(progress IN_PROGRESS·COMPLETED·DELAYED·MANUAL, method), `returnUntil`·`defectReturnUntil`, `actions`, 줄별 리뷰 상태) · 남의 주문 404 |
| 회원 | `POST …/{orderNo}/payment` · `GET`·`PUT …/address` | 결제 다시 열기 `{afterFailure}`(409 `HOLD_EXPIRED`) / 전체 배송지·수정(409 `ADDRESS_LOCKED`·`ADDRESS_ERASED`) |
| 회원 | `POST …/cancel/preview`·`…/cancel` · `…/requests/preview`·`…/requests` · `…/requests/{id}/payment`·`…/withdraw` · `…/delay-cancel` · `…/supply-choice` · `…/photos` | 줄 `lines[{skuId, qty}]` → 환불 나눔 / 요청 미리 보기 `byMethod{COMPANY_PICKUP, CUSTOMER_SEND}` 각각 `refundAmount`(음수 가능)·나눔 + `feeBearer`·`returnAddress` / 요청(+ `defectKnownOn`) → `{request, returnFeePayment}` · 409 `REQUEST_OPEN`·`RETURN_WINDOW_CLOSED`·`AMOUNT_CHANGED`·`ADDON_ONLY{skuIds}` · 취소·지연 취소·요청은 `MoneyRequestGuard` `REFUND_REQUEST` |
| 회원 | Q&A·리뷰·신고 | 경로·모양은 user-web `useGroupBuyQnaApi`·`useGroupBuyReviewApi` 주석. 신고는 공용 `POST /api/reports`(대상 `GROUP_BUY_QUESTION`·`GROUP_BUY_REVIEW`) |
| 관리자 | `/api/admin/group-buy-products`(목록 `?q&categoryId` · `addon-sources?except=` · 저장·상세) · `GET·PUT /api/admin/group-buys/categories`(통째, 새 줄 `tempKey`·`parent`) · `GET·PUT …/group-buys/option-types` · `POST …/group-buys/images` · `GET …/group-buys/notice-catalog` | `ProductDraft{categoryId, name, summary, spec, description, normalPrice, normalPriceBasis, normalPriceSource, withdrawalRestriction, optionTypeCodes[], skus[{skuId\|null, name, values[], defaultSupplyPrice, onSale}], images[]}` → 상세(+ `optionNames`·`skus[{…, valueIds}]`·`axes` 읽기 전용) · 옵션 종류 `[{code, name, sortOrder, hidden, productCount, usedValues[]}]` · 고시 저장 `notices[{categoryCode, title, skuNames, values[{itemKey, value, unavailable, unavailableReason} \| {custom: true, itemKey: null, name, value, sortOrder}]}]`, 응답 `notices[{…, skuIds, customItems}]`, 카탈로그는 JSON 파일 모양 그대로 · 400 `skus`·`optionTypeCodes`·`fields.notices[i].…` · 409 `PRODUCT_SKU_IN_USE{skuNames}`·`OPTION_TYPE_IN_USE{codes}`·`LOCKED_FIELD`·`NOTICE_INCOMPLETE` · 404 `PRODUCT_NOT_FOUND` |
| 관리자 | `GET /api/admin/group-buys`(+`counts`) · `form-options` · `POST`·`PUT /{id}`(DRAFT) · `PATCH /{id}/content` · `GET /{id}/revisions` | `GroupBuyDraft{productId, title, groupPrice, shippingPolicyCode, minQty, maxQty, maxQtyPerUser, startAt, endAt, extensionMode, fulfillment, shipPlannedAt, shipWithinBusinessDays, returnPolicy, roundLimitShared, skus[{productSkuId, extraPrice, maxQty, onSale, supplyPrice}], addons[{productSkuId, price, maxQty, supplyPrice}], marginConfirmed}` · 모집 뒤 `PATCH …/content {supplyPrices, marginConfirmed, version}` · 409 `LOCKED_FIELD`·`VERSION_CONFLICT`·`NOT_DRAFT`·`SUPPLY_PRICE_MISSING{skuIds}`·`NEGATIVE_MARGIN{skuIds}` |
| 관리자 | `POST /{id}/start {marginConfirmed}` · `/close-early` · `/ordered` · `/rounds` · `GET /{id}/cancel-preview` + `POST /{id}/cancel` · `POST /{id}/delay-notices` · `GET /{id}/supply-failure-preview` + `POST /{id}/supply-failures` | 409 `ROUND_EXISTS`·`ROUND_SOURCE_INVALID`·`SHIPPING_POLICY_MISSING`·`NOTICE_INCOMPLETE`·`SUPPLY_PRICE_MISSING`·`NEGATIVE_MARGIN`·`NOT_CANCELABLE`·`NOT_ORDERABLE` · 중단 미리 보기 `refundOrders·refundAmount·expireOrders·shippedOrders` |
| 관리자 | `GET /{id}/stock` · `/stock/movements` · `POST /{id}/stock/adjust` | `{skuId?(null = 공구 최대만), direction ADD·REDUCE, qty, reason}` → `StockChange{…, skuMaxQty}` · 409 `QTY_BELOW_FLOOR{floor}`·`SKU_UNLIMITED` |
| 관리자 | `GET /api/admin/group-buy-orders`(`alert` SHIP_OVERDUE·DELIVERY_STALE) · `/{id}` · `/carriers` · `POST prepare`·`ship`(≤ 200 줄)·`deliver` · `/{id}/address-reveal`·`/memo`·`/tracking` · `/refund-preview` + `/refunds` · `/refunds/pending/{pendingId}/approve\|reject` · `POST export` | 일괄 `{succeeded, failed, results[{orderId, ok, code, message}]}` · 운영 환불 `{state QUEUED·AWAITING_APPROVAL}` · 409 `REFUND_SHIPPED`·`PAYMENT_UNRECONCILED`·`ORDER_NOT_PREPARABLE`·`ORDER_NOT_SHIPPABLE`·`TRACKING_INVALID`·`EXPORT_NOT_READY` · 내보내기 `{fileName, rowCount, downloadUrl}`(한 번 쓰는 `data:` 주소) |
| 관리자 | `GET /api/admin/group-buy-requests`(`elapsedBusinessDays`·`deadlineLevel`) · `/{id}` · `POST approve`·`reject`·`pickup`·`receive`·`complete`(`dispositions[]`·`compensationAmount`·`expectedAmount`)·`exchange-ship`·`convert-to-refund` · `photos` | 409 `REFUND_AMOUNT_CHANGED`·`NOT_RESELLABLE`·`REJECT_REASON_NOT_ALLOWED`·`REQUEST_NOT_OPEN`·`PAYMENT_UNRECONCILED` · 403 `REFUND_LARGE_REQUIRED` |
| 관리자 | `GET /api/admin/commerce/dashboard` · `shipping-policies` · `carriers` · `business-info` · `sales`(+ `checklist/{key}`) · `group-buy-settings` | 대시보드 일곱 칸 · 409 `COMMERCE_SHIPPING_POLICY_OVERLAP`·`COMMERCE_SHIPPING_POLICY_STARTED`·`COMMERCE_CARRIER_IN_USE`·`COMMERCE_SALES_NOT_READY{missing}`·`COMMERCE_SALES_ROOT_ONLY` · 400 `values.businessNo` · 설정 범위는 서버 `ranges` |
| 관리자 | Q&A·리뷰 | admin-web `useGroupBuyQnaAdminApi` 주석 |

## 마이그레이션 계획 (모두 shared 스트림)
| 번호 | 내용 |
|---|---|
| `V101__group_buy_core` | `group_buy_settings`(1행) · `shipping_policies`(기간 배제 `btree_gist` + 적용 시작 줄 불변 트리거) · `group_buy_categories` · `group_buy_products` · `group_buy_product_images` · `group_buy_option_types` · `group_buy_option_axes` · `group_buy_option_values` · `group_buy_product_skus` · `group_buys` · `group_buy_skus` · `group_buy_revisions` · `group_buy_notices` · `group_buy_notice_skus` · `group_buy_notice_values` · `group_buy_status_history` · `group_buy_participations`(주문) · `group_buy_participation_items`(주문 줄) · `participation_status_history` · `group_buy_qty_movements` · `group_buy_refunds` · `group_buy_watches` · `group_buy_delay_notices` · `group_buy_order_counters` |
| `V102__group_buy_fulfillment` | `commerce_carriers`(시드 6) · `group_buy_shipments` · `group_buy_escrow_jobs` · `group_buy_requests` · `group_buy_request_items` · `group_buy_return_dispositions` · `group_buy_request_photos`(MEMBER·OPERATOR) · `commerce_address_views`(REVEAL·EXPORT, 사유 5~200) · `commerce_bank_transfers` · `group_buy_supply_failures` · `group_buy_supply_choices` · `group_buy_admin_refunds` · 열 `group_buy_refunds.fee_covered`·`fee_refund`·`operator_amount` · 주문 줄 `exchanged_qty` · 결제 `payment_orders.order_type` 에 `GROUP_BUY_RETURN_FEE` · `payment_cancels.manual_method` |
| `V103__group_buy_defect_known_on` | `group_buy_requests.defect_known_on`(업체 부담 반품만 — CHECK) |
| `V104__commerce_sales_switch` | `commerce_sales_settings`(1행, 설치 직후 꺼짐) · `commerce_sales_checklist`(체크 여섯 — 누가·언제) · `commerce_business_info`(1행 — 결정 18 열두 칸, 등록번호 모양 CHECK) · 회원 알림 종류 `GROUP_BUY` · `group_buy_requests.deadline_alert_level` · 회원 권한 `commerce.purchase` 를 `pointshop.use` 가진 역할에 같이 줌 · `ux_group_buy_skus_combination` 을 값이 있는 MAIN 단품만으로 |
| `V106__group_buy_qna_review` | Q&A `group_buy_questions`·`group_buy_answers`·`group_buy_answer_revisions` · 리뷰 `group_buy_reviews`(`item_id` 유일 — 지워도 줄은 남음)·`group_buy_review_photos`·`group_buy_review_revisions`·`group_buy_review_replies` · 신고 `group_buy_ugc_reports`. (V105 는 테마·레이아웃) |
| `R__batch_32_groupbuy_privacy_cleanup` | 배치 `groupbuy-privacy-cleanup`, 매일 04:35 — 결정 34 의 배송지·수정 이력·요청 + ④ 문의 보관 만료 파기 |
- **열 요점**(「최종 검토로 더하는 열·표」 포함): `group_buys` — `product_id`·판매 조건·`version`(설명·이미지 낙관 잠금 — 수량 열은 조건부 UPDATE 로 바꾸고 `version` 을 올리지 않는다)·`published_at`·`closing_at`·`close_reason`·`round_root_id`·`round_no`(유일 `(round_root_id, round_no)`)·`round_limit_shared`·`added_qty`·`initial_max_qty`·`ship_planned_at`·`ship_within_business_days`, CHECK `min_qty ≤ max_qty`·발송 시기. 주문 — `order_no`·배송지 암호문·`amount`(≥ 1)·`point_amount`·`cash_amount`·`refunded_amount`·`compensation_amount`·`reshipping_fee_charged`·`shipped_shipping_fee`·`admin_memo`·결제 때 `revision_no`, CHECK `refunded_amount + compensation_amount ≤ cash_amount`. 주문 줄 — `sku_id NOT NULL`·`qty`·`unit_price`·`item_amount`·`canceled_qty`·`returned_qty`·`restocked_qty`·`exchanged_qty`, CHECK `0 ≤ restocked_qty ≤ returned_qty`·`canceled_qty + returned_qty ≤ qty`, 유일 (주문, 단품)·복합 FK(같은 공구 단품). 수량 이동 — `participation_item_id`·`sku_id`·`sku_*_after`·공구 `*_after`(추가상품만 움직이면 NULL — 둘 중 하나는 CHECK), 종류 RESERVE·RELEASE·CONFIRM·CANCEL·RETURN·RESTOCK·ADD·REDUCE·EXCHANGE_OUT. 관심 신청 — `kind`(RESTOCK·CLOSING_SOON·NEXT_ROUND — 1차 공구 기준)·`sku_id`(NULLS NOT DISTINCT 유일)·`last_notified_at`. 수정 이력 — `product_name`·`option_axes`·`skus`·`notices` jsonb 사본, 종류 `CONTENT`·`NOTICE`. 요청 — `preview_amount`·`resolution`(REFUND·EXCHANGE)·`reject_reason_code`·`received_at`(3영업일 기한 시작)·반품비 부족분 결제 칸. 요청 줄 — 사유·`refund_amount`·`prior_refund_offset`. 발송 — `kind`(ORIGINAL 주문당 하나·EXCHANGE 요청당 하나)·`escrow_applicable`·`escrow_logistics_at`·`escrow_completed_at`.
- 요청 사진은 요청 ID 없이 먼저 올리고 붙인다 — 붙지 않은 사진은 24시간 뒤 정리.

## 패키지·워커 (`groupbuy → payment`, `groupbuy → point` 단방향 — `payment` 는 공구를 모른다)
- shared `com.community.groupbuy`: `entity`·`dto`(`GroupBuyEvents` 등)·`exception`(`GroupBuyException`·`GroupBuyBlockedException`)·`repository.master`(수량·상태 전이는 JDBC 조건부 UPDATE·`SKIP LOCKED` — `GroupBuyStore`·`GroupBuyQtyStore`·`ParticipationStore`·`GroupBuyRefundStore`·`ShippingPolicyStore`·`GroupBuyProductStore`·`GroupBuyNoticeStore`·`FulfillmentStore`·`GroupBuyRequestStore`·`BankTransferStore`)·`service`(`GroupBuyAdminService`·`GroupBuyProductService`·`GroupBuyNoticeService`·`ShippingPolicyService`·`GroupBuyQuoteService`·`GroupBuyJoinService`(자격 → 선점 → 금액 → 배송지 암호화 → 주문 PENDING → `PaymentOrderService.create`)·**`GroupBuyQtyService`**(수량을 바꾸는 유일한 길)·`HoldExpiryService`·`ParticipationCancelService`·`GroupBuyRefundService`·**`GroupBuyRefundCalculator`**(계산 한 곳)·`FulfillmentService`·`GroupBuyRequestService`·`GroupBuyAdminRefundService`·`GroupBuySupplyService`·`BankTransferService`·`AddressRevealService`·`GroupBuyBusinessDays`·`GroupBuyShipDeadlines`·`GroupBuySettingsService`·`GroupBuyWatchService`·`GroupBuyNotifications`·`GroupBuyInvariantCheck`·`GroupBuyEligibility`·`GroupBuyPricing`·`GroupBuySavepoint`·`GroupBuySalesSwitch`)·`handler`(`GroupBuyPaymentHandler`(`GROUP_BUY`)·`GroupBuyReturnFeeHandler`(`GROUP_BUY_RETURN_FEE`)·`GroupBuyManualRefunds`)·`feedback`(Q&A·리뷰·신고). shared `com.community.commerce.sales`(판매 스위치·사업자 정보)·`com.community.commerce.image.GroupBuyImageUploader`.
- api `com.community.groupbuy`: `GroupBuyController`·`MyGroupBuyOrderController`·`CommerceInfoController`·`GroupBuyFeedbackController`. admin `com.community.groupbuy`: 공구·상품·주문·요청·커머스 설정 컨트롤러·`GroupBuyFeedbackAdminController`, `config.GroupBuyLocalSeeder`.
- 워커(Java, `worker_heartbeats`, admin `batch.service.GroupBuyStateScheduler` — `roboyam.batch.enabled` 서버만): `groupbuy-state`(1분 — 선점 만료·모집 시작·마감·연장·판정·COMPLETED sweep·다 팔림 안전망, `roboyam.groupbuy.state-poll-millis`) · `groupbuy-refund`(1분 — 결정 61, `refund-poll-millis`) · `groupbuy-fulfillment`(에스크로 재시도·구매 확정·미결제 요청 닫기·공급 불가 기한, `fulfillment-poll-millis`) · `groupbuy-deadline`(1시간 — P35·결정 51·마감 임박, `deadline-poll-millis`).

## 결정
번호는 코드 주석·테스트 이름이 가리킨다. 나중에 바뀐 결정은 최종 값만 적고 「(고침)」.

### 세션 정리 결정 (1~26)
1. **회원 화면 경로**: `MenuBuiltin` 그대로 `/group-buys`·`/group-buys/{id}`·`/group-buys/{id}/order`·`/me/orders`·`/me/orders/{orderNo}`. 결제창은 `/dev/payment` 의 SDK·창 닫기 판정(`UtilPortone`)을 공용 컴포저블로 옮겨 쓴다.
2. **설명·이미지**: 설명은 `@roboyam/rich-editor` + 서버 정화 `GroupBuyDescriptionHtml`(스크립트·이벤트·iframe·style 제거, 이미지·링크 새 탭, 남이 올리지 않은 이미지 키 거절) + 평문 `summary`·`spec`(≤ 5,000), 수정 이력은 정화된 HTML 사본. 대표 이미지 ≤ 8장 · JPEG·PNG·WebP 정지 · 5MB · 다시 인코딩·EXIF 제거 · 긴 변 1600px·썸네일 400px · 순서 바꾸기, 키 `groupbuy/<용도>/<년월>/<번호>.jpg`(기존 미디어 저장소). 미디어는 쓰는 곳이 하나도 없을 때만 지운다. 설명·이미지는 상품 것(89).
3. **관리자 메뉴**: 보통 모드 묶음 「공구」(포인트 상점과 결제 사이) — 상품·옵션 종류·카테고리·공구·수량·주문·요청·Q&A·리뷰. 배송비 정책·택배사·사업자 정보·판매 스위치·공구 설정은 `/settings/commerce` 구획. (고침)
4. **에스크로**: `PaymentGateway.registerEscrowLogistics`·`completeEscrow` — 발송 처리 때 에스크로 결제 건이면 배송 정보 등록(가짜 PG 는 기록만). 주문서에는 「결제대금예치 선택 가능」 고지만(체크 칸 없음). 실제 적용 범위는 dev 확인(「남은 일」).
5. **현금영수증**: 카드·간편결제만 받으므로 주문서 입력은 두지 않고 표 열만(SDK 현금영수증 인자는 가상계좌·계좌이체용).
6. **계좌 송금 표** `commerce_bank_transfers`(V102 — 계좌 암호문 `ROBOYAM_DATA_KEY`, 「송금 완료」 후 계좌 삭제): 보상이 남은 결제액을 넘는 부분(9-3)·PG 로 못 돌려주는 환불(67). 3단계 충전 환불도 이어 쓴다.
7. **배송완료**: 운영자가 처리(일괄 가능). 자동 배송완료 없음(청약철회 7일의 시작점이라 추측하지 않는다). 누락은 대시보드 「발송 뒤 7일 지난 SHIPPED」.
8. **영업일**: 주말 + 관리자 휴일 목록(`group_buy_settings`, 설정 화면 편집), 공휴일 API 없음. 2·3영업일 기한(P35)과 환불 기한 표시가 같이 쓴다.
9. **공구 COMPLETED**: 선착순 즉시 발송은 마감됐고 수량에 든 주문이 모두 DELIVERED·CANCELED·REFUNDED 이면. 마감 후 발주는 `ORDERED → SHIPPING`(첫 발송 처리) `→ COMPLETED`(같은 조건). 판단은 배송완료 트랜잭션이 아니라 1분 워커의 조건부 sweep(`… WHERE NOT EXISTS 진행 중 주문`). 반품 요청은 COMPLETED 뒤에도 받는다(주문·요청 기준).
10. **공구 설정**(`GroupBuySettingsService` — 범위·처음 값의 정본, 바뀐 칸만 전→후 감사 `GROUPBUY_SETTINGS_UPDATE`): 선점 5분(3~30) · 성사형 기간 상한 14일 · 선착순 30일 · 연장 최대 30분 · 연장 판단 창 5분 · 판정 여유 2분 · 고객 택배비 보상 상한(`compensation_cap`) 10,000원 · 고액 환불 문턱 100,000원(0 = 끔) · 배송지 열람 유지 10분 · 휴일 목록 · 만료 주문 배송지 보관 30일 · 즉시 발송 기본 3영업일(51) · 공급 불가 선택 기한 3영업일(53) · Q&A 답변 기한 2영업일(60).
11. **택배사·local 시드**: 택배사는 마이그레이션 시드(전 환경) 6개 `CJ`·`HANJIN`·`LOTTE`·`EPOST`·`LOGEN`·`KDEXP` + 조회 주소 템플릿(`https://`, `{no}` 한 번 — CHECK). 목록은 통째 저장, 발송 이력 있는 코드는 지우지 못하고 끄기만(409 `COMMERCE_CARRIER_IN_USE`), 송장번호는 숫자·영문 6~30자로 정리. local 시드(`GroupBuyLocalSeeder` — local + 배치 켠 서버): 사업자 정보·체크 여섯을 채우고 **켜는 조건 검사를 그대로 통과해야** 판매를 켬, 배송비 정책 `STANDARD`(3,000 · 무료 50,000 · 반품 3,000 · 편도), 예시 품목 여덟(햅쌀·사과/감귤·고등어·김치·소스·티셔츠·러닝화·로봇 키트 — 고시 양식 완성, 신선식품은 `DATE_LIKE` 대체 표기 예시), 공구 여덟(성사형 모집 중 셋 · 선착순 즉시 발송 둘 · 마감 임박 · 조기 마감으로 끝난 1차 · 공개된 예정). (고침)
12. **운영자 알림**(받는 사람 `commerce.order.manage`·`commerce.refund.approve` 보유자): `COMMERCE_ORDER_ANOMALY`(불변식이 깨진 결제 거절 — 주문마다 묶음, 도메인 거절은 회원 안내만) · `COMMERCE_REFUND_REQUESTED`(요청 접수 — WARNING, 2영업일 경고·3영업일 CRITICAL) · `COMMERCE_SHIP_OVERDUE`(51) · `COMMERCE_GROUPBUY_CANCELED`(중단) · `COMMERCE_SALES_TOGGLE` · `COMMERCE_QNA_RECEIVED`·`COMMERCE_QNA_REPORTED`·`COMMERCE_REVIEW_REPORTED`(INFO, 묶기). (고침 — 옛 `COMMERCE_GROUPBUY_QUESTION` 대신 Q&A·리뷰 셋)
13. **주문 번호**: `G` + KST `yyyyMMdd` + 그날 일련 5자리(예 `G2026102800012`, `group_buy_order_counters`, 하루 99,999건을 넘으면 자리를 늘린다). 회원 주소·결제창 주문명·메일·고객 응대에 쓴다. 포트원 `redirectUrl` 은 쿼리 없이 주문 상세로.
14. **에스크로 구매 확정**: 에스크로 결제 건은 배송완료 뒤 청약철회 기간(7일)이 열린 요청 없이 지나면 `escrow/complete`(실패는 재시도·결제 이상). 송장 정정·교환 재발송은 배송 정보를 다시 보낸다(이미 등록됐으면 `PATCH`).
15. **반품 상품 처리**(사용자 결정): 반품 완료 때 운영자가 요청 줄마다 처리 방법과 수량(필수, 합 = 그 줄 반품 수량 — 예 「2개 다시 판매 + 1개 폐기」).
    - `RESELL`(다시 판매): 공구 `RECRUITING` + 줄 사유 `CHANGE_OF_MIND` 일 때만(아니면 409 `GROUP_BUY_NOT_RESELLABLE`). 확정 수량 감소(이동 `RESTOCK`, 조건 `status='RECRUITING'`) → 남은 수량 증가·수량 풀림 알림. 실제로는 선착순 즉시 발송 공구에서만 고를 수 있어 화면은 다른 공구에서 숨긴다.
    - `DISCARD`·`RETURN_TO_SUPPLIER`·`KEEP`: 수량 그대로, 이동 `RETURN` 기록만. 성사 판정은 뒤집지 않는다(10-4).
16. **환불 진행 표시**: 주문은 같은 트랜잭션에서 REFUNDED·CANCELED 로 바뀌고 돈은 취소 워커가 보낸다. 화면은 `group_buy_refunds.payment_cancel_id` 로 「환불 진행 중 · 카드사 반영까지 3~7영업일」/「환불 완료(날짜)」/「환불 지연 — 확인 중」(GAVE_UP)/「운영자가 직접 처리함」(MANUAL). MANUAL 은 결제 모듈 `PaymentOrderHandler.onCancelResolvedManually`(기본 빈 메서드)를 「수동 처리 완료」가 같은 트랜잭션에서 부르고, 공구 처리기가 환불 기록 완료 + 「환불 처리됨(운영자 확인)」 알림·메일.
17. **모집 뒤 수량 조정·추가 입고**(사용자 결정, `commerce.stock.adjust`, commerce.md 10-2 를 고친다):
    - 품절은 둘 — **잠시 품절**(확정 + 선점 = 최대, 모집 중)과 **다 팔림**(확정 = 최대 → 그 자리에서 CLOSING, 27). 단품도 같다(84).
    - **늘리기**(ADD): `RECRUITING` 이고 다 팔리기 전(잠시 품절 포함)만 — 조건 `status='RECRUITING' AND current_qty < max_qty`. 단품 지정이면 단품·공구 최대를 한 트랜잭션(공구 행 → 단품 행). `added_qty` 누계·「추가 입고 N개」(80)·수정 이력·감사(돈)·수량 풀림 알림, 사유 필수.
    - **줄이기**(REDUCE): `RECRUITING`, 하한 = max(확정 + 선점, 성사형이면 `min_qty`) — 409 `GROUP_BUY_QTY_BELOW_FLOOR`, 사유 필수·감사. 줄여서 확정 = 최대면 같은 트랜잭션에서 CLOSING.
    - 다 팔린 뒤 추가 입고 = 추가 차수(31). 모집 뒤 가격·기간·옵션 구성·배송비는 못 바꾼다.
18. **사업자 정보·공개 API**: 칸 열둘 — 상호·대표자·사업자등록번호(체크섬)·통신판매업 신고번호·신고 기관·주소·전화·이메일(필수 여덟) + 운영시간·개인정보 관리책임자·호스팅 사업자·배송/환불 정책 문구(≤ 3,000). 평문 저장. 회원 공개 `GET /api/commerce/info`(값 + `ready`) — 사업자 정보는 판매 켜짐 + 필수 여덟 칸(체크섬 포함)일 때만. 판매 켜기 조건에 「공개된 공구 1개 이상」은 넣지 않는다.
19. **설정 읽기**: 돈·시각에 닿는 값(선점 시간·배송비·문턱·판매 스위치)은 그 트랜잭션 안에서 DB 의 지금 값(캐시 금지). 화면 표시용만 캐시.
20. **내보내기 안전**: 배송 명단 CSV 는 UTF-8 BOM·늘 큰따옴표, `= + - @`·탭·CR 로 시작하는 칸 앞에 `'`(수식 주입).
21. **공구 Q&A**(사용자 결정): 별도 단위 2b-6 — 「Q&A 규칙」.
22. **관리자 날짜 필터**: 한국 날짜, 양 끝 포함.
23. **한 주문에 여러 단품**(사용자 결정): 단품 여러 줄 × 수량을 한 주문으로. 결제·배송비·배송지·주문 번호는 주문 단위, 수량·선점·반품·다시 판매는 줄 단위. 선점은 모든 줄을 한 트랜잭션에서(하나라도 모자라면 전체 실패, 잠금은 공구 행 → 단품 ID 오름차순). 배송비·무료배송 기준은 주문 전체 상품 금액(추가상품 포함). 공구당 **진행 중 주문 1건**(PENDING_PAYMENT~SHIPPED) — 더 사려면 배송완료 뒤 새 주문 또는 취소 후 다시 주문.
24. **차수 사이 1인 한도**(사용자 결정): 공구마다 `round_limit_shared`(기본 꺼짐 = 차수마다 따로). 켜면 같은 `round_root_id` 의 모든 차수 수량을 합쳐 센다(86).
25. **연장 조건 넓힘**(사용자 결정, commerce.md 10-2 를 고친다): `DEMAND` 공구는 마감 전 5분 안에 선점 만료뿐 아니라 다시 판매(RESTOCK)·추가 입고(ADD)·모집 중 본인 취소(CANCEL — 81)로 수량이 풀려도 연장한다.
26. **Q&A 관리**(사용자 결정): 개인정보 자동 비밀글 + 작성자 안내 · 신고 대상 · 단순 문의도 3년 보관 · 답변 수정 이력 — 「Q&A 규칙」.

### 수량·상태 (27~35)
27. **다 팔림은 그 자리에서 마감**: 결제 확정(`onPaid` 의 CONFIRM)·줄이기(REDUCE)가 확정 = 최대를 만들면 같은 트랜잭션에서 `RECRUITING → CLOSING`(`closing_at`, 상태 이력, 종료 사유 `SOLD_OUT`). 1분 워커(10-7 ③)는 안전망. 추가 입고·다시 판매는 `status='RECRUITING' AND current_qty < max_qty` 조건부라 마감과 경쟁해도 하나만 이긴다.
28. **판매 수량·불변식·1인 한도**: CONFIRM 된 줄의 판매 수량 = `qty − canceled_qty − restocked_qty`. 발송 전 취소·불발·중단 = CANCEL + `canceled_qty`, 반품 = `returned_qty`(폐기·반송·보관이면 판매 수량 그대로 — 다시 팔 수 없다), 다시 판매 = RESTOCK + `restocked_qty`. **불변식**(commerce.md 18절을 고친다): 공구·단품 `current_qty` = Σ 판매 수량, `reserved_qty` = Σ PENDING_PAYMENT 줄 수량, 이동 줄 마지막 값 = 현재 값. **1인 한도** = 그 회원 Σ `qty − canceled_qty − returned_qty`(반품은 처리 방법과 관계없이 뺀다, 금액만 운영 환불은 그대로 센다) + 진행 중 선점(+ 24 합산). 진행 중 주문 = PENDING_PAYMENT·JOINED·CONFIRMED·PREPARING·SHIPPED. 만료 원인 `ExpireCause` 중 이탈 제한(`GROUP_BUY_BAN` — 24시간 3번)은 `HOLD_TIMEOUT` 만, `participation_status_history` 의 최근 24시간 EXPIRED 로 센다(`hold_expire_count` 열 없음).
29. **중단**: 공구 상태 전이 뒤 주문마다 새 트랜잭션(37·61) — ① PENDING_PAYMENT → EXPIRED(`GROUP_BUY_CANCELED`) + RELEASE + 결제 READY → EXPIRED(뒤늦은 결제는 늦은 결제로 자동 취소) ② 발송 전(JOINED·CONFIRMED·PREPARING) → REFUNDED + CANCEL + 전액 ③ 이미 발송(SHIPPED·DELIVERED)은 정상 흐름(반품은 요청으로). 확인 창 「환불 N명 · 총 N원 / 이미 발송 M명은 그대로」.
30. **배송지 수정·명단 기한은 주문 기준**(commerce.md 10-5·10-9 를 고친다): 수정 = 즉시 발송은 결제 중·CONFIRMED, 마감 후 발주는 결제 중·JOINED·CONFIRMED 이고 공구 RECRUITING·CLOSING·CLOSED_SUCCESS(아니면 409 `GROUP_BUY_ADDRESS_LOCKED`). 명단 내보내기는 발송 전 주문(CONFIRMED·PREPARING) 단위 — 마감 후 발주는 ORDERED 뒤, 즉시 발송은 언제든. (고침)
31. **추가 차수**: 원본은 모집이 끝난 공구(CLOSING 이후 — 다 팔림·기간 끝·조기 마감·완료, 중단 제외 → 409 `GROUP_BUY_ROUND_SOURCE_INVALID`). 같은 루트에 DRAFT·RECRUITING 차수가 있으면 409 `GROUP_BUY_ROUND_EXISTS`(동시 클릭은 유일 `(round_root_id, round_no)`). `round_root_id` = 1차 공구, `round_no` = 루트 최대 + 1. **같은 상품으로 새 DRAFT** — 판매 조건·공급가·방식(성사형/선착순·발송 시점·연장)·`round_limit_shared` 복사(고시는 상품 것 그대로, 상품에 새로 더한 단품은 기본값), 기간 처음 값 = 내일 같은 시각 시작·7일 모집·(마감 후 발주면) 마감 7일 뒤 발송 예정. 배송비는 모집 시작 때 정책. 복사 때 「이전 차수의 날짜·수량·배송비 문구가 설명에 남아 있는지」 경고. 모집을 시작하면 루트 모든 차수의 `NEXT_ROUND`·`RESTOCK`·`CLOSING_SOON` 신청자에게 한 번(같은 회원 한 번), 신청은 새 차수로 옮기지 않는다. (고침)
32. **운영 환불**(요청 없이, `commerce.refund.approve`, 사유 필수, 고액은 70): 발송 전 주문(JOINED·CONFIRMED·PREPARING)은 「발송 전 취소」와 같다(CANCEL 이동, 줄·수량 지정 — 비면 전체). 배송완료 주문은 **금액만 돌려주는 환불**(회수 없음, 1 ~ 남은 결제액, 줄 비례 나눔 — 71)로 수량 이동 없이 `refunded_amount` 만, 남은 결제액을 다 쓰면 REFUNDED. SHIPPED 는 409 `GROUP_BUY_REFUND_SHIPPED`. 사유 「사업자 귀책」이면 재부과·반품비 없음.
33. **연속 부분 반품의 배송비**: 주문 `reshipping_fee_charged` — 계산기는 「남은 수량 기준 배송비 − 이미 재부과한 것」의 차액만 뺀다. 마지막 남은 것을 반품하면 전체 반품 규칙(43).
34. **개인정보 파기**(`R__batch_32`): 배송지 = 배송완료·취소/환불 뒤 180일(commerce.md 10-9 「6개월」), 결제하지 못한 만료 주문 30일(설정값), 열린 요청이 있으면 미룸 · 수정 이력 = 다음 판이 생긴 지 180일 지난 판(마지막 판은 남김) · 요청 = 끝난 지 3년 지난 요청의 회원 사유 글(`memo`)·회원 사진 줄(운영자 거절 근거·금액은 청약철회 기록으로 남김) · 문의 = 「Q&A 규칙」 보관 만료. 지운 사진 줄의 파일은 저장소에 남는다(고아 파일 정리 몫).
35. **권한**(commerce.md 13-2 에 더한다, 서버 `Permission` 12개 `featureReady=true` + 화면 카탈로그): 조회 `commerce.product.read`(상품·옵션 종류·카테고리·공구·수량)·`commerce.order.read`(주문·요청·Q&A·리뷰) · 상품·공구 등록·추가 차수 `commerce.product.manage` · 공급가 고치기 `commerce.product.price`(보기는 `commerce.settlement.read` 도) · 추가 입고·줄이기 `commerce.stock.adjust` · 중단 `commerce.groupbuy.cancel` · 발주·발송·준비중·발송 지연 공지·반품 처리 방법(다시 판매 포함)·Q&A 답변·숨김·신고 처리·리뷰 답글·숨김 `commerce.order.manage` · 요청 승인·운영 환불 `commerce.refund.approve`(반품 완료·공급 불가는 `order.manage` + `refund.approve`) · 고액 `commerce.refund.large` · 배송지 열람 `commerce.address.reveal` · 명단 내보내기 `commerce.export` + `commerce.address.reveal` · 설정 `commerce.settings`(판매 켜기는 최고 관리자). 회원 주문 `commerce.purchase`. (고침)

### 결제 경계·주문 상태 (36~50)
36. **환불 기록 `group_buy_refunds`**(V101): 공구가 돈을 돌려주는 모든 일 — 요청 승인·본인 즉시 취소·불발·중단·운영 환불·공급 불가·지연 취소·반품비 부족분 돌려주기 — 을 한 줄씩: 주문, 원인(`REQUEST`·`USER_CANCEL`·`FAILED`·`CANCELED`·`ADMIN`·`SUPPLY_FAILURE`·`DELAY`·`RETURN_FEE_REFUND`), 요청 ID(FK 없이), 금액 나눔(75), `payment_cancel_id`, 요청 키 `GB_REFUND:{id}`, 상태(진행 중·완료·지연·수동), `method`(67). 줄별 금액은 `group_buy_request_items.refund_amount`(요청) 또는 이 표의 줄 내역 jsonb. `CancelKind`: 본인 취소 `USER_CANCEL` · 불발·중단·반품 `REFUND` · 운영 환불 `ADMIN`.
37. **잠금 차례 — 교착 막기**:
    - `onPaid`·`onCanceled` 안에서는 공구·단품 행을 `FOR UPDATE` 하지 않고 조건부 UPDATE 만(결제 행을 쥔 채 불린다).
    - 공구 행을 잠그는 일(중단·판정·추가 입고·줄이기·추가 차수)은 결제 행을 잠그는 일과 같은 트랜잭션에 넣지 않는다: ① 공구 상태 전이만 짧은 트랜잭션 ② 주문마다 새 트랜잭션 「주문 조건부 전이 → CANCEL → 환불 기록 → 취소 넣기」(다시 돌려도 한 번). commerce.md 10-7 「같은 트랜잭션」을 이렇게 고친다.
    - 결제가 PAID 인(취소를 넣는) 흐름: 주문 행 → (조건부) 공구·단품 수량 → 결제 행. 결제가 READY 인 주문을 닫는 흐름(선점 만료·결제 다시 열기·중단의 결제 중 주문·조회 정리·REJECT)은 **결제 행 먼저** — `UPDATE payment_orders SET status='EXPIRED' WHERE id=:p AND status='READY'`(0 행이어도 계속, `PaymentOrderService.expireOpen`) → 주문 조건부 전이 → 이긴 쪽만 RELEASE.
    - 어드바이저리 잠금 `pg_advisory_xact_lock`(키 `gb:{공구}:{회원}`)은 모든 행 잠금보다 먼저: 어드바이저리 → 공구 → 단품(ID 오름차순, 한 줄씩) → 결제.
    - 판정 ①은 공구 행을 `FOR UPDATE` 로 잠근 뒤 판단·전이 — 마지막 결제 확정과 겹쳐 성사 수량인데 불발이 되는 일을 막는다.
38. **환불 누계는 취소를 넣을 때 올린다**: `refunded_amount` 는 취소를 넣는 트랜잭션에서 조건부로 올린다(공구가 상한의 정본 — GAVE_UP·MANUAL 이어도 같은 금액을 두 번 못 넣는다). 남은 결제액 = `cash_amount − refunded_amount − compensation_amount`, CHECK `refunded_amount + compensation_amount ≤ cash_amount`, 조건부 UPDATE 도 둘을 합쳐 비교.
39. **보상액은 따로 센다**: PG 취소에 함께 담은 고객 택배비 보상은 `compensation_amount`, 계좌 송금분은 `commerce_bank_transfers`. 불변식 `payment_orders.paid_amount − canceled_amount = amount − refunded_amount − compensation_amount`(+ 진행 중 취소분, 계좌 송금으로 돌린 취소는 뺀다). 매출 집계는 상품 환불과 보상을 가른다.
40. **자동 취소의 `onCanceled`**: 늦은 결제·도메인 거절(`anomaly != null`)의 취소 완료는 주문 금액·상태를 건드리지 않고 회원 안내(commerce.md 8-2 5 문구)만.
41. **선점과 결제 유효 시각을 같게**: 결제 주문 `expires_at = reserved_until`(유효 시간 = `reserved_until − 지금`). 결제 다시 열기도 같은 끝 — 선점을 늘리지 않는다.
42. **발송 전 일부 줄 취소의 배송비**(사용자 결정, commerce.md 10-3 D11 을 고친다): 주문 전체 취소면 전액. 일부만 취소해 무료배송 기준 아래로 떨어지면 남은 상품 기준 배송비를 다시 계산해 환불액에서 뺀다(반품비 없음, `reshipping_fee_charged` 누계) — 발송 후 부분 반품과 같은 계산(`GroupBuyRefundCalculator` 한 길). 본인 즉시 취소도 줄·수량 단위.
43. **나눠서 모두 반품하면 처음 배송비 처리만 한 번에 전체 반품한 결과와 같게**(사용자 결정): 편도면 발송 뒤 반품으로 재부과한 배송비를 마지막 환불에 더해 돌려주고, 왕복이면 처음 배송비를 한 번만 받은 것으로 맞춘다. 반품비는 실제로 보낸 택배(요청)마다. 발송 전 일부 취소(42)의 재부과는 「처음 배송비」로 본다(발송 처리 때 `shipped_shipping_fee = shipping_fee + 그때까지의 재부과` 고정, 계산기는 이 값으로 「낸 주문 / 무료배송 주문」 판단). 그다음은 10-8 표: 단순 변심 전체 반품은 처음 배송비를 돌려주지 않고(낸 주문), 하자·오배송 전체 반품은 돌려준다(업체 부담). 처음부터 1개만 사고 배송비를 낸 회원과 같은 결과다. 처음 배송비까지 받으려면 그 공구의 배송비 정책을 왕복으로. G7: 25,000 × 3 무료배송(편도·업체회수·반품비 3,000) — 22,000 → 19,000(재부과 3,000 + 반품비) → 25,000(− 반품비 + 재부과 돌려줌), 합 66,000 = 75,000 − 3,000 × 3.
44. **반품 요청은 줄마다 사유**(사용자 결정): 한 요청(한 택배)에 줄마다 사유·수량. 반품 배송비·보상은 요청당 한 번 — 업체 부담 줄이 하나라도 있으면 반품 배송비 운영자 부담. 줄별 금액은 줄 사유로(업체 부담 줄은 재계산 없이 전액, 변심 줄은 10-8 표). 주문당 열린 요청 1개.
45. **집계 중(CLOSING) 본인 즉시 취소 허용**(사용자 결정): 성사형 JOINED 주문은 CLOSING 중에도 즉시 취소(청약철회). 판정은 판정 시점 수량 — 이 취소로 불발이 될 수 있다. 판정과 취소는 주문 조건부 전이로 하나만 이긴다.
46. **주문 상태는 줄 전체로**: CANCELED = Σ(`qty − canceled_qty`) = 0(발송 전 전부 취소), REFUNDED = Σ(`qty − canceled_qty − returned_qty`) = 0(받은 뒤 전부 반품 — 반품비가 남아 누계가 결제액에 못 닿아도) 또는 운영 환불이 남은 결제액을 모두 쓴 때. 불발·중단 환불은 REFUNDED, 본인 취소·지연 취소는 CANCELED. REFUNDED·CANCELED 주문에는 운영 환불을 더 넣지 못한다. 일부 줄만이면 상태는 그대로. 「진행 중 주문 1건」·COMPLETED(9)가 이 정의를 쓴다.
47. **발송은 주문 한 번에**: 줄마다 따로 발송하지 않는다(ORIGINAL 발송은 주문당 하나). 줄 하나라도 열린 요청이 있으면 주문 전체를 발송하지 못한다(10-3).
48. **단품 전부 다 팔림도 마감**: 판매 중인 MAIN 단품이 모두 한도가 있고 모두 확정 = 한도이면(한도 합 < 공구 최대여도) 27 처럼 그 자리에서 CLOSING(`SOLD_OUT`). 단품 줄이기 하한은 그 단품의 확정 + 선점. 추가상품 품절은 공구를 닫지 않는다.
49. **중단·종료 공구의 남은 일**: 공구 CANCELED 뒤에도 발송된 주문의 배송완료·반품·환불은 계속된다. 마감 후 발주 공구의 `SHIPPING → CANCELED`(일부만 발송한 뒤 남은 수량을 못 보낼 때)를 허용한다(commerce.md 10-2 전이에 더한다) — 발송 전 주문만 환불(29).
50. **판정 경쟁**: 마감 시각의 연장 판단과 마지막 1개 확정은 둘 다 `status='RECRUITING'` 조건부라 하나만 이긴다(확정이 이기면 SOLD_OUT 마감, 연장이 이기면 연장 중 판매).

### 법·실제 운영 (51~60)
51. **발송 예정일(공급시기 약정)** — 전자상거래법 제15조 ①(약정이 없으면 대금 지급일부터 3영업일 안 공급): 성사형·마감 후 발주는 `ship_planned_at` 필수, 선착순 즉시 발송은 `ship_within_business_days`(결제 후 N영업일, 기본 3). 모집 시작 뒤 잠금. 상세·주문서·계약 통지 메일·동의 「구매 조건」에 표시. 발송 기한(`GroupBuyShipDeadlines` — 대시보드·주문 거르기·워커 한 식): 마감 후 발주 = 예정일 다음 날 0시(KST), 즉시 발송 = 결제(확정)부터 N영업일 끝, 발송 지연 공지가 있으면 새 예정일 다음 날. 넘긴 발송 전 주문은 대시보드·운영자 알림 `COMMERCE_SHIP_OVERDUE`(하루 한 건 — 묶음 키 KST 날짜).
52. **발송 지연 공지**(사용자 결정 — 통지 + 취소권만): 운영자 「발송 지연 공지」(새 예정일·사유 필수) → `group_buy_delay_notices` + 공지 전에 결제한 발송 전 주문 전원에게 앱 + 메일(제15조 ② 지체 없는 통지, 발송된 주문 제외). 그 주문은 **승인 없이 즉시 전액 취소**(줄을 골라도 주문 전체, 원인 `DELAY`, 재부과 없음, 상태 CANCELED — 성사 뒤·준비중이어도). 보상은 두지 않는다. 수정 이력에 예정일 변경.
53. **단품 공급 불가**(사용자 결정 — 답이 없으면 나머지만 받기): 관리자 「공급 불가 처리」(단품 지정, 감사 `GROUPBUY_SUPPLY_FAILURE`) — ① 기록(`group_buy_supply_failures`) + 그 단품 판매 중지 ② 주문마다 새 트랜잭션: 그 단품의 발송 전 줄 전부 CANCEL + 환불(원인 `SUPPLY_FAILURE`, **배송비 재계산 없음** — 사업자 귀책), 본 상품이 모두 빠지면 주문 전체(배송비 포함) ③ 단품 최대 = max(확정 + 선점, 1), MAIN 이면 공구 최대도 같은 수만큼(줄이기 하한 예외, 이동 `REDUCE`, 최소 수량 CHECK 까지). 남은 줄이 있는 주문에는 앱 + 메일로 「나머지만 받기 / 전체 취소」(`group_buy_supply_choices` — 기한 = 설정 영업일의 마지막 날 끝 KST): 전체 취소면 남은 줄도 전액(처음 배송비 포함), 기한까지 답이 없으면 워커가 `KEEP_REST`(defaulted), 기한 전에는 그 주문 발송 거절. 결제 중 주문은 「2b-3 정한 세부」 17.
54. **교환은 하자·오배송·훼손만**(사용자 결정 — 소비자분쟁해결기준 「교환 또는 구입가 환급」): 업체 부담 반품 요청은 회원이 「환불」/「교환」(`resolution`). 교환은 회수 확인 뒤 같은 주문에 재발송(`group_buy_shipments.kind = EXCHANGE`, 환불 0, 판매 수량 그대로 — 이동 `EXCHANGE_OUT` 기록·`exchanged_qty`, 반품비·재발송비 운영자, 에스크로 배송 정보 다시). 공구가 끝났거나 물건이 없으면 운영자가 환불로 바꾼다(미리 보기 다시 계산, 회원 알림). 단순 변심 교환은 받지 않고 「환불 뒤 다시 주문」 안내.
55. **청약철회 제한 표시·반품 거절 근거** — 제17조 ②⑥⑤: 상품 `withdrawal_restriction`(제한 사유 안내, 선택)을 상세·주문서·계약 메일에 표시(⑥ 표시하지 않으면 제한할 수 없다). 반품 거절은 사유 코드 필수(`CUSTOMER_DAMAGE`·`USED_VALUE_LOSS`·`TIME_VALUE_LOSS`·`OUT_OF_WINDOW`·`ALREADY_SHIPPED`) + 운영자 근거 사진(⑤ 다툼은 사업자가 증명), 계약·청약철회 기록으로 5년. 등록 화면 경고: 「개봉하면 반품 불가」처럼 법보다 넓게 막는 문구, 「재판매 금지·리셀 의심 시 취소」 문구(공정위 2023 시정 사례). 주문 제작 공구(②6호 — 서면 동의)는 지원하지 않는다.
56. **사업자 정보는 사이트 전체에** — 제10조 ①: 판매 스위치가 켜져 있으면 user-web 공통 푸터(`AppFooter.vue` → `CommerceBusinessInfo`)에 상호·대표자·주소(불만 처리 장소)·전화·이메일·사업자등록번호·통신판매업 신고번호·호스팅 사업자·약관 링크. 공구 상세 하단은 18 그대로. 푸터는 지금 데스크톱만 보인다(`DefaultShell` 의 `max-md:hidden`). [확인 필요 — 모바일 자리, 공정위 사업자정보공개 링크 의무]
57. **계약 통지 메일·주문서 항목** — 제13조 ② 6·8호, 시행령 제20조 2호: 환불 지연배상금(연 15%) 조건, 판매조건 제한(모집 기간·총 수량·1인 한도·차수·성사 조건), 발송 예정일(51), 청약철회 제한(55), 분쟁 처리 안내(1372 소비자상담센터·한국소비자원·전자문서·전자거래분쟁조정위원회). 메일 = `RequiredMail.GROUP_BUY_ORDER_CONFIRMED`(주문 줄·금액·판매 조건·발송·판매자).
58. **정상가(할인율) 근거** — 표시·광고 고시(종전거래가격): 상품 `normal_price_basis`(공식 판매처가·직전 차수 공구가 `PREVIOUS_ROUND`·시가·종전 판매가 `PREVIOUS_SALE`) + `normal_price_source`(내부 메모·URL), 상세에 「정상가 기준: ○○」. 「최저가」 문구 금지 안내. 「한정」은 시스템 문구로 쓰지 않고 「총 N개」만(설명에 「한정」이 있으면 추가 입고 때 경고).
59. **주문서 화면 규칙**(다크패턴 금지 — 제21조의2, 시행령 제9조): 단품·수량 기본 선택 없음, 동의 체크·결제대금예치 선택 기본 체크 없음, 배송비·반품비는 처음부터 금액으로. 「선불·기프트·지역화폐 카드는 부분 취소가 안 돼 계좌로 환불될 수 있어요」 안내. 동의 3종·줄 필수(400).
60. **Q&A 답변 기한**: 설정값 2영업일, 대시보드 「기한 지난 Q&A」(관행, 법정 의무 아님).

### 트랜잭션·워커 (61~66)
61. **환불은 별도 워커**: 불발·중단의 주문별 환불(37 ②)은 상태 워커가 하지 않고 `groupbuy-refund`(1분, 바퀴당 50건, `SKIP LOCKED`)가 「CLOSED_FAILED·CANCELED 공구의 비종결 주문」을 sweep — 대량 환불이 마감·만료를 막지 않고, 멈춘 일도 이어 받는다. 각 주문은 지금 상태를 다시 잠가 읽고 분기(그 사이 결제 중 → 확정된 주문도 환불).
62. **`onPaid` 는 예외를 던지지 않는다**: 상태 불일치·한도·유일 충돌·공급 불가 같은 도메인 사정은 모두 REJECT(예외는 인프라 오류뿐 — 예외면 확정이 영원히 재시도). 받아들이는 일은 JDBC 세이브포인트 안이라 지면 되돌린다. REJECT 경로는 주문 PENDING → EXPIRED + RELEASE 를 **주문 조건부 전이가 이긴 쪽만**(선점 만료 워커와 이중 RELEASE 방지), 이탈 횟수에 세지 않는다(돈을 낸 회원). 「선점 시간 안」 판단은 포트원 `paidAt` 과 비교(결제 계약 결정 4).
63. **결제 다시 열기**: 남은 선점이 1분 미만이면 409 `GROUP_BUY_HOLD_EXPIRED`(`PaymentOrderService.MIN_VALID`). 아직 유효한 READY 결제가 있으면 새로 만들지 않고 그 결제창 정보를 돌려준다(`openCheckout` — 두 번 눌러도 앞 결제가 늦은 결제가 되지 않게). 화면이 「결제창 실패 뒤」(`afterFailure`)라고 알리면 새 결제 번호(commerce.md 8-1). 둘 다 선점 끝 그대로.
64. **에스크로 호출 위치**: 배송 정보 등록·구매 확정은 트랜잭션 밖(커밋 뒤)에서 부르고, 결과는 `escrow_logistics_at`·`escrow_completed_at` 에 조건부 기록, 실패는 `group_buy_escrow_jobs`(LOGISTICS·COMPLETE, 끝나지 않은 일 (발송, 종류) 부분 유일 — 시도·다음 시도·마지막 오류, 간격 1분→5분→30분→2시간→6시간, 6번째 실패 GAVE_UP + 이벤트). 포트원 「이미 등록·완료」 응답은 성공. 에스크로 여부는 결제 조회의 `escrow` 로 판단해 발송 줄 `escrow_applicable` 에 처음 호출 결과로 남긴다(아니면 SKIPPED, 구매 확정 대상 밖).
65. **불변식 검사는 한 SQL**(같은 스냅샷 — `GroupBuyInvariantCheck`): 공구·단품 수량, 주문 줄 합, 결제 금액(39 — `anomaly IS NULL` 결제만, 진행 중 취소분을 뺀 식), 반품비 결제 잔액(`RETURN_FEE_BALANCE`).
66. **부분 유일 색인**(어드바이저리 뒤 마지막 방어선): 진행 중 주문 `(group_buy_id, user_id) WHERE status IN (PENDING_PAYMENT…SHIPPED)` → `GROUP_BUY_ALREADY_JOINED` · 열린 요청 `(participation_id)`(AWAITING_FEE 포함 열린 상태) → `GROUP_BUY_REQUEST_OPEN` · 주문 줄 유일 (주문, 단품) · 줄의 단품이 같은 공구 것인지 복합 FK. (고침 — 옵션 → 단품)

### 금액 (67~76)
67. **PG 로 못 돌려주는 환불은 계좌 송금으로**: `group_buy_refunds.method`(`PG_CANCEL`·`BANK_TRANSFER`·`NONE`). 취소가 GAVE_UP 이고 PG 가 영구 거절(취소 기간 지남·부분 취소 불가 카드·에스크로 확정 뒤)이면 운영자 「계좌 송금으로 전환」(`BankTransferService.convertRefund` — 금액을 바꾸는 전환은 받지 않는다): 취소 줄 MANUAL(`BANK_TRANSFER`) + `commerce_bank_transfers` 행 + 환불 기록 방법 변경을 한 트랜잭션, 감사(돈). 송금 완료 때 기록 COMPLETED.
68. **반품 환불이 마이너스면 회원이 고른다**(사용자 결정 — 운영자가 떠안지 않는다): 단순 변심은 반품 비용이 회원 부담(제18조 ⑨).
    - 신청 화면이 회수 방식별 결과를 미리 보인다 — 예(47,000 + 5,000 무료배송, B 5,000 변심 반품): 업체회수 −1,000 / 직접 발송 +2,000.
    - 마이너스인 방식을 고르면 부족분을 카드로 추가 결제해야 접수(`PaymentOrderType.GROUP_BUY_RETURN_FEE` — 대상 = 반품 요청, 금액 = 부족분, 선점 없음, 유효 30분, 같은 요청에 다시 열기 가능). 결제 전 요청 상태는 `AWAITING_FEE`(열린 요청), 결제 확정 때 REQUESTED, 결제하지 않으면 하루 뒤 EXPIRED(결제창도 결제 행 먼저 닫음). 직접 발송도 마이너스면 직접 발송 + 추가 결제.
    - 철회·거절·닫힘·SUPERSEDED 나 승인 때 다시 계산한 값이 달라지면 남는 몫을 그 결제에서 부분 취소(원인 `RETURN_FEE_REFUND`). 환불액이 정확히 0 이면 취소 없이 기록만(`NONE`).
    - 받은 돈까지 실제로 받은 반품비·재부과로 `return_fee`·`reshipping_fee`·`reshipping_fee_charged` 에 남긴다. 하자·오배송은 빼는 것이 없어 마이너스가 되지 않는다.
    - [법무 확인] 회수 전 부족분 선결제가 청약철회 방해로 보이지 않는지 — 비용 없는 직접 발송 길을 늘 함께 연다.
69. **불발·중단·지연 취소·공급 불가의 「전액」** = 그 주문의 남은 결제액(38 — 앞선 일부 취소를 뺀다). 처리되면 그 주문의 열린 반품·취소 요청은 자동 종료(`SUPERSEDED`, 받아 둔 부족분은 돌려준다).
70. **고액 문턱은 운영자가 금액을 정하는 환불에만**(사용자 결정 — 로봇 공구는 상품 하나가 백만 원대): 고액 기준은 「누가 승인하느냐」이고, 환불은 금액과 관계없이 늘 PG 취소 한 번.
    - 서버가 계산한 환불은 금액과 관계없이 일반 권한(`commerce.refund.approve`·`commerce.order.manage`)으로 바로: 발송 전 취소 승인, 반품 승인(계산기 값 그대로), 공급 불가, 지연 취소, 불발·중단. 본인 취소·늦은 결제 자동 취소는 승인 자체가 없다.
    - `commerce.refund.large` 는 운영자가 금액을 넣는 환불에만 — 요청 없는 운영 환불(32), 고객 택배비 보상. 그 주문의 이런 환불 **누계**(`operator_amount`)가 문턱(기본 100,000원, 0 = 끔) 이상이 되는 건부터(쪼개서 피하지 못하게). 권한이 없으면 운영 환불은 승인 대기(`group_buy_admin_refunds`, 주문당 하나 → 고액 권한자 `approvePending`, 그때 다시 계산해 금액이 다르면 409), 보상은 403 `GROUP_BUY_REFUND_LARGE_REQUIRED`(완료를 고액 권한자가). 계산값을 고쳐 승인하는 기능은 두지 않는다(필요하면 운영 환불). (고침)
    - 모든 환불은 감사(돈, 5년). commerce.md 13-2 「고액 공구 환불(1회 100,000원 이상)」을 이 뜻으로 고친다.
71. **운영 환불 뒤 반품**: 운영 환불도 줄별 금액으로 나눠 기록(금액 비례, 원 미만 버림·남는 원은 마지막 줄) → 그 줄의 다음 반품 환불에서 먼저 뺀다(`prior_refund_offset`), 최종은 `min(계산, 남은 결제액)`. 열린 요청이 있는 주문에는 운영 환불을 막는다(409 `GROUP_BUY_REQUEST_OPEN`).
72. **미리 보기 ↔ 승인 · 견적 ↔ 주문**: 요청에 `preview_amount` 저장 — 승인 때 다시 계산한 값이 다르면 두 값과 사유를 보이고 운영자 확인 뒤 확정(409 `GROUP_BUY_REFUND_AMOUNT_CHANGED`). 주문 API 는 화면이 본 금액(`expectedAmount`)을 비교용으로만 받아 다르면 409 `GROUP_BUY_AMOUNT_CHANGED`(청구액은 늘 서버 계산).
73. **콘솔 직접 취소가 있는 결제**: 열린 대사 이슈 `CANCEL_MISMATCH` 가 있으면 공구 환불 승인을 막는다(409 `GROUP_BUY_PAYMENT_UNRECONCILED` — 대사 처리 뒤 승인). [확인 필요 — 결제 모듈 후속: 재시도 때 포트원 잔액(`amount.total − amount.cancelled`) 쓰기]
74. **MANUAL 의 실제 처리 방법**(결제 모듈 `PaymentCancelManualService` 한 길, `payment_cancels.manual_method`): 「수동 처리 완료」에 방법 — `CONSOLE_CANCEL`(포트원 취소 번호 필수 — 줄에 붙이고 취소 누계까지, SUCCEEDED 와 같게 → 대사 이슈가 매일 다시 열리지 않는다) / `BANK_TRANSFER`(누계 그대로, 67 의 송금 대기 줄) / 표시만. 처리기를 같은 트랜잭션에서 부른다. 모르는 방법 400. 불변식·대사는 송금분을 따로 뺀다(`PaymentCancelStore.succeededAmount` 는 콘솔 수동 포함).
75. **매출을 나눠 셀 수 있게**: `group_buy_refunds` 에 `item_refund`·`shipping_refund`·`return_fee`(−)·`reshipping_fee`(−)·`reshipping_refund`(+)·`compensation_pg`·`compensation_bank`·`fee_covered`(낸 부족분이 메운 몫)·`fee_refund`·`operator_amount`·`completed_at`(합 CHECK). 매출 결제 총액은 받아들인 결제만, 자동 취소(AUTO_REJECT)는 환불이 아니다. 불변식 「주문별 완료 환불의 PG 분 합 = 취소 누계」.
76. **포인트 결제 대비 열**(11절, 3+ 단계): 주문 `point_amount`(0)·`cash_amount`(= amount) — 남은 결제액·불변식·고액 문턱은 처음부터 `cash_amount`. `amount ≥ 1` CHECK.

### 재고 (77~86)
77. **한도 없는 단품도 단품 행을 늘 갱신**: 조건만 `max_qty IS NULL OR current_qty + reserved_qty + :q <= max_qty`. CHECK `0 ≤ current, 0 ≤ reserved, (max IS NULL OR current + reserved ≤ max)`. 불변식: Σ MAIN 단품 current·reserved = 공구 값(단품 최대 합과 공구 최대는 관계없다 — 어느 쪽이 커도 된다).
78. **단품 전부 다 팔림의 동시 확정**: `onPaid` 의 마감 UPDATE 한 문장 — `… SET status='CLOSING', close_reason='SOLD_OUT' … WHERE id=? AND status='RECRUITING' AND (current_qty = max_qty OR NOT EXISTS (판매 중 MAIN 단품 중 max IS NULL 또는 current < max))`. 두 단품의 마지막 1개가 동시에 확정돼 서로 못 보면 1분 워커 안전망이 같은 조건으로 잡는다(10-7 ③에 더함).
79. **종료 사유** `group_buys.close_reason`(varchar(15)): `SOLD_OUT` 다 팔림 · `TIME_UP` 기간 끝 · `EARLY_CLOSED` 운영자 조기 마감(사용자 결정, `closing_at` = 누른 시각, 상태 이력 사유 `EARLY_CLOSE`) · `CANCELED` 중단. CLOSING 전이와 같은 문장에서 기록. 다 팔린 뒤 취소로 수량이 줄어도 표시·「다음 차수 알림 받기」·목록 탭은 이 열. 연장은 상태 이력 `RECRUITING → RECRUITING`(사유 `EXTENDED`). (고침)
80. **처음 수량·추가 입고 표시**: `initial_max_qty`(모집 시작 때 고정, 단품도), 「추가 입고 N개」 = max(0, `max_qty − initial_max_qty`), `added_qty` = ADD 합. 불변식 `max_qty = initial_max_qty + ΣADD − ΣREDUCE`(모집 시작 이후, 공구 값을 적은 이동만). MAIN 단품 추가 입고·줄이기는 같은 수만큼 공구 최대도 움직이고, 추가상품은 공구 최대·`added_qty` 를 바꾸지 않는다. 한도 없는 단품의 ADD/REDUCE 는 409 `GROUP_BUY_SKU_UNLIMITED`(공구 ADD 로).
81. **모집 중 본인 취소로 풀린 수량도 「수량 풀림」**: 알림(신청자)과 DEMAND 연장(25)에 CANCEL 을 더한다. 알림은 남은 수량 > 0 일 때만(헛알림 방지 — 「2b-4 정한 세부」 3). 연장 판단은 이동 줄 `kind IN (RELEASE, CANCEL, RESTOCK, ADD) AND created_at ∈ [end_at − 5분, 판단 시각]`, 색인 `(group_buy_id, created_at)`.
82. **이동 줄의 이후 값은 조건부 UPDATE … RETURNING**(따로 SELECT 하면 다른 트랜잭션 값이 섞인다 — 기록만 하는 이동도 같은 문장). 「마지막」은 `id` 순. CONFIRM·RELEASE 조건에 `reserved_qty >= :q`, 0 행이면 REJECT(`GROUP_BUY_QTY_ANOMALY`) + `COMMERCE_ORDER_ANOMALY`.
83. **`returned_qty` 는 반품 완료 때** 처리 방법과 같은 트랜잭션에서 올린다. 요청 때 줄 수량 ≤ `qty − canceled_qty − returned_qty` 검사, 승인·완료 때 다시 검사.
84. **단품별 남은 수량·품절**: MAIN 남은 = min(단품 `max − current − reserved`, 공구 `max − current − reserved`)(단품 최대가 없으면 공구 값), ADDON 은 자기 한도만(null = 제한 없음). 다 팔림 = 단품 `current = max`(공구가 모집 중이면 추가 입고·다시 판매로 되살아날 수 있다고 안내), 잠시 품절 = 단품 또는 공구의 `current + reserved = max`. 관심 신청 `group_buy_watches.sku_id`(선택)로 단품 품절 알림.
85. **조회 때 정리는 본인 주문만**(10-4 「참여·견적 판정 때 지난 PENDING 정리」): 남의 주문까지 만료시키면 잠금이 넓어진다 — 나머지는 워커.
86. **1인 한도 세부**: `round_limit_shared` 면 지금 차수의 `max_qty_per_user` 로 루트 전체를 센다(루트당 모집 중 차수는 하나라 어드바이저리 키는 공구 단위로 충분). `max_qty_per_user` null = 제한 없음. MAIN 수량만 센다(88).

### 상품정보제공고시 (87)
87. **상품정보제공고시**(공정위 「전자상거래 등에서의 상품 등의 정보제공에 관한 고시」 제2022-15호, 2023-01-01 시행 — 40개 품목·379개 항목, 원문 항목 표 이미지 40장을 옮김). 공구 상세에 표로 보인다.
    - **카탈로그 정본** = 서버 `server/shared/src/main/resources/groupbuy/notice-catalog-2023-01-01.json`(머리에 고시 번호·시행일·옮긴 날짜·출처·일반원칙 2~7 원문). `GroupBuyNoticeCatalog` 가 `classpath*:groupbuy/notice-catalog-*.json` 을 모두 읽고 가장 늦은 시행일이 지금 버전. 항목 `no`(하위는 `7-1` + `group`)·`key`(같은 뜻은 품목이 달라도 같은 키)·`name`·`description`·`kind`(`TEXT`·`DATE_LIKE` ④·`KC_CERT` ⑥·`QUALITY_WARRANTY` ⑤)·`required`(모두 true)·`conditional`, 품목 `scope`·`warrantyDisadvantageDisclosure`. 개정 시 새 버전 파일, 양식은 만든 때 버전 유지(같은 자리·같은 품목은 유지, 새 양식·품목을 바꾼 양식은 지금 버전).
    - **일반원칙(원문 Ⅱ)**: ② 가장 유사한 품목, 없으면 (39)/(40) ③ 제공할 수 없는 항목은 비우지 않고 「제공 불가 + 사유」 ④ 날짜 항목은 「가장 빠른 날짜」·「범위」·「주문일·발송일 기준 기간」으로 대신 ⑤ 품질보증기준 + (10) 노트북·(11)~(14)는 분쟁해결기준보다 불리하면 그 사실 ⑥ KC 마크 + 식별부호, 면제 품목은 면제 사실 ⑦ 부수 상품은 기본 상품 정보만, 별개 상품을 옵션으로 함께 팔면 따로.
    - **데이터는 상품에 붙는다**(89): `group_buy_notices`(상품·차례·품목 코드·제목·카탈로그 버전) · `group_buy_notice_skus`(적용 상품 단품 — 한 단품은 양식 하나에만, 같은 상품 단품만) · `group_buy_notice_values`(`id`·`custom`·`item_key`·`custom_name`·`sort_order` — 고시 항목 = 키·값 / 제공 불가 + 사유(CHECK: 제공 불가면 값 없이 사유 필수), 판매자 추가 항목 = 이름 1~40·내용 1~1,000 평문, 부분 유일 (양식, 키)·(양식, 이름)·(양식, 차례)).
    - **적용 대상**: 적용 단품이 빈 양식 = 기본 양식 — **MAIN 만** 덮고 하나까지. 기본 양식이 없으면 모든 MAIN 단품이 어느 양식에 들어야 한다. 추가 구성상품은 그 상품의 자기 양식이 따로 필수이고, 추가상품을 가리키는 양식은 그 단품 하나만(MAIN·다른 추가상품과 섞으면 400 `skuNames`).
    - **완성 검사**: 모집 시작(공개) 때 + 공개 뒤 저장 때(공개된 고시를 비울 수 없다) — 모든 항목이 값(공백만이면 빈 것) 또는 제공 불가 + 사유, 아니면 409 `GROUP_BUY_NOTICE_INCOMPLETE`(extras `productId`·`noNotice`·`notices[{index(0부터), title, categoryCode, missingKeys}]`·`unassignedSkus`). 공개 전 DRAFT 는 빈 채 저장 가능. 「상세페이지 참조」만 적은 값은 저장하고 경고(`DetailPageWarning`).
    - **판매자 추가 항목**: 양식마다 고시 항목 뒤에 「항목 이름 + 내용」(양식당 20개, 차례 `sortOrder` — 비면 넣은 차례로 0부터 다시 매김). 이름·내용이 모두 빈 줄은 저장하지 않고, 반쪽 줄·길이 초과 400, 같은 양식 이름 겹침·고시 항목과 같은 이름(공백 빼고, 「크기, 무게」처럼 쉼표로 나뉜 각 이름도) 거절. 완성 검사 밖. 회원 화면은 「판매자 추가 정보」.
    - 저장은 통째. 공개 뒤 수정은 정보 정정 — 진행 중 공구 수정 이력 사본(`NOTICE`, 표시·광고 기록 6개월), 모집 시작 이력에도 고시 사본. 추가 차수는 같은 상품의 양식을 그대로 쓴다.
    - KC 인증 대상 여부는 품목·상품마다 운영자 책임(등록 화면 안내). [확인 필요 — 고시 위반 과태료 근거 조문·금액]

### 단품·추가 구성상품 (88)
88. **옵션 = 단품**(사용자 결정 — 결정 23·28·48·77·84 등의 수량·가격 단위 「옵션」은 단품). (고침 — 「축·값 → 조합 자동 생성」 대신 **상품 단품 목록이 정본**·옵션 이름은 **옵션 종류 코드**)
    - **상품 단품** `group_buy_product_skus`: 운영자가 파는 아이템을 한 줄씩 등록(1~200개, 이름 유일, 기본 공급가·판매 여부 — 이 둘은 모집 중에도 고친다). 단품 하나 = 옵션 없는 상품, 옵션 없이 이름으로 가리는 단품 여럿도 된다(쌀 10kg·20kg — `valueIds` 빔, 회원 상세는 한 목록). 상품 ID·단품 ID 는 서버 채번(`product_id`·`id` 두 열, 단품 ID 에 상품 ID 를 붙이지 않음, 운영자 코드 칸 없음). 고칠 때 기존 단품은 `skuId` 로(이름·값을 바꿔도 같은 ID — 공구 단품·고시 연결 유지, 이름 맞바꾸기 가능, 남의 상품 `skuId` 400 `skus`), 목록에 없는 단품은 지운다(공개된 공구가 가리키면 409 `GROUP_BUY_PRODUCT_SKU_IN_USE {skuNames}`). 단품 값이 바뀌면 공구 MAIN 조합 사본도 고친다(이름 사본은 공개 전 DRAFT 공구만).
    - **옵션 종류** `group_buy_option_types`(사용자 결정): 코드 PK(영문 대문자로 시작 2~20자, 만든 뒤 불변)·이름 1~30 유일·차례·숨김. 상품은 코드 0~3개(`optionTypeCodes` — 없는·숨긴·겹친 코드 400, 이 상품이 이미 쓰는 숨긴 종류는 유지 가능), 값은 상품마다 자유 입력(그 종류로 쓴 값을 자동완성으로 권함). 옵션 종류가 있으면 단품마다 값 모두 필수·같은 값 묶음 두 줄 금지(400 `skus`). 상품 축은 `option_type_code` FK(이름 열 없음 — 종류 이름을 바꾸면 상품·회원 상세·수정 이력 사본이 따라감). 저장은 목록 통째(`PUT`, 쓰는 상품이 있는 종류를 빼면 409 `GROUP_BUY_OPTION_TYPE_IN_USE {codes}`)와 한 줄, 감사 `GROUPBUY_OPTION_TYPE_SAVE`. 서버가 단품의 값으로 축·값(단품 목록에 처음 나온 차례)을 만들어 `valueIds` 를 채운다 — 회원 응답 모양(`availability.axes`·`skus[].valueIds`)은 그대로. `ux_gbps_combination` 은 값이 있는 단품만.
    - **공구 단품** `group_buy_skus`: 상품 단품마다 이 공구의 `kind`(`MAIN` 본 상품 / `ADDON` 추가 구성상품)·`extra_price`(MAIN — 가격 = 공구가 + 추가금)·`addon_price`(ADDON ≥ 1)·`max_qty`(null = 공구 한도만)·`initial_max_qty`·`current_qty`·`reserved_qty`·`on_sale`·`supply_price`·차례, 유일 (공구, 상품 단품)·(공구, 이름)·MAIN 의 (공구, 조합)(값이 있는 것만). 설정을 적지 않은 상품 단품은 추가금 0·한도 없음·판매 여부와 공급가는 상품 기본값. 이름: MAIN = 상품 단품 이름(옵션 없는 단품 하나면 상품 이름), ADDON = 단품 하나뿐인 상품이면 상품 이름, 아니면 「상품 이름 (단품 이름)」. 글자 상한 축 이름 30·값 30·추가상품 이름 100.
    - **추가 구성상품**(고시 원칙 ⑦ 의 별개 상품) = **다른 상품**의 단품(같은 상품 단품은 400 `addons`, ≤ 20, 판매 여부 스위치 없음 — 늘 판매, `maxQty` null = 한도 없음). **단독 구매 불가** — 주문에 MAIN 줄이 하나 이상(400 `items`). 본 상품 줄을 모두 취소·반품하면서 추가상품을 남기면 409 `GROUP_BUY_ADDON_ONLY {skuIds}`(화면이 「함께 고르기」를 먼저 묻는다, 불발·중단·지연 취소는 늘 주문 전체라 해당 없음). 공구 최대·성사 판정(`min_qty`)·1인 한도·다 팔림은 **MAIN 수량만**(추가상품은 자기 한도만), 무료배송 기준 금액에는 포함.
    - **고르는 곳은 상세 화면**(일반 쇼핑몰처럼), 주문서는 고른 목록 확인만. 선점은 「결제하기」 때.
    - **모집 뒤 잠금** `options`(409 `GROUP_BUY_LOCKED_FIELD`): 단품 구성·추가금·한도(처음 한도나 지금 한도 중 하나와 같으면 같다고 본다 — 추가 입고로 바뀐 한도를 그대로 보내도 통과)·판매 여부(모집 뒤 판매 끈 단품 다시 켜기 불가), 추가상품 구성·가격·한도.

### 상품 마스터·카테고리·공급가 (89)
89. **상품과 공구를 나눈다**(사용자 결정 — 같은 상품을 차수·조건을 바꿔 반복해서 판다):
    - **상품** `group_buy_products`: 카테고리(필수)·이름·요약·사양·설명·이미지(`group_buy_product_images`)·정상가·정상가 근거·청약철회 제한·옵션 종류·상품 단품·고시 양식·`version`. 관리자 「상품」(`commerce.product.manage`, 조회 `commerce.product.read`).
    - **공구** = 상품 하나 + 판매 조건(공구가·기간·최소/최대·1인 한도·배송비 정책·발송 예정일/영업일·연장 방식·반품 정책·차수 한도) + 공구 단품 설정. `group_buys` 에는 상품 칸(이름·설명·정상가·청약철회 제한)이 없고 조회는 상품을 이어 읽는다(`GroupBuyRow` 모양 그대로). 공구 등록은 「상품 고르기 → 단품별 설정 표」.
    - **상품 수정**: 설명·이미지·카테고리·고시·기본 공급가·기본 판매 여부는 언제든 — 진행 중 공구(모집 중~발송 중)에 바로 보이고(정보 정정) 그 공구마다 수정 이력 사본(`CONTENT`·`NOTICE`). 이름·정상가·청약철회 제한·옵션 구성은 그 상품을 쓰는(본 상품 또는 추가 구성상품) 공구가 공개된 예정·모집 중·집계 중이면 409 `GROUP_BUY_LOCKED_FIELD`(field `name|normalPrice|withdrawalRestriction|options` — 화면도 같은 칸만 잠근다). 모집 시작은 관련 상품 행을 잠가 구성 바꾸기와 하나만 이긴다. 공개 전 DRAFT 공구는 상품 구성을 따라간다(없앤 단품은 빠지고 더한 단품은 기본값으로).
    - **모집 시작 사본**: 상품 내용(이름·설명·이미지·축·단품·고시)의 사본을 공구 수정 이력에 남긴다(표시·광고 기록, 공급가는 넣지 않음).
    - **카테고리** `group_buy_categories`: 2단(앱 검사)·이름 유일 (부모, 이름)·차례·숨김, 상품이 있으면 못 지움, 통째 저장, 감사 `GROUPBUY_CATEGORY_SAVE`. 회원 목록 필터(`publicList(categoryId)`): 공개된 공구(중단 제외), 위 단을 고르면 아래 단 포함, 숨긴 카테고리(숨긴 부모 아래 포함) 제외.
    - **공급가**: 상품 단품 기본 공급가(선택)를 공구 단품으로 복사해 고친다. DRAFT 는 비워도 저장, **모집 시작 때 모든 공구 단품(MAIN·ADDON, 판매 끈 것 포함)에 필수**(0 은 명시 입력만, 빈 값 409 `GROUP_BUY_SUPPLY_PRICE_MISSING {skuIds}`). 판매가 < 공급가면 `marginConfirmed` 없이 409 `GROUP_BUY_NEGATIVE_MARGIN {skuIds}`(화면이 이름으로 바꿔 확인 창 → `marginConfirmed: true` 로 다시, 모집 시작 감사에 `negativeMarginSkus`). 모집 뒤 공구 수정은 공급가만(`PATCH …/content` — 비우기 거절, 판매가보다 크게 올리면 확인, 단품마다 감사 `GROUPBUY_SUPPLY_PRICE`). 보는 권한 `commerce.product.price`·`commerce.settlement.read`, 회원 응답에는 없다.

## 구현에서 정한 세부 (단위별 번호 — 코드가 「2b-N 정한 세부 n」으로 가리킨다)
결정으로 옮긴 것은 「→ 결정 N」만 적는다.
- **2b-2**: 1 환불 기록 표는 V101(→ 36) · 2 **「모집 시작」 = 공개**(`published_at`): 누르면 공개(시작 전이면 「예정」), 시작 시각에 상태 워커가 DRAFT → RECRUITING(그때의 배송비 정책 사본·처음 수량·수정 이력 1), 시작 시각이 지났으면 누른 자리에서. 공개 뒤에는 공급가·고시·상품 정정만(가격을 미리 공개하므로 공개 시점부터 잠근다) · 3 → 63 · 4 **`onPaid` 거절 사유 코드** `GROUP_BUY_ORDER_NOT_FOUND`·`_ORDER_NOT_PENDING`·`_PAYMENT_MISMATCH`·`_HOLD_EXPIRED`·`_AMOUNT_MISMATCH`·`_CLOSED`(판정·중단 뒤)·`_QTY_ANOMALY`(선점 부족 — 이벤트 `anomaly=true`)·`_CONFLICT`(DB 제약 충돌)·`_SUPPLY_FAILED`(2b-3 정한 세부 17) · 5 **만료 원인** `ExpireCause`: `HOLD_TIMEOUT`(이탈로 셈)·`GROUP_BUY_CANCELED`·`PAYMENT_FAILED_CLOSED`(불발 공구의 결제 중 주문)·`REJECTED_CLOSED`·`LATE_PAYMENT`·`REJECTED_ANOMALY` · 6 → 79 · 7 → 52 · 8 → 46, 전액 나눔 = 남은 결제액 → 상품(가진 수량) + 나머지(앞서 재부과한 배송비는 `reshipping_refund`, 그 밖은 `shipping_refund`) · 9 배송지 암호 용도 `groupbuy.address.{recipient,phone,line1,line2,memo}`(DataCipher), 우편번호만 평문 · 10 판매 스위치 `GroupBuySalesSwitch` → `DefaultGroupBuySalesSwitch` 는 DB 의 지금 값(→ 19, 고침) · 11 회원·운영자 알림·메일은 `GroupBuyEvents` 를 업무 트랜잭션 안에서 발행하고 `GroupBuyNotifications` 가 받는다 · 12 수량·상태 저장소는 JDBC 행 레코드.
- **2b-2 고시**: 1 적용 대상은 단품 이름(`skuNames`)으로 받고 응답은 `skuIds`·`skuNames`(고침 — 옛 옵션 이름) · 2·4·5·6 → 87 · 3 저장 통째·카탈로그 버전 유지(→ 87) · 7 회원 표·관리자 API 는 2b-4(완료).
- **2b-2 단품**: 1 단품 이름은 공구 안에서 유일 — 고시·DRAFT 다시 잇기·추가 차수 복사가 이름으로 가리킨다 · 2 공구 행 `current/reserved/max` = MAIN 단품만(→ 88) · 3 잠금 차례 어드바이저리 → 공구 행(MAIN 합, 한 문장) → 단품 행 ID 오름차순 — MAIN 필수라 공구 행이 늘 먼저 · 4 무료배송 기준 = 추가상품 포함 상품 금액 합 · 5 → 88(`ADDON_ONLY`, 반품도 같은 규칙) · 6 → 87 · 7 → 88(모집 뒤 잠금) · 8 불변식: 공구 = Σ MAIN 줄 판매 수량, 단품마다 = 그 단품 줄, Σ MAIN 단품 = 공구, 공구 마지막 이동은 공구 값을 적은 줄, 최대 식은 공구 값을 적은 ADD·REDUCE 만 · 9 MAIN 단품 이미지는 받지 않는다(공구 단품 `image_key` 없음).
- **2b-2 상품**: 1 공구 단품 기본값·추가 구성상품 이름(→ 88) · 2·3·4 → 89 · 5 → 31 · 6 → 89(카테고리 필터). **「상품 단품 목록이 정본」 2b-2 반영**: ① 옵션 이름 없이 이름으로 가리는 단품 여럿 ② 기존 단품은 `skuId` 로 같은 ID ③ 축 값 차례 = 단품 목록에 처음 나온 차례 ④ 추가 구성상품 이름 규칙(모두 → 88).
- **2b-3**:
  1. 요청 상태 `AWAITING_FEE → REQUESTED → APPROVED → RECEIVED → COMPLETED` / `REJECTED`·`WITHDRAWN`·`SUPERSEDED`·`EXPIRED`. AWAITING_FEE 도 열린 요청 — 회수 방식을 바꾸려면 철회 뒤 다시. 철회는 회수 확인 전까지.
  2. 요청 줄 사유 `CHANGE_OF_MIND`·`DEFECT`·`WRONG_ITEM`·`DAMAGED`(배송 중 훼손)·`OTHER` — 업체 부담은 하자·오배송·훼손, **기타는 회원 부담**.
  3. **반품 기한**: 단순 변심·기타 줄이 하나라도 있으면 배송완료(한국 날짜) + 7일, 모두 업체 부담 줄이면 + 3개월(그날 끝). 3개월 뒤는 16.
  4. 「전체 반품」 = 이 요청으로 주문의 남은 줄(추가상품 포함)이 모두 빠질 때. 업체 부담 전체 반품은 처음 배송비 + 발송 전·뒤 재부과까지 모두 돌려준다(G32 하자 28,000). 회원 부담 전체는 편도·고객발송이면 발송 뒤 재부과만 돌려주고, 왕복 + 업체회수면 처음 배송비를 한 번만 받은 것으로(이미 받은 재부과와 차액).
  5. 반품 배송비·재부과 몫은 요청 줄 중 회원 부담 줄(없으면 마지막 줄)의 `refund_amount` 에 싣는다.
  6. 결과가 음수면 부족분 = −결과로 `GROUP_BUY_RETURN_FEE` 결제(→ 68). 완료 때 낸 부족분이 메운 몫은 `fee_covered`(주문 결제 환불액에 더해 합이 맞는다), 남는 몫은 부분 취소. 다시 계산한 부족분이 낸 돈보다 크면 재부과 → 반품비 차례로 실제 받은 만큼만 남긴다.
  7. 발송 전 취소 요청 거절은 「이미 발송함」 + 택배사·송장 — 같은 트랜잭션에서 발송 처리(열린 요청이 발송을 막으므로 거절이 먼저).
  8. 반품 거절은 근거 사진 1~3장 필수, `USED_VALUE_LOSS`·`TIME_VALUE_LOSS` 는 상품 `withdrawal_restriction` 이 있고 업체 부담 요청이 아닐 때만, 반품에 `ALREADY_SHIPPED` 금지(409 `GROUP_BUY_REJECT_REASON_NOT_ALLOWED`). 받아 둔 부족분은 거절·철회·닫힘·SUPERSEDED 때 모두 돌려준다.
  9. 보상액은 업체 부담 + 고객발송 반품만, 상한 = 설정 `compensation_cap`. PG 몫 = min(보상, 남은 결제액 − 이번 환불), 넘는 몫은 `commerce_bank_transfers(GROUP_BUY_COMPENSATION)`. 보상은 고액 문턱 누계(`operator_amount`)에 든다(→ 70).
  10. 운영 환불(→ 32·70·71) — 승인 대기는 `group_buy_admin_refunds`(`AWAITING_APPROVAL`).
  11. 공급 불가 순서·기한(→ 53).
  12. 배송지 수정 기한(→ 30), 명단 CSV(→ 20), 열람 감사 detail 은 사유·주문 번호만(평문 없음).
  13. 에스크로 여부·송장 정정·교환 재발송(→ 64·14).
  14. 수동 처리 「계좌 송금」(→ 67·74): 공구 처리기가 환불 기록 방법을 BANK_TRANSFER(진행 중 그대로) + 송금 대기 줄, 송금 완료 때 COMPLETED. 불변식은 BANK_TRANSFER 수동 취소를 「아직 돌려줄 몫」에서 뺀다.
  15. 불변식에 `RETURN_FEE_BALANCE`(부족분 결제 − 취소 = 낸 금액 − 돌려준 금액), `PAYMENT_BALANCE` 는 계좌 송금으로 돌린 취소를 뺀다. 다시 판매·폐기·교환 출고 이동도 단품 이후 값을 조건부 UPDATE … RETURNING 으로.
  16. **「안 날부터 30일」**(제17조 ③ — V103): 배송완료 + 3개월이 지나도 요청 줄이 모두 업체 부담이면, 회원이 적은 「하자를 안 날」(`defectKnownOn`)이 오늘(한국 날짜)부터 30일 안일 때 받는다. 안 날이 미래이거나 배송완료 날 전이면 400(`groupbuy.request.defectKnownOn.invalid`), 적지 않았거나 31일째면 409 `RETURN_WINDOW_CLOSED`. 단순 변심·기타 줄이 있으면 7일이고 안 날은 남기지 않는다. 운영자는 날짜가 사실과 다르면 `OUT_OF_WINDOW` + 근거 사진으로 거절(감사 detail 에 `defectKnownOn`).
  17. **공급 불가 뒤 결제 중 주문**: `onPaid` 가 단품 수량 확정 UPDATE **뒤에** `group_buy_supply_failures` 를 묻고, 그 단품이 있으면 REJECT(`GROUP_BUY_SUPPLY_FAILED`, `REJECTED_CLOSED` + 선점 해제 → 자동 취소). 공급 불가 기록도 같은 단품 행을 잠그므로(판매 중지 UPDATE) 빠지는 틈이 없다. 공급 불가 처리는 결제 중 주문을 직접 닫지 않는다(결제 행을 먼저 잠가야 해서 — 결제 확정 판정과 선점 만료 워커에 맡긴다).
  18. **동시성**(G17): 같은 주문의 ① 취소 요청 승인 ∥ 운영 환불 ② 반품 완료 ∥ 운영 환불 ③ 취소 요청 승인 ∥ 중단 환불 워커 — 모두 주문 행 → 요청 행 차례로 잠가 교착 없이 한쪽이 기다렸다가 새 상태로 판정. 운영 환불은 열린 요청이 있으면 409, 중단 워커가 먼저면 전체 환불 + 열린 요청 SUPERSEDED.
  19. 번호: 2b-4 = V104, Q&A·리뷰 = V106(V105 는 테마·레이아웃).
- **2b-4**(가정으로 보고한 것 포함):
  1. 회원 알림은 종류 하나 `GROUP_BUY`(회원이 끌 수 없는 거래 알림) + 덧붙임 `kind`(주문 확인·자동 취소·선점 만료·수량 풀림·마감 임박·연장·차수 시작·환불 완료·운영자 처리 환불·발송 지연·공급 불가 선택·성사·불발·중단·요청 상태·`QNA_ANSWERED`·`REVIEW_REPLIED`). 메일은 법·돈에 닿는 것만 커머스 메일 대기열(`RequiredMail.GROUP_BUY_ORDER_CONFIRMED`·`_REFUND_COMPLETED`·`_ORDER_NOTICE`(발송 지연·공급 불가·자동 취소)), 관심 알림 메일은 회원 이벤트 설정의 이메일을 따른다. 처리기는 업무 트랜잭션·결제 행 잠금 안에서도 공구 행을 잠그지 않는다.
  2. 메일 변수의 글(판매 조건·발송 문구)은 사이트 기본 언어(한국어)로 만든다.
  3. **수량 풀림**: 단품 신청은 단품·공구 남은 수량이 둘 다 > 0(추가상품은 단품만), 공구 단위 신청은 공구 남은 > 0, 같은 신청은 `last_notified_at` 조건부로 10분에 한 번. 연장 알림 = 마감 임박·수량 풀림 신청자(같은 회원 한 번). 마감 임박 = 모집 마감 24시간 안, 신청마다 한 번.
  4. **P35 경고**: 요청·단계(WARNING → CRITICAL)마다 한 번(`deadline_alert_level` 조건부). 발송 기한(→ 51).
  5. 개인정보 파기(→ 34).
  6. 추가 차수 기간 처음 값(→ 31). 회수 확인의 반환받은 날 = 처리한 시각(3영업일 기한이 늦춰지지 않게). 교환 재발송의 돌려받은 물건 = 줄마다 「공급처 반송」. 배송 명단 = 한 번 쓰는 `data:` 주소(서버에 파일을 남기지 않음).
  7. 대시보드 칸 정의(→ 「화면 목록」).
  8. 관리자 권한 입구(→ 35).
  9. **판매 켜기**: 최고 관리자 + 자동 조건 넷(사업자 정보 필수 칸·PG `PaymentGateway.ready()`·켜진 택배사·본인인증 시작 가능) + 체크 여섯, 아니면 409 `COMMERCE_SALES_NOT_READY {missing}`. 끄기는 최고 관리자 또는 `commerce.settings`. 사유 5~200자, 감사 `COMMERCE_SALES_TOGGLE`(전→후·사유) + 운영자 알림.
  10. local 시드(→ 11).

## Q&A 규칙 (2b-6)
- **표**: `group_buy_questions`(공구·회원·본문 평문 ≤ 1,000자·비밀글·`forced_secret`·상태 OPEN/ANSWERED·숨김·지운 시각·보관 만료일) · `group_buy_answers` · `group_buy_answer_revisions`(이전 내용·고친 운영자·시각).
- **누가·언제**: 로그인한 정상 회원(본인인증 없이). 공개된 공구(예정·모집 중·종료) 어디서나, 중단된 공구는 닫는다(409 `GROUP_BUY_QUESTION_CLOSED`). 정지·탈퇴 회원은 못 쓰고, 공구 참여 제한(`GROUP_BUY_BAN`)은 질문을 막지 않는다. 쓰기 간격 30초(가정).
- **보기**: 공개 질문은 누구나, 비밀글은 작성자와 운영자만. Q&A 는 공구마다 따로, 상세에 「이전 차수 문의 보기」 링크.
- **개인정보 자동 비밀글**(사용자 결정): 본문에 전화번호·이메일·주소 모양(`PersonalInfo` — 전화·이메일·「구/군 + 로/길 + 번지」)이 보이면 서버가 비밀글로 저장(`forced_secret`)하고 응답에 싣는다. 작성 화면은 「연락처·주소가 보여 다른 회원에게 보이지 않게 비밀글로 바꿨어요」 안내, 쓰는 동안 「연락처·주소는 쓰지 마세요」.
- **작성자**: 답변 전에는 고치거나 지운다. 답변 뒤에는 못 고치고(409 `GROUP_BUY_QUESTION_ANSWERED`) 공개 → 비밀글 전환과 지우기만. 지워도 보관 기간 동안 감춘 채 남는다.
- **운영자**: 답변·답변 수정(이력, 감사 `GROUPBUY_QNA_ANSWER_EDIT`), 숨김(사유 5~200자, 감사 `GROUPBUY_QNA_HIDE`), 비밀글 열람·관리자 Q&A 목록 조회는 개인정보 열람 감사(`GROUPBUY_QNA_VIEW`, 2년). 첫 답변이면 작성자에게 앱 알림, 새 질문·신고는 운영자 알림(결정 12).
- **신고**: 공용 `POST /api/reports`, 대상 `GROUP_BUY_QUESTION`. **세션 정리 결정 — 신고 저장소**: 기존 `reports`·`report_cases` 는 `board_id` 가 꼭 있어야 하고 신고 관리 화면이 게시판 기준이라 공구 신고는 별도 표 `group_buy_ugc_reports` 에 둔다(`ReportService` 가 회원 상태·신고 켜짐·사유를 같은 규칙으로 검사한 뒤 `GroupBuyUgcReportService` 로 넘김). 처리는 숨김(열린 신고 RESOLVED). 대상마다 한 번만 — 신고 간격·하루 한도는 아직 없다.
- **보관**(사용자 결정): 단순 문의도 소비자 문의 기록 — 마지막 답변일(없으면 작성일)부터 3년 뒤 파기(`R__batch_32` ④, commerce.md 17-3). 탈퇴 회원은 「탈퇴한 회원」. 개인정보 처리방침에 항목을 더한다. [법무 확인 — 단순 상품 문의가 「소비자 불만·분쟁 처리 기록」에 드는지, 보수적으로 3년]
- 하자·오배송 환불은 Q&A 가 아니라 반품 요청으로 안내.

## 리뷰 규칙 (2b-6, 사용자 확정)
- **누가·언제**: 그 단품을 산 회원. 주문 줄이 배송완료된 뒤부터 배송완료일 + 90일까지, 주문 줄(단품)마다 하나(`group_buy_reviews.item_id` 유일). 전부 취소·반품된 줄은 못 쓴다. 쓸 수 있는지는 서버가 줄마다(`WRITABLE`·`WRITTEN`·`EXPIRED`·`NONE`).
- **내용**: 별점 1~5(필수) · 글 10~1,000자 · 사진 5장까지(먼저 올리고 ID 로 붙임) · 산 단품 이름은 주문 줄 사본.
- **작성자**: 작성 30일 안에 고치기(이력 `group_buy_review_revisions`), 언제든 지우기 — 같은 줄에 다시 쓰지 못한다(가정), 지운 줄은 남는다.
- **운영자**: 공식 답글 하나(수정 가능 — 회원 화면 「답글 수정됨」, 감사 `GROUPBUY_REVIEW_REPLY_EDIT`), 숨김은 사유 코드로만(욕설·개인정보·광고·무관한 내용·기타 + 메모, 기타는 메모 필수 5~200자, 감사 `GROUPBUY_REVIEW_HIDE`). **낮은 별점은 숨김 사유가 아니다**(표시광고법·공정위 소비자 리뷰 지침). 숨긴 리뷰는 작성자에게 「숨김 처리됨 + 사유」, 평균·수에서 빠진다. 숨김 풀기 사유 5~200자. 숨기면 열린 신고도 처리. 첫 답글이면 작성자 알림. 권한은 Q&A 와 같다.
- **신고**: 공용 신고에 대상 `GROUP_BUY_REVIEW`(사유는 글·댓글 체계, 저장은 `group_buy_ugc_reports`).
- **노출**: 상세 머리 = 평균 별점·리뷰 수·「리뷰 제공 기준 ⓘ」(별점 분포 없음 — 고침), 정렬(최신·별점 높은·낮은·사진 먼저), 사진 리뷰만. 목록 카드에는 평균(소수 첫째 자리)과 수. **같은 상품(상품 마스터)의 다른 차수 리뷰도 함께**, 카드마다 「n차 구매」.
- **보상: 없음**(사용자 확인 대기). 보상을 넣으면 「대가를 받은 리뷰」 표시 의무(공정위 추천·보증 심사지침) — 보상 여부 열과 화면 표시가 필요하다. 지금 화면은 「리뷰를 쓴 대가로 받은 혜택은 없어요」.
- **가정**: 답글 수정은 이력 없이 시각·사람만 · 탈퇴 회원은 「탈퇴한 회원」 · 숨긴 남의 리뷰는 회원 목록에서 아예 빠진다(Q&A 는 자리 글을 남긴다) · 지운 리뷰 본문 보관 기간 미정(지금은 남긴다).

## 홈 `GROUP_BUY` 가젯 연결
- `GroupBuyMemberService.recruiting(count, order)` — 회원 목록과 같은 거름(공개·숨긴 카테고리 제외)·줄 모양(`ListItem`, 리뷰 평균·수)에서 **모집 중(`RECRUITING`)만**(집계 중은 살 수 없어 뺀다). `count` 1~10(기본 5).
- 차례(가젯 설정 `sort`, 사용자 결정 2026-10-29): `DEADLINE` 마감(연장 끝) 이른 순(기본 — 없거나 모르는 값도) · `NEWEST` 시작 시각 늦은 순 · `POPULAR` 참여 수량(`current_qty`) 많은 순, 같으면 마감 이른 순. 운영자는 관리자 홈 구성 실제 화면 편집의 ⚙ 설정 창에서 개수·정렬을 고친다(`PUT /api/admin/home-layouts/{theme}` 의 공구 가젯 `config`, 틀리면 400 `gadgets[i].config.count|sort`).
- 테스트: api `HomeGadgetGroupBuyTest`(정렬 3가지·모르는 값 포함 7건), admin `HomeLayoutGroupBuyConfigTest`(처음 값·저장·보내지 않으면 그대로·새 가젯·400·다른 종류 무시 4건). data = `{items, salesOpen}` — 판매 스위치는 거르지 않고 값만 싣는다(꺼지면 가젯 위에 「지금은 주문을 받지 않아요」).
- 화면 `HomeGadgetGroupBuyCard` 가 `GroupBuyListItem` 을 그린다(상세 주소는 `UtilGroupBuy.detailPath(id)`).

## 감사·오류 코드 (코드 주석의 「감사·오류 코드 후보」)
- **감사 — 돈 관련**: `GROUPBUY_CANCEL`·`GROUPBUY_REFUND`·`GROUPBUY_REQUEST_APPROVE`·`GROUPBUY_REQUEST_REJECT`·`GROUPBUY_STOCK_ADD`·`GROUPBUY_STOCK_REDUCE`·`GROUPBUY_RETURN_DISPOSE`·`GROUPBUY_ROUND_CREATE`·`GROUPBUY_SUPPLY_FAILURE`·`GROUPBUY_BANK_TRANSFER`·`GROUPBUY_SUPPLY_PRICE`·`GROUPBUY_SETTINGS_UPDATE`·`COMMERCE_SALES_TOGGLE`·`COMMERCE_SHIPPING_POLICY_CREATE`·`COMMERCE_SHIPPING_POLICY_DELETE`.
- **감사 — 개인정보 열람**: `GROUPBUY_ADDRESS_REVEAL`·`GROUPBUY_SHIPPING_EXPORT`·`GROUPBUY_ORDER_VIEW`·`GROUPBUY_QNA_VIEW`.
- **감사 — 일반**: `GROUPBUY_CREATE`·`GROUPBUY_UPDATE`·`GROUPBUY_START`·`GROUPBUY_CLOSE_EARLY`·`GROUPBUY_DELAY_NOTICE`·`GROUPBUY_PRODUCT_SAVE`·`GROUPBUY_CATEGORY_SAVE`·`GROUPBUY_OPTION_TYPE_SAVE`·`GROUPBUY_FULFILLMENT`·`GROUPBUY_ORDER_MEMO`·`COMMERCE_CARRIER_SAVE`·`COMMERCE_SALES_CHECKLIST`·`COMMERCE_BUSINESS_INFO_UPDATE`·`GROUPBUY_QNA_HIDE`·`GROUPBUY_QNA_ANSWER_EDIT`·`GROUPBUY_REVIEW_HIDE`·`GROUPBUY_REVIEW_REPLY_EDIT`. 감사 대상 `GROUP_BUY`·`SHIPPING_POLICY` 등. 정확한 분류의 정본은 `AuditActions`·`AuditRetention`.
- **오류**: 404 `GROUP_BUY_NOT_FOUND`·`GROUP_BUY_PRODUCT_NOT_FOUND` · 403 `GROUP_BUY_SALES_CLOSED`·`GROUP_BUY_REFUND_LARGE_REQUIRED` · 409 `GROUP_BUY_NOT_RECRUITING`·`GROUP_BUY_SOLD_OUT`·`GROUP_BUY_SKU_SOLD_OUT`·`GROUP_BUY_SKU_NOT_ON_SALE`·`GROUP_BUY_SKU_UNLIMITED`(옛 이름 `GROUP_BUY_OPTION_SOLD_OUT`·`GROUP_BUY_OPTION_UNLIMITED` 은 단품 코드로 바뀜)·`GROUP_BUY_LIMIT_EXCEEDED`·`GROUP_BUY_ALREADY_JOINED`·`GROUP_BUY_HOLD_EXPIRED`·`GROUP_BUY_AMOUNT_CHANGED`·`GROUP_BUY_ADDON_ONLY`·`GROUP_BUY_LOCKED_FIELD`·`GROUP_BUY_VERSION_CONFLICT`·`GROUP_BUY_NOT_DRAFT`·`GROUP_BUY_NOT_CANCELABLE`·`GROUP_BUY_NOT_ORDERABLE`·`GROUP_BUY_QTY_BELOW_FLOOR`·`GROUP_BUY_NOT_RESELLABLE`·`GROUP_BUY_ROUND_EXISTS`·`GROUP_BUY_ROUND_SOURCE_INVALID`·`GROUP_BUY_SHIPPING_POLICY_MISSING`·`GROUP_BUY_NOTICE_INCOMPLETE`·`GROUP_BUY_SUPPLY_PRICE_MISSING`·`GROUP_BUY_NEGATIVE_MARGIN`·`GROUP_BUY_PRODUCT_SKU_IN_USE`·`GROUP_BUY_OPTION_TYPE_IN_USE`·`GROUP_BUY_REQUEST_OPEN`·`GROUP_BUY_REQUEST_NOT_OPEN`·`GROUP_BUY_RETURN_WINDOW_CLOSED`·`GROUP_BUY_REFUND_AMOUNT_CHANGED`·`GROUP_BUY_REFUND_SHIPPED`·`GROUP_BUY_REJECT_REASON_NOT_ALLOWED`·`GROUP_BUY_PAYMENT_UNRECONCILED`·`GROUP_BUY_ORDER_NOT_PREPARABLE`·`GROUP_BUY_ORDER_NOT_SHIPPABLE`·`GROUP_BUY_ORDER_STATE`·`GROUP_BUY_TRACKING_INVALID`·`GROUP_BUY_EXPORT_NOT_READY`·`GROUP_BUY_ADDRESS_LOCKED`·`GROUP_BUY_ADDRESS_ERASED`·`GROUP_BUY_QUESTION_ANSWERED`·`GROUP_BUY_QUESTION_CLOSED` · `onPaid` 거절 사유(결제 REJECT — `GROUP_BUY_CLOSED`·`GROUP_BUY_HOLD_EXPIRED` 등)는 「2b-2 정한 세부」 4. 그 밖: `COMMERCE_SHIPPING_POLICY_OVERLAP`·`COMMERCE_SHIPPING_POLICY_STARTED`·`COMMERCE_CARRIER_IN_USE`·`COMMERCE_SALES_NOT_READY`·`COMMERCE_SALES_ROOT_ONLY`(409) · `DATA_KEY_MISSING`(503). 정본은 `GroupBuyException`·번들 ko·en.

## 규칙 → 테스트
테스트는 shared 가 기본(`com.community.groupbuy.*` 등), admin·api 는 앞에 적는다. `…` 는 메서드 이름 뒷부분 줄임.
| 번호·규칙 | 시나리오 → 기대 | 테스트 |
|---|---|---|
| G1 · 결정 15 | 다시 판매: 모집 중 + 단순 변심 → 확정 감소·알림 / CLOSING 뒤 `NOT_RESELLABLE` / 하자·오배송 거절 / 보관·폐기 수량 그대로 / 합 ≠ 줄 수량 거절 | `GroupBuyRequestTest.G1_…` |
| G2 | 마지막 1개 확정 직후(워커 전) 추가 입고·다시 판매 → 거절(이미 CLOSING) | `GroupBuyStockTest.G2_…` |
| G3 | 잠시 품절에서 추가 입고 → 선점 만료 → 추가분과 풀린 수량 모두 판매 | `GroupBuyStockTest.G3_…` |
| G4 | 줄이기 하한(확정 + 선점, 성사형 `min_qty`) 아래 거절 · 줄여서 확정 = 최대면 즉시 CLOSING | `GroupBuyStockTest.G4_…` · `성사형_최소_수량_…` |
| G5 · 결정 23·77 | 여러 줄 동시 선점·추가 입고·줄이기 — 공구·단품 수량 함께 맞음, 교착 없음, 줄 하나 모자라면 주문 전체 실패, 한도 없는 단품도 수량 열 갱신 | `GroupBuyJoinTest.G5_여러_줄_중_…` · `GroupBuyStockTest.G5_여러_줄_동시_선점과_…` |
| G6 | 부분 반품 다시 판매 뒤·전체 반품 폐기 뒤·불발 환불 뒤·중단 뒤 불변식 — 판매 수량 식과 일치 | `GroupBuySchemaTest.G6_…` · `GroupBuyRequestTest.G7_G6_…` · 각 시험의 `assertInvariants` |
| G7 · 결정 43 | 25,000 × 3 무료배송 1개씩 세 번 반품 → 22,000·19,000·25,000, 재부과 두 번 빼지 않음, 왕복 맞춤, REFUNDED·PG 누계 66,000 | `GroupBuyRefundCalculatorTest.G7_…`(둘) · `GroupBuyRequestTest.G7_G6_…` |
| G8 · 결정 31·24 | 추가 차수: 끝난 공구만·복사 범위·동시 두 번 하나·DRAFT 차수 있으면 거절·중단 공구 거절·한도 독립/합산·시작 때 이전 신청자 한 번 | `GroupBuyAdminTest.G8_…` · `중단된_공구에서는_…` · `GroupBuyJoinTest.차수_사이_한도는_…_G8_G20` · `GroupBuyNotificationsTest.G8_…` |
| G9 | 결제창 열린 동안 중단 → 그 뒤 결제: 주문 EXPIRED(`GROUP_BUY_CANCELED`)·선점 해제·늦은 결제 자동 취소·이탈 안 셈 | `GroupBuyPaymentTest.G9_…` |
| G10 · 결정 30 | 즉시 발송 / 마감 후 발주 공구의 명단·배송지 수정 기한 · 발송된 주문이 있는 공구 중단 → 발송 전만 환불 | `GroupBuyFulfillmentTest.G10_…`(셋) |
| G11 · G11R | Q&A: 비밀글 열람·답변 전후 수정·지워도 3년 보관·자동 비밀글과 안내·숨김 감사·신고·속도 제한·정지 회원 / 리뷰: 산 회원·배송완료 뒤 90일·줄마다 하나·모두 취소·반품 줄 거절·검증·30일 수정·이력·삭제·숨김 사유 코드만·감사·사유 표시·평균·수 제외·신고·같은 상품 다른 차수 | `GroupBuyFeedbackTest`(15) |
| G12 · 결정 25 | 마감 4분 전 다시 판매·추가 입고(`DEMAND`) → 30분 연장 | `GroupBuyStateWorkerTest.G12_…` |
| G13 · 결정 42·46·69 | 25,000 × 3 무료배송 발송 전 2개 취소 → 47,000 환불(재부과 3,000), `reshipping_fee_charged` 3,000, 나머지 취소는 남은 결제액 전부 | `ParticipationCancelTest.G13_…` |
| G14 · 결정 44 | 한 요청에 하자 줄 + 변심 줄 · 하자 A 먼저 → 변심 B 마지막(편도·왕복) → 반품비 운영자·줄별 금액·43 맞춤 | `GroupBuyRefundCalculatorTest.G14_…` · `GroupBuyRequestTest.G14_…` |
| G15 · P41 | 보상 든 PG 취소 뒤 불변식(39) · 보상 > 남은 결제액 → 넘는 몫 계좌 송금·송금 완료 뒤 계좌 삭제 · 상한 · 변심엔 없음 | `GroupBuyRequestTest.P41_G15_…` · `G15_보상이_…` |
| G16 · 결정 48 | 단품 한도 합 < 공구 최대에서 단품 모두 다 팔림 → 즉시 CLOSING(SOLD_OUT) | `GroupBuyPaymentTest.G16_…` |
| G17 | 결제 확정 ∥ 중단 · 판정 · 반품 승인 동시 → 교착 없음, 확정이 이기면 환불 워커가 환불 / 승인 ∥ 운영 환불 · 반품 완료 ∥ 운영 환불 · 승인 ∥ 중단 환불 워커(4번씩) → 환불 기록 합 = 누계 = 성공한 PG 취소 합 ≤ 결제액 | `GroupBuyPaymentTest.G17_…` · `GroupBuyRequestConcurrencyTest`(셋) |
| G18 · 결정 16·40·74 | 결제 다시 열기 선점 안 늘어남 · 자동 취소의 `onCanceled` 는 `refunded_amount` 그대로 · MANUAL(콘솔 취소) 완료 표시·이벤트·대사 SUCCEEDED 합 | `GroupBuyPaymentTest.P16_…` · `P3b_…` · `GroupBuyAdminRefundTest.G35_G18_…` |
| G19 · 결정 46 | 일부 줄 취소 뒤 상태 · 전부 취소 뒤 CANCELED · 그 뒤 새 주문 | `GroupBuyJoinTest.진행_중_주문은_…_G19` · `ParticipationCancelTest.G13_…` |
| G20 · 결정 28·24·45 | 1인 한도: 반품은 처리 방법과 관계없이 뺌·금액만 운영 환불은 그대로 · `round_limit_shared` · CLOSING 중 본인 취소로 불발 | `GroupBuyJoinTest.한_사람_한도는_…` · `반품은_처리_방법과_…_G20` · `차수_사이_한도는_…_G8_G20` · `GroupBuyStateWorkerTest.결정45_G20_…` · `GroupBuyRequestTest.G20_…` |
| G21 · P35 · 결정 8·20·38 | 휴일 낀 2·3영업일 경고(단계마다 한 번) · CSV 수식 주입 `'` · GAVE_UP 중 같은 금액 두 번 → 거절 | `GroupBuySupplyTest.G21_P35_…` · `P35_…` · `GroupBuyNotificationsTest.G21_P35_…` · `GroupBuyFulfillmentTest.G21_…` · `GroupBuyAdminRefundTest.G35_G21_…` |
| G22 · 결정 37 | 웹훅 확정 ∥ 선점 만료 워커 · ∥ 결제 다시 열기 · ∥ 중단의 결제 중 주문 → 교착 없음(결제 행 먼저), 하나만 이김, RELEASE 한 번 | `GroupBuyPaymentTest.G22_…`(둘) |
| G23 | 판정 ① ∥ 마지막 확정(확정 = min_qty) → 성사 | `GroupBuyStateWorkerTest.G23_…` |
| G24 · 결정 61 | 불발 300건 · 환불 워커 중간 종료 → 상태 워커 주기 유지, 바퀴당 50·이어 받기·중복 없음 | `GroupBuyStateWorkerTest.G24_…` |
| G25 · 결정 62 | `onPaid` 도메인 사정(한도·유일 충돌·상태 불일치·불변식 깨짐) → 예외 없이 REJECT, 만료·RELEASE 한 번, 이탈 안 셈 | `GroupBuyPaymentTest.G25_…` |
| G26 · 결정 63 | 남은 선점 50초에 다시 열기 → `HOLD_EXPIRED` · 유효한 READY 가 있으면 같은 결제 | `GroupBuyPaymentTest.G26_…` |
| G27 · 결정 78·77 | 단품 A·B 마지막 1개 동시 확정 → 1분 안에 CLOSING · 한도 없는 단품 구매 → 단품 행 수량 맞음 | `GroupBuyStateWorkerTest.G27_…` · `GroupBuyPaymentTest.한도_없는_옵션이_…` |
| G28 · 결정 81 | 모집 중 본인 취소로 풀림(마감 3분 전) → 수량 풀림 알림 · DEMAND 연장 | `GroupBuyStateWorkerTest.G28_…` |
| G29 · 결정 80 | 추가 입고·줄이기 이력 · 단품 추가 입고가 공구 최대도 움직임 · 한도 없는 단품 거절 · 「추가 입고 N개」 · 사유 없음 거절 · 감사 | `GroupBuyStockTest.G5_G29_…` · `사유가_없거나_…` |
| G30 · 결정 46 | 전체 반품(반품비 남음) 뒤 REFUNDED · 그 뒤 운영 환불 거절 | `GroupBuyRequestTest.G7_G6_…` |
| G31 · 결정 68 | 47,000 + 5,000 무료배송 B 변심 반품: 업체회수 1,000 추가 결제해야 접수 · 고객발송 2,000 환불 · 미결제 하루 뒤 닫힘·결제창 닫힘 · 접수 뒤 거절이면 1,000 취소 | `GroupBuyRefundCalculatorTest.G31_…` · `GroupBuyRequestTest.G31_…` |
| G32 · 결정 42·43 | 발송 전 2/3 취소(재부과 3,000) 뒤 남은 1개 변심 반품 22,000 / 직접 발송 25,000 / 하자 28,000 — 처음부터 1개를 28,000에 산 회원과 같음 | `GroupBuyRefundCalculatorTest.G32_…` · `결정42_…` |
| G33 · 결정 38·71 | 하자 부분 반품 + 보상 4,000 뒤 운영 환불 → 보상 뺀 남은 결제액으로 막힘 · 운영 환불 몫을 반품에서 먼저 뺌 | `GroupBuyRefundCalculatorTest.결정71_…` · `GroupBuyAdminRefundTest.G33_결정71_…` |
| G34 · 결정 70 | 1,200,000원 반품 승인 → 일반 권한·PG 한 번 · 운영 환불 3만 + 8만 → 두 번째부터 고액 권한·승인 대기 · 문턱 0 → 모두 일반 | `GroupBuyAdminRefundTest.G34_…` |
| G35 · 결정 67·74 | GAVE_UP(취소 기간 지남) → 계좌 송금 전환·송금 대기·감사 · MANUAL 콘솔 취소 ID → 누계 반영, 대사 이슈 다시 안 열림 | `GroupBuyAdminRefundTest.G35_G21_…` · `G35_G18_…` |
| G36 · 결정 73 | 콘솔 직접 취소(CANCEL_MISMATCH 열림) 결제에 반품 승인 → `PAYMENT_UNRECONCILED` | `GroupBuyRequestTest.G36_…` |
| G37 · 결정 51·52 | 발송 예정일 필수(성사형)·즉시 발송은 선착순만·정상가 근거 · 기한 지난 발송 전 주문 경고(하루 한 건, 지연 공지의 새 예정일) · 지연 공지 뒤 준비중 주문 즉시 전액(재부과 없음) | `GroupBuyAdminTest.G37_…` · `즉시_발송의_발송_영업일은_…` · `GroupBuyNotificationsTest.G37_…` · `ParticipationCancelTest.G37_…` |
| G38 · 결정 53 | 공급 불가: 그 단품만 산 주문 전액 · 섞인 주문 그 줄만(재부과 없음) · 판매 중지·최대 줄임 · 기한 전 발송 거절 · 전체 취소 · 기한 뒤 나머지만 · 공급 불가 뒤 결제 중 주문 결제 → REJECT(`SUPPLY_FAILED`)·자동 취소, 다른 단품만이면 받음 | `GroupBuySupplyTest.G38_…` · `G38_공급_불가_뒤_…` |
| G39 · 결정 54 | 하자 교환 → 재발송·환불 0·교환 출고·에스크로 다시 · 물건 없음 → 환불 전환(다시 계산)·알림 · 변심 교환 거절 | `GroupBuyRequestTest.G39_…` |
| G40 · 결정 55 | 사유 코드·근거 사진 없는 거절 → 거절 · 제한 문구 없는 공구의 「사용으로 가치 감소」 → 거절 | `GroupBuyRequestTest.G40_…` |
| G41 · 결정 18·56 | 판매 켜짐 + 필수 칸 → 공개 정보에 사업자 정보 · 꺼짐 → 숨김 | `CommerceSalesTest.G41_…` · api `GroupBuyHttpTest.G41_…` |
| G42 · 결정 59·72 | 동의·줄 미선택 거절 · `expectedAmount` 다름 → 409 `AMOUNT_CHANGED` | `GroupBuyJoinTest.G42_…`(둘) |
| G43 · 결정 87 | 카탈로그 40개·키 겹침 없음·원칙 표시 · 빈 항목·공백·양식 없음이면 모집 시작 거절(빈 양식·항목 목록) · 제공 불가 사유 없음 거절(+ DB CHECK) · 어느 양식에도 없는 단품 거절·기본 양식이 덮음 · 단품은 양식 하나·기본 양식 하나·없는 품목·항목 거절 · 모집 뒤 수정 이력·비우기 거절 · 카탈로그 버전 고정(시험 전용 `src/test/resources/groupbuy-test/notice-catalog-2022-01-01.json`) · 「상세페이지 참조」 경고 · 추가 항목 차례·빈 줄 제외·완성 검사 밖·이력 / 같은 이름·21개·겹침·반쪽·41자 거절 · 같은 상품 양식 사용 | `GroupBuyNoticeTest`(12) |
| G44 · 결정 88 | 축 2개 → 6조합·판매 끈 조합 견적·주문 409 · 단품 한도(잠시 구매 불가·그 단품만 품절) · 추가상품만 400 · 공구 최대·1인 한도·성사 판정은 MAIN 만, 무료배송엔 포함 · 본 상품 모두 취소하며 추가상품 남기면 `ADDON_ONLY`·함께면 전체 취소 · 고시는 추가 구성상품의 상품에도 · 모집 뒤 `LOCKED_FIELD options`·추가상품 입고는 공구 최대 그대로 · 등록 검사 | `GroupBuySkuTest`(10) |
| G45 · 결정 89 | 상품 하나로 공구 둘 · 모집 시작 사본(공급가 없음) · 모집 중 상품 이름·구성 잠금(추가 구성상품으로 쓰여도)·설명 정정은 바로·가리키는 단품 지우기 409 · 공급가 빈 값 409·0 허용·역마진 확인 · 회원 응답에 공급가 없음 · 카테고리 2단·필터·숨김·필수 · 단품 목록 정본·옵션 종류 | `GroupBuyProductTest`(8) |
| 결정 41·13 | 선점 끝 = 결제 유효 시각, 배송지 암호문, 주문 번호 | `GroupBuyJoinTest.주문하면_…` · `GroupBuySchemaTest.주문_번호는_…` |
| 10-5 | 자격(권한·본인인증·19세·정지·참여 제한·판매 스위치) | `GroupBuyJoinTest.자격_권한_…` |
| 결정 23·66 | 진행 중 주문 1건 — 동시 두 번 → 하나 | `GroupBuyJoinTest.같은_회원이_동시에_…` · `GroupBuySchemaTest.진행_중_주문은_…` |
| 결정 17·84 | 잠시 품절 / 다 팔림 구분, 마지막 하나 동시 → 하나만 | `GroupBuyJoinTest.마지막_하나를_…` · `다_팔리면_temporary_false_…` |
| 결정 85 | 조회 때 정리는 본인 주문만 | `GroupBuyJoinTest.본인의_지난_선점은_…` |
| 10-3 | 결제 확정 → 성사형 JOINED / 즉시 발송 CONFIRMED | `GroupBuyPaymentTest.성사형은_결제되면_…` |
| 결정 27 | 다 팔림 즉시 CLOSING | `GroupBuyPaymentTest.결정27_…` |
| P3 · P3b | 결제 중 마감 → 받고 판정 포함 · 판정 뒤 결제 → REJECT·자동 취소·만료·RELEASE 한 번·이탈 안 셈 | `GroupBuyPaymentTest.P3_…` · `P3b_…` |
| P15 · P16 | 선점 만료 직후 결제 → 살리지 않음(결제 행 먼저) · 결제창 실패 뒤 재결제 → 같은 주문, 선점 안 늘어남 | `GroupBuyPaymentTest.P15_…` · `P16_…` |
| 결정 28 · 10-5 | 선점 반복 이탈 24시간 3번 → 참여 제한, HOLD_TIMEOUT 만 셈 | `GroupBuyPaymentTest.선점_반복_이탈_…` |
| P16b·P16c·P16d · 결정 25 | 연장, 결제 중 선점이 남으면 연장 | `GroupBuyStateWorkerTest.P16b_…` · `P16c_…` · `P16d_…` · `DEMAND_마감_시각에_…` |
| 2b-2 정한 세부 2 | 예정 공구는 시작 시각에 워커가 모집 시작·그때 배송비 복사 | `GroupBuyStateWorkerTest.예정_공구는_…` |
| 판정 · P25 | 성사 → CONFIRMED 일괄, 불발 → 환불 워커 전액·CANCEL·기록 완료 · 성사 뒤 최소 아래로 내려가도 성사 유지 | `GroupBuyStateWorkerTest.판정_성사면_…` · `P25_…` |
| 결정 9 | COMPLETED sweep | `GroupBuyStateWorkerTest.즉시_발송_성사_공구는_…` |
| 10-3 표 · 결정 42 | 성사형은 모집 중 즉시 취소·성사 뒤 요청 · 즉시 발송은 준비중 전까지 · 배송비 낸 주문 재부과 없음 · 남의 주문 404 · 가진 수량 초과 400 | `ParticipationCancelTest.성사형_JOINED_…` · `즉시_발송은_…` · 나머지 |
| 모집 시작 · 결정 10 | 배송비 사본·처음 수량·이력 1·감사, 기간 상한·정책 없음 거절 | `GroupBuyAdminTest.모집_시작은_…` · `기간이_설정_상한을_…` |
| 잠금 필드 | 모집 뒤 LOCKED_FIELD·수정 이력·낙관 잠금, DRAFT 는 모두 | `GroupBuyAdminTest.모집_뒤에는_…` · `공개_전_DRAFT_는_…` |
| 결정 29 · 79 | 중단 ① → ② 환불 워커(발송 전만·결제 중 닫음·발송 그대로·다시 돌려도 한 번) · 종료 사유 넷·조기 마감 `EARLY_CLOSED` | `GroupBuyAdminTest.결정29_…` · `조기_마감은_…_EARLY_CLOSED` · `GroupBuySchemaTest.종료_사유는_…` |
| P16e | 배송비 예약 인상 · 지난·앞선 예약 거절 · 적용 시작 줄 불변·기간 겹침 금지(DB) | `GroupBuyAdminTest.P16e_…` · `지난_시각이나_…` · `적용이_시작된_…_DB` |
| 결정 65·66 | V101 제약(부분 유일·NULLS NOT DISTINCT·복합 FK·금액·수량·방식 CHECK)·불변식 한 SQL | `GroupBuySchemaTest` |
| P36~P42 · 10-8 | P36·P37·P37b·P37c·P37d·P38·P38b·P39·P40·P41·P42 기준 표 | `GroupBuyRefundCalculatorTest` 해당 이름 |
| P33·P34 · 2b-3 정한 세부 3·16 | 반품 기한 · 「안 날부터 30일」(3개월 뒤 업체 부담 받음·안 날 없음·31일째 409·미래·배송 전 400·변심 7일·안 날 저장·`OUT_OF_WINDOW` 감사) | `GroupBuyRequestTest.P33_P34_…` · `P33_보강_…` |
| 10-3 · 2b-3 정한 세부 7 | 발송 전 취소 요청 승인(줄·재부과)·열린 요청 하나·「이미 발송함」 거절은 송장으로 발송 | `GroupBuyRequestTest.발송_전_취소_요청은_…` |
| 결정 72 | 미리 보기 ↔ 승인 | `GroupBuyRequestTest.결정72_…` |
| 결정 88 | 반품도 추가상품만 남기기 금지 · 철회 뒤 다시 요청 | `GroupBuyRequestTest.반품도_…` |
| 결정 69 | 지연 취소 전액이면 열린 요청 SUPERSEDED | `GroupBuyRequestTest.결정69_…` |
| 결정 32 | 운영 환불(발송 전 = 취소·배송 중 거절·배송완료 금액만·REFUNDED·남은 결제액 초과 거절) | `GroupBuyAdminRefundTest.결정32_…` |
| 결정 11 | 택배사 시드·이력 있으면 지우기 금지·주소 규칙 · 송장 정리 | `GroupBuyFulfillmentTest.택배사는_…` · `송장은_…` |
| 결정 47·43 · 10-3 | 발송 일괄 행 결과(열린 요청·택배사·송장) · 처음 배송비 고정 | `GroupBuyFulfillmentTest.발송_일괄은_…` |
| 결정 7·9 | 발주 → 준비중 → 발송(SHIPPING) → 배송완료 → COMPLETED | `GroupBuyFulfillmentTest.마감_후_발주_공구는_…` |
| 결정 64·14 | 에스크로 커밋 밖·재시도 표·송장 정정 PATCH · 6번 실패 GAVE_UP · 구매 확정 7일·열린 요청이면 안 함 | `GroupBuyFulfillmentTest.에스크로_…`(둘) · `결정14_…` |
| 10-9 | 가린 값·운영자 열람(사유·기록·감사에 평문 없음·10분)·본인 보기 | `GroupBuyFulfillmentTest.가린_값과_…` |
| 결정 57 · P27 | 계약 내용 통지 메일 — 결제 확정 트랜잭션의 대기열에 한 통(다시 처리해도 한 통) | `GroupBuyNotificationsTest.결제가_확정되면_…_P27` |
| 결정 16 | 환불 완료 알림·메일 | `GroupBuyNotificationsTest.환불이_끝나면_…` |
| 결정 12·40 | 불변식 거절 = 운영자 주문 이상(주문마다 묶음), 도메인 거절은 회원 안내만·메일 한 통 | `GroupBuyNotificationsTest.불변식이_깨진_거절은_…` |
| 결정 81 · 2b-4 정한 세부 3 | 수량 풀림 — 둘 다 남을 때만·10분에 한 번(선점 만료 포함) · 연장 알림·마감 임박 알림 | `GroupBuyNotificationsTest.수량_풀림은_…` · `모집이_연장되면_…` · `마감_임박_알림은_…` |
| 결정 52·53·29 | 지연 공지 앱 + 메일(발송된 주문 제외) · 공급 불가 선택 앱 + 메일 · 중단 회원·운영자 알림 · 요청 접수 운영자 알림(본인 철회는 회원 알림 없음) | `GroupBuyNotificationsTest.발송_지연_공지는_…` · `공급_불가로_…` · `중단은_…` · `요청이_접수되면_…` |
| 20절 · 2b-4 정한 세부 9 | 판매 스위치 — 설치 직후 꺼짐·켜기는 최고 관리자·자동 조건 + 체크리스트·감사·알림·끄기는 설정 권한자도·사유 | `CommerceSalesTest.설치_직후에는_…` · admin `GroupBuyAdminHttpTest.판매_켜기는_…` |
| 결정 19 | 판매 스위치는 트랜잭션 안 DB 값 · 꺼지면 주문 403·단추 `SALES_CLOSED` | `CommerceSalesTest.판매_스위치_구현은_…` · api `GroupBuyHttpTest.판매가_꺼지면_…` |
| 결정 18 | 사업자등록번호 체크섬·칸 길이·전자우편, 바뀐 칸 이름만 감사 | `CommerceSalesTest.사업자등록번호는_…` · admin `GroupBuyAdminHttpTest.판매_켜기는_…`(400 `values.businessNo`) |
| 결정 10 | 공구 설정 범위·바뀐 칸만 감사·휴일 정리·업무가 바로 읽음 | `CommerceSalesTest.공구_설정은_…` |
| 결정 2 | 상품 설명 정화 · 남이 올리지 않은 이미지 키 거절 | `CommerceSalesTest.상품_설명은_…` · admin `GroupBuyAdminHttpTest.결정2_…` |
| 결정 34 · 10-9 | 개인정보 파기(배송지·만료·열린 요청 미룸 / 수정 이력 / 요청 3년) | `GroupBuyPrivacyCleanupBatchTest`(4) |
| P26 | 같은 멱등 키·다른 본문 409, 키 없음 400, 같은 본문은 처음 응답 · `GROUPBUY_JOIN` 1분 5번 넘으면 429 | api `GroupBuyHttpTest.P26_…` · `주문_요청_제한_…` |
| 공개 API | 로그인 없이 목록·상세, 글자 ID, 공급가 없음 · 남의 주문 404 · 관심 신청은 서버가 허락한 때만 · 반품 요청은 처리 시각·회수 송장이 없어도 만들어지고 내 주문에 보임 | api `GroupBuyHttpTest.목록과_상세는_…` · `관심_신청은_…` · `반품_요청은_처리_시각과_회수_송장이_아직_없어도_만들어지고_내_주문에_보인다` |
| 결정 35 | 권한(조회·관리·중단·수량 조정·환불·준비중·내보내기 = export + address.reveal·고액 승인 대기·설정)·`featureReady`·메뉴 서버 = 화면 | admin `GroupBuyAdminHttpTest.결정35_…`(둘) · `CommercePermissionsAndMenuTest` · `Commerce2aCatalogTest` |
| 대시보드 | 볼 권한이 있는 칸만 | admin `GroupBuyAdminHttpTest.대시보드_…` |
| 결정 74 | 결제 수동 처리 방법(콘솔 취소면 번호 필수·모르는 방법 400) | admin `GroupBuyAdminHttpTest.결정74_…` |
| 워커 | 배치를 켠 서버만·워커 따로·워커 기록·가린 오류 · 기한 점검 | admin `GroupBuyStateSchedulerTest` · `기한_점검은_…` |
| 결정 11·87 | local 시드(조건 검사 통과해 켬·예시 품목 여덟 고시 완성·공구 여덟·이름으로 가리는 단품 여럿) | admin `GroupBuyLocalSeederTest` |
| 홈 가젯 | 모집 중만·마감 순·줄 모양 / 공개 전·집계 중·숨긴 카테고리 제외 / `count` 1~10·기본 5 / 판매 꺼짐 | api `HomeGadgetGroupBuyTest`(4) |

## 2b-5 로컬 실제 확인 (포트원 테스트 채널 KG이니시스 V2 카드 — 결제만 포트원, 본인인증은 가짜)
| 확인 | 결과 |
|---|---|
| 공구 결제(두 줄 42,800원) | PAID, 주문 확정 |
| 이니시스 카드 **부분 취소 연속 2회**(발송 전 본인 취소: 한 줄 19,900 → 남은 줄 + 배송비 22,900) | PG 취소 2건 모두 첫 시도 SUCCEEDED, 결제 PARTIAL_CANCELED → CANCELED, 주문 CANCELED·누계 42,800 |
| 공구 중단 → 환불 워커(마감 후 발주 69,000원) | REFUND SUCCEEDED, 결제 CANCELED, 주문 REFUNDED |
| 발송 → 배송완료 → 단순 변심 반품(업체 회수) → 승인 → 회수 확인 → 완료(보관 1) | 환불 21,900(24,900 − 반품비 3,000), PG 부분 취소 SUCCEEDED |
확인하며 고친 것(규칙은 해당 절에 반영): `?items=` 단품 ID 19자리까지 · 결제창 구매자 정보는 평범한 객체로 · 반품 요청 NPE(처리 시각·회수 송장 null) · 가짜 게이트웨이 `fake-checkout` 은 본인 결제 주문이면 종류와 관계없이 받는다(결제 계약).

## 남은 일
- **2b-5 dev 서버 확인**(공개 주소 필요): 웹훅(`Transaction.Paid`·`Cancelled`) · 에스크로 배송 등록·구매 확정(결정 4·14·64 — 실제 응답, 구매 확정 전·후 부분 취소가 PG 에서 막히는지 — 막히면 반품 환불은 계좌 송금(67)) · 네이버페이·카카오페이 부분 취소 연속 2회 · PG 가맹점 취소 한도(불발 대량 환불) · 콘솔 직접 취소(결제 P18 `CANCEL_MISMATCH` → 결정 73).
- **법무 확인**(commerce.md 20-2 목록): 공정위 사업자정보공개 링크 의무(56) · 소비자분쟁해결기준·표시광고 고시 현행 원문 · 「한정 N개」 문구 공구에 추가 입고·다음 차수를 열 때 표시·광고 문제(17·58) · 단순 상품 문의 3년 보관(Q&A) · 반품비 부족분 선결제(68) · 고시 위반 과태료 근거 조문·금액(87).
- **가정·사용자 확인 대기**: 리뷰 보상 없음 · 리뷰 지운 뒤 같은 줄 다시 못 씀 · 답글 수정 이력 없음 · 숨긴 남의 리뷰는 목록에서 빠짐 · 지운 리뷰 본문 보관 기간 · Q&A 쓰기 간격 30초 · 신고 간격·하루 한도 없음 · 「2b-4 정한 세부」(가정으로 보고) · 사이트 푸터 모바일 자리(56).
- **구현·시험 남음**: 고액 운영 환불 승인 대기의 승인·거절 화면 단추(API 있음) · 대시보드 「기한 지난 Q&A」 칸 연결(60) · 붙지 않은 리뷰 사진 정리 배치 · 지운 사진 파일 고아 정리(34) · 시험 없음: `GROUP_BUY_BAN` 이 질문을 막지 않음(코드만), 「이전 차수 문의 보기」 · 결정 73 결제 모듈 후속 · 홈 가젯 관리자 편집기 `count` 칸.

## 옛 절 이름 → 새 위치 (코드 주석이 옛 이름을 가리킨다)
| 옛 절 | 새 위치 |
|---|---|
| 「화면 목록」·「2b-1 화면 목업」(API·추가분 고시/단품/상품) | 「화면 목록」·「API」·결정 87~89 |
| 「마이그레이션 계획」·「최종 검토로 더하는 열·표」 | 「마이그레이션 계획」(열 요점) |
| 「세션 정리 결정」·「수량·상태 규칙」·「2차 검토 보강」·「최종 검토 보강」 | 「결정」 1~89(번호 그대로) |
| 「상품 단품 목록이 정본」·옵션 종류 코드 세션 결정 | 결정 88 |
| 「2b-2 / 2b-3 / 2b-4 구현 결과」의 정한 세부·「2b-3 보강」 | 「구현에서 정한 세부」(번호 그대로) |
| 「2b-4 결과」(가정) | 「구현에서 정한 세부」 2b-4 |
| 「2b-6 결과」(신고 저장소 세션 결정) | 「Q&A 규칙」 신고 |
| 「감사·오류 코드 후보」 | 「감사·오류 코드」 |
| 「새 규칙 → 테스트」·단위별 「규칙 → 테스트」 | 「규칙 → 테스트」 |
| 「2b-5 로컬 실제 확인」 | 같은 이름 |
| 「2a 보강(고침)」 | `commerce-payment-contract.md` 「2a 보강 — 취소 호출을 잠금 밖으로」 |
