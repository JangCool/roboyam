# 사업자 신청 건 계약 — 광고·핫딜 사전 심사 → 결제 (화면 확정 2026-10-30 · 서버 구현·화면 연결 2026-10-31 — V112)

**의도**: 사업자 광고와 사업자 핫딜은 모두 **사전 심사를 신청**하고, 운영자가 승인한 뒤에만 돈을 받는다. 결제는 온라인(`payment` 모듈) 또는 수동(계약서 사본 + 입금 확인) 두 길이고, 둘 다 확정되면 예약 → 시각에 맞춰 자동 시작·종료한다.
**정본·근거**: `docs/plans/biz-ad-hotdeal/00-overview.md` **★ 절**(★-1~★-5 — 사용자 확정 2026-10-30). 분석 `02-ad.md`·`03-hotdeal.md`.
**프론트 기준**: admin-web `pages/biz-applications.vue`, `components/bizapplication/**`, `composables/bizapplication/api/useBizApplicationApi.ts`(+types·mock) / user-web `pages/business/ads/new.vue`·`deals/new.vue`·`applications/index.vue`·`applications/[id].vue`, `composables/business/api/useBusinessApi.ts`.
**커머스 규칙 적용**: 돈이 움직이므로 `docs/domain/commerce.md` 18절(설계 규칙은 모두 테스트로 막고 `--tests` 로 통과해야 완료)을 따른다.

## 화면 경로
- 관리자 `/biz-applications` — 큐 탭(심사 대기·결제 대기·입금 확인 대기·입금 기한 지남·예약·진행·환불 심사·종료/반려/취소) + 종류 필터 + 3단(목록 · 내용+점검표 5개 · 미리보기). 메뉴 묶음 「사업자」(`business.read`).
- 공개 `/business/ads/new`(5단계) · `/business/deals/new` · `/business/applications`(상태 묶음 + 카드) · `/business/applications/[id]`(단계 표시·결제·입금 안내·취소·환불 요청·처리 기록).

## 종류
| `kind` | 무엇 | 비고 |
|---|---|---|
| `AD_BANNER` | 배너(유형 WIDE·STRIP·SQUARE·SMALL) | 이미지·https 링크·대체 글 |
| `AD_TEXT_LINE` | 한 줄 광고(직접홍보 한 줄 포함) | 문구 40자·링크 |
| `HOTDEAL_POST` | 사업자 핫딜 글 게시 | 딜 정보 + 선택 노출 줄(아래) |
| `HOTDEAL_PLACEMENT` | 이미 게시된 사업자 핫딜에 노출만 추가 | 노출 자리 `HOME_TOP`·`CATEGORY_TOP` |

**세션 정리 결정(2026-10-30)**: 새 핫딜을 신청하면서 노출을 고르면 **한 신청 건(`HOTDEAL_POST` + 노출 줄)으로 한 번에 심사·결제**한다(user-web 목업 그대로). 게시된 글에 나중에 노출을 붙일 때만 `HOTDEAL_PLACEMENT` 를 따로 낸다(admin 목업 그대로). 한도 판정은 두 경우 모두 노출 자리 기준. 회원 핫딜(일반 회원 글)은 심사하지 않는다.

## 상태
`SUBMITTED`(심사 대기) → `REJECTED` / `CANCELED`(사업자) / `AWAITING_PAYMENT`(승인·온라인) / `AWAITING_DEPOSIT`(승인·수동) · `AWAITING_PAYMENT` → `SCHEDULED`(결제 완료) / `EXPIRED`(72시간) / `AWAITING_DEPOSIT`(수동 전환) · `AWAITING_DEPOSIT` → `SCHEDULED`([예약 확정]) / `CANCELED` · `SCHEDULED` 안에서 `RUNNING`·`ENDED` 는 시각으로 계산 · `SCHEDULED` → `TAKEN_DOWN`(긴급 내림·사업자 정지) ↔ 되돌리기 · → `CANCELED`(환불).
- 운영자가 직접 등록하는 건은 `AWAITING_DEPOSIT` 에서 시작한다(광고 `ad-contract.md` 수동 등록).
- **세션 정리 결정**: 환불 요청은 상태가 아니라 신청 건의 `refund` 하위 값(`REQUESTED`·`APPROVED`·`DENIED`, 요청 금액·제안 금액·사유)이다. 관리자 「환불 심사」 큐 = `refund.status = REQUESTED`. user-web 목업의 `REFUND_REQUESTED` 상태는 API 연결 때 이 모양으로 바꾼다.
- 승인 뒤 결제 전에 사업자가 소재를 바꾸면 `SUBMITTED`(재심사). 예약 확정 뒤 소재 교체는 운영자 소재 교체 심사 → 새 소재 버전(옛 버전 보존).

