# 회원 탈퇴 계약

> 의도: 회원이 언제든 탈퇴를 신청할 수 있게 하되, 남은 거래·환불·광고가 있으면 「탈퇴 진행 중」으로 두었다가 끝나면 자동 완료한다. 돈·글·재가입에서 회원이 모르고 손해 보지 않게 한 화면에 사실을 먼저 보여 준다.
> 규칙의 정본: `docs/domain/commerce.md` 17절(2026-11-03 개정 D1~D6). 법 조사·데이터 처리표: `docs/plans/member-withdrawal/00-analysis.md`(4절이 표별 처리의 정본). 화면 확정 2026-11-03(사용자 「좋아 구현해」).

## 화면
- `/settings/account` 맨 아래 「회원 탈퇴」 줄 → `/settings/account/withdraw`(설정 왼쪽 메뉴 계정 아래).
- `/settings/account/withdraw` — 한 화면(단계·만류 팝업 없음): 사라지는 것 · 남는 것 · 끝나야 하는 일 · 정할 것(배송완료 7일 안 주문 / 충전 포인트 / 진행 중 광고·핫딜 / 모임장인 소모임) · 재가입 가능일 · 탈퇴 사유(선택) · 본인 확인 · 단추 하나. 진행 중이면 같은 화면이 남은 일·예상 완료·「탈퇴 취소」.
- `/withdrawn` — 완료 안내(로그인 불필요). 즉시 완료 응답 값을 `useState` 로 들고 감, 새로고침하면 일반 안내.
- 서버 연결됨(2026-11-03, 목업 파일 삭제). 탈퇴 진행 중이면 사이트 전체에 안내 띠(`components/account/WithdrawalBanner.vue`, `RestrictionBanner` 아래).
- 탈퇴 완료 회원의 글·댓글 작성자 `name` 은 서버가 null 로 준다(닉네임 NULL) — 화면이 「탈퇴한 회원」으로 그린다.

## API
응답·요청 모양의 정본은 `apps/user-web/app/composables/account/api/useWithdrawalApi.types.ts`.
- `GET /api/me/withdrawal` → `WithdrawalView`. 로그인 필요, 탈퇴 완료 회원 401.
- `POST /api/me/withdrawal` `WithdrawalRequest` → `WithdrawalResult`
  - `COMPLETED`: 같은 트랜잭션에서 익명화 끝 + 모든 세션 종료.
  - `SCHEDULED`: 남은 일(선택과 무관한 것 + 「기다리기」를 고른 것)이 있을 때.
  - 오류: `400 VALIDATION`(`fields.password|forfeitConsent|groupChoices|reasonNote`), `401 CURRENT_PASSWORD_WRONG`, `429 LOGIN_LOCKED`(+`retryAfterSeconds`, 비밀번호 실패 잠금 공유), `401 REAUTH_REQUIRED`(소셜 전용·최근 10분 로그인 아님), `409 WITHDRAWAL_ALREADY_SCHEDULED`.
  - 포기를 고르면 `forfeitConsent` 금액이 서버 계산과 같아야 한다(다르면 `400 fields.forfeitConsent` — 금액이 바뀌었으니 다시 보라).
- `DELETE /api/me/withdrawal` → 204 · `409 WITHDRAWAL_NOT_SCHEDULED`.
- `GET /api/auth/me`(로그인 정보)에 `withdrawalScheduled: boolean` — 진행 중이면 화면이 안내 띠·탈퇴 화면 링크를 보인다.
- 가입 거절(이메일 가입·소셜 가입 완료): `409 REJOIN_BLOCKED` + `retryAfterSeconds` + 최상위 `rejoinAvailableAt`. `GET /api/auth/email-available` 은 제한을 알리지 않는다(가입 시도 때만).
- 탈퇴 진행 중에 막힌 기능은 새 코드가 아니라 정지와 같은 기존 코드(`ACCOUNT_RESTRICTED` 계열)로 거절한다. 공구 반품 요청·환불 계좌 입력·주문 보기는 막지 않는다.

## 필드·검증
- `reasonNote` 0~200자(정규화 뒤), `reason` 6종(`NOT_USING·PRIVACY·INCONVENIENT·OTHER_SERVICE·REJOIN·OTHER`) 또는 null.
- `groupChoices`: 내가 모임장인 ACTIVE 소모임만, `TRANSFER` 는 그 모임의 지금 멤버만. 빠진 모임은 `AUTO`.
- 선택값이 필요 없는 상황에서 온 선택은 무시. 필요한데 null 이면 기본값(배송·광고 = `WAIT`, 충전 = `REFUND`).

