/**
 * 관리자 메뉴 항목 정의 — **메뉴가 정의되는 유일한 곳**이다. 사이드 메뉴(데스크톱)·상단 메뉴 줄·모바일 드로어·메뉴 검색이 모두 여기서 읽는다.
 *
 * 새 기능 화면을 만들면 `ITEMS` 에 **한 줄**을 더하고, 두 언어의 `i18n/locales/{ko,en}/menu.ts` 에 항목 이름을 더한다(`labelKey` 의 키).
 * **아직 화면이 없는 기능의 항목은 넣지 않는다**(규약: 없는 기능의 메뉴·링크는 보여 주지 않는다 — `_frontend-core.md` 2-2).
 * 메뉴별 권한: 화면 항목에 `permission`(권한 코드, `util-permission-catalog.ts`)을 달면 그 권한이 없는 운영자에게는 메뉴·검색에서 감춘다(`visible`). 칸이 없는 항목은 모두에게 보인다.
 * 지금은 운영자·역할 관리와 배치·감사 항목에만 달았다. 주소로 직접 들어오면 셸(`layouts/default.vue`)이 `canOpen` 으로 걸러 "권한 없음" 상태를 보인다. 감추는 것은 편의이고 막는 것은 서버다.
 *
 * 구조는 hamonica3 의 메뉴(`menu` 표의 최상위 + 하위)를 따른다 — 두 층까지다.
 *  · 화면 항목(`AdminMenuItem`): 주소(`to`)가 있는 갈 수 있는 항목.
 *  · 하위 항목(`children`): 화면 항목이 하위 화면 항목을 거느릴 수 있다(예: 역할 관리 › 회원 역할). 부모 항목은 눌러도 이동하지 않고 펼치고 접는다 — `to` 는 하위 주소들의 공통 앞부분(`/settings/roles`)이라
 *    하위 주소에 있으면 부모도 활성이 된다(부모 자체의 화면은 없다). 권한·모드·활성·열기 판정은 하위 항목까지 재귀로 본다(하위가 모두 숨겨지면 부모도 숨는다).
 *  · 묶음(`AdminMenuGroup`): 주소가 없고 아래에 화면 항목을 거느린다. 사이드바에서는 눌리지 않는 구획 제목이 되고(레일에서는 제목 없이 항목만),
 *    상단 메뉴 줄에서는 팝업을 여는 최상위 항목이 되며, 모바일 드로어는 사이드바와 같다.
 */
/**
 * 메뉴 모드 — 일반 메뉴(`normal`)와 설정 메뉴(`settings`). 사이드바·상단 메뉴·모바일 드로어는 지금 모드의 메뉴만 보이고, 검색은 모든 모드를 찾는다.
 * 모드는 **최상위 노드**(화면 항목 또는 묶음)에 달고, 묶음 아래 항목은 묶음의 모드를 따른다. 칸이 없으면 `normal` 이다.
 * 어느 모드를 보일지는 `composables/shell/useAppMenu.ts` 가 정한다(주소가 속한 노드의 모드 → 없으면 마지막으로 고른 모드).
 */
export type AdminMenuMode = 'normal' | 'settings';

export interface AdminMenuItem {
    /** 항목 식별자 — `v-for` 의 key */
    key: string;
    /** 최상위 노드일 때 속한 메뉴 모드. 없으면 `normal` (묶음 아래 항목에서는 읽지 않는다) */
    mode?: AdminMenuMode;
    /** 이동할 주소(화면 경로) */
    to: string;
    /**
     * primeicons 클래스 — **선택**이다. 있으면 그리고, 없으면 아이콘 자리(폭)도 만들지 않고 이름 글자만 그린다(2차 항목은 보통 있고, 하위(3차) 항목은 두지 않는다).
     * 아이콘이 없는 항목은 접힌 레일(아이콘만)에 그릴 수 없어 레일에서는 숨는다 — 그래서 하위 항목은 레일에서 그리지 않고 아이콘 있는 부모만 보인다. 이름 첫 글자 칩 같은 대체 표시는 두지 않는다.
     */
    icon?: string;
    /** 항목 이름의 i18n 키 (`menu.<key>`) */
    labelKey: string;
    /** 이 화면을 열 수 있는 권한 코드. 없으면 모두에게 보인다 */
    permission?: string;
    /** 하위 화면 항목 — 있으면 이 항목은 이동하지 않고 하위를 펼치는 부모다(`to` 는 하위 주소들의 공통 앞부분) */
    children?: readonly AdminMenuItem[];
}

