# 사업자 계약 (화면 확정 2026-10-30 · 서버 구현·화면 연결 2026-10-30 — 신청 건 부분은 목업)

**의도**: 일반 회원이 사업자 신원을 신청하고, 운영자가 국세청 상태조회와 사업자등록증 사본으로 심사해 승인·반려·정지한다. 승인된 사업자만 광고·핫딜 신청 건(`biz-application-contract.md`)을 낼 수 있다.
**정본·근거**: `docs/plans/biz-ad-hotdeal/00-overview.md` ★ 절(B1~B3·H2·F7·F8), 상세 분석 `01-business.md`. community 원본 `docs/api-business.md`·`api-business-services.md`(V36·V103).
**프론트 기준**: admin-web `pages/businesses/index.vue`, `components/business/**`, `composables/business/api/useBusinessApi.ts`(+types·mock) / user-web `pages/business/index.vue`·`apply.vue`, `components/business/**`, `composables/business/api/useBusinessApi.ts`.

## 화면 경로
- **세션 정리 결정(2026-10-31, `docs/plans/biz-ad-hotdeal/04-ia-review.md` §5)**: 사업자 화면은 두 메뉴다 — **인증 심사 `/business-reviews`**(심사 중 = 처음·재신청 `PENDING` + 재심사 `PENDING+re_review`, 오래된 것 위 · 국세청 다시 조회·승인·반려·등록증 열람) · **사업자 목록 `/businesses`**(승인·정지·탈퇴·반려·전체 + 서비스 필터 · 정지·**정지 해제 단추**·평문 열람·등록증 열람·서비스 카드). 같은 Drawer·Dialog 를 쓰고 탭 집합·기본 정렬만 다르다. 사업자 알림 링크 `/business-reviews?id=`. 아래 한 화면 설명은 이 둘로 나뉜다.
- 관리자 `/businesses` — 상태 탭(심사 중·승인·반려·정지·탈퇴·전체) + 서비스 필터 + 검색 + 표(국세청 조회 Tag) → 행 Drawer(`?id=`): 신원(가린 값·[평문 보기])·국세청 카드([다시 조회])·사업자등록증 사본·서비스 카드(영향 건수)·처리 기록. 메뉴 묶음 「사업자」(`business.read`).
- 공개 `/business`(상태별 한 화면, 마이페이지 바로가기) · `/business/apply`(신청·수정 Stepper 3단계).

## 모델
- 신원 1줄 + 서비스별 이용 권한 N줄(community V103 구조). 서비스는 **`HOTDEAL`·`AD` 둘**(직접홍보는 광고 한 줄 광고로 합침 — 00 D2).
- 사업자 전용 계정(소셜 없는 별도 가입)은 **두지 않는다**(00 D1). 일반 회원이 신청하고, 승인되면 **역할은 붙이지 않고** 사업자 인증(뱃지)만 생긴다(아래 세션 정리 결정).
- **세션 정리 결정(사용자 2026-10-31)**:
  - **사업자 = 일반 회원 + 사업자 인증(뱃지)**. 승인돼도 글쓰기·댓글·쪽지·소모임 등 일반 회원 활동은 그대로다. 「사업자」라는 말은 이 인증 하나만 가리킨다.
  - 역할은 붙이지 않는다. 기존 `ROLE_MEMBER_BUSINESS` 는 **「홍보 전용 계정」**(따로 가입한 홍보용 계정 — 일반 활동 제한)으로 이름만 바꾼다(역할 ID·권한 묶음 그대로). 1차에는 쓰지 않고, 전용 가입 길을 열 때 쓴다.
  - 신원이 `APPROVED` 인 동안 공개 「사업자」 뱃지(작성자 표시 `PostAuthorView.business` · 공개 프로필). 심사 중(재심사 포함)·반려·정지·탈퇴는 없음, 서비스별 승인은 보지 않음, 본인이 숨길 수 없음(투명성).
  - 구현(2026-10-31): 뱃지 칸 `business` 는 `PostAuthorView`(글 목록·글 보기·댓글·토론 의견·피드)·`DmPartnerView`(쪽지·소모임 사람)·공개 프로필에 있고, 작성자 ID 를 모아 `BusinessEligibility.approvedUserIds` 로 한 번에 채운다. 숨긴 글·지운 댓글·나간 소모임 멤버의 가린 댓글은 false. 역할 이름·설명은 shared `V113__promo_account_role_name.sql`(원래 시드 글자인 칸만 바꿈 — 운영자가 고친 칸은 그대로).
  - 관리자 회원 목록은 **회원 종류와 사업자 인증을 따로 본다**: 「회원 종류」(`memberType`)는 역할 기준 그대로(표시만 일반 / 홍보 전용), 「사업자」는 별도 칸·필터 `businessVerified`(사업자 신원 `APPROVED` 기준).
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
- 권한(기존 `Permission`): `business.read`(목록·상세) · `business.manage`(승인·반려·정지·서비스 처리) — featureReady 켬. **추가**: `business.reveal`·`business.unsuspend`(SENSITIVE). 메뉴는 `AdminScreenRegistry` 에 「사업자」 묶음(2026-10-31: 인증 심사 `businessReviews` · 사업자 목록 `businesses`, 둘 다 `business.read` — 광고·핫딜 신청 건 심사는 각 「광고」·「핫딜」 묶음).
- 감사: `BUSINESS_APPROVE`·`_REJECT`·`_SUSPEND`·`_UNSUSPEND`·`_REVEAL`·`_CERT_VIEW`·`_NTS_CHECK`·`_NTS_OVERRIDE`.
- 회원 알림(계정 알림 — 끌 수 없음): 접수됨·승인·반려·정지·해제·재심사 대기. 관리자 알림: 사업자 신청·재심사.

