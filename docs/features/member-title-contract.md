# 회원 칭호 계약 (시안 확정 2026-11-17 · 구현 2026-11-17)

**의도**: 등급(경험치 사다리)과 따로, 「무엇을 잘하는 사람인가」를 보여 주는 칭호를 만든다. 한 회원이 여러 개를 가질 수 있고, 자동·신청 심사·운영자 수여 세 가지 방법으로 받는다. 등급은 칭호를 받는 조건으로만 쓴다.
**관련 계약**: `grade-level-contract.md`(등급 — 섞지 않는다) · `point-board-tier-contract.md`(우수 글) · `trial-group-contract.md`(리뷰어 단계·체험단 우대).
**시안**: 대화 시안 「2 / 3 · 칭호」(2026-11-17).

## 기본 칭호 (설치 프리셋·데모에 넣음)
| 칭호 | 받는 방법 | 조건 · 단계 | 심사 |
|---|---|---|---|
| 이달의 작가 | 운영자 수여 | 매달 최대 N명(설정), 받은 달이 기록으로 남음 | — |
| 올해의 작가 | 운영자 수여 | 그해 이달의 작가 중에서 | — |
| 명예 작가 | 자동 | 이달의 작가 M번(설정) · 기간 없음 · 명예의 전당 | — |
| 리뷰어 | 신청·심사 | 정식 이상 · 사용기 3편(그중 구매 인증 1편 이상) · 단계 동·은·금 = 체험단 약속 지킴 3·10·30번 | 운영자 + 사용기 게시판 매니저 |
| 기자단 | 신청·심사 | 정식 이상 · 뉴스 10편 · 신고로 숨겨진 글 없음 | 운영자만 |
| 인기글 | 자동 · 동·은·금 | 하루 인기글 상위 진입 10·30·100번 | — |
| 출석 100일 | 자동 | 출석 100일 | — |

## 작가 칭호 설정 (칭호 관리 맨 위)
- 이달의 작가 한 달 최대 인원: 기본 **3**, 1~20.
- 명예 작가가 되는 이달의 작가 횟수: 기본 **3**, 1~24.
- 기준을 올려도 이미 받은 명예 작가는 그대로(회수 없음). 내리면 새 기준을 채운 회원에게 바로 소급해서 준다.
- 이달의 작가 후보 = 그 달에 「우수 글」로 뽑힌 글의 글쓴이 + 「이달의 작가 후보에 넣기」로 넣은 글쓴이.

## 화면
- 회원 글 목록·글 보기·댓글 작성자 줄(`PostAuthorLabel`): **대표 칭호 1개**만. 기본값 = 가진 작가 칭호 중 가장 높은 것, 없으면 표시 없음. 이달의 작가는 줄여서 「11월의 작가」. 등급은 지금처럼 보이지 않는다. 제재 기간에는 칭호를 숨긴다.
- 회원 공개 프로필(`/users/...`): 등급 도트·레벨 아래 칭호 모음. 같은 칭호 여러 번은 「이달의 작가 ×4」(누르면 받은 달 목록). 비공개 프로필이면 남에게는 대표 칭호만.
- 회원 내 정보 › 칭호(`/me/titles` 후보): 대표 칭호 고르기(「안 보이기」 포함) · 다음 칭호 진행 막대 · 신청형 칭호 신청 · 신청 상태(승인·거절 사유·다시 신청할 수 있는 날).
- 칭호를 받으면 알림 + 다음 접속 때 한 번 축하 창(「대표 칭호로 쓰기」·「명예의 전당 보기」, 보유 비율 표시).
- 누구나 보는 **명예의 전당 · 칭호 도감**(`/hall-of-fame` 후보): 명예 작가 · 역대 올해의 작가 · 역대 이달의 작가 · 칭호별 받는 방법·가진 회원 수·비율. 들어가는 곳 = 푸터와 내 정보.
- 관리자 `/members/titles`(후보) 탭: **칭호** · **확인할 일**(칭호 신청 심사, 체험단 쪽 확인 일과 같은 목록) · **이달의 작가 뽑기** · **주고 뺀 기록**.
- 관리자 칭호 만들기·고치기 서랍: 이름(최대 6자, 등급 이름과 겹치면 저장 안 됨) · 그림(아래 「아이콘」 목록에서 고름)·칩 색(보라 = 수여·심사, 초록 = 자동) · 받는 방법 · 심사하는 사람(운영자만 / 운영자 + 연결 게시판 매니저) · 조건(등급 이상·게시판·글 수·인기글 횟수·출석 일수 등) · 단계(없음 / 동·은·금, 자동 칭호와 리뷰어) · 혜택(체험단 먼저 신청 · 매거진 첫 화면 후보 · 한정 아바타 테두리) · 휴면(6개월 활동 없으면 숨김, 다시 쓰면 돌아옴) · 저장 시 기존 활동 소급 지급.