export interface AdminMenuGroup {
    /** 묶음 식별자 — `v-for` 의 key */
    key: string;
    /** 속한 메뉴 모드. 없으면 `normal` */
    mode?: AdminMenuMode;
    /** primeicons 클래스 — 상단 메뉴 줄의 최상위 항목에 보인다 */
    icon: string;
    /** 묶음 이름의 i18n 키 (`menu.<key>`) */
    labelKey: string;
    /** 묶음 아래의 화면 항목 */
    items: readonly AdminMenuItem[];
}

export type AdminMenuNode = AdminMenuItem | AdminMenuGroup;

/** 사이드바·드로어가 그리는 한 덩어리 — 제목(`group`)이 없으면 제목 없는 항목들이다 */
export interface AdminMenuSection {
    key: string;
    /** 구획 제목이 되는 묶음. 낱개 화면 항목들의 덩어리면 null */
    group: AdminMenuGroup | null;
    items: readonly AdminMenuItem[];
}

export default class UtilAdminMenu {
    /**
     * 지금 있는 화면의 메뉴. 새 화면이 생길 때 한 줄씩 더한다.
     * 운영자와 권한 묶음 — hamonica3 의 `사용자와 권한`(회원 관리 · 역할 관리)에 대응한다. 이 제품에서 회원은 공개 사이트 이용자이고 관리자 앱 계정은 운영자다.
     * 배치와 로그 묶음 — hamonica3 의 시스템 설정 메뉴(`배치와 로그`: 배치 관리 · 배치 로그 · 감사 로그)와 같은 이름·차례·아이콘·주소다.
     * 모드: 대시보드·회원(회원 관리·등급 설정)은 일반 메뉴, 「일반」·운영자와 권한·배치와 로그는 설정 메뉴(`mode: 'settings'`)다. 등급 설정은 회원 운영 업무라 일반 메뉴에 남긴다(주소가 `/settings/…` 여도 모드는 메뉴 정의가 정한다).
     */
    static readonly ITEMS: readonly AdminMenuNode[] = [
        { key: 'dashboard', to: '/', icon: 'pi pi-home', labelKey: 'menu.dashboard' },
        // 회원 묶음 — 공개 사이트 이용자(회원)를 다룬다. 관리자 앱 계정(운영자)은 아래 `operatorsAndPermissions` 다.
        {
            key: 'member',
            icon: 'pi pi-users',
            labelKey: 'menu.member',
            items: [
                { key: 'members', to: '/members', icon: 'pi pi-user', labelKey: 'menu.members', permission: 'member.read' },
                { key: 'grades', to: '/grades', icon: 'pi pi-trophy', labelKey: 'menu.grades', permission: 'grade.read' },
            ],
        },
        // 게시판 묶음 — 게시판 관리(게시판 목록·게시판별 설정)와 모든 게시판에 일괄 적용되는 기본 설정. community 의 「게시판 구성」·「게시판 기본 설정」, 그누보드7 의 게시판 관리·기본 설정에 대응한다
        {
            key: 'board',
            icon: 'pi pi-list',
            labelKey: 'menu.board',
            items: [
                { key: 'boards', to: '/boards', icon: 'pi pi-th-large', labelKey: 'menu.boards', permission: 'board.read' },
                // 기본 설정은 하위 메뉴 다섯을 가진 부모다(그누보드7 게시판 기본 설정의 탭 다섯) — 역할 관리와 같은 방식. 부모 주소(`/boards/defaults`)로 들어오면 「일반」으로 보낸다
                {
                    key: 'boardDefaults',
                    to: '/boards/defaults',
                    icon: 'pi pi-sliders-v',
                    labelKey: 'menu.boardDefaults',
                    permission: 'board.read',
                    children: [
                        { key: 'boardDefaultsGeneral', to: '/boards/defaults/general', labelKey: 'menu.boardDefaultsGeneral', permission: 'board.read' },
                        { key: 'boardDefaultsReport', to: '/boards/defaults/report', labelKey: 'menu.boardDefaultsReport', permission: 'board.read' },
                        { key: 'boardDefaultsSecurity', to: '/boards/defaults/security', labelKey: 'menu.boardDefaultsSecurity', permission: 'board.read' },
                        { key: 'boardDefaultsNotifications', to: '/boards/defaults/notifications', labelKey: 'menu.boardDefaultsNotifications', permission: 'board.read' },
                        { key: 'boardDefaultsPrivacy', to: '/boards/defaults/privacy', labelKey: 'menu.boardDefaultsPrivacy', permission: 'board.read' },
                    ],
                },
            ],
        },
        // 포인트 묶음 — 전체 포인트 내역(수동 지급·차감 포함)과 지급 규칙(포인트 정책). community 의 「포인트 설정」, 그누보드7 의 포인트 관리에 대응한다
        {
            key: 'point',
            icon: 'pi pi-wallet',
            labelKey: 'menu.point',
            items: [
                { key: 'points', to: '/points', icon: 'pi pi-receipt', labelKey: 'menu.points', permission: 'point.read' },
                { key: 'pointPolicy', to: '/points/policy', icon: 'pi pi-percentage', labelKey: 'menu.pointPolicy', permission: 'point.read' },
            ],
        },
        // 설정 메뉴의 첫 묶음 — 시스템(hamonica3 의 설정 > 일반). 「일반」·「알림」(알림 이벤트 관리)·「알림 도구」·「조직 구성」이 있고 후속 설정(사이트·연동 등)이 들어올 자리다. 프로필 메뉴의 「설정」이 여기로 들어온다
        {
            key: 'settingsSystem',
            mode: 'settings',
            icon: 'pi pi-cog',
            labelKey: 'menu.system',
            items: [
                { key: 'siteGeneral', to: '/settings/general', icon: 'pi pi-sliders-h', labelKey: 'menu.general', permission: 'site.read' },
                // 조직 구성 — 운영자를 팀으로 묶는다(알림 이벤트의 「추가 수신 팀」으로 쓴다)
                { key: 'teams', to: '/settings/teams', icon: 'pi pi-sitemap', labelKey: 'menu.teams', permission: 'team.read' },
            ],
        },
        {
            key: 'settingsNotification',
            mode: 'settings',
            icon: 'pi pi-bell',
            labelKey: 'menu.notifications',
            items: [
                { key: 'notifications', to: '/settings/notifications', icon: 'pi pi-bell', labelKey: 'menu.notificationEvents', permission: 'notification.read' },
                { key: 'notificationChannels', to: '/settings/notification-channels', icon: 'pi pi-send', labelKey: 'menu.notificationChannels', permission: 'notification.read' },
            ],
        },
        {
            key: 'operatorsAndPermissions',
            mode: 'settings',
            icon: 'pi pi-users',
            labelKey: 'menu.operatorsAndPermissions',
            items: [
                { key: 'operators', to: '/settings/operators', icon: 'pi pi-user-edit', labelKey: 'menu.operators', permission: 'operator.read' },
                // 역할 관리는 하위 메뉴 셋을 가진 부모다 — 하위 주소의 마지막 조각이 화면 종류다(`pages/settings/roles/[[kind]].vue`). 부모 주소(`/settings/roles`)로 직접 들어오면 운영자 역할로 보낸다
                {
                    key: 'roles',
                    to: '/settings/roles',
                    icon: 'pi pi-id-card',
                    labelKey: 'menu.roles',
                    permission: 'role.read',
                    children: [
                        { key: 'rolesMember', to: '/settings/roles/member', labelKey: 'menu.rolesMember', permission: 'role.read' },
                        { key: 'rolesOperator', to: '/settings/roles/operator', labelKey: 'menu.rolesOperator', permission: 'role.read' },
                        { key: 'rolesGrade', to: '/settings/roles/grade', labelKey: 'menu.rolesGrade', permission: 'role.read' },
                    ],
                },
            ],
        },
        {
            key: 'batchAndLogs',
            mode: 'settings',
            icon: 'pi pi-history',
            labelKey: 'menu.batchAndLogs',
            items: [
                { key: 'batches', to: '/settings/batches', icon: 'pi pi-clock', labelKey: 'menu.batches', permission: 'batch.read' },
                { key: 'batchLogs', to: '/settings/batch-logs', icon: 'pi pi-book', labelKey: 'menu.batchLogs', permission: 'batch.read' },
                { key: 'audit', to: '/settings/audit', icon: 'pi pi-history', labelKey: 'menu.audit', permission: 'audit.read' },
            ],
        },
    ];

