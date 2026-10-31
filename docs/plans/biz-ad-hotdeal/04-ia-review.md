# 04. 관리자 IA 재검토 — 도메인별 메뉴 · 수동 등록도 심사 · 단일 엔진 유지

**사용자 확정 2026-10-31: 추천안 그대로(Q1~Q5 모두 추천).** 정본 반영 대상: 00 ★-7 절, `business-contract.md`·`biz-application-contract.md`·`ad-contract.md`.
기준 브랜치 `claude/biz-application-backend-78da23`(HEAD `12cc850f`). [확인] = 코드·문서에서 읽은 사실, [제안] = 설계 판단.

## 0. 결론
- **메뉴·화면·권한·알림은 업무(도메인)별(사업자 / 광고 / 핫딜), 서버는 `biz_applications` 엔진 하나 유지(안 C).** V112·테스트·결제 핸들러·훅·배치는 그대로, 바뀌는 것은 「어느 화면·권한이 어느 종류(kind)를 보는가」 한 겹.
- **수동 등록 = `source=OPERATOR` 신청 건을 `SUBMITTED` 로 만든다** → 같은 광고 심사 큐에서 승인(수동 결제 고정) → 계약서 사본 + 입금 확인 → 확정. 1인 운영은 「자기 승인 허용」 설정(기본 허용)으로 막히지 않게, 자기 승인은 처리 기록·감사에 표시.
- 심사 권한은 기존 관례(심사 = manage)대로 `ad.manage`·`hotdeal.manage` 를 종류별로 나눠 쓴다. 환불은 1차에 `deal-order.refund` 하나.

## 1. IA 전략 비교

| 기준 | A. 도메인별 (서버도 분리) | B. 한 큐 (기존 `/biz-applications`) | **C. 화면은 도메인별, 서버 하나 (확정)** |
|---|---|---|---|
| 권한 분리(광고 ≠ 핫딜) | 쉬움 | 불가 — 큐 하나가 `business.read`·`ad.manage`(`pages/biz-applications.vue:8`) | 화면·API 가 도메인으로 가름(`@RequirePermission(anyOf)` + 서비스 재검사 — `RequirePermission.java:16-17,28`) |
| 알림 | 도메인별 쉬움 | `BIZ_APPLICATION_REVIEW_PENDING` 수신 `AD_MANAGE` 하나(`AdminNotificationEvent.java:138`) | 이벤트 도메인별 분리 + 대시보드 카드 교차 집계 |
| 1인 운영 | 묶음 3개 순회 | 한 화면 | 대시보드 「처리할 일」 카드에서 바로 |
| 확장성 | 종류마다 표·핸들러·환불·훅 재작성 | `kind` 추가 — 화면 비대 | `kind` + `domain` 매핑 한 줄 |
| 기존 자산 | V112 쪼갬 | 100% | 약 95% |

광고·핫딜은 한도·결제·환불·정지 연계 규칙이 같다(★ 절 「한 규칙」 — `00-overview.md:95,105,144`).

### 1-2. 메뉴 트리 (`util-admin-menu.ts` ITEMS 와 서버 `AdminScreenRegistry` 를 같게)

| 묶음 | 메뉴 | 주소 | 조회 권한 | 처리 권한 | 이번 보완 |
|---|---|---|---|---|---|
| **사업자** `pi pi-briefcase` | 인증 심사 | `/business-reviews` | `business.read` | `business.manage`(국세청 재조회·승인·반려) | `/businesses` 의 심사 중·재심사 탭을 분리 |
| | 사업자 목록 | `/businesses` | `business.read` | `business.manage`(정지) · `business.reveal` · `business.unsuspend` · 등록증 열람 | 탭 조정 + **정지 해제 단추** |
| **광고** `pi pi-megaphone` | 광고 심사 | `/ad-reviews` | `ad.read` | `ad.manage`(승인·반려·수동 전환·계약서·입금·확정·취소·기한) · `deal-order.refund`(환불) | `/biz-applications` 를 AD_* 로 고정 |
| | 광고 일정표 | `/ads` | `ad.read` | `ad.manage`(수동 등록 → 신청 건 생성) · `ad.takedown` · `ad.restore`(추가) | 목업 유지(백엔드 다음 세션) |
| **핫딜** `pi pi-ticket` | 핫딜 심사 | `/hotdeal-reviews` | `hotdeal.read` | `hotdeal.manage` · `deal-order.refund` | `/biz-applications` 의 HOTDEAL_* |
| | (후속) 핫딜 글 / 링크 규칙 / 의심 후보 / 핫딜 설정 | `/hotdeals` 등 | `hotdeal.read` | `hotdeal.manage` 등 | 화면 없음 → 메뉴에 넣지 않음(`util-admin-menu.ts:7` 규약) |

