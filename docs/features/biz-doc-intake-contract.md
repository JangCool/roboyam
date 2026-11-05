# 사업자 서류 접수 기록 (화면 확정 2026-11-05)

**의도**: 계약서 사본(광고·핫딜 신청 건의 수동 결제 근거)과 사업자등록증 사본을 파일 업로드뿐 아니라 **인쇄본(우편·직접 전달)·팩스**로도 받는다. 종이로 받으면 운영자가 「접수 기록」을 남기고, 인쇄본은 계속 보관할지 폐기할지 정한다. 폐기도 기록으로 남는다.
**관련 계약**: `biz-application-contract.md`(수동 결제·계약서 사본), `business-contract.md`(등록증 사본·심사), `ad-contract.md`(광고 일정표 서랍 결제 탭).
**프론트 기준**: admin-web `composables/docintake/**`, `components/docintake/**`, `utils/util-doc-intake.ts`, 연결 `BizAppDepositPanel`·`BizAppReviewPanel`·`BizAppReviewPage`·`BizContractFiles`·`AdBookingDrawer`·`BusinessDetailDrawer`·`BusinessAdminPage` / user-web `pages/business/apply.vue`·`index.vue`, `components/business/BusinessDocSendToCard.vue`.

## 사용자 결정 (2026-11-05)
- 대상: **계약서 사본 + 사업자등록증 사본 둘 다**. user-web 사업자 신청에서도 「우편·팩스로 보내기」를 고를 수 있다.
- 팩스: 접수 방식 「팩스」 + **받은 형태 종이·파일 중 고름**. 종이면 인쇄본과 같은 접수 기록(보관·폐기 상태), 파일(전자 팩스)이면 파일 사본으로 올리고 파일 줄의 들어온 길만 `FAX` 로 표시.
- 계약서는 **종이 접수 기록만 있어도 [예약 확정]의 「계약서 근거 1건 이상」을 채운다**(서버 CHECK 도 바꿈).
- 계약서 인쇄본 폐기는 **같은 건에 대체되지 않은 파일 사본이 있을 때만** — 그중 하나를 스캔본으로 고른다(5년 보존은 파일로 채운다).
- 화면 목업의 가정을 확정으로 본다(아래 「규칙」).

## 화면 경로
- 계약서: `/ad-reviews`·`/hotdeal-reviews` — 입금·계약 확인 대기 건 ①(편집), 운영자 등록 건 `SUBMITTED`(편집), 확정 뒤 결제 기록(처리만). `/ads` 일정표 서랍 「결제」 탭(처리만, 수동 결제 건).
- 등록증: `/business-reviews`·`/businesses` 서랍 ③ 사업자등록증 사본.
- user-web: `/business/apply` 2단계(파일 올리기 / 우편·팩스로 보내기) → `/business` 심사 중 화면의 「등록증 사본을 받으면 심사를 시작해요」 + 보낼 곳 카드.

## 필드·검증 (종이 접수 한 줄)
- 서류 `CONTRACT`(신청 건) | `CERTIFICATE`(사업자 신원).
- 접수 방식 `MAIL`(우편) | `VISIT`(직접 전달) | `FAX`(종이 팩스) — 필수.
- 접수일 `YYYY-MM-DD` 필수, KST 오늘 이전. 접수자 = 로그인 운영자(서버가 채움). 기록 시각.
- 쪽수 1~200(선택) · 메모 ≤200(선택).
- 처리 `KEEP`(계속 보관, 보관 위치 ≤100 선택) | `DISPOSE_PENDING`(스캔 후 폐기 예정) | `DISPOSED`(폐기함 — [폐기 처리]로만, 되돌릴 수 없음).
- 폐기: 폐기일(접수일 ~ 오늘) · 사유 1~200 필수 · 스캔본 파일 id(계약서 필수 — 그 건의 대체되지 않은 파일, 등록증 선택 — 그 사업자의 등록증 파일) · 폐기자 = 로그인 운영자.
- 잘못 기록: 사유 5~200. 지우지 않고 줄을 그어 남기며 개수에 세지 않는다. **폐기한 줄은 잘못 기록할 수 없다**(폐기 기록은 근거).

## 규칙·권한
- 권한: 계약서 = 그 신청 건 도메인의 처리 권한(`ad.manage` / `hotdeal.manage`), 등록증 = `business.manage`.
- 계약서 새 접수 기록·잘못 기록은 계약서 올리기 허용 상태(`AWAITING_DEPOSIT`, 운영자 등록 건 `SUBMITTED`)에서만. [처리 바꾸기]·[폐기 처리]는 확정 뒤에도 된다(종이는 남아 있으니까).
- 계약서 근거 = 대체되지 않은 파일 + 잘못 기록이 아닌 종이 접수(폐기한 줄 포함). [예약 확정]은 근거 ≥ 1 + 입금 확인.
- 등록증: 신청자가 「우편·팩스로 보냄」을 골랐고 등록증 파일도 종이 접수도 없으면 **승인할 수 없다**(화면 잠금 + 서버 409). 새 접수 기록은 탈퇴 상태가 아닐 때만. 등록증 인쇄본은 스캔본 없이도 사유만 적으면 폐기할 수 있다.
- 등록증 인쇄본이 반려·탈퇴 뒤 90일 지나도 보관 중(계속 보관·폐기 예정)이면 「폐기 기한 지남」 경고. 기준 시각 = 그 상태가 된 처리 기록의 가장 최근 시각(자동 파기 배치는 종이를 지울 수 없으므로 경고만).
- 전자 팩스: 계약서는 기존 계약서 올리기에 들어온 길 `FAX` 를 더한다. 등록증은 관리자가 등록증 파일을 올리는 길이 새로 필요하다(그 사업자의 등록증이 된다).
- 처리 기록·감사: 접수·처리 바꾸기·폐기·잘못 기록은 신청 건 처리 기록 / 사업자 처리 기록에 남기고 감사도 남긴다.
- 보낼 곳(우편 주소·팩스 번호)은 서버 설정값으로 둔다. 비어 있으면 user-web 은 「우편·팩스로 보내기」를 보이지 않는다(관리자 설정 화면은 후속).