## API 후보
목업 머리말이 정본 후보다 — admin `useBusinessApi.ts`(`/api/admin/businesses/**`: 목록·상세·nts-check·approve·reject·suspend·reveal·certificate), user-web `useBusinessApi.ts`(`GET/POST/PUT /api/business/me`, `POST /api/business/files`).

## 저장 (제안 — shared 스트림, V111~)
`business_profiles`·`business_services`·`business_nts_checks`(스냅샷)·`business_documents`(등록증 파일 참조)·`business_sensitive_views`(사유·IP) (회원 역할 시드 「사업자 회원」은 만들지 않음 — 모델 절 세션 정리 결정) + 약관 종류 `BUSINESS`.

## 미확정
- 국세청 API 키 발급·호출 한도(공공데이터포털) — dev 배포 때 확인.
- 대표자 본인인증을 신청 조건으로 둘지(00 — 3차 선택 설정).
- 보존 기간(90일·5년)은 법무 확인.

## 서버 구현 결정 (2026-10-30 — 계약과 다르게·더 정한 것)
- **마이그레이션** shared `V111__business.sql`: 계약의 다섯 표 + **`business_history`(처리 기록)** + 회원 알림 종류 `BUSINESS`(CHECK 합집합 — V104 목록 ∪ BUSINESS).
- **「사업자 회원」 역할은 붙이지 않는다.** 기존 `ROLE_MEMBER_BUSINESS`(KIND)는 community 의 별도 사업자 계정용이라 글쓰기·쪽지·소모임 쓰기를 막는다(`BUSINESS_ACCOUNT_RESTRICTED`). 일반 회원에게 붙이면 회원 기능이 사라지므로, 사업자 여부는 `business_profiles` 가 원천이고 판정은 `BusinessEligibility` 한 곳이다. 로그인 정보 `businessStatus` 는 이 표로 채운다. → 2026-10-31 사용자 결정으로 확정(모델 절 「세션 정리 결정」): 회원 목록은 `memberType`(역할)과 `businessVerified`(사업자 표)를 따로 본다.
- **약관 종류 `BUSINESS` 는 넣지 않았다.** `TermsService` 가 모든 `TermsKind` 로 회원 동의 문자열(`T3.P2`)을 만들어, 종류를 더하면 전 회원의 가입 동의 판정이 바뀐다. 동의 버전은 신원 줄(`terms_version`·`privacy_version`, 화면이 보낸 글자 1~20자)에 저장한다. 사업자 약관 게시·버전 관리는 후속.
- **blind index** 는 기존 `BlindIndex`(HMAC-SHA256, `ROBOYAM_INDEX_KEY`, 용도 `business.biz-no`)를 쓴다 — `DataCipher` 에 따로 넣지 않았다. `DataCipher` 에는 **바이트 암호화**(`encryptBytes`·`decryptBytes`)만 더했다(등록증 사본). 키 교체 배치는 글자 칸만 다시 잠그므로 바이트 칸은 옛 키를 `ROBOYAM_DATA_KEY_PREVIOUS` 에 남겨 두는 동안 읽힌다(후속).
- **등록증 사본은 공개 `/media` 폴더가 아니라 DB(`business_documents.content`, 암호문 bytea)** 에 둔다 — `/media/**` 는 로그인 없이 열린다. 형식은 내용 첫 바이트로 판정(PDF·JPEG·PNG), 10MB, 하루 20장(429 `BUSINESS_UPLOAD_LIMIT`). 관리자 열람 `GET …/certificate` 는 `{ url: data:…, mimeType, fileName }` — 화면은 그림 `<img>`, PDF `<iframe>`. 열람 권한 = `business.manage`(사유 없음, 열람 기록·처리 기록·감사).
- **국세청**: 구현 `roboyam.business.nts.provider`(`ROBOYAM_NTS_PROVIDER` odcloud | fake, dev·prod 가짜 기동 차단 — `NtsConfig`), 키 `ROBOYAM_NTS_SERVICE_KEY`(공공데이터포털 일반 인증키 Decoding, 비면 「조회 불가(키 없음)」). 신청·번호 수정 뒤 **서버가 한 번 조회**(제한 2초/4초, 실패해도 신청은 그대로). 상태에 **`NOT_FOUND`(국세청에 없는 번호)** 를 더했다 — 휴·폐업과 같이 승인 불가(409 `BUSINESS_NTS_NOT_ACTIVE`). 조회한 적이 없으면 화면은 `UNAVAILABLE` + 안내, 승인은 409 `BUSINESS_NTS_NOT_CHECKED`. local 가짜 규칙: 등록번호 끝에서 둘째 자리 6 없는 번호 · 7 휴업 · 8 폐업 · 9 조회 불가 · 그 밖 계속.
- **재심사(F7) 표현**: DB 는 `status = PENDING` + `re_review = true`(관리자 목록 「심사 중」, 행에 `reReview`). 회원 화면(`GET /api/business/me`·로그인 `businessStatus`)은 **`APPROVED` + `reReview: true`** — 이미 예약·진행 중인 건이 돌고 화면이 「승인 + 재심사 중」 안내를 보인다. 재심사 중에도 회원은 다시 고칠 수 있고(PUT), 운영자는 정지할 수 있다. 자격 판정은 재심사 중이면 409 `BUSINESS_RE_REVIEW`, 그 밖 409 `BUSINESS_NOT_APPROVED`.
- **오류 코드**: 중복 409 `BUSINESS_DUPLICATE`(목업의 `BUSINESS_NUMBER_TAKEN` 폐기) · 이미 신청 409 `BUSINESS_ALREADY_APPLIED` · 고칠 수 없는 상태 409 `BUSINESS_NOT_EDITABLE` · 재신청 하루 3회 429 `BUSINESS_REAPPLY_LIMIT`(한국 날짜, 처리 기록의 신청 수) · 사본 없음 404 `BUSINESS_FILE_NOT_FOUND`·`BUSINESS_CERT_NOT_FOUND` · 상태 바뀜 409 `BUSINESS_STATE_CHANGED`. 칸 오류는 400 `fields.companyName·bizNo·ownerName·phone·email·address1·certificate·agree`(관리자: `code·text·pendingAction·scheduledAction·runningAction·ntsOverrideReason·reason·restore`).
- **반려 코드 정본 = 관리자 화면 값** `DOC_UNREADABLE·INFO_MISMATCH·NTS_CLOSED·DUPLICATE·OTHER`(정지 `POLICY_VIOLATION·FRAUD·NTS_CLOSED·OTHER`). user-web 은 신원용 `BusinessProfileRejectCode` 를 따로 두었다 — 신청 건의 `BusinessRejectCode` 는 신청 건 백엔드가 정한다.
- **서비스 줄은 신원 처리를 따라간다**(신청 = 심사 중 · 승인 · 반려 · 정지 · 해제 = 승인 · 탈퇴 = OFF). 서비스별 따로 켜고 끄는 API 는 없다(후속).
- **정지 해제 API 추가**: `POST /api/admin/businesses/{id}/unsuspend` `{ restore: EXTEND_RESTORE | KEEP_REFUND, note? }`(`business.unsuspend`) — admin-web `useBusinessApi.unsuspend` 는 있으나 **화면 단추가 없다**(미확정).
- **신청 건 훅**: shared `BusinessApplicationHook`(영향 건수·서비스 카드 건수·`onSuspended`·`onUnsuspended`·`withdrawalCheck`·`onWithdrawn`) — 빈이 없으면 `NONE`(건수 0, 처리 없음). 신청 건 백엔드가 구현 빈 하나를 등록하면 된다. 정지·해제 훅은 신원 처리 트랜잭션 안에서 불린다. 탈퇴는 shared `BusinessWithdrawal.check/withdraw` — 부르는 곳 없음(공개 탈퇴 API 와 함께).
- **알림**: 회원 `BUSINESS` 한 종류(덧붙임 kind `SUBMITTED·APPROVED·REJECTED·SUSPENDED·UNSUSPENDED·RE_REVIEW`, 반려·정지 사유, 링크 `/business`, 회원 이벤트 잠금 ACCOUNT). 관리자 `BUSINESS_APPLICATION_PENDING`(예약 → 켬) · **`BUSINESS_RE_REVIEW_PENDING`(새로)** — 수신 `business.manage`, 링크 `/businesses?id={profileId}`, 신원별 5분 묶음.
- **가린 값**: 등록번호 `***-**-*6789`(끝 4자리만), 대표자 `홍*동`, 연락처 `010-****-5678`, 이메일 `ab***@도메인`, 주소 앞 두 어절. 회원 본인 화면은 대표자·이메일·주소를 평문으로(고칠 칸), 번호·연락처는 가린 값.
- **감사 대상 종류** `BUSINESS`. 평문 열람 감사 detail 은 사유만, 승인은 상호·국세청 상태.
- user-web: 신원 부분(`profile`·`apply`·`update`)과 새 `uploadCertificate` 만 실제 API. **광고 이미지 `uploadFile` 과 신청 건 전부는 목업**(목업 신청 건은 목업 안의 승인 사업자로 판정 — 서버 신원과 따로). 홈의 「목업 상태 바꾸기」 상자와 `mockSetStatus` 는 지웠다. admin-web 목업 파일(`business-mock.ts`)은 지웠다.

