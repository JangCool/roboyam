# 회원 소통·개인 설정(공개 사이트) 계약 (**화면 목업 2026-10-23 — 사용자 화면 확인 대기**)

**의도**: community 에만 있던 「소통·개인 설정」 화면(쪽지·차단·이용자 메모·닉네임 그림·성인 확인·보기 설정)을 user-web 으로 옮긴다. UI 는 community 와 같게 두고, 서버에 API 가 하나도 없어 모두 community 계약 모양의 `// MOCK:`/`// REAL:` 짝 목업으로 먼저 확정한다. 서버 코드는 바꾸지 않았다.
**참고(읽기 전용)**: community `frontend/app/pages/{messages*,settings/{messages,blocks,memos,nickname-image,adult,view}}.vue`, `docs/api-{dm,block-scrap,nickname-image,profile(설정·보기)}.md`, 4차 계약 B절(성인 확인).

## 화면 (user-web, 모두 로그인 필요 — `auth` 미들웨어)
| 경로 | 내용 | 목업 API |
|---|---|---|
| `/messages` | 쪽지함(부모 라우트 — 데스크톱 lg 2단: 왼쪽 300px 목록 + 오른쪽 칸). 법적 고지 · 정지 중/수신 거부 안내 · 대화 줄(등급+닉네임+메모 칩 / 마지막 쪽지 "나: " / 시간 / 안 읽은 수 99+) · 이전 대화 더 보기 · 60초 주기 조회 | `GET /api/dm/threads` · `GET /api/me/dm` |
| `/messages/{threadId}` | 대화방(모바일 안쪽 화면): 날짜 칩·말풍선(링크만 `<a>`, `v-html` 없음)·이전 쪽지 더 보기·10초 주기 조회(맨 아래면 따라감)·보여 준 뒤 읽음·입력창(Enter=데스크톱만, 5줄, 450자부터 카운터)·잠금 이유·링크/연락처 경고(상대 답장 전)·전송 실패 붉은 말풍선(다시 보내기·삭제)·요청 제한 카운트다운·⋯ 시트(차단/해제 · 대화 삭제) | `GET /api/dm/threads/{id}` · `POST …/read` · `DELETE …` · `POST /api/users/{id}/dm` |
| `/messages/new?to=` | 받는 사람 고르기(닉네임 2~16자 검색 → 「처음 대화하는 상대예요/이미 대화 중이에요 · 대화 열기」) · 못 보내는 이유 배너(가입 7일 전이면 진행 막대) · 내용(500자) · 오늘 남은 한도 두 칸 · 보내면 대화방으로 + 토스트 | `GET /api/me/dm` · `GET /api/dm/recipients?q=` · `GET /api/users/{id}/dm` · `GET /api/users/{id}/profile`(기존 목업) · `POST /api/users/{id}/dm` |
| `/settings/messages` | 쪽지 받기/받지 않기(고르면 바로 저장, 실패하면 되돌림) · 법적 고지 2줄 · 쪽지함 열기 | `GET·PUT /api/me/dm/settings` |
| `/settings/blocks` | 탭 「닉네임 N」(차단한 회원·해제(실행 취소)·더 보기) / 「단어 N」(2~20자·최대 50개 추가·삭제) | `GET /api/me/blocks/users` · `PUT·DELETE /api/users/{id}/block` · `GET·POST /api/me/blocks/words` · `DELETE …/{id}` |
| `/settings/memos` | 내가 남긴 메모 목록 · 수정(셸의 메모 시트) · 삭제(확인) · 더 보기 | `GET /api/me/memos` · `PUT·DELETE /api/users/{id}/memo` |
| `/settings/nickname-image` | 자격 미달(내/필요 등급·등급 안내) · 신청(파일 → 형식·200KB·4:1 화면 검사 → 4:1 미리보기 → 동의 → 확인 → 진행률) · 검토 대기(취소) · 반려(사유·재신청까지 남은 시간) · 적용 중(목록·댓글 미리보기·바꾸기·해제) | `GET·POST·DELETE /api/me/nickname-image` · `DELETE /api/me/nickname-image/request` |
| `/settings/adult` | 상태 카드 · 출생연도(올해−14~올해−100) · 사실 확인 체크 · 저장 · 안내 3줄 | `GET·PUT /api/me/adult` |
| `/settings/view` | 화면: 야간 모드(자동·라이트·다크)·글자 크기(90/100/110/125%) · 댓글: 처음 정렬 · 시간 표시(기존 `/settings/display`)로 잇는 줄 · 라이트/다크 미리보기 | `GET·PUT /api/me/settings` |
- **들어가는 길**: 데스크톱 헤더 쪽지 아이콘(안 읽은 대화 수 배지, `AppMessageLink`) · 프로필 메뉴 「쪽지함 · 안 읽음 N」 · 햄버거 메뉴 「쪽지함」 · `/me` 바로가기 「쪽지함」(배지) · 설정 왼쪽 메뉴/목록(차례: 프로필 · 닉네임 그림 · 성인 확인 · 보기 · 시간 표시 · 차단 관리 · 이용자 메모 · 알림 설정 · 쪽지 수신 · 계정).
- **회원 프로필 `/users/{id}`**: 남의 프로필 → ⋯ 시트(이 사용자 차단/차단 해제 · 메모 남기기/수정, 비회원은 로그인 유도) · 차단한 회원이면 맨 위 「차단한 회원이에요 · 차단 해제」 · 닉네임 옆 메모 칩 · 「쪽지 보내기」(보낼 수 있나를 따로 읽어 활성 / 비활성+이유, 비회원 로그인 유도, 사업자 계정은 이유만). 내 프로필 → 「프로필 편집」 옆 「쪽지함」.
- **차단 접힘(community 방식)**: 게시판 목록 LIST(모바일 줄·데스크톱 표 한 줄 통째)·CARD·GALLERY 와 댓글을 「차단한 사용자의 글/댓글이에요 · 보기」 한 줄로 접는다(`FoldedRow`, 펼치면 「다시 숨기기」, 내 글·댓글은 안 접음). 작성자 이름 옆 메모 칩은 `PostAuthorLabel` 이 그린다(글 줄·댓글·쪽지 상대).
- **보기 설정 적용**: 야간 모드 → `<html data-color-scheme>`(`plugins/view-theme.client.ts`, 첫 그림 전 이 브라우저 사본으로), 글자 크기 → `--text-scale`(`main.css` Tailwind 글자 토큰·임의 크기·body 에 곱함), 댓글 정렬 → 글 보기의 첫 정렬(`usePostComments`).

