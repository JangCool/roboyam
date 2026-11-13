# 상품 가격(요금표) 예약 변경 — 계약

광고·핫딜 상품 9개의 요금표(`biz_price_rules`)를 관리자가 고친다. 단가·동시 한도·최대 일수·시작일 선택 범위는 **상품마다 적용 시각을 정해 예약**하고(한 상품에 여러 건), 신청 받기 켬/끔만 바로 바뀐다. 사업자 신청 화면은 가장 가까운 예약 변경을 미리 알린다. (화면 확정 2026-11-13)

## 화면
- admin-web `/ads/placements?tab=prices` — `pages/ads/placements.vue` + `components/ad/AdPriceRuleRow.vue`(상품 카드) · `AdPriceScheduleDialog.vue`(한 상품 예약 만들기·수정) · `AdPriceBulkDialog.vue`(여러 상품) · `AdPriceProductPreview.vue`·`AdPriceSlotDiagram.vue`(미리보기, 서버 없음). 오른쪽 열 = 다가오는 예약.
- user-web 사업자 광고·핫딜 신청 화면(`pages/business/ads/new.vue`·`deals/new.vue`) — 상품마다 「○부터 하루 60,000원으로 올라요」, 예상 금액 옆 요약(`BusinessPriceChangeNote`·`BusinessPriceChangeNotice`).
- 화면 타입 정본: admin `composables/ad/api/useAdApi.types.ts` 「상품 가격」 절, user `composables/business/api/useBusinessApi.types.ts` `BizScheduledPriceChange`.

## 규칙
1. **어느 가격인가 = 사업자가 신청을 낸 시각.** 신청 건 견적(`biz_applications.quote_*`)은 그때 고정된다(지금과 같음). 승인·결제가 적용 시각 뒤여도 그대로.
2. **가격 기간표 — 배치 없음(사용자 결정 2026-11-13).** 예약 줄은 「이 시각부터 이 값」이고 적용 시각이 지나도 지우지 않는다. 신청 검사·견적·한도·일수는 「적용 시각 ≤ 지금인 가장 늦은 줄, 없으면 요금표 줄 값(설치 기본값)」을 읽으므로 적용 시각부터 정확하다. 지난 줄은 「언제부터 얼마였나」 기록이자 상품 카드 「최근 변경」의 원천.
3. 예약 하나는 값 넷(`unitPrice`·`capacity`·`maxDays`·`maxLeadDays`)을 통째로 가진다. 같은 상품에 같은 적용 시각은 하나만. 적용 시각은 지금보다 뒤(지나면 400). 고치기·취소는 아직 적용 전인 예약만.
4. 칸 범위 = DB CHECK 와 같다: 단가 0 이상 정수(0 = 무료), 한도 `DAY` 1 이상 · `POST` 늘 null, 최대 일수·시작일 선택 범위 1 이상.
5. **여러 상품 한꺼번에**: 고른 상품마다 예약 1건, 단가만 바꾼다(나머지 칸은 그 시각 직전 값). 기준 = 각 상품의 지금 단가. 비율 −90~300% 또는 정액(원), 결과 10원 단위 버림, 0원 아래 금지. 하나라도 안 되면 전부 안 된다.
6. 신청 받기 켬/끔은 바로 적용(긴급 스위치) — 끄면 새 신청만 409 `BIZ_PRODUCT_CLOSED`, 들어온 건·노출은 그대로.
7. 한도를 줄여도 이미 잡힌 신청 건은 그대로. 새 한도를 넘는 날은 새 신청만 받지 않는다(지금 슬롯 검사 그대로).
8. 운영자 직접 등록 광고에는 최대 일수·시작일 선택 범위·받기 꺼짐이 적용되지 않는다(ad-contract 그대로).

## 권한·기록
- 읽기 `ad.read`, 바꾸기 `ad.settings`(서버 `Permission.AD_SETTINGS`).
- 감사: `AD_PRICE_SCHEDULE_CREATE` · `AD_PRICE_SCHEDULE_UPDATE` · `AD_PRICE_SCHEDULE_CANCEL` · `AD_PRICE_ACCEPTING_UPDATE`(여러 상품·모두 취소는 한 건).
- 변경 이력(설정 이력 서랍, 광고 자리 이력과 같은 모양)은 관리자가 한 일만 남는다(출처 ADMIN). 칸 코드: `schedules.<상품>.effectiveAt` · `schedules.<상품>.<칸>` · `rules.<상품>.accepting`(받기). 예약이 적용되는 순간은 이력 줄이 아니라 기간표 줄로 보인다. 카드에는 예약자·예약한 때를 보이지 않는다(이력에만).

