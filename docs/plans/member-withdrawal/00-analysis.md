# 회원 탈퇴 — 조사·분석·제안 (2026-11-02)

> 기준: develop `4bdc65d7`. 법 조사는 웹 사본(casenote·nepla·itwiki)과 정부 보도자료 기준이며 **법률 자문이 아니다** — 오픈 전 law.go.kr 원문 대조 + 전문가 확인(10절).
> 정본 관계: 커머스 쪽 탈퇴 규칙의 정본은 `docs/domain/commerce.md` 17절이다. 이 문서는 그 결정을 법 조사로 다시 검토해 **바꾸자는 제안(3절)** 을 담는다 — 사용자가 고르면 17절을 고친다.

## 1. 결론 요약

1. **스키마·부품은 탈퇴를 이미 대비하고 있다.** 회원 행은 지우지 않고 `status=WITHDRAWN` + `deleted_at` + 익명화하는 설계(V85 가 포인트 FK 의 CASCADE 를 일부러 뺐다). 재가입 해시(`IdentityWithdrawalRecorder`), 연락용 이메일 분리 보관(`WithdrawnContactStore`), 사업자·광고 탈퇴 훅(`BusinessWithdrawal`·`BizApplicationHookImpl`), 핫딜 정리(`DealWithdrawal`), 세션 종료(`MemberSessionTerminator.endAll`), 원장 사유 `WITHDRAW_FORFEIT`, 정리 배치(R__batch_20·28) 가 있다. **없는 것은 이것들을 묶는 탈퇴 서비스·API·화면**이다.
2. **법적으로 가장 큰 위험은 「탈퇴를 막는 것」이다.** 개인정보보호법 제37조②4호는 「해지 의사를 명확히 밝힌」 경우 동의 철회를 거절할 수 없게 하고, 제38조④는 탈퇴를 가입보다 어렵게 하지 못하게 하며, 전자상거래법 제21조의2①4호(2025-02-14 시행 다크패턴 규제)는 정당한 사유 없는 탈퇴 방해를 금지한다. 개인정보위는 2025-12 쿠팡의 「멤버십 해지가 끝나야 탈퇴」를 위반 소지로 봤다. → 17-1 의 「진행 중 주문·청약철회 7일·정지 중이면 탈퇴 불가」는 **「탈퇴 예약」(즉시 신청 접수 → 남은 일이 끝나면 자동 완료)** 로 바꾸는 것을 권장한다.
3. **회원 불이익을 없애는 핵심**: 돈(충전 포인트·광고 환불 대기·미사용 아이템)은 탈퇴 화면에서 **금액을 계산해 먼저 보여 주고 기본값은 환불**, 포기는 명시 동의. 글은 남는다는 사실·지우는 방법을 미리 알리고 탈퇴 뒤에도 블라인드 요청 창구를 둔다.
4. **3절 결정 D1~D6 은 2026-11-03 사용자가 모두 승인했다** — `commerce.md` 17절·18절 시나리오(P30~P30e)에 반영함.

## 2. 지금 상태 (코드 기준)

