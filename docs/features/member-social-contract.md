# 회원 소통·개인 설정(공개 사이트) 계약 (화면 목업 2026-10-23 · **쪽지·차단·메모·닉네임 이미지·보기 설정 서버 구현·화면 연결 2026-10-24** — 성인 확인은 아직 목업)

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
| `/settings/nickname-image` | 자격 미달(신청 조건 체크리스트 — 「지금 N / 필요 M」·등급 안내) · 신청(파일 → 형식·200KB·4:1 화면 검사 → 4:1 미리보기 → 동의 → 확인 → 진행률) · 검토 대기(취소) · 반려(사유·재신청까지 남은 시간) · 적용 중(목록·댓글 미리보기·바꾸기·해제) | `GET·POST·DELETE /api/me/nickname-image` · `DELETE /api/me/nickname-image/request` |
| `/settings/adult` | 상태 카드 · 출생연도(올해−14~올해−100) · 사실 확인 체크 · 저장 · 안내 3줄 | `GET·PUT /api/me/adult` |
| `/settings/view` | 화면: 야간 모드(자동·라이트·다크)·글자 크기(90/100/110/125%) · 댓글: 처음 정렬 · 시간 표시(기존 `/settings/display`)로 잇는 줄 · 라이트/다크 미리보기 | `GET·PUT /api/me/settings` |
- **들어가는 길**: 데스크톱 헤더 쪽지 아이콘(안 읽은 대화 수 배지, `AppMessageLink`) · 프로필 메뉴 「쪽지함 · 안 읽음 N」 · 햄버거 메뉴 「쪽지함」 · `/me` 바로가기 「쪽지함」(배지) · 설정 왼쪽 메뉴/목록(차례: 프로필 · 닉네임 그림 · 성인 확인 · 보기 · 시간 표시 · 차단 관리 · 이용자 메모 · 알림 설정 · 쪽지 수신 · 계정).
- **회원 프로필 `/users/{id}`**: 남의 프로필 → ⋯ 시트(이 사용자 차단/차단 해제 · 메모 남기기/수정, 비회원은 로그인 유도) · 차단한 회원이면 맨 위 「차단한 회원이에요 · 차단 해제」 · 닉네임 옆 메모 칩 · 「쪽지 보내기」(보낼 수 있나를 따로 읽어 활성 / 비활성+이유, 비회원 로그인 유도, 사업자 계정은 이유만). 내 프로필 → 「프로필 편집」 옆 「쪽지함」.
- **차단 접힘(community 방식)**: 게시판 목록 LIST(모바일 줄·데스크톱 표 한 줄 통째)·CARD·GALLERY 와 댓글을 「차단한 사용자의 글/댓글이에요 · 보기」 한 줄로 접는다(`FoldedRow`, 펼치면 「다시 숨기기」, 내 글·댓글은 안 접음). 작성자 이름 옆 메모 칩은 `PostAuthorLabel` 이 그린다(글 줄·댓글·쪽지 상대).
- **보기 설정 적용**: 야간 모드 → `<html data-color-scheme>`(`plugins/view-theme.client.ts`, 첫 그림 전 이 브라우저 사본으로), 글자 크기 → `--text-scale`(`main.css` Tailwind 글자 토큰·임의 크기·body, `text-scale.css` PrimeVue 글자 크기 토큰 30개, `nuxt.config.ts` 프리셋 `css` 의 Button·InputText·Textarea·ToggleButton·Select 라벨 기본 크기에 곱함 — community 방식), 댓글 정렬 → 글 보기의 첫 정렬(`usePostComments`).

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
- `GET /api/me/nickname-image` → `{eligible, ineligibleReason: REQUIREMENTS_NOT_MET|SUSPENDED|null, grade{code,label}, requirements{mode, items}(아래 「신청 조건」), current{id,url,still}|null, request{id, status: PENDING|REJECTED, url, createdAt, rejectReason, canReapplyAt}|null, reapplyAt, limits{maxBytes:204800, width:240, height:60, ratio:"4:1", maxGifFrames:30, formats, rejectedReapplyHours:72}}`
- `POST`(multipart `file`) → `201` 같은 모양 · `403 NICKNAME_IMAGE_REQUIREMENTS_NOT_MET` · `409 NICKNAME_IMAGE_REAPPLY_TOO_SOON`(`retryAfterSeconds`·`canReapplyAt`) · `400 VALIDATION fields.file` · `429 RATE_LIMITED`(10초·하루 10번) · `403 EMAIL_NOT_VERIFIED`(설정이 요구할 때만)·`ACCOUNT_RESTRICTED`. 대기 신청은 회원당 하나(새로 올리면 대체). 검토 대기가 생기면 운영자 알림 **`NICKNAME_IMAGE_PENDING`**(이미 예약된 이벤트).
- `DELETE /api/me/nickname-image/request` 204 · `404 NICKNAME_IMAGE_REQUEST_NOT_FOUND` · `DELETE /api/me/nickname-image` 204 · `404 NICKNAME_IMAGE_NOT_FOUND`
### 성인 확인 (`composables/adult/api/useAdultApi.ts`) — community 4차 계약 B절
- `GET /api/me/adult` → `{birthYear|null, adult, method: SELF|IDENTITY|null, confirmedAt, minYear, maxYear}`(저장 전에도 200) · `PUT {birthYear, confirm:true}` → 같은 모양 · `400 fields.birthYear`(범위·만 14세 미만)·`fields.confirm`. 성인 = 한국 시간 올해 − 출생연도 ≥ 19. 출생연도는 개인정보(로그 금지).
### 보기 설정 (`composables/view/api/useViewSettingsApi.ts`) — community `GET·PUT /api/me/settings` 경로·규칙, 칸 셋
- `GET` → `{theme: AUTO|LIGHT|DARK, textScale: 90|100|110|125, commentOrder: OLDEST|LATEST}`(저장 전 기본값 AUTO·100·OLDEST) · `PUT` 세 칸 통째 → 저장된 값 · `400 fields.<칸>`. 서버는 값만 저장, 적용은 화면.
- **서버 구현 결과 (2026-10-24)**: api `com.community.viewsetting`(`MyViewSettingsController` `GET·PUT /api/me/settings`, `MemberViewSettingsService`, 허용 값 `ViewSettingsPolicy`) — 응답은 `ViewSettings` 그대로(`textScale` JSON 정수), 둘 다 `Cache-Control: no-store`. 로그인 필요(`/api/**` 규칙, 401)·`PUT` CSRF·본인만, 이메일 인증·정지 검사 없음. `PUT` 은 세 칸 모두 필수 — 빠진 칸·타입 틀림(`"100"`·`100.5`)·없는 값은 `400 VALIDATION fields.theme|textScale|commentOrder` 를 한꺼번에(저장 없음), 본문 없음도 세 칸 오류. 표 `member_view_settings`(shared `V73__member_view_settings.sql`, 회원당 한 행, 값 CHECK, `users` FK `ON DELETE CASCADE`), 행이 없으면 기본값 AUTO·100·OLDEST(GET 은 행을 만들지 않는다). 문구 `viewSettings.*.invalid`(ko·en).
- **화면 연결 (2026-10-24)**: `useViewSettingsApi` 가 실제 `GET·PUT` 을 부른다(목업 `view-mock.ts`·`?mock=view-*` 스위치 삭제, CSRF 는 `useApiClient` 재시도). 비로그인은 서버를 부르지 않고 이 브라우저 사본(`roboyam:view-settings`)만 쓴다. 로그인하면 첫 그림은 사본으로 그리고(`view-theme.client.ts`) 서버 값이 오면 이긴다(`view-settings.client.ts` — 로그인한 사람이 바뀔 때마다 다시 받음). 서버에 저장한 적 없는 회원은 기본값이 와서 사본도 기본값으로 덮인다(계정 값이 정본 — 비회원 보기 설정은 두지 않으므로 의도대로).

