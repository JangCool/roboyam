# 운영 주체 단계와 약관 문안 계약 (**화면 목업 2026-11-06 — 확정 전**)

**의도**: 운영자는 지금 사업자등록 전인 개인이고, 나중에 개인사업자 → 법인이 될 수 있다. 이 「운영 주체 단계」를 사이트 설정 하나로 두고, 약관·개인정보처리방침·운영정책 본문은 자리표시로 써서 **게시할 때** 그 단계 값으로 바꿔 저장한다. 게시본은 고치지 않으므로 지난 판 글자와 개정 이력이 그대로 남는다. 기존 약관 기능(초안·게시·시행일·버전 기록·변경 안내)은 `terms-contract.md` 가 정본이고, 이 계약은 그 위에 얹는 것만 적는다.

## 결정 (2026-11-06, 사용자 대화)
- 단계 3개: `INDIVIDUAL`(개인, 사업자등록 전) · `SOLE_PROPRIETOR`(개인사업자) · `CORPORATION`(법인). 약관 속 호칭은 개인·개인사업자 「운영자」, 법인 「회사」. 두 낱말 모두 받침이 없어 조사를 그대로 쓴다.
- 구글 애드센스는 개인 단계부터 붙인다 → 개인정보처리방침 첫 판부터 「광고와 맞춤형 광고(행태정보)」 절을 넣는다.
- 사업자등록은 일반과세자로 할 생각이다(시점은 광고 직접 판매를 시작할 때). 과세 유형은 약관에 영향이 없다.
- 단계와 켤 수 있는 기능
  - 개인: 무료 커뮤니티와 애드센스
  - 개인사업자 이상: 광고 직접 판매(사업자등록만 있으면 됨)
  - 통신판매업 신고까지 마친 뒤: 공구·충전·상점

## 화면 (목업 — `// MOCK:`)
- 관리자 `/settings/operator` 「운영 주체」 화면(설정 메뉴 「운영·약관」 묶음 = 운영 주체 → 약관, 시스템 묶음 다음 — 2026-11-07 일반 설정에서 따로 뺐다. `site.read`·`site.manage`): 단계, 운영자 이름·연락 이메일·전화, 개인정보 보호책임자(기본 「운영자와 같음」), 호스팅 사업자, 사업자 칸(상호·대표자·사업자등록번호·주소·통신판매업 신고번호/기관·법인등록번호), 전환 이력.
- 관리자 `/settings/terms`
  - 운영 주체 요약 줄
  - 종류 「운영정책」(`OPERATION_POLICY`) 추가
  - 자리표시 칩, [미리보기], [기본 문안으로 초안 만들기]
  - 게시 대화상자의 「회원에게 불리한 변경」과 최소 시행일 규칙
  - 버전 보기의 「게시 당시 운영 주체」
- 공개 `/policy`(운영정책). 푸터에 운영정책 링크, 사업자 정보가 없을 때는 「문의 이메일」 한 줄.
- 기본 문안: `apps/admin-web/app/utils/terms-templates/{terms,privacy,operation-policy}.ts`(법률 검토 전 초안).

## 규칙
- **자리표시**(게시할 때 바꿈, 규칙의 원천은 `apps/admin-web/app/utils/util-terms-placeholder.ts` — 서버가 같은 규칙으로 구현)
  - `{{운영자}}` · `{{서비스이름}}` · `{{운영자정보}}`(문단 하나를 차지하면 목록, 글 중간이면 한 줄)
  - `{{보호책임자}}` · `{{보호책임자연락처}}` · `{{연락이메일}}` · `{{시행일}}`(「YYYY년 M월 D일」)
  - 모르는 자리표시가 있으면 게시를 거절한다(서버).
- **버전 사본**: 게시본에 바꾼 본문과 그때의 운영 주체 값을 함께 저장한다. 나중에 단계를 바꿔도 지난 판은 바뀌지 않는다.
- **최소 시행일**(`terms-contract.md` 의 「내일 이후」 규칙을 넓힘)
  - 그 종류의 첫 게시본: 내일
  - 개정: 7일 뒤
  - 회원에게 불리한 개정(`unfavorable`): 30일 뒤 + 가입 회원에게 메일 알림
  - 「마지막 시행일 이후」 규칙은 그대로 둔다.
- **단계 전환** = 약관·처리방침·운영정책 새 버전 게시. 법인으로 바뀔 때는 바뀐 약관이 시행되기 전에 회원 전체에게 개인정보 이전 사실을 알린다(개인정보보호법 제27조 — 이전 사실, 받는 곳의 이름·주소·연락처, 원하지 않을 때 탈퇴 방법). 약관 제20조에 미리 적어 두었다.
- **운영정책**은 가입 동의 대상이 아니다(`signup=false`, 동의 버전 문자열에 들어가지 않음). 공개 화면과 버전 기록만 있다.