### 2-1. 있는 것
| 항목 | 위치 | 상태 |
|---|---|---|
| `users.status` WITHDRAWN · `deleted_at` · `email` NULL 허용(부분 유일 색인) | `V2__auth.sql:7,18,20,22` | 쓰는 코드 없음(테스트만 UPDATE 로 흉내) |
| 탈퇴 판정 `UserEntity.isGone()` = WITHDRAWN 또는 deleted_at | `UserEntity.java:189` | 로그인 401·세션 401·쪽지·알림·메일·공구·포인트 조회 등 전부 이 기준. SQL 쪽은 둘 중 하나만 보는 곳·둘 다 보는 곳이 섞임 → **탈퇴는 둘 다 채운다** |
| 재가입 제한 해시 `identity_withdrawals` + `recordEmail/recordSocial/recordIdentity` | `V84:88`, `IdentityWithdrawalRecorder.java:78-115` | 부르는 곳 없음. 본인인증만 `blockedUntil` 확인, **이메일·소셜 가입은 확인 안 함** |
| 연락용 이메일 분리 보관 `withdrawn_member_contacts` + `store/reveal`(권한 `member.withdrawn.contact`) | `V84:106`, `WithdrawnContactStore.java` | store 부르는 곳 없음, 「마지막 거래일」 계산 없음 |
| 사업자 `BusinessWithdrawal.check/withdraw(forfeitRunning)` · 신청 건 훅(대기 취소·시작 전 전액 환불·진행 중 FORFEIT) | `BusinessWithdrawal.java:40-59`, `BizApplicationHookImpl.java:181-219` | 부르는 곳 없음 |
| 회원 핫딜 `DealWithdrawal.withdraw` | `DealWithdrawal.java:17-47` | 부르는 곳 없음 |
| 포인트 사유 `WITHDRAW_FORFEIT`(EXPIRE) | `V85:81`, `PointReason.java:54` | 쓰는 코드 없음 |
| 배치 `member-privacy-retention`(해시 30일·연락처 purge_after) · `member-notification-cleanup`(탈퇴 회원 알림) | `R__batch_28`, `R__batch_20` | 동작 중 |

### 2-2. 없는 것
- 탈퇴 API·서비스·화면(`/settings/account` 에 탈퇴 줄 없음 — `pages/settings/account/index.vue:169` 주석), 관리자 강제 탈퇴(만 14세 미만 확인 시 필요 — `signup.ts:68`), 탈퇴 감사 행위.
- 계정 정지(`SUSPENDED/BANNED`) 를 거는 기능 — 지금 실효 제재는 기능 제한(`member_restrictions`) 뿐.
- 소모임 정리(`GroupCleanupService.eraseUser` 는 community 에만 있음), 게시판 스태프·사업자 역할 자동 해제(문서엔 있음, 코드 없음).
- 닉네임 이미지 **디스크 파일** 삭제(`V72:12` 「탈퇴 흐름이 생기면 지운다」), 사업자 파기 배치(`erased_at` 칸만).
- 충전·충전 환불(커머스 3단계) — 지금 유상 잔액은 늘 0. 탈퇴의 「환불/포기」 분기는 설계·화면만 하고 실제 분기는 3단계 때 켠다.

### 2-3. 회원 행을 남기므로 생기는 일
FK 의 `ON DELETE CASCADE` 는 회원 행을 지울 때만 작동한다. 행을 남기는 설계라 **CASCADE 30개 표와 FK 없는 표 모두 탈퇴 트랜잭션이 직접 정리해야 한다**(4절 표가 그 목록).

### 2-4. 문서·코드 불일치 (고쳐야 함)
1. `commerce.md:830` 「`payment_cancels` REQUESTED/FAILED」 → 실제 상태는 `PENDING·SENDING·SUCCEEDED·GAVE_UP·MANUAL`(pg_status 가 REQUESTED/FAILED, `V97:90-92`). 「끝나지 않은 환불」 = `PENDING·SENDING·GAVE_UP·MANUAL`.
2. `V70:13`(탈퇴자의 쪽지는 상대 쪽지함에 남긴다 — 현재 동작) vs `member-social-contract.md:128`(쪽지 정리는 탈퇴 때 정함) → 4절에서 「남긴다」로 확정 제안.
3. `operator-permissions.md:46` 「탈퇴 시 게시판 지정 자동 해제」 — 코드 없음(계산으로 숨기기만).
4. 이메일 미인증 모임장은 넘기기·해산이 `requireWriter` 로 막혀, 「모임장이면 탈퇴 차단」 규칙에서 **영영 탈퇴 못 한다**.

## 3. 17절 변경 결정 — **승인 2026-11-03(사용자: 「전부 받아들이기」)**