## 목업 가정
1. 모든 목업은 메모리 상태라 새로고침하면 처음으로 돌아간다. 보는 사람은 한 명으로 본다. (보기 설정은 서버에 연결됨 — 위 「화면 연결」.)
2. **가짜 회원 명부**(`composables/common/mock/member-mock.ts`) 8명 — 쪽지 상대·받는 사람 검색·차단·메모 대상. 명부 밖 ID 는 실제 게시판 글의 작성자 이름을 빌리고, 없으면 「회원 {끝 4자리}」. 공개 프로필 목업도 명부 이름·역할·`blockedByMe`·`memo` 를 쓴다. 「조용한관찰자」는 쪽지를 받지 않고, 「운영지기」는 운영진(차단 불가).
3. 쪽지 처음 상태: 대화 6개(안 읽음 2통 · 답장 전 첫 쪽지(링크·연락처 막힘) · 탈퇴한 회원 · 수신 거부 회원 · 차단한 회원 · 열흘 전 안 읽음). 보낼 자격은 로그인 정보로 정한다(인증 전 → 정지 → 사업자 → 가입 7일 전(`?mock=dm-too-new` — 로그인 정보에 가입일이 없다) → 한도). 상대가 나를 차단한 경우는 만들지 않았다.
4. 차단 처음 상태: 「로봇팔장인」 차단(사흘 전), 단어 「스포일러」, 메모 2개. **서버 응답에 `blocked`·`memo` 가 없어서** 셸이 로그인할 때 목업의 차단·단어·메모 한 벌을 받아 이 기기의 표시(`useBlockOverrides`)에 깐다(`useBlockOverridesLoader`, `useBlockApi().mockSnapshot`) — 서버가 붙으면 이 로더와 「단어 차단을 화면이 대신 판정」하는 줄을 지운다.
5. 닉네임 그림: 자격은 로그인 정보의 등급(단골·베테랑)·정지 여부. 올린 그림은 브라우저 안 주소(`blob:`)로만 보인다. 승인·반려는 관리자 화면 몫이라 스위치로만 흉내 낸다.
6. 개발 스위치(`?mock=`): 쪽지 `dm-empty|dm-inbox-fail|dm-room-fail|dm-too-new|dm-daily-limit|dm-partner-limit|dm-send-fail|dm-send-offline|dm-recipients-fail|dm-settings-fail|dm-settings-save-fail|dm-reply` · 차단·메모 `block-fail|blocks-users-fail|blocks-words-fail|blocks-empty|memo-save-fail|memos-fail|memos-empty` · 닉네임 그림 `nickimg-eligible|nickimg-pending|nickimg-rejected|nickimg-current|nickimg-fail|nickimg-apply-fail|nickimg-approve` · 성인 확인 `adult-fail|adult-save-fail|adult-confirmed|adult-minor`.

