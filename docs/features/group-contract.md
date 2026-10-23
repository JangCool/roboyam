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
- 소모임 설정의 「소모임의 이름」 구획(용어 설정 `GROUPS` 없음) · 분류 목록의 끌어 놓기·Alt+↑↓(▲▼ 만) · 분류 화면의 [소모임 게시판 만들기] 대화상자(게시판 관리 링크로 대신 — 로보얌 게시판 만들기는 종류·분류를 받지 않는다) · 상세 서랍의 「공개 화면 보기」·「회원 조회 ›」·「신고 보기 ›」 링크(관리자 앱이 공개 주소를 모르고, 회원 관리에 주소로 여는 서랍·신고 처리 화면이 없다) · 좁은 화면 카드 목록(가로 스크롤 표).

### 미확정
1. 분류 화면에서 소모임 게시판을 바로 만들지(게시판 만들기에 종류 `GROUP`·`groupCategoryId` 를 받을지).
2. 등급 고르기(`limits.grades`) — 목업은 community 네 등급. 서버는 등급 설정의 등급 키를 줄 것으로 가정.
3. 설정 변경 이력 API(로보얌 추가)의 모양·전후 값 표시 방식.
4. 메뉴·권한의 서버 쪽(`AdminScreenRegistry` 네 줄, `Permission` 의 `group.*` `featureReady=true`)은 서버 단계.
