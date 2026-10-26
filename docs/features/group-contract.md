# 소모임 계약

## 관리자 화면 (목업 — 2026-10-24, 화면 확정 전)

**의도**: 운영자가 개설 신청을 승인·반려하고, 열린 소모임을 찾아 비활성화·다시 열기를 하며, 소모임 분류(소모임 게시판 할당)와 소모임 설정(개설 규칙·매니저·공개 범위·운영 한도)을 정한다. community 관리자 앱의 소모임 화면을 UI 그대로 옮겼다.
**참고(읽기 전용)**: community `docs/api-groups.md` 1·4절(개설 규칙·운영 한도)·5-1(매니저 규칙)·6절(6-1 개설 승인 · 6-2 소모임 관리 · 6-3 분류)·11-9·11-10. 프론트 기준 admin-web `composables/group/api/use{GroupAdmin,GroupCategory,GroupSettings}Api.ts`(+`.types.ts`, 목업 `mock/group-mock.ts`).

### 화면 경로 (일반 메뉴 「소모임」 묶음 — 메뉴 키 `group`)
| 메뉴 키 | 주소 | 화면 | 메뉴 권한 |
|---|---|---|---|
| `groups` | `/groups` (`?status=&closed=1&offline=1&visibility=&q=&page=&group=ID`) | 소모임 목록 + 상세 서랍(비활성화·다시 열기) | `group.read` |
| `groupRequests` | `/group-requests` | 개설 승인(승인·반려) | `group.read` |
| `groupCategories` | `/group-categories` (`?category=ID`) | 소모임 분류(분류 만들기·이름·게시판 할당·순서·삭제) | `group.read` |
| `groupSettings` | `/group-settings` | 소모임 설정 네 구획 + 변경 이력 서랍 | `group.read` |
- 아이콘: 묶음 `pi pi-comments`, 항목 `pi pi-users`·`pi pi-inbox`·`pi pi-tags`·`pi pi-sliders-h`. 차례는 포인트 묶음 다음. 주소는 앞부분이 겹치지 않게(`/groups` 와 `/group-…`) 두었다.

### 권한 (카탈로그에 있던 코드를 그대로 쓴다 — `featureReady` 는 아직 false)
- `group.read`(READ) 네 화면 열기 · `group.manage`(MANAGE) 승인·반려·비활성화·다시 열기 · `group.settings`(CONFIG) 분류·설정 저장. 권한이 없으면 단추를 막거나 숨기고 읽기 전용 안내.
- community 와 다름: 분류·설정은 community 에서 최고 관리자 전용(`ADMIN_ONLY`)이지만 로보얌은 `group.settings` 로 위임한다. 오류도 `MENU_FORBIDDEN`·`MENU_READ_ONLY`·`ADMIN_ONLY` 대신 `403 PERMISSION_DENIED`.
- 운영자 알림 이벤트 `CLUB_OPEN_REQUEST_PENDING`(예약, `group.manage`) — 개설 승인 화면이 그 이벤트 화면 링크를 둔다(`notification.read`). 서버가 개설 신청(`PENDING`)을 받을 때 발행하고 `featureReady` 를 켠다.