## community 대비 뺀 것
- **보기 설정**: 주 색상·배경 색상·본문 너비·목록 밀도·메뉴 위치·「화면 꾸미기 되돌리기」 — 로보얌은 사이트 테마(운영자 설정)가 정하고 개인 테마·보기 설정을 두지 않는다(`.claude/guides/frontend/themes.md`). 이미지 숨기기·답글 접기·한 번에 보는 댓글 수 — 적용할 곳(본문 이미지 가리기·답글 묶음 접기·댓글 쪽 나누기)이 아직 없다. 시간 표시는 기존 `/settings/display` 를 그대로 두고 「보기」에서 잇는다(합치지 않음). 비회원 보기 설정(설정 묶음 전체가 로그인 필요).
- **쪽지**: 쪽지 신고(로보얌 신고 대상에 쪽지가 없다) · 푸시. **닉네임 그림**: 아이템샵 변경권(로보얌에 아이템샵 화면이 없다)·닉네임 그림 신고·작성자 표시에 그림 그리기(서버 작성자 칸에 `nicknameImage` 가 없다 — 미리보기만). **차단**: 글/댓글 ⋯ 메뉴의 차단·메모 항목(로보얌 글·댓글에 회원 ⋯ 메뉴가 없다 — 프로필·쪽지 대화방에서만), 설정 목록의 개수 요약(`GET /api/me/counts`).
- **성인 확인**: community 도 출생연도 **자기 신고**라 그대로 옮겼다(본인인증 기반은 커머스 세션 0-4 범위 — 그때 `method: IDENTITY`). 게시판 머리의 19금 안내·글쓰기 19금 체크와의 연결은 하지 않았다.