## API 후보
- 계약서 `/api/admin/biz-applications/{id}`, 등록증 `/api/admin/businesses/{id}` 아래:
  - `GET …/doc-intakes` → `{ target, intakes[], fileChannels{fileId: UPLOAD|FAX}, faxFiles[], mailDeclared }`(프론트 `DocIntakeBundle`)
  - `POST …/doc-intakes` `{ channel, receivedOn, pages, memo, handling, storageLocation }`
  - `POST …/doc-intakes/{intakeId}/handling` `{ handling, storageLocation }`
  - `POST …/doc-intakes/{intakeId}/dispose` `{ disposedOn, reason, scanFileId }`
  - `POST …/doc-intakes/{intakeId}/void` `{ reason }`
  - 쓰기 응답은 그 대상의 묶음 전체. 오류: 400 `fields.*`, 404 `DOC_INTAKE_NOT_FOUND`, 409 `DOC_INTAKE_STATE_CHANGED`(이미 바뀐 줄·허용 안 되는 상태).
- 계약서 올리기 `POST …/contracts` 에 `channel`(`UPLOAD` 기본 | `FAX`). 계약서 파일 줄에 `channel`.
- 관리자 등록증 올리기 `POST /api/admin/businesses/{id}/certificate`(multipart `file` + `channel`).
- 사업자 상세에 `certificateDelivery`(`UPLOAD` | `MAIL_FAX`). 승인 거절 409 `BUSINESS_CERT_NOT_RECEIVED`.
- user-web `POST /api/business/me`(신청·재신청) 에 `certificateDelivery` — `MAIL_FAX` 면 `certificateFileId` 없음. `GET` 보낼 곳(공개 사업자 API 아래).
- 마이그레이션 shared V140 후보: 종이 접수 표, 계약서 파일 `channel`, 등록증 파일 `channel`, 사업자 신원 `certificate_delivery`, 신청 건 종이 근거 수 + `ck_ba_manual_confirmed` 를 「파일 + 종이 ≥ 1」로 바꿈.

## 미확정·후속
- 보낼 곳 관리자 설정 화면(지금은 서버 설정값).
- 인쇄본 폐기 방법(파쇄·위탁) 칸, 「폐기 기한 지남」 대상 모아 보기 화면.
- 보존 기간(5년·90일) 법무 확인은 `business-contract.md`·`biz-application-contract.md` 와 같이.

## 서버 구현 결정 (2026-11-05, shared V140)
- 표 `biz_doc_intakes`(대상 CHECK — 계약서 `application_id` / 등록증 `profile_id`, 처리·폐기·잘못 기록 일관성 CHECK, 스캔본 `scan_contract_file_id`·`scan_document_id`). 메모·보관 위치는 평문(키 교체 대상 아님).
- `biz_contract_files.channel`·`business_documents.channel`(UPLOAD·FAX) + `business_documents.uploaded_by`(운영자 올림 — 회원 하루 한도에 안 셈), `business_profiles.certificate_delivery`, `biz_applications.paper_contract_count` + `ck_ba_manual_confirmed` = 파일 + 종이 ≥ 1. 사업자 처리 기록 행위 ∪ `PAPER_RECEIVED`·`PAPER_HANDLING`·`PAPER_DISPOSED`·`PAPER_VOIDED`·`CERT_UPLOADED`(신청 건 처리 기록도 `PAPER_*` — 회원 공개 처리 기록에는 안 나감).
- 「받았다」 = 지금 등록증 사본(파기 안 됨) 또는 **이번 신청(`submitted_at`) 뒤** 잘못 기록이 아닌 등록증 종이 접수(`BusinessCertificateReceipt`) — 사업자 상세 `certificateReceived`, 회원 `certificateReceived`. 반려 뒤 재신청이면 예전 종이 접수는 안 센다.
- 관리자가 올린 등록증이 지금 등록증이 된다(옛 사본 줄은 신원에 붙은 채 남고 파기 배치가 신원 단위로 지운다). 사업자 상세 등록증에 문서 `id`.
- 묶음 GET 은 조회 권한(`ad.read`·`hotdeal.read` / `business.read`), 쓰기는 처리 권한. 등록증 잘못 기록은 상태 제한 없음(열린 줄이면).
- user-web 수정(`PUT`)은 늘 `UPLOAD`. 보낼 곳 `GET /api/business/doc-send-to` → `{postcode, address, recipient, fax}`(빈 칸 null) — api 설정 `roboyam.biz.doc-send-to.*`(환경 변수 `ROBOYAM_BIZ_DOC_POSTCODE·ADDRESS·RECIPIENT·FAX`, 기본 빈 값). 주소·팩스 둘 다 비면 `MAIL_FAX` 신청 400 `fields.certificateDelivery`.
- 감사: `BIZ_APP_PAPER_RECEIVE·HANDLING·DISPOSE·VOID`(돈 — 5년), `BUSINESS_PAPER_RECEIVE·HANDLING·DISPOSE·VOID`·`BUSINESS_CERT_UPLOAD`.
- 테스트는 쓰지 않았다(컴파일·타입 검사만). V140 은 로컬 DB 에서 트랜잭션으로 돌려 보고 되돌림.