## 권한·정책
- 탈퇴 진행 중(`users.status = WITHDRAWING`): 로그인은 되고 남은 일 처리(주문 보기·반품 요청·환불 계좌 입력)·탈퇴 취소만. 새 활동(글·댓글·공구 참여·결제·충전·포인트샵 구매·소모임 가입/개설·쪽지 보내기) 막음. 공개 프로필·회원 검색·쪽지 받는 사람 검색에서 빠짐, 쪽지 받기 불가. 글·댓글은 완료 전까지 닉네임 그대로.
- 정지·기능 제한 중에도 탈퇴 가능, 재가입 제한 = max(30일, 제재 끝) — 영구는 상한 1년(설정값).
- 완료 처리·데이터 정리는 분석 문서 4절 표대로. 결제 이력이 있으면 연락용 이메일 분리 보관(파기 = 마지막 거래 시각 + 5년, 설정값 — 세법 보존 기준일은 거래 기록에만, 2026-11-04).
- 감사: `MEMBER_WITHDRAW_REQUEST`·`MEMBER_WITHDRAW`(MONEY — 포기 동의 증빙)·`MEMBER_WITHDRAW_CANCEL`.
- 메일: 완료 메일(익명화 전 주소로, **이메일 인증한 주소에만** — 미인증 주소는 남의 것일 수 있다), 신청 10일 뒤에도 진행 중이면 진행 상태 메일 1회. 둘 다 outbox 를 거치지 않고 커밋 뒤 바로 보낸다.
- 모임장 처리(넘기기·해산·자동 위임)는 **완료 때** 한다 — 진행 중에 취소하면 모임은 그대로. 완료 때 넘길 대상이 멤버가 아니면 자동 위임 규칙으로.
- 본인 확인의 「최근 10분 로그인」은 `users.last_login_at` 기준(세션 인증 시각은 저장하지 않는다).
- `supportEmail` = 설정 `roboyam.withdrawal.support-email`, 비면 메일 서버의 보내는 주소(사이트 설정에 연락 메일 칸이 없다).
- 배치: `member-withdrawal-complete`(Java, 30분마다 — 남은 일 다시 보고 끝났으면 완료, 10일 안내), `member-withdrawal-cleanup`(`R__batch_36`, 매일 04:30 — 탈퇴 회원 글·댓글 IP 작성 90일 지난 것 비움, 탈퇴·반려 사업자 신원 90일 뒤 암호문·등록증 파기).
- 재가입 제한은 이메일 가입·소셜 가입·본인인증 모두에서 확인.

## 관리자 (화면 확정 2026-11-04 — 사용자 「확정, 서버 구현해」)
- 화면: `/members/withdrawals`(회원 › 탈퇴 관리, 회원 관리 다음, `pi pi-user-minus`) — 요약 4칸(진행 중·오늘 완료·이번 달 완료·10일 넘긴 진행 중), 상태 탭·기간·회원 번호, 서랍(남은 일 + 관리 화면 링크, 고른 것, 포기 동의, 사유, 강제·대리 취소 정보, 처리 이력 타임라인), 사유 분포. 회원 목록·상세에 `WITHDRAWING` 상태와 탈퇴 블록. 대시보드 회원 현황의 「탈퇴 대기」·「오늘 탈퇴」가 이 화면 조건으로 이어진다.
- 응답·요청 모양 정본: `apps/admin-web/app/composables/member/api/useMemberWithdrawalApi.types.ts`(머리말에 API 목록·오류 코드).
- 권한: 보기 `member.read`. 강제 탈퇴·대리 취소 = 새 `member.withdraw`(SENSITIVE, 기본 SUPER). 연락용 이메일 열람은 기존 `member.withdrawn.contact`.
- **강제 탈퇴**: 사유 종류 `UNDER_14·POLICY_VIOLATION·ON_BEHALF·OTHER` + 운영 메모 5~500자 필수. 회원 탈퇴와 같은 서비스 — 선택값은 회원에게 유리한 기본값(배송·광고 WAIT, 충전 REFUND, 모임 AUTO)으로 고정, 남은 일이 있으면 운영자도 건너뛰지 못하고 진행 중. 본인 확인 없음. 감사 `MEMBER_WITHDRAW_FORCE`.
- **대리 취소**: 회원이 신청한(`source = MEMBER`) 진행 중 탈퇴만 — 회원이 고객센터·전화·이메일로 취소를 요청했을 때. `memberConfirmed: true` + 요청 경로 `CS_INQUIRY·PHONE·EMAIL·OTHER` + 운영 메모 5~500자 필수. 운영자 강제 탈퇴는 대리 취소 불가(`409 WITHDRAWAL_NOT_MEMBER_REQUESTED`). 결과는 회원 본인 취소와 같다(신청 전 상태로, 기록은 CANCELED). 감사 `MEMBER_WITHDRAW_CANCEL`(운영자·경로·메모).

## 미확정
- 관리자 대시보드 「오늘 탈퇴」는 이미 연결됨(9e5a2148) — 카드 링크만 탈퇴 관리 화면으로.
- 충전·충전 환불(커머스 3단계) 전이라 유상 잔액은 늘 0 → 「환불받고 탈퇴」의 실제 환불 연결은 3단계 때.
- 거래 기록 5년 자동 파기 배치(커머스 17-3) — 탈퇴와 별개로 아직 없음.
- 탈퇴자 닉네임을 바로 남이 쓸 수 있게 둔다(묶지 않음) — 사칭 문제가 보이면 다시 정한다.