- 「신청 심사」(`/biz-applications`)는 **없앤다**(Q5). 1인 운영의 「한눈에」는 대시보드 카드(광고 심사·핫딜 심사·입금 확인 대기·입금 기한 지남·환불 심사·사업자 인증 대기)가 맡는다 — 지금 대시보드엔 사업자·광고 카드가 없다(`apps/admin-web/app/pages/index.vue:68-74`).
- 묶음 차례: 소모임 → 사업자 → 광고 → 핫딜(`AdminScreenRegistry.java:101-105` 뒤에 광고·핫딜 묶음 — 지금 서버 레지스트리에 광고 묶음 없음 [확인]).
- 역할 유형은 이미 맞다 [확인]: `BIZ_REVIEW`(business.*) · `AD_PROMO`(ad.*) · `HOTDEAL`(hotdeal.*) · `BIZ_PRODUCT`(deal-order.*)(`PermissionType.java:21,26-28`).

## 2. 수동 등록도 심사 (Q1 확정: 자기 승인 = 설정값, 기본 허용)
- 지금 [확인]: ★-3 상태도·계약은 운영자 등록을 `AWAITING_DEPOSIT` 에서 시작(`00-overview.md:61`, `biz-application-contract.md:24`). 등록 API 는 없다(`BizSource.java:3`). 전이 `SUBMITTED → APPROVE_MANUAL → AWAITING_DEPOSIT` 는 있다(`BizTransitions.java:68`). `ck_ba_source_operator` 는 운영자 건에 정책 동의·사업자 신원을 요구하지 않는다(`V112:106`) → **운영자 건을 `SUBMITTED` 로 넣어도 DB·전이표 변경 없음.**
- 규칙 [확정]:
  1. 운영자 등록 → `biz_applications(source=OPERATOR, status=SUBMITTED, preferred_method=MANUAL, 광고주 또는 business_profile_id)` + 소재 버전 1 + 자리 줄, 처리 기록 `SUBMITTED`(actor OPERATOR). 감사 `BIZ_APP_OPERATOR_CREATE`(ad-contract 의 `AD_MANUAL_CREATE` 대신).
  2. 광고 심사 큐 「심사 대기」에 「운영자 등록」 Tag 로 들어온다. 점검표 동일.
  3. 승인: `source=OPERATOR` 면 결제 방식 `MANUAL` 만(온라인이면 400 `fields.method`).
  4. **자기 승인**(등록자 = 승인자): 설정 `roboyam.biz.self-approval`(`allow` 기본 | `deny`, `BizApplicationProperties`). `allow` = 승인 창 경고 + 메모 필수 + 처리 기록·감사 detail `selfApproved=true`. `deny` = 409 `BIZ_SELF_APPROVAL_DENIED`.
  5. 등록 실수: `OPERATOR_CANCEL: SUBMITTED → CANCELED` 를 `source=OPERATOR` 에만 허용(반려로 닫지 않는다).
- 차례: **심사(승인) → 계약서 사본 + 입금 확인 → [예약 확정].** 승인이 금액(`approved_total`)·자리를 확정하므로 계약 금액 근거가 승인 스냅샷이어야 하고, 확정 CHECK(`ck_ba_manual_confirmed`)를 그대로 쓴다. 오프라인 계약서를 먼저 받은 경우를 위해 **계약서 올리기는 운영자 건 `SUBMITTED` 부터 허용**(지금 `uploadContracts` 상태 제한 여부는 [미확인]). 입금 확인은 `AWAITING_DEPOSIT` 에서만.
- 구현 시점: 운영자 등록 API·자기 승인 설정·`OPERATOR_CANCEL` 은 광고주 표(`ad_advertisers`)와 함께 **`/ads` 백엔드 세션**. 이번 보완은 계약 문서에 설계만 적는다.

## 3. 서버 — 단일 엔진 유지하며 바꿀 것