## 아이콘 (전용 도트 그림 — 사용자 결정 2026-11-17)
- 시안·정본: `member-title-icons-mockup.html`. 12×12 픽셀 지도 + 색표를 화면 코드로 그대로 옮긴다(등급 도트와 같은 방식 — 이미지 파일 없음, `image-rendering: pixelated`, 정수배).
- 크기: 작성자 줄 1배(12px, 등급 도트와 같음) · 공개 프로필·내 정보 2배(24px) · 도감·명예의 전당·관리자 3~4배.
- 기본 그림: 이달의 작가 = **만년필**(금빛 촉·촉 틈·금 띠) · 올해의 작가 = 트로피 · 명예 작가 = 월계관과 별 · 리뷰어 = 별 든 돋보기 · 기자단 = 마이크 · 인기글 = **글 아래쪽에 동·은·금 메달** · 출석 = 달력과 체크(사용자 결정 2026-11-17 — 깃펜·불꽃은 알아보기 어려워 뺌).
- 동·은·금은 모양 그대로 색만 바꾼다(리뷰어 = 돋보기 테, 인기글 = 메달, 범용 메달).
- 범용 그림(운영자가 새 칭호를 만들 때 고름): 메달(동·은·금) · 전구 · 하트. 그림 키는 서버에 `icon` 문자열로 저장(`pen`·`trophy`·`wreath`·`mag`·`mic`·`popular`·`cal`·`medal`·`bulb`·`heart`), 단계 칭호는 단계마다 색 키(`BRONZE`·`SILVER`·`GOLD`).
- 움직임 없음. 접근성: `role="img"` + `aria-label`(칭호 이름).

## 규칙
- AI 회원·운영팀 계정은 늘 제외(자동·신청 모두).
- 심사: 게시판 매니저(`MANAGER`)는 자기 게시판에 연결된 칭호만, 자기 신청은 심사 못 한다. 승인·거절·수여·회수는 모두 감사 기록. 매니저가 준 칭호도 운영자가 회수할 수 있다. 거절하면 사유와 다시 신청할 수 있는 날(기본 30일 뒤).
- 회수: 운영자 회수 · 체험단 미반납 확정(리뷰어) · 사용기 기한 두 번 넘김(리뷰어 정지). 제재 기간에는 숨김(회수 아님).
- 칭호는 권한을 주지 않는다(등급 역할과 섞지 않는다). 혜택은 체험단·노출 같은 기능 쪽에서 칭호를 읽어 판단한다.

## 권한 (서버 `Permission` 후보)
- `title.read` · `title.manage`(칭호 만들기·설정·수여·회수) · `title.review`(신청 심사 — 게시판 매니저는 게시판 스텝 권한으로 자기 게시판 칭호만).

## API 후보
- 공개: `GET /api/titles`(도감) · `GET /api/hall-of-fame` · `GET /api/me/titles` · `PUT /api/me/titles/representative` · `POST /api/me/titles/{key}/applications`.
- 작성자 DTO 에 `title: { key, label, icon, tier, chip } | null`(대표 칭호 — `tier` = BRONZE|SILVER|GOLD|null, `chip` = 칩 색), 공개 프로필에 `titles[]`.
- 관리자: `GET/POST/PUT /api/admin/titles` · `GET/PUT /api/admin/titles/settings`(이달의 작가 최대 인원·명예 작가 횟수) · `POST /api/admin/titles/{key}/grants`·`DELETE`(수여·회수) · `GET /api/admin/titles/applications` · `POST .../{id}/approve|reject` · `GET /api/admin/titles/monthly-candidates?month=`.

## 미확정
- 없음(자동 판정 시점은 아래 「구현 결정」 1 로 닫음).