### API 후보 (모양은 community 관리자 절과 같다 — 각 `use*Api.ts` 머리말이 정본)
- `GET /api/admin/groups/requests` · `POST /api/admin/groups/{id}/approve` · `POST …/{id}/reject {reason 1~200}` (404 `GROUP_NOT_FOUND`)
- `GET /api/admin/groups?q&status&closed&offline&visibility&page&size` → `{items, page, size, hasNext, total, counts{all,active,inactive,closed,offline}, pendingRequests}` · `GET /api/admin/groups/{id}` · `POST …/{id}/deactivate {reason 1~200}`(409 `GROUP_ALREADY_INACTIVE`) · `POST …/{id}/reactivate {memo 0~200}`(409 `GROUP_NOT_INACTIVE`)
- `GET·POST /api/admin/group-categories` · `PUT …/{id} {name, boardIds, updatedAt?}`(409 `GROUP_CATEGORY_STALE`·`GROUP_CATEGORY_NAME_TAKEN`) · `PUT …/order {ids}` · `DELETE …/{id}`(409 `GROUP_CATEGORY_NOT_EMPTY` + 최상위 `groupCount`)
- `GET·PUT /api/admin/group-settings/{creation,manager,visibility,policy}-rules` — `{values, defaults, limits, fromDefaults, updatedAt, updatedBy}` (+ creation `pendingRequestCount`, manager `usage`, visibility `usage`·`noticeSent`). PUT 은 구획의 칸 모두, 틀린 칸은 `400 fields.<칸>`(기본>최대 `fields.limitDefault`, 최소>최대 정원 `fields.capacityMax`).
- **로보얌 추가 후보**: `GET /api/admin/group-settings/history?section=CREATION|MANAGER|VISIBILITY|POLICY&page&size` → 공용 `SettingHistoryPage` 모양 + `section`. 바뀐 칸의 `before`·`after` 는 서버가 요청 언어의 표시 글자(「승인제」·「켜짐」·「7일」)로 준다고 가정했다. `labelKey` = `group.historyField.<칸>`.
- 감사 행위 후보: `GROUP_APPROVE`·`GROUP_REJECT`·`GROUP_DEACTIVATE`·`GROUP_REACTIVATE`·`GROUP_CATEGORY_CREATE|UPDATE|ORDER|DELETE`·`GROUP_CREATION_RULE_UPDATE`·`GROUP_MANAGER_RULE_UPDATE`·`GROUP_VISIBILITY_RULE_UPDATE`·`GROUP_POLICY_RULE_UPDATE`.

### 저장 규칙
- 설정 네 구획·분류 패널은 **구획마다 따로 저장**(구획 맨 아래 저장 줄). 승인제→자유 개설, 매니저 끄기·켜기·최대 줄이기, 공개 범위 켜기·끄기, 분류 삭제는 확인 창. 저장 전 변경이 있으면 화면 이탈·다른 분류 선택 때 묻는다.
- 사람 칸(모임장·신청자·매니저)은 `GroupPerson { id|null, nickname, withdrawn, grade{label,color,icon}|null }` — community `AuthorView` 중 화면이 쓰는 칸만.

### community 대비 뺀 것
- 소모임 설정의 「소모임의 이름」 구획(용어 설정 `GROUPS` 없음) · 분류 목록의 끌어 놓기·Alt+↑↓(▲▼ 만) · 상세 서랍의 「공개 화면 보기」·「회원 조회 ›」·「신고 보기 ›」 링크(관리자 앱이 공개 주소를 모르고, 회원 관리에 주소로 여는 서랍·신고 처리 화면이 없다) · 좁은 화면 카드 목록(가로 스크롤 표).

### 미확정
1. ~~분류 화면에서 소모임 게시판을 바로 만들지~~ → 2026-10-26: 만든다. 분류 화면의 [소모임 게시판 만들기]가 `/boards?create=group&category=<ID>` 를 연다(`board-contract.md` 「소모임 게시판」).
2. 등급 고르기(`limits.grades`) — 목업은 community 네 등급. 서버는 등급 설정의 등급 키를 줄 것으로 가정.
3. 설정 변경 이력 API(로보얌 추가)의 모양·전후 값 표시 방식.
4. 메뉴·권한의 서버 쪽(`AdminScreenRegistry` 네 줄, `Permission` 의 `group.*` `featureReady=true`)은 서버 단계.