## 목업 API 후보 (서버에 없음 — 화면 확정 뒤 구현, 모양은 각 `.types.ts`)
공통: 모두 로그인 필요(`401 UNAUTHENTICATED`), ID 는 문자열(Snowflake), 시각 UTC ISO, 목록은 커서 더보기(`size` 1~50, 틀린 커서 `400 VALIDATION fields.cursor`). 개인 설정이라 이메일 인증 전·정지 중에도 되는 것(읽기·삭제·수신 설정·차단·메모)과 막히는 것(쪽지 보내기·닉네임 그림 올리기·취소·해제)을 아래에 적었다.

### 쪽지 (`composables/dm/api/useDmApi.ts`) — community `docs/api-dm.md` 그대로
- `GET /api/me/dm` → `{acceptDm, sender{canSend, reason: EMAIL_NOT_VERIFIED|ACCOUNT_RESTRICTED|BUSINESS_RESTRICTED|TOO_NEW|DAILY_LIMIT|null, availableFrom}, quota, unreadThreads(≤100), rules{minAccountAgeDays:7, bodyMax:500, retentionDays:365}}`
- `GET /api/dm/unread-count` → `{count}`(≤100, 배지 60초 주기) · `GET·PUT /api/me/dm/settings` `{acceptDm}` · `400 fields.acceptDm`
- `GET /api/dm/threads?size(20)&cursor` → `{items[{id, partner: DmPartner, last{id, mine, preview(40자), createdAt}, unreadCount}], hasNext, nextCursor}`(마지막 쪽지 최근 순, 차단한 회원과의 대화도 남는다)
- `GET /api/dm/threads/{id}?size(30)&cursor` → `{thread{id, partner, canSend, reason, availableFrom, contactAllowed}, items(최근이 앞), hasNext, nextCursor, quota}` — 읽음 처리는 하지 않는다 · `POST …/read` 204(이 대화의 쪽지 알림도 읽음) · `DELETE …` 204(내 쪽지함에서만) · 없는·남의 대화 `404 DM_THREAD_NOT_FOUND`
- `GET /api/dm/recipients?q(2~16)` → `{items[{user, threadId|null}]}` 최대 10명(앞부분 일치 먼저, 나·탈퇴·차단 관계 제외) · `400 fields.q`
- `GET /api/users/{id}/dm` → `{canSend, reason, availableFrom, threadId, newPartner, contactAllowed, quota}`(읽기만) · `404 PROFILE_NOT_FOUND`
- `POST /api/users/{id}/dm` `{body}` → `201 {threadId, created, message, quota}`. 오류(처음 걸린 하나): `400 VALIDATION fields.body` → `404 PROFILE_NOT_FOUND` → `400 DM_SELF` → `403 EMAIL_NOT_VERIFIED` → `403 ACCOUNT_RESTRICTED` → `403 BUSINESS_ACCOUNT_RESTRICTED` → `403 DM_SENDER_TOO_NEW` → `403 DM_UNAVAILABLE`(차단 관계 — 어느 쪽이든) → `403 DM_RECIPIENT_REFUSES` → `400 DM_CONTACT_NOT_ALLOWED`(상대가 이 대화에서 보낸 적 없는데 링크·연락처 — 감지 규칙 `utils/util-dm-contact.ts` 를 서버도 같게) → `429 RATE_LIMITED`(3초) → `429 DM_DAILY_LIMIT`(20통) → `429 DM_NEW_PARTNER_LIMIT`(새 상대 5명). 저장·한도 차감이 한 트랜잭션(실패하면 한도 그대로).
- `DmPartner = {id|null, nickname, grade, gradeLabel, role, withdrawn, memo}` — 탈퇴한 회원은 `id: null`·`nickname: "탈퇴한 회원"`. **하루는 한국 시간 0시**. 1년(365일) 지난 쪽지 자동 삭제(신고된 대화 제외) — 서버 배치 몫.
### 차단·메모 (`composables/block/api/useBlockApi.ts`·`useMemoApi.ts`) — community `docs/api-block-scrap.md` 그대로
- `PUT /api/users/{id}/block` → `{blocked, blockedAt}`(멱등) · `400 BLOCK_SELF` · `403 BLOCK_TARGET_PROTECTED`(운영진) · `404 PROFILE_NOT_FOUND` · `409 BLOCK_LIMIT_REACHED`(500) · `DELETE` 204(멱등)
- `GET /api/me/blocks/users` → `{total, items[{id, nickname, grade, gradeLabel, role, blockedAt}], hasNext, nextCursor}`
- `GET /api/me/blocks/words` → `{limit:50, minLength:2, maxLength:20, items[{id, word, createdAt}]}` · `POST {word}` → 201 · `400 fields.word` · `409 BLOCKED_WORD_DUPLICATE`(대소문자 무시) · `409 BLOCKED_WORD_LIMIT` · `DELETE …/{id}` 204(남의 항목도)
- `PUT /api/users/{id}/memo {memo}` → `{memo|null}`(빈 메모 = 삭제) · `400 fields.memo`(20자·한 줄) · `400 MEMO_SELF` · `404` · `409 MEMO_LIMIT_REACHED`(500, 처음 남길 때만) · `DELETE` 204 · `GET /api/me/memos` → `{total, items[{id(상대 회원), nickname, grade, gradeLabel, role, memo, updatedAt}], …}`(처음 남긴 순)
- **이미 있는 응답에 더할 칸(서버 후보, 화면 타입에 선택 칸으로 넣어 둠)**: 글 줄 `PostRow.blocked`·댓글 `CommentView.blocked` = `USER|WORD|null`(차단 단어는 글 **제목**·댓글 **본문** 부분 일치, 내 것은 늘 null), 작성자 `PostAuthor.memo`, 공개 프로필 `PublicProfile.blockedByMe`·`memo`. 접는 것은 화면 몫(검색 결과는 서버가 뺀다 — 통합 검색 서버를 만들 때).
### 닉네임 그림 (`composables/profile/api/useNicknameImageApi.ts`) — community `docs/api-nickname-image.md` 2·4절(변경권 칸 없음)
- `GET /api/me/nickname-image` → `{eligible, ineligibleReason: GRADE_TOO_LOW|SUSPENDED|null, grade{code,label}, requiredGrade, current{id,url,still}|null, request{id, status: PENDING|REJECTED, url, createdAt, rejectReason, canReapplyAt}|null, reapplyAt, limits{maxBytes:204800, width:240, height:60, ratio:"4:1", maxGifFrames:30, formats, rejectedReapplyHours:72}}`
- `POST`(multipart `file`) → `201` 같은 모양 · `403 NICKNAME_IMAGE_GRADE_TOO_LOW` · `409 NICKNAME_IMAGE_REAPPLY_TOO_SOON`(`retryAfterSeconds`·`canReapplyAt`) · `400 VALIDATION fields.file` · `429 RATE_LIMITED`(10초·하루 10번) · `403 EMAIL_NOT_VERIFIED`·`ACCOUNT_RESTRICTED`. 대기 신청은 회원당 하나(새로 올리면 대체). 검토 대기가 생기면 운영자 알림 **`NICKNAME_IMAGE_PENDING`**(이미 예약된 이벤트).
- `DELETE /api/me/nickname-image/request` 204 · `404 NICKNAME_IMAGE_REQUEST_NOT_FOUND` · `DELETE /api/me/nickname-image` 204 · `404 NICKNAME_IMAGE_NOT_FOUND`
### 성인 확인 (`composables/adult/api/useAdultApi.ts`) — community 4차 계약 B절
- `GET /api/me/adult` → `{birthYear|null, adult, method: SELF|IDENTITY|null, confirmedAt, minYear, maxYear}`(저장 전에도 200) · `PUT {birthYear, confirm:true}` → 같은 모양 · `400 fields.birthYear`(범위·만 14세 미만)·`fields.confirm`. 성인 = 한국 시간 올해 − 출생연도 ≥ 19. 출생연도는 개인정보(로그 금지).
### 보기 설정 (`composables/view/api/useViewSettingsApi.ts`) — community `GET·PUT /api/me/settings` 경로·규칙, 칸 셋
- `GET` → `{theme: AUTO|LIGHT|DARK, textScale: 90|100|110|125, commentOrder: OLDEST|LATEST}`(저장 전 기본값 AUTO·100·OLDEST) · `PUT` 세 칸 통째 → 저장된 값 · `400 fields.<칸>`. 서버는 값만 저장, 적용은 화면.