## 구현 결정 (2026-11-17 — 사용자 「다 개발」 지시로 화면·서버를 함께 만든다)
1. **자동 칭호 판정**: admin 배치 `title-auto-grant` 매일 00:10 KST(인기글 보상 배치 00:00 뒤). 인기글 = 그날 인기글 상위(`popularRewardTopN`, 0 이면 10) 진입 일수 — 인기글 보상 배치가 남기는 날짜별 목록 `point_popular_rewards` 와 같은 뽑기를 포인트와 상관없이 남긴다. 출석 100일 = 출석한 날 수. **명예 작가는 배치를 기다리지 않고** 이달의 작가 수여 트랜잭션에서 바로 준다. 칭호 저장(조건 바꿈) 때 소급 지급은 그 칭호만 배치를 한 번 돌린다(admin 배치 수동 실행과 같은 길).
2. **표**: `titles`(키·이름·그림·칩 색 PURPLE|GREEN·받는 방법 AUTO|APPLY|AWARD·심사자 OPERATOR|OPERATOR_AND_BOARD_MANAGER·연결 게시판·조건 JSON·단계 칸·혜택·휴면·기본 칭호 여부·켜짐·순서) · `member_titles`(회원·칭호·단계·기간 `period`(이달의 작가 `YYYY-MM`, 올해의 작가 `YYYY`, 그 밖 빈 값)·받은 때·준 사람·회수 때·회수 사유 — 회수해도 줄은 남김) · `title_applications`(상태 PENDING|APPROVED|REJECTED·사유·다시 신청할 수 있는 날) · `title_settings`(한 행) · `title_monthly_candidates`(달·글·회원·넣은 운영자) · 회원 대표 칭호(`member_title_prefs`: 회원·대표 칭호 키·안 보이기). 기본 칭호 7개는 마이그레이션으로 넣는다(모든 설치·데모 공통).
3. **이달의 작가 후보** = 그 달 `post_features`(살아 있는 선정) 글의 글쓴이 + `title_monthly_candidates`. 관리자 글 관리 ⋯ 메뉴 「이달의 작가 후보에 넣기」/「후보에서 빼기」 = `POST/DELETE /api/admin/titles/monthly-candidates` `{ postId }`(`title.manage`).
4. **알림·축하 창**: 알림 이벤트 `TITLE_GRANTED`. 축하 창은 `GET /api/me/titles` 의 `unseen[]` → 닫으면 `POST /api/me/titles/seen`.
5. **게시판 매니저 심사**: 회원 앱 게시판 스텝 화면(`/boards/{slug}/actions` 탭)에 「칭호 신청」 탭 — `GET /api/boards/{slug}/staff/title-applications` · `POST .../{id}/approve|reject`(api 서버, 그 게시판에 연결된 칭호만, 자기 신청 불가).
6. **리뷰어 단계**: 체험단 「약속 지킴」 횟수로 동·은·금 — 체험단 기능이 생기기 전에는 승인 = 단계 없음(기본). 체험단 쪽 회수(미반납 확정·기한 두 번)는 체험단 구현 때 연결한다.
7. **응답 모양**
   - 작성자 DTO `title: { key, label, icon, tier, chip } | null` — 대표 칭호(기본 = 가진 작가 칭호 중 가장 높은 것: 명예 작가 > 올해의 작가 > 이달의 작가), 「안 보이기」면 null, 제재 기간·휴면이면 null. 이달의 작가의 `label` 은 「11월의 작가」(가장 최근 받은 달).
   - 공개 프로필 `titles: [{ key, label, icon, tier, chip, count, periods[] }]` — 비공개 프로필이면 남에게는 대표 칭호 하나만.
   - `GET /api/titles` → `[{ key, label, icon, chip, method, tiered, howTo, holders, ratio, tiers: [{ tier, threshold, holders }] }]` · `GET /api/hall-of-fame` → `{ honorary: [회원], yearly: [{ year, members }], monthly: [{ month, members }] }`(회원 = id·닉네임·아바타).
   - `GET /api/me/titles` → `{ representative: { key } | null, hidden, owned: [...공개 프로필 줄], progress: [{ key, tier, current, target }], applicable: [{ key, label, conditions: [{ label, met }], canApply, reapplyAt }], applications: [{ id, key, status, reason, reapplyAt, createdAt }], unseen: [{ key, label, icon, tier, chip, ratio }] }` · `PUT /api/me/titles/representative` `{ key | null, hidden }` · `POST /api/me/titles/{key}/applications`.
   - 관리자: `GET/POST/PUT /api/admin/titles`(`title.read`/`title.manage`) · `GET/PUT /api/admin/titles/settings` `{ monthlyMax, honoraryCount }` · `POST /api/admin/titles/{key}/grants` `{ memberId, period?, tier?, reason }` · `DELETE /api/admin/titles/{key}/grants/{grantId}` `{ reason }` · `GET /api/admin/titles/applications?status&key` · `POST /api/admin/titles/applications/{id}/approve|reject` `{ reason }`(`title.review`) · `GET /api/admin/titles/monthly-candidates?month=YYYY-MM` → `{ month, max, awarded: [...], candidates: [{ member, posts: [{ id, title, featured, manual }] }] }` · `GET /api/admin/titles/grants?key&memberId&page&size`(주고 뺀 기록) · 회원 상세에 칭호 목록.