| # | 지금 결정(17절) | 법 조사 판정 | 제안 |
|---|---|---|---|
| D1 | 진행 중 공구 참여·열린 취소/반품·미완 환불 중 **탈퇴 불가** | 위험 — 개보법 37조②4호(해지 의사 표시 시 거절 불가), 전상법 21조의2①4호, 쿠팡 사례 | **탈퇴 예약**: 신청은 즉시 받고 계정을 「탈퇴 진행 중」으로 → 남은 건이 끝나면 배치가 자동 완료. 그 사이 로그인하면 남은 건 처리·탈퇴 취소만 되는 제한 화면. 남은 일이 없으면(대부분 회원) 즉시 완료 |
| D2 | 배송완료 7일(청약철회 기한) 안 **탈퇴 불가** | 위험·불필요 — 전상법 6조②로 탈퇴 뒤에도 거래 기록·이메일 보존 가능, 17-1 이 하자 반품은 이미 그렇게 처리 | 회원이 고른다: 「지금 탈퇴(반품은 고객센터 메일로)」 / 「반품 기한까지 기다렸다 자동 탈퇴」(기본) |
| D3 | 정지·기능 제한 중 **탈퇴 불가** | 위반 소지 — 37조② 거절 사유에 제재 회피 방지 없음. 관행은 재가입 제한으로 막음(카카오톡 비정상 탈퇴 60일) | 탈퇴 허용 + 재가입 제한을 **max(30일, 제재 끝나는 날)** 로. 영구 정지는 1년 등 상한(설정값). 처리방침에 기재 |
| D4 | 결제 이력 없는 회원의 무상 원장은 **탈퇴 때 정리** | 법은 허용하지만 원장 불변 트리거·`R__batch_29` 불변식(원장 합 = 잔액 = 묶음 합)과 충돌 | 무상 원장도 **지우지 않고** `WITHDRAW_FORFEIT` 로 잔액 0 → 일반 5년 파기 배치에 맡김. 회원 행이 익명이라 개인정보가 아니다. 트리거 예외를 안 만든다 |
| D5 | 거래 기록·연락용 이메일 = **마지막 거래일 + 5년** | 세법 주의 — 국세기본법 85조의3②는 **법정신고기한 다음날부터 5년** | 거래 기록 = 마지막 거래일이 속한 해의 다음 해 6월 1일(종합소득세 신고기한 다음날) + 5년. **연락용 이메일은 마지막 거래 시각 + 5년**(2026-11-04 정정 — 이메일은 세무 증빙이 아니라 전상법 식별정보, 세법 기준으로 늘리면 개인정보 과보유) |
| D6 | 모임장이면 **탈퇴 차단**(넘기기·해산 유도) | 법 위험은 작지만 미인증 모임장 교착(2-4 #4) | 탈퇴 화면에서 넘기기/해산을 고르게 하고, **안 고르면 자동 위임**(매니저 → 가장 오래된 멤버), 멤버가 없으면 해산(해산 패널티 없음) |

맞다고 판정된 결정(그대로 둠): 충전 포인트 「환불받고/포기하고」 선택(단 **두 버튼 같은 모양, 기본은 환불**), 7일 지난 충전 10% 공제(게임 표준약관 30조·공정위 팬클럽 시정 10% 와 같음 — 약관 사유는 「탈퇴」가 아니라 「청약철회 기간 경과」), 무상 포인트·아이템 소멸(고지 전제), 회원 행 익명화, 이메일 하나만 암호화 분리 보관(전상법 6조②·시행령 6조②3호·개보법 21조③), 재가입 해시 30일(관행 7일~1년 중 짧은 쪽, 처리방침 기재 필수), 30일 뒤 완전히 새 계정, 글·댓글 유지.

## 4. 탈퇴 시 데이터 처리안 (D1~D6 제안을 받아들였을 때)

처리 시점: **신청** = 탈퇴 진행 중이 되는 순간, **완료** = 익명화 트랜잭션. 남은 일이 없으면 둘이 같은 트랜잭션.

| 영역 | 표 | 신청 때 | 완료 때 | 남기는 이유·기간 |
|---|---|---|---|---|
| 계정 | `users` | 상태 = 탈퇴 진행 중(새 상태 값, 5-1) | `email`·`password_hash` NULL, `status=WITHDRAWN`, `deleted_at`, `last_login_at` NULL. `terms_version/agreed_at`·`age_confirmed_at` 은 남김(동의 증빙, 식별 불가) | 껍데기 행 영구(글·원장이 참조) |
| | `profiles` | 공개 프로필·검색·쪽지 수신 중단 | `nickname` NULL(5-2), `bio`·`profile_image`·`nickname_image` NULL, 등급 유지 | |
| | `profile_nickname_history`, `member_grade_changes` | | 삭제 | |
| 로그인 | `user_identities` | | `recordSocial` 뒤 삭제(**지우기 전에 식별값을 읽는다**) | 해시 30일+(D3) |
| | `auth_tokens`, `spring_session` | 다른 기기 세션 종료(`endAll`) | 남은 토큰 삭제 | |
| | `identity_verifications` | | `recordIdentity`(해시 옮기고 줄 삭제) | 해시 30일+ |
| | 재가입 해시(이메일) | | `recordEmail` | 30일+ |
| 설정 | `member_display_preferences`·`member_view_settings`·`member_notification_preferences`·`dm_user_states`·`blocked_words` | | 삭제 | |
| 관계 | `user_blocks`·`user_memos` | | **양방향** 삭제(남의 나에 대한 메모도 — 나에 관한 정보) | |
| 역할 | `user_roles`·`board_staff` | | 삭제(게시판 스태프·사업자 회원 역할 해제) | |
| 글 | `posts`·`comments` | | 본문 유지, 작성자 「탈퇴한 회원」(이미 gone 판정으로 표시). `author_ip` 는 작성 90일 지난 것 즉시 NULL, 안 지난 것은 90일째 배치가 NULL(통비법 접속기록 3개월) | 약관 이용허락 조항 필요 |
| | 반응(`post_likes`·`comment_likes`·`comment_dislikes`·`post_stances`·`poll_votes`) | | **남김**(집계가 바뀌지 않게, 회원 번호만 있어 식별 불가) | — 결정 여지: 지우면 남의 글 추천 수가 줄어든다 |
| | `debate_pin_requests`·`group_post_publish_requests`(대기) | 대기 건 취소 | | |
| 파일 | 닉네임 이미지(`nickname_image_requests` image/still 키, 승인본) · 프로필 이미지 | | **디스크 파일 삭제** + 행 삭제 | |
| | 글 첨부 이미지(`files` POST_IMAGE) | | 글과 함께 남김 | |
| 쪽지 | `dm_boxes`(내 쪽지함) | | 내 쪽지함 삭제. 상대 쪽지함의 대화는 남김(「탈퇴한 회원」, 1년 배치) — 상대의 기록이다 | 1년 |
| 알림 | `member_notifications` | | 삭제(배치도 지움) | |
| 메일 | `mail_outbox` PENDING | | 탈퇴 완료 메일만 남기고 CANCELED(5-4) | |
| 소모임 | `groups`(모임장) | D6 처리(넘기기·해산·자동 위임) | | |
| | `group_members`·`group_managers`·`group_event_responses`·가입 신청 | | 탈퇴(`leave` 와 같은 처리 — 그 모임 안 내 글 가림) + 행 삭제, 대기 공개 요청 `AUTHOR_LEFT` | |
| 포인트 | `point_balances`·`point_ledger`·`point_lots` | | 무상 잔액 `WITHDRAW_FORFEIT`. 유상(3단계): 환불 완료 또는 포기 동의 뒤 `WITHDRAW_FORFEIT`. `revoke_debt` 는 그대로(계정이 닫혀 더 생기지 않음) | 원장 5년(D4·D5) |
| 포인트샵 | `user_items`·`user_equipped` | 7일 안 미사용 아이템 환불 안내(7-3) | `REVOKED`, 장착 삭제 | 구매 기록 5년 |
| | `point_shop_refund_requests` PENDING | 남은 일(운영자 결정 기다림) | | |
| 공구 | 진행 중 참여·열린 요청·미완 `payment_cancels`·`commerce_bank_transfers` | 남은 일(D1·D2) | | |
| | `group_buy_participations` 배송지·연락처·현금영수증 암호문 | | 즉시 마스킹 | 주문·금액·상태는 5년 |
| | `group_buy_watches`·`group_buy_supply_choices` | | 삭제 | |
| | Q&A·리뷰 | | 남김(「탈퇴한 회원」) — Q&A 3년 배치 | |
| | 연락용 이메일 | | 결제 이력 있으면 `WithdrawnContactStore.store`(마지막 거래일 = 결제·환불·공구·포인트샵·광고 결제 중 최근) | D5 |
| 사업자 | `business_profiles` | `BusinessWithdrawal.check` → 진행 중 광고·핫딜이 있으면 회원이 고른다: 「끝날 때까지 기다렸다 자동 탈퇴」(기본) / 「포기하고 지금 탈퇴」(**잃는 금액·환불 대기 금액을 보여 주고 동의**) | `withdraw` → WITHDRAWN, 서비스 OFF. 90일 뒤 암호문·등록증 파기 배치(이미 결정, 미구현) | 신청 건 스냅샷(상호·끝4자리) 5년. 전체 사업자번호 보관 필요 여부는 세무사 |
| 핫딜 | `post_deals`·`deal_end_requests` | | `DealWithdrawal.withdraw` | |
| 신고·제재 | `reports`·`report_cases`·`member_restrictions` | | 남김(신고 기록 보유 배치, 제재 기록) | 기존 배치 |
| 감사 | `audit_logs` | `MEMBER_WITHDRAW_REQUEST` | `MEMBER_WITHDRAW`(MONEY 분류 — 포기 동의 증빙) | 5년 |
| 탈퇴 기록 | 새 표 `member_withdrawals` | 신청 시각·사유(선택)·고른 것·**포기 동의(금액·시각)**·남은 일 | 완료 시각 | 5년(돈 포기 증빙) |

## 5. 설계 메모

1. **탈퇴 진행 중 상태**: `users.status` 에 새 값(예: `WITHDRAWING`)을 더하면 공개 노출 차단은 gone 판정에 넣고, 로그인은 제한 모드로 허용해야 한다 — `ACTIVE` 를 보는 곳 전부를 훑어야 해서 백엔드 단계에서 영향 범위를 먼저 센다. 대안은 상태는 그대로 두고 `member_withdrawals` 행으로 쓰기 경로를 막는 것(누락 위험이 더 큼). 권장: 새 상태 값.
2. **닉네임**: `profiles.nickname` 은 `NOT NULL` + `lower()` 유일 + 최대 12자라 「탈퇴한 회원」 공통값이나 `탈퇴#<19자리 id>` 를 넣을 수 없다. 권장: 마이그레이션으로 NULL 허용(유일 색인은 NULL 을 여럿 허용). 화면은 이미 gone 판정으로 「탈퇴한 회원」을 그린다. 결정 여지: 탈퇴자 닉네임을 바로 남이 쓸 수 있게 할지(사칭 방지로 30일 묶기 — 재가입 해시처럼 닉네임 해시).
3. **본인 확인**: 비밀번호 1회. 소셜 전용 회원은 최근 10분 안 로그인이 아니면 다시 로그인. 가입보다 단계가 많지 않게.
4. **탈퇴 완료 메일**: `mail_outbox` 는 보낼 때 `users.email` 을 읽어 NULL 이면 취소한다 → 완료 메일은 익명화 **전에** 주소를 담아 넣는 경로가 필요. 내용: 완료 사실, 남긴 항목·기간, 고객센터 연락처(탈퇴 뒤 반품·환불·하자), 글 블라인드 요청 방법, 다시 가입할 수 있는 날.
5. **재가입 제한 연결**: 이메일 가입·소셜 가입에서 `blockedUntil(EMAIL/SOCIAL)` 확인을 더한다(지금 없음). 문구는 로그인 실패처럼 존재 노출을 줄이되 「탈퇴 후 N일 뒤 가입할 수 있어요」는 알려야 회원이 헤매지 않는다 — 결정 여지.
6. **관리자**: 탈퇴 진행 중 목록(남은 일·예상 완료일), 강제 탈퇴(만 14세 미만 등 — 같은 서비스, 사유 필수·감사). 만 14세 미만은 가입 불가라 법정대리인 절차는 필요 없다.
7. **커머스 테스트 의무**: 공구·결제·포인트가 엮이므로 commerce.md 18절대로 규칙마다 테스트(P28~P30 은 D1·D3 를 받아들이면 기대 결과가 바뀐다).

## 6. 탈퇴 화면 (다크패턴 규제 기준)

- 진입: 설정 → 계정 → 「회원 탈퇴」 한 줄(숨기지 않음, 앱·웹 모두).
- 한 화면에 사실만: **사라지는 것**(적립 포인트 N P, 아이템 N개, 등급·출석), **남는 것**(글·댓글 — 「탈퇴한 회원」 표시, 거래 기록 법정 기간), **남은 일**(진행 중 주문·반품·환불·광고 — 각 건 링크와 「끝나면 자동 탈퇴」), **돈**(충전 포인트 환불 예상액·수수료, 같은 모양의 「환불받고」/「포기하고」, 기본은 환불), 재가입 가능일.
- 「글을 지우고 싶으면 탈퇴 전에 지우세요」 + 내 글 목록 링크(선택, 강제 단계 아님).
- 탈퇴 사유는 **선택**. 만류 팝업 없음(위 사실 고지가 그 역할). 확인 후 비밀번호 → 끝.
- 완료 화면: 즉시 완료 또는 「탈퇴 진행 중 — 남은 일 N건, 끝나면 자동 완료, 취소하려면 다시 로그인」.

## 7. API 후보 (화면 확정 뒤 계약으로 옮김)

- `GET /api/me/withdrawal` → `{ state: NONE|SCHEDULED, losses{freePoints, items}, money{paidPoints, refundEstimate, fee}, pending[{code, count, waitUntil|null, link, choice?}], rejoinAvailableAt, groupsLed[] }`
- `POST /api/me/withdrawal` `{ password?, reason?, paidPointChoice: REFUND|FORFEIT, deliveredChoice: NOW|WAIT, bizChoice: WAIT|FORFEIT, groupChoices[] , forfeitConsent{amounts} }` → `{ state: COMPLETED|SCHEDULED, pending[] }`
- `DELETE /api/me/withdrawal` — 진행 중 취소
- admin: `GET /api/admin/members/withdrawals`, `POST /api/admin/members/{id}/withdraw`(사유)
- 마이그레이션 V132 이후 빈 번호(`member_withdrawals`, users 상태 값, nickname NULL 허용), 배치 `R__batch_36` 이후 빈 번호(예약 완료·IP 90일·사업자 파기) — 병합 직전 develop 번호를 다시 확인.

## 8. 개인정보처리방침·약관에 넣을 것 (운영자가 `/settings/terms` 에서 씀)
- 탈퇴 방법(설정 → 계정 → 회원 탈퇴), 파기 시점(즉시 / 남은 거래가 끝나면).
- 보존 항목·근거·기간: 거래 기록(전상법 시행령 6조 — 계약·결제 5년, 분쟁 3년, 표시광고 6개월, 세법 증빙), 연락용 이메일(전상법 6조② — 분리 보관), 재가입 제한 해시(이메일·소셜·본인인증, 부정 이용 방지, 30일·제재 시 연장), 접속 기록 3개월(통비법), 포기 동의 기록 5년, 백업 보관 기간.
- 이용약관: 게시물 이용허락과 탈퇴 후 존속, 탈퇴 후 블라인드 요청 방법, 무상 포인트·아이템 소멸, 충전 포인트 환불 규칙(수수료 사유 = 청약철회 기간 경과).

## 9. 회원 불이익 점검표
| 위험 | 막는 법 |
|---|---|
| 탈퇴하고 싶은데 못 함 | 탈퇴 예약(D1·D2·D3), 모임장 자동 위임(D6) |
| 돈을 잃음 | 환불 기본·금액 먼저 표시·포기는 금액 적힌 동의, 광고 환불 대기 금액 별도 표시 |
| 수수료가 탈퇴 벌금처럼 | 공제 사유 = 청약철회 기간 경과, 탈퇴와 무관하게 같은 규칙 |
| 미사용 아이템 환불 기회 상실 | 탈퇴 화면에서 7일 안 미사용 아이템 환불 안내 |
| 탈퇴가 가입보다 어려움 | 한 줄 진입·사실 고지 1화면·사유 선택·비밀번호 1회 |
| 내 글을 못 지움 | 탈퇴 전 안내·내 글 링크, 탈퇴 뒤 블라인드 요청 창구 |
| 탈퇴 뒤 하자·반품 해결 못 함 | 분리 보관 이메일 + 완료 메일에 연락 방법 |
| 재가입이 막혀 헤맴 | 재가입 가능일을 탈퇴 화면·완료 메일·가입 실패 문구에 |
| 실수 탈퇴 | 예약 중 취소 가능. 즉시 완료 건의 유예(옵트인 7일)는 1차 범위 밖 — 기본 유예는 「지체 없이 파기」(표준지침 5일)와 충돌 위험 |

## 10. 전문가 확인 (오픈 전)
1. 배송 중·환불 처리 중이 탈퇴 지연의 「정당한 사유」인지(D1 을 채택하면 위험이 크게 줄지만 「자동 완료까지의 보유」 근거 확인).
2. 정지 중 탈퇴 허용 + 재가입 제한 연장의 근거(개보법 15조①6호 정당한 이익 vs 동의)와 처리방침 문안.
3. 논리적 분리 보관(별도 표 + 접근 통제)로 개보법 21조③ 충족 여부 — 개인정보위 「개인정보 처리 통합 안내서(2025.7)」 해당 절.
4. 세무: 거래 기록·현금영수증·광고 세금계산서 보존 기준일(D5), 사업자 회원 사업자번호 전체 보관 필요 여부.
5. 충전 환불 수수료 10%·보너스 회수 산식 약관 문안.
6. 게시물 이용허락·탈퇴 후 존속 조항, 블라인드 요청 본인 확인 방법.
7. 소셜 식별값 해시 보관이 카카오·네이버·구글 개발자 약관(연결 해제 시 파기)과 맞는지.
8. 전상법 6조② 2026-01-20 개정 내용, 다크패턴 과태료 현행 조문.

### 주요 근거 링크
개보법 [21조](https://casenote.kr/법령/개인정보_보호법/제21조)·[37조](https://casenote.kr/법령/개인정보_보호법/제37조)·[38조](https://casenote.kr/법령/개인정보_보호법/제38조)·[30조](https://casenote.kr/법령/개인정보_보호법/제30조)·[15조](https://casenote.kr/법령/개인정보_보호법/제15조) · 전상법 [6조](https://casenote.kr/법령/전자상거래_등에서의_소비자보호에_관한_법률/제6조)·[21조의2](https://casenote.kr/법령/전자상거래_등에서의_소비자보호에_관한_법률/제21조의2)·[시행령](https://www.nepla.ai/law/전자상거래-등에서의-소비자보호에-관한-법률-시행령) · 전금법 [2조](https://casenote.kr/법령/전자금융거래법/제2조)·[28조](https://casenote.kr/법령/전자금융거래법/제28조) · [약관법 6조](https://casenote.kr/법령/약관의_규제에_관한_법률/제6조) · [국세기본법 85조의3](https://casenote.kr/법령/국세기본법/제85조의3) · [통비법 시행령](https://www.nepla.ai/law/통신비밀보호법-시행령) · [공정위 다크패턴 가이드라인(2026-10)](https://eiec.kdi.re.kr/policy/materialView.do?num=287835) · [개인정보위 쿠팡 탈퇴 절차](https://www.newsseoul.co.kr/news/view/1065608415040704) · [방통위 자기게시물 접근배제 가이드라인](https://m.korea.kr/briefing/pressReleaseView.do?newsId=156126431) · [온라인게임 표준약관(생활법령)](https://easylaw.go.kr/CSP/CnpClsMain.laf?popMenu=ov&csmSeq=2858&ccfNo=2&cciNo=1&cnpClsNo=2)