## 소모임 분류 서버 (2026-10-26 — 사용자 「분류 서버까지」, 분류 화면은 실제 API 연결)
- **표**(shared `V88__group_categories.sql` — 2026-10-26 확정: 커머스 1-1 이 V85, 1-2 가 V86·V87 을 써서 다음 번호. Flyway 가 `outOfOrder` 를 끄고 있으니 **커머스 1-1·1-2 를 develop 에 먼저 합친 뒤** 합친다. 그 사이 다른 작업이 V88 을 먼저 쓰면 다시 매긴다): `group_categories(id Snowflake, name varchar(20), name_key varchar(40) UNIQUE, sort_order 10 단위, created_at, updated_by, updated_at)` · `group_category_boards(board_id PK → boards CASCADE, category_id NOT NULL → CASCADE, assigned_order)` — 게시판 하나는 분류 최대 하나.
- **API** `/api/admin/group-categories` 는 admin-web `useGroupCategoryApi.ts` 머리말 그대로(GET `group.read`, 나머지 `group.settings`). `available` = 켜진 `kind=GROUP` 게시판. `PUT {id}` 검사 차례 404 → `fields.updatedAt` → STALE → 입력 → 이름 중복, `boardIds` 는 필수. `GROUP_CATEGORY_NAME_TAKEN` 은 `fields` 없이 최상위 코드만. `updatedAt` 은 이름·게시판 목록이 바뀔 때만 오르고(옮겨 나간 분류도), 분류 순서는 올리지 않는다.
- **`groupCount` 는 늘 0** — 소모임 표가 없다. `GroupCategoryViews.groupCountsByBoard()` 와 삭제의 `GROUP_CATEGORY_NOT_EMPTY` 는 자리만 있고 소모임 서버 단계에서 채운다.
- **감사** `GROUP_CATEGORY_CREATE|UPDATE|ORDER|DELETE`(대상 `GROUP_CATEGORY`), 보관 분류 **GENERAL**(게시판 구성과 같은 운영 일 — 구현 세션 판단, 사용자 확인 전).
- **동시성**: 분류 바꾸기와 「분류를 고른 게시판 만들기」는 어드바이저리 잠금 `7_340_005` 하나로 줄을 선다.
- 그대로 둔 것: `AdminScreenRegistry` 네 줄·`Permission` `group.*` `featureReady`(다른 소모임 화면이 아직 목업). 게시판을 지울 때 그 분류 `updatedAt` 은 오르지 않는다(열어 둔 화면이 지워진 ID 로 저장하면 `400 fields.boardIds`).

## 전체 개발 계획 (2026-10-26 사용자 — 「전부」 옮기고 「멈춤 없이 끝까지」)
- **원본**: community `docs/api-groups.md`(절 번호는 아래 단계에 적는다) + `frontend/app/{pages/groups,components/groups}` + `admin/app` 소모임 화면. 로보얌 관례(권한 코드 `group.*`·`403 PERMISSION_DENIED`, i18n 키, Snowflake ID, 회원 알림 `MemberNotificationType`, 기능 제한 `restriction`, 메뉴 `MenuBuiltin.GROUPS_ALL|MINE|NEW`)에 맞춘다. 원본과 다르게 한 것은 단계마다 아래에 「로보얌 차이」로 적는다.
- **진행 방식(사용자 결정)**: 화면 목업 → 확인 → 서버의 규칙을 이 기능에서는 쓰지 않는다. 단계마다 화면·서버·연동을 한 번에 끝내고, 화면은 단계 끝에 개발자가 몰아서 본다. 관리자 목업 화면 3개(`/groups`·`/group-requests`·`/group-settings`)도 확정으로 본다.
- **작업 자리**: 작업 트리 `.claude/worktrees/group-full`, 브랜치 `claude/group-full`(develop `4641db5` 에서). 단계가 끝날 때마다 커밋하고, develop 에 합치는 것은 사용자 지시로. 마이그레이션은 V90~V93(2026-10-26 develop 병합 때 커머스 1-3 의 V89 다음으로 다시 매김 — 다음 단계는 V94 부터, 합칠 때 develop 의 마지막 번호를 다시 본다).
- **단계** (앞 단계에 기댄다 — 차례대로):
  1. **기본** — api-groups 1·2(분류 제외)·3·4·5(5-1 제외)·6-1·6-2·7(개설 승인 알림): 소모임·멤버·가입 신청·개설 신청 표, 개설 규칙·운영 한도(관리자 설정 4구획 + 변경 이력), 목록·상세·가입·신청 취소·탈퇴·내 소모임, 모임장 관리(정보 수정·멤버·가입 신청 처리·모집 마감·채팅 링크·모임장 넘기기·해산), 관리자 목록·상세·승인·반려·비활성화·다시 열기, 분류 `groupCount` 채우기, `AdminScreenRegistry` 네 줄·`group.*` `featureReady`. user-web `/groups`·`/groups/me`·`/groups/new`·`/groups/[id]`(글·일정 탭은 다음 단계 자리)·`/groups/[id]/manage/*`.
  2. **소모임 글** — 8절: 소모임 게시판에 글쓰기(멤버), 글 탭, 공개 전환·되돌리기, seen, 나간 멤버의 글, 알림 `GROUP_POST_UNPUBLISHED`. 공개 api 의 소모임 게시판 404(`PublicBoardService.isPublic`)를 소모임 경로에서 연다.
  3. **매니저** — 5-1: 임명·맡길 일·사퇴·한도, 관리자 설정 매니저 구획 연결.
  4. **일정** — 9절: 일정·참석·참석자·다음 일정·알림 `GROUP_EVENT`.
  5. **공개 범위·공개 요청** — 11절: 공개 범위 모델·변경·공개 요청 처리·알림 7종·관리자 읽기(ADM-012)·설정.
  6. **홈·프로필·지금 뜨는** — 2절 trending·10절: 홈 인기 소모임 가젯·최신글 소모임 배지·프로필 소모임 탭·탈퇴 때 모임장 확인.

