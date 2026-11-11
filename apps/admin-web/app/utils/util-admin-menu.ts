import type { AdminMenuConfigNodeDto } from '~/composables/menu/api/useMenuApi.types';

/**
 * 관리자 메뉴 항목 정의 — 관리자 셸의 메뉴 정의다. 사이드 메뉴(데스크톱)·상단 메뉴 줄·모바일 드로어·메뉴 검색이 모두 여기서 읽는다.
 *
 * **새 화면 = 여기 `ITEMS` 한 줄 + 두 언어의 `i18n/locales/{ko,en}/menu.ts` 항목 이름(`labelKey` 의 키) + 서버 `AdminScreenRegistry`(server/admin `menu/service`) 한 줄.**
 * 서버 레지스트리는 관리자 메뉴 구성(메뉴 관리 › 관리자 메뉴)이 쓰는 화면 키 → 주소·권한 표이자 저장한 적 없을 때의 기본 구성이라, 두 곳의 키·주소·권한·아이콘·차례를 같게 유지한다.
 * **셸이 그리는 메뉴는 DB 의 관리자 메뉴 구성이다**(메뉴 관리 › 관리자 메뉴 — `GET /api/admin/admin-menu/shell`, 저장한 적 없으면 서버가 레지스트리 기본 구성을 준다 · 2026-11-07).
 *   `fromConfig` 가 그 구성을 이 파일의 노드 모양으로 바꾸고 `useAdminMenuTree` 가 들고 있다. 이 `ITEMS` 는 **화면 레지스트리**(키 → 주소·권한·아이콘 — 주소 권한 판정 `canOpen` ·
 *   더 구체적인 항목 판정 `isActive` · 메뉴 편집의 화면 목록)이자 서버 구성을 못 받았을 때의 대신 메뉴다. 메뉴에 없는 화면(편집 화면의 「메뉴에 없는 화면」)은 셸에도 없다.
 * **아직 화면이 없는 기능의 항목은 넣지 않는다**(규약: 없는 기능의 메뉴·링크는 보여 주지 않는다 — `_frontend-core.md` 2-2).
 * 메뉴별 권한: 화면 항목에 `permission`(권한 코드, `util-permission-catalog.ts`)을 달면 그 권한이 없는 운영자에게는 메뉴·검색에서 감춘다(`visible`). 칸이 없는 항목(대시보드)은 모두에게 보인다.
 * 주소로 직접 들어오면 셸(`layouts/default.vue`)이 `canOpen` 으로 걸러 "권한 없음" 상태를 보인다. 감추는 것은 편의이고 막는 것은 서버다.
 * 최고 관리자 전용(권한 코드로 위임하지 않는 화면 — 로그인 보안): 항목에 `superOnly: true` 를 단다. 판정은 같은 `can` 에 예약 코드 `SUPER_ONLY` 를 물어서 한다 —
 * 로그인한 운영자의 `can`(`useAdminPermission`)은 최고 관리자에게만 모든 코드에 true 이고, 역할 미리보기의 `can`(그 역할이 가진 코드)에는 이 코드가 없어 늘 감춰진다.
 * 권한 카탈로그의 `featureReady`(기능 준비 중 표시)는 메뉴 감춤에 쓰지 않는다 — 감춤은 `permission` 보유만 본다.
 *
 * 구조는 hamonica3 의 메뉴(`menu` 표의 최상위 + 하위)를 따른다 — 두 층까지다.
 *  · 화면 항목(`AdminMenuItem`): 주소(`to`)가 있는 갈 수 있는 항목.
 *  · 하위 항목(`children`): 화면 항목이 하위 화면 항목을 거느릴 수 있다(예: 역할 관리 › 회원 역할). 부모 항목은 눌러도 이동하지 않고 펼치고 접는다 — `to` 는 하위 주소들의 공통 앞부분(`/settings/roles`)이라
 *    하위 주소에 있으면 부모도 활성이 된다(부모 자체의 화면은 없다). 권한·모드·활성·열기 판정은 하위 항목까지 재귀로 본다(하위가 모두 숨겨지면 부모도 숨는다).
 *    어느 하위에도 속하지 않는 부모 아래 주소(하위 둘이 같이 쓰는 편집 화면 — 문구 편집 `/settings/notification-messages/{id}`)는 부모만 활성이고, 권한·모드도 부모의 것으로 본다.
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
    /** 최고 관리자(루트)만 — 권한 코드로 위임하지 않는 화면. `permission` 과 함께 쓰지 않는다 */
    superOnly?: true;
    /** 하위 화면 항목 — 있으면 이 항목은 이동하지 않고 하위를 펼치는 부모다(`to` 는 하위 주소들의 공통 앞부분) */
    children?: readonly AdminMenuItem[];
    /** 운영자가 메뉴 구성에서 덮어쓴 이름(한국어·영문) — 있으면 `labelKey` 대신 그린다(`useAdminMenuName`). 운영자가 만든 부모·외부 링크는 이것만 있고 `labelKey` 는 빈 글자다 */
    label?: string;
    labelEn?: string;
    /** 외부 링크(메뉴 구성의 `URL` 항목) — 있으면 `to` 대신 이 주소로 `<a>` 를 연다. 활성·주소 권한 판정에는 끼지 않는다 */
    href?: string;
    /** 외부 링크를 새 탭으로 */
    newTab?: boolean;
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
    /** 운영자가 덮어쓴 이름 — 화면 항목과 같다. 운영자가 만든 묶음은 이것만 있다 */
    label?: string;
    labelEn?: string;
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
    /** 최고 관리자 전용 항목(`superOnly`)을 물을 때 쓰는 예약 코드 — 권한 카탈로그에 없는 값이라 최고 관리자의 `can` 만 true 를 준다 */
    static readonly SUPER_ONLY = '$super';

    /**
     * 이 항목을 열 수 있는가 — 최고 관리자 전용이면 `SUPER_ONLY`, 권한이 걸렸으면 그 권한을 본다.
     * @param {AdminMenuItem} item - 메뉴 항목.
     * @param {(code: string) => boolean} can - 그 권한 코드를 가졌는가.
     * @returns {boolean} 열 수 있으면 true.
     */
    static allowed(item: AdminMenuItem, can: (code: string) => boolean): boolean {
        if (item.superOnly && !can(UtilAdminMenu.SUPER_ONLY)) return false;
        return !item.permission || can(item.permission);
    }

    /**
     * 지금 있는 화면의 메뉴. 새 화면이 생길 때 한 줄씩 더한다(서버 `AdminScreenRegistry` 에도 같은 한 줄 — 파일 머리말).
     * 운영자와 권한 묶음 — hamonica3 의 `사용자와 권한`(회원 관리 · 역할 관리)에 대응한다. 이 제품에서 회원은 공개 사이트 이용자이고 관리자 앱 계정은 운영자다.
     * 배치와 로그 묶음 — hamonica3 의 시스템 설정 메뉴(`배치와 로그`: 배치 관리 · 배치 로그 · 감사 로그)와 같은 이름·차례·아이콘·주소다.
     * 모드: 대시보드·회원(회원 관리·등급 설정)·메뉴 관리·게시판·포인트·포인트 상점·소모임은 일반 메뉴, 「일반」·알림·운영자와 권한·배치와 로그는 설정 메뉴(`mode: 'settings'`)다. 등급 설정은 회원 운영 업무라 일반 메뉴에 남긴다(주소가 `/settings/…` 여도 모드는 메뉴 정의가 정한다).
     * 포인트 묶음(포인트 내역·포인트 정책)은 실제 서버(`usePointApi`)를 부른다 — 권한(`point.read`)이 있는 운영자에게 보인다.
     */
    static readonly ITEMS: readonly AdminMenuNode[] = [
        { key: 'dashboard', to: '/', icon: 'pi pi-home', labelKey: 'menu.dashboard' },
        // 방문자 통계 묶음(site-analytics-contract — 사용자 결정 2026-11-04, 그누보드 「방문자 통계」처럼 여러 쪽) — 모두 `site.read`. 수집 설정(`/settings/analytics`)은 설정 메뉴에 그대로 둔다.
        // 서버 `AdminScreenRegistry` 와 같은 키·주소·권한·아이콘·차례다(바꿀 때 함께).
        {
            key: 'visitStats',
            icon: 'pi pi-chart-line',
            labelKey: 'menu.visitStats',
            items: [
                { key: 'visitOverview', to: '/analytics/visits', icon: 'pi pi-chart-line', labelKey: 'menu.visitOverview', permission: 'site.read' },
                // 3단계 월별 보기(사용자 결정 2026-11-11 — 개요 바로 아래)
                { key: 'visitMonthly', to: '/analytics/visits/monthly', icon: 'pi pi-calendar', labelKey: 'menu.visitMonthly', permission: 'site.read' },
                { key: 'visitPages', to: '/analytics/visits/pages', icon: 'pi pi-file', labelKey: 'menu.visitPages', permission: 'site.read' },
                { key: 'visitReferrers', to: '/analytics/visits/referrers', icon: 'pi pi-directions', labelKey: 'menu.visitReferrers', permission: 'site.read' },
                { key: 'visitFlows', to: '/analytics/visits/flows', icon: 'pi pi-sitemap', labelKey: 'menu.visitFlows', permission: 'site.read' },
                { key: 'visitTime', to: '/analytics/visits/time', icon: 'pi pi-calendar-clock', labelKey: 'menu.visitTime', permission: 'site.read' },
                { key: 'visitRealtime', to: '/analytics/visits/realtime', icon: 'pi pi-bolt', labelKey: 'menu.visitRealtime', permission: 'site.read' },
                // 2단계(사용자 결정 2026-11-04)
                { key: 'visitEnvironment', to: '/analytics/visits/environment', icon: 'pi pi-mobile', labelKey: 'menu.visitEnvironment', permission: 'site.read' },
                { key: 'visitSearch', to: '/analytics/visits/search', icon: 'pi pi-search', labelKey: 'menu.visitSearch', permission: 'site.read' },
                { key: 'visitBots', to: '/analytics/visits/bots', icon: 'pi pi-microchip-ai', labelKey: 'menu.visitBots', permission: 'site.read' },
                { key: 'visitHealth', to: '/analytics/visits/health', icon: 'pi pi-wave-pulse', labelKey: 'menu.visitHealth', permission: 'site.read' },
                { key: 'visitRequests', to: '/analytics/visits/requests', icon: 'pi pi-eraser', labelKey: 'menu.visitRequests', permission: 'site.read' },
            ],
        },
        // 회원 묶음 — 공개 사이트 이용자(회원)를 다룬다. 관리자 앱 계정(운영자)은 아래 `operatorsAndPermissions` 다.
        {
            key: 'member',
            icon: 'pi pi-users',
            labelKey: 'menu.member',
            items: [
                { key: 'members', to: '/members', icon: 'pi pi-user', labelKey: 'menu.members', permission: 'member.read' },
                // 탈퇴 관리 — 주소가 `/members` 아래지만 다른 화면이다(member-withdrawal-contract 「관리자」). 서버 `AdminScreenRegistry` 와 같은 키·주소·권한
                { key: 'memberWithdrawals', to: '/members/withdrawals', icon: 'pi pi-user-minus', labelKey: 'menu.memberWithdrawals', permission: 'member.read' },
                { key: 'grades', to: '/grades', icon: 'pi pi-trophy', labelKey: 'menu.grades', permission: 'grade.read' },
                // 닉네임 이미지 심사 — 주소가 `/members` 아래지만 회원 관리와 다른 화면·권한이다(`isActive` 가 더 구체적인 항목에 주소를 준다)
                { key: 'nicknameImages', to: '/members/nickname-images', icon: 'pi pi-image', labelKey: 'menu.nicknameImages', permission: 'nickname-image.read' },
                // 아바타 올리기 심사(서버 연결됨 — commerce-motion-avatar-contract) — 권한은 닉네임 이미지 심사와 같다. 서버 `AdminScreenRegistry.avatarUploads` 와 같은 줄
                { key: 'avatarUploads', to: '/members/avatar-uploads', icon: 'pi pi-upload', labelKey: 'menu.avatarUploads', permission: 'nickname-image.read' },
            ],
        },
        // 메뉴 관리 묶음 — 공개 사이트 메뉴(메뉴 그룹별 트리)와 관리자 사이트 메뉴 구성. community 의 「메뉴 구성」에 대응한다. 부모 주소(`/menus`)로 들어오면 공개 사이트 메뉴로 보낸다
        {
            key: 'siteMenu',
            icon: 'pi pi-bars',
            labelKey: 'menu.siteMenu',
            items: [
                { key: 'menusPublic', to: '/menus/public', icon: 'pi pi-globe', labelKey: 'menu.menusPublic', permission: 'layout.read' },
                { key: 'menusAdmin', to: '/menus/admin', icon: 'pi pi-desktop', labelKey: 'menu.menusAdmin', permission: 'layout.read' },
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
        // 커뮤니티 안전 묶음(콘텐츠 묶음 앞) — 신고 관리: 글·댓글 신고를 대상별 사건으로 보고 조치·무시(docs/features/report-admin-contract.md). 서버 `AdminScreenRegistry` 와 같은 키·주소·권한
        // 제재·이의제기(docs/features/member-sanction-contract.md): 둘 다 보기 `sanction.read`(처리는 화면 안에서 `sanction.manage`·`sanction.ban`·`appeal.manage`). 서버 `AdminScreenRegistry` 와 같은 키·주소·권한·아이콘·차례
        // 게시판 제재(docs/features/board-sanction-contract.md): 보기 `sanction.read`(해제·무효·반복 검토 닫기는 화면 안에서 `sanction.manage`). ⚠ 서버 `AdminScreenRegistry` 에 같은 키·주소·권한·아이콘·차례로 한 줄 더해야 한다(아직 없음)
        // 스텝 처리 감독(docs/features/board-staff-action-contract.md): 보기 `keeper-action.read`(되돌리기는 화면 안에서 `keeper-action.restore`). 서버 `AdminScreenRegistry` 의 `staffActions`(게시판 제재 다음)와 같은 키·주소·권한·아이콘·차례
        {
            key: 'safety',
            icon: 'pi pi-shield',
            labelKey: 'menu.safety',
            items: [
                { key: 'reports', to: '/reports', icon: 'pi pi-flag', labelKey: 'menu.reports', permission: 'report.read' },
                { key: 'sanctions', to: '/sanctions', icon: 'pi pi-ban', labelKey: 'menu.sanctions', permission: 'sanction.read' },
                { key: 'boardSanctions', to: '/board-sanctions', icon: 'pi pi-hammer', labelKey: 'menu.boardSanctions', permission: 'sanction.read' },
                { key: 'staffActions', to: '/staff-actions', icon: 'pi pi-eye', labelKey: 'menu.staffActions', permission: 'keeper-action.read' },
                { key: 'appeals', to: '/appeals', icon: 'pi pi-comments', labelKey: 'menu.appeals', permission: 'sanction.read' },
            ],
        },
        // 콘텐츠 묶음 — 게시글 관리(맨 앞)·하이라이트(토론 의견 관리 포함)·인기글 관리(빼기·다시 넣기)·인기글 설정(점수식). 서버 `AdminScreenRegistry` 의 `menu.content` 와 같은 키·주소·권한·차례(docs/features/popular-highlight-contract.md).
        // `/content/popular/settings` 는 `/content/popular` 아래지만 다른 화면이다(`isActive` 가 더 구체적인 항목에 주소를 준다)
        {
            key: 'content',
            icon: 'pi pi-folder-open',
            labelKey: 'menu.content',
            items: [
                // 게시글 관리 — 모든 게시판의 글을 찾아 숨기기·삭제·복구·이동·공지·고정(docs/features/post-admin-contract.md). 서버 `AdminScreenRegistry` 와 같은 키·주소·권한
                { key: 'posts', to: '/posts', icon: 'pi pi-file-edit', labelKey: 'menu.posts', permission: 'post.read' },
                // 공지 관리 — 공지 게시판(slug `notice`) 글 쓰기·고치기·지우기·고정(docs/features/notice-admin-contract.md). 쓰기는 화면 안에서 `post.manage`.
                // 서버 `AdminScreenRegistry` 와 같은 키·주소·권한. 관리자 메뉴를 이미 저장한 사이트는 메뉴 관리 › 「메뉴에 없는 화면」에서 넣는다.
                { key: 'notices', to: '/notices', icon: 'pi pi-megaphone', labelKey: 'menu.notices', permission: 'post.read' },
                { key: 'highlights', to: '/content/highlights', icon: 'pi pi-sparkles', labelKey: 'menu.highlights', permission: 'highlight.read' },
                { key: 'popularPosts', to: '/content/popular', icon: 'pi pi-thumbs-up', labelKey: 'menu.popularPosts', permission: 'popular.read' },
                { key: 'popularSettings', to: '/content/popular/settings', icon: 'pi pi-sliders-h', labelKey: 'menu.popularSettings', permission: 'popular.read' },
                // 팝업 관리 — 공개 사이트에 들어오면 가운데 뜨는 이벤트·프로모션 창(docs/features/site-popup-contract.md). 서버 `AdminScreenRegistry` 와 같은 키·주소·권한·아이콘
                { key: 'sitePopups', to: '/content/popups', icon: 'pi pi-window-maximize', labelKey: 'menu.popups', permission: 'site.read' },
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
        // 포인트 상점 묶음(포인트 묶음 바로 다음) — 포인트로 사는 디지털 아이템의 품목 관리 · 구매 내역(예외 환불) · 포인트 상점 설정(환불 기한). commerce.md 7절, 계약 commerce-stage1 「1-2 화면 목업」.
        // 「상점」이라고만 부르지 않고(포인트 상점), 공동구매(현금으로 사는 실물)와 같은 묶음에 넣지 않는다(사용자 2026-10-26).
        // 서버 `AdminScreenRegistry` 에도 같은 세 줄이 있다. 세 화면 모두 조회 `pointshop.read`, 화면 안에서 저장 `pointshop.manage` · 예외 환불 `pointshop.refund`(0-5 의 `shop.*` 를 1-2 에서 바꿨다).
        {
            key: 'pointShop',
            icon: 'pi pi-shopping-bag',
            labelKey: 'menu.pointShop',
            items: [
                { key: 'pointShopItems', to: '/point-shop/items', icon: 'pi pi-box', labelKey: 'menu.pointShopItems', permission: 'pointshop.read' },
                { key: 'pointShopPurchases', to: '/point-shop/purchases', icon: 'pi pi-receipt', labelKey: 'menu.pointShopPurchases', permission: 'pointshop.read' },
                { key: 'pointShopSettings', to: '/point-shop/settings', icon: 'pi pi-sliders-h', labelKey: 'menu.pointShopSettings', permission: 'pointshop.read' },
            ],
        },
        // 로보티콘 묶음(포인트 상점 묶음 다음) — 로보티콘 상점(회원 `/robotikon`, 무료 받기 + 실결제 유료 팩)은 포인트 상점과 메뉴를 따로 둔다(계약 emoticon-contract 「이름」). 코드 식별자는 `emoticon` 그대로.
        // 심사(작가 신청 팩) · 팩 관리(판매 중단·운영 팩) · 판매 내역(실결제·예외 환불) · 작가 정산(월 정산·지급 기록) · 설정(작가 보상·팩 규격·유료 판매·정산 규칙). 모두 실제 API(`useEmoticonAdminApi`, S1~S4).
        // 서버 `AdminScreenRegistry` 의 같은 다섯 줄 · 권한 `emoticon.*`(서버 `Permission`)와 맞춘다(정본은 서버).
        // 판매 내역은 `emoticon.sales`(조회·예외 환불), 작가 정산은 `emoticon.settlement`(조회·처리)로 거른다 — 돈과 구매 회원·작가 정보를 다루므로 일반 조회(`emoticon.read`)와 나눴다.
        {
            key: 'emoticon',
            icon: 'pi pi-face-smile',
            labelKey: 'menu.emoticon',
            items: [
                { key: 'emoticonReviews', to: '/robotikon/reviews', icon: 'pi pi-check-square', labelKey: 'menu.emoticonReviews', permission: 'emoticon.read' },
                { key: 'emoticonPacks', to: '/robotikon/packs', icon: 'pi pi-images', labelKey: 'menu.emoticonPacks', permission: 'emoticon.read' },
                { key: 'emoticonSales', to: '/robotikon/sales', icon: 'pi pi-receipt', labelKey: 'menu.emoticonSales', permission: 'emoticon.sales' },
                { key: 'emoticonSettlements', to: '/robotikon/settlements', icon: 'pi pi-money-bill', labelKey: 'menu.emoticonSettlements', permission: 'emoticon.settlement' },
                { key: 'emoticonSettings', to: '/robotikon/settings', icon: 'pi pi-sliders-h', labelKey: 'menu.emoticonSettings', permission: 'emoticon.read' },
            ],
        },
        // 보상 이벤트 묶음(로보티콘 묶음 다음) — 가입 선착순 지급·추첨 당첨·전체 일괄 지급처럼 회원에게 보상을 주는 이벤트와 지급 내역. 「사이트 팝업」(알림 창)과 다른 업무라 늘 「보상 이벤트」라고 부른다.
        // ⚠ 목업(`useRewardEventAdminApi`, 2026-11-07 화면 확정 전) — 서버 AdminScreenRegistry·Permission 에 같은 줄 필요(키 `rewardEvents` · 주소 `/reward-events` · 권한 `reward-event.read`).
        //   화면 안 처리는 `reward-event.manage`(만들기·고치기·후보·당첨자·추첨·시작·마감·취소)·`reward-event.grant`(지급·재시도·회수·전달 완료)·`point.adjust.large`(1인 고액 포인트)를 따로 본다.
        {
            key: 'rewardEvent',
            icon: 'pi pi-gift',
            labelKey: 'menu.rewardEvent',
            items: [{ key: 'rewardEvents', to: '/reward-events', icon: 'pi pi-gift', labelKey: 'menu.rewardEvents', permission: 'reward-event.read' }],
        },
        // 공구 묶음(결제 묶음 앞) — 운영자가 파는 실물 공동구매(현금 결제): 공구(목록·등록·운영 동작) · 수량(총량·선점·변동·추가 입고/줄이기) · 주문(발주·발송·운영 환불·명단) · 취소·반품 요청 대기열.
        // 계약 commerce-groupbuy 「화면 목록 (2b-1)」·결정 3·35. ⚠ 화면은 아직 목업(`useGroupBuy*Api`)이다. 서버 `AdminScreenRegistry` 에도 같은 네 줄이 필요하고, 쓰는 권한의 `featureReady` 를 켜야 한다(2b-2~2b-4).
        // 조회는 공구·수량 `commerce.product.read`, 주문·요청 `commerce.order.read`. 화면 안 처리는 `commerce.product.manage`·`commerce.stock.adjust`·`commerce.groupbuy.cancel`·`commerce.order.manage`·`commerce.refund.approve`·`commerce.refund.large`·`commerce.address.reveal`·`commerce.export` 를 따로 본다.
        // 주소는 서로 앞부분이 겹치지 않게 둔다(`/group-buys` 와 `/group-buy-…`). Q&A 대기열은 2b-6 에서 더한다.
        {
            key: 'groupBuy',
            icon: 'pi pi-shop',
            labelKey: 'menu.groupBuy',
            items: [
                // 결정 89 — 상품(이름·설명·이미지·카테고리·고시·옵션 축/단품·기본 공급가)과 카테고리(2단). 서버 AdminScreenRegistry 에도 두 줄
                { key: 'groupBuyProducts', to: '/group-buy-products', icon: 'pi pi-tag', labelKey: 'menu.groupBuyProducts', permission: 'commerce.product.read' },
                // 옵션 종류(사용자 결정 2026-10-28 — 상품 옵션 이름은 코드로 고른다). 서버 AdminScreenRegistry 에도 한 줄
                { key: 'groupBuyOptionTypes', to: '/group-buy-option-types', icon: 'pi pi-list-check', labelKey: 'menu.groupBuyOptionTypes', permission: 'commerce.product.read' },
                { key: 'groupBuyCategories', to: '/group-buy-categories', icon: 'pi pi-sitemap', labelKey: 'menu.groupBuyCategories', permission: 'commerce.product.read' },
                { key: 'groupBuys', to: '/group-buys', icon: 'pi pi-shopping-bag', labelKey: 'menu.groupBuys', permission: 'commerce.product.read' },
                { key: 'groupBuyStock', to: '/group-buy-stock', icon: 'pi pi-warehouse', labelKey: 'menu.groupBuyStock', permission: 'commerce.product.read' },
                { key: 'groupBuyOrders', to: '/group-buy-orders', icon: 'pi pi-truck', labelKey: 'menu.groupBuyOrders', permission: 'commerce.order.read' },
                { key: 'groupBuyRequests', to: '/group-buy-requests', icon: 'pi pi-replay', labelKey: 'menu.groupBuyRequests', permission: 'commerce.order.read' },
            ],
        },
        // 결제 묶음(포인트 상점 묶음 다음) — 현금 결제 모듈(포트원 V2)의 결제 주문 · 웹훅 수신 내역 · 결제 이상 · 날짜별 대사 · 대사 불일치. commerce.md 13-5, 계약 commerce-payment 결정 13.
        // 서버 API 에 연결됨(2a-3 — `usePaymentAdminApi`). 서버 `AdminScreenRegistry` 의 같은 다섯 줄·권한 `commerce.payment.read`·`commerce.reconcile.manage`(`featureReady` 켬)와 맞춘다 — 바꿀 때 두 곳을 같게 한다.
        // 다섯 화면 모두 조회 `commerce.payment.read`, 화면 안의 처리(다시 시도 · 수동 처리 완료 · 포트원에서 다시 확인 · 대사 처리)는 `commerce.reconcile.manage`. PG 설정은 설정 › 포트원(`/settings/portone`) 그대로.
        {
            key: 'payment',
            icon: 'pi pi-credit-card',
            labelKey: 'menu.payment',
            items: [
                { key: 'paymentOrders', to: '/payments/orders', icon: 'pi pi-receipt', labelKey: 'menu.paymentOrders', permission: 'commerce.payment.read' },
                { key: 'paymentWebhooks', to: '/payments/webhooks', icon: 'pi pi-inbox', labelKey: 'menu.paymentWebhooks', permission: 'commerce.payment.read' },
                { key: 'paymentAnomalies', to: '/payments/anomalies', icon: 'pi pi-exclamation-triangle', labelKey: 'menu.paymentAnomalies', permission: 'commerce.payment.read' },
                { key: 'paymentReconcileDays', to: '/payments/reconcile-days', icon: 'pi pi-calendar', labelKey: 'menu.paymentReconcileDays', permission: 'commerce.payment.read' },
                { key: 'paymentReconciliation', to: '/payments/reconciliation', icon: 'pi pi-arrow-right-arrow-left', labelKey: 'menu.paymentReconciliation', permission: 'commerce.payment.read' },
            ],
        },
        // 소모임 묶음 — 소모임 목록(열림·비활성, 상세 서랍에서 비활성화·다시 열기) · 개설 승인 · 분류(소모임 게시판 할당) · 설정(개설 규칙·매니저·공개 범위·운영 한도). community 관리자 앱의 「소모임」 묶음에 대응한다.
        // 네 화면 모두 서버에 연결됐다(`useGroupAdminApi`·`useGroupCategoryApi`·`useGroupSettingsApi`). 서버 `AdminScreenRegistry` 에도 같은 네 줄이 있고, 권한 `group.*` 는 `featureReady` 가 켜져 있다(group-contract 「관리자 화면」).
        // 네 화면 모두 조회 `group.read` 로 열리고, 화면 안에서 처리 `group.manage`(승인·반려·비활성화·다시 열기) · 설정 `group.settings`(분류·설정 저장)를 따로 본다.
        // 주소는 서로 앞부분이 겹치지 않게 둔다(`/groups` 와 `/group-…`) — 겹치면 목록 항목이 하위 화면에서도 같이 켜진다.
        {
            key: 'group',
            icon: 'pi pi-comments',
            labelKey: 'menu.group',
            items: [
                { key: 'groups', to: '/groups', icon: 'pi pi-users', labelKey: 'menu.groups', permission: 'group.read' },
                { key: 'groupRequests', to: '/group-requests', icon: 'pi pi-inbox', labelKey: 'menu.groupRequests', permission: 'group.read' },
                { key: 'groupCategories', to: '/group-categories', icon: 'pi pi-tags', labelKey: 'menu.groupCategories', permission: 'group.read' },
                { key: 'groupSettings', to: '/group-settings', icon: 'pi pi-sliders-h', labelKey: 'menu.groupSettings', permission: 'group.read' },
            ],
        },
        // AI 회원 묶음 — 계정 목록 · 검수 대기함 · 설정 · 활동 기록(docs/features/ai-members-contract.md). 소모임 묶음 다음.
        // 네 화면 모두 조회 `ai-member.read`(화면 안에서 처리 `ai-member.manage` · 제공사 키 `ai-member.secret`). 서버 `AdminScreenRegistry` 와 같은 키·주소·권한·아이콘·차례(아이콘은 화면 안 `AiMemberNav` 와 같다).
        // `/ai-members` 아래 세 화면은 다른 화면이다(`isActive` 가 더 구체적인 항목에 주소를 준다)
        {
            key: 'aiMember',
            icon: 'pi pi-microchip-ai',
            labelKey: 'menu.aiMember',
            items: [
                { key: 'aiMembers', to: '/ai-members', icon: 'pi pi-users', labelKey: 'menu.aiMembers', permission: 'ai-member.read' },
                { key: 'aiMemberDrafts', to: '/ai-members/drafts', icon: 'pi pi-inbox', labelKey: 'menu.aiMemberDrafts', permission: 'ai-member.read' },
                { key: 'aiMemberSettings', to: '/ai-members/settings', icon: 'pi pi-cog', labelKey: 'menu.aiMemberSettings', permission: 'ai-member.read' },
                { key: 'aiMemberLogs', to: '/ai-members/logs', icon: 'pi pi-history', labelKey: 'menu.aiMemberLogs', permission: 'ai-member.read' },
            ],
        },
        // 사업자 묶음 — 신원 인증 심사(`/business-reviews`, 심사 중·재심사) · 승인된 사업자 운영(`/businesses`, 정지·정지 해제·평문 열람). 계약 business-contract · 04-ia-review 5절.
        // 두 화면 모두 조회 `business.read`(화면 안에서 처리 `business.manage` · 평문 `business.reveal` · 정지 해제 `business.unsuspend`). 서버 `AdminScreenRegistry` 와 같은 키·주소·권한·아이콘·차례
        {
            key: 'business',
            icon: 'pi pi-briefcase',
            labelKey: 'menu.business',
            items: [
                { key: 'businessReviews', to: '/business-reviews', icon: 'pi pi-inbox', labelKey: 'menu.businessReviews', permission: 'business.read' },
                { key: 'businesses', to: '/businesses', icon: 'pi pi-building', labelKey: 'menu.businesses', permission: 'business.read' },
            ],
        },
        // 광고 묶음 — 광고 심사(`/ad-reviews`, 신청 건 AD_* — 조회 `ad.read`, 처리 `ad.manage`, 환불 `deal-order.refund`) · 광고 일정표(`/ads`) · 광고 자리 설정(`/ads/placements`, 저장 `ad.settings`). 04-ia-review 1-2.
        // 서버 `AdminScreenRegistry` 와 같은 키·주소·권한·아이콘·차례. `/ads/placements` 는 `/ads` 아래지만 다른 화면이다(`isActive` 가 더 구체적인 항목에 주소를 준다)
        {
            key: 'ad',
            icon: 'pi pi-megaphone',
            labelKey: 'menu.ad',
            items: [
                { key: 'adReviews', to: '/ad-reviews', icon: 'pi pi-inbox', labelKey: 'menu.adReviews', permission: 'ad.read' },
                { key: 'ads', to: '/ads', icon: 'pi pi-calendar', labelKey: 'menu.ads', permission: 'ad.read' },
                // 광고 성과(`/ads/performance` — 서버 `AdminScreenRegistry` 「광고」 셋째와 같은 키·주소·권한·차례)
                { key: 'adPerformance', to: '/ads/performance', icon: 'pi pi-chart-bar', labelKey: 'menu.adPerformance', permission: 'ad.read' },
                // 광고주 관리(`/ad-advertisers` — 서버 `AdminScreenRegistry` 「광고」 넷째와 같은 키·주소·권한·차례)
                { key: 'adAdvertisers', to: '/ad-advertisers', icon: 'pi pi-address-book', labelKey: 'menu.adAdvertisers', permission: 'ad.read' },
                { key: 'adPlacements', to: '/ads/placements', icon: 'pi pi-sliders-h', labelKey: 'menu.adPlacements', permission: 'ad.read' },
            ],
        },
        // 핫딜 묶음 — 핫딜 심사(`/hotdeal-reviews`, 신청 건 HOTDEAL_* — 조회 `hotdeal.read`, 처리 `hotdeal.manage`, 환불 `deal-order.refund`).
        // 회원 핫딜 1차(hotdeal-contract — 서버 `useHotdeal*Api`): 핫딜 글(`/hotdeals`, 종료·재개 `hotdeal.manage`) · 링크 규칙(`/hotdeals/domains`, 처리 `hotdeal.domain.manage`) ·
        // 의심 후보(`/hotdeals/suspicions`, 무시 `hotdeal.manage`) · 핫딜 설정(`/hotdeals/settings`, 조회·저장 `hotdeal.manage`). `/hotdeals/*` 는 `/hotdeals` 아래지만 다른 화면이다(`isActive` 가 더 구체적인 항목에 주소를 준다).
        // 서버 `AdminScreenRegistry` 와 같은 키·주소·권한·아이콘·차례 — 새 네 줄(`hotdeals`·`hotdealDomains`·`hotdealSuspicions`·`hotdealSettings`)은 서버 레지스트리에도 더해야 한다(서버 단계)
        {
            key: 'hotdeal',
            icon: 'pi pi-ticket',
            labelKey: 'menu.hotdeal',
            items: [
                { key: 'hotdealReviews', to: '/hotdeal-reviews', icon: 'pi pi-inbox', labelKey: 'menu.hotdealReviews', permission: 'hotdeal.read' },
                { key: 'hotdeals', to: '/hotdeals', icon: 'pi pi-ticket', labelKey: 'menu.hotdeals', permission: 'hotdeal.read' },
                { key: 'hotdealDomains', to: '/hotdeals/domains', icon: 'pi pi-link', labelKey: 'menu.hotdealDomains', permission: 'hotdeal.read' },
                { key: 'hotdealSuspicions', to: '/hotdeals/suspicions', icon: 'pi pi-exclamation-triangle', labelKey: 'menu.hotdealSuspicions', permission: 'hotdeal.read' },
                { key: 'hotdealSettings', to: '/hotdeals/settings', icon: 'pi pi-sliders-h', labelKey: 'menu.hotdealSettings', permission: 'hotdeal.manage' },
            ],
        },
        // 설정 메뉴의 첫 묶음 — 시스템(hamonica3 의 설정 > 일반). 「일반」·「소셜 로그인」·「조직 구성」이 있고(메일 서버는 다음 「메일」 묶음, 알림 도구·템플릿·문구·이벤트는 그다음 「알림·이벤트」 묶음) 후속 설정(사이트·연동 등)이 들어올 자리다. 프로필 메뉴의 「설정」이 여기로 들어온다
        {
            key: 'settingsSystem',
            mode: 'settings',
            icon: 'pi pi-cog',
            labelKey: 'menu.system',
            items: [
                { key: 'siteGeneral', to: '/settings/general', icon: 'pi pi-sliders-h', labelKey: 'menu.general', permission: 'site.read' },
                // 소셜 로그인 설정(카카오·네이버·구글 키) — 조회는 `site.read`, 저장·삭제는 화면 안에서 `site.secret`
                { key: 'socialLogin', to: '/settings/social-login', icon: 'pi pi-sign-in', labelKey: 'menu.socialLogin', permission: 'site.read' },
                // 포트원 설정(본인인증 — 상점 ID·API 비밀키·채널 키, 2a 에 결제 칸) — 조회는 `site.read`, 저장·지우기는 화면 안에서 `site.secret`(커머스 0-4). 서버 `AdminScreenRegistry` 에도 같은 한 줄이 필요하다(서버 단계)
                { key: 'portone', to: '/settings/portone', icon: 'pi pi-credit-card', labelKey: 'menu.portone', permission: 'site.read' },
                // 공공데이터포털 설정(국세청 사업자 조회 서비스 키 — business-contract) — 조회는 `site.read`, 저장·지우기는 화면 안에서 `site.secret`. 서버 `AdminScreenRegistry` 에도 같은 한 줄이 필요하다(서버 단계)
                { key: 'publicData', to: '/settings/public-data', icon: 'pi pi-building-columns', labelKey: 'menu.publicData', permission: 'site.read' },
                // 로그인 보안(관리자 2차 인증 켜기·끄기와 값) — 최고 관리자만(commerce.md 13-3, 권한 코드로 위임하지 않는다). 서버 `AdminScreenRegistry` 에도 같은 한 줄이 필요하다(서버 단계)
                { key: 'loginSecurity', to: '/settings/login-security', icon: 'pi pi-lock', labelKey: 'menu.loginSecurity', superOnly: true },
                // 휴일 관리(사이트 휴일 표 하나 — 공공데이터 공휴일·기본값은 읽기만, 회사 휴무일 더하기·지우기 · 「지금 받기」) — 조회 `site.read`, 바꾸기는 화면 안에서 `site.manage`. 서버 `AdminScreenRegistry` 의 같은 한 줄(로그인 보안 다음)
                { key: 'holidays', to: '/settings/holidays', icon: 'pi pi-calendar', labelKey: 'menu.holidays', permission: 'site.read' },
                // 주소 검색(회원 화면 「우편번호 찾기」의 제공처 · 자체 주소 DB 올리기 — address-search-contract) — 조회는 `site.read`, 저장·올리기는 화면 안에서 `site.manage`. 서버 `AdminScreenRegistry` 의 같은 한 줄(로그인 보안 다음)
                { key: 'addressSearch', to: '/settings/address', icon: 'pi pi-map-marker', labelKey: 'menu.addressSearch', permission: 'site.read' },
                // 조직 구성 — 운영자를 팀으로 묶는다(알림 이벤트의 「추가 수신 팀」으로 쓴다)
                { key: 'teams', to: '/settings/teams', icon: 'pi pi-sitemap', labelKey: 'menu.teams', permission: 'team.read' },
                // 통계 수집(site-analytics-contract — 방문·알림·게시판 집계를 항목별로 켜고 끈다, 모두 기본 꺼짐) — 조회 `site.read`, 저장은 화면 안에서 `site.manage`. 서버 `AdminScreenRegistry` 의 같은 한 줄(조직 구성 다음)
                { key: 'analyticsSettings', to: '/settings/analytics', icon: 'pi pi-chart-bar', labelKey: 'menu.analyticsSettings', permission: 'site.read' },
            ],
        },
        // 운영·약관 묶음(terms-operator-contract, 사용자 지시 2026-11-07) — 시스템 묶음 다음. 운영 주체(`/settings/operator` — 개인·개인사업자·법인 단계와 사업자 정보,
        // 조회 `site.read` · 저장은 화면 안에서 `site.manage`) → 약관(`/settings/terms` — 약관 자리표시가 운영 주체 값을 쓴다). 서버 `AdminScreenRegistry` 와 같은 키·주소·권한·아이콘·차례
        {
            key: 'settingsLegal',
            mode: 'settings',
            icon: 'pi pi-book',
            labelKey: 'menu.legal',
            items: [
                { key: 'siteOperator', to: '/settings/operator', icon: 'pi pi-id-card', labelKey: 'menu.siteOperator', permission: 'site.read' },
                { key: 'terms', to: '/settings/terms', icon: 'pi pi-file-check', labelKey: 'menu.terms', permission: 'terms.read' },
                // 오픈소스 라이선스 고지(oss-licenses-contract) — 조회 `site.read`, 저장은 화면 안에서 `site.manage`. 서버 `AdminScreenRegistry` 와 같은 키·주소·권한·아이콘·차례(약관 다음)
                { key: 'ossLicenses', to: '/settings/oss-licenses', icon: 'pi pi-code', labelKey: 'menu.ossLicenses', permission: 'site.read' },
            ],
        },
        // 테마 묶음(사용자 결정 2026-10-28) — 공개 사이트의 겉모양. 화면 하나(`pages/settings/theme/[[tab]].vue`): 맨 위에서 테마(기본·다음식·네이버식·로보얌)를 고르고,
        // 그 테마의 레이아웃(홈 구성 — 게시판·페이지 레이아웃은 자리만) · 메뉴 설정(메뉴 배치·메뉴 모양)을 탭으로 편집한다. 레이아웃·메뉴 설정은 테마마다 따로 저장된다.
        // 메뉴 설정은 이 메뉴에 따로 두지 않는다(사용자 결정 2026-10-28 — 화면 안 탭으로만). 항목 주소가 `/settings/theme` 라 두 탭(`…/layout`·`…/menu`) 모두 이 항목이 활성이다.
        // 조회 `site.read`, 저장은 화면 안에서 `site.manage`. 메뉴 항목 자체(무엇을 어떤 차례로)는 일반 메뉴의 「공개 메뉴」(`/menus/public`)에서 정한다. 서버 `AdminScreenRegistry` 에도 같은 한 줄이 필요하다(서버 단계)
        {
            key: 'settingsTheme',
            mode: 'settings',
            icon: 'pi pi-palette',
            labelKey: 'menu.theme',
            items: [
                { key: 'themeLayout', to: '/settings/theme', icon: 'pi pi-objects-column', labelKey: 'menu.themeLayout', permission: 'site.read' },
            ],
        },
        // 회원 설정 묶음 — 회원 기능의 사이트 규칙. 닉네임 이미지 신청 조건(조회 `nickname-image.read`, 저장은 화면 안에서 `nickname-image.manage`)
        {
            key: 'settingsMember',
            mode: 'settings',
            icon: 'pi pi-users',
            labelKey: 'menu.memberSettings',
            items: [{ key: 'nicknameImageSettings', to: '/settings/nickname-image', icon: 'pi pi-image', labelKey: 'menu.nicknameImageSettings', permission: 'nickname-image.read' }],
        },
        // 메일 묶음 — 메일 서버만(message-template-contract 「개정 3」 — 메일 종류·템플릿·레이아웃은 알림·이벤트 묶음으로 옮겼다 — 개정 4 의 「템플릿 › 메일」·「문구」)
        {
            key: 'settingsMail',
            mode: 'settings',
            icon: 'pi pi-envelope',
            labelKey: 'menu.mail',
            items: [
                // 메일 서버 설정 — 조회는 `site.read`, 저장·시험 발송은 화면 안에서 `site.secret`
                { key: 'mailSettings', to: '/settings/mail', icon: 'pi pi-server', labelKey: 'menu.mailSettings', permission: 'site.read' },
            ],
        },
        // 알림·이벤트 묶음(개정 4 — message-template-contract) — 알림 도구 · 템플릿(부모: 메일 · Slack · Mattermost · 카카오톡 — 도구별 겉모양) · 문구(부모: 운영자 · 회원 · 필수 안내 메일 — 이벤트·채널별 제목과 본문) · 이벤트(부모: 운영자 · 회원).
        // 옛 주소(개정 3 의 `/settings/notification-templates/templates|layouts|required-mails/**` · 그 전 `/settings/mail-*`)는 각 화면이 새 주소로 보낸다.
        // 모두 실제 서버에 연결돼 있다. 키·주소·권한(`template.read` · `notification.read`)은 서버 `AdminScreenRegistry` 와 같다.
        {
            key: 'settingsNotification',
            mode: 'settings',
            icon: 'pi pi-bell',
            labelKey: 'menu.notifications',
            items: [
                { key: 'notificationChannels', to: '/settings/notification-channels', icon: 'pi pi-send', labelKey: 'menu.notificationChannels', permission: 'notification.read' },
                // 템플릿은 하위 메뉴 넷을 가진 부모다(역할 관리와 같은 방식). 부모 주소(`/settings/notification-templates`)로 들어오면 메일 템플릿으로 보낸다.
                // 편집 화면(`/{유형}/{id}`)은 그 유형의 하위 주소라 그 하위가 활성이다. 카카오톡은 준비 중(보기만) — 계약이 메뉴에 두기로 정했다
                {
                    key: 'notificationTemplates',
                    to: '/settings/notification-templates',
                    icon: 'pi pi-palette',
                    labelKey: 'menu.notificationTemplates',
                    permission: 'template.read',
                    children: [
                        { key: 'mailTemplates', to: '/settings/notification-templates/mail', labelKey: 'menu.mailTemplates', permission: 'template.read' },
                        { key: 'slackTemplates', to: '/settings/notification-templates/slack', labelKey: 'menu.slackTemplates', permission: 'template.read' },
                        { key: 'mattermostTemplates', to: '/settings/notification-templates/mattermost', labelKey: 'menu.mattermostTemplates', permission: 'template.read' },
                        { key: 'kakaoTemplates', to: '/settings/notification-templates/kakao', labelKey: 'menu.kakaoTemplates', permission: 'template.read' },
                    ],
                },
                // 문구는 하위 메뉴 셋을 가진 부모다. 부모 주소(`/settings/notification-messages`)로 들어오면 운영자 문구로 보낸다.
                // 문구 목록은 받는 사람(운영자 · 회원 — 이벤트 메뉴와 같은 차례)마다 메뉴 하나다. 편집 화면(`/settings/notification-messages/{id}`)은 어느 하위에도 속하지 않아 부모만 활성이 되고, 권한·모드도 부모의 것으로 본다(`parents`)
                {
                    key: 'notificationMessages',
                    to: '/settings/notification-messages',
                    icon: 'pi pi-file-edit',
                    labelKey: 'menu.notificationMessages',
                    permission: 'template.read',
                    children: [
                        { key: 'operatorMessages', to: '/settings/notification-messages/operator', labelKey: 'menu.operatorMessages', permission: 'template.read' },
                        { key: 'memberMessages', to: '/settings/notification-messages/member', labelKey: 'menu.memberMessages', permission: 'template.read' },
                        { key: 'requiredMails', to: '/settings/notification-messages/required-mails', labelKey: 'menu.requiredMails', permission: 'template.read' },
                    ],
                },
                // 이벤트는 하위 메뉴 둘(운영자·회원)을 가진 부모다. 부모 주소(`/settings/notifications`)로 들어오면 운영자 이벤트로 보낸다
                {
                    key: 'events',
                    to: '/settings/notifications',
                    icon: 'pi pi-bolt',
                    labelKey: 'menu.events',
                    permission: 'notification.read',
                    children: [
                        { key: 'operatorEvents', to: '/settings/notifications/operator', labelKey: 'menu.eventsOperator', permission: 'notification.read' },
                        { key: 'memberEvents', to: '/settings/notifications/member', labelKey: 'menu.eventsMember', permission: 'notification.read' },
                    ],
                },
            ],
        },
        // 커머스 묶음 — 판매·결제 공통 설정(commerce.md 12-4 · 13-5). 지금은 「커머스 설정」(요청 제한) 하나이고 판매 스위치·사업자 정보 등은 그 화면의 구획으로 더한다.
        // ⚠ 화면은 아직 목업(`useCommerceSettingsApi`)이다. 서버 `AdminScreenRegistry` 에도 같은 한 줄이 필요하다(서버 단계)
        {
            key: 'settingsCommerce',
            mode: 'settings',
            icon: 'pi pi-shopping-cart',
            labelKey: 'menu.commerce',
            items: [{ key: 'commerceSettings', to: '/settings/commerce', icon: 'pi pi-sliders-h', labelKey: 'menu.commerceSettings', permission: 'commerce.settings' }],
        },
        {
            key: 'operatorsAndPermissions',
            mode: 'settings',
            icon: 'pi pi-shield',
            labelKey: 'menu.operatorsAndPermissions',
            items: [
                { key: 'operators', to: '/settings/operators', icon: 'pi pi-user-edit', labelKey: 'menu.operators', permission: 'operator.read' },
                // 역할 관리는 하위 메뉴 셋을 가진 부모다 — 하위 주소의 마지막 조각이 화면 종류다(`pages/settings/roles/[[kind]].vue`). 부모 주소(`/settings/roles`)로 직접 들어오면 운영자 역할로 보낸다
                {
                    key: 'roles',
                    to: '/settings/roles',
                    icon: 'pi pi-key',
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
                { key: 'batchLogs', to: '/settings/batch-logs', icon: 'pi pi-list', labelKey: 'menu.batchLogs', permission: 'batch.read' },
                // 캐시 관리 — 공개 사이트 응답 캐시(Spring Cache + Caffeine)의 켜기/끄기·TTL·갱신(docs/features/cache-admin-contract.md). 서버 `AdminScreenRegistry` 와 같은 권한 `cache.read`, 화면 안 바꾸기는 `cache.manage` 를 따로 본다.
                //   관리자 메뉴 구성을 이미 저장한 DB 에서는 「메뉴에 없는 화면」에 나오므로 메뉴 관리 › 관리자 메뉴에서 넣는다.
                { key: 'caches', to: '/settings/caches', icon: 'pi pi-database', labelKey: 'menu.caches', permission: 'cache.read' },
                { key: 'audit', to: '/settings/audit', icon: 'pi pi-eye', labelKey: 'menu.audit', permission: 'audit.read' },
            ],
        },
    ];

    /**
     * 권한으로 걸러 낸 메뉴 — 권한이 없는 화면 항목은 빼고, 항목이 하나도 안 남은 묶음도 뺀다. 사이드바·상단 메뉴·드로어·검색이 이것을 읽는다.
     * 역할 관리의 "이 역할이 보는 메뉴" 미리보기도 같은 함수에 그 역할의 권한을 넣어 계산한다.
     * @param {(code: string) => boolean} can - 그 권한 코드를 가졌는가.
     * @param {AdminMenuMode} [mode] - 이 모드의 노드만. 안 주면 모든 모드(검색·역할 미리보기).
     * @param {readonly AdminMenuNode[]} [nodes] - 거를 메뉴 — 셸은 DB 구성(`useAdminMenuTree().nodes`)을 넣는다. 안 주면 메뉴 정의(`ITEMS`).
     * @returns {AdminMenuNode[]} 보이는 메뉴 노드(원본 순서).
     */
    static visible(can: (code: string) => boolean, mode?: AdminMenuMode, nodes: readonly AdminMenuNode[] = UtilAdminMenu.ITEMS): AdminMenuNode[] {
        const out: AdminMenuNode[] = [];
        for (const node of nodes) {
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
            if (!UtilAdminMenu.allowed(item, can)) continue;
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
     * @param {readonly AdminMenuNode[]} [nodes] - 볼 메뉴 — 셸은 DB 구성을 넣는다(운영자가 화면을 다른 모드로 옮겼으면 그 모드). 안 주면 메뉴 정의.
     * @returns {AdminMenuMode | null} 속한 모드. 어느 메뉴에도 없으면 null.
     */
    static modeOfPath(path: string, nodes: readonly AdminMenuNode[] = UtilAdminMenu.ITEMS): AdminMenuMode | null {
        for (const node of nodes) {
            const items = UtilAdminMenu.leaves(UtilAdminMenu.isGroup(node) ? node.items : [node]);
            if (items.some((item) => UtilAdminMenu.isActive(item, path))) return UtilAdminMenu.modeOf(node);
        }
        for (const node of nodes) {
            const parents = UtilAdminMenu.parents(UtilAdminMenu.isGroup(node) ? node.items : [node]);
            if (parents.some((item) => UtilAdminMenu.isActive(item, path))) return UtilAdminMenu.modeOf(node);
        }
        return null;
    }

    /**
     * 하위를 가진 **부모** 항목들(하위까지 재귀) — 어느 하위에도 속하지 않는 부모 아래 주소(문구 편집 `/settings/notification-messages/{id}` — 받는 사람 메뉴 둘이 같이 쓴다)를 부모의 것으로 보는 데 쓴다.
     * @param {readonly AdminMenuItem[]} items - 항목들.
     * @returns {AdminMenuItem[]} 부모 항목.
     */
    static parents(items: readonly AdminMenuItem[]): AdminMenuItem[] {
        return items.flatMap((item) => (item.children ? [item, ...UtilAdminMenu.parents(item.children)] : []));
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
            if (hit) return UtilAdminMenu.allowed(hit, can);
        }
        // 어느 화면 항목에도 없지만 부모 아래인 주소(문구 편집 등)는 부모의 권한으로 본다
        for (const node of UtilAdminMenu.ITEMS) {
            const parents = UtilAdminMenu.parents(UtilAdminMenu.isGroup(node) ? node.items : [node]);
            const hit = parents.find((item) => UtilAdminMenu.isActive(item, path));
            if (hit) return UtilAdminMenu.allowed(hit, can);
        }
        return true;
    }

    /**
     * DB 의 관리자 메뉴 구성(두 모드) → 셸이 그리는 노드. 숨긴 노드(`visible: false`)·레지스트리에 없는 화면 키는 뺀다. 권한으로 거르는 일은 여기서 하지 않는다(`visible`).
     *  · 묶음(`SECTION`) → `AdminMenuGroup` · 부모 항목(`FOLDER`) → 하위를 가진 화면 항목 · 관리자 화면(`SCREEN`·`ADMIN_SCREEN`) → 레지스트리 항목(주소·권한은 레지스트리, 이름·아이콘은 구성)
     *  · 외부 링크(`SCREEN`·`URL`) → `href` 항목(구성에 고른 권한이 있으면 그 권한).
     *  · 부모 항목의 `to`: 메뉴 정의에 같은 하위를 가진 부모가 있으면 그 주소(문구 편집처럼 하위에 없는 아래 주소를 부모 것으로 보는 판정이 그대로 산다), 아니면 하위 주소들의 공통 앞부분,
     *    그것도 없으면 어느 주소와도 겹치지 않는 자리 표시 주소.
     * @param {Record<AdminMenuMode, AdminMenuConfigNodeDto[]>} modes - 서버 응답의 `modes`.
     * @returns {AdminMenuNode[]} 일반 메뉴 → 설정 메뉴 차례의 최상위 노드(최상위마다 `mode`).
     */
    static fromConfig(modes: Record<AdminMenuMode, AdminMenuConfigNodeDto[]>): AdminMenuNode[] {
        const registry = new Map(
            UtilAdminMenu.allItems()
                .filter((item) => !item.children)
                .map((item) => [item.key, item]),
        );
        const parents = UtilAdminMenu.ITEMS.flatMap((node) => UtilAdminMenu.parents(UtilAdminMenu.isGroup(node) ? node.items : [node]));
        let seq = 0;
        const keyOf = (prefix: string, node: AdminMenuConfigNodeDto) => `${prefix}-${node.id ?? node.labelKey ?? ++seq}`;
        const names = (node: AdminMenuConfigNodeDto) => ({ ...(node.label ? { label: node.label } : {}), ...(node.labelEn ? { labelEn: node.labelEn } : {}) });

        const item = (node: AdminMenuConfigNodeDto): AdminMenuItem | null => {
            if (!node.visible) return null;
            if (node.kind === 'FOLDER') {
                const children = (node.children ?? []).map(item).filter((child): child is AdminMenuItem => child !== null && !child.children);
                if (!children.length) return null;
                const keys = new Set(children.map((child) => child.key));
                const known = parents.find((parent) => (parent.children ?? []).some((child) => keys.has(child.key)));
                return { key: keyOf('folder', node), to: known?.to ?? UtilAdminMenu.commonPath(children), labelKey: node.labelKey ?? '', ...names(node), ...(node.icon ? { icon: node.icon } : {}), children };
            }
            if (node.targetType === 'URL') {
                const href = node.settings?.href;
                if (!href) return null;
                return {
                    key: keyOf('url', node),
                    to: href,
                    href,
                    newTab: node.settings?.newTab ?? true,
                    labelKey: '',
                    ...names(node),
                    ...(node.icon ? { icon: node.icon } : {}),
                    ...(node.permission ? { permission: node.permission } : {}),
                };
            }
            const screen = node.targetKey ? registry.get(node.targetKey) : undefined;
            if (!screen) return null;
            const { icon: _registryIcon, ...rest } = screen;
            return { ...rest, ...names(node), ...(node.icon ? { icon: node.icon } : {}) };
        };

        const out: AdminMenuNode[] = [];
        for (const mode of ['normal', 'settings'] as const) {
            for (const node of modes[mode] ?? []) {
                if (node.kind === 'SECTION') {
                    if (!node.visible) continue;
                    const items = (node.children ?? []).map(item).filter((child): child is AdminMenuItem => child !== null);
                    if (items.length) out.push({ key: keyOf('section', node), mode, icon: node.icon ?? 'pi pi-folder', labelKey: node.labelKey ?? '', ...names(node), items });
                    continue;
                }
                const top = item(node);
                if (top) out.push({ ...top, mode });
            }
        }
        return out;
    }

    /**
     * 하위 주소들의 공통 앞부분(조각 단위) — 운영자가 만든 부모 항목의 `to`. 공통 조각이 없으면 어느 주소에도 걸리지 않는 자리 표시 주소.
     * @param {readonly AdminMenuItem[]} children - 하위 항목.
     * @returns {string} 공통 경로.
     */
    static commonPath(children: readonly AdminMenuItem[]): string {
        const lists = children.filter((child) => !child.href).map((child) => child.to.split('/').filter(Boolean));
        const first = lists[0] ?? [];
        let n = 0;
        while (n < first.length && lists.every((list) => list[n] === first[n] && list.length > n + 1)) n++;
        return n > 0 ? `/${first.slice(0, n).join('/')}` : `/#menu-folder-${children.map((child) => child.key).join('-')}`;
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
     * 단 그 하위 주소를 **더 구체적인 다른 메뉴 항목**이 가지면(`/members` 아래의 `/members/nickname-images`, `/points` 아래의 `/points/policy`) 그 항목의 화면이다 —
     * 활성 표시가 둘로 갈라지지 않고, 열기 권한(`canOpen`)·모드(`modeOfPath`)도 그 항목의 것으로 본다.
     * @param {AdminMenuItem} item - 메뉴 항목.
     * @param {string} path - 지금 주소의 경로(`route.path`).
     * @returns {boolean} 그 항목이 활성이면 true.
     */
    static isActive(item: AdminMenuItem, path: string): boolean {
        if (item.href) return false;
        if (item.to === '/') return path === '/';
        if (!UtilAdminMenu.under(path, item.to)) return false;
        return !UtilAdminMenu.allItems().some((other) => other.to.length > item.to.length && UtilAdminMenu.under(other.to, item.to) && UtilAdminMenu.under(path, other.to));
    }

    /**
     * 주소가 그 경로이거나 그 아래인가.
     * @param {string} path - 볼 주소.
     * @param {string} to - 메뉴 항목의 경로.
     * @returns {boolean} 같거나 `to/` 로 시작하면 true.
     */
    static under(path: string, to: string): boolean {
        return path === to || path.startsWith(`${to}/`);
    }

    /**
     * 메뉴의 모든 화면 항목(부모·하위 포함) — 더 구체적인 항목을 찾는 데 쓴다.
     * @returns {AdminMenuItem[]} 모든 항목.
     */
    static allItems(): AdminMenuItem[] {
        const walk = (items: readonly AdminMenuItem[]): AdminMenuItem[] => items.flatMap((item) => [item, ...(item.children ? walk(item.children) : [])]);
        return walk(UtilAdminMenu.ITEMS.flatMap((node) => (UtilAdminMenu.isGroup(node) ? node.items : [node])));
    }

    /**
     * 묶음 아래 항목 중 하나라도 활성인가 — 상단 메뉴 줄의 최상위 항목 표시에 쓴다.
     * @param {AdminMenuGroup} group - 메뉴 묶음.
     * @param {string} path - 지금 주소의 경로(`route.path`).
     * @returns {boolean} 하나라도 활성이면 true.
     */
    static isGroupActive(group: AdminMenuGroup, path: string): boolean {
        return group.items.some((item) => UtilAdminMenu.isItemActive(item, path));
    }

    /**
     * 이 항목이나 그 하위 중 하나라도 활성인가 — 하위가 있는 부모의 활성·펼침 판정에 쓴다.
     * @param {AdminMenuItem} item - 메뉴 항목.
     * @param {string} path - 지금 주소의 경로(`route.path`).
     * @returns {boolean} 하나라도 활성이면 true.
     */
    static isItemActive(item: AdminMenuItem, path: string): boolean {
        // 부모는 하위 주소에 있을 때, 또는 어느 하위에도 속하지 않는 부모 아래 주소(문구 편집 `/settings/notification-messages/{id}`)에 있을 때 활성이다
        return UtilAdminMenu.leaves([item]).some((leaf) => UtilAdminMenu.isActive(leaf, path)) || (Boolean(item.children) && UtilAdminMenu.isActive(item, path));
    }
}