## 목업 가정
1. 모든 목업은 메모리 상태라 새로고침하면 처음으로 돌아간다(보기 설정만 처음 읽을 때 이 브라우저 사본을 「서버 값」으로 삼는다). 보는 사람은 한 명으로 본다.
2. **가짜 회원 명부**(`composables/common/mock/member-mock.ts`) 8명 — 쪽지 상대·받는 사람 검색·차단·메모 대상. 명부 밖 ID 는 실제 게시판 글의 작성자 이름을 빌리고, 없으면 「회원 {끝 4자리}」. 공개 프로필 목업도 명부 이름·역할·`blockedByMe`·`memo` 를 쓴다. 「조용한관찰자」는 쪽지를 받지 않고, 「운영지기」는 운영진(차단 불가).
3. 쪽지 처음 상태: 대화 6개(안 읽음 2통 · 답장 전 첫 쪽지(링크·연락처 막힘) · 탈퇴한 회원 · 수신 거부 회원 · 차단한 회원 · 열흘 전 안 읽음). 보낼 자격은 로그인 정보로 정한다(인증 전 → 정지 → 사업자 → 가입 7일 전(`?mock=dm-too-new` — 로그인 정보에 가입일이 없다) → 한도). 상대가 나를 차단한 경우는 만들지 않았다.
4. 차단 처음 상태: 「로봇팔장인」 차단(사흘 전), 단어 「스포일러」, 메모 2개. **서버 응답에 `blocked`·`memo` 가 없어서** 셸이 로그인할 때 목업의 차단·단어·메모 한 벌을 받아 이 기기의 표시(`useBlockOverrides`)에 깐다(`useBlockOverridesLoader`, `useBlockApi().mockSnapshot`) — 서버가 붙으면 이 로더와 「단어 차단을 화면이 대신 판정」하는 줄을 지운다.
5. 닉네임 그림: 자격은 로그인 정보의 등급(단골·베테랑)·정지 여부. 올린 그림은 브라우저 안 주소(`blob:`)로만 보인다. 승인·반려는 관리자 화면 몫이라 스위치로만 흉내 낸다.
6. 개발 스위치(`?mock=`): 쪽지 `dm-empty|dm-inbox-fail|dm-room-fail|dm-too-new|dm-daily-limit|dm-partner-limit|dm-send-fail|dm-send-offline|dm-recipients-fail|dm-settings-fail|dm-settings-save-fail|dm-reply` · 차단·메모 `block-fail|blocks-users-fail|blocks-words-fail|blocks-empty|memo-save-fail|memos-fail|memos-empty` · 닉네임 그림 `nickimg-eligible|nickimg-pending|nickimg-rejected|nickimg-current|nickimg-fail|nickimg-apply-fail|nickimg-approve` · 성인 확인 `adult-fail|adult-save-fail|adult-confirmed|adult-minor` · 보기 `view-fail|view-save-fail`.