## 단계별 결과 (2026-10-26 — 브랜치 `claude/group-full`, 경로·칸의 정본은 user-web `composables/group/api/useGroupApi.ts` 머리말·admin-web `useGroup*Api.ts` 머리말과 서버 DTO)
- **마이그레이션**: V90 `groups`(소모임·멤버·설정·이력) · V91 `group_posts`(posts.group_id·is_public·group_left_at) · V92 `group_managers`(알림 종류, member_notifications.type varchar(40)) · V93 `group_events` · V94 `group_visibility`. 회원 알림 종류 CHECK 는 단계마다 앞 목록과의 합집합으로 다시 만든다 — develop 에 합칠 때 develop 쪽 목록과 다시 맞춘다.
- **1단계 기본**: 회원 `/api/groups…`·`/api/me/groups…`(목록·분류·개설 규칙·상세·멤버·가입·신청 취소·탈퇴·개설 신청·모임장 관리), 관리자 개설 승인·소모임 관리·설정 4구획·이력, 알림 GROUP_* 10종 + 운영자 `CLUB_OPEN_REQUEST_PENDING`. user-web `/groups`·`/groups/me`·`/groups/new`·`/groups/[id]`·`/groups/[id]/manage/{,edit,members,applications,chat}`, admin-web 목업 3화면 실제 연결.
- **2단계 글**: `/api/groups/{id}/posts`(커서)·`board`(로보얌 추가 — 공개 `BoardDetail` 모양, 소모임 게시판은 공개 게시판 API 가 404 라서)·`seen`·`posts/{postId}/visibility`, 기존 글·댓글·투표·신고 경로는 `GroupPostGate` 한 곳에서 판정. user-web 글 탭(기본)·`/groups/[id]/write`·글 상세 소모임 표시. 관리자 글 관리는 비공개 소모임 글의 제목·본문을 가리고(내용은 신고 상세에서만) 옮기기 409.
- **3단계 매니저**: `/api/groups/{id}/managers`(목록·임명/교체·해임·`me` 사퇴)·`manager-limit`, 알림 5종. 관리 화면은 체크형 권한으로 매니저에게도 열린다.
- **4단계 일정**: `/api/groups/{id}/events`(목록·만들기·상세·수정·취소·응답·참석자), 내 소모임 `nextEvent`, 알림 `GROUP_EVENT`(소모임 알림 중 유일하게 회원이 끌 수 있다).
- **5단계 공개 범위**: `visibility-preview`·`PUT visibility`(쿨다운)·공개 요청(`publish-request`·`publish-requests`·approve·reject)·글 단위 visibility 네 가지 일, 멤버 전용 댓글 가림(`hiddenBy MEMBERS_ONLY`), 알림 7종. `posts.is_public` 의 뜻은 「비멤버에게 보이는가(유효 노출)」로 넓혔고 쓰는 길은 `GroupPostVisibilityService` 하나.