## 미확정
1. 쪽지 「가입 7일」·하루 20통·새 상대 5명·3초 간격·500자·1년 보관의 주인(서버 상수? 사이트 설정?) — 관리자 화면 필요 여부.
2. 회원 알림에 `DM`(쪽지 받음) 이벤트를 더할지 — 회원 알림 카탈로그(`member-notification-contract.md`)와 겹치는 개념이라 그 계약과 맞춘다. 닉네임 그림 검토 결과 알림(`NICKNAME_IMAGE_REVIEWED`)도 같다.
3. 차단·메모를 글·댓글 ⋯ 메뉴에도 둘지(로보얌 글 상세·댓글에 회원 메뉴가 생길 때). 글 상세의 차단 안내는 붙였다(2026-10-24, 아래 「글 상세 접힘」).
4. 닉네임 그림 관리자 검토 화면(승인·반려·해제)과 그 권한·메뉴, 아이템샵 변경권을 둘지.
5. 성인 확인 결과를 어디에 쓰나(19금 글 보기 판정 — 게시판 `adult` 글 처리, 서버 몫), 본인인증 도입 시 자기 신고 값을 어떻게 다룰지.
6. 보기 설정의 글자 크기를 PrimeVue 위젯(단추·입력칸) 글자에도 적용할지(지금은 Tailwind 글자만 — community 의 `text-scale.css`·프리셋 보정은 옮기지 않았다).

## 결정 (2026-10-24, 사용자 "추천대로")
- **쪽지 한도**(하루 20통 · 새 상대 5명 · 가입 7일 뒤부터)는 처음에는 서버 상수로 둔다(community 와 같다). 운영자가 바꿀 필요가 생기면 사이트 설정으로 옮긴다.
- **쪽지 받음 알림**: 쪽지 서버를 만들 때 회원 이벤트 `DM`(앱 알림, 회원이 끌 수 있음)을 더한다.
- **닉네임 그림 검토**: 쪽지·차단 서버 단계에서 관리자 검토 화면과 권한(`nickname-image.manage`)을 함께 만든다(운영자 이벤트 `NICKNAME_IMAGE_PENDING` 발행 포함).

## 결정 (2026-10-24 2차, 사용자 "추천대로" — community 정책을 로보얌에 맞춤)
- **쪽지 나머지 값(미확정 1)**: 연속 전송 3초 간격(`429 RATE_LIMITED` + `retryAfterSeconds`)·500자·1년 보관(매일 정리, 신고된 대화는 처리 끝까지 보관)도 서버 상수로 둔다(community `api-dm.md` 와 같다). 관리자 화면은 두지 않는다.
- **글·댓글의 차단·메모(미확정 3)**: 로보얌 글 상세·댓글에 회원 메뉴(⋯)가 생길 때 차단·메모를 넣는다. 이번 범위가 아니다.
- **성인 확인(미확정 5)**: 19금 글은 성인 열람자(성인 확인 회원·글쓴이·운영진)만 보고 그 밖에는 글 ID 로 와도 `404`, 여러 게시판 모음에는 싣지 않는다(community `api-board.md`). **본인인증과의 관계는 커머스 0-4(본인인증·기능 제한) 병합 뒤에 정한다** — 커머스는 다른 작업에서 진행 중이다.
- **글자 크기(미확정 6)**: 보기 설정의 글자 크기를 PrimeVue 위젯(단추·입력칸·선택 등) 글자에도 적용한다. community 의 `--text-scale` 방식을 user-web 에 맞춰 옮긴다(화면 몫, 서버 계약은 그대로).

## 결정 (2026-10-24 3차, 사용자 지시)
- **용어**: 「닉네임 그림」을 **「닉네임 이미지」**로, 관리자 처리를 **「심사」**로 통일한다(관리자 메뉴·화면 「닉네임 이미지 심사」, 대기 = 「심사 대기」). 회원 화면·서버 문구(오류·알림·이벤트 이름) 모두 같다. 권한 카탈로그 이름과 같다.

## 서버 구현·화면 연결 결과 (2026-10-24)
응답 모양은 위 「목업 API 후보」와 각 `.types.ts` 그대로이고, 아래는 달라진 점·정한 것만 적는다. 테스트·마이그레이션 적용·실제 호출은 하지 않았다(컴파일·타입 검사만).