## community 대비 뺀 것
- **보기 설정**: 주 색상·배경 색상·본문 너비·목록 밀도·메뉴 위치·「화면 꾸미기 되돌리기」 — 로보얌은 사이트 테마(운영자 설정)가 정하고 개인 테마·보기 설정을 두지 않는다(`.claude/guides/frontend/themes.md`). 이미지 숨기기·답글 접기·한 번에 보는 댓글 수 — 적용할 곳(본문 이미지 가리기·답글 묶음 접기·댓글 쪽 나누기)이 아직 없다. 시간 표시는 기존 `/settings/display` 를 그대로 두고 「보기」에서 잇는다(합치지 않음). 비회원 보기 설정(설정 묶음 전체가 로그인 필요).
- **쪽지**: 쪽지 신고(로보얌 신고 대상에 쪽지가 없다) · 푸시. **닉네임 그림**: 아이템샵 변경권(로보얌에 아이템샵 화면이 없다)·닉네임 그림 신고·작성자 표시에 그림 그리기(서버 작성자 칸에 `nicknameImage` 가 없다 — 미리보기만). **차단**: 글 상세의 「차단한 사용자의 글」 안내·글/댓글 ⋯ 메뉴의 차단·메모 항목(로보얌 글·댓글에 회원 ⋯ 메뉴가 없다 — 프로필·쪽지 대화방에서만), 최신·인기·검색·프로필 작성글 목록의 접힘(게시판 목록·댓글만), 설정 목록의 개수 요약(`GET /api/me/counts`).
- **성인 확인**: community 도 출생연도 **자기 신고**라 그대로 옮겼다(본인인증 기반은 커머스 세션 0-4 범위 — 그때 `method: IDENTITY`). 게시판 머리의 19금 안내·글쓰기 19금 체크와의 연결은 하지 않았다.