## 규칙
- **자리(한도)**: 승인하는 순간 `AWAITING_PAYMENT`·`AWAITING_DEPOSIT` 도 자리를 차지한다. 승인 때 유형별 advisory lock 아래 다시 판정, 꽉 차면 409 `BIZ_SLOT_FULL`(관리자·공개 같은 코드 — 목업의 `AD_SLOT_FULL` 은 연결 때 바꾼다). 기간은 반열림 `[시작, 끝)`, 분 단위, KST.
- **금액**: 요금표(종류 × 하루 단가 × 일수) + 부가세 10%. 운영자가 승인 때 공급가를 조정하면 사유 필수(`fields.adjustReason`).
- **기한**: 온라인 결제 72시간(설정값) — 지나면 배치가 `EXPIRED`·자리 반납·알림. 수동 입금 기한은 운영자 지정(기본 7일) — 지나도 자동 취소하지 않고 「입금 기한 지남」 큐에서 연장·취소.
- **시작이 지난 신청**(F6): 승인 창에서 「같은 길이로 뒤로 옮기기」, 그대로는 400 `fields.period`.
- **수동 결제 확정**: 계약서 사본 1장 이상 + 입금 확인(입금액·입금일·입금자명·확인자) **둘 다** — 서버 CHECK, 아니면 409 `BIZ_DEPOSIT_INCOMPLETE`. 입금액 ≠ 확정 금액이면 사유 필수. 계약서는 원본 보존(바꾸면 옛 파일은 「대체됨」), PDF·JPG·PNG 10MB 여러 장, 열람 기록, 보존 5년.
- **사업자 취소**(세션 정리 결정): `SUBMITTED`·`AWAITING_PAYMENT`·**`AWAITING_DEPOSIT`(입금 확인 전)** 은 사업자가 바로 취소(돈 이동 없음). user-web 목업의 「운영팀 문의」 안내는 연결 때 [신청 취소]로 바꾼다.
- **환불**(H1): 시작 전 예약 = 전액 자동(온라인은 `payment` 취소, 수동은 환불 기록 + 운영자 송금 표시) · 진행 중 = 운영자 심사(남은 기간 일할 제안) · 운영 귀책 중단은 끝 시각 연장 또는 일할 환불(F5) · 정책 위반 중단은 환불 없음(사유 코드로 구분).
- **결제 모듈**: `PaymentOrderType.BIZ_APPLICATION` + `PaymentOrderHandler`(`onPaid` → `SCHEDULED`, `onCanceled` → 환불 기록). 웹훅·취소 재시도·대사는 `payment` 가 맡는다. 늦은 결제·금액 불일치는 커머스처럼 자동 취소.
- 정책 동의(광고 정책·환불 규정, 버전) 없이는 신청 400.

## 권한·감사·알림
- 관리자: 조회 `business.read`, 심사·승인·반려·수동 전환·계약서·입금 확인·확정·취소 `ad.manage`, 환불 승인 `deal-order.refund`(SENSITIVE — 이름은 기존 키 재사용, 백엔드에서 확정).
- 감사: `BIZ_APP_APPROVE`·`_REJECT`·`_MANUAL_SWITCH`·`_CONTRACT_UPLOAD`·`_CONTRACT_VIEW`·`BIZ_DEPOSIT_CONFIRM`(돈 — 5년)·`_CONFIRM`·`_CANCEL`·`_REFUND_APPROVE`·`_REFUND_DENY`·`_PRICE_ADJUST`.
- 사업자 알림(F9): 접수·승인(결제 요청 — 금액·기한)·반려·결제 기한 24시간 전·결제 완료·시작·종료 임박·중단·환불. 관리자 알림·대시보드(F10): 심사 대기·입금 확인 대기·입금 기한 지남·환불 심사.
- 배치(admin `BatchCatalog`): 결제 기한 만료, 결제 기한 임박 알림, 시작·종료 임박 알림.