## API 후보 (화면 확정 뒤 다듬는다)
- `GET·PUT /api/admin/site/operator`(`site.read`·`site.manage`) — 모양의 원천은 화면 타입 `apps/admin-web/app/composables/site/api/useSiteOperatorApi.types.ts`(`SiteOperator*`, 기존 운영자 계정용 `useOperatorApi` 와 다른 것).
  - 칸: `stage` · `operatorName` · `contactEmail` · `phone` · `privacyOfficerSameAsOperator` · `privacyOfficerName` · `privacyOfficerEmail` · `hostingProvider` · `business{businessName, representative, registrationNumber, address, mailOrderNumber, mailOrderAuthority, corporateNumber}|null`
  - 응답에 더함: `updatedAt` · `history[{changedAt, fromStage, toStage, changedBy}]`
  - 오류 칸: 사업자 칸은 `fields["business.xxx"]`
  - 감사: 단계가 바뀌면 `SITE_OPERATOR_STAGE_CHANGE` 와 전환 이력 한 줄, 칸만 바뀌면 `SITE_OPERATOR_UPDATE`
  - 화면이 정한 길이 상한(서버가 확정): 이름 30 · 이메일 254 · 전화 20(숫자와 `+ - ( )`) · 그 밖 50 · 주소 200
- 공개 `GET /api/site/info` 에 `operator{stage, contactEmail}` 추가(푸터 문의 줄, 지금 `AppFooter.vue` 의 `// REAL:` 자리).
- `POST /api/admin/terms/{kind}/publish` 에 `unfavorable: boolean` 추가(지금 화면은 실제 종류에는 빼고 보낸다 — `useTermsApi.publish` 의 MOCK 줄). 응답과 버전 상세에 `operatorSnapshot{stage, operatorName, businessName}`.
- `TermsKind.OPERATION_POLICY("operation_policy", "O", false)` 와 공개 `/api/terms/operation_policy`.

## 세션 정리 결정 (서버 단계에서 할 일)
- **사업자 정보의 정본 = 운영 주체 설정.** 지금 커머스 설정의 사업자 정보(`CommerceBusiness`, 푸터 표시)는 운영 주체 값을 읽게 바꾼다. 판매 스위치를 켜는 조건에 「단계가 개인사업자 이상이고 통신판매업 신고번호가 있음」을 더한다. 커머스 규칙이므로 테스트가 필요하다(commerce.md 18절).

## 미확정 · 법무 확인
1. 개인 단계에서 개인정보 보호책임자로 실명을 꼭 공개해야 하는지(법 제30조 「성명 또는 부서」). 지금 문안은 실명과 이메일을 쓴다.
2. 통신비밀보호법의 접속 기록 3개월 보관이 이 사이트에 적용되는지. 지금은 접속 로그를 따로 남기지 않는다.
3. 애드센스 행태정보의 국외 이전 고지 수준, 유럽 방문자 동의 창(CMP)이 필요한지.
4. 19금 글은 본인인증(PortOne)이 있어야 볼 수 있다. 본인인증 계약은 사업자가 필요할 가능성이 높아 개인 단계에서는 쓰지 못할 수 있다. 쓰게 되면 처리방침에 본인인증 항목(생년월일, CI·DI 해시, 이름 암호문)을 더한 개정판을 게시한다.
5. 게시판 개인정보 보유 배치의 스위치(`retention_enabled`)가 기본으로 꺼져 있다. 처리방침의 「작성 IP 365일」과 맞추려면 오픈 전에 켜야 한다.
6. 회원 이메일은 평문으로 저장된다(처리방침에 암호화 저장이라고 적지 않았다).
7. 위탁처(AWS·Amazon SES)의 계약 주체와 운영(prod) 리전을 확인해야 한다.

## 서버 구현 결과 (2026-11-07 — 화면 연결 전)
- **마이그레이션**: shared `V159__site_operator_terms_policy.sql`(V158 은 profile-privacy 가번호라 비움 — 합칠 때 다시 확인). `site_operator`(1행, 사업자 정보의 정본) · `site_operator_history` · 커머스 사업자 정보 → 운영 주체로 옮기고(상호가 있으면 `SOLE_PROPRIETOR`) `commerce_business_info` 의 겹치는 열 삭제 · 약관 종류 CHECK ∪ `OPERATION_POLICY` · `terms_versions.unfavorable`·`operator_snapshot(jsonb)`.
- **관리자** `GET·PUT /api/admin/site/operator`(`site.read`·`site.manage`): 응답·요청은 화면 타입 그대로. 오류 `400 VALIDATION fields.{stage,operatorName,contactEmail,phone,privacyOfficerName,privacyOfficerEmail,hostingProvider,business.*}`(문구 `siteOperator.*`), 판매 중 개인 단계·신고번호 비우기 `409 COMMERCE_SALES_OPEN`. 전화는 화면과 같이 띄어쓰기도 받는다. 감사 `SITE_OPERATOR_STAGE_CHANGE`(+이력)·`SITE_OPERATOR_UPDATE`(대상 SITE/1, 바뀐 칸 before/after, 이메일은 가림).
- **공개** `GET /api/site/info` 에 `operator{stage, contactEmail|null}`(사본·ETag 경로 그대로).
- **약관 게시**: `unfavorable`(없으면 false), 최소 시행일 첫 판 내일 · 개정 7일 뒤(`terms.effectiveDate.revision`) · 불리 30일 뒤(`terms.effectiveDate.unfavorable`). 자리표시는 `TermsPlaceholders`(정화된 초안의 `{<!-- -->{` 를 되돌린 뒤 바꾸고 다시 정화), 모르는 이름 `fields.draft`(`terms.draft.unknownPlaceholder`). 버전 목록·상세에 `unfavorable`·`operatorSnapshot{stage,operatorName,businessName}`(DB 에는 사본 전체). 감사 `TERMS_PUBLISH` after 에 `unfavorable`.
- **불리한 변경 메일**: 필수 안내 메일 `MEMBER_TERMS_CHANGE`(끌 수 없음)를 커머스 메일 대기열에 회원마다 한 줄(탈퇴·삭제·미인증 제외), 1분에 20통씩 `next_attempt_at` 을 미뤄 넣는다(`TermsChangeMails`). 이용약관·개인정보처리방침·운영정책만(`TermsKind.memberNotice`).
- **커머스**: 사업자 정보 읽기의 겹치는 칸은 운영 주체(법인 = 회사명, 보호책임자 = 운영 주체의 보호책임자). `PUT /api/admin/commerce/business-info` 는 `businessHours`·`shippingRefundPolicy` 만 저장하고 나머지는 받아도 무시. 판매 켜기 자동 조건에 `OPERATOR_STAGE`(개인사업자 이상 + 통신판매업 신고번호) 추가.