### 저장 (shared)
- `V70__dm.sql`: `dm_threads`(두 회원 짝 유일) · `dm_boxes`(회원마다 쪽지함 줄 — 읽은 위치·지운 위치) · `dm_messages` · `dm_user_states`(수신 설정 + 오늘 한도 줄, 잠가서 한 트랜잭션에 차감).
- `V71__blocks_memos.sql`: `user_blocks` · `blocked_words`(`lower(word)` 유일) · `user_memos`.
- `V72__nickname_images.sql`: `nickname_image_requests`(상태 `PENDING·APPROVED·REJECTED·REVOKED·CANCELED·REMOVED`, 대기·적용 중은 회원당 하나 — 부분 유일 색인). **같은 파일에서 `ck_member_notifications_type` 을 DROP 뒤 다시 만든다**(`DM`·`NICKNAME_IMAGE_REVIEWED` 추가) — 다른 작업도 이 CHECK 를 다시 만들면 나중 것만 남으니 합칠 때 값 목록의 합집합을 확인한다.
- DB 배치 `dm-retention`(`R__batch_21_dm_retention.sql`, 매일 04:20 KST, `BatchCatalog.DM_RETENTION`): 마지막 쪽지가 365일보다 오래된 대화는 통째로, 남는 대화는 365일 지난 쪽지만 지운다. 「신고된 대화는 처리 끝까지 보관」은 신고 대상에 쪽지가 생길 때 넣는다.
- admin `V5` 는 쓰지 않았다(관리자 메뉴는 코드 `AdminScreenRegistry` 동기화).

### 쪽지 (api)
- 보낼 자격은 글쓰기 판정과 같은 `BoardActorResolver`: 이메일 인증 `email_verified_at` · 정지 `users.status` SUSPENDED/BANNED → `ACCOUNT_RESTRICTED` · 사업자 `ROLE_MEMBER_BUSINESS` → `BUSINESS_RESTRICTED` · 가입 7일 `users.created_at`. 새 제재·기능 제한 모델은 만들지 않았다(커머스 0-4 몫).
- **이유 하나 추가**: 회원 역할에 회원 권한 `message.send` 가 없으면 `reason: NOT_PERMITTED` / `403 DM_NOT_PERMITTED`(차례: 사업자 다음, `TOO_NEW` 앞). 기본 시드에서는 사업자만 이 권한이 없어 그 앞에서 걸린다. 화면 `DmReason`·문구 반영.
- `429 RATE_LIMITED`(3초, `retryAfterSeconds`)는 새 공용 오류 코드. `DM_DAILY_LIMIT`·`DM_NEW_PARTNER_LIMIT` 도 `retryAfterSeconds`(한국 시간 0시까지).
- 받는 사람 검색은 `lower(nickname) LIKE '%q%'`(앞부분 일치 먼저, 최대 10명).
- 회원 알림 `DM`(category **`dm`**, `filter=dm` 도 받음 — 화면 거르기 단추는 전체/댓글/계정 그대로): `actor` = 보낸 회원, `link` = `/messages/{threadId}`. **같은 대화의 안 읽은 DM 알림이 있으면 새 줄·메일을 만들지 않는다**. 대화를 읽음·지움·답장하면 그 대화의 DM 알림도 읽음. 회원이 끌 수 있음(잠금 없음).

