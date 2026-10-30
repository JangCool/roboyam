# 사업자 신청 건 계약 — 광고·핫딜 사전 심사 → 결제 (화면 확정 2026-10-30 — 목업, 서버 없음)

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
