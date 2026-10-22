import {
    mockAdminMenu,
    mockAdminMenuSave,
    mockMenuSetCreate,
    mockMenuSetDelete,
    mockMenuSets,
    mockMenuSetUpdate,
    mockMenuTargetOptions,
    mockMenuTree,
    mockMenuTreeSave,
} from '~/composables/menu/api/mock/menu-mock';
import type {
    AdminMenuConfigResponse,
    AdminMenuConfigSaveRequest,
    MenuSet,
    MenuSetCreateRequest,
    MenuSetUpdateRequest,
    MenuTargetOptions,
    MenuTreeResponse,
    MenuTreeSaveRequest,
} from '~/composables/menu/api/useMenuApi.types';

/**
 * 메뉴 관리 API — 서버 주소 문자열(`/api/admin/menu-sets…`·`/api/admin/menu/…`·`/api/admin/admin-menu`)이 있는 **유일한 곳**이다. 상태는 없다.
 * community `docs/api-menu.md`(공개 메뉴 트리 하나)를 roboyam 의 **메뉴 그룹(메뉴 세트) 여럿**으로 옮기고, 관리자 사이트 메뉴 구성을 더했다.
 *
 * 후보 계약(확정 전). 오류는 모두 `ApiError`(useApiClient)로 던져진다. 쓰기는 CSRF. 권한: 조회 `layout.read`, 공개 메뉴 저장 `layout.settings`, 관리자 메뉴 저장 `admin-menu.manage`.
 *   공개 사이트 메뉴 — 메뉴 그룹
 *   - `GET    /api/admin/menu-sets` → 200 `MenuSet[]` (만든 차례. `layout.read`)
 *   - `POST   /api/admin/menu-sets` `MenuSetCreateRequest` → 201 `MenuSet` (key `^[a-z][a-z0-9-]*$` 2~30자 · name 1~20자 · description 100자. 400 `VALIDATION` `fields.key|name|nameEn|placement|description` · 409 `MENU_SET_KEY_DUPLICATE`. `layout.settings`)
 *   - `PUT    /api/admin/menu-sets/{key}` `MenuSetUpdateRequest` → 200 `MenuSet` (key 는 바꿀 수 없다. 기본 그룹(`system`)은 위치도 바꿀 수 없다. `layout.settings`)
 *   - `DELETE /api/admin/menu-sets/{key}` → 204 (트리도 함께 지운다. 기본 그룹 409 `MENU_SET_SYSTEM`. `layout.settings`)
 *   공개 사이트 메뉴 — 트리(그룹마다)
 *   - `GET    /api/admin/menu-sets/{key}/tree` → 200 `MenuTreeResponse` `{ set, nodes, version, editable }` (`layout.read`)
 *   - `PUT    /api/admin/menu-sets/{key}/tree` `{ version, nodes }` → 200 `MenuTreeResponse` (**트리 전체 교체** — 요청에 없는 기존 노드는 지운다, `id: null` 은 새 노드.
 *              버전이 다르면 409 `MENU_VERSION_CONFLICT`. 틀린 칸은 400 `VALIDATION` + `fields["nodes[1].children[2].label"]` 꼴(트리 전체 문제는 `fields.nodes`).
 *              규칙: 폴더 깊이 ≤ 3 · 항목 깊이 ≤ 4 · 노드 ≤ 80 · 모바일 탭 ≤ 10(SIDE 그룹만) · 이름 1~20자(`<`·`>`·줄바꿈 금지, 게시판·앱 화면 항목은 비우면 대상 이름) ·
 *              한 그룹 안에서 한 게시판·한 앱 화면은 한 번 · 로그인 필요 앱 화면은 보이는 대상 MEMBER·GRADE_MIN·STAFF · 자동 추가 폴더 하나(SIDE 그룹만) ·
 *              PATH/URL 주소 규칙(`UtilMenu.pathProblem`·`hrefProblem`) · 모르는 유형은 받은 그대로 보존. `layout.settings`. 감사 기록 후보 `MENU_TREE_UPDATE`)
 *   - `POST   /api/admin/menu-sets/{key}/preview` `{ nodes, viewer: { kind: 'GUEST'|'MEMBER'|'STAFF', gradeKey? } }` → 200 `{ nodes, mobileTabs }` (저장 전 트리를 공개 응답 모양으로 걸러 본다.
 *              **지금 화면은 같은 규칙의 순수 함수 `UtilMenu.preview` 로 계산한다** — 서버 판정과 어긋나는지 확인하는 용도의 후보)
 *   - `GET    /api/admin/menu/target-types` → 200 `[{ type, label, picker: 'BOARD_SELECT'|'BUILTIN_SELECT'|'PATH_INPUT'|'URL_INPUT', ready }]` (지금은 화면 상수 `UtilMenu.TARGET_TYPES`·`SOON_TYPES`)
 *   - `GET    /api/admin/menu/target-options?set=&type=&q=` → 200 `MenuTargetOptions` `{ boards, grades, salesEnabled }` (대상 고르기·상태·미리보기용. `type`·`q` 를 주면 그 유형만 찾는다 — 지금은 한 번에 받는다)
 *   관리자 사이트 메뉴 — **두 모드(일반·설정)를 한 번에** 주고받는다(한 화면은 두 모드 통틀어 한 번이라 한 트랜잭션으로 검사한다)
 *   - `GET    /api/admin/admin-menu` → 200 `AdminMenuConfigResponse` `{ modes: { normal, settings }, version, editable }` (`layout.read`)
 *   - `PUT    /api/admin/admin-menu` `{ version, modes }` → 200 `AdminMenuConfigResponse` (전체 교체. 409 `MENU_VERSION_CONFLICT` · 400 `VALIDATION` `fields["modes.normal[0].children[1].label"]`.
 *              화면 항목의 권한은 서버의 화면 레지스트리가 정하고 요청으로 바꿀 수 없다. `admin-menu.manage`. 감사 기록 후보 `ADMIN_MENU_UPDATE`)
 *   공개 사이트(user-web, 읽기만)
 *   - `GET    /api/menu?set=main` → 200 user-web `MenuResponse` (기존 모양 그대로, `set` 기본값 `main`. 꺼진 그룹은 빈 `nodes`)
 *   공통 오류: 403 `PERMISSION_DENIED` · 404 `MENU_SET_NOT_FOUND`.
 *
 * ⚠ MOCK: 지금은 서버가 없어서 `./mock/menu-mock.ts` 를 부른다. 서버가 붙으면 `// MOCK:` 줄(과 `can`·목업 import)을 지우고 `// REAL:` 줄을 되살린다.
 */