| # | 곳 | 내용 | 크기 | 시점 |
|---|---|---|---|---|
| S1 | `BizApplicationKind.java` | `domain()` 추가(새 enum `BizDomain`): `AD_BANNER·AD_TEXT_LINE → AD`, `HOTDEAL_POST·HOTDEAL_PLACEMENT → HOTDEAL` | 작음 | 이번 |
| S2 | `BizApplicationAdminController.java` | 조회 입구 `anyOf={AD_READ, HOTDEAL_READ}`, 처리 입구 `anyOf={AD_MANAGE, HOTDEAL_MANAGE}`(환불은 `DEAL_ORDER_REFUND` 그대로). 목록에 `domain` 파라미터(필수). 서비스가 건의 `domain` 으로 `actor.can(…)` 재검사 → 아니면 403. 경로 `/api/admin/biz-applications/**` 는 유지 | 중간 | 이번 |
| S3 | `BizApplicationStore.forAdmin` | `forAdmin(domain, kind, q)` — `kind IN (...)`, 큐별 건수도 도메인 안에서 | 작음 | 이번 |
| S4 | `Permission.java` | `AD_READ`·`HOTDEAL_READ`·`HOTDEAL_MANAGE` featureReady 켬. `AD_RESTORE`(SENSITIVE, AD_PROMO) 추가. admin-web `util-permission-catalog.ts` 같이 | 작음 | 이번 |
| S5 | `AdminNotificationEvent.java` | `BIZ_APPLICATION_REVIEW_PENDING` → `AD_REVIEW_PENDING`(예약돼 있던 것 사용, holders AD_MANAGE, 링크 `/ad-reviews?id=`) + 새 `HOTDEAL_REVIEW_PENDING`(HOTDEAL_MANAGE, `/hotdeal-reviews?id=`). 환불 `AD_REFUND_REQUESTED`·`HOTDEAL_REFUND_REQUESTED`(DEAL_ORDER_REFUND, 링크만 다름). `BizApplicationNotices` 가 도메인으로 고른다. 사업자 알림 링크 `/businesses?id=` → `/business-reviews?id=`(`BUSINESS_APPLICATION_PENDING`·`BUSINESS_RE_REVIEW_PENDING`). ko/en 문구 | 중간 | 이번 |
| S6 | `AuditActions.java` | 행위 코드 `BIZ_APP_*` 그대로, detail 에 `domain·source·selfApproved` | 작음 | 이번(detail) / `/ads`(OPERATOR_CREATE) |
| S7 | `AdminScreenRegistry.java` | `bizApplications` 줄 삭제 → 사업자(`businessReviews`·`businesses`) · 광고(`adReviews`·`ads`) · 핫딜(`hotdealReviews`) | 작음 | 이번 |
| S8 | 테스트 | `BizApplicationAdminCatalogTest`(메뉴 키·권한·알림 수신자·링크) · `BizApplicationAdminHttpTest`(BUSINESS_READ 가정 → AD_READ/HOTDEAL_READ, `domain` 파라미터, 다른 도메인 403) | 중간 | 이번 |
| S9 | 운영자 등록 API + 자기 승인 + `OPERATOR_CANCEL` | §2 | 중간 | `/ads` |
| S10 | `ad_advertisers` | 계정 없는 광고주 | 중간 | `/ads` |

## 4. 핫딜 묶음 범위 (Q3 확정: 노출 자리는 광고 일정표 탭)
- 사업자 핫딜 심사 → **핫딜 › 핫딜 심사**. 당장 큐에 들어오는 종류는 `HOTDEAL_POST`(공개 `HOTDEAL_PLACEMENT` 신청·글 실제 게시는 아직 없음).
- 회원 핫딜 글·종료 요청·링크 규칙·의심 후보·설정 → 핫딜 묶음 후속(03-hotdeal 1차).
- **노출 자리(홈 상단·말머리 상단)의 달력·한도·긴급 내림은 광고 일정표의 탭**(한도 엔진·F5 보상 한 벌). 핫딜 담당에게는 `ad.read` 를 같이 줘 조회만. 핫딜 쪽 읽기 전용 「노출 현황」은 2차 선택.