## 국세청 진위확인 · 공공데이터 설정 (화면 확정 2026-11-02 — 사용자 결정, 위 「심사 규칙」의 「신청 단계는 체크섬만」을 바꾼다)
**의도**: 신청 때 번호·상호·대표자명·개업일이 국세청 등록 정보와 맞는지 바로 확인해, 남의 번호·가짜 이름으로 사업자 승인(→ 광고·핫딜 돈 흐름)에 이르지 못하게 한다. 서비스 키는 관리자 화면에서 넣는다.
**화면**: user-web `/business/apply` 1단계(개업일 칸·불일치 안내) · admin-web 사업자 심사·목록 Drawer 국세청 카드(진위 줄)·목록 국세청 칸 태그 · 설정 › 시스템 › 공공데이터포털 `/settings/public-data`.

- **외부 API(실제 호출로 확인 2026-11-02)**: 공공데이터포털 「국세청_사업자등록정보 진위확인 및 상태조회」(publicDataPk 15081808) — 상태조회 `POST /api/nts-businessman/v1/status`, 진위확인 `POST /api/nts-businessman/v1/validate`(`{"businesses":[{b_no, start_dt(YYYYMMDD), p_nm, b_nm}]}`), **같은 서비스 키 하나**. 진위 일치 `valid:"01"` + `status`(상태조회 한 줄과 같은 칸) · 불일치 `valid:"02"`(`valid_msg` 「확인할 수 없습니다.」, status 없음 — 국세청에 없는 번호도 02). 대표자명·개업일·상호는 **정확히 같아야** 일치(상호 「파이언넷」 02 · 「(주)파이언넷」 01, 개업일 하루 차이도 02). 본문은 UTF-8.
- **보내는 칸 = 번호·개업일·대표자명·상호(`b_nm`)** — 상호도 보낸다(사용자 결정). 신청 화면 상호 칸이 「(주)·주식회사 표기와 띄어쓰기까지 등록증 그대로」를 안내한다.
- **신청·수정(`POST`·`PUT /api/business/me`)**: 요청에 `openedOn`(`YYYY-MM-DD`, 필수, 오늘 이후 불가 — 400 `fields.openedOn`). 서버가 저장 **전에**(트랜잭션 밖) 진위확인을 부른다.
  - 불일치(02) → **400 `BUSINESS_NTS_MISMATCH`** + `fields.companyName·ownerName·openedOn`(어느 칸인지 국세청이 알려 주지 않는다 — 화면 문구는 화면 i18n). 신청을 받지 않는다.
  - 조회 불가(장애·제한 시간·키 없음) → 지금처럼 신청은 받고 스냅샷 `UNAVAILABLE` — 운영자가 사유를 적어 승인.
  - 일치(01) → 함께 온 `status` 로 상태 스냅샷을 채운다(**신청 때 상태조회를 따로 부르지 않는다** — 진위확인이 대신한다). 휴·폐업이면 신청은 받고 승인 단계에서 막힌다(지금 규칙).
  - 수정: 번호·상호·대표자명·개업일 중 하나라도 바뀌면 다시 부른다(번호를 비워 두면 저장된 번호로). 상호·번호가 바뀌면 재심사(F7 그대로).
  - 승인된 옛 사업자(개업일 없음)가 고칠 때 개업일 필수.
  - **하루 횟수 제한(사용자 결정 2026-11-02)**: 회원당 **하루 5회**(한국 날짜 0시 기준, **신청·수정 합산**, 설정 `roboyam.business.nts.daily-validate-limit` — 0 이면 제한 없음). 국세청을 **실제로 부르게 되는 시도만** 센다(결과 무관 — 불일치 400·조회 불가도 셈, 수정에서 네 칸이 그대로라 부르지 않으면 안 셈, 운영자 [다시 조회]는 안 셈). 넘으면 **429 `BUSINESS_NTS_DAILY_LIMIT`** + `retryAfterSeconds`(다음 한국 0시까지). 이미 신청함·재신청 한도·수정 불가 검사보다 뒤, 국세청 호출 바로 앞. 시도 수는 업무와 따로 먼저 커밋(shared `V126` 표 `business_nts_daily_counts`, 지난 날짜는 DB 배치 `business-nts-count-cleanup` 매일 04:55).