const PATH = {
    sets: '/api/admin/menu-sets',
    set: (key: string) => `${PATH.sets}/${encodeURIComponent(key)}`,
    tree: (key: string) => `${PATH.set(key)}/tree`,
    targetOptions: '/api/admin/menu/target-options',
    adminMenu: '/api/admin/admin-menu',
};

export function useMenuApi() {
    // REAL: const auth = useAdminAuthApi();
    // REAL: const client = useApiClient({ refreshCsrf: () => auth.me() });
    // MOCK: 목업이 서버처럼 내 권한(`layout.read`·`layout.settings`·`admin-menu.manage`)으로 403 을 낸다
    const { can } = useAdminPermission();
    void PATH;

    return {
        /** 메뉴 그룹 목록 */
        sets: (): Promise<MenuSet[]> =>
            // MOCK:
            mockMenuSets(can),
        // REAL: client.get<MenuSet[]>(PATH.sets),

        /** 메뉴 그룹 만들기 */
        createSet: (body: MenuSetCreateRequest): Promise<MenuSet> =>
            // MOCK:
            mockMenuSetCreate(can, body),
        // REAL: client.post<MenuSet>(PATH.sets, body),

        /** 메뉴 그룹 정보 바꾸기(key 제외) */
        updateSet: (key: string, body: MenuSetUpdateRequest): Promise<MenuSet> =>
            // MOCK:
            mockMenuSetUpdate(can, key, body),
        // REAL: client.put<MenuSet>(PATH.set(key), body),

        /** 메뉴 그룹 지우기(트리 포함) */
        deleteSet: (key: string): Promise<void> =>
            // MOCK:
            mockMenuSetDelete(can, key),
        // REAL: client.del<void>(PATH.set(key)),

        /** 그룹의 트리 + 버전 */
        tree: (key: string): Promise<MenuTreeResponse> =>
            // MOCK:
            mockMenuTree(can, key),
        // REAL: client.get<MenuTreeResponse>(PATH.tree(key)),

        /** 그룹의 트리를 통째로 바꾼다 */
        saveTree: (key: string, body: MenuTreeSaveRequest): Promise<MenuTreeResponse> =>
            // MOCK:
            mockMenuTreeSave(can, key, body),
        // REAL: client.put<MenuTreeResponse>(PATH.tree(key), body),

        /** 대상 고르기·상태·미리보기에 쓰는 게시판·등급·판매 스위치 */
        targetOptions: (key: string): Promise<MenuTargetOptions> =>
            // MOCK:
            mockMenuTargetOptions(can, key),
        // REAL: client.get<MenuTargetOptions>(PATH.targetOptions, { query: { set: key } }),

        /** 관리자 사이트 메뉴(두 모드) */
        adminMenu: (): Promise<AdminMenuConfigResponse> =>
            // MOCK:
            mockAdminMenu(can),
        // REAL: client.get<AdminMenuConfigResponse>(PATH.adminMenu),

        /** 관리자 사이트 메뉴를 통째로 바꾼다 */
        saveAdminMenu: (body: AdminMenuConfigSaveRequest): Promise<AdminMenuConfigResponse> =>
            // MOCK:
            mockAdminMenuSave(can, body),
        // REAL: client.put<AdminMenuConfigResponse>(PATH.adminMenu, body),
    };
}