## 5. 사업자 인증 별도 관리 (Q4 확정: 두 메뉴)
| 화면 | 담는 것 | 상태 | 처리 |
|---|---|---|---|
| 인증 심사 `/business-reviews` | 신원 심사 큐 | `PENDING`(처음·재신청) · `PENDING+re_review`(재심사) — 오래된 것 위 | 국세청 [다시 조회]·승인(계속사업자만, 조회 불가 사유)·반려·등록증 열람 |
| 사업자 목록 `/businesses` | 승인된 사업자 운영 | `APPROVED`·`SUSPENDED`·`WITHDRAWN`·`REJECTED`·전체 + 서비스 필터 | 정지(영향 건수·처리 선택) · **정지 해제**(API 있음, 단추 추가) · 평문 열람 · 등록증 열람 · 서비스 카드 |
- 두 화면은 같은 Drawer·Dialog(`components/business/**`)를 쓰고 탭 집합·기본 정렬만 다르다.

## 6. 프로세스 끊김 점검
✅ 있음 · ⚠ 주의 · ❌ 아직 없음(담당 세션)

| 흐름 | 단계 | 상태 |
|---|---|---|
| A. 사업자 광고 | 인증 신청·심사·승인 / 광고 신청 / 광고 심사 / 온라인 결제 / 수동 전환→계약서·입금→확정 / 환불 | ✅ (주소·권한만 바뀜, 일할 기준은 법무 미확정) |
| | 예약 → 공개 노출(`AdSlot`·가젯 `AD`) | ❌ `/ads` 백엔드·공개 노출 |
| | 일정표·긴급 내림·되돌리기 | ❌ `/ads` 백엔드 |
| | 운영자 알림 도메인 분리 | ⚠ S5 뒤 ✅ |
| B. 운영자 수동 등록 | 등록 → `SUBMITTED` → 같은 큐 심사(자기 승인 경고) → 계약서·입금 → 확정 | ❌ 등록 API·승인 분기는 `/ads` 세션, 계약서·입금·확정은 ✅ |
| C. 사업자 핫딜 | 신청(+노출 줄) → 심사 → 결제 → 예약 | ✅ (주소·권한만) |
| | 예약 → 글 실제 게시(`BizHotdealPosting` no-op) | ❌ 핫딜 1차 |
| | 노출 자리 한도·달력 | ⚠ 일정표 백엔드 뒤 |
| D. 정지·탈퇴 | 정지 연계 ✅ · 해제 단추 ⚠(이번 추가) · 탈퇴 ❌(공개 탈퇴 API — 커머스 「탈퇴 대비」) | |

이번 보완으로 새로 생기는 끊김은 없다.

## 7. 순서
1. **이번 보완**: 00 ★-7 절 + 세 계약 개정 → S1·S2·S3·S4·S5·S6(detail)·S7·S8 → admin-web 메뉴 재구성 · `BizAppReviewPage`(prop `domain`) + `pages/ad-reviews.vue`·`pages/hotdeal-reviews.vue` · `pages/biz-applications.vue` 삭제 · 사업자 두 화면(`pages/business-reviews.vue` 분리, `/businesses` 정지 해제 단추) · `useBizApplicationApi` 에 `domain` · `util-permission-catalog.ts` · 대시보드 「처리할 일」 카드 · i18n(menu 키 `businessReviews`·`adReviews`·`hotdeal`·`hotdealReviews`, ko·en) → `--tests` 선별 통과 + 두 앱 타입 검사.
2. **`/ads` 백엔드**: `ad_advertisers` · 운영자 등록 API(`SUBMITTED` 시작) · 자기 승인 설정 · `OPERATOR_CANCEL` · 일정표 calendar/list · 긴급 내림·되돌리기(`ad.restore`) · 수동 등록 서랍 개편(「④ 계약서 ⑤ 입금 → 예약 확정」 → 「등록(심사 요청)」, 계약서는 선택 첨부) · 공개 `AdSlot`·집계.
3. **핫딜**: 1차 회원 핫딜 → `BizHotdealPosting` 실제 게시 → 공개 `HOTDEAL_PLACEMENT` 신청 → (선택) 노출 현황.

## 8. 확정한 질문
| # | 질문 | 확정 |
|---|---|---|
| Q1 | 운영자 직접 등록 건의 자기 승인 | 설정값(기본 허용, 경고·메모·감사 표시, `deny` 로 전환 가능) |
| Q2 | 신청 건 환불 권한 | `deal-order.refund` 하나 유지(1차) |
| Q3 | 핫딜 노출 자리 화면 | 광고 일정표 탭 + 핫딜 담당에 `ad.read` (노출 현황은 2차) |
| Q4 | 사업자 화면 | 「인증 심사」·「사업자 목록」 두 메뉴 |
| Q5 | 통합 「신청 심사」 큐 | 없앤다, 대시보드 「처리할 일」 카드로 대체 |