## API 후보
목업 머리말이 정본 후보 — admin `useBizApplicationApi.ts`(`/api/admin/biz-applications/**`: 목록(큐별 건수)·상세·availability·approve·reject·manual·contracts·deposit·confirm·deposit-deadline·cancel·refund approve/deny) / user-web `useBusinessApi.ts`(`/api/business/ad-products`·`deal-products`·`availability`·`applications/{ads|deals}`·`applications[?bucket=]`·`{id}`·`cancel`·`payment`·`refund-request`, 409 `BUSINESS_NOT_APPROVED`·`BUSINESS_RE_REVIEW`·`BIZ_SLOT_FULL`·`BIZ_APPLICATION_STATE_CHANGED`).

## 저장 (제안 — shared 스트림)
`biz_applications`(종류·상태·기간·금액 스냅샷·결제 방식·기한·자리 키) · `biz_application_items`(핫딜 노출 줄) · `biz_creative_versions`(소재 버전) · `biz_contract_files` · `biz_deposits` · `biz_refunds` · `biz_application_events`(처리 기록) · 요금표 `biz_price_rules`.

## 미확정
- 진행 중 환불의 일할 계산 기준(시간·일)과 수수료 — 법무·정책.
- B2B 디지털 서비스의 청약철회 적용 범위(전자상거래법) — 법무.
- 세금계산서: 수동 표시로 시작(전자 연동은 3차).
- 월 이용권·묶음권은 3차 검토.

