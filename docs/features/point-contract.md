# 포인트 계약 (**서버 구현 2026-10-23** — 화면 연결은 다음 단계)

**의도**: 회원이 글·댓글·추천 받음으로 포인트를 쌓고, 운영자가 원장을 보고 수동 지급·차감하며 지급 정책(14칸)을 정한다. 잔액은 0 아래로 내려가지 않는다.
**참고(읽기 전용)**: community `docs/api-point.md`(지급·회수 규칙의 정본). 프론트 기준 admin-web `composables/point/api/usePointApi.ts`(+types·mock), `pages/points/**`, user-web `composables/point/api/usePointApi.ts`.

## 저장 (shared `V51__points.sql`)
- `point_settings`(한 행, 14칸 + `updated_at`·`updated_by`, 범위 CHECK — 기본값은 목업 기본값), `point_balances`(회원당 한 줄, `balance >= 0`), `point_ledger`(추가만 — UPDATE 를 막는 트리거, 운영자 삭제 때 `actor_operator_id → NULL` 만 허용. 유일 `(reason, ref_type, ref_id)` = 같은 대상에 두 번 지급·회수 없음).
- 모든 쓰기는 shared `PointService` 한 길: 잔액 행을 (없으면 0 으로 만들고) `FOR UPDATE` 로 잠근 뒤 원장 한 줄 + 잔액. 하루 상한 셈도 그 잠금 아래라 동시 요청이 넘지 못한다.

## 적립·회수 (실제로 일어나는 사건에만 — api 서버)
| 사건 | 지점 | 원장 |
|---|---|---|
| 회원 글쓰기 | `PostWriteService#create` 저장 트랜잭션 끝 | `POST` +`postPoints`, 하루 `postDailyCap` |
| 회원 댓글쓰기(답글 포함) | `CommentService#create` | `COMMENT` +`commentPoints`, 하루 `commentDailyCap` |
| 추천이 인정된 추천 문턱을 채움 | `PostWriteService#like`·`CommentService#like`(추천 수 갱신 뒤 — 같은 대상의 추천이 그 행에서 줄을 선다) | `LIKE_BONUS` +`likeBonusPoints` → 작성자, 하루 `likeBonusDailyLimit`(글·댓글 합산) |
| 본인이 글 지움(바로·예약 모두 요청 시점) | `PostWriteService#delete` | `POST_REVOKE`·`LIKE_BONUS_REVOKE` — 받은 양만, 0P 까지 |
| 본인이 댓글 지움 | `CommentService#delete` | `COMMENT_REVOKE`·`LIKE_BONUS_REVOKE` |
| 운영 조정 | admin `PointAdjustService` | `ADMIN_ADJUST` ±(부호로 EARN/REVOKE) |
- 규칙(community 정본): 하루 = 한국 시간 0시부터 **지급한 횟수**(지운 글도 센다), 정상(ACTIVE) 회원만 받는다, 비회원 없음, 회수는 실제 지급량만·0P 바닥·반영 0 이면 줄 없음, 본인 삭제는 알림 없음, 숨김(신고)은 회수하지 않는다.
- **인정된 추천** = 작성자가 아닌 정상 회원 중 등급이 가장 낮은 등급(서열 최솟값, 기본 새싹)보다 높은 회원의 추천. 방금 누른 추천이 인정되고 인정된 추천이 정확히 문턱 개가 될 때 한 번(소급 없음).
- **적용 지점 없음(저장만)**: `attendancePoints`(출석) · `popularRewardPoints`·`popularRewardTopN`(인기글) · `dealEndRequestPoints`·`dealEndRequestDailyLimit`(핫딜) · `signupPoints`(가입 — 공개 가입 기능 없음. **지급 조건은 `docs/domain/commerce.md` 6-2 로 확정: 본인인증 완료 시 평생 1회**) · `nicknameTicketPrice`(교환 — 닉네임 변경권은 아이템샵이 생길 때 품목으로 만든다, 할 일은 `member-mypage-contract.md` 「후속 작업: 닉네임 변경권」).
- **적립 실패가 글쓰기를 깨지 않는다**(community 와 다름 — 가정 1): 적립·회수·추천 보너스는 JDBC 세이브포인트(`IsolatedSideWork`) 안이고 실패하면 0(ERROR 로그). 정책 사본도 JDBC 로 읽는다(JPA 저장소 호출의 실패가 업무 트랜잭션을 롤백 전용으로 만들지 않게). 검증: 원장 INSERT 를 막아도 글·댓글 201.