### 차단·메모 (api)
- 운영진(`BLOCK_TARGET_PROTECTED`) = 게시판 매니저·스텝을 하나라도 맡은 회원(기존 규칙).
- 칸을 채우는 곳: 게시판 글 목록 `GET /api/boards/{slug}/posts`(로그인 때)의 `PostRowView.blocked`·`PostAuthorView.memo`, 댓글 목록의 `CommentView.blocked`·작성자 `memo`. 그 밖의 응답(최신·인기·검색·공개 프로필)은 null — **합칠 때 메인 세션이 붙인다**.
- **합친 뒤 붙임(2026-10-24)**: 최신 `/api/home/latest`·인기 `/api/home/popular`·통합 검색 `/api/search`·프로필 작성글 `/api/users/{id}/posts` 의 줄(`PostFeedRowView`·`SearchRowView`)에 `blocked`·작성자 `memo`, 프로필 작성 댓글 줄(`AuthorCommentRowView`)에 `blocked`(단어는 댓글 본문), 공개 프로필 `blockedByMe`(회원 차단만)·`memo` 를 싣는다 — 모두 `ViewerBlocks`(같은 칸·같은 판정, 쪽마다 한 번 모아 읽음, 비로그인·본인은 비어 있음). **검색도 결과에서 빼지 않고 표시만 한다**(위 「검색 결과는 서버가 뺀다」를 대신함 — 게시판 목록과 같게, 화면이 접는다). 인기글은 회원 공통 스냅샷으로 순서·쪽을 정한 뒤 줄을 만들 때 붙인다(공유 캐시 없음).
- **글 상세(2026-10-24)**: `GET /api/posts/{id}` 의 `PostDetailView.blocked`(`USER|WORD|null`, 단어는 글 제목 — 게시판 글 목록과 같은 판정)·작성자 `memo` 를 `ViewerBlocks` 로 싣는다(비로그인·내 글 null, 본문은 그대로 — 가리는 것은 화면 몫). `PUT /api/posts/{id}` 응답은 두 칸 늘 null.
- 화면 접힘: 최신·인기·검색·프로필 작성글(`FeedPostItem`)과 프로필 작성 댓글 탭도 게시판 글 목록·댓글과 같은 `FoldedRow`(같은 문구, 「보기」로 그 자리에서 펼침·「다시 숨기기」)로 접는다(2026-10-24). 인기글은 순위 칸을 남긴다.
- 글 상세 접힘(2026-10-24): `/posts/{id}` 에서 `blocked`(+ 이 기기에서 방금 바꾼 차단)이면 제목(말머리)·본문·추천/링크 복사/신고 줄을 같은 `FoldedRow` 로 접는다(목록처럼 제목도 가림 — 단어 차단은 제목에 걸린다, 탭 제목도 「글」). 게시판 이름·작성자 줄(메모 칩)·예약 삭제/보호 알림·수정/삭제·이전·다음 글·댓글(따로 접힘)은 남긴다. 「보기」로 펼치고 「다시 숨기기」로 접으며, 다른 글로 가면 다시 접힌다.
- 화면: 셸 로더 `useBlockOverridesLoader`·목업 한 벌·화면의 단어 차단 판정을 지웠다. 차단/해제·메모 직후 바로 보이게 하는 이 기기 덮어쓰기(`useBlockOverrides`)는 남겼다. 공개 프로필 목업(`user-profile-mock.ts`, 다른 작업 몫)은 `blockedByMe: false`·`memo: null` 을 준다 — 실제 프로필 서버가 붙으면 채워진다.