### 로보얌 차이 (원본 community 대비)
- 사람 칸: 회원 API 는 `DmPartnerView`(user-web `GroupUser`), 관리자 API 는 `GroupPerson{grade{label,color,icon}}`.
- 개설에 회원 권한 `group.create` 판정(로보얌 추가, `post.write` 가진 역할에 같이 줌) — 없으면 403 `GROUP_CREATE_NOT_PERMITTED`. 공개 사이트에 로그인하는 최고 관리자가 없어 「운영자 개설 201」·「운영진 등급 면제」 없음.
- 쓰기 자격은 게시판과 같은 코드(`EMAIL_NOT_VERIFIED`·`ACCOUNT_RESTRICTED`·`BUSINESS_ACCOUNT_RESTRICTED`) — 사업자 회원은 소모임 쓰기가 막힌다(탈퇴·신청 취소·매니저 사퇴는 된다).
- 같은 이름 막기 범위에 INACTIVE 포함. 소모임이 쓰는 게시판은 지울 수 없다(409 `BOARD_HAS_GROUPS`).
- 나가서 가린 글은 `hidden_reason` 대신 따로 칸 `group_left_at`(운영자 숨김과 덮어쓰지 않게). 지운 글은 410 대신 로보얌대로 404.
- 소모임 신고: 2026-10-26 사용자 「신고는 당연히 소모임에도 있어야지」 — 신고 대상에 소모임을 더한다(7단계 「소모임 신고」, V95).
- 알림 문구는 서버가 만든다(로보얌 알림 응답에 원본의 `groupManager` 칸이 없다). 소모임 알림은 묶음 `system`. 푸시는 없고 메일 채널만.
- 원본의 `group_visibility_rollout`·`group_visibility_settings` 표 대신 `group_settings` 한 행.
- 용어 설정(`GROUPS` 이름 바꾸기)은 소모임 화면에 걸지 않는다 — 고정 「소모임」. **영어 이름은 "Club(s)" 로 통일**(2026-10-26 사용자 「영어이름 통일해」 — 하단 탭·서비스 이름 기본값·서버 영어 문구까지).

### 미확정 (사용자 확인 전 — 지금 동작)
1. 해산한 소모임에 공개로 남은 글에 댓글을 계속 받을지 — 받는다.
2. 비공개 소모임 글을 신고 상세에서 보는 것을 최고 관리자로 좁힐지 — 신고 처리 권한이면 본다.
3. 감사 보관 분류: 소모임·분류·설정 감사 행위는 모두 GENERAL.
4. 실행 확인 없음: 공용 로컬 DB 때문에 서버를 띄우지 않았다(develop 에 없는 V90~ 를 적용하면 develop 에서 띄우는 다른 세션이 Flyway 검증에서 막힌다). develop 에 합친 뒤 첫 기동에서 마이그레이션·번들 키 검사·JSON 모양·화면을 확인한다.
- **6단계 홈·프로필**: `GET /api/groups/trending`(가중 무작위, 관리자 운영 한도의 `trendingSize`·`trendingActiveDays`), 목록 `exclude` 최대 20, `GET /api/users/{id}/groups`(role LEADER·MANAGER·MEMBER — 원본은 LEADER·MEMBER), 최신글·인기글·통합 검색·프로필 작성글에 **비멤버에게 보이는 소모임 글**(`is_public`, 나가서 가린 글·비활성 소모임 제외 — 판정은 `PostRepositoryQDSL.feedVisible()` 한 곳)을 섞고 줄에 `group{id,name,visibility,dissolved}`. user-web `/groups` 지금 뜨는 소모임 슬라이드·프로필 소모임 탭.
  - **만들지 않은 것(붙일 곳 없음)**: 홈 인기 소모임 가젯(2026-10-26 사용자: **화면 테마 템플릿 기능을 만들 때 같이** 구현한다 — 그때 `GROUPS` 가젯이 `trending`·`?sort=popular&size=3` 을 쓴다), 탈퇴 때 모임장 차단·자격 정리(2026-10-26 사용자: 탈퇴 기능은 **지금은 두기** — 탈퇴를 만들 때 사전 검사에 `GROUP_LEADER`, 트랜잭션에서 멤버 행 leave·신청 삭제·일정 응답 삭제·공개 요청 `AUTHOR_LEFT` 취소, 원본 `GroupCleanupService.eraseUser`).
  - 프로필 댓글 수·작성 댓글에는 소모임 글 댓글을 넣지 않았다(멤버 전용·나간 멤버로 가린 댓글이 드러나서 — 원본은 넣는다).