- **진위 결과 `validity`** = `MATCH`(일치) · `MISMATCH`(불일치) · `UNCHECKED`(진위 미확인 — 개업일이 없는 옛 신청, 상태조회만) · `UNAVAILABLE`(조회 불가). 상태 스냅샷(`business_nts_checks`)에 함께 저장, 관리자 `nts.validity`, 상세 `openedOn`, 회원 `GET /api/business/me` 에 `openedOn`.
- **승인 판정**(잠금이 사유보다 먼저): 진위 `MISMATCH` → **409 `BUSINESS_NTS_MISMATCH`**(휴·폐업과 같은 축 — 승인 단추 잠금) · 상태 휴업·폐업·없는 번호 → 409 `BUSINESS_NTS_NOT_ACTIVE`(그대로) · 상태 `UNAVAILABLE` 또는 진위 `UNAVAILABLE`·`UNCHECKED` → 사유(`ntsOverrideReason`) 필수(없으면 400 `fields.ntsOverrideReason`, 감사 `BUSINESS_NTS_OVERRIDE`) · 그 밖(계속 + 일치) → 바로.
- **[다시 조회]**: 개업일이 있으면 진위확인(상태 포함), 없으면 상태조회만 + `UNCHECKED`.
- **이미 들어온 신청(개업일 없음)**: 마이그레이션은 개업일을 비워 두고 기존 스냅샷의 진위를 `UNCHECKED` 로 둔다. 심사 화면 「진위 미확인」 — 등록증으로 확인한 사유를 적어 승인. 승인된 사업자는 그대로(고칠 때 개업일 필수).
- **local 가짜 구현**(`FakeNtsStatusClient`): 등록번호 끝에서 둘째 자리 — `5` 진위 불일치 · `6` 국세청에 없는 번호(상태 NOT_FOUND, 진위 불일치) · `7` 휴업 · `8` 폐업 · `9` 조회 불가 · 그 밖 계속 + 일치. dev·prod 가짜 기동 차단 그대로(`NtsConfig`).
- **공공데이터포털 설정 `/settings/public-data`**(메뉴 시스템 › 공공데이터포털, 조회 `site.read` · 저장·지우기 `site.secret`, 포트원 설정과 같은 규칙):
  - `GET /api/admin/public-data-settings` → `{ ntsProvider: ODCLOUD|FAKE, cryptoKeyConfigured, serviceKey: { source: SCREEN|ENV|NONE, updatedAt, updatedBy }, lastTest }` — 키 값은 내려가지 않는다.
  - `PUT …` `{ serviceKey }`(빈 값·공백·500자 초과 400 `fields.serviceKey`, 암호화 키 없으면 503 `CRYPTO_KEY_MISSING`) · `DELETE …/service-key` → 204(없으면 404 `PUBLIC_DATA_KEY_NOT_FOUND`) · `POST …/test` → `{ result: OK|KEY_INVALID|NO_KEY|TIMEOUT|ERROR, testedAt, testedBy }`(`site.read`, 저장된 키로 실제 국세청 상태조회 한 번 — 구현이 가짜여도 실제로).
  - 키 읽는 차례: **화면에서 저장한 값 → 환경 변수 `ROBOYAM_NTS_SERVICE_KEY` → 없음(조회 불가 `NO_KEY`)**. 저장은 암호문(`SecretEncryptor`). 감사는 칸 이름만.