## 정책 반영
- `PointPolicy`(shared) 15초 메모리 사본 — 관리자 저장 시 그 서버는 커밋 직후 비우고 api 는 최대 15초 뒤 따라온다(실측 16초 = 15초 + 확인 간격). 소급 없음.

## API
**관리자(admin, `Cache-Control: no-store`)** — 목업 머리말 그대로. 공통 403 `PERMISSION_DENIED`.
- `GET /api/admin/points/ledger?from&to&kind&reasons&q&page&size` (`point.read`) → `{ items: PointLedgerItem[], total, summary: { earned, spent, adjustCount } }`. 최신이 위(`createdAt`·id 내림차순), `page` 0부터, `size` 1~100(기본 20). `reasons` 반복 또는 쉼표. `q` = 닉네임 부분(대소문자 무시) · 회원번호 같음 · 이메일(`member.pii` 있으면 부분, 없으면 전체 주소가 같을 때만). 탈퇴 회원의 줄은 나오지 않는다. 틀린 조건은 칸을 모아 400 `fields.from|to|kind|reasons|page|size`(시작 > 끝은 `fields.from`).
- `GET /api/admin/points/member-candidates?q=` (`point.adjust`) → `PointMemberCandidate[]` — 탈퇴하지 않은 회원(정상·정지) 최대 20명, 최근 가입순, `q` 규칙은 위와 같다, 이메일은 `member.pii` 없으면 `m***@verify.test` 처럼 가린다.
- `POST /api/admin/members/{id}/points` `{ delta, reason }` (`point.adjust`) → `{ applied, balance }`. delta 0 아닌 정수 −10000~10000(소수·빈 값도 `fields.delta`), reason 정규화(앞뒤 공백 제거·연속 공백 한 칸) 뒤 1~100자. 검사 → 회원(탈퇴·없음·형식 `404 MEMBER_NOT_FOUND`) 순. 정지 회원도 조정 가능. 감사 `POINT_ADJUST`(대상 MEMBER, `requested`·`applied`·`balance`·`reason`, 반영 0 도 남김). 차감이 반영되면 회원 알림 `POINTS_REVOKED`.
- `GET /api/admin/settings/points` (`point.read`) · `PUT` (`point.settings`) 14칸 모두 → `PointSettingsResponse`(14칸 + `updatedAt`·`updatedBy`=운영자 이름). 칸마다 정수·범위 → 400 `fields.<칸>`(문구 「0 ~ 10,000 사이 정수로 입력해 주세요.」), 하나라도 틀리면 저장 없음. 바뀐 칸이 없으면 쓰지 않는다. 감사 `POINT_SETTINGS_UPDATE`(대상 `POINT_SETTINGS`, 바뀐 칸만 before/after).
- 회원 목록·상세의 `point` = 잔액(탈퇴 0), 목록의 `pointMin`·`pointMax`·`sort=point` 가 실제로 동작. 상세 `gradePoint.pointEntries` = 원장 최근 10줄(`entryId·kind·delta·reason(요청 언어)·createdAt`), `nicknameTicketCount` 는 교환 기능이 없어 0.

**공개(api)** — `GET /api/me/points`(로그인 필요 401, no-store):
`{ balance, ticketCount: 0, attendanceAvailable: false, today: { date: "YYYY-MM-DD"(KST), attended: false, post: {count, cap}, comment: {count, cap}, likeBonus: {count, cap} }, rules: { attendance, post, comment, popularReward: {topN, points}, nicknameTicketPrice, likeBonus: {threshold, points, dailyCap} } }`

## 권한
- 서버 `Permission` 의 `point.read`·`point.settings`·`point.adjust` 를 `featureReady=true` 로 바꿨다 — **프론트 `util-permission-catalog.ts` 도 같이 맞춰야 한다**(다음 단계).