### 닉네임 이미지 (api·admin)
- **자격**: 관리자 설정 「신청 조건」으로 판정한다(아래 「결정 (2026-10-24 4차)」·「신청 조건 설정 결과」). 처음 구현의 회원 역할 권한 `nickname-image.request` 방식은 없앴다.
- 저장: 기존 미디어(`ImageStorage`·`ImageCodec`·`ImageGate`), 키 `nickname-image/{무작위 32자}/image.{확장자}`, 움직이는 GIF 는 첫 장면 `still.png` 를 따로 둔다(WebP·GIF 는 원본 크기 — 화면이 240×60 틀에 맞춰 그린다). **심사 전 이미지도 `/media/**` 공개 주소**(글 이미지와 같은 판단 — 짐작할 수 없는 주소). 형식은 파일 서명으로 판정, 200KB · 최소 120×30 · 4:1(±1%) · 400만 화소 · GIF 30프레임, 움직이는 WebP 거절. 반려 파일은 다음 신청까지 두고 취소·대체·해제된 파일은 지운다.
- `409 NICKNAME_IMAGE_REAPPLY_TOO_SOON` 은 `retryAfterSeconds` 만 싣는다(`extras.canReapplyAt` 없음 — 같은 값은 GET 의 `reapplyAt`·`request.canReapplyAt`). 하루 10번은 저장된 신청만 센다.
- 관리자 API(admin, `ApiResponse`, 쓰기 CSRF): `GET /api/admin/nickname-images?status=PENDING|APPROVED|REJECTED|REVOKED&keyword&page(1부터 — 다른 관리자 목록은 0부터)&size(1~100, 기본 20)` → `{items[{id, member{id,nickname,grade,gradeLabel}, status, url|null(파일을 지운 신청), contentType, bytes, width, height, frames, createdAt, reviewedAt, reviewer{id,name|null}|null, rejectReason, revokedAt, revokeReason}], total, page, size, pendingCount}`(`nickname-image.read`) · `POST /{id}/approve`·`/{id}/reject {reason}`·`/{id}/revoke {reason}`(1~200자) → 200 같은 항목(`nickname-image.manage`). 오류 `404 NICKNAME_IMAGE_NOT_FOUND` · `409 NICKNAME_IMAGE_NOT_PENDING` · `409 NICKNAME_IMAGE_NOT_ACTIVE` · `400 fields.reason`. 승인은 앞 이미지를 바꾸고, 반려 뒤 72시간·**해제 뒤 7일**(community 값을 따른 가정) 재신청 금지. 감사 `NICKNAME_IMAGE_APPROVE|REJECT|REVOKE`(대상 종류 `NICKNAME_IMAGE`).
- 운영자 이벤트 `NICKNAME_IMAGE_PENDING` 예약 해제(링크 `/members/nickname-images`, 5분 묶음). 회원 알림 `NICKNAME_IMAGE_REVIEWED`(승인·반려·해제마다 한 줄, category system, 잠금 `ACTION` — 회원이 끌 수 없음, `body` = 사유, `link` = `/settings/nickname-image`).
- 관리자 화면 `/members/nickname-images`(회원 묶음 등급 뒤, 메뉴 「닉네임 이미지 심사」): 상태 탭(심사 대기 N·승인·반려·해제) · 닉네임 검색 · 240×60 미리보기(누르면 3배 확대) · 회원 등급·닉네임 · 파일 정보 · 신청·처리 정보 · 승인 / 반려·해제(사유 대화상자). `nickname-image.manage` 가 없으면 처리 칸이 없다. 권한 `nickname-image.read`·`manage` 는 `featureReady=true`.
- 관리자 메뉴 `UtilAdminMenu.isActive` 가 **가장 구체적인 메뉴 항목**에 주소를 준다(`/members/nickname-images` 에서 「회원」이 같이 켜지지 않게 — `/points/policy`·`/boards/defaults/*` 도 그 하위 메뉴만 켜진다).

### 건드린 공용 파일 (줄만 더함)
`Permission`(`NICKNAME_IMAGE_READ/MANAGE` featureReady) · `AdminScreenRegistry`(`nicknameImages`) · `MemberEvent`·`MemberNotificationType`(끝에 `DM`·`NICKNAME_IMAGE_REVIEWED`) · `AdminNotificationEvent`(`NICKNAME_IMAGE_PENDING` 예약 해제) · `AuditActions`·admin `AuditLabels` · `BatchCatalog`(`DM_RETENTION`) · `MessageTargetCatalog`(회원 문구 대상 2개) · `messages_ko/en.properties`(끝에 묶음, `error.PROFILE_NOT_FOUND`·`error.RATE_LIMITED` 새로 — 다른 작업과 겹치면 하나로) · admin-web `util-admin-menu.ts`·`util-permission-catalog.ts`·`i18n/locales/{ko,en}/{menu,index}.ts` · user-web `i18n/locales/{ko,en}/dm.ts`. 보안 설정은 그대로.

### 미확정·후속
1. 닉네임 이미지 자격의 기본값(모든 일반 회원 vs community 「단골 이상」) — 역할 설정으로 바꿀 수 있다.
2. 받는 사람 검색 `LIKE '%q%'` 는 회원이 많아지면 `pg_trgm` 색인이 필요하다.
3. 해제 뒤 재신청 7일은 가정. 탈퇴 때 쪽지·차단·닉네임 이미지 정리는 탈퇴 기능이 생길 때.
4. 작성자 표시에 닉네임 이미지 그리기(`nicknameImage` 칸)·쪽지 신고·글/댓글 ⋯ 메뉴의 차단·메모는 범위 밖.