## 서버 구현 결정 (2026-10-31 — 계약과 다르게·더 정한 것)
API 요청·응답 모양 전체는 서버 DTO(admin `bizapplication/dto/BizAdminView`·`BizAdminRequest`, api `bizapplication/dto/BizMemberView`)가 정본이다. 아래는 규칙만.
- **마이그레이션** shared `V112__biz_applications.sql`: 계약의 표 8개 + CHECK 합집합(`payment_orders` 종류 ∪ `BIZ_APPLICATION`, 회원 알림 ∪ `BIZ_APPLICATION`, 파일 용도 ∪ `BIZ_AD_IMAGE` — ⚠ 합칠 때 다른 세션의 같은 CHECK 와 합집합 확인). 구현은 JDBC 저장소(커머스·공구 선례), 패키지 `com.community.bizapplication`.
- **자리 줄**: `biz_application_items` 는 핫딜 노출만이 아니라 **모든 종류의 자리·금액 줄**이다(배너 유형 1줄 · 한 줄 1줄 · 핫딜 글 POST 1줄 + 노출 1줄 · 노출 1줄). 한도 엔진 하나가 이 줄의 `product` 로 판정한다. `/ads` 수동 등록은 `biz_applications.source = OPERATOR`(사업자 신원 없이 `advertiser_name`)로 같은 표를 쓴다(API 는 광고 일정표 세션).
- **자리를 차지하는 상태** = `AWAITING_PAYMENT`·`AWAITING_DEPOSIT`·`SCHEDULED`·`TAKEN_DOWN`(되돌릴 수 있어서 — 가정). 판정 = 같은 상품의 겹치는 줄의 **최대 동시 겹침 수** < 한도(반열림이라 끝 = 다음 시작이면 안 겹침). 승인 때 상품별 `pg_advisory_xact_lock` 아래 다시 판정, 신청 때는 잠금 없이 안내 판정. 공개 날짜별 남은 칸 = 그날 KST 하루 안 최대 동시 겹침.
- **요금표 시드 = user-web 목업 값**(관리자 목업과 단가가 달라 공개 쪽을 택함 — 가정): 넓은 50,000/한도 2/최대 14일 · 띠 30,000/3/14 · 네모 20,000/4/30 · 작은 10,000/6/30 · 한 줄 5,000/5/30 · 핫딜 홈 상단 20,000/3/7 · 말머리 상단 10,000/2/7 · 핫딜 글 30,000(건당). 미리 신청 60일, 모두 받기 켬. 요금표 편집 API 는 후속. 받기 꺼진 상품 409 **`BIZ_PRODUCT_CLOSED`**(목업 `AD_TYPE_CLOSED` 대신).
- **금액**: 일수 = ceil(기간 / 24시간)(최소 1), 공급가 = 줄 합(단가 × 일수, 핫딜 글은 건당), 부가세 = floor(공급가 × 10%). 승인 supply ≥ 1, 요금표와 다르면 `fields.adjustReason`(감사 `BIZ_APP_PRICE_ADJUST`). 승인 때 스냅샷 고정, 결제 금액 = 확정 합계.
- **기간**: 공개 입력은 날짜(`startDate`·`endDate` 끝 포함) ↔ 서버 `[startDate 0시 KST, endDate+1 0시 KST)`. 공개 신청은 **내일 이후 시작**만, 미리 신청 일수·최대 일수는 요금표. 노출 없는 핫딜 글 = 내일 0시부터 딜 마감일(포함)까지, 없거나 지났으면 7일, 최대 30일(가정). 관리자 승인 기간은 ISO 분 단위.
- **승인**: 온라인 결제 기한은 지금 뒤이고 **시작 시각 이전**이어야 한다(400 `fields.deadlineAt` — 가정). 결제 기한 기본 72시간·입금 기한 기본 7일은 화면이 넣고 서버 설정 `roboyam.biz.*` 에도 둔다.
- **온라인 결제**: `POST /api/business/applications/{id}/payment` → `BizPaymentStart`. 열린 결제가 1분 이상 남았으면 같은 결제, 아니면 새 결제(유효 = min(30분, 결제 기한 − 지금), 1분 미만이면 409). 완료는 결제 모듈 완료 API. 처리기 `BizApplicationPaymentHandler`: 신청 건 없음·결제 대기 아님·온라인 아님·결제 시각 > 결제 기한·금액 ≠ 확정 합계 → REJECT(자동 취소, 예외 없음). PG 금액 불일치·결제 주문 유효 시각 지남은 결제 모듈이 처리기 없이 자동 취소. 결제 대기 건을 닫는 길(만료·취소·수동 전환·소재 바꾸기·정지)은 **열린 결제를 먼저 닫고(`expireOpen`) 신청 건 행을 잠근다**(공구 결정 37 과 같은 잠금 차례).
- **소재 바꾸기**(새 API `PUT /api/business/applications/{id}/creative`): `SUBMITTED` 는 새 버전만 · `AWAITING_PAYMENT`·`AWAITING_DEPOSIT`(입금 기록 없음)는 **`SUBMITTED` 로 되돌림**(승인 스냅샷 비움·자리 반납·열린 결제 닫기). 자격을 다시 본다(재심사 중 409). 노출 자리는 못 바꾼다.
- **사업자 취소**: `SUBMITTED`·`AWAITING_PAYMENT`·`AWAITING_DEPOSIT`(입금 기록 전). 입금 기록 뒤는 409(운영자 취소).
- **운영자 취소**: `AWAITING_PAYMENT`·`AWAITING_DEPOSIT`(입금 기록이 있으면 입금액 전액 수동 환불 송금 대기)·`SCHEDULED` 시작 전(전액 환불). 심사 대기는 반려로, 진행 중은 환불 승인으로.
- **수동 결제**: 계약서 사본은 사업자등록증처럼 **DB 암호문**(`biz_contract_files.content`, 첫 바이트로 PDF·JPEG·PNG 판정, 10MB, 한 요청 1~10장 — 단 multipart 요청 한도 11MB 라 **화면은 한 장씩 올린다**). 대체(`replaceId`)는 정확히 1장, 옛 파일은 `replaced_at` 이 찍힌 채 보존. 열람 새 API `GET …/contracts/{fileId}` → `{ url: data:…, mimeType, fileName }` + 처리 기록 `CONTRACT_VIEWED` + 감사 `BIZ_APP_CONTRACT_VIEW`. 입금 기록은 덮어쓰기(처리 기록에 남김), 입금일 ≤ 오늘(KST), 금액 ≠ 확정 합계면 저장 때부터 `fields.mismatchReason` 필수. 확정 CHECK `ck_ba_manual_confirmed`(대체되지 않은 계약서 수 ≥ 1 + 입금 확인 시각) — 서비스는 409 `BIZ_DEPOSIT_INCOMPLETE`, DB 직접 UPDATE 도 거절. 없는 계약서 404 **`BIZ_CONTRACT_NOT_FOUND`**.
- **환불**: 사업자 `refund-request` 는 `SCHEDULED` 이고 환불 줄이 한 번도 없을 때만(거절 뒤 다시 요청 409 — 가정). 시작 전 = `CANCELED` + 환불 APPROVED(FULL_AUTO — 온라인 PG 취소, 수동은 송금 대기) · 진행 중 = 환불 REQUESTED(제안액), 노출 계속 · 끝남 409. **일할 기준(가정 — 계약 미확정)**: 일 단위, 총 일수 = 기간의 KST 날짜 수, 쓴 날 = 시작 날짜부터 오늘까지(오늘 포함), 제안액 = floor(결제액 × 남은 날 / 총 일수), 수수료 없음(user-web `proratedRefund` 와 같은 식). 운영자 승인 `0 ≤ amount ≤ 결제액 − 이미 환불` → `CANCELED`(온라인 amount > 0 이면 PG 취소, 수동은 송금 대기) · 거절은 상태 그대로. 수동 송금 완료 표시 새 API `POST …/refund/transfer`. 수동 건 전액 환불액 = 입금 확인 금액. 응답 `refund` = `{ type, status: REQUESTED|APPROVED|DENIED, … }`(관리자는 method·approvedAmount·transferStatus 등 추가).
- **반려 코드 정본 = 관리자 화면 값** `CREATIVE_SPEC·POLICY_VIOLATION·LINK_BROKEN·PERIOD_UNAVAILABLE·FALSE_PRICE·OTHER`(user-web 신청 건 `BusinessRejectCode` 를 이 값으로 바꿈).
- **처리 기록 action = 코드**(관리자·공개 같은 묶음, 화면 i18n). 공개에는 운영 기록(계약서 교체·열람)을 내지 않는다. `STARTED`·`ENDED` 는 기록하지 않고 시각으로 계산한다. 관리자 상세에 `source`·`takedown`·`quote.lines` 를 더했다.
- **사업자 정지·해제·탈퇴 훅**(`BizApplicationHookImpl`): 정지 Pending CANCEL = 심사·결제·입금 대기 취소(입금 기록 있으면 수동 환불) · Scheduled REFUND_FULL = 취소 + 전액 환불 / HOLD = `TAKEN_DOWN` · Running TAKEDOWN_PRORATE = `TAKEN_DOWN` + 일할 환불 심사 대기 / TAKEDOWN_ONLY = `TAKEN_DOWN`. 해제 EXTEND_RESTORE = 끝 시각을 (지금 − max(내린 시각, 시작))만큼 늘려 되돌림(자리 잠금 판정, 꽉 차면 늘리지 않고 되돌리고 `EXTEND_FAILED` 기록, 정지로 생긴 환불 요청은 거절 처리) · KEEP_REFUND = 환불 심사 대기 건은 그대로, 환불 대기가 없는 내림 건은 연장 없이 되돌림(그대로 두면 되돌릴 길이 없어서). 탈퇴 판정 = 진행 중 건이 있으면 막음, 탈퇴 = 대기 건 취소·시작 전 전액 환불·포기 동의 시 진행 중 `CANCELED`(환불 없음, `FORFEITED`).
- **배치**(admin `BatchCatalog`, 작업은 shared `bizapplication.batch`): `biz-payment-expire`(결제 기한 지남 → `EXPIRED`·자리 반납·알림) · `biz-payment-due-notice`(기한 24시간 전 1회) · `biz-schedule-notice`(시작·종료 24시간 전 1회씩). 입금 기한 지남은 자동 취소 없이 「입금 기한 지남」 큐.
- **알림**: 회원 `BIZ_APPLICATION` 한 종류(덧붙임 kind — 접수·승인·반려·수동·결제 완료·확정·만료·결제 기한 임박·시작·종료 임박·취소·내림·되돌림·환불 요청·승인·거절·환불됨·자동 취소, 링크 `/business/applications/{id}`). 관리자 `BIZ_APPLICATION_REVIEW_PENDING`(`ad.manage`) · `BIZ_APPLICATION_REFUND_REQUESTED`(`deal-order.refund`) — 예약돼 있던 `AD_REVIEW_PENDING` 은 핫딜도 담아야 해 쓰지 않았다.
- **권한·감사·메뉴**: `ad.manage`·`deal-order.refund` featureReady 켬(환불 승인·거절·송금 = `deal-order.refund`). 감사 계약 목록 + `BIZ_APP_DEPOSIT_DEADLINE`, 대상 종류 `BIZ_APPLICATION`, 보관 분류 돈(5년) = 승인·조정·계약서 올리기·입금·확정·취소·환불 3종 / 개인정보 = 계약서 열람 / 일반 = 반려·수동 전환·입금 기한. 메뉴 「사업자」 묶음에 `/biz-applications`(`business.read`).
- **광고 이미지** 새 API `POST /api/business/ad-images`(JPG·PNG·GIF·WEBP 10MB, 글 이미지와 같은 검사·다시 인코딩, 공개 노출용이라 `/media` 사용 — 파일 용도 `BIZ_AD_IMAGE`). 사업자등록증 `/files` 와 따로다. **딜 링크 검사** `GET /api/business/deal-link-check?url=`(단축 URL 차단·쇼핑몰 이름 상수표 — 목업 그대로), 신청 때 서버가 다시 막는다.
- **공개 `HOTDEAL_PLACEMENT` 신청은 만들지 않았다**(게시된 사업자 핫딜이 아직 없음). 핫딜 글 게시는 고리(`BizHotdealPosting`, 기본 아무것도 안 함)만.
- **입금 계좌 안내**: 설정 `roboyam.biz.deposit-account.*`(`ROBOYAM_BIZ_*`, local 은 예시 값), 입금자명 = 상호.