## API (admin `/api/admin/ads`)
| 방법 | 주소 | 본문 → 응답 |
|---|---|---|
| GET | `/price-rules` | → `AdPriceRulesView`(상품 9줄: 지금 값 · `schedules[]` 아직 적용 전인 줄, 적용 시각 차례 · `recentChanges[]` 최근 3건(적용 시각이 지난 줄 중 직전 값과 다른 칸 + 받기 켬/끔, 사람 = 그 줄을 마지막으로 손댄 운영자) · `occupancy[]` `{peak, days}` 오늘부터 시작일 선택 범위 안 날짜별 최대 동시 겹침 분포, `POST` 는 빈 목록) |
| POST | `/price-rules/{product}/schedules` | `{effectiveAt, unitPrice, capacity, maxDays, maxLeadDays}` → View |
| PUT | `/price-rules/{product}/schedules/{id}` | 같은 본문 → View |
| DELETE | `/price-rules/{product}/schedules/{id}` | → View |
| DELETE | `/price-rules/schedules` | 모두 취소 → View |
| POST | `/price-rules/schedules/bulk` | `{effectiveAt, products[], adjust:{kind: PERCENT\|AMOUNT, value}}` → View |
| PATCH | `/price-rules/{product}/accepting` | `{accepting}` → View |
| GET | `/price-rules/history?page=&size=` | → 설정 이력 쪽 |

400 칸 오류: `fields.effectiveAt`(지남·같은 시각 중복) · `fields.unitPrice|capacity|maxDays|maxLeadDays` · `fields.products` · `fields.adjust.value`. 없는 예약·상품 404.

user(api) `/api/business/ad-products` · `/api/business/deal-products` — 상품마다 `scheduledChange: {effectiveAt, unitPrice, maxDays, maxLeadDays} | null`(아직 적용 전인 가장 가까운 예약 하나, 노출 자리 상품 포함).

## 구현(2026-11-13)
- shared V208 `biz_price_schedules`(가격 기간표 — 상품·적용 시각 유일, 지난 줄도 남음) · `biz_price_rule_history`(source ADMIN 만, `product` 는 받기 줄만). 배치 없음(처음 만든 R__batch_44 는 기간표로 바꾸며 뺐다). 다음 번호 shared V209.
- 읽기 `BizPriceRuleStore.effectiveAt` = 상품마다 적용 시각 ≤ 지금인 가장 늦은 줄 하나(상관 하위 질의, 유일 색인) — 신청 검사·견적·슬롯·운영자 등록 모두 같은 값. 요금표 줄(`biz_price_rules`)의 값 넷은 설치 기본값으로 남고 신청 받기만 바로 바뀐다.
- 지난 줄은 고치기·취소 404(모두 취소도 건드리지 않는다). 직전 값과 같은 칸만 가진 지난 줄은 최근 변경에 보이지 않는다. 화면 「마지막 변경」 = 가장 늦게 적용된 줄의 적용 시각·운영자와 받기 이력 중 늦은 것.
- 승인 때 다시 계산해도 **이미 있는 줄의 단가를 유지**(`BizApplicationMemberService.items`) — 규칙 1.
- 오류: 없는·다른 상품의·이미 지난 예약 404 `AD_PRICE_SCHEDULE_NOT_FOUND`(화면은 목록을 다시 읽는다) · 모르는 상품 404 `AD_PRICE_PRODUCT_NOT_FOUND` · 일괄 결과 0원 미만·조정 값 0·소수 400 `fields.adjust.value` · `fields.adjust.kind`.
- 감사 대상 종류 `AD_PRICE_RULES`(대상 ID = 상품, 일괄 `bulk`, 모두 취소 `all`), 보관 MONEY.

## 미확정·가정
- 사업자 화면은 예약이 여러 건이어도 가장 가까운 하나만 안내한다.
- 최근 변경은 상품마다 3건(서버가 자른다).
- 미리보기 도식(핫딜 글이 목록 셋째 줄 등)은 화면 그림 가정 — 서버와 무관.