## 화면 연결 (2026-11-07 — 목업 파일 모두 지움, 두 앱 typecheck 0, 브라우저 확인 전)
- admin: `useSiteOperatorApi` 실제 호출(`409 COMMERCE_SALES_OPEN`·`fields.stage` 는 구획 안 한 줄) · 운영정책도 `/api/admin/terms/operation_policy` · 게시는 모든 종류에 `unfavorable` · 이력 표와 버전 보기에 「불리한 변경」 태그와 서버 `operatorSnapshot`(옛 판 null 이면 줄 숨김).
- 커머스 사업자 정보 구획: 겹치는 10칸은 읽기 전용 + 「설정 › 운영·약관 › 운영 주체에서 바꿔요」 링크(`/settings/operator`), 저장은 운영시간·배송/환불 안내만. 오류 칸 열쇠를 실제 `values.<칸>` 으로 고침. 판매 켜기 자동 조건에 `OPERATOR_STAGE`.
- user-web: 푸터 문의 = `/api/site/info` 의 `operator.contactEmail`(null 이면 숨김) · `/policy` = `/api/terms/operation_policy`(`?version=N` 은 메일 링크와 같은 꼴).

## 메뉴 분리 · 기본값 개인 · 단계별 확인 (2026-11-07)
- 운영 주체는 일반 설정에서 빼 `/settings/operator` 화면이 됐다. 설정 메뉴 「운영·약관」 묶음(시스템 다음) = 운영 주체 → 약관(서버 `AdminScreenRegistry` 키 `siteOperator`). 관리자 셸 메뉴는 DB 의 관리자 메뉴 구성을 읽는다(menu-contract 「관리자 사이트 메뉴」).
- **기본값은 개인**(사용자 지시): 새 설치는 `INDIVIDUAL`. local 공구 시드(`GroupBuyLocalSeeder`)는 운영 주체를 건드리지 않는다 — 개인 단계면 판매는 꺼진 채(`OPERATOR_STAGE`), 판매 시험은 운영 주체를 개인사업자로 바꾸고 켠다. V159 의 「커머스 상호가 있으면 개인사업자」 옮기기 규칙은 실제 사업자 정보를 지키려고 그대로 둔다.
- 단계별로 실제로 막는 것은 **공구 판매 스위치 하나**(`OPERATOR_STAGE` · 판매 중 개인 전환/신고번호 비우기 409). 판매가 꺼져도 공구 목록·상세는 보이고 주문만 서버가 막는다(커머스 계약 그대로).
- ~~미확정: 로보티콘 유료 판매 · 광고/핫딜 유료 신청이 운영 주체 단계를 보지 않는다~~ → **정함(사용자 결정 2026-11-07): 같은 조건(`operatorCanSell()` = 개인사업자 이상 + 신고번호)으로 막는다.** 로보티콘 — 켜기 400 `fields.paidSalesEnabled`(이미 켜진 값 그대로 저장은 됨) · 결제 준비 409 `EMOTICON_PAID_SALES_CLOSED` · 관리자 설정 조회에 `paidSalesAllowed` · 공개 규칙 `paidSalesEnabled` = 스위치 && 조건 · 켜져 있으면 개인 전환/신고번호 비우기 409 `EMOTICON_PAID_SALES_OPEN`. 광고·핫딜(스위치 없음) — 새 신청 409 `BIZ_PRODUCT_CLOSED`(상품 `accepting` false) · 결제 시작 409 `BIZ_PAYMENT_UNAVAILABLE` · 유료 승인(ONLINE·MANUAL)·입금 확정 409 `BIZ_OPERATOR_STAGE`. 이미 결제창에 들어간 결제의 완료는 받는다.