    /**
     * 권한으로 걸러 낸 메뉴 — 권한이 없는 화면 항목은 빼고, 항목이 하나도 안 남은 묶음도 뺀다. 사이드바·상단 메뉴·드로어·검색이 이것을 읽는다.
     * 역할 관리의 "이 역할이 보는 메뉴" 미리보기도 같은 함수에 그 역할의 권한을 넣어 계산한다.
     * @param {(code: string) => boolean} can - 그 권한 코드를 가졌는가.
     * @param {AdminMenuMode} [mode] - 이 모드의 노드만. 안 주면 모든 모드(검색·역할 미리보기).
     * @returns {AdminMenuNode[]} 보이는 메뉴 노드(원본 순서).
     */
    static visible(can: (code: string) => boolean, mode?: AdminMenuMode): AdminMenuNode[] {
        const out: AdminMenuNode[] = [];
        for (const node of UtilAdminMenu.ITEMS) {
            if (mode && UtilAdminMenu.modeOf(node) !== mode) continue;
            if (UtilAdminMenu.isGroup(node)) {
                const items = UtilAdminMenu.visibleItems(node.items, can);
                if (items.length) out.push({ ...node, items });
            } else {
                const [item] = UtilAdminMenu.visibleItems([node], can);
                if (item) out.push(item);
            }
        }
        return out;
    }