8. **권한**: `title.read`·`title.manage`·`title.review`(운영자). 칭호 이름은 최대 6자, 등급 이름과 겹치면 400. AI 회원·운영팀 계정(`operator_member_accounts`)은 자동·신청·수여 모두 제외.

## 서버 구현 결과 (2026-11-17)
- **마이그레이션**: shared `V240__member_titles.sql`(표 6개·기본 칭호 7개·알림 종류 `TITLE_GRANTED`·권한 행 — `grade.read` 가진 운영자 역할에 `title.read`, `grade.manage` 가진 역할에 `title.manage`·`title.review`) · admin `V11__admin_menu_member_titles.sql`(저장한 메뉴 구성의 「등급」 아래 `memberTitles`). 배치 `title-auto-grant` 는 Java(`BatchCatalog`, 매일 00:10 KST).
- **이름 6자** = 공백을 뺀 글자 수(기본 칭호 「출석 100일」 때문). 다른 칭호와 이름이 같으면(공백·대소문자 무시) 409 `TITLE_NAME_TAKEN`, 등급 이름과 같으면 400 `fields.name`.
- **조건 JSON**(`titles.conditions`) = `[{ type, value?, boardId?, boardSlug?, gradeKey? }]` — `GRADE_AT_LEAST`(gradeKey) · `POSTS`(value, 게시판 — 없으면 모든 게시판) · `POPULAR_DAYS` · `ATTENDANCE_DAYS` · `NO_REPORT_HIDDEN`(지금 신고·불법·권리 침해로 숨겨진 글 없음). 기본 칭호는 게시판을 slug(`review`·`news`)로 들고 게시판이 생기면 ID 로 굳는다(설치 마법사가 게시판을 마이그레이션 뒤에 만든다). 단계 = `tierMetric`(POPULAR_DAYS·ATTENDANCE_DAYS·POSTS·TRIAL_KEPT) + `tierBronze·tierSilver·tierGold`.
- **관리자 모양**(admin-web 타입에 맞춤): 칭호 줄·저장 본문은 `TitleSpec` 칸 그대로 + `key`·`builtin`·`holders`·`boardName`·`howTo`·조건 `label`. `PUT /api/admin/titles`(본문 `key`)와 `PUT /api/admin/titles/{key}` 둘 다. 만들 때 `key` 는 선택(비우면 `t…` 발급). 저장하면 자동 칭호는 그 칭호만 배치 「지금 실행」(본문 `retroactive:false` 면 안 함). 받은 줄 = `{ grantId, title:{key,label,labelEn,icon,tier,chip}, member:{id,nickname,memberNo}, period|null, source, grantedAt, grantedBy(이름), grantedByKind, reason, revokedAt, revokedBy(이름), revokeReason, active, honorary }`. 신청 목록 `{ items:[{ id, title, member, status, conditions:[{type,label,met}], boardName, reason, reapplyAt, createdAt, reviewedAt, reviewedBy, reviewedByKind }] }`. 이달의 작가 후보 `candidates:[{ member, avatarUrl, awarded, posts }]`, `awarded` = 받은 줄. 회원 상세 `titles`(받은 줄, 회수 포함)와 `GET /api/admin/members/{id}/titles`. 글 관리 목록·상세 줄 `monthlyCandidate`(이번 달 후보로 넣은 글). 설정에 `reapplyDays`(1~365, 기본 30)·응답 `honoraryGranted`(기준을 내려 바로 준 수).
- **공개 모양**: 계약 7 그대로 + `labelEn`. `representative` 는 고른 칭호만(기본이면 null). `PUT /api/me/titles/representative` 는 바뀐 내 칭호 전체를 돌려준다. 게시판 매니저 목록 `{ items:[{ id, title, member:{id,nickname,avatarUrl}, status, conditions, reason, reapplyAt, createdAt, reviewedAt, canReview }] }`.
- **규칙**: 자동 판정은 운영자가 회수한 칭호를 다시 주지 않는다. 회원 상태 SUSPENDED·BANNED 면 작성자 줄·명예의 전당·남이 보는 프로필에서 칭호를 숨긴다. 휴면 = 고른 칭호의 휴면 숨김이 켜졌고 6개월 동안 글·댓글이 없음. 리뷰어의 「구매 인증 1편」은 구매 인증 기능이 생길 때 조건에 더한다(지금은 등급 + 사용기 3편).