- **미확정**: 사업자 삭제 기능(없음 — 탈퇴·90일 파기로 정리, 사용자 답 대기) · local 기본 구현을 실제 국세청(`odcloud`)으로 바꿀지.
- **서버 구현 결정(2026-11-02)**:
  - 마이그레이션 shared `V125__business_nts_validate.sql` — `business_profiles.opened_on`(date null) · `business_nts_checks.validity`(기존 줄 `UNCHECKED`, CHECK 넷) · 새 표 `public_data_settings`(칸마다 한 줄 `NTS_SERVICE_KEY`, `v1:` 암호문 + 마지막 저장·지움 + 마지막 연결 시험 `tested_*`). `portone_settings` 는 재사용하지 않았다(감사 분류·화면이 다르다).
  - 외부 호출: shared `OdcloudNtsApi`(HTTP·해석, 키를 호출마다 인자로 — 늘 빈으로 만든다, 연결 시험용) + `OdcloudNtsStatusClient`(키 = `PublicDataSettingsService#ntsServiceKey` 를 조회마다 읽음). 401·403 은 조회 불가 사유 `KEY_INVALID`(새 번들 문구), 연결·응답 실패 `TIMEOUT`, 그 밖 `ERROR`.
  - 신청·수정: 진위확인 결과 스냅샷은 신청·수정과 **같은 트랜잭션**에 남긴다(예전 「신청 뒤 한 번 조회」는 없앴다). 이미 신청함·재신청 한도·수정 불가 상태는 국세청을 부르기 **전에** 막는다. 확인한 번호와 저장할 번호가 그 사이 달라지면 409 `BUSINESS_STATE_CHANGED`. 개업일은 `1900-01-01`~오늘(한국 날짜), 틀리면 400 `fields.openedOn`(`business.field.required`·`business.openedOn.invalid`·`business.openedOn.future`).
  - [다시 조회]가 진위 불일치면 국세청이 상태를 주지 않으므로 상태조회를 한 번 더 해 스냅샷에 상태(없는 번호 등)를 함께 남긴다. 처리 기록 메모는 불일치면 `MISMATCH`(「진위 불일치」), 그 밖은 상태. 감사 `BUSINESS_NTS_CHECK` detail 에 `validity`, `BUSINESS_APPROVE` 에 `ntsValidity`.
  - 조회한 적 없는 신원의 관리자 `nts` 는 `status: UNAVAILABLE` + `validity: UNCHECKED`(승인은 그대로 409 `BUSINESS_NTS_NOT_CHECKED`).
  - 공공데이터 설정: 키는 앞뒤 공백을 지우고 가운데 공백이 있으면 400. 저장·지우기는 마지막 연결 시험을 지운다(옛 키의 결과). 시험 번호 `1248100998`. 감사 `PUBLIC_DATA_SETTINGS_UPDATE`·`_DELETE`(대상 `PUBLIC_DATA_SETTINGS/nts`, 칸 이름만, **일반 분류** — 포트원은 돈 관련). 연결 시험은 감사하지 않고 표에만 남긴다.

## 후속 (이 세션 범위 밖)
- 보존 기간 파기 DB 배치(반려·탈퇴 90일 뒤 암호문·등록증, 붙지 않은 사본 정리) — 표 칸(`erased_at`)만 있다.
- 사업자 약관 게시(약관 종류) · 정지 해제 화면 단추 · 서비스별 처리.