## 가정
1. 적립 실패 격리(위). 원장·잔액이 어긋나는 일은 없다(같은 세이브포인트 안에서 함께 되돌아간다).
2. 예약 삭제(댓글 많은 글 보호 DELAY)는 **요청 시점에** 회수한다(예약을 취소하는 기능이 없다).
3. 「인정된 추천」의 일반 등급 이상 = 서열 최솟값보다 높은 등급(등급 목록이 바뀌어도 「가장 낮은 등급 제외」).
4. 지운 글에 달린 댓글은 댓글 포인트를 회수하지 않는다(community 도 글 삭제는 글 포인트만).
5. 탈퇴 시 포인트 소멸 — 탈퇴 기능이 없어 지우는 코드는 없고, 조회(원장·후보·목록·상세)에서 탈퇴 회원을 0/제외로 보인다. **커머스 정책 확정(2026-10-23) 이후 탈퇴 규칙은 `docs/domain/commerce.md` 17절이 정본이다**: 적립(무상) 포인트는 소멸, 충전(유상) 포인트는 「환불받고 탈퇴」 또는 「포기하고 탈퇴」 선택, 원장은 지우지 않고 마지막 거래일부터 5년 보관(회원 행은 익명화), 정지·기능 제한 중 탈퇴 불가. 지금 `point_balances`·`point_ledger` 의 회원 삭제 연쇄(`ON DELETE CASCADE`)는 커머스 1단계 마이그레이션에서 바꾼다.

## 미확정·후속
1. 글·댓글 작성 응답의 `pointsEarned`(community 의 "+20P" 토스트)는 응답 모양을 바꾸지 않으려고 넣지 않았다 — 필요하면 `PostWriteResult`·댓글 쓰기 응답에 더한다.
2. 출석·교환·인기글 보상·가입 포인트·핫딜 기능이 생기면 `PointService` 에 사유별 지급 메서드를 더한다.
3. 운영자·스텝의 글·댓글 삭제가 생기면 회수 + `POINTS_REVOKED`(`POST_DELETED`·`COMMENT_DELETED`) 를 그 지점에 연결한다.
4. 회원 쪽 포인트 내역 API(`GET /api/me/points/ledger`)는 화면이 없어 만들지 않았다.

## 프론트 연결 결과 (2026-10-23)
- admin-web `usePointApi` 5개 메서드 실제 호출(`reasons` 반복 이름), `point/api/mock/`·`common/mock/mock-util.ts` 삭제 — 목업의 `point-settings-reject`(가입 포인트 상호 규칙) 시나리오는 서버에 없는 규칙이라 함께 없어졌다. 원장 쪽 크기 20·50·100(서버 1~100), 조건 오류는 `fields.from|to|kind|reasons` 를 그 줄 아래·`page|size` 를 조건 아래 한 줄에 서버 문구 그대로. 정책 칸 오류도 서버 문구 그대로(화면 검사는 서버와 같은 범위라 화면에서는 미리 막힌다).
- 포인트 정책: 적용 지점이 없는 구획(출석·인기글 보상·핫딜 종료 요청·가입 포인트·상점의 교환권 가격)에 「아직 적용되지 않아요」 딱지 + 한 줄 안내. 운영 원칙의 「게시판별 켜고 끄기」 문구는 서버에 그런 설정이 없어 「모든 게시판에 같은 정책」으로 고쳤다.
- 권한 카탈로그 `point.read`·`point.settings`·`point.adjust` → `featureReady: true`(서버 `Permission` 과 같다).
- 회원 상세: 기본 정보의 포인트·등급·포인트 구획의 잔액·최근 내역 10줄을 실제 값으로(「준비 중」 제거). 닉네임 변경권은 늘 0 이라 「준비 중」 유지. 회원 목록의 `pointMin`·`pointMax`·`sort=point` 는 서버와 맞는 것을 확인.
- user-web `usePointApi`(`GET /api/me/points`) 실제 호출, 타입에 `attendanceAvailable`·`today`(post·comment·likeBonus)·`rules` 전체 반영. `attendanceAvailable=false` 면 「오늘 출석하기」 안내를 그리지 않고 프로필 메뉴 줄 이름도 「포인트」(출석체크 없음).
- (2026-10-24) 미확정 4 의 회원 포인트 내역 `GET /api/me/points/ledger` 를 만들고 `/me/points` 를 연결했다 — `member-mypage-contract.md` 「서버 구현·연결 결과」.