## 결정 (2026-10-24 4차, 사용자 지시)
- **닉네임 이미지 신청 조건은 관리자 설정 화면에서 정한다**. 방식은 둘이다. ① **등급 기준**: 최소 등급 하나를 고르고, 이메일 인증은 늘 필요하다. ② **사용자 정의**: 최소 등급(없어도 됨)·가입 후 일수·글·댓글 수·보유 포인트·이메일 인증 필요 여부(끌 수 있음)를 정하고, 조건은 모두 채워야 한다. 0 은 「조건 없음」이다. 기본값은 **등급 기준 · 베테랑 이상**.
- 회원 역할 권한 `nickname-image.request`(「닉네임 이미지 신청」)는 없앤다. 설정 하나가 자격의 원천이다.
- 설정 화면은 설정 메뉴에 따로 둔다: 새 묶음 「회원」 › 「닉네임 이미지」 `/settings/nickname-image`(보기 `nickname-image.read`, 저장 `nickname-image.manage`).

## 신청 조건 설정 결과 (2026-10-24)
- 저장: shared `V72` 에 `nickname_image_settings`(행 하나 `id=1` · `mode` GRADE|CUSTOM · `grade_key`(외래 키 없음) · `min_days` 0~3650 · `min_activity` 0~100000 · `min_points` 0~100000000 · `require_email_verified` · `updated_at/by`)를 더했다. 기본 행은 GRADE·VETERAN이고, 기존 DB에서 VETERAN이 지워졌으면 가장 높은 등급을 넣는다. GRADE면 등급이 꼭 있고 이메일 인증은 true다(CHECK). 설정은 요청마다 읽는다(저장하면 바로 반영).
- `nickname-image.request` 삭제: admin 서버가 뜰 때 `PermissionCatalogSynchronizer` 가 `role_permissions` → `permissions` 순서로 정리한다(같은 트랜잭션). 정리 SQL은 따로 없다.
- 회원 `GET /api/me/nickname-image` 의 `requirements = {mode, items}`: 켜진 조건만, `EMAIL_VERIFIED{met}` → `GRADE{required{code,label}|null(지워진 등급), current, met}` → `DAYS`·`ACTIVITY`·`POINTS{required, current, met}` 차례로 온다. `ACTIVITY` 는 즉시 승급과 같은 셈(`MemberActivityRepository.countUpTo`, 필요한 수에서 멈춤)이고, `POINTS` 는 포인트 잔액(`point_balances`)이다. 고른 등급이 지워졌으면 등급 조건을 못 채운 것으로 본다.
- `ineligibleReason`·POST 오류 차례: 이메일 인증(설정이 요구할 때) → 정지(`SUSPENDED`/`403 ACCOUNT_RESTRICTED`) → 나머지 조건(`REQUIREMENTS_NOT_MET`/`403 NICKNAME_IMAGE_REQUIREMENTS_NOT_MET` — `GRADE_TOO_LOW` 대체). 취소·해제는 정지만 막고, 이메일 인증은 설정이 요구할 때만 본다. 이미 적용 중인 이미지는 조건이 바뀌어도 그대로다.
- 관리자 `GET/PUT /api/admin/nickname-image-settings` → `{mode, gradeKey|null, minDays, minActivity, minPoints, requireEmailVerified, gradeMissing, grades[{key,label,level}](등급 고르개 — grade.read 없이도 보임), updatedAt, updatedBy{id,name|null}|null}`. 오류는 `400 VALIDATION fields.<칸>`. GRADE로 저장해도 사용자 정의 칸 값은 남는다(이메일 인증은 true로). 바뀐 칸이 없으면 쓰지 않는다. 감사 기록 `NICKNAME_IMAGE_SETTINGS_UPDATE`(대상 `NICKNAME_IMAGE_SETTINGS`, 바뀐 칸만 전·후 값).
- 화면: 관리자 설정 화면(지금 조건·「저장하면」 요약 한 줄, 방식 고르기, 최소 등급 고르개, 사용자 정의 칸, 지워진 등급 경고, 하단 저장 줄, 처리 권한이 없으면 읽기 전용), 심사 화면에서 설정으로 가는 링크, 회원 화면의 조건 체크리스트(이메일 인증 막음은 조건에 있을 때만).
- 공용 파일: `MemberPermission`(줄 삭제) · `BuiltinRoleSeeder` · `AuditActions`·`AuditLabels` · `AdminScreenRegistry`/`util-admin-menu.ts`(설정 쪽 `menu.system` 바로 뒤에 묶음 `menu.memberSettings`) · `util-permission-catalog.ts`(회원 권한 줄 삭제) · admin-web locale `menu`·`permission`·`nicknameImage` · `messages_ko/en`.