## 화면 연결 결정 (2026-10-31)
- admin-web·user-web 신청 건은 실제 API(목업 파일·`mockPay`·`mockOperate`·개발용 상자 삭제). 관리자 처리 단추 권한은 `ad.manage`, 환불 송금 완료 단추는 `deal-order.refund`. 계약서는 한 장씩 차례로 올리고, 줄마다 [보기](PDF iframe·그림 img).
- 처리 기록·환불·반려 코드는 i18n — 신원 반려(`business.rejectCode`)와 신청 건 반려(`business.applicationRejectCode`) 키를 나눴다. 공개 처리 기록의 노출 시작·종료는 화면이 phase 로 덧붙인다.
- user-web 결제는 `startPayment` → `usePaymentCheckout`(가짜 결제창·포트원·모바일 리디렉션) → 상세 다시 읽기. 입금 확인 전 입금 대기 건은 [신청 취소]. 광고 [소재 바꾸기](`BusinessCreativeDialog`)는 결제 전에만, 승인 뒤면 「다시 심사」 경고.
- ⚠ 배너 소재를 바꿀 때 이미지를 다시 올려야 한다(공개 상세 `creative` 에 `imageFileId` 가 없고 PUT 은 필수) — 후속. 핫딜 글 소재 바꾸기는 composable(`updateCreative`)만 있다.
- 광고 일정표(`/ads`) 목업과 그 안의 `AD_SLOT_FULL` 은 광고 일정표 세션 몫이라 그대로.

## 후속 (이 세션 범위 밖)
- 관리자 `/ads` 일정표 API(수동 등록 `source=OPERATOR`·긴급 내림·되돌리기 — F5 보상 선택), 공개 광고 노출(`AdSlot`), 핫딜 글 실제 게시·회원 핫딜, 공개 `HOTDEAL_PLACEMENT` 신청.
- 요금표 편집 API · 「입금 기한 지남」 운영자 알림 · 계약서 5년 보존 파기 · 일할 기준·수수료·청약철회(법무).
