# 사업자 계약 (화면 확정 2026-10-30 — 목업, 서버 없음)

**의도**: 일반 회원이 사업자 신원을 신청하고, 운영자가 국세청 상태조회와 사업자등록증 사본으로 심사해 승인·반려·정지한다. 승인된 사업자만 광고·핫딜 신청 건(`biz-application-contract.md`)을 낼 수 있다.
**정본·근거**: `docs/plans/biz-ad-hotdeal/00-overview.md` ★ 절(B1~B3·H2·F7·F8), 상세 분석 `01-business.md`. community 원본 `docs/api-business.md`·`api-business-services.md`(V36·V103).
**프론트 기준**: admin-web `pages/businesses/index.vue`, `components/business/**`, `composables/business/api/useBusinessApi.ts`(+types·mock) / user-web `pages/business/index.vue`·`apply.vue`, `components/business/**`, `composables/business/api/useBusinessApi.ts`.

## 화면 경로
- 관리자 `/businesses` — 상태 탭(심사 중·승인·반려·정지·탈퇴·전체) + 서비스 필터 + 검색 + 표(국세청 조회 Tag) → 행 Drawer(`?id=`): 신원(가린 값·[평문 보기])·국세청 카드([다시 조회])·사업자등록증 사본·서비스 카드(영향 건수)·처리 기록. 메뉴 묶음 「사업자」(`business.read`).
- 공개 `/business`(상태별 한 화면, 마이페이지 바로가기) · `/business/apply`(신청·수정 Stepper 3단계).

## 모델
- 신원 1줄 + 서비스별 이용 권한 N줄(community V103 구조). 서비스는 **`HOTDEAL`·`AD` 둘**(직접홍보는 광고 한 줄 광고로 합침 — 00 D2).
- 사업자 전용 계정(소셜 없는 별도 가입)은 **두지 않는다**(00 D1). 일반 회원이 신청하고 승인되면 회원 역할 「사업자 회원」을 붙인다(정지·탈퇴 때 뗀다).
- 이용 자격 판정은 shared 한 곳: `approvedProfileOf(userId, service)` = 신원 `APPROVED` 이고 그 서비스 줄 `APPROVED`. 광고·핫딜은 이것만 부른다.
- 신원 상태: `PENDING`(심사 중) → `APPROVED` / `REJECTED`(재신청 = 같은 줄 `PENDING`) · `APPROVED` → `SUSPENDED` ↔ `APPROVED` · 모든 상태 → `WITHDRAWN`. 승인 뒤 상호·등록번호를 바꾸면 `PENDING`(재심사) — 그동안 이미 예약·진행 중인 신청 건은 그대로, 새 신청만 막힌다.

## 필드·검증
- 상호 1~60 · 등록번호 10자리(체크섬) · 대표자 · 연락처 · 이메일 · 사업장 주소 · **사업자등록증 사본 필수**(PDF·JPG·PNG, 10MB, 1장) · 약관 동의(사업자 이용약관·개인정보 수집, 버전 저장).
- 등록번호·대표자·연락처·주소는 암호문(`DataCipher`), 같은 번호 비교는 blind index(HMAC — `DataCipher` 에 추가), 끝 4자리 평문. 응답은 가린 값만.
- 등록번호 중복: 살아 있는 줄(`PENDING`·`APPROVED`·`SUSPENDED`) 사이에서 409 `BUSINESS_DUPLICATE`.
- 수정 모드: 민감 칸은 비워 두면 유지. 상호·번호가 바뀌면 화면이 재심사 경고 + 확인 창, 서버는 `PENDING` 으로.

## 심사 규칙
- **국세청 상태조회**(공공데이터포털 「사업자등록정보 진위확인 및 상태조회」): 승인 전 필수. 결과 스냅샷(상태 계속/휴업/폐업·과세유형·휴폐업일·조회 시각) 저장, [다시 조회]. **계속사업자만 승인**. 휴업·폐업은 409 `BUSINESS_NTS_NOT_ACTIVE`. 조회 불가(장애·키 없음)는 사유(`ntsOverrideReason`)를 받아야 승인 — 감사에 남는다. 신청 단계는 체크섬만(외부 장애가 신청을 막지 않게). 외부 호출은 인터페이스 + local 가짜 구현(dev·prod 에서 가짜 기동 차단 — 커머스 본인인증과 같은 패턴).
- 반려·정지 사유 = 코드(번호 불일치·서류 미비·휴폐업·정책 위반·기타) + 글 1~200자. 재신청은 반려 뒤 하루 3회까지.
- **정지**(H2): 창에 영향 건수와 처리 선택 — 결제·입금 대기 건 = 자동 취소(기본), 시작 전 예약 = 전액 환불(기본), 진행 중 = 긴급 내림 + 일할 환불 심사 대기열(기본). 해제 때 진행 중이던 건은 운영자가 「남은 기간 연장 후 되돌리기 / 환불 유지」를 고른다.
- **평문 열람**: 사유 필수 → 열람 기록(운영자·IP·사유·시각, 배송지 열람 `commerce_address_views` 모양) → 화면은 10분 뒤 자동으로 가림, 복사 단추 없음. 등록증 사본 열람도 같은 기록.
- **탈퇴**(F8): 결제 전·시작 전 신청 건은 자동 취소·환불하고 탈퇴 허용, 진행 중 건만 막는다(「남은 기간 포기」 동의로 풀 수 있음). 공개 탈퇴 API 가 아직 없어 커머스 「탈퇴 대비」와 함께 연결한다.
- 보존: 반려·탈퇴 90일 뒤 등록증·암호문 파기(DB 배치), 거래 근거(상호·끝 4자리·주문 요약)는 5년.

## 권한·감사·알림
- 권한(기존 `Permission`): `business.read`(목록·상세) · `business.manage`(승인·반려·정지·서비스 처리) — featureReady 켬. **추가**: `business.reveal`·`business.unsuspend`(SENSITIVE). 메뉴는 `AdminScreenRegistry` 에 「사업자」 묶음.
- 감사: `BUSINESS_APPROVE`·`_REJECT`·`_SUSPEND`·`_UNSUSPEND`·`_REVEAL`·`_CERT_VIEW`·`_NTS_CHECK`·`_NTS_OVERRIDE`.
- 회원 알림(계정 알림 — 끌 수 없음): 접수됨·승인·반려·정지·해제·재심사 대기. 관리자 알림: 사업자 신청·재심사.

## API 후보
목업 머리말이 정본 후보다 — admin `useBusinessApi.ts`(`/api/admin/businesses/**`: 목록·상세·nts-check·approve·reject·suspend·reveal·certificate), user-web `useBusinessApi.ts`(`GET/POST/PUT /api/business/me`, `POST /api/business/files`).

## 저장 (제안 — shared 스트림, V111~)
`business_profiles`·`business_services`·`business_nts_checks`(스냅샷)·`business_documents`(등록증 파일 참조)·`business_sensitive_views`(사유·IP) + 회원 역할 시드 「사업자 회원」 + 약관 종류 `BUSINESS`.

## 미확정
- 국세청 API 키 발급·호출 한도(공공데이터포털) — dev 배포 때 확인.
- 대표자 본인인증을 신청 조건으로 둘지(00 — 3차 선택 설정).
- 보존 기간(90일·5년)은 법무 확인.
