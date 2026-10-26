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