    /**
     * 화면 항목들을 권한으로 거른다 — 하위 항목은 재귀로 거르고, 하위가 있던 부모는 하위가 모두 숨겨지면 함께 숨긴다.
     * @param {readonly AdminMenuItem[]} items - 거를 항목들.
     * @param {(code: string) => boolean} can - 그 권한 코드를 가졌는가.
     * @returns {AdminMenuItem[]} 보이는 항목(원본 순서, 하위도 걸러진 새 객체).
     */
    static visibleItems(items: readonly AdminMenuItem[], can: (code: string) => boolean): AdminMenuItem[] {
        const out: AdminMenuItem[] = [];
        for (const item of items) {
            if (item.permission && !can(item.permission)) continue;
            if (item.children) {
                const children = UtilAdminMenu.visibleItems(item.children, can);
                if (children.length) out.push({ ...item, children });
            } else {
                out.push(item);
            }
        }
        return out;
    }

    /**
     * 하위까지 펼친 **화면 항목(잎)** 들 — 이동할 수 있는 항목만. 부모(`children`)는 빠지고 그 하위가 들어간다.
     * @param {readonly AdminMenuItem[]} items - 항목들.
     * @returns {AdminMenuItem[]} 잎 항목.
     */
    static leaves(items: readonly AdminMenuItem[]): AdminMenuItem[] {
        return items.flatMap((item) => (item.children ? UtilAdminMenu.leaves(item.children) : [item]));
    }

    /**
     * 노드의 메뉴 모드 — 칸이 없으면 일반 메뉴.
     * @param {AdminMenuNode} node - 최상위 메뉴 노드.
     * @returns {AdminMenuMode} 속한 모드.
     */
    static modeOf(node: AdminMenuNode): AdminMenuMode {
        return node.mode ?? 'normal';
    }

    /**
     * 이 주소가 속한 메뉴 노드의 모드 — 메뉴 항목(하위 주소 포함)이 아닌 주소(내 정보 등)는 null. 권한과 무관하다(주소가 어느 메뉴의 것인지만 본다).
     * @param {string} path - 주소의 경로(`route.path`).
     * @returns {AdminMenuMode | null} 속한 모드. 어느 메뉴에도 없으면 null.
     */
    static modeOfPath(path: string): AdminMenuMode | null {
        for (const node of UtilAdminMenu.ITEMS) {
            const items = UtilAdminMenu.leaves(UtilAdminMenu.isGroup(node) ? node.items : [node]);
            if (items.some((item) => UtilAdminMenu.isActive(item, path))) return UtilAdminMenu.modeOf(node);
        }
        return null;
    }