## 미확정
1. 쪽지 「가입 7일」·하루 20통·새 상대 5명·3초 간격·500자·1년 보관의 주인(서버 상수? 사이트 설정?) — 관리자 화면 필요 여부.
2. 회원 알림에 `DM`(쪽지 받음) 이벤트를 더할지 — 회원 알림 카탈로그(`member-notification-contract.md`)와 겹치는 개념이라 그 계약과 맞춘다. 닉네임 그림 검토 결과 알림(`NICKNAME_IMAGE_REVIEWED`)도 같다.
3. 차단·메모를 글·댓글 ⋯ 메뉴에도 둘지(로보얌 글 상세·댓글에 회원 메뉴가 생길 때), 글 상세의 차단 안내.
4. 닉네임 그림 관리자 검토 화면(승인·반려·해제)과 그 권한·메뉴, 아이템샵 변경권을 둘지.
5. 성인 확인 결과를 어디에 쓰나(19금 글 보기 판정 — 게시판 `adult` 글 처리, 서버 몫), 본인인증 도입 시 자기 신고 값을 어떻게 다룰지.
6. 보기 설정의 글자 크기를 PrimeVue 위젯(단추·입력칸) 글자에도 적용할지(지금은 Tailwind 글자만 — community 의 `text-scale.css`·프리셋 보정은 옮기지 않았다).