    /**
     * 메뉴에서 맨 처음 갈 수 있는 화면 항목의 주소 — 설정 메뉴로 들어갈 때 첫 화면을 고른다.
     * @param {readonly AdminMenuNode[]} nodes - 권한·모드로 거른 메뉴(`visible` 의 결과).
     * @returns {string | null} 첫 화면 항목의 주소. 항목이 없으면 null.
     */
    static firstTo(nodes: readonly AdminMenuNode[]): string | null {
        const first = nodes[0];
        if (!first) return null;
        return UtilAdminMenu.leaves(UtilAdminMenu.isGroup(first) ? first.items : [first])[0]?.to ?? null;
    }

    /**
     * 이 주소의 화면을 열 권한이 있는가 — 메뉴 항목에 걸린 권한을 본다. 메뉴에 없는 주소(내 정보 등)·권한이 안 걸린 항목은 열 수 있다.
     * @param {string} path - 주소의 경로(`route.path`).
     * @param {(code: string) => boolean} can - 그 권한 코드를 가졌는가.
     * @returns {boolean} 열 수 있으면 true.
     */
    static canOpen(path: string, can: (code: string) => boolean): boolean {
        for (const node of UtilAdminMenu.ITEMS) {
            const items = UtilAdminMenu.leaves(UtilAdminMenu.isGroup(node) ? node.items : [node]);
            const hit = items.find((item) => UtilAdminMenu.isActive(item, path));
            if (hit) return !hit.permission || can(hit.permission);
        }
        return true;
    }

    /**
     * 노드가 묶음인가.
     * @param {AdminMenuNode} node - 메뉴 노드.
     * @returns {boolean} 묶음이면 true.
     */
    static isGroup(node: AdminMenuNode): node is AdminMenuGroup {
        return 'items' in node;
    }

    /**
     * 사이드바·드로어가 그릴 덩어리로 나눈다 — 이어진 낱개 화면 항목은 한 덩어리(제목 없음), 묶음은 제목 있는 한 덩어리다.
     * @param {readonly AdminMenuNode[]} nodes - 나눌 메뉴(기본은 전체, 권한으로 거른 것은 `visible` 의 결과를 넣는다).
     * @returns {AdminMenuSection[]} 위에서 아래 차례의 덩어리.
     */
    static sections(nodes: readonly AdminMenuNode[] = UtilAdminMenu.ITEMS): AdminMenuSection[] {
        const out: AdminMenuSection[] = [];
        for (const node of nodes) {
            if (UtilAdminMenu.isGroup(node)) {
                out.push({ key: node.key, group: node, items: node.items });
                continue;
            }
            const last = out[out.length - 1];
            if (last && !last.group) (last.items as AdminMenuItem[]).push(node);
            else out.push({ key: node.key, group: null, items: [node] });
        }
        return out;
    }

    /**
     * 지금 주소가 이 항목의 화면인가 — 홈(`/`)은 정확히 같을 때만, 나머지는 하위 주소(`/members/12`)까지 같은 항목으로 본다.
     * @param {AdminMenuItem} item - 메뉴 항목.
     * @param {string} path - 지금 주소의 경로(`route.path`).
     * @returns {boolean} 그 항목이 활성이면 true.
     */
    static isActive(item: AdminMenuItem, path: string): boolean {
        if (item.to === '/') return path === '/';
        return path === item.to || path.startsWith(`${item.to}/`);
    }

    /**
     * 묶음 아래 항목 중 하나라도 활성인가 — 상단 메뉴 줄의 최상위 항목 표시에 쓴다.
     * @param {AdminMenuGroup} group - 메뉴 묶음.
     * @param {string} path - 지금 주소의 경로(`route.path`).
     * @returns {boolean} 하나라도 활성이면 true.
     */
    static isGroupActive(group: AdminMenuGroup, path: string): boolean {
        return UtilAdminMenu.leaves(group.items).some((item) => UtilAdminMenu.isActive(item, path));
    }

    /**
     * 이 항목이나 그 하위 중 하나라도 활성인가 — 하위가 있는 부모의 활성·펼침 판정에 쓴다.
     * @param {AdminMenuItem} item - 메뉴 항목.
     * @param {string} path - 지금 주소의 경로(`route.path`).
     * @returns {boolean} 하나라도 활성이면 true.
     */
    static isItemActive(item: AdminMenuItem, path: string): boolean {
        return UtilAdminMenu.leaves([item]).some((leaf) => UtilAdminMenu.isActive(leaf, path));
    }
}
